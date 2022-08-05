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

package com.coresecure.brightcove.wrapper.webservices;

import com.coresecure.brightcove.wrapper.enums.PlaylistTypeEnum;
import com.coresecure.brightcove.wrapper.objects.Playlist;
import com.coresecure.brightcove.wrapper.objects.Text_track;
import com.coresecure.brightcove.wrapper.objects.Video;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.AccountUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.TextUtil;
import com.day.cq.wcm.api.Page;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.*;

@Service
@Component
@Properties(value = {
        @Property(name = "sling.servlet.extensions", value = {"json", "js"}),
        @Property(name = "sling.servlet.paths", value = "/bin/brightcove/api")
})
public class BrcApi extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrcApi.class);
    private transient ServiceUtil serviceUtil = null;
    private transient ConfigurationGrabber cg;
    private transient com.coresecure.brightcove.wrapper.BrightcoveAPI brAPI;
    private List<String> allowedGroups = new ArrayList<String>();
    private transient ConfigurationService cs;

    @Override
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws ServletException, IOException {
        executeRequest(request, response);
    }

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws ServletException, IOException {
        executeRequest(request, response);
    }

    private boolean getServices(SlingHttpServletRequest request) {
        boolean result = false;
        String requestedAccount = AccountUtil.getSelectedAccount(request);
        LOGGER.info("getServices", requestedAccount);
        Set<String> services = cg.getAvailableServices(request);
        if (services.contains(requestedAccount)) {
            cs = cg.getConfigurationService(requestedAccount);
            brAPI = new com.coresecure.brightcove.wrapper.BrightcoveAPI(cs.getClientID(), cs.getClientSecret(), requestedAccount, cs.getProxy());
            serviceUtil = new ServiceUtil(requestedAccount);
            result = true;
        }
        return result;
    }

    private JSONObject getLocalPlayers(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        JSONArray players = new JSONArray();
        String playersPath = cs.getPlayersLoc();
        ResourceResolver resourceResolver = request.getResourceResolver();
        Resource res = resourceResolver.resolve(playersPath);
        Iterator<Resource> playersItr = res.listChildren();
        String selectedAccount = request.getParameter("account_id");
        LOGGER.info("getLocalPlayers(): " + playersPath);
        if (TextUtil.notEmpty(selectedAccount)) {
            while (playersItr.hasNext()) {
                Page playerRes = playersItr.next().adaptTo(Page.class);
                if (playerRes != null && "brightcove/components/page/brightcoveplayer".equals(playerRes.getContentResource().getResourceType())) {
                    JSONObject item = new JSONObject();
                    String path = playerRes.getPath();
                    String title = playerRes.getTitle();
                    String account = playerRes.getProperties().get("account", "");
                    if (TextUtil.notEmpty(account) && account.equals(selectedAccount)) {
                        item.put("id", path);
                        item.put("name", title);
                        players.put(item);
                    }
                }
            }
        }
        result.put(Constants.ITEMS, players);
        return result;
    }

    private JSONObject getPlayers() throws JSONException {
        return serviceUtil.getPlayers();
    }

    private JSONObject getListVideos(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        LOGGER.debug("query: " + request.getParameter(Constants.QUERY));
        if (request.getParameter(Constants.QUERY) != null && !request.getParameter(Constants.QUERY).trim().isEmpty()) {
            int start = 0;
            try {
                start = Integer.parseInt(request.getParameter(Constants.START));
            } catch (NumberFormatException e) {
                LOGGER.error("NumberFormatException", e);

            }
            int limit = ServiceUtil.DEFAULT_LIMIT;
            try {
                limit = Integer.parseInt(request.getParameter(Constants.LIMIT));
            } catch (NumberFormatException e) {
                LOGGER.error("NumberFormatException", e);

            }
            result = new JSONObject(serviceUtil.getList(false, start, limit, false, request.getParameter(Constants.QUERY)));
        } else {
            LOGGER.debug("getListSideMenu");
            result = new JSONObject(serviceUtil.getListSideMenu(request.getParameter(Constants.LIMIT)));
        }
        return result;
    }

    private JSONObject getListPlaylists(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        if (request.getParameter(Constants.QUERY) != null && !request.getParameter(Constants.QUERY).trim().isEmpty()) {
            result = new JSONObject(serviceUtil.getPlaylistByID(request.getParameter(Constants.QUERY)).toString());
        } else {
            result = new JSONObject(serviceUtil.getListPlaylistsSideMenu(request.getParameter(Constants.LIMIT)));
        }
        return result;
    }

    private JSONObject moveVideoToFolder(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        result = serviceUtil.moveVideoToFolder(request.getParameter("folder"), request.getParameter("video"));
        return result;
    }

    private JSONObject removeVideoFromFolder(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        result = serviceUtil.removeVideoFromFolder(request.getParameter("folder"), request.getParameter("video"));
        return result;
    }

    private JSONObject deletePlaylist(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        result = serviceUtil.deletePlaylist(request.getParameter("playlist"));
        return result;
    }

    private JSONObject createBlankPlaylist(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        result = serviceUtil.createPlaylist(request.getParameter("title"));
        return result;
    }

    private JSONObject getVideosInFolder(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        result = new JSONObject(serviceUtil.getVideosInFolder(request.getParameter("folder"), Integer.parseInt(request.getParameter(Constants.START))));
        return result;
    }

    private JSONObject getVideosWithLabel(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        result = new JSONObject(serviceUtil.getVideosWithLabel(request.getParameter("label"), Integer.parseInt(request.getParameter(Constants.START))));
        return result;
    }

    private JSONObject getFolders(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        result = new JSONObject(serviceUtil.getFolders());
        return result;
    }

    private JSONObject getLabels(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        result = new JSONObject(serviceUtil.getLabels());
        return result;
    }

    private JSONObject searchVideos(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        LOGGER.debug("query: " + request.getParameter(Constants.QUERY));
        if ("true".equals(request.getParameter("isID"))) {
            LOGGER.debug("isID");

            JSONArray videos = new JSONArray();
            try {
                JSONObject video = serviceUtil.getSelectedVideo(request.getParameter(Constants.QUERY));

                long totalItems = 0;
                if (video.has("id")) {
                    totalItems = 1;
                    videos.put(video);
                }
                result.put(Constants.ITEMS, videos);
                result.put(Constants.TOTALS, totalItems);

            } catch (JSONException je) {
                LOGGER.error("search_videos", je);
            }
        } else {
            LOGGER.debug("NOT isID");
            result = new JSONObject(serviceUtil.searchVideo(request.getParameter(Constants.QUERY), Integer.parseInt(request.getParameter(Constants.START)), Integer.parseInt(request.getParameter(Constants.LIMIT)), request.getParameter(Constants.SORT)));
        }
        return result;
    }

    private JSONObject searchExperiences(SlingHttpServletRequest request) throws JSONException {
        LOGGER.debug("searchExperiences called");
        JSONObject result = new JSONObject(serviceUtil.getExperiences(request.getParameter(Constants.QUERY)));
        return result;
    }

    private JSONObject getVideosInPlayList(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject(serviceUtil.getVideosInPlaylistByID(request.getParameter(Constants.QUERY)));
        return result;
    }

    private JSONObject searchPlaylist(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        if ("true".equals(request.getParameter("isID"))) {
            JSONArray playlists = new JSONArray();
            try {
                JSONObject playlist = serviceUtil.getPlaylistByID(request.getParameter(Constants.QUERY));

                long totalItems = 0;
                if (playlist.has("id")) {
                    totalItems = 1;
                    playlists.put(playlist);
                }
                result.put(Constants.ITEMS, playlists);
                result.put(Constants.TOTALS, totalItems);

            } catch (JSONException je) {
                LOGGER.error("search_playlists", je);
            }
        } else {
            result = new JSONObject(serviceUtil.getPlaylists(request.getParameter(Constants.QUERY), Integer.parseInt(request.getParameter(Constants.START)), Integer.parseInt(request.getParameter(Constants.LIMIT)), false, false));
        }
        return result;
    }

    private JSONObject deleteVideo(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        String[] ids = request.getParameter(Constants.QUERY).split(",");
        for (String id : ids) {
            if (!TextUtil.isEmpty(id)) {

                boolean resultDelete = serviceUtil.deleteVideo(id);
                LOGGER.debug(id + " " + resultDelete);

            }
        }
        result = new JSONObject(serviceUtil.searchVideo("", Integer.parseInt(request.getParameter(Constants.START)), Integer.parseInt(request.getParameter(Constants.LIMIT)), request.getParameter(Constants.SORT)));
        return result;
    }

    private JSONObject createPlaylist(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        RequestParameter requestParameter = request.getRequestParameter(Constants.PLST);
        if (requestParameter == null) {
            result.put(Constants.ERROR, 500);
            return result;
        }
        String[] ids = requestParameter.getString().split(",");

        LOGGER.info("Creating a Playlist");
        Playlist playlist = new Playlist();
        // Required fields
        playlist.setName(request.getParameter(Constants.PLST_NAME));
        playlist.setDescription(request.getParameter(Constants.PLST_SHORT_DESC));
        playlist.setPlaylistType(PlaylistTypeEnum.EXPLICIT);
        // Optional Fields
        if (request.getParameter(Constants.PLST_REFERENCE_ID) != null && request.getParameter(Constants.PLST_REFERENCE_ID).trim().length() > 0)
            playlist.setReferenceId(request.getParameter(Constants.PLST_REFERENCE_ID));

        List<String> videoIDs = new ArrayList<String>();
        for (String idStr : ids) {
            LOGGER.info("Video ID: " + idStr);
            videoIDs.add(idStr);
        }
        LOGGER.info("Writing Playlist to Media API");

        playlist.setVideoIds(videoIDs);
        JSONObject videoItem = brAPI.cms.createPlaylist(playlist);
        LOGGER.info("New Playlist id: " + videoItem.toString(1));
        if (!videoItem.has(Constants.ID)) {
            result.put(Constants.ERROR, 409);
        } else {
            result = null;
        }
        return result;
    }

    private JSONObject createVideo(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        RequestParameter requestParameter = request.getRequestParameter(Constants.PLST);
        if (requestParameter == null) {
            result.put(Constants.ERROR, 500);
            return result;
        }
        String ingestURL = requestParameter.getString();

        String ingestProfile = "balanced-high-definition";
        RequestParameter requestIngestParameter = request.getRequestParameter("profile_Ingest");
        if (requestIngestParameter != null) {
            ingestProfile = requestIngestParameter.getString();
        }

        Collection<String> tagsToAdd = new ArrayList<String>();
        if (request.getParameter("tags") != null) {

            List<String> tags = Arrays.asList(request.getParameterValues("tags"));
            for (String tag : tags) {
                if (tag.startsWith("+")) tagsToAdd.add(tag.substring(1));
            }

        }
        com.coresecure.brightcove.wrapper.objects.RelatedLink link = new com.coresecure.brightcove.wrapper.objects.RelatedLink(request.getParameter("linkText"), request.getParameter("linkURL"));
        com.coresecure.brightcove.wrapper.objects.Ingest ingest = new com.coresecure.brightcove.wrapper.objects.Ingest(ingestProfile, ingestURL);
        com.coresecure.brightcove.wrapper.objects.Video video = new com.coresecure.brightcove.wrapper.objects.Video(
                request.getParameter(Constants.NAME),
                request.getParameter(Constants.REFERENCE_ID),
                request.getParameter(Constants.DESCRIPTION),
                request.getParameter(Constants.LONG_DESCRIPTION),
                "",
                tagsToAdd,
                null,
                null,
                false,
                link
        );
        JSONObject videoItem = brAPI.cms.createVideo(video);
        String newVideoId = videoItem.getString(Constants.ID);
        JSONObject videoIngested = new JSONObject();
        try {
            videoIngested = brAPI.cms.createIngest(new com.coresecure.brightcove.wrapper.objects.Video(videoItem), ingest);
            if (videoIngested != null && videoIngested.has(Constants.ID)) {
                LOGGER.info("New video id: {}", newVideoId);
                result.put(Constants.VIDEOID, newVideoId);
                result.put("output", videoIngested);
            } else {
                result.put(Constants.ERROR, "createIngest Error");
                brAPI.cms.deleteVideo(newVideoId);
            }

        } catch (Exception exIngest) {
            result.put(Constants.ERROR, "createIngest Exception");
            brAPI.cms.deleteVideo(newVideoId);
        }
        return result;
    }

    private JSONObject updateVideo(SlingHttpServletRequest request) throws JSONException {
        Collection<String> tagsToAdd = new ArrayList<String>();
        if (request.getParameter("tags") != null) {

            List<String> tags = Arrays.asList(request.getParameterValues("tags"));
            for (String tag : tags) {
                if (tag.startsWith("+")) tagsToAdd.add(tag.substring(1));
            }

        }
        com.coresecure.brightcove.wrapper.objects.RelatedLink link = new com.coresecure.brightcove.wrapper.objects.RelatedLink(request.getParameter("linkText"), request.getParameter("linkURL"));
        com.coresecure.brightcove.wrapper.objects.Video video = new com.coresecure.brightcove.wrapper.objects.Video(
                request.getParameter(Constants.ID),
                request.getParameter(Constants.NAME),
                request.getParameter("referenceId"),
                request.getParameter(Constants.DESCRIPTION),
                request.getParameter(Constants.LONG_DESCRIPTION),
                "",
                tagsToAdd,
                null,
                null,
                false,
                link
        );
        JSONObject videoItem = brAPI.cms.updateVideo(video);
        //LOGGER.debug("videoItem", videoItem);

        return null;
    }

    private JSONObject updatePlaylist(SlingHttpServletRequest request) throws JSONException {
        JSONObject result = new JSONObject();
        if ( (request.getParameter("videos") != null) && (request.getParameter("playlistId") != null) ) {
            String[] videos = request.getParameterValues("videos");
            String playlistId = request.getParameter("playlistId");
            result = brAPI.cms.updatePlaylist(playlistId, videos);
        }

        return result;
    }

    private JSONObject removeTextTrack(SlingHttpServletRequest request) throws JSONException {
        try {
            String trackID = request.getParameter("track");
            String videoID = request.getParameter(Constants.ID);
            LOGGER.trace("TRACK DELETION ACTIVATED FOR TRACK {}", trackID);
            //PUT TOGETHER THE TEXT TRACKS JSON OBJECT IN ORDER TO SEND
            LOGGER.trace("VideoID: {}", videoID);

            //GET VIDEO FOR THIS VIDEO ID  - REMOVE FORM THE JSON OBJECT AND RESEND UP
            //GET VIDEO AND UPDATE TEXT TRACKS JSON
            JSONObject down_video = brAPI.cms.getVideo(request.getParameter(Constants.ID));

            //DELETE THE TRACK
            JSONArray trackslist = down_video.has(Constants.TEXT_TRACKS) ? down_video.getJSONArray(Constants.TEXT_TRACKS) : new JSONArray();
            String curID = "";

            //CONSTRUCTED CORRECTLY
            JSONArray updated_tracks = new JSONArray();

            LOGGER.trace("OLD TRACKS LIST {}", trackslist.length());
            for (int x = 0; x < trackslist.length(); x++) {
                JSONObject track = trackslist.getJSONObject(x);
                curID = track.getString(Constants.ID);
                if (!trackID.equals(curID)) {
                    Text_track currentTrack = new Text_track(track);
                    updated_tracks.put(currentTrack.toJSON());
                }
            }
            LOGGER.trace("UPDATED TRACKS LIST {}", updated_tracks.length());

            com.coresecure.brightcove.wrapper.objects.Video video = new Video(
                    request.getParameter(Constants.ID),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    updated_tracks
            );

            //LOGGER.debug("GOT VIDEO: "+ down_video.toString(1));
            LOGGER.debug("REBUILT VIDEO: {}", video.toJSON().toString(1));
            JSONObject videoItem = brAPI.cms.updateVideo(video);
            LOGGER.trace("RESP TXT TRACK : {}", videoItem.toString(1));
        } catch (JSONException e) {
            LOGGER.error(Constants.ERROR_LOG_TMPL, e);
        }
        return null;
    }

    private JSONObject uploadTextTrack(SlingHttpServletRequest request, SlingHttpServletResponse response) throws JSONException, UnsupportedEncodingException, IOException {
        JSONObject text_track_payload = new JSONObject();
        JSONArray text_track_arr = new JSONArray();
        JSONObject text_track = new JSONObject();

        text_track.put(Constants.SRCLANG, request.getParameter(Constants.TRACK_LANG));
        text_track.put(Constants.KIND, request.getParameter(Constants.TRACK_KIND));
        String label = request.getParameter(Constants.TRACK_LABEL);
        String filename;
        if (label != null && !label.isEmpty()) {
            text_track.put(Constants.LABEL, label);
            filename = label.replaceAll(" ", "_") + ".vtt";
        } else {
            filename = "no_label.vtt";
        }
        text_track.put(Constants.DEFAULT, "true".equals(request.getParameter(Constants.TRACK_DEFAULT)));
        text_track.put(Constants.MIME_TYPE, request.getParameter(Constants.TRACK_MIME_TYPE));
        //LOGGER.trace(text_track.toString(1));


        //FILE UPLOAD CASE***
        //HERE IT GETS THE TRACK SOURCE - HANDLE CASE OF FILE UPLOAD
        if ("".equals(request.getParameter(Constants.TRACK_SOURCE)) && !"".equals(request.getParameter(Constants.TRACK_FILEPATH))) {
            LOGGER.trace("FILEPATH: {} ", request.getParameter(Constants.TRACK_FILEPATH));
            //DO PUSH OF THE FILE GIVEN THE FILEPATH AND THEN PUSH THE NEW OBJECT TRACK TO VIDEO AS BEFORE
            //CHECK THAT IT IS A VTT FILE??? END OF NAME???

            InputStream is = new ByteArrayInputStream(request.getParameter(Constants.TRACK_FILEPATH).getBytes("UTF-8"));

            //REQUEST INGEST URL
            JSONObject s3_url_resp = serviceUtil.createAssetS3(request.getParameter(Constants.ID), filename, is);
            //IF SUCCESS
            if (s3_url_resp != null && s3_url_resp.has(Constants.SENT) && s3_url_resp.getBoolean(Constants.SENT)) {
                //text_track.put("url", s3_url_resp.getString("signed_url"));
                text_track.put(Constants.URL, s3_url_resp.getString(Constants.API_REQUEST_URL));
                LOGGER.trace("S3URLRESP: {}", s3_url_resp);
            } else {
                LOGGER.error("FAILED TO INITIALIZE BUCKET");
            }

        } else if (!"".equals(request.getParameter(Constants.TRACK_SOURCE))) {
            LOGGER.trace("SOURCEPATH: {}", request.getParameter(Constants.TRACK_SOURCE));

            text_track.put(Constants.URL, request.getParameter(Constants.TRACK_SOURCE));

        }

        text_track_arr.put(text_track);
        text_track_payload.put(Constants.TEXT_TRACKS, text_track_arr);


        JSONObject videoItem = brAPI.cms.uploadInjest(request.getParameter(Constants.ID), text_track_payload);
        //DEBUGGER PRINT - LOGGER.trace("**:" + videoItem.toString(1));

        if (videoItem.has(Constants.RESPONSE)) {
            JSONObject responseOBJ = new JSONObject(videoItem.getString(Constants.RESPONSE));
            LOGGER.trace("**has id object: {}", responseOBJ.has(Constants.ID));
            //response.sendError(422, "Incompatible Payload for Audio Track");
            LOGGER.trace("Text Track Upload Complete");

        } else {
            response.sendError(500, "Check logs");
        }
        return null;
    }

    private JSONObject uploadImage(SlingHttpServletRequest request) throws JSONException {
        LOGGER.trace("upload_thumbnail");


        JSONObject images_payload = new JSONObject();

        if (request.getParameter(Constants.THUMBNAIL_SOURCE) != null) {
            JSONObject thumbnail = new JSONObject();
            thumbnail.put(Constants.URL, request.getParameter(Constants.THUMBNAIL_SOURCE));
            images_payload.put(Constants.THUMBNAIL, thumbnail);
        }
        if (request.getParameter(Constants.POSTER_SOURCE) != null) {
            JSONObject poster = new JSONObject();
            poster.put(Constants.URL, request.getParameter(Constants.POSTER_SOURCE));
            images_payload.put(Constants.POSTER, poster);
        }

        LOGGER.trace("UploadImagesPayload>> {}", images_payload.toString(1));

        JSONObject videoItem = brAPI.cms.uploadInjest(request.getParameter(Constants.ID), images_payload);
        LOGGER.trace(videoItem.toString(1));

        return null;
    }

    private JSONObject apiLogic(SlingHttpServletRequest request, SlingHttpServletResponse response, JSONObject jsonObject) throws JSONException, IOException {
        JSONObject result = jsonObject;
        String requestedAPI = request.getParameter("a");
        LOGGER.debug("apiLogic requested :: ", requestedAPI);
        if ("local_players".equals(requestedAPI)) { //getPlayers
            result = getLocalPlayers(request);
        } else if ("players".equals(requestedAPI)) { //getPlayers
            result = getPlayers();
        } else if ("list_videos".equals(requestedAPI)) {
            result = getListVideos(request);
        } else if ("export".equals(requestedAPI)) {
            response.setHeader("Content-type", "application/xls");
            response.setHeader("Content-disposition", "inline; filename=Brightcove_Library_Export.csv");
            result = new JSONObject(serviceUtil.getList(true, Integer.parseInt(request.getParameter(Constants.START)), Integer.parseInt(request.getParameter(Constants.LIMIT)), true, request.getParameter(Constants.QUERY)));
        } else if ("list_playlists".equals(requestedAPI)) {
            result = getListPlaylists(request);
        } else if ("search_videos".equals(requestedAPI)) {
            result = searchVideos(request);
        } else if ("search_playlists".equals(requestedAPI)) {
            result = searchPlaylist(request);
        } else if ("delete_video".equals(requestedAPI)) {
            result = deleteVideo(request);
        } else if ("create_playlist".equals(requestedAPI)) {
            result = createPlaylist(request);
        } else if ("create_blank_playlist".equals(requestedAPI)) {
            result = createBlankPlaylist(request);
        } else if ("create_video".equals(requestedAPI)) {
            result = createVideo(request);
        } else if ("update_video".equals(requestedAPI)) {
            result = updateVideo(request);
        } else if ("remove_text_track".equals(requestedAPI)) {
            result = removeTextTrack(request);
        } else if ("upload_text_track".equals(requestedAPI)) {
            result = uploadTextTrack(request, response);
        } else if ("upload_image".equals(requestedAPI)) {
            result = uploadImage(request);
        } else if ("list_folders".equals(requestedAPI)) {
            result = getFolders(request);
        } else if ("get_videos_in_folder".equals(requestedAPI)) {
            result = getVideosInFolder(request);
        } else if ("get_videos_with_label".equals(requestedAPI)) {
            result = getVideosWithLabel(request);
        } else if ("move_video_to_folder".equals(requestedAPI)) {
            result = moveVideoToFolder(request);
        } else if ("remove_video_from_folder".equals(requestedAPI)) {
            result = removeVideoFromFolder(request);
        } else if ("delete_playlist".equals(requestedAPI)) {
            result = deletePlaylist(request);
        } else if ("search_experiences".equals(requestedAPI)) {
            result = searchExperiences(request);
        } else if ("list_videos_in_playlist".equals(requestedAPI)) {
            result = getVideosInPlayList(request);
        } else if ("update_playlist".equals(requestedAPI)) {
            result = updatePlaylist(request);
        } else if ("list_labels".equals(requestedAPI)) {
            result = getLabels(request);
        } else {
            result.put(Constants.ERROR, 404);
        }
        return result;
    }

    private void executeRequest(final SlingHttpServletRequest request,
                                final SlingHttpServletResponse response) throws ServletException, IOException {
        cg = ServiceUtil.getConfigurationGrabber();
        String extension = request.getRequestPathInfo().getExtension();
        LOGGER.debug("executeRequest");
        int error_code = 0;
        boolean js = "js".equals(extension);
        boolean dropdown = "jsx".equals(extension);
        boolean hasError = false;
        JSONObject result = new JSONObject();
        String resultstr = "{\"" + Constants.ITEMS + "\":[],\"" + Constants.TOTALS + "\":0,\"" + Constants.ERROR + "\":" + error_code + "}";

        try_loop:
        try {
            result.put("items", new JSONArray());
            result.put(Constants.TOTALS, 0);
            result.put("error", JSONObject.NULL);
            if (request.getParameter("a") == null) break try_loop;


            if (!getServices(request)) {
                result.put(Constants.ERROR, 403);
                break try_loop;
            }

            result = apiLogic(request, response, result);
            if (result == null) {
                //NOTE : This had to be added to fix a limitation of etx.js where a json response was non acceptable on file submission
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write("true");
                break try_loop;
            }
            resultstr = result.toString();

            String callback = request.getParameter("callback");
            if (callback == null || callback.isEmpty() || callback.matches("[^0-9a-zA-Z\\$_]|^(abstract|boolean|break|byte|case|catch|char|class|const|continue|debugger|default|delete|do|double|else|enum|export|extends|false|final|finally|float|for|function|goto|if|implements|import|in|instanceof|int|interface|long|native|new|null|package|private|protected|public|return|short|static|super|switch|synchronized|this|throw|throws|transient|true|try|typeof|var|volatile|void|while|with|NaN|Infinity|undefined)$")) {
                callback = "callback";
            }

            if (js) {
                response.setContentType("text/javascript;charset=UTF-8");
                response.getWriter().write(callback + "(" + resultstr + ");");
                break try_loop;
            }

            if (dropdown) {
                response.setContentType("text/html;charset=UTF-8");
                JSONArray itemsArray = result.getJSONArray("items");
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < itemsArray.length(); i++) {
                    JSONObject item = new JSONObject();
                    builder.append("<li class=\"coral-SelectList-item coral-SelectList-item--option\" data-value=\"" + itemsArray.getJSONObject(i).getString("name") + " [" + itemsArray.getJSONObject(i).getString("id") + "]\">" + itemsArray.getJSONObject(i).getString("name") + " [" + itemsArray.getJSONObject(i).getString("id") + "]</li>");
                }
                LOGGER.debug("dropdown values requested");
                response.getWriter().write(builder.toString());
                break try_loop;
            }

            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(resultstr);

            if (result.has(Constants.ERROR) && result.getInt(Constants.ERROR) >= 400) {
                error_code = result.getInt(Constants.ERROR);
            }
        } catch (JSONException je) {
            LOGGER.error(je.getClass().getName(), je);
            error_code = 500;
        }

        if (error_code >= 400) {
            response.setStatus(error_code);
        }

    }


}
