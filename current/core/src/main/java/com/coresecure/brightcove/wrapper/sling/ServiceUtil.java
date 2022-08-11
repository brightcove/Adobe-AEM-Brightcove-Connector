/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2018 Coresecure Inc.

    Authors:    Alessandro Bonfatti
                Yan Kisen
                Pablo Kropilnicki

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

    - Additional permission under GNU GPL version 3 section 7
    If you modify this Program, or any covered work, by linking or combining
    it with httpclient 4.1.3, httpcore 4.1.4, httpmine 4.1.3, jsoup 1.7.2,
    squeakysand-commons and squeakysand-osgi (or a modified version of those
    libraries), containing parts covered by the terms of APACHE LICENSE 2.0
    or MIT License, the licensors of this Program grant you additional
    permission to convey the resulting work.

*/
package com.coresecure.brightcove.wrapper.sling;

import com.coresecure.brightcove.wrapper.BrightcoveAPI;
import com.coresecure.brightcove.wrapper.enums.EconomicsEnum;
import com.coresecure.brightcove.wrapper.objects.*;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.HttpServices;
import com.coresecure.brightcove.wrapper.utils.JcrUtil;
import com.coresecure.brightcove.wrapper.utils.S3UploadUtil;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.dam.api.Rendition;
import com.day.cq.dam.commons.handler.StandardImageHandler;
import com.day.cq.tagging.InvalidTagFormatException;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagConstants;
import com.day.cq.tagging.TagManager;
import com.day.cq.wcm.api.NameConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.*;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ServiceUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceUtil.class);
    private static final String ISO_8601_24H_FULL_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    private static final String[] fields = {Constants.NAME, Constants.CREATED_AT  , Constants.DURATION, Constants.COMPLETE, Constants.ID, Constants.ACCOUNT_ID ,Constants.DESCRIPTION , Constants.LINK, Constants.TAGS, Constants.LONG_DESCRIPTION, Constants.REFERENCE_ID, Constants.ECONOMICS, Constants.UPDATED_AT , Constants.SCHEDULE, Constants.STATE, Constants.GEO , Constants.CUSTOM_FIELDS, Constants.TEXT_TRACKS , Constants.IMAGES ,Constants.PROJECTION, Constants.LABELS};

    private String account_id;
    public static final int DEFAULT_LIMIT = 100;
    private BrightcoveAPI brAPI = null;

    public ServiceUtil(String aAccount_id) {
        account_id = aAccount_id;
        brAPI = new BrightcoveAPI(aAccount_id);
    }

    public static ConfigurationGrabber getConfigurationGrabber() {
        BundleContext bundleContext = FrameworkUtil.getBundle(ConfigurationGrabber.class).getBundleContext();
        return (ConfigurationGrabber) bundleContext.getService(bundleContext.getServiceReference(ConfigurationGrabber.class.getName()));
    }

    public static Cookie getAccountCookie(SlingHttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        Map<String, Cookie> cookiesMap = new TreeMap<String, Cookie>();
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++) {

                LOGGER.trace("Cookies: " + cookies[i].getName());
                cookiesMap.put(cookies[i].getName(), cookies[i]);
            }
        }
        return cookiesMap.get("brc_act");
    }

    public static String getAccountFromCookie(SlingHttpServletRequest request) {
        Cookie account = getAccountCookie(request);

        LOGGER.trace("getAccountFromCookie: " + ((account != null) ? account.getValue() : ""));
        return (account != null) ? account.getValue() : "";
    }

    public static void setAccountCookie(HttpServletResponse response, String account, boolean secure) {
        SimpleDateFormat COOKIE_EXPIRES_HEADER_FORMAT = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss zzz");
        COOKIE_EXPIRES_HEADER_FORMAT.setTimeZone(new SimpleTimeZone(0, "GMT"));
        Date d = new Date();
        d.setTime(d.getTime() + 3600 * 24000); //1 hour
        String cookieLifeTime = COOKIE_EXPIRES_HEADER_FORMAT.format(d);
        if (secure) {
            response.setHeader("Set-Cookie", "brc_act=" + account + "; Expires=" + cookieLifeTime + "; Path=/; Secure; HTTPOnly");
        } else {
            response.setHeader("Set-Cookie", "brc_act=" + account + "; Expires=" + cookieLifeTime + "; Path=/; HTTPOnly");
        }
    }

    private static List sortByValue(final Map m) {
        List keys = new ArrayList();
        keys.addAll(m.keySet());
        Collections.sort(keys, new Comparator() {
            public int compare(Object o1, Object o2) {
                try {
                    JSONObject v1 = (JSONObject) m.get(o1);
                    String s1 = (String) v1.get("name");
                    JSONObject v2 = (JSONObject) m.get(o2);
                    String s2 = (String) v2.get("name");

                    if (s1 == null) {
                        return (s2 == null) ? 0 : 1;
                    } else if (s1 instanceof Comparable) {
                        return ((Comparable) s1).compareTo(s2);
                    } else {
                        return 0;
                    }

                } catch (JSONException e) {
                    return 0;
                }
            }
        });
        return keys;
    }

    private String getLength(String videoId, String accountKeyStr) {
        String result = "";
        try {
            long millis = brAPI.cms.getVideoByRef(videoId).getLong(Constants.DURATION);
            result = String.format("%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(millis),
                    TimeUnit.MILLISECONDS.toSeconds(millis) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
            );
        } catch (JSONException je) {
            LOGGER.error(je.getClass().getName(), je);
        }
        return result;
    }

    public String getName(String videoId, String accountKeyStr) {
        String result = "";
        try {
            result = brAPI.cms.getVideoByRef(videoId).getString(Constants.NAME);
        } catch (JSONException je) {
            LOGGER.error(je.getClass().getName(), je);
        }
        return result;
    }

    public boolean deleteVideo(String videoId) {
        boolean result = false;
        JSONObject apiResult = brAPI.cms.deleteVideo(videoId);
        if (!apiResult.has("error_code")) {
            result = true;
        }
        return result;
    }

    public String getList(Boolean exportCSV, String query) {
        String result = getList(exportCSV, 0, DEFAULT_LIMIT, true, query);
        return result;
    }

    public String getList(Boolean exportCSV, int offset, int limit, boolean full_scroll, String query) {
        return getList(exportCSV, offset, limit, full_scroll, query, Constants.NAME);
    }
    public JSONArray getVideoSources(String videoID) {
        return brAPI.cms.getVideoSources(videoID);
    }
    public String getList(Boolean exportCSV, int offset, int limit, boolean full_scroll, String query, String sort) {
        return getList(exportCSV,  offset,  limit, full_scroll, query, sort, false, false);
    }
    public String getList(Boolean exportCSV, int offset, int limit, boolean full_scroll, String query, String sort, boolean dam_only) {
        return getList(exportCSV,  offset,  limit, full_scroll, query, sort, dam_only, false);
    }
    public String getList(Boolean exportCSV, int offset, int limit, boolean full_scroll, String query, String sort, boolean dam_only, boolean clips_only) {
        LOGGER.debug("getList: " + query);


        JSONObject items = new JSONObject();
        String result = "";
        limit = limit > 0 ? limit : 100;
        try {
            long totalItems = 0;
            JSONArray videos = brAPI.cms.addThumbnail(brAPI.cms.getVideos(query, limit, offset, sort));
            LOGGER.debug("videos " + videos.toString());
            offset = offset + limit;
            if (videos.length() > 0) {
                totalItems = brAPI.cms.getVideosCount(query).getLong("count");

                while (offset < totalItems && full_scroll) {
                    JSONArray videos_page = brAPI.cms.addThumbnail(brAPI.cms.getVideos(query, limit, offset, sort, dam_only, clips_only));
                    for (int i = 0; i < videos_page.length(); i++) {
                        JSONObject video = videos_page.getJSONObject(i);
                        videos.put(video);
                    }
                    offset = offset + limit;

                }

            }

            if (exportCSV) {
                JSONObject tempJSON;
                String csvString = "\"Video Name\",\"Video ID\"\r\n";

                for (int key = 0; key < videos.length(); key++) {
                    tempJSON = videos.getJSONObject(key);
                    csvString += "\"" + tempJSON.getString(Constants.NAME) + "\",\"" + tempJSON.getString(Constants.ID) + "\"\r\n";
                }
                result = csvString;
            } else {
                items.put("items", videos);
                items.put("totals", totalItems);
                result = items.toString(1);
            }
        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public boolean isLong(String input) {
        if (input == null || input.trim().isEmpty()) return false;
        try {
            Long.parseLong(input);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String searchVideo(String querystr, int offset, int limit) {
        //Fixed the performance issue at the component authoring side.
        //String result = getList(false, offset, limit, true, querystr);
        boolean fullscroll = !(limit > 0);
        String result = getList(false, offset, limit, fullscroll, querystr);
        return result;
    }

    public String searchVideo(String querystr, int offset, int limit, String sort, boolean dam_only) {
        //Fixed the performance issue at the component authoring side.
        //String result = getList(false, offset, limit, true, querystr);
        boolean fullscroll = !(limit > 0);
        String result = getList(false, offset, limit, fullscroll, querystr, sort, dam_only);
        return result;
    }
    public String searchVideo(String querystr, int offset, int limit, String sort) {
        return searchVideo(querystr, offset, limit, sort, false);
    }

    public JSONObject getSelectedVideo(String videoIdstr) {
        JSONObject result = new JSONObject();
        try {
            result = brAPI.cms.getVideo(videoIdstr);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public JSONObject getCustomFields() {
        JSONObject result = new JSONObject();
        try {
            result = brAPI.cms.getCustomFields();
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }


    public String getVideoByRefID(String videoIdstr) {
        String result = "";
        try {
            JSONObject video = brAPI.cms.getVideoByRef(videoIdstr);
            result = video.toString();
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public String getListSideMenu(String limits) {
        String result = "";

        try {
            int limit = DEFAULT_LIMIT;
            int firstElement = 0;
            int lastElement = limit;
            if (limits != null && !limits.trim().isEmpty() && limits.split(Constants.DELIMETER_STRING_DOUBLE)[0] != null) {
                firstElement = Integer.parseInt(limits.split(Constants.DELIMETER_STRING_DOUBLE)[0]);
                lastElement = Integer.parseInt(limits.split(Constants.DELIMETER_STRING_DOUBLE)[1]);
                limit = lastElement - firstElement;
            }
            result = getList(false, firstElement, limit, false, "");
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public String getSuggestions(String querystr, int offset, int limit) {
        String result = getList(false, offset, limit, true, querystr);
        return result;
    }

    public JSONObject getPlayers() {
        JSONObject result = new JSONObject();
        try {
            result = brAPI.cms.getPlayers();
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public JSONObject moveVideoToFolder(String folderId, String videoId) {
        JSONObject result = new JSONObject();
        try {
            result = brAPI.cms.moveVideoToFolder(videoId, folderId);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public JSONObject removeVideoFromFolder(String folderId, String videoId) {
        JSONObject result = new JSONObject();
        try {
            result = brAPI.cms.removeVideoFromFolder(videoId, folderId);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public JSONObject deletePlaylist(String playlistId) {
        JSONObject result = new JSONObject();
        try {
            result = brAPI.cms.deletePlaylist(playlistId);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public JSONObject createPlaylist(String title) {
        JSONObject result = new JSONObject();
        try {
            result = brAPI.cms.createBlankPlaylist(title);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public String getVideosInFolder(String folder, int offset) {
        JSONObject items = new JSONObject();
        String result = "";
        try {
            JSONArray videos = brAPI.cms.getVideosInFolder(folder, offset);

            if (videos.length() > 0 ) {
                items.put("items", videos);
                items.put(Constants.TOTALS, videos.length());
            }

            result = items.toString(1);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public String getVideosWithLabel(String label, int offset) {
        JSONObject items = new JSONObject();
        String result = "";
        try {
            JSONArray videos = brAPI.cms.getVideosWithLabel(label, offset);

            if (videos.length() > 0 ) {
                items.put("items", videos);
                items.put(Constants.TOTALS, videos.length());
            }

            result = items.toString(1);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public String getFolders() {
        JSONObject items = new JSONObject();
        String result = "";
        try {
            JSONArray folders = brAPI.cms.getFolders(100, 0);

            if (folders.length() > 0 ) {
                items.put("items", folders);
                items.put(Constants.TOTALS, folders.length());
            }

            result = items.toString(1);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public String getLabels() {
        JSONObject items = new JSONObject();
        String result = "";
        try {
            JSONObject labels = brAPI.cms.getLabels();
            LOGGER.debug("getLabels(): " + labels.toString());

            if (labels.getJSONArray("labels").length() > 0 ) {
                items.put("items", labels.getJSONArray("labels"));
                items.put(Constants.TOTALS, labels.getJSONArray("labels").length());
            }

            result = items.toString(1);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public JSONObject getPlaylistByID(String id) {
        JSONObject items = new JSONObject();
        JSONObject result = new JSONObject();
        try {
            result = brAPI.cms.getPlaylist(id);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public String getVideosInPlaylistByID(String id) {
        JSONObject items = new JSONObject();
        String result = "";
        try {
            JSONArray videos = brAPI.cms.getVideosInPlaylist(id);

            if (videos.length() > 0 ) {
                items.put("items", videos);
            }
            items.put("playlist", id);

            result = items.toString(1);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public String getPlaylists(int offset, int limit, boolean exportCSV, boolean full_scroll) {
        return getPlaylists(null, offset, limit, exportCSV, full_scroll);
    }

    public String getExperiences(String q) {
        JSONObject items = new JSONObject();
        String result = "";
        try {
            items = brAPI.cms.getExperiences(q, Constants.NAME);
            LOGGER.info("getExperiences count(): " + items.length());
            result = items.toString(1);
        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public String getPlaylists(String q, int offset, int limit, boolean exportCSV, boolean full_scroll) {
        JSONObject items = new JSONObject();
        String result = "";
        try {
            int pageNumber = 0;
            long totalItems = 0;
            JSONArray playlists = brAPI.cms.getPlaylists(q, limit, offset, Constants.NAME);
            offset = offset + limit;
            if (playlists.length() > 0) {
                totalItems = brAPI.cms.getPlaylistsCount().getLong("count");

                double totalPages = Math.floor((double)totalItems / limit);

                while (offset < totalItems && full_scroll) {
                    JSONArray videos_page = brAPI.cms.getPlaylists(q, limit, offset, Constants.NAME);
                    for (int i = 0; i < videos_page.length(); i++) {
                        playlists.put(videos_page.get(i));
                    }
                    offset = offset + limit;

                }

            }

            if (exportCSV) {
                JSONObject tempJSON;
                String csvString = "\"Video Name\",\"Video ID\"\r\n";

                for (int key = 0; key < playlists.length(); key++) {
                    tempJSON = playlists.getJSONObject(key);
                    csvString += "\"" + tempJSON.getString(Constants.NAME) + "\",\"" + tempJSON.getString(Constants.ID) + "\"\r\n";
                }
                result = csvString;
            } else {
                items.put("items", playlists);
                items.put("totals", totalItems);
                result = items.toString(1);
            }
        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    //Returns JSON of the video information based on a comma separated string of their ids.
    public JSONArray getVideosJsonByIds(String videoIds, String videoProperties) {
        JSONArray jsa = new JSONArray();
        try {
            String[] videos_ids = videoIds.split(",");
            for (String id : videos_ids) {
                jsa.put(brAPI.cms.getVideo(id));
            }
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return jsa;
    }

    public JSONObject updatePlaylist(String playlistId, String[] videos) {
        JSONObject result = new JSONObject();
        result = brAPI.cms.updatePlaylist(playlistId, videos);
        return result;
    }


    public JSONObject updateVideo(Video aVideo) {

        JSONObject result = new JSONObject();
        try {
            JSONObject videoItem = brAPI.cms.updateVideo(aVideo);
            try {
                if (videoItem != null && videoItem.has(Constants.ID)) {
                    String newVideoId = videoItem.getString(Constants.ID);
                    LOGGER.info(Constants.RESULT_LOG_NEW_VIDEO_TMPL, newVideoId);
                    result.put(Constants.VIDEOID, newVideoId);
                    result.put(Constants.SENT, true);
                } else {
                    result.put(Constants.ERROR, "updateVideo Error");
                    result.put(Constants.SENT, false);
                }

            } catch (Exception e) {
                LOGGER.error(e.getClass().getName(), e);
                result.put(Constants.ERROR, "updateVideo Exception");
                result.put(Constants.SENT, false);
            }
        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }


    public JSONObject createVideo(Video aVideo, String ingestURL, String ingestProfile) {
        JSONObject result = new JSONObject();
        try {
            JSONObject videoItem = brAPI.cms.createVideo(aVideo);
            String newVideoId = videoItem.getString(Constants.ID);
            JSONObject videoIngested = new JSONObject();
            try {
                com.coresecure.brightcove.wrapper.objects.Ingest ingest = new com.coresecure.brightcove.wrapper.objects.Ingest(ingestProfile, ingestURL);
                videoIngested = brAPI.cms.createIngest(new com.coresecure.brightcove.wrapper.objects.Video(videoItem), ingest);
                if (videoIngested != null && videoIngested.has(Constants.ID)) {
                    LOGGER.info(Constants.RESULT_LOG_NEW_VIDEO_TMPL, newVideoId);
                    result.put(Constants.VIDEOID, newVideoId);
                    result.put("output", videoIngested);
                } else {
                    result.put(Constants.ERROR, "createIngest Error");
                    brAPI.cms.deleteVideo(newVideoId);
                }

            } catch (Exception exIngest) {
                LOGGER.error("createVideo", exIngest);
                result.put(Constants.ERROR, "createIngest Exception");
                brAPI.cms.deleteVideo(newVideoId);
            }
        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName() + "Create Video", e);
        }
        return result;
    }

    public JSONObject createVideoS3(Video aVideo, String filename, InputStream is) {
        JSONObject result = new JSONObject();
        try {
            JSONObject videoItem = brAPI.cms.createVideo(aVideo);
            String newVideoId = videoItem.getString(Constants.ID);
            JSONObject videoIngested = new JSONObject();
            try {
                videoIngested = brAPI.cms.getIngestURL(newVideoId, filename);
                LOGGER.info(Constants.RESULT_LOG_NEW_VIDEO_TMPL, newVideoId);
                result.put(Constants.BUCKET, videoIngested.get(Constants.BUCKET));
                result.put(Constants.VIDEOID, newVideoId);
                result.put(Constants.OBJECT_KEY, videoIngested.get(Constants.OBJECT_KEY));
                result.put(Constants.API_REQUEST_URL, videoIngested.get(Constants.API_REQUEST_URL));
                result.put(Constants.SIGNED_URL, videoIngested.get(Constants.SIGNED_URL));

                ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
                ConfigurationService brcService = cg.getConfigurationService(account_id);
                String proxy_address = brcService.getProxy();

                LOGGER.trace("******CREATE ASSET s3 [ 1 ] " + proxy_address);

                boolean sent = S3UploadUtil.uploadToUrl(new URL(videoIngested.getString(Constants.SIGNED_URL)), is , HttpServices.getProxy());
                result.put(Constants.SENT, sent);
                if (!sent) {
                    brAPI.cms.deleteVideo(newVideoId);
                }
                else
                {
                    cg = ServiceUtil.getConfigurationGrabber();
                    brcService = cg.getConfigurationService(account_id);
                    String ingest_profile = brcService.getIngestProfile();
                    result.put("job", brAPI.cms.requestIngestURL(newVideoId, ingest_profile, videoIngested.getString(Constants.API_REQUEST_URL), true));
                }
            } catch (Exception e) {
                LOGGER.error(e.getClass().getName(), e);
                result.put(Constants.ERROR, e.getStackTrace()[0].getMethodName());
                brAPI.cms.deleteVideo(newVideoId);
            }
            LOGGER.trace(Constants.RESULT_LOG_TMPL, result.toString(1));

        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    public JSONObject createAssetS3(String newVideoId, String filename, InputStream is) {
        JSONObject result = new JSONObject();
        try {
            try {
                JSONObject assetIngested = brAPI.cms.getIngestURL(newVideoId, filename);
                LOGGER.info(Constants.RESULT_LOG_NEW_VIDEO_TMPL,newVideoId);
                result.put(Constants.BUCKET, assetIngested.get(Constants.BUCKET));
                result.put(Constants.VIDEOID, newVideoId);
                result.put(Constants.OBJECT_KEY, assetIngested.get(Constants.OBJECT_KEY));
                result.put(Constants.API_REQUEST_URL, assetIngested.get(Constants.API_REQUEST_URL));
                result.put(Constants.SIGNED_URL, assetIngested.get(Constants.SIGNED_URL));


                ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
                ConfigurationService brcService = cg.getConfigurationService(account_id);
                String proxy_address = brcService.getProxy();

                LOGGER.trace("******CREATE ASSET s3 [ 2 ] " + proxy_address);

                boolean sent = S3UploadUtil.uploadToUrl(new URL(assetIngested.getString(Constants.SIGNED_URL)), is , HttpServices.getProxy());
                result.put(Constants.SENT, sent);
            } catch (Exception e) {
                LOGGER.error(e.getClass().getName(), e);
                result.put(Constants.ERROR, e.getStackTrace()[0].getMethodName());
                brAPI.cms.deleteVideo(newVideoId);
            }
            LOGGER.trace(Constants.RESULT_LOG_TMPL, result.toString(1));
        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName(), e);
        }

        return result;
    }

    public String getListPlaylistsSideMenu(String limits) {
        String result = "";

        try {
            int limit = DEFAULT_LIMIT;
            int firstElement = 0;
            int lastElement = limit;
            if (limits != null && !limits.trim().isEmpty() && limits.split(Constants.DELIMETER_STRING_DOUBLE)[0] != null) {
                firstElement = Integer.parseInt(limits.split(Constants.DELIMETER_STRING_DOUBLE)[0]);
                lastElement = Integer.parseInt(limits.split(Constants.DELIMETER_STRING_DOUBLE)[1]);
                limit = lastElement - firstElement;
            }
            result = getPlaylists(firstElement, limit, false, false);
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return result;
    }

    private String getKey(String x){
        String key = x;
        if (x.equals(Constants.TAGS))
        {
            key = NameConstants.PN_TAGS;
        }
        // else if (x.equals(Constants.LABELS))
        // {
        //     key = Constants.LABELS;
        // }
        else if (Constants.NAME.equals(x))
        {
            key = DamConstants.DC_TITLE;      //NAME -> ASSET TITLE
        }
        else
        {
            key = "brc_".concat(x); //ALL ELSE -> BRC_KEYNAME
        }
        return key;
    }

    private void setMapJSONArray(String key, JSONArray objArray, ResourceResolver resourceResolver, ModifiableValueMap map) {
        TagManager tagManager = resourceResolver.adaptTo(TagManager.class);
        if (tagManager == null) return;
        try {
            if (key.equals(NameConstants.PN_TAGS)) {
                List<String> tags = new ArrayList<String>();
                for (int cnt = 0; cnt < objArray.length(); cnt++) {
                    String tagValue = objArray.getString(cnt);

                    String tagKey = tagValue.replaceAll(": ", ":").trim();


                    try {
                        if (tagManager.canCreateTag(tagKey)) {

                            Tag tag = tagManager.createTag(tagKey, tagValue, "");

                            //Tag tag = tagManager.createTagByTitle(tagValue, Locale.US);
                            resourceResolver.commit();
                            LOGGER.trace("tag created > {}", tagValue);
                            //tagManager.setTags(assetRes, new Tag[]{tag}, true);
                        } else {
                            //Tag[] tags = tagManager.findTagsByTitle(tagValue, Locale.US);
                            //tagManager.setTags(assetRes, tags, true);
                            LOGGER.warn("tag create failed [exists] > added >  {}", tagValue);

                        }
                        tags.add(tagKey);
                    } catch (InvalidTagFormatException e) {
                        LOGGER.error(e.getClass().getName(), e);
                    }
                }
                resourceResolver.commit();
                map.put(key, tags.toArray());
            } else {
                LOGGER.trace("setMapJSONArray() is using a generic array for " + key);
                map.put(key, objArray.join("#@#").split("#@#"));
            }
        }catch (Exception e) {
            LOGGER.error(e.getClass().getName(),e);
        }
    }

    private void setLabelsJSONArray(String key, JSONArray objArray, ResourceResolver resourceResolver, ModifiableValueMap map) {
        TagManager tagManager = resourceResolver.adaptTo(TagManager.class);
        if (tagManager == null) return;
        try {
            if (key.equals(NameConstants.PN_TAGS)) {
                List<String> tags = new ArrayList<String>();
                for (int cnt = 0; cnt < objArray.length(); cnt++) {
                    String tagValue = objArray.getString(cnt);

                    String tagKey = tagValue.replaceAll(": ", ":").trim();


                    try {
                        if (tagManager.canCreateTag(tagKey)) {

                            Tag tag = tagManager.createTag(tagKey, tagValue, "");

                            //Tag tag = tagManager.createTagByTitle(tagValue, Locale.US);
                            resourceResolver.commit();
                            LOGGER.trace("tag created > {}", tagValue);
                            //tagManager.setTags(assetRes, new Tag[]{tag}, true);
                        } else {
                            //Tag[] tags = tagManager.findTagsByTitle(tagValue, Locale.US);
                            //tagManager.setTags(assetRes, tags, true);
                            LOGGER.warn("tag create failed [exists] > added >  {}", tagValue);

                        }
                        tags.add(tagKey);
                    } catch (InvalidTagFormatException e) {
                        LOGGER.error(e.getClass().getName(), e);
                    }
                }
                resourceResolver.commit();
                map.put(key, tags.toArray());
            } else {
                map.put(key, objArray.join("#@#").split("#@#"));
            }
        }catch (Exception e) {
            LOGGER.error(e.getClass().getName(),e);
        }
    }

    private void setImages(JSONObject objObject, Asset newAsset) {
        try {
            if (objObject.has(Constants.POSTER)) {
                JSONObject images_poster_obj = objObject.getJSONObject(Constants.POSTER);
                String src = images_poster_obj.getString(Constants.SRC);
                //DO GET FOR RENDITION -> TO ASSET "brc_poster"
                URL srcURL = new URL(src);
                InputStream ris = srcURL.openStream();
                //Map<String,Object> rendition_map = new HashMap<String,Object>();
                newAsset.addRendition(Constants.BRC_POSTER_PNG, ris, StandardImageHandler.PNG1_MIMETYPE);
            } else {
                newAsset.removeRendition(Constants.BRC_POSTER_PNG);
            }
            if (objObject.has(Constants.THUMBNAIL))
            {
                JSONObject images_poster_obj = objObject.getJSONObject(Constants.THUMBNAIL);
                String src = images_poster_obj.getString(Constants.SRC);
                //DO GET FOR RENDITION -> TO ASSET "brc_thumbnail"

                InputStream ris = new URL(src).openStream();
                //Map<String,Object> rendition_map = new HashMap<String,Object>();
                newAsset.addRendition(Constants.BRC_THUMBNAIL_PNG, ris, StandardImageHandler.PNG1_MIMETYPE);
            } else {
                newAsset.removeRendition(Constants.BRC_THUMBNAIL_PNG);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Failure to initialize remote source for {0}", newAsset.getPath(), e);
        }
    }
    private void setSchedule(JSONObject objObject, ModifiableValueMap assetmap){
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_24H_FULL_FORMAT);
            String starts_at = objObject.getString(Constants.STARTS_AT);
            if (starts_at != null && !starts_at.toString().equals(Constants.NULLSTRING)) {
                assetmap.put(DamConstants.PN_ON_TIME, starts_at);
            } else {
                if (assetmap.containsKey(DamConstants.PN_ON_TIME)) assetmap.remove(DamConstants.PN_ON_TIME);
            }
            String ends_at = objObject.getString(Constants.ENDS_AT);
            if (ends_at != null && !ends_at.toString().equals(Constants.NULLSTRING)) {
                assetmap.put(DamConstants.PN_OFF_TIME, ends_at);
            } else {
                if (assetmap.containsKey(DamConstants.PN_OFF_TIME)) assetmap.remove(DamConstants.PN_OFF_TIME);
            }
        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
    }

    private void setLink(JSONObject objObject, ModifiableValueMap map) {
        try {
            String link_url = objObject.getString(Constants.URL);
            if (link_url != null && !link_url.toString().equals(Constants.NULLSTRING)) {
                map.put(Constants.BRC_LINK_URL, link_url);
            } else {
                if (map.containsKey(Constants.BRC_LINK_URL)) map.remove(Constants.BRC_LINK_URL);
            }
            String link_text = objObject.getString(Constants.TEXT);
            if (link_text != null && !link_text.toString().equals(Constants.NULLSTRING)) {
                map.put(Constants.BRC_LINK_TEXT, link_text);
            } else {
                if (map.containsKey(Constants.BRC_LINK_TEXT)) map.remove(Constants.BRC_LINK_TEXT);
            }
        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
    }
    private void setObject(JSONObject objObject, Resource metadataRes, String key) {
        try {
            Node metadataNode = metadataRes.adaptTo(Node.class);
            if (metadataNode == null) return;

            Node subNode;
            if (metadataRes.getChild(key) == null) {
                subNode = metadataNode.addNode(key);
            } else {
                subNode = metadataNode.getNode(key);
            }
            if (subNode == null) return;
            Resource subResource = metadataRes.getChild(key);
            if (subResource == null) return;
            ModifiableValueMap submap = subResource.adaptTo(ModifiableValueMap.class);
            if(submap==null) return;

            Iterator<String> itrObj = objObject.keys();
            while (itrObj.hasNext()) {
                String selectorKey = itrObj.next();
                submap.put(selectorKey, objObject.get(selectorKey));
            }
        } catch (RepositoryException e) {
            LOGGER.error(e.getClass().getName(), e);
        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
    }

    private void removeObject(String key, String x, ModifiableValueMap map, Asset newAsset, ModifiableValueMap assetmap ) {
        if (x.equals(Constants.IMAGES)) {
            //NULL ON IMAGES MEANS NULL SOURCES OF IMAGES
            newAsset.removeRendition(Constants.BRC_POSTER_PNG);
            newAsset.removeRendition(Constants.BRC_THUMBNAIL_PNG);
        } else if (x.equals(Constants.SCHEDULE)) {
            //NULL OBJECT OF SCHEDULE = EMPTY SCHEDULE METADATA
            if (assetmap.containsKey(DamConstants.PN_ON_TIME))
                assetmap.remove(DamConstants.PN_ON_TIME);
            if (assetmap.containsKey(DamConstants.PN_OFF_TIME))
                assetmap.remove(DamConstants.PN_OFF_TIME);
        } else if (x.equals(Constants.LINK)) {
            //NULL LINK OBJECT MEANS EMPTY URL + TEXT METADATA
            if (map.containsKey(Constants.BRC_LINK_TEXT)) map.remove(Constants.BRC_LINK_TEXT);
            if (map.containsKey(Constants.BRC_LINK_URL)) map.remove(Constants.BRC_LINK_URL);
        } else {
            //IF ANY OTHER ARE NULL AND WERE ACTIVE - ARE NOW EQUIVALENT
            if (map.containsKey(key)) map.remove(key);
        }
    }

    private void setObject(Object obj, String key, String x, ModifiableValueMap map, Asset newAsset, ModifiableValueMap assetmap) {
        if (obj != null && !obj.toString().toString().equals(Constants.NULLSTRING)) {
            if (key.equals(Constants.BRC_DURATION)) {
                double input = Double.parseDouble(obj.toString());
                // now we need to convert to a double to account for partial seconds
                double inputD = input / 1000;
                LOGGER.debug("brc_duration: " + inputD);
                obj = String.format("%02d:%02d:%02d", (int)(inputD / 3600), (int)((inputD % 3600) / 60), Math.round((inputD % 3600) % 60));
            }
            map.put(key, obj); //MAIN SET OF THE KEYS->VALUES FOR THIS VIDEO OBJECT
        } else {

            //Improve this check, this is the handle for null object / string
            //WE TAKE THESE NULL VALUES AS ACTUAL VALUES AND EXECUTE
            removeObject(key, x, map, newAsset, assetmap );
        }
    }

    public void updateAsset(@Nonnull Asset newAsset, JSONObject innerObj, ResourceResolver resourceResolver, String requestedAccount) throws JSONException, RepositoryException, PersistenceException {

        try {

                // LOGGER.trace(innerObj.toString(1));
                LOGGER.trace("UPDATING ASSET>>: " + newAsset.getPath());
                LOGGER.trace("ASSET JSON>>: " + innerObj.toString());

                Resource assetRes = newAsset.adaptTo(Resource.class);                        //INITIALIZE THE ASSET RESOURCE
                ModifiableValueMap assetmap = assetRes.getChild(NameConstants.NN_CONTENT).adaptTo(ModifiableValueMap.class);

                Resource metadataRes = assetRes.getChild(Constants.ASSET_METADATA_PATH);            //INITIALIZE THE ASSET BC METADATA MAP RESOURCE
                ModifiableValueMap map = metadataRes.adaptTo(ModifiableValueMap.class);

                //SET FIRST PIECE OF METADATA
                map.put(Constants.BRC_ACCOUNTID, requestedAccount);

                //HANDLE TAG S
                TagManager tagManager = resourceResolver.adaptTo(TagManager.class);
                List<String> tags = new ArrayList<String>();


                map.put(TagConstants.PN_TAGS, tags.toArray());

                for (String x : fields) {

                    //ADAPT NAME OF METADATA COMPING IN -> AEM PROPERTIES TO BE STORED
                    if (!innerObj.has(x)) {
                        LOGGER.trace("##HAS KEY BUT OBJECT IT LEADS TO IS NULL!");
                        LOGGER.trace("## HAS OBJECT WITH KEY : " + x +" ? "+innerObj.has(x) + " isnull? : "+ (innerObj.get(x)==null));
                        break;
                    }


                    String key = getKey(x);

                    Object obj = innerObj.get(x);

                    LOGGER.trace("[X] {} {}", obj, key);

                    //IF THE CURRENT METADATA IS AN ARRAY
                    if (obj instanceof JSONArray) {
                        LOGGER.trace("FOUND ARRAY>>: " + key);
                        JSONArray objArray = (JSONArray) obj;
                        if (key.equals(NameConstants.PN_TAGS)) {
                            setMapJSONArray(key, objArray, resourceResolver, map);
                        }
                        else {
                            setMapJSONArray(key, objArray, resourceResolver, map);
                        }
                    } else if (obj instanceof JSONObject) {

                        JSONObject objObject = (JSONObject) obj;
                        //CASE IMAGES
                        if (x.equals(Constants.IMAGES)) {
                            setImages(objObject, newAsset);
                        } //CASE SCHEDULE
                        else if (x.equals(Constants.SCHEDULE)) {
                            setSchedule(objObject, assetmap);
                        } //ELSE - LINK
                        else if (x.equals(Constants.LINK)) {
                            setLink(objObject, map);
                        } else {
                            setObject(objObject, metadataRes, key);
                        }
                    } else //NOT ARRAY NOR OBJECT
                    {

                        //THIS HANDLES REST OF NULL SET KEYS WHICH MAP TO PROPERTY VALUES
                        setObject(obj, key, x, map, newAsset, assetmap);
                    }
                }

                //AFTER SETTING ALL THE METADATA - SET THE LAST UPDATE TIME
                //SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                map.put(Constants.BRC_LASTSYNC, com.coresecure.brightcove.wrapper.utils.JcrUtil.now2calendar());
                resourceResolver.commit();
                LOGGER.trace(">>UPDATED METADATA FOR VIDEO : [{}]",map.get(Constants.BRC_ID));

            //MAIN TRY
        } catch (IOException e) {
            LOGGER.error(e.getClass().getName(), e);
        }

    }

    private JSONObject setOriginalRendition(Rendition original_rendition, Date brc_lastsync_time, Asset _asset, ServiceUtil serviceUtil, Video currentVideo) throws JSONException{
        JSONObject master = new JSONObject();
        ValueMap original_map = original_rendition.getProperties();
        Date orig_lastmod_time = original_map.get(JcrConstants.JCR_LASTMODIFIED,new Date(0));
        LOGGER.trace("ORGINAL RENDITION : [Rendition Last Mod: {}] VS [Last Sync: {} ]"  ,orig_lastmod_time, brc_lastsync_time);
        //LOGGER.trace("{}",original_map);
        ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
        ConfigurationService brcService = cg.getConfigurationService(account_id);
        String ingest_profile = brcService.getIngestProfile();
        if (ingest_profile == null || ingest_profile.length() == 0) {
            ingest_profile = "";
        }

        if(orig_lastmod_time.compareTo(brc_lastsync_time)  > 0)
        {
            LOGGER.trace("UPLOADING ORIGINAL");
            //CHECK FOR Null BRC _ ID?
            InputStream original_rendition_is = _asset.getRendition(DamConstants.ORIGINAL_FILE) != null ? _asset.getRendition(DamConstants.ORIGINAL_FILE).getStream() : null;
            JSONObject s3_url_resp_original = serviceUtil.createAssetS3(currentVideo.id, _asset.getName() ,original_rendition_is);

            LOGGER.trace("S3RESP : " + s3_url_resp_original);
            LOGGER.trace("##CURRENT VIDEO " + currentVideo.toJSON());
            if (s3_url_resp_original != null && s3_url_resp_original.getBoolean(Constants.SENT)) {
                master = new JSONObject("{'master': {'url': '" + s3_url_resp_original.getString(Constants.API_REQUEST_URL) + "'},'profile': '" + ingest_profile + "','capture-images': false}");
            }

        }
        else
        {
            LOGGER.trace("Original Rendition Update Skipped");
        }
        return master;
    }

    private void addPoster(Rendition poster_rendition, Date brc_lastsync_time, Asset _asset, ServiceUtil serviceUtil, Video currentVideo, JSONObject master) throws JSONException {
        ValueMap poster_map = poster_rendition.getProperties();
        Date poster_lastmod_time = poster_map.get(JcrConstants.JCR_LASTMODIFIED,new Date(0));

        LOGGER.trace("POSTER RENDITION : [Rendition Last Mod: {}] VS [Last Sync: {} ]"  ,poster_lastmod_time, brc_lastsync_time);

        if(poster_lastmod_time.compareTo(brc_lastsync_time) > 0)
        {
            LOGGER.trace("UPLOADING POSTER");
            //CHECK FOR Null BRC _ ID?
            InputStream poster_rendition_is = _asset.getRendition(Constants.BRC_POSTER_PNG) != null ? _asset.getRendition(Constants.BRC_POSTER_PNG).getStream() : null;
            JSONObject s3_url_resp_poster = serviceUtil.createAssetS3(currentVideo.id,Constants.BRC_POSTER_PNG,poster_rendition_is);

            LOGGER.trace("S3RESP : " + s3_url_resp_poster);
            LOGGER.trace("##CURRENT VIDEO " + currentVideo.toJSON());
            //POSTER
            if (s3_url_resp_poster != null && s3_url_resp_poster.getBoolean(Constants.SENT)) {
                //IF SUCCESS - PUT
                Poster poster = new Poster(s3_url_resp_poster.getString(Constants.API_REQUEST_URL));
                master.put(Constants.POSTER, poster.toJSON());
            }
        }
        else
        {
            LOGGER.trace("Poster Rendition Update Skipped");
        }
    }


    private void addThumb(Rendition thumb_rendition, Date brc_lastsync_time, Asset _asset, ServiceUtil serviceUtil, Video currentVideo, JSONObject master) throws JSONException {
        ValueMap thumbnail_map = thumb_rendition.getProperties(); //RETURNED NULL EACH TIME
        Date thumbnail_lastmod_time = thumbnail_map.get(JcrConstants.JCR_LASTMODIFIED, new Date(0));
        LOGGER.trace("THUMBNAIL RENDITION : [Rendition Last Mod: {}] VS [Last Sync: {} ]"  ,thumbnail_lastmod_time, brc_lastsync_time);
        if (thumbnail_lastmod_time.compareTo(brc_lastsync_time) > 0)
        {
            LOGGER.trace("UPLOADING THUMBNAIL");
            InputStream thumbnail_rendition = _asset.getRendition(Constants.BRC_THUMBNAIL_PNG) != null ? _asset.getRendition(Constants.BRC_THUMBNAIL_PNG).getStream() : null;
            JSONObject s3_url_resp_thumbnail = serviceUtil.createAssetS3(currentVideo.id, Constants.BRC_THUMBNAIL_PNG, thumbnail_rendition);

            if (s3_url_resp_thumbnail != null && s3_url_resp_thumbnail.getBoolean(Constants.SENT)) {
                //IF SUCCESS - PUT
                Thumbnail thumbnail = new Thumbnail(s3_url_resp_thumbnail.getString(Constants.API_REQUEST_URL));
                master.put(Constants.THUMBNAIL, thumbnail.toJSON());
            }
        }
        else
        {
            LOGGER.trace("Thumbnail Rendition Update Skipped");
        }
    }
    public boolean updateRenditions(Asset _asset, Video currentVideo) throws JSONException
    {
        boolean result = false;
        Long asset_lastmod = _asset.getLastModified();
        Resource assetRes = _asset.adaptTo(Resource.class);
        if (assetRes == null) return false;
        Resource metadataRes = assetRes.getChild(Constants.ASSET_METADATA_PATH);
        if (metadataRes == null) return false;
        ValueMap brc_lastsync_map = metadataRes.adaptTo(ValueMap.class);
        if (brc_lastsync_map == null) return false;
        Date brc_lastsync_time = brc_lastsync_map.get(Constants.BRC_LASTSYNC,new Date(0));
        if (brc_lastsync_time == null) return false;
        ServiceUtil serviceUtil = new ServiceUtil(account_id);

        Rendition poster_rendition = _asset.getRendition(Constants.BRC_POSTER_PNG);
        Rendition thumb_rendition = _asset.getRendition(Constants.BRC_THUMBNAIL_PNG);
        Rendition original_rendition = _asset.getRendition(DamConstants.ORIGINAL_FILE);

        JSONObject master = new JSONObject();

        if (currentVideo.id == null) return false;
        //ORGINAL RENDITION - REPLACE CHECK -  RENDITION PROCESS
        if (original_rendition != null)
        {
            master = setOriginalRendition(original_rendition, brc_lastsync_time, _asset, serviceUtil, currentVideo);
        }


        //POSTER RENDITION PROCESS
        if (poster_rendition != null)
        {
            addPoster(poster_rendition, brc_lastsync_time, _asset, serviceUtil, currentVideo, master);
        }


        //THUMBNAIL RENDITION PROCESS
        if (thumb_rendition != null) {
            addThumb(thumb_rendition, brc_lastsync_time, _asset, serviceUtil, currentVideo, master);
        }


        //UPLOAD INJEST SENDS THE IMAGE OBJECT TO THE  API - UPDATES THE METADATA TO POINT TO THE NEW URLS
        if (master.has(Constants.POSTER) || master.has(Constants.THUMBNAIL) || master.has(Constants.MASTER) ) {

            LOGGER.trace("master OBJ    ECT : {}" , master);
            JSONObject response = brAPI.cms.uploadInjest(currentVideo.id, master);
            LOGGER.trace(Constants.RESPONSE , response);
            JSONObject api_resp = new JSONObject(response.getString(Constants.RESPONSE));
            if (api_resp.has(Constants.ID)) {
                result = true;
            }
        } else {
            result = true;
        }



        return result;
    }


    private Schedule getSchedule(ValueMap assetMap) {
        //SCHEDULE
        SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_24H_FULL_FORMAT);

        Date sched_start = assetMap.get(DamConstants.PN_ON_TIME, Date.class);
        Date sched_ends  = assetMap.get(DamConstants.PN_OFF_TIME, Date.class);
        Schedule schedule = null;
        String start = sched_start != null ? sdf.format(sched_start): null;
        String end = sched_ends != null ? sdf.format(sched_ends): null;

        if(sched_start!=null || sched_ends != null)
        {
            schedule = new Schedule(start,end );
        }
        return schedule;
    }



    public Video createVideo(String request, Asset asset, String aState)
    {



        LOGGER.trace("VIDEO CREATION CALLED FOR {} req: {}", asset.getName() , request);

        Resource assetRes = asset.adaptTo(Resource.class);
        LOGGER.trace("assetRes: {}" , assetRes.getPath());

        Resource metadataRes = assetRes.getChild(Constants.ASSET_METADATA_PATH);
        //SUB ASSETS
        Resource custom_node = metadataRes.getChild(Constants.BRC_CUSTOM_FIELDS)!= null ? metadataRes.getChild(Constants.BRC_CUSTOM_FIELDS) : null;

        //MAIN MAP
        ValueMap assetMap = assetRes.getChild(NameConstants.NN_CONTENT).adaptTo(ValueMap.class);
        ValueMap map = metadataRes.adaptTo(ValueMap.class);

        //SUBMAPS
        ValueMap custom_node_map = custom_node != null ? custom_node.adaptTo(ValueMap.class) : null;

        //RELATED LINK
        String aUrl = map.get(Constants.BRC_LINK_URL, String.class);
        String aText  = map.get(Constants.BRC_LINK_TEXT, String.class);
        RelatedLink alink  = new RelatedLink( (aUrl != null ? (aText != null ? aText : "" ) : null),  (aText != null ? (aUrl != null ? aUrl : "" ): null));

        //Schedule
        Schedule schedule = getSchedule(assetMap);

        //Geo
        Geo geo = null;

        //TAGS
        String[] tagsList = metadataRes.getValueMap().get(TagConstants.PN_TAGS,new String[]{});
        List<String> list = new ArrayList<String>(Arrays.asList(tagsList));
        tagsList = list.toArray(new String[0]);
        //REMOVE BRIGHTCOVE TAG BEFORE PUSH
        Collection<String> tags = JcrUtil.tagsToCollection(tagsList);
        list = null;

        String[] rawList = metadataRes.getValueMap().get(getKey(Constants.LABELS),new String[]{});
        List<String> labelList = new ArrayList<String>(Arrays.asList(rawList));
        rawList = labelList.toArray(new String[0]);
        //REMOVE BRIGHTCOVE TAG BEFORE PUSH
        Collection<String> labels = JcrUtil.tagsToCollection(rawList);
        labelList = null;


        //STO FROM LOCAL VIDEOS INITIALIZE THESE SO THAT YOU CAN SEND -- COULD COME FROM PROPERTIES VALUE MAP
        String name = map.get(DamConstants.DC_TITLE, asset.getName());
        String id = map.get(Constants.BRC_ID, String.class);
        String referenceId = map.get(Constants.BRC_REFERENCE_ID, "");
        String shortDescription = map.get(Constants.BRC_DESCRIPTION,"");
        String longDescription = map.get(Constants.BRC_LONG_DESCRIPTION,"");
        String projection = "equirectangular".equals(map.get(Constants.BRC_PROJECTION,""))? Constants.EQUIRECTANGULAR : "";


        Map<String, Object> custom_fields = new HashMap();

        LOGGER.trace("###CUSTOM NODEMAP###");
        try
        {
            JSONObject custom_fields_obj = getCustomFields();
            JSONArray custom_fields_arr = custom_fields_obj.getJSONArray(Constants.CUSTOM_FIELDS);
            for(int z = 0 ; z < custom_fields_arr.length() ; z ++ )
            {
                JSONObject current = custom_fields_arr.getJSONObject(z);
                custom_fields.put( current.getString(Constants.ID), custom_node_map.get(current.getString(Constants.ID),""));
            }

        }
        catch (Exception e)
        {
            LOGGER.error("REPO EXCEPTION {}" , e);
        }

        //economics enum initialization
        EconomicsEnum economics = EconomicsEnum.valueOf(map.get(Constants.BRC_ECONOMICS, "AD_SUPPORTED"));

        //ININTIALIZING WRAPPER OBJECTS

        //COMPLETE
        Boolean complete = map.get(Constants.BRC_COMPLETE,false);

        //THIS VIDEO
        Video video;

        video = new Video(
                id,
                name,
                referenceId,
                shortDescription,
                longDescription,
                aState,
                tags,
                geo,
                schedule,
                complete,
                alink,
                custom_fields,
                economics,
                projection
        );
        LOGGER.trace("Video {}", video);
        LOGGER.trace(">>>>>>>>>>///>>>>>>>>>>");
        return video;
    }
}
