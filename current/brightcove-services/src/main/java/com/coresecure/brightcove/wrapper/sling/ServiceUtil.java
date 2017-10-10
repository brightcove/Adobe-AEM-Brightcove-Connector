package com.coresecure.brightcove.wrapper.sling;

import com.coresecure.brightcove.wrapper.BrightcoveAPI;
import com.coresecure.brightcove.wrapper.objects.Video;
import com.coresecure.brightcove.wrapper.utils.S3UploadUtil;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ServiceUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceUtil.class);


    public static int DEFAULT_LIMIT = 100;
    private BrightcoveAPI brAPI = null;

    public static ConfigurationGrabber getConfigurationGrabber() {
        BundleContext bundleContext = FrameworkUtil.getBundle(ConfigurationGrabber.class).getBundleContext();
        return (ConfigurationGrabber) bundleContext.getService(bundleContext.getServiceReference(ConfigurationGrabber.class.getName()));
    }

    public static Cookie getAccountCookie(SlingHttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        Map<String, Cookie> cookiesMap = new TreeMap<String, Cookie>();
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++) {

                LOGGER.trace("Cookies: "+cookies[i].getName());
                cookiesMap.put(cookies[i].getName(), cookies[i]);
            }
            ;
        }
        return cookiesMap.get("brc_act");
    }

    public static String getAccountFromCookie(SlingHttpServletRequest request) {
        Cookie account = getAccountCookie(request);

        LOGGER.trace("getAccountFromCookie: "+ ((account != null) ? account.getValue() : "" ));




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

    public ServiceUtil(String account_id) {
        brAPI = new BrightcoveAPI(account_id);
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
        if(!apiResult.has("error_code")){
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
        boolean fullscroll = !(limit >0);
        String result = getList(false, offset, limit, fullscroll, querystr);
        return result;
    }
    public String searchVideo(String querystr, int offset, int limit, String sort) {
        //Fixed the performance issue at the component authoring side.
        //String result = getList(false, offset, limit, true, querystr);
        boolean fullscroll = !(limit >0);
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

    public String getListSideMenu(String limits)
    {
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



    public JSONObject updateVideo(Video aVideo)
    {

        JSONObject result = new JSONObject();
        try
        {
            JSONObject videoItem = brAPI.cms.updateVideo(aVideo);
            try {
                if (videoItem != null && videoItem.has("id"))
                {
                    String newVideoId = videoItem.getString("id");
                    LOGGER.info("New video id: '" + newVideoId + "'.");
                    result.put("videoid", newVideoId);
                    result.put("sent", true);
                }
                else
                {
                    result.put("error", "updateVideo Error");
                    result.put("sent", false);
                }

            } catch (Exception exIngest) {
                LOGGER.error("updateVideo",exIngest);
                result.put("error", "updateVideo Exception");
                result.put("sent", false);
            }
        }
        catch (JSONException e)
        {
            LOGGER.error("updateVideo", e);
        }
        return result;
    }


    public JSONObject createVideo(Video aVideo, String ingestURL, String ingestProfile){
        JSONObject result = new JSONObject();
        try {
            JSONObject videoItem = brAPI.cms.createVideo(aVideo);
            String newVideoId = videoItem.getString("id");
            JSONObject videoIngested = new JSONObject();
            try {
                com.coresecure.brightcove.wrapper.objects.Ingest ingest = new com.coresecure.brightcove.wrapper.objects.Ingest(ingestProfile, ingestURL);
                videoIngested = brAPI.cms.createIngest(new com.coresecure.brightcove.wrapper.objects.Video(videoItem), ingest);
                if (videoIngested != null && videoIngested.has("id"))
                {
                    LOGGER.info("New video id: '" + newVideoId + "'.");
                    result.put("videoid", newVideoId);
                    result.put("output", videoIngested);
                }
                else
                {
                    result.put("error", "createIngest Error");
                    brAPI.cms.deleteVideo(newVideoId);
                }

            } catch (Exception exIngest) {
                LOGGER.error("createVideo",exIngest);
                result.put("error", "createIngest Exception");
                brAPI.cms.deleteVideo(newVideoId);
            }
        }
        catch (JSONException e)
        {
            LOGGER.error("JSON Error - createVideo", e);
        }
        return result;
    }
    public JSONObject createVideoS3(Video aVideo, String filename, InputStream is){
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
                    result.put("videoid",newVideoId);
                    result.put("object_key", videoIngested.get("object_key"));
                    result.put("api_request_url", videoIngested.get("api_request_url"));
                    result.put("signed_url", videoIngested.get("signed_url"));
                    boolean sent = S3UploadUtil.uploadToUrl(new URL(videoIngested.getString("signed_url")),is);

                    result.put("sent",sent);
                    if (!sent) {
                        brAPI.cms.deleteVideo(newVideoId);
                    } else {
                        result.put("job",brAPI.cms.requestIngestURL(newVideoId,"high-resolution", videoIngested.getString("api_request_url"),true));
                    }
                } else {
                    LOGGER.trace("createIngest: "+videoIngested.toString(1));

                    result.put("error", "createIngest Error");
                    brAPI.cms.deleteVideo(newVideoId);
                }

            } catch (Exception exIngest) {
                LOGGER.error("createIngest",exIngest);
                result.put("error", "createIngest Exception");
                brAPI.cms.deleteVideo(newVideoId);
            }
            LOGGER.trace("result: "+result.toString(1));

        } catch (JSONException e) {
            LOGGER.error("createVideo", e);
        }

        return result;
    }

    public JSONObject createAssetS3(String newVideoId, String filename, InputStream is){
        JSONObject result = new JSONObject();
        try {
            JSONObject assetIngested = new JSONObject();
            try {
                assetIngested = brAPI.cms.getIngestURL(newVideoId, filename);
                if (assetIngested != null && assetIngested.has("bucket")) {
                    LOGGER.info("New video id: '" + newVideoId + "'.");
                    result.put("bucket", assetIngested.get("bucket"));
                    result.put("videoid",newVideoId);
                    result.put("object_key", assetIngested.get("object_key"));
                    result.put("api_request_url", assetIngested.get("api_request_url"));
                    result.put("signed_url", assetIngested.get("signed_url"));
                    boolean sent = S3UploadUtil.uploadToUrl(new URL(assetIngested.getString("signed_url")),is);
                    result.put("sent",sent);
                } else {
                    LOGGER.trace("createIngest: "+assetIngested.toString(1));
                    result.put("error", "createIngest Error");
                }

            } catch (Exception exIngest) {
                LOGGER.error("createIngest",exIngest);
                result.put("error", "createIngest Exception");
                brAPI.cms.deleteVideo(newVideoId);
            }
            LOGGER.trace("result: "+result.toString(1));

        } catch (JSONException e) {
            LOGGER.error("createAssetS3", e);
        }

        return result;
    }


    //FindAllPlaylists(String readToken, Integer pageSize, Integer pageNumber, SortByTypeEnum sortBy, SortOrderTypeEnum sortOrderType, EnumSet<VideoFieldEnum> videoFields, Set<String> customFields, EnumSet<PlaylistFieldEnum> playlistFields)
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
}
