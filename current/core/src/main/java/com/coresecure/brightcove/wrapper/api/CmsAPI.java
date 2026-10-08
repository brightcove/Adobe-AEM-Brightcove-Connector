package com.coresecure.brightcove.wrapper.api;

import com.coresecure.brightcove.wrapper.utils.JsonUtil;
import com.coresecure.brightcove.wrapper.objects.*;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.HttpServices;
import com.coresecure.brightcove.wrapper.utils.JsonReader;
import com.coresecure.brightcove.wrapper.utils.LogRedactor;
import com.coresecure.brightcove.wrapper.utils.TextUtil;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
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
    public ObjectNode getPlayers() {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
            }

        }
        return json;
    }

    //postDIRequest_API
    public ObjectNode uploadInjest(String videoId, ObjectNode payload) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null)
        {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + videoId + Constants.INGEST_REQUEST;
            LOGGER.trace("UploadInjestPayload: {}", payload);
            String response = account.platform.postDIRequest_API(targetURL, payload.toString(), headers);
            if (response != null && !response.isEmpty()) json.put(Constants.RESPONSE, response);
        }
        return json;
    }

    //postDIRequest_API
    public ObjectNode requestIngestURL(String videoId, String profile, String master, boolean getImages) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + videoId + Constants.INGEST_REQUEST;
            LOGGER.trace("requestIngestURL: {}", targetURL);
            LOGGER.trace("ingest_profile: {} ",  profile);

            try {
                //Support for profile changed as per - 12961
                ObjectNode payload = JsonNodeFactory.instance.objectNode();
                ObjectNode master_obj = JsonNodeFactory.instance.objectNode();
                master_obj.put("url", master);
                payload.set("master", master_obj);
                payload.put("capture-images", getImages);
                if(!TextUtil.isEmpty(profile))
                {
                    payload.put("profile", profile);
                }
                String response = account.platform.postDIRequest_API(targetURL, JsonUtil.pretty(payload), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //postDI_API
    public ObjectNode createIngest(Video aVideo, Ingest aIngest) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + aVideo.id + Constants.INGEST_REQUEST;
            try {
                String response = account.platform.postDI_API(targetURL, JsonUtil.pretty(aIngest.toJSON()), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //getDI_API
    public ObjectNode getIngestURL(String videoId, String filename) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
            }
        }
        return json;
    }
    
    //postAPI
    public ObjectNode createFolder(String title) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/folders/";
            try {
                String payload = "{ \"name\": \"" + title + "\" }";
                String response = account.platform.postAPI(targetURL, payload, headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        LOGGER.trace("createBlankPlaylist: {} Response: {}", title);
        return json;
    }

    //putAPI
    public ObjectNode moveVideoToFolder(String videoId, String folderId) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
            }
        }
        LOGGER.trace("createVideo: {} Response: {}", json);
        return json;
    }

    //deleteAPI
    public ObjectNode removeVideoFromFolder(String videoId, String folderId) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
            }
        }
        LOGGER.trace("createVideo: {} Response: {}", json);
        return json;
    }

    //deleteAPI
    public ObjectNode deletePlaylist(String playlistId) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
            }
        }
        LOGGER.trace("createVideo: {} Response: {}", json);
        return json;
    }
    
    //postAPI
    public ObjectNode createBlankPlaylist(String title) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/playlists";
            try {
                String payload = "{ \"name\": \"" + title + "\" }";
                String response = account.platform.postAPI(targetURL, payload, headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        LOGGER.trace("createBlankPlaylist: {} Response: {}", title);
        return json;
    }

    //postAPI
    public ObjectNode createVideo(Video aVideo) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/videos";
            try {
                ObjectNode videoObj = aVideo.toJSON();
                videoObj.remove(Constants.ACCOUNT_ID);
                String response = account.platform.postAPI(targetURL, JsonUtil.pretty(videoObj), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        LOGGER.trace("createVideo: {} Response: {}",aVideo , json);
        return json;
    }

    //PatchAPI
    public ObjectNode updateVideo(Video aVideo) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH+aVideo.id;
            try {
                LOGGER.debug("targetURL: {}",targetURL);
                ObjectNode video = aVideo.toJSON();
                LOGGER.trace("UPDATE VIDEO DATA OBJECT: {} ", video);
                video.remove(Constants.ID);
                video.remove(Constants.ACCOUNT_ID);

                String response = account.platform.patchAPI(targetURL, JsonUtil.pretty(video), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (Exception e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //PatchAPI
    public ObjectNode updatePlaylist(String playlistId, String[] videos) {
        return updatePlaylist(playlistId, videos, null);
    }

    //PatchAPI — supports optional name update and null videos (for smart playlists)
    public ObjectNode updatePlaylist(String playlistId, String[] videos, String name) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/playlists"
                + "/" + playlistId;
            try {
                LOGGER.debug("targetURL: {}", targetURL);
                ObjectNode request = JsonNodeFactory.instance.objectNode();
                if (videos != null) {
                    ArrayNode videoArray = JsonNodeFactory.instance.arrayNode();
                    for (String item : videos) {
                        videoArray.add(item);
                    }
                    request.set("video_ids", videoArray);
                }
                if (name != null && !name.isEmpty()) {
                    request.put("name", name);
                }
                LOGGER.info("updatePlaylistParams: {}", JsonUtil.pretty(request));
                String response = account.platform.patchAPI(targetURL, JsonUtil.pretty(request), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    public ObjectNode updateLabels(String videoId, String[] labels) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/videos"
                + "/" + videoId;
            try {
                LOGGER.debug("targetURL: {}", targetURL);
                ObjectNode request = JsonNodeFactory.instance.objectNode();
                ArrayNode labelArray = JsonNodeFactory.instance.arrayNode();
                for (String item : labels) {
                    labelArray.add(item);
                }
                request.set("labels", labelArray);
                LOGGER.info("updateVideoParams: {}", JsonUtil.pretty(request));
                HttpServices.PatchResponse patch = account.platform.patchAPIFull(targetURL, JsonUtil.pretty(request), headers);
                json = toUpdateResult(patch);
            } catch (Exception e) {
                // The exception text can name the proxy host:port: log it, never return it.
                LOGGER.error("update_labels failed for video {}", videoId, e);
                json = JsonNodeFactory.instance.objectNode();
                json.put("error_code", 500);
                json.put("message", "Could not update the labels");
            }
        } else {
            json.put("error_code", 401);
            json.put("message", "No auth token");
        }
        return json;
    }

    /**
     * Maps a CMS PATCH outcome to the shape the admin UI reads: the updated video object on
     * success, otherwise {@code {"error_code", "message"}}. The UI treats any response without
     * {@code error_code} as saved, so every failure must carry one: no response at all (refused,
     * proxy denied, timeout) is 502, an HTTP error keeps the CMS's own code and message when it
     * sent them, and the HTTP status otherwise. Anything else that is not a video object (an
     * empty 2xx/3xx body, 204, a redirect, {@code []}, a JSON scalar) is a 502, never {@code {}}.
     */
    static ObjectNode toUpdateResult(HttpServices.PatchResponse patch) throws IOException {
        boolean httpError = patch.status >= 400;
        boolean success = patch.status >= 200 && patch.status < 300;
        String body = patch.body;
        if (body == null || body.trim().isEmpty()) {
            if (httpError) {
                return error(patch.status, "Brightcove returned HTTP " + patch.status);
            }
            if (patch.status == 0) {
                // The failure text can name the proxy host:port; it is logged where it was caught.
                LOGGER.warn("update_labels: no response from Brightcove ({})", patch.failure);
                return error(502, "No response from Brightcove");
            }
            return unexpected(patch.status, "empty body");
        }
        com.fasterxml.jackson.databind.JsonNode parsed;
        try {
            parsed = JsonReader.readJsonTree(body);
        } catch (Exception notJson) {
            return error(httpError ? patch.status : 502, "Unreadable response from Brightcove");
        }
        com.fasterxml.jackson.databind.JsonNode first = parsed.isArray() && parsed.size() > 0 ? parsed.get(0) : parsed;
        if (first != null && first.isObject()) {
            boolean cmsError = first.has("error_code") && !first.get("error_code").isNull();
            if (cmsError || httpError || parsed.isArray()) {
                // CMS's own code and message when it returns one; HTTP status (422 for a
                // bare validation array with no status) otherwise.
                int fallbackStatus = httpError ? patch.status : 422;
                ObjectNode json = JsonNodeFactory.instance.objectNode();
                if (cmsError) {
                    json.put("error_code", first.get("error_code").asText());
                } else {
                    json.put("error_code", fallbackStatus);
                }
                json.put("message", first.has("message") ? first.get("message").asText()
                        : "Brightcove returned HTTP " + fallbackStatus);
                if (cmsError && httpError) {
                    json.put("status", patch.status);
                }
                return json;
            }
            if (success) {
                return (ObjectNode) first;
            }
            return unexpected(patch.status, "not a success status");
        }
        if (httpError) {
            return error(patch.status, "Brightcove returned HTTP " + patch.status);
        }
        // 200 [], a JSON scalar, a 3xx: none of them is the updated video.
        return unexpected(patch.status, parsed.isArray() ? "empty array" : "not a video object");
    }

    private static ObjectNode unexpected(int status, String what) {
        return error(502, "Unexpected response from Brightcove (HTTP " + status + ", " + what + ")");
    }

    private static ObjectNode error(int code, String message) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("error_code", code);
        json.put("message", message);
        return json;
    }

    //deleteAPI
    public ObjectNode deleteVideo(String videoID) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
                json.put("error_code", "IOException");
                json.put("message", e.getMessage());
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //getAPI
    public ObjectNode getVideo(String id) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
    public ObjectNode getPlaylistsCount() {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
    public ObjectNode createPlaylist(Playlist aPlaylist) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null)
        {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/playlists";
            try {
                LOGGER.debug("Playlist {}", aPlaylist.toJSON().toString());
                String response = account.platform.postAPI(targetURL, JsonUtil.pretty(aPlaylist.toJSON()), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        LOGGER.trace("createPlaylist: {} Response: {}", aPlaylist, json);
        return json;
    }

    public ObjectNode createLabel(String label) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null)
        {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/labels";
            try {
                LOGGER.debug("Label {}", label.toString());
                String response = account.platform.postAPI(targetURL, "{ \"path\": \"" + label + "\" }", headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        LOGGER.trace("createLabel: {} Response: {}", label, json);
        return json;
    }

    public ObjectNode getVideosCount(String q) {
        return getVideosCount(q, true);
    }
    //getAPI
    public ObjectNode getVideosCount(String q, boolean dam_only) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null)
        {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/counts/videos";
            String tagParam = getTagParam(dam_only);
            try {
            	String urlParameters = "q=" + tagParam + (q != null && !q.isEmpty()  ? Constants.WHITESPACE_FIX+URLEncoder.encode(q, DEFAULT_ENCODING):"");
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
    public ArrayNode getVideoSources(String id) {
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
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
    public ObjectNode getVideoImagesByRef(String refID) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
    public ArrayNode getVideoSourcesByRef(String refID) {
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
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
    public ObjectNode getVideoByRef(String refID) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
    public ObjectNode getCustomFields() {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
    public ObjectNode getVideoImages(String id) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
    public ArrayNode addThumbnail(ArrayNode input) {
        ArrayNode videos = JsonNodeFactory.instance.arrayNode();
        try {
            for (int i = 0; i < input.size(); i++) {
                ObjectNode video = (ObjectNode) input.get(i);
                if (video.has(Constants.ID)) {
                    if (video.has(Constants.IMAGES) && ((ObjectNode) video.get(Constants.IMAGES)).has(Constants.THUMBNAIL)) {
                        com.fasterxml.jackson.databind.JsonNode thumbNode = ((ObjectNode) video.get(Constants.IMAGES)).get(Constants.THUMBNAIL);
                        String srcUrl = (thumbNode != null && thumbNode.has(Constants.SRC) && !thumbNode.get(Constants.SRC).isNull())
                                ? thumbNode.get(Constants.SRC).asText()
                                : Constants.DEFAULT_THUMBNAIL_LOCATION;
                        video.put(Constants.THUMBNAIL_URL, srcUrl);
                    } else {
                        video.put(Constants.THUMBNAIL_URL, Constants.DEFAULT_THUMBNAIL_LOCATION);
                    }
                    videos.add(video);
                }
            }
        } catch (Exception e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return videos;
    }


    public ArrayNode getVideos(String q, int limit, int offset, String sort) {
        return getVideos(q, limit, offset, sort, true, false);
    }

    //ACTUAL GET VIDEOS FUNCTION
    //DO NOT TOUCH  - getAPI Adaptation - IGNORES NON ACTIVE - IGNORES VIDEOS WITH "AEM_NO_DAM" TAG
    public ArrayNode getVideos(String q, int limit, int offset, String sort, boolean dam_only, boolean clips_only) {
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
        LOGGER.debug("account: {}" , account.getAccount_ID());
        TokenObj authToken = account.getLoginToken();
        LOGGER.debug("authToken: {}", LogRedactor.headerValue(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken()));
        String tagParam = getTagParam(dam_only);
        try {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            q = (q != null) ? URLEncoder.encode(q, DEFAULT_ENCODING) : "";
            // String urlParameters = "q=%2Bstate:ACTIVE" + (dam_only ? "%20%2Dtags:AEM_NO_DAM" : "")
            String urlParameters = "q=" + tagParam + (!q.isEmpty()  ? Constants.WHITESPACE_FIX+URLEncoder.encode(q, DEFAULT_ENCODING).replace("%253A", ":").replaceAll("%252F", "/"):"") + "&limit=" + limit + "&offset=" + offset + (sort != null ? "&sort=" + sort:"") + (clips_only ? "&is_clip:true":"");
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/videos";
            LOGGER.debug("urlParameters: {}" , urlParameters);
            String response = account.platform.getAPI(targetURL, urlParameters, headers);
            if (!response.isEmpty()) {
                json = JsonReader.readJsonArrayFromString(response);
                LOGGER.debug(Constants.RESPONSE, response);
            } else if (!q.isEmpty() && NumberUtils.isNumber(q)) {
                json.add(getVideo(q));
            }
        } catch (IOException e) {
            LOGGER.error(e.getClass().getName(), e);
        } catch (NullPointerException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return json;
    }

    // Fetching configured Tag from configuration & adding to query parameter
	private String getTagParam(boolean damOnlyFlag) {
		ConfigurationGrabber configGrabber = ServiceUtil.getConfigurationGrabber();
        ConfigurationService configService = configGrabber.getConfigurationService(account.getAccount_ID());
        String configuredTag = configService.getTagInclude();
        String includeTag = StringUtils.isNotBlank(configuredTag) ? ("tags:" + configuredTag) : StringUtils.EMPTY;
        String tagParam = StringUtils.isNotBlank(includeTag) ? (includeTag + (damOnlyFlag ? "&%20%2Dtags:AEM_NO_DAM" : "")) : (damOnlyFlag ? "%20%2Dtags:AEM_NO_DAM" : "");
		return tagParam;
	}
    
    public ArrayNode getVideosInFolder(String folder, int offset) {
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/folders/" + folder + "/videos";
            String tagParam = getTagParam(true);
            try {
            	String urlParameters = "q=" + tagParam + "&limit=100&offset=" + offset;
                json = getJSONArrayResponse(targetURL, urlParameters, headers);
            } catch (Exception e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //GET VIDEO OVERLOADS
    public ArrayNode getVideos() {
        return getVideos(Constants.EMPTY_Q_PARAM);
    }
    public ArrayNode getVideos(String q) {
        return getVideos(q, DEFAULT_LIMIT);
    }
    public ArrayNode getVideos(String q, String sort) {
        return getVideos(q, DEFAULT_LIMIT, DEFAULT_OFFSET, sort);
    }
    public ArrayNode getVideos(String q, String sort, int limit) {
        return getVideos(q, limit, DEFAULT_OFFSET, sort);
    }
    public ArrayNode getVideos(String q, int limit) {
        return getVideos(q, limit, DEFAULT_OFFSET);
    }
    public ArrayNode getVideos(String q, int limit, int offset) {
        return getVideos(q, limit, offset, Constants.EMPTY_SORT_PARAM);
    }
    public ArrayNode getVideos(int limit, int offset, String sort) {
        return getVideos(Constants.EMPTY_Q_PARAM, limit, offset, sort);
    }




    //getAPI
    public ObjectNode getPlaylist(String refID) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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
    public ArrayNode getVideosInPlaylist(String ID) {
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/playlists/" + ID + "/videos";
            json = getJSONArrayResponse(targetURL, Constants.EMPTY_URLPARAMS, headers);
        }
        return json;
    }

    public ArrayNode getPlaylists() {
        return getPlaylists(DEFAULT_LIMIT,  DEFAULT_OFFSET,  Constants.NAME);
    }
    public ArrayNode getPlaylists(int limit, int offset, String sort) {
        return getPlaylists( null,  limit,  offset,  sort);
    }
    public ArrayNode getPlaylists(String q, int limit, int offset, String sort) {
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/playlists";
            try {
                String encodedQ = (q != null) ? URLEncoder.encode(q, DEFAULT_ENCODING) : "";
                String urlParameters = "q=" + encodedQ + "&limit=" + limit + "&offset=" + offset + "&sort=" + sort;
                json = getJSONArrayResponse(targetURL, urlParameters, headers);
            } catch (UnsupportedEncodingException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    public ObjectNode getExperiences(String q, String sort) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + "/experiences";
            try {
                String encodedQ = (q != null) ? URLEncoder.encode(q, DEFAULT_ENCODING) : "";
                String urlParameters = "q=" + encodedQ + "&sort=" + sort;
                json = getExperiencesJSONObjectResponse(targetURL, urlParameters, headers);
            } catch (UnsupportedEncodingException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    public ArrayNode getVideosWithLabel(String label, int offset) {
        return getVideos("labels:" + label);
    }

    public ArrayNode getOnlyClipVideos(String q, int limit, int offset, String sort) {
        return getVideos(q, limit, offset, sort, true, true);
    }

    public ArrayNode getFolders(int limit, int offset) {
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
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

    public ObjectNode getLabels() {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
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

    public ObjectNode getExperiencesJSONObjectResponse(String targetURL, String urlParameters, Map<String, String> headers) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        try
        {
            String response = account.platform.getExperiencesAPI(targetURL, urlParameters, headers);
            if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
        }
        catch (IOException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return json;

    }

    //GET API
    public ObjectNode getJSONObjectResponse(String targetURL, String urlParameters, Map<String, String> headers) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        try
        {
            String response = account.platform.getAPI(targetURL, urlParameters, headers);
            if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
        }
        catch (IOException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return json;
    }

    public ArrayNode getJSONArrayResponse(String targetURL, String urlParameters, Map<String, String> headers) {
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
        try
        {
            String response = account.platform.getAPI(targetURL, urlParameters, headers);
            if (response != null && !response.isEmpty()) json = JsonReader.readJsonArrayFromString(response);
        }
        catch (IOException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return json;

    }

    // POST /accounts/{accountId}/videos/{videoId}/variants
    public ObjectNode addVariant(String videoId, ObjectNode variantBody) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            headers.put(Constants.CONTENT_TYPE_HEADER, "application/json");
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + videoId + "/variants";
            try {
                String response = account.platform.postAPI(targetURL, JsonUtil.pretty(variantBody), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    // PATCH /accounts/{accountId}/videos/{videoId}/variants/{language}
    public ObjectNode updateVariant(String videoId, String language, ObjectNode variantBody) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + videoId + "/variants/" + language;
            try {
                String response = account.platform.patchAPI(targetURL, JsonUtil.pretty(variantBody), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    // DELETE /accounts/{accountId}/videos/{videoId}/variants/{language}
    public ObjectNode deleteVariant(String videoId, String language) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        TokenObj authToken = account.getLoginToken();
        if (authToken != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put(Constants.AUTHENTICATION_HEADER, authToken.getTokenType() + " " + authToken.getToken());
            String targetURL = Constants.ACCOUNTS_API_PATH + account.getAccount_ID() + Constants.VIDEOS_API_PATH + videoId + "/variants/" + language;
            try {
                String response = account.platform.deleteAPI(targetURL, headers);
                if (response == null || response.isEmpty()) {
                    // Brightcove returns 204 No Content on success — distinguish from
                    // the auth-null / exception fall-through which also returns empty {}.
                    json.put(Constants.ERROR, 204);
                } else {
                    json = JsonReader.readJsonFromString(response);
                }
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    public ArrayNode getExperiencesJSONArrayResponse(String targetURL, String urlParameters, Map<String, String> headers) {
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
        try
        {
            String response = account.platform.getExperiencesAPI(targetURL, urlParameters, headers);
            if (response != null && !response.isEmpty()) json = JsonReader.readJsonArrayFromString(response);
        }
        catch (IOException e) {
            LOGGER.error(e.getClass().getName(), e);
        }
        return json;

    }

}