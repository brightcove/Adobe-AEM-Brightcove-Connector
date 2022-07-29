package com.coresecure.brightcove.wrapper.api;

import com.coresecure.brightcove.wrapper.objects.*;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.JsonReader;
import com.coresecure.brightcove.wrapper.utils.TextUtil;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by pablo.kropilnicki on 12/20/17.
 */
public class CmsAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmsAPI.class);

    private Account account;
    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;
    private static final String DEFAULT_ENCODING = "UTF-8";

    //    CMS
    public CmsAPI(Account aAccount){ LOGGER.debug("CmsAPI Init aAccount {}" , aAccount.getAccount_ID()); account= aAccount;}


    //GET PLAYERS API
    public JSONObject getPlayers() {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/players";
            try {
                String response = account.platform.getPLAYERS_API(targetURL, Constants.EMPTY_URLPARAMS, headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e) {
                LOGGER.error(e.getClass().getName(), e);
            }

        }
        return json;
    }

    //postDIRequest_API
    public JSONObject uploadInjest(String videoId, JSONObject payload) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null)
        {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + videoId + Constants.INGEST_REQUEST;
            try
            {
                LOGGER.trace("UploadInjestPayload: {}", payload);
                String response = account.platform.postDIRequest_API(targetURL, payload.toString(), headers);
                if (response != null && !response.isEmpty()) json.put(Constants.RESPONSE,response);
            }
            catch (JSONException e)
            {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //postDIRequest_API
    public JSONObject requestIngestURL(String videoId, String profile, String master, boolean getImages) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + videoId + Constants.INGEST_REQUEST;
            LOGGER.trace("requestIngestURL: {}", targetURL);
            LOGGER.trace("ingest_profile: {} ",  profile);

            try {
                //Support for profile changed as per - 12961
                JSONObject payload  = new JSONObject();
                JSONObject master_obj = new JSONObject();
                master_obj.put("url", master);
                payload.put("master", master_obj);
                payload.put("capture-images", getImages);
                if(!TextUtil.isEmpty(profile))
                {
                    payload.put("profile", profile);
                }
                String response = account.platform.postDIRequest_API(targetURL, payload.toString(1), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //postDI_API
    public JSONObject createIngest(Video aVideo, Ingest aIngest) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + aVideo.id + Constants.INGEST_REQUEST;
            try {
                String response = account.platform.postDI_API(targetURL, aIngest.toJSON().toString(1), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //getDI_API
    public JSONObject getIngestURL(String videoId, String filename) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + videoId + "/upload-urls/"+filename;
            LOGGER.trace("getIngestURL: {}", targetURL);
            try {
                String response = account.platform.getDI_API(targetURL, "", headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //putAPI
    public JSONObject moveVideoToFolder(String videoId, String folderId) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL =
                Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/folders/" +
                folderId + "/videos/" + videoId;
            try {
                String response = account.platform.putAPI(targetURL, headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        LOGGER.trace("createVideo: {} Response: {}", json);
        return json;
    }

    //deleteAPI
    public JSONObject removeVideoFromFolder(String videoId, String folderId) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL =
                Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/folders/" +
                folderId + "/videos/" + videoId;
            try {
                String response = account.platform.deleteAPI(targetURL, headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        LOGGER.trace("createVideo: {} Response: {}", json);
        return json;
    }

    //deleteAPI
    public JSONObject deletePlaylist(String playlistId) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL =
                Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/playlists/" +
                playlistId;
            try {
                String response = account.platform.deleteAPI(targetURL, headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        LOGGER.trace("createVideo: {} Response: {}", json);
        return json;
    }

    //postAPI
    public JSONObject createVideo(Video aVideo) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/videos";
            try {

                JSONObject videoObj = aVideo.toJSON();
                videoObj.remove(Constants.ACCOUNT_ID);
                String response = account.platform.postAPI(targetURL, aVideo.toJSON().toString(1), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        LOGGER.trace("createVideo: {} Response: {}",aVideo , json);
        return json;
    }

    //PatchAPI
    public JSONObject updateVideo(Video aVideo) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH+aVideo.id;
            try {
                LOGGER.debug("targetURL: {}",targetURL);
                JSONObject video = aVideo.toJSON();
                LOGGER.trace("UPDATE VIDEO DATA OBJECT: {} ", video);
                video.remove(Constants.ID);
                video.remove(Constants.ACCOUNT_ID);

                String response = account.platform.patchAPI(targetURL, video.toString(1), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //PatchAPI
    public JSONObject updatePlaylist(String playlistId, String[] videos) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/playlists"
                + "/" + playlistId;
            try {
                LOGGER.debug("targetURL: {}", targetURL);
                JSONObject request = new JSONObject();
                ArrayList<String> videoArray = new ArrayList<String>();
                for (String item : videos) {
                    videoArray.add(item);
                }
                request.put("video_ids", new JSONArray(videoArray));
                LOGGER.info("updatePlaylistParams: {}", request.toString(1));
                String response = account.platform.patchAPI(targetURL, request.toString(1), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //deleteAPI
    public JSONObject deleteVideo(String videoID) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + videoID;
            try {
                String response = account.platform.deleteAPI(targetURL, videoID, headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
                LOGGER.debug("deleteVideo response json: {}" , json);
            } catch (IOException e) {
                try {
                    json.put("error_code", "IOException");
                    json.put("message", e.getMessage());
                }catch (JSONException ee) {
                    LOGGER.error(ee.getClass().getName(), ee);
                }
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e) {
                try {
                    json.put("error_code", "JSONException");
                    json.put("message", e.getMessage());
                }catch (JSONException ee) {
                    LOGGER.error(ee.getClass().getName(), ee);
                }
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //getAPI
    public JSONObject getVideo(String id) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + id;
            json = getJSONObjectResponse(targetURL, Constants.EMPTY_URLPARAMS, headers);
        }
        return json;
    }

    //getAPI
    public JSONObject getPlaylistsCount() {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null)
        {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/counts/playlists";
            json = getJSONObjectResponse(targetURL, Constants.EMPTY_URLPARAMS, headers);
        }
        return json;
    }

    //postAPI
    public JSONObject createPlaylist(Playlist aPlaylist) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null)
        {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/playlists";
            try {
                LOGGER.debug("Playlist {}", aPlaylist.toJSON().toString());
                String response = account.platform.postAPI(targetURL, aPlaylist.toJSON().toString(1), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            } catch (JSONException e)
            {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        LOGGER.trace("createPlaylist: {} Response: {}", aPlaylist, json);
        return json;
    }

    public JSONObject getVideosCount(String q) {
        return getVideosCount(q, true);
    }
    //getAPI
    public JSONObject getVideosCount(String q, boolean dam_only) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null)
        {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/counts/videos";
            try {
                //String urlParameters = "q=%2Bstate:ACTIVE" + (dam_only ? "%20%2Dtags:AEM_NO_DAM" :
                String urlParameters = "q=" + (dam_only ? "%20%2Dtags:AEM_NO_DAM" :
                "") + (q != null && !q.isEmpty()  ? Constants.WHITESPACE_FIX+URLEncoder.encode(q, DEFAULT_ENCODING):"");
                json = getJSONObjectResponse(targetURL, urlParameters, headers);
            }
            catch (UnsupportedEncodingException e)
            {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //getAPI
    public JSONArray  getVideoSources(String id) {
        JSONArray json = new JSONArray();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null)
        {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + id + "/sources";
            json = getJSONArrayResponse(targetURL, Constants.EMPTY_URLPARAMS, headers);
        }
        return json;
    }

    //getAPI
    public JSONObject getVideoImagesByRef(String refID) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH+Constants.REFERENCE_SEARCH_FIELD_TAG + refID + "/images";
            json = getJSONObjectResponse(targetURL, Constants.EMPTY_URLPARAMS, headers);
        }
        return json;
    }

    //getAPI
    public JSONArray  getVideoSourcesByRef(String refID) {
        JSONArray json = new JSONArray();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + Constants.REFERENCE_SEARCH_FIELD_TAG + refID + "/sources";
            json = getJSONArrayResponse(targetURL, Constants.EMPTY_URLPARAMS, headers);
        }
        return json;
    }

    //getAPI
    public JSONObject getVideoByRef(String refID) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + Constants.REFERENCE_SEARCH_FIELD_TAG + refID;
            json = getJSONObjectResponse(targetURL, Constants.EMPTY_URLPARAMS, headers);
        }
        return json;
    }

    //getAPI
    public JSONObject getCustomFields() {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/video_fields";
            json = getJSONObjectResponse(targetURL, Constants.EMPTY_URLPARAMS, headers);
        }
        return json;
    }

    //getAPI
    public JSONObject getVideoImages(String id) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType()+" "+authToken.getToken());
            String targetURL =Constants.ACCOUNTS_API_PATH+account.getAccount_ID()+Constants.VIDEOS_API_PATH+id+"/images";
            json =  getJSONObjectResponse(targetURL, Constants.EMPTY_URLPARAMS , headers);
        }
        return json;
    }

    //No network call
    public JSONArray addThumbnail(JSONArray input) {
        JSONArray videos = new JSONArray();
        try {
            for (int i = 0; i < input.length(); i++) {
                JSONObject video = input.getJSONObject(i);
                if (video.has(Constants.ID)) {
                    if (video.has(Constants.IMAGES) && video.getJSONObject(Constants.IMAGES).has(Constants.THUMBNAIL)) {
                        video.put(Constants.THUMBNAIL_URL, video.getJSONObject(Constants.IMAGES).getJSONObject(Constants.THUMBNAIL).getString(Constants.SRC));
                    } else {
                        video.put(Constants.THUMBNAIL_URL, Constants.DEFAULT_THUMBNAIL_LOCATION);
                    }
                    videos.put(video);
                }
            }
        } catch (JSONException je) {
            LOGGER.error(je.getClass().getName(), je);
        }
        return videos;
    }


    public JSONArray getVideos(String q, int limit, int offset, String sort) {
        return getVideos(q, limit, offset, sort, true, false);
    }

    //ACTUAL GET VIDEOS FUNCTION
    //DO NOT TOUCH  - getAPI Adaptation - IGNORES NON ACTIVE - IGNORES VIDEOS WITH "AEM_NO_DAM" TAG
    public JSONArray getVideos(String q, int limit, int offset, String sort, boolean dam_only, boolean clips_only) {
        JSONArray json = new JSONArray();
        LOGGER.debug("account: {}" , account.getAccount_ID());
        TokenObj authToken = account.getLoginToken();
        LOGGER.debug("authToken: {}" , authToken.getToken());
        try {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            q = (q != null) ? URLEncoder.encode(q, DEFAULT_ENCODING) : "";
            // String urlParameters = "q=%2Bstate:ACTIVE" + (dam_only ? "%20%2Dtags:AEM_NO_DAM" : "")
            String urlParameters = "q=" + (dam_only ? "%20%2Dtags:AEM_NO_DAM" : "") + (!q.isEmpty()  ? Constants.WHITESPACE_FIX+URLEncoder.encode(q, DEFAULT_ENCODING).replace("%253A", ":").replaceAll("%252F", "/"):"") + "&limit=" + limit + "&offset=" + offset + (sort != null ? "&sort=" + sort:"") + (clips_only ? "&is_clip:true":"");
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/videos";
            LOGGER.debug("urlParameters: {}" , urlParameters);
            String response = account.platform.getAPI(targetURL, urlParameters, headers);
            if (!response.isEmpty()) {
                json = JsonReader.readJsonArrayFromString(response);
                LOGGER.debug(Constants.RESPONSE, response);
//                LOGGER.trace("json {}", json);
            } else if (!q.isEmpty() && NumberUtils.isNumber(q)) {
                json.put(getVideo(q));
            }
        } catch (IOException e) {
            LOGGER.error(e.getClass().getName(), e);
        } catch (JSONException e) {
            LOGGER.error(e.getClass().getName(), e);
        } catch (NullPointerException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return json;
    }
    //GET VIDEO OVERLOADS
    public JSONArray getVideos() {
        return getVideos(Constants.EMPTY_Q_PARAM);
    }
    public JSONArray getVideos(String q) {
        return getVideos(q, DEFAULT_LIMIT);
    }
    public JSONArray getVideos(String q, String sort) {
        return getVideos(q, DEFAULT_LIMIT, DEFAULT_OFFSET, sort);
    }
    public JSONArray getVideos(String q, String sort, int limit) {
        return getVideos(q, limit, DEFAULT_OFFSET, sort);
    }
    public JSONArray getVideos(String q, int limit) {
        return getVideos(q, limit, DEFAULT_OFFSET);
    }
    public JSONArray getVideos(String q, int limit, int offset) {
        return getVideos(q, limit, offset, Constants.EMPTY_SORT_PARAM);
    }
    public JSONArray getVideos(int limit, int offset, String sort) {
        return getVideos(Constants.EMPTY_Q_PARAM, limit, offset, sort);
    }




    //getAPI
    public JSONObject getPlaylist(String refID) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/playlists/" + refID;
            json = getJSONObjectResponse(targetURL, Constants.EMPTY_URLPARAMS, headers);
        }
        return json;
    }

    //getAPI
    public JSONArray getVideosInPlaylist(String ID) {
        JSONArray json = new JSONArray();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/playlists/" + ID + "/videos";
            json = getJSONArrayResponse(targetURL, Constants.EMPTY_URLPARAMS, headers);
        }
        return json;
    }

    public JSONArray  getPlaylists() {
        return getPlaylists(DEFAULT_LIMIT,  DEFAULT_OFFSET,  Constants.NAME);
    }
    public JSONArray  getPlaylists(int limit, int offset, String sort) {
        return getPlaylists( null,  limit,  offset,  sort);
    }
    public JSONArray  getPlaylists(String q, int limit, int offset, String sort) {
        JSONArray json = new JSONArray();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/playlists";
            try {
                q = (q != null) ? URLEncoder.encode(q, DEFAULT_ENCODING) : "";
                String urlParameters = "q=" + URLEncoder.encode(q, DEFAULT_ENCODING) + "&limit=" + limit + "&offset=" + offset + "&sort=" + sort;
                json = getJSONArrayResponse(targetURL, urlParameters, headers);
            } catch (UnsupportedEncodingException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    public JSONObject getExperiences(String q, String sort) {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/experiences";
            try {
                q = (q != null) ? URLEncoder.encode(q, DEFAULT_ENCODING) : "";
                String urlParameters = "q=" + URLEncoder.encode(q, DEFAULT_ENCODING) + "&sort=" + sort;
                json = getExperiencesJSONObjectResponse(targetURL, urlParameters, headers);
            } catch (UnsupportedEncodingException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    public JSONArray getVideosInFolder(String folder, int offset) {
        JSONArray json = new JSONArray();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/folders/" + folder + "/videos";
            try {
                String urlParameters = "offset=" + offset;
                json = getJSONArrayResponse(targetURL, urlParameters, headers);
            } catch (Exception e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    public JSONArray getVideosWithLabel(String label, int offset) {
        return getVideos("labels:" + label);
    }

    public JSONArray getOnlyClipVideos(String q, int limit, int offset, String sort) {
        return getVideos(q, limit, offset, sort, true, true);
    }

    public JSONArray getFolders(int limit, int offset) {
        JSONArray json = new JSONArray();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/folders";
            try {
                String urlParameters = "offset=" + offset;
                json = getJSONArrayResponse(targetURL, urlParameters, headers);
            } catch (Exception e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    public JSONObject getLabels() {
        JSONObject json = new JSONObject();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/labels";
            try {
                String urlParameters = "";
                json = getJSONObjectResponse(targetURL, urlParameters, headers);
            } catch (Exception e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    public JSONObject getExperiencesJSONObjectResponse(String targetURL, String urlParameters, Map<String, String> headers) {
        JSONObject json = new JSONObject();
        try
        {
            String response = account.platform.getExperiencesAPI(targetURL, urlParameters, headers);
            if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
        }
        catch (IOException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        catch (JSONException e)
        {
            LOGGER.error(e.getClass().getName(), e);
        }
        return json;

    }

    //GET API
    public JSONObject getJSONObjectResponse(String targetURL, String urlParameters, Map<String, String> headers) {
        JSONObject json = new JSONObject();
        try
        {
            String response = account.platform.getAPI(targetURL, urlParameters, headers);
            if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
        }
        catch (IOException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        catch (JSONException e)
        {
            LOGGER.error(e.getClass().getName(), e);
        }
        return json;
    }

    public JSONArray getJSONArrayResponse(String targetURL, String urlParameters, Map<String, String> headers) {
        JSONArray json = new JSONArray();
        try
        {
            String response = account.platform.getAPI(targetURL, urlParameters, headers);
            if (response != null && !response.isEmpty()) json = JsonReader.readJsonArrayFromString(response);
        }
        catch (IOException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        catch (JSONException e)
        {
            LOGGER.error(e.getClass().getName(), e);
        }
        return json;

    }

    public JSONArray getExperiencesJSONArrayResponse(String targetURL, String urlParameters, Map<String, String> headers) {
        JSONArray json = new JSONArray();
        try
        {
            String response = account.platform.getExperiencesAPI(targetURL, urlParameters, headers);
            if (response != null && !response.isEmpty()) json = JsonReader.readJsonArrayFromString(response);
        }
        catch (IOException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        catch (JSONException e)
        {
            LOGGER.error(e.getClass().getName(), e);
        }
        return json;

    }

}