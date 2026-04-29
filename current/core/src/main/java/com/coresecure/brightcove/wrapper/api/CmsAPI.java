package com.coresecure.brightcove.wrapper.api;

import com.coresecure.brightcove.wrapper.objects.*;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.JsonReader;
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
                String response = account.platform.postDIRequest_API(targetURL, payload.toPrettyString(), headers);
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
                String response = account.platform.postDI_API(targetURL, aIngest.toJSON().toPrettyString(), headers);
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
                String response = account.platform.postAPI(targetURL, videoObj.toPrettyString(), headers);
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

                String response = account.platform.patchAPI(targetURL, video.toPrettyString(), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
        return json;
    }

    //PatchAPI
    public ObjectNode updatePlaylist(String playlistId, String[] videos) {
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
                ArrayNode videoArray = JsonNodeFactory.instance.arrayNode();
                for (String item : videos) {
                    videoArray.add(item);
                }
                request.set("video_ids", videoArray);
                LOGGER.info("updatePlaylistParams: {}", request.toPrettyString());
                String response = account.platform.patchAPI(targetURL, request.toPrettyString(), headers);
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
                LOGGER.info("updateVideoParams: {}", request.toPrettyString());
                String response = account.platform.patchAPI(targetURL, request.toPrettyString(), headers);
                if (response != null && !response.isEmpty()) json = JsonReader.readJsonFromString(response);
            } catch (IOException e) {
                LOGGER.error(e.getClass().getName(), e);
            }
        }
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
                String response = account.platform.postAPI(targetURL, aPlaylist.toJSON().toPrettyString(), headers);
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
        LOGGER.debug("authToken: {}" , authToken.getToken());
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
                String response = account.platform.postAPI(targetURL, variantBody.toPrettyString(), headers);
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
                String response = account.platform.patchAPI(targetURL, variantBody.toPrettyString(), headers);
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