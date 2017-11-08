/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2017 Coresecure Inc.

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
import com.coresecure.brightcove.wrapper.utils.JcrUtil;
import com.coresecure.brightcove.wrapper.utils.S3UploadUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.day.cq.tagging.InvalidTagFormatException;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.*;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final String[] fields = {"name", "created_at"  , "duration", "complete", "id", "account_id" ,"description" , "link", "tags","long_description", "reference_id", "economics", "updated_at" , "schedule", "state", "geo" , "custom_fields","text_tracks" , "images" ,"projection"};

    private String account_id;
    public static int DEFAULT_LIMIT = 100;
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
            ;
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
            long millis = brAPI.cms.getVideoByRef(videoId).getLong("duration");
            result = String.format("%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(millis),
                    TimeUnit.MILLISECONDS.toSeconds(millis) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
            );
        } catch (JSONException je) {
            //todo log
        }
        return result;
    }

    public String getName(String videoId, String accountKeyStr) {
        String result = "";
        try {
            result = brAPI.cms.getVideoByRef(videoId).getString("name");
        } catch (JSONException je) {
            //todo log
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
        return getList(exportCSV, offset, limit, full_scroll, query, "name");
    }
    public JSONArray getVideoSources(String videoID) {
        return brAPI.cms.getVideoSources(videoID);
    }
    public String getList(Boolean exportCSV, int offset, int limit, boolean full_scroll, String query, String sort) {
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
                    JSONArray videos_page = brAPI.cms.addThumbnail(brAPI.cms.getVideos(query, limit, offset, sort));
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
                    csvString += "\"" + tempJSON.getString("name") + "\",\"" + tempJSON.getString("id") + "\"\r\n";
                }
                result = csvString;
            } else {
                items.put("items", videos);
                items.put("totals", totalItems);
                result = items.toString(1);
            }
        } catch (JSONException e) {
            LOGGER.error("JSONException", e);
            e.printStackTrace();
        } finally {

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

    public String searchVideo(String querystr, int offset, int limit, String sort) {
        //Fixed the performance issue at the component authoring side.
        //String result = getList(false, offset, limit, true, querystr);
        boolean fullscroll = !(limit > 0);
        String result = getList(false, offset, limit, fullscroll, querystr, sort);
        return result;
    }

    public JSONObject getSelectedVideo(String videoIdstr) {
        JSONObject result = new JSONObject();
        try {
            result = brAPI.cms.getVideo(videoIdstr);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
        return result;
    }

    public JSONObject getCustomFields() {
        JSONObject result = new JSONObject();
        try {
            result = brAPI.cms.getCustomFields();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
        return result;
    }


    public String getVideoByRefID(String videoIdstr) {
        String result = "";
        try {
            JSONObject video = brAPI.cms.getVideoByRef(videoIdstr);
            result = video.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
        return result;
    }

    public String getListSideMenu(String limits) {
        String result = "";

        try {
            int limit = DEFAULT_LIMIT;
            int firstElement = 0;
            int lastElement = limit;
            if (limits != null && !limits.trim().isEmpty() && limits.split("\\.\\.")[0] != null) {
                firstElement = Integer.parseInt(limits.split("\\.\\.")[0]);
                lastElement = Integer.parseInt(limits.split("\\.\\.")[1]);
                limit = lastElement - firstElement;
            }
            result = getList(false, firstElement, limit, false, "");
        } catch (Exception e) {

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
            e.printStackTrace();
        } finally {

        }
        return result;
    }


    public JSONObject getPlaylistByID(String id) {
        JSONObject result = new JSONObject();
        try {
            result = brAPI.cms.getPlaylist(id);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
        return result;

    }

    public String getPlaylists(int offset, int limit, boolean exportCSV, boolean full_scroll) {
        return getPlaylists(null, offset, limit, exportCSV, full_scroll);
    }

    public String getPlaylists(String q, int offset, int limit, boolean exportCSV, boolean full_scroll) {
        JSONObject items = new JSONObject();
        String result = "";
        try {
            int pageNumber = 0;
            long totalItems = 0;
            JSONArray playlists = brAPI.cms.getPlaylists(q, limit, offset, "name");
            offset = offset + limit;
            if (playlists.length() > 0) {
                totalItems = brAPI.cms.getPlaylistsCount().getLong("count");

                double totalPages = Math.floor(totalItems / limit);

                while (offset < totalItems && full_scroll) {
                    JSONArray videos_page = brAPI.cms.getPlaylists(q, limit, offset, "name");
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
                    csvString += "\"" + tempJSON.getString("name") + "\",\"" + tempJSON.getString("id") + "\"\r\n";
                }
                result = csvString;
            } else {
                items.put("items", playlists);
                items.put("totals", totalItems);

                result = items.toString(1);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {

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
            e.printStackTrace();
        } finally {

        }
        return jsa;
    }


    public JSONObject updateVideo(Video aVideo) {

        JSONObject result = new JSONObject();
        try {
            JSONObject videoItem = brAPI.cms.updateVideo(aVideo);
            try {
                if (videoItem != null && videoItem.has("id")) {
                    String newVideoId = videoItem.getString("id");
                    LOGGER.info("New video id: '" + newVideoId + "'.");
                    result.put("videoid", newVideoId);
                    result.put("sent", true);
                } else {
                    result.put("error", "updateVideo Error");
                    result.put("sent", false);
                }

            } catch (Exception exIngest) {
                LOGGER.error("updateVideo", exIngest);
                result.put("error", "updateVideo Exception");
                result.put("sent", false);
            }
        } catch (JSONException e) {
            LOGGER.error("updateVideo", e);
        }
        return result;
    }


    public JSONObject createVideo(Video aVideo, String ingestURL, String ingestProfile) {
        JSONObject result = new JSONObject();
        try {
            JSONObject videoItem = brAPI.cms.createVideo(aVideo);
            String newVideoId = videoItem.getString("id");
            JSONObject videoIngested = new JSONObject();
            try {
                com.coresecure.brightcove.wrapper.objects.Ingest ingest = new com.coresecure.brightcove.wrapper.objects.Ingest(ingestProfile, ingestURL);
                videoIngested = brAPI.cms.createIngest(new com.coresecure.brightcove.wrapper.objects.Video(videoItem), ingest);
                if (videoIngested != null && videoIngested.has("id")) {
                    LOGGER.info("New video id: '" + newVideoId + "'.");
                    result.put("videoid", newVideoId);
                    result.put("output", videoIngested);
                } else {
                    result.put("error", "createIngest Error");
                    brAPI.cms.deleteVideo(newVideoId);
                }

            } catch (Exception exIngest) {
                LOGGER.error("createVideo", exIngest);
                result.put("error", "createIngest Exception");
                brAPI.cms.deleteVideo(newVideoId);
            }
        } catch (JSONException e) {
            LOGGER.error("JSON Error - createVideo", e);
        }
        return result;
    }

    public JSONObject createVideoS3(Video aVideo, String filename, InputStream is) {
        JSONObject result = new JSONObject();
        try {
            JSONObject videoItem = brAPI.cms.createVideo(aVideo);
            String newVideoId = videoItem.getString("id");
            JSONObject videoIngested = new JSONObject();
            try {
                videoIngested = brAPI.cms.getIngestURL(newVideoId, filename);
                if (videoIngested != null && videoIngested.has("bucket")) {
                    LOGGER.info("New video id: '" + newVideoId + "'.");
                    result.put("bucket", videoIngested.get("bucket"));
                    result.put("videoid", newVideoId);
                    result.put("object_key", videoIngested.get("object_key"));
                    result.put("api_request_url", videoIngested.get("api_request_url"));
                    result.put("signed_url", videoIngested.get("signed_url"));
                    boolean sent = S3UploadUtil.uploadToUrl(new URL(videoIngested.getString("signed_url")), is);

                    result.put("sent", sent);
                    if (!sent) {
                        brAPI.cms.deleteVideo(newVideoId);
                    } else {
                        result.put("job", brAPI.cms.requestIngestURL(newVideoId, "high-resolution", videoIngested.getString("api_request_url"), true));
                    }
                } else {
                    LOGGER.trace("createIngest: " + videoIngested.toString(1));

                    result.put("error", "createIngest Error");
                    brAPI.cms.deleteVideo(newVideoId);
                }

            } catch (Exception exIngest) {
                LOGGER.error("createIngest", exIngest);
                result.put("error", "createIngest Exception");
                brAPI.cms.deleteVideo(newVideoId);
            }
            LOGGER.trace("result: " + result.toString(1));

        } catch (JSONException e) {
            LOGGER.error("createVideo", e);
        }

        return result;
    }

    public JSONObject createAssetS3(String newVideoId, String filename, InputStream is) {
        JSONObject result = new JSONObject();
        try {
            JSONObject assetIngested = new JSONObject();
            try {
                assetIngested = brAPI.cms.getIngestURL(newVideoId, filename);
                if (assetIngested != null && assetIngested.has("bucket")) {
                    LOGGER.info("New video id: '" + newVideoId + "'.");
                    result.put("bucket", assetIngested.get("bucket"));
                    result.put("videoid", newVideoId);
                    result.put("object_key", assetIngested.get("object_key"));
                    result.put("api_request_url", assetIngested.get("api_request_url"));
                    result.put("signed_url", assetIngested.get("signed_url"));
                    boolean sent = S3UploadUtil.uploadToUrl(new URL(assetIngested.getString("signed_url")), is);
                    result.put("sent", sent);
                } else {
                    LOGGER.trace("createIngest: " + assetIngested.toString(1));
                    result.put("error", "createIngest Error");
                }

            } catch (Exception exIngest) {
                LOGGER.error("createIngest", exIngest);
                result.put("error", "createIngest Exception");
                brAPI.cms.deleteVideo(newVideoId);
            }
            LOGGER.trace("result: " + result.toString(1));

        } catch (JSONException e) {
            LOGGER.error("createAssetS3", e);
        }

        return result;
    }

    public String getListPlaylistsSideMenu(String limits) {
        String result = "";

        try {
            int limit = DEFAULT_LIMIT;
            int firstElement = 0;
            int lastElement = limit;
            if (limits != null && !limits.trim().isEmpty() && limits.split("\\.\\.")[0] != null) {
                firstElement = Integer.parseInt(limits.split("\\.\\.")[0]);
                lastElement = Integer.parseInt(limits.split("\\.\\.")[1]);
                limit = lastElement - firstElement;
            }
            result = getPlaylists(firstElement, limit, false, false);
        } catch (Exception e) {

        }
        return result;
    }

    public void updateAsset(Asset newAsset, JSONObject innerObj, ResourceResolver resourceResolver, String requestedAccount) throws JSONException, RepositoryException, PersistenceException {

        try {
            if (newAsset != null)
            {

                // LOGGER.trace(innerObj.toString(1));//TODO: Debugger print statement
                LOGGER.trace("UPDATING ASSET>>: " + newAsset.getPath());

                Resource assetRes = newAsset.adaptTo(Resource.class);                        //INITIALIZE THE ASSET RESOURCE
                ModifiableValueMap assetmap = assetRes.getChild("jcr:content").adaptTo(ModifiableValueMap.class);

                Resource metadataRes = assetRes.getChild("jcr:content/metadata");            //INITIALIZE THE ASSET BC METADATA MAP RESOURCE
                ModifiableValueMap map = metadataRes.adaptTo(ModifiableValueMap.class);

                //SET FIRST PIECE OF METADATA
                map.put("brc_account_id", requestedAccount);

                //HANDLE TAG S
                TagManager tagManager = resourceResolver.adaptTo(TagManager.class);
                List<String> tags = new ArrayList<String>();


                map.put("cq:tags", tags.toArray());

                for (String x : fields)
                {

                    if(innerObj.has(x))
                    {
                        LOGGER.trace("[X] {} " + innerObj.getString(x), x);
                    }

                    //ADAPT NAME OF METADATA COMPING IN -> AEM PROPERTIES TO BE STORED
                    if (innerObj.has(x))
                    {
                        String key = x;
                        if (x.equals("tags"))
                        {
                            key = "cq:".concat(x); //TAGS -> CQ TAGS
                        }
                        else if ("name".equals(x))
                        {
                            key = "dc:title";      //NAME -> ASSET TITLE
                        }
                        else
                        {
                            key = "brc_".concat(x); //ALL ELSE -> BRC_KEYNAME
                        }
                        // SECOND PRINT STATEMNT - LOGGER.trace("" + x + " -> " + "[" + innerObj.get(x) + "] is null? -> " + innerObj.get(x).equals(null));

                        Object obj = innerObj.get(x);

                        //IF THE CURRENT METADATA IS AN ARRAY
                        if (obj instanceof JSONArray)
                        {
                            JSONArray objArray = (JSONArray) obj;
                            if (x.equals("tags")) {


                                //LOGGER.trace("TAG ARRAY "+objArray.toString(1));

                                for (int cnt = 0; cnt < objArray.length(); cnt++)
                                {
                                    String tagValue = objArray.getString(cnt);

                                    String tagKey = tagValue.replaceAll(": ",":").trim();


                                    try {
                                        if (tagManager.canCreateTag(tagKey)) {

                                            Tag tag = tagManager.createTag(tagKey, tagValue, "");

                                            //Tag tag = tagManager.createTagByTitle(tagValue, Locale.US);
                                            resourceResolver.commit();
                                            LOGGER.trace("tag created > " + tagValue);
                                            //tagManager.setTags(assetRes, new Tag[]{tag}, true);
                                        } else {
                                            //Tag[] tags = tagManager.findTagsByTitle(tagValue, Locale.US);
                                            //tagManager.setTags(assetRes, tags, true);
                                            LOGGER.warn("tag create failed [exists] > added >  ", tagValue);

                                        }
                                        tags.add(tagKey);
                                    } catch (InvalidTagFormatException e) {
                                        LOGGER.error("Invalid Tag Format", e);
                                    }
                                }
                                resourceResolver.commit();
                                map.put(key, tags.toArray());
                            } else {
                                map.put(key, objArray.join("#@#").split("#@#"));
                            }
                        }
                        else if (obj instanceof JSONObject)
                        {

                            //ELSE IF IT IS AN OBJECT
                            JSONObject objObject = (JSONObject) obj;

                            //CASE IMAGES
                            if (x.equals("images"))
                            {
                                if (objObject != null) {

                                    try {

                                        //LOGGER.trace(objObject.toString());
                                        if (objObject.has("poster")) {
                                            JSONObject images_poster_obj = objObject.getJSONObject("poster");
                                            String src = images_poster_obj.getString("src");
                                            //DO GET FOR RENDITION -> TO ASSET "brc_poster"
                                            URL srcURL = new URL(src);
                                            InputStream ris = srcURL.openStream();
                                            //Map<String,Object> rendition_map = new HashMap<String,Object>();
                                            newAsset.addRendition("brc_poster.png", ris, "image/jpeg");
                                        }

                                        if (objObject.has("thumbnail"))
                                        {
                                            JSONObject images_poster_obj = objObject.getJSONObject("thumbnail");
                                            String src = images_poster_obj.getString("src");
                                            //DO GET FOR RENDITION -> TO ASSET "brc_thumbnail"

                                            InputStream ris = new URL(src).openStream();
                                            //Map<String,Object> rendition_map = new HashMap<String,Object>();
                                            newAsset.addRendition("brc_thumbnail.png", ris, "image/jpeg");
                                        }
                                    }
                                    catch (Exception e)
                                    {
                                        LOGGER.error("Failure to initialize remote source for "+ newAsset.getPath(), e);
                                    }


                                } else {
                                    newAsset.removeRendition("brc_poster.png");
                                    newAsset.removeRendition("brc_thumbnail.png");
                                }
                            } //CASE SCHEDULE
                            else if (x.equals("schedule"))
                            {
                                if (objObject != null) {


                                    LOGGER.trace("PRE-PARSE>>>>>>>");
                                    SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_24H_FULL_FORMAT);


                                    String starts_at = objObject.getString("starts_at");
                                    if (starts_at != null && !starts_at.equals("null")) {
                                        assetmap.put("onTime", starts_at);
                                    } else {
                                        if (assetmap.containsKey("onTime")) assetmap.remove("onTime");
                                    }
                                    String ends_at = objObject.getString("ends_at");
                                    if (ends_at != null && !ends_at.equals("null")) {
                                        assetmap.put("offTime", ends_at);
                                    } else {
                                        if (assetmap.containsKey("offTime")) assetmap.remove("offTime");
                                    }
                                } else {
                                    LOGGER.trace("PRE-REMOVE>>>>>>>");
                                    if (assetmap.containsKey("onTime")) assetmap.remove("onTime");
                                    if (assetmap.containsKey("offTime")) assetmap.remove("offTime");
                                }
                            } //ELSE - LINK
                            else if (x.equals("link"))
                            {

                                //"link":{"text":"Sample related link","url":"www.brightcove.com"},"
                                if (objObject != null)
                                {
                                    String link_url = objObject.getString("url");
                                    if (link_url != null && !link_url.equals("null")) {
                                        map.put("brc_link_url", link_url);
                                    } else {
                                        if (map.containsKey("brc_link_url")) map.remove("brc_link_url");
                                    }
                                    String link_text = objObject.getString("text");
                                    if (link_text != null && !link_text.equals("null")) {
                                        map.put("brc_link_text", link_text);
                                    } else {
                                        if (map.containsKey("brc_link_text")) map.remove("brc_link_text");
                                    }
                                }
                                else
                                {
                                    if (map.containsKey("brc_link_text")) map.remove("brc_link_text");
                                    if (map.containsKey("brc_link_url")) map.remove("brc_link_url");
                                }
                            }
                            else
                            {

                                //TODO: CHECK - SUBMODULE NECESSARY ? This is else JSON Object Case

                                Node subNode;
                                Resource subResource;
                                if (metadataRes.getChild(key) == null) {
                                    subNode = metadataRes.adaptTo(Node.class).addNode(key);
                                } else {
                                    subNode = metadataRes.adaptTo(Node.class).getNode(key);
                                }

                                if (subNode != null) {
                                    subResource = metadataRes.getChild(key);
                                    ModifiableValueMap submap = subResource.adaptTo(ModifiableValueMap.class);
                                    Iterator<String> itrObj = objObject.keys();
                                    while (itrObj.hasNext()) {
                                        String selectorKey = itrObj.next();
                                        submap.put(selectorKey, objObject.get(selectorKey));
                                    }
                                }



                            }
                        }
                        else //NOT ARRAY NOR OBJECT
                        {
                            //DURATION SETTING AND CHECK
                            try {
                                //Check format of brc_duration
                                if (key.equals("brc_duration") && obj!=null)
                                {
                                    //LOGGER.trace("*!*!*! current key : " + key.toString() + " value: " + obj.toString());
                                    //conditional conversion
                                    int input = Integer.parseInt(obj.toString());
                                    input = input / 1000 ;
                                    obj = String.format("%02d:%02d:%02d", input/3600,(input % 3600) / 60,(input % 3600) % 60);
                                    //LOGGER.trace("*!*!*! is now :" + obj.toString());
                                }
                            }
                            catch (IllegalStateException e)
                            {
                                LOGGER.warn("Duration Check Error! Invalid / empty video duration");
                            }
                            catch (NumberFormatException e)
                            {
                                LOGGER.warn("Duration Check Error! Invalid / empty video duration");
                            }
                            catch (Exception e)
                            {
                                LOGGER.warn("Duration Check Error!", e);
                            }
                            //END DURATION CHECK AND SET



                            //THIS HANDLES REST OF NULL SET KEYS WHICH MAP TO PROPERTY VALUES
                            if (obj != null && !obj.equals(null) && !obj.equals("null"))
                            {
                                map.put(key, obj); //MAIN SET OF THE KEYS->VALUES FOR THIS VIDEO OBJECT
                            }
                            else
                            {

                                //TODO: Improvie this check, this is the handle for null object / string
                                //WE TAKE THESE NULL VALUES AS ACTUAL VALUES AND EXECUTE
                                if (x.equals("images"))
                                {
                                    //NULL ON IMAGES MEANS NULL SOURCES OF IMAGES
                                    newAsset.removeRendition("brc_poster.png");
                                    newAsset.removeRendition("brc_thumbnail.png");
                                }
                                else if (x.equals("schedule"))
                                {
                                    //NULL OBJECT OF SCHEDULE = EMPTY SCHEDULE METADATA
                                    if (assetmap.containsKey("onTime")) assetmap.remove("onTime");
                                    if (assetmap.containsKey("offTime")) assetmap.remove("offTime");
                                }
                                else if (x.equals("link"))
                                {
                                    //NULL LINK OBJECT MEANS EMPTY URL + TEXT METADATA
                                    if (map.containsKey("brc_link_text")) map.remove("brc_link_text");
                                    if (map.containsKey("brc_link_url")) map.remove("brc_link_url");
                                }
                                else
                                {
                                    //IF ANY OTHER ARE NULL AND WERE ACTIVE - ARE NOW EQUIVALENT
                                    if (map.containsKey(key)) map.remove(key);
                                }
                            }
                        }
                    }
                    else
                    {
                        LOGGER.trace("##HAS KEY BUT OBJECT IT LEADS TO IS NULL!");
                        LOGGER.trace("## HAS OBJECT WITH KEY : " + x +" ? "+innerObj.has(x) + " isnull? : "+ innerObj.get(x).equals(null));
                    }
                }




                //AFTER SETTING ALL THE METADATA - SET THE LAST UPDATE TIME

                //SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");


                map.put("brc_lastsync", com.coresecure.brightcove.wrapper.utils.JcrUtil.now2calendar());
                resourceResolver.commit();


                LOGGER.trace(">>UPDATED METADATA FOR VIDEO : [" + map.get("brc_id")+ "]");
            }
            else
            {
                LOGGER.error("BC ASSET UPDATE FAILED - ASSET IS NULL ! ERROR");
            }

            //MAIN TRY
        }
        catch (JSONException e)
        {
            LOGGER.error("JSON EXCEPTION", e);
        } catch (NullPointerException e) {
            LOGGER.error("NULL POINTER", e);
        }  catch (IOException e)
        {
            LOGGER.error("FILE NOT FOUND", e);
        }

    }

    public boolean updateRenditions(Asset _asset, Video currentVideo) throws JSONException
    {
        boolean result = false;
        Long asset_lastmod = _asset.getLastModified();
        Resource metadataRes = _asset.adaptTo(Resource.class).getChild("jcr:content/metadata");
        ModifiableValueMap brc_lastsync_map = metadataRes.adaptTo(ModifiableValueMap.class);
        Date brc_lastsync_time = brc_lastsync_map.get("brc_lastsync",new Date(0));

        ServiceUtil serviceUtil = new ServiceUtil(account_id);

        Rendition poster_rendition = _asset.getRendition("brc_poster.png");
        Rendition thumb_rendition = _asset.getRendition("brc_thumbnail.png");
        Rendition original_rendition = _asset.getRendition("original");

        JSONObject master = new JSONObject();


        //ORGINAL RENDITION - REPLACE CHECK -  RENDITION PROCESS
        if (currentVideo.id != null && original_rendition != null)
        {

            ValueMap original_map = original_rendition.getProperties();
            Date orig_lastmod_time = original_map.get("jcr:lastModified",new Date(0));
            LOGGER.trace("ORGINAL RENDITION LASTMOD: " + orig_lastmod_time + " VS LASTSYNC " + brc_lastsync_time);
            LOGGER.trace(""+original_map);

            if(orig_lastmod_time.compareTo(brc_lastsync_time)  > 0)
            {
                LOGGER.trace("UPLOADING ORIGINAL");
                //CHECK FOR Null BRC _ ID?
                InputStream original_rendition_is = _asset.getRendition("original") != null ? _asset.getRendition("original").getStream() : null;
                JSONObject s3_url_resp_original = serviceUtil.createAssetS3(currentVideo.id, _asset.getName() ,original_rendition_is);

                LOGGER.trace("S3RESP : " + s3_url_resp_original);
                LOGGER.trace("##CURRENT VIDEO " + currentVideo.toJSON());
                if (s3_url_resp_original != null && s3_url_resp_original.getBoolean("sent")) {
                    master = new JSONObject("{'master': {'url': '" + s3_url_resp_original.getString("api_request_url") + "'},'profile': 'high-resolution','capture-images': false}");
                }

            }

        }


        //POSTER RENDITION PROCESS
        if (poster_rendition != null)
        {
            ValueMap poster_map = poster_rendition.getProperties();
            Date poster_lastmod_time = poster_map.get("jcr:lastModified",new Date(0));
            LOGGER.trace("POSTER RENDITION LASTMOD: " + poster_lastmod_time);


            if(poster_lastmod_time.compareTo(brc_lastsync_time) > 0)
            {
                LOGGER.trace("UPLOADING POSTER");
                //CHECK FOR Null BRC _ ID?
                InputStream poster_rendition_is = _asset.getRendition("brc_poster.png") != null ? _asset.getRendition("brc_poster.png").getStream() : null;
                JSONObject s3_url_resp_poster = serviceUtil.createAssetS3(currentVideo.id,"brc_poster.png",poster_rendition_is);

                LOGGER.trace("S3RESP : " + s3_url_resp_poster);
                LOGGER.trace("##CURRENT VIDEO " + currentVideo.toJSON());
                //POSTER
                if (s3_url_resp_poster != null && s3_url_resp_poster.getBoolean("sent"))
                {
                    //IF SUCCESS - PUT
                    Poster poster = new Poster(s3_url_resp_poster.getString("api_request_url"));
                    master.put("poster", poster.toJSON());

                }
            }

        }


        //THUMBNAIL RENDITION PROCESS
        if (thumb_rendition != null) {

            LOGGER.trace("ASSET LASTMOD: " + asset_lastmod);

            ValueMap thumbnail_map = thumb_rendition.getValueMap(); //RETURNED NULL EACH TIME


            Date thumbnail_lastmod_time = thumbnail_map.get("jcr:lastModified", new Date(0));
            LOGGER.trace("THUMBNAIL RENDITION LASTMOD: " + thumbnail_lastmod_time);

            if (thumbnail_lastmod_time.compareTo(brc_lastsync_time) > 0)
            {
                LOGGER.trace("UPLOADING THUMBNAIL");
                InputStream thumbnail_rendition = _asset.getRendition("brc_thumbnail.png") != null ? _asset.getRendition("brc_thumbnail.png").getStream() : null;
                JSONObject s3_url_resp_thumbnail = serviceUtil.createAssetS3(currentVideo.id, "brc_thumbnail.png", thumbnail_rendition);

                if (s3_url_resp_thumbnail != null && s3_url_resp_thumbnail.getBoolean("sent")) {
                    //IF SUCCESS - PUT
                    Thumbnail thumbnail = new Thumbnail(s3_url_resp_thumbnail.getString("api_request_url"));
                    master.put("thumbnail", thumbnail.toJSON());
                }
            }
        }


        //UPLOAD INJEST SENDS THE IMAGE OBJECT TO THE  API - UPDATES THE METADATA TO POINT TO THE NEW URLS
        if (master.has("poster") || master.has("thumbnail") || master.has("master") ) {

            LOGGER.trace("master OBJ    ECT : " + master.toString());

            JSONObject response = brAPI.cms.uploadInjest(currentVideo.id, master);

            LOGGER.trace("response: " + response.toString());

            JSONObject api_resp = new JSONObject(response.getString("response"));
            if (api_resp.has("id")) {
                result = true;
            }
        } else {
            result = true;
        }



        return result;
    }

    public Video createVideo(String request, Asset asset, String aState)
    {
        LOGGER.trace("VIDEO CREATION CALLED FOR"  + asset.getName() + " req? : " + request);

        Resource assetRes = asset.adaptTo(Resource.class);
        LOGGER.trace("assetRes: " + assetRes.getPath());

        Resource metadataRes = assetRes.getChild("jcr:content/metadata");
        //SUB ASSETS
        Resource custom_node = metadataRes.getChild("brc_custom_fields")!= null ? metadataRes.getChild("brc_custom_fields") : null;

        //MAIN MAP
        ValueMap assetMap = assetRes.getChild("jcr:content").adaptTo(ValueMap.class);
        ValueMap map = metadataRes.adaptTo(ValueMap.class);

        //SUBMAPS
        ValueMap custom_node_map = custom_node != null ? custom_node.adaptTo(ValueMap.class) : null;

        //RELATED LINK
        String aUrl = map.get("brc_link_url", null);
        String aText  = map.get("brc_link_text", null);
        RelatedLink alink  = new RelatedLink( (aUrl != null ? (aText != null ? aText : "" ) : null),  (aText != null ? (aUrl != null ? aUrl : "" ): null));

        //SCHEDULE
        SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_24H_FULL_FORMAT);

        Date sched_start = assetMap.get("onTime", Date.class);
        Date sched_ends  = assetMap.get("offTime", Date.class);
        Schedule schedule = null;
        if(sched_start!=null || sched_ends != null)
        {

            schedule = new Schedule(sdf.format(sched_start), sdf.format(sched_ends) );
        }

        Geo geo = null; //NOT SUPPORTED

        //TAGS
        String[] tagsList = metadataRes.getValueMap().get("cq:tags",new String[]{});
        List<String> list = new ArrayList<String>(Arrays.asList(tagsList));
        tagsList = list.toArray(new String[0]);
        //REMOVE BRIGHTCOVE TAG BEFORE PUSH
        Collection<String> tags = JcrUtil.tagsToCollection(tagsList);
        list = null;


        //STO FROM LOCAL VIDEOS INITIALIZE THESE SO THAT YOU CAN SEND -- COULD COME FROM PROPERTIES VALUE MAP
        String name = map.get("dc:title", asset.getName());
        String id = map.get("brc_id", null);
        String referenceId = map.get("brc_reference_id", "");
        String shortDescription = map.get("brc_description","");
        String longDescription = map.get("brc_long_description","");
        String projection = "equirectangular".equals(map.get("brc_projection",""))? "equirectangular" : "";


        Map<String, Object> custom_fields = new HashMap();

        LOGGER.trace("###CUSTOM NODEMAP###");


        try
        {
            JSONObject custom_fields_obj = getCustomFields();
            JSONArray custom_fields_arr = custom_fields_obj.getJSONArray("custom_fields");
            for(int z = 0 ; z < custom_fields_arr.length() ; z ++ )
            {
                JSONObject current = custom_fields_arr.getJSONObject(z);
                custom_fields.put( current.getString("id"), custom_node_map.get(current.getString("id"),""));
            }

        }
        catch (Exception e)
        {
            LOGGER.error("REPO EXCEPTION " + e);
        }

        //economics enum initialization
        EconomicsEnum economics = EconomicsEnum.valueOf(map.get("brc_economics", "AD_SUPPORTED"));

        //ININTIALIZING WRAPPER OBJECTS

        //COMPLETE
        Boolean complete = map.get("brc_complete",false);

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
        LOGGER.trace("Video "+video.toString());
        LOGGER.trace(">>>>>>>>>>///>>>>>>>>>>");

        return video;
    }
}
