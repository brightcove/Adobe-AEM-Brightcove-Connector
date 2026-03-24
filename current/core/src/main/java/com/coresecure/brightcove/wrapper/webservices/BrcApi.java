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
import com.coresecure.brightcove.wrapper.utils.HttpServices;
import com.coresecure.brightcove.wrapper.utils.TextUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.wcm.api.Page;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;
import javax.servlet.Servlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.*;

@Component(service = { Servlet.class },
    property = {
        "sling.servlet.extensions=json",
        "sling.servlet.extensions=js",
        "sling.servlet.paths=/bin/brightcove/api"
    }
)
@ServiceDescription("Brightcove API Servlet")
public class BrcApi extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrcApi.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
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

    private ObjectNode getLocalPlayers(SlingHttpServletRequest request) throws IOException {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        ArrayNode players = JsonNodeFactory.instance.arrayNode();
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
                    ObjectNode item = JsonNodeFactory.instance.objectNode();
                    String path = playerRes.getPath();
                    String title = playerRes.getTitle();
                    String account = playerRes.getProperties().get("account", "");
                    if (TextUtil.notEmpty(account) && account.equals(selectedAccount)) {
                        item.put("id", path);
                        item.put("name", title);
                        players.add(item);
                    }
                }
            }
        }
        result.set(Constants.ITEMS, players);
        return result;
    }

    private ObjectNode getPlayers() throws IOException {
        return serviceUtil.getPlayers();
    }

    private ObjectNode getListVideos(SlingHttpServletRequest request) throws IOException {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
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
            result = (ObjectNode) MAPPER.readTree(serviceUtil.getList(false, start, limit, false, request.getParameter(Constants.QUERY)));
        } else {
            LOGGER.debug("getListSideMenu");
            result = (ObjectNode) MAPPER.readTree(serviceUtil.getListSideMenu(request.getParameter(Constants.LIMIT)));
        }
        return result;
    }

    private ObjectNode getListPlaylists(SlingHttpServletRequest request) throws IOException {
        ObjectNode result;
        if (request.getParameter(Constants.QUERY) != null && !request.getParameter(Constants.QUERY).trim().isEmpty()) {
            result = serviceUtil.getPlaylistByID(request.getParameter(Constants.QUERY));
        } else {
            result = (ObjectNode) MAPPER.readTree(serviceUtil.getListPlaylistsSideMenu(request.getParameter(Constants.LIMIT)));
        }
        return result;
    }

    private ObjectNode moveVideoToFolder(SlingHttpServletRequest request) throws IOException {
        return serviceUtil.moveVideoToFolder(request.getParameter("folder"), request.getParameter("video"));
    }

    private ObjectNode removeVideoFromFolder(SlingHttpServletRequest request) throws IOException {
        return serviceUtil.removeVideoFromFolder(request.getParameter("folder"), request.getParameter("video"));
    }

    private ObjectNode deletePlaylist(SlingHttpServletRequest request) throws IOException {
        return serviceUtil.deletePlaylist(request.getParameter("playlist"));
    }

    private ObjectNode createBlankPlaylist(SlingHttpServletRequest request) throws IOException {
        return serviceUtil.createPlaylist(request.getParameter("title"));
    }

    private ObjectNode getVideosInFolder(SlingHttpServletRequest request) throws IOException {
        return (ObjectNode) MAPPER.readTree(serviceUtil.getVideosInFolder(request.getParameter("folder"), Integer.parseInt(request.getParameter(Constants.START))));
    }

    private ObjectNode getVideosWithLabel(SlingHttpServletRequest request) throws IOException {
        return (ObjectNode) MAPPER.readTree(serviceUtil.getVideosWithLabel(request.getParameter("label"), Integer.parseInt(request.getParameter(Constants.START))));
    }

    private ObjectNode getFolders(SlingHttpServletRequest request) throws IOException {
        return (ObjectNode) MAPPER.readTree(serviceUtil.getFolders());
    }

    private ObjectNode getLabels(SlingHttpServletRequest request) throws IOException {
        return (ObjectNode) MAPPER.readTree(serviceUtil.getLabels());
    }

    private ObjectNode searchVideos(SlingHttpServletRequest request) throws IOException {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        LOGGER.debug("query: " + request.getParameter(Constants.QUERY));
        if ("true".equals(request.getParameter("isID"))) {
            LOGGER.debug("isID");

            ArrayNode videos = JsonNodeFactory.instance.arrayNode();
            try {
                ObjectNode video = serviceUtil.getSelectedVideo(request.getParameter(Constants.QUERY));

                long totalItems = 0;
                if (video.has("id")) {
                    totalItems = 1;
                    videos.add(video);
                }
                result.set(Constants.ITEMS, videos);
                result.put(Constants.TOTALS, totalItems);

            } catch (Exception je) {
                LOGGER.error("search_videos", je);
            }
        } else {
            LOGGER.debug("NOT isID");
            result = serviceUtil.searchVideo(request.getParameter(Constants.QUERY), Integer.parseInt(request.getParameter(Constants.START)), Integer.parseInt(request.getParameter(Constants.LIMIT)), request.getParameter(Constants.SORT), false);
        }
        return result;
    }

    private ObjectNode searchExperiences(SlingHttpServletRequest request) throws IOException {
        LOGGER.debug("searchExperiences called");
        return (ObjectNode) MAPPER.readTree(serviceUtil.getExperiences(request.getParameter(Constants.QUERY)));
    }

    private ObjectNode getVideosInPlayList(SlingHttpServletRequest request) throws IOException {
        return (ObjectNode) MAPPER.readTree(serviceUtil.getVideosInPlaylistByID(request.getParameter(Constants.QUERY)));
    }

    private ObjectNode searchPlaylist(SlingHttpServletRequest request) throws IOException {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        if ("true".equals(request.getParameter("isID"))) {
            ArrayNode playlists = JsonNodeFactory.instance.arrayNode();
            try {
                ObjectNode playlist = serviceUtil.getPlaylistByID(request.getParameter(Constants.QUERY));

                long totalItems = 0;
                if (playlist.has("id")) {
                    totalItems = 1;
                    playlists.add(playlist);
                }
                result.set(Constants.ITEMS, playlists);
                result.put(Constants.TOTALS, totalItems);

            } catch (Exception je) {
                LOGGER.error("search_playlists", je);
            }
        } else {
            result = (ObjectNode) MAPPER.readTree(serviceUtil.getPlaylists(request.getParameter(Constants.QUERY), Integer.parseInt(request.getParameter(Constants.START)), Integer.parseInt(request.getParameter(Constants.LIMIT)), false, true));
        }
        return result;
    }

    private ObjectNode deleteVideo(SlingHttpServletRequest request) throws IOException {
        String[] ids = request.getParameter(Constants.QUERY).split(",");
        for (String id : ids) {
            if (!TextUtil.isEmpty(id)) {

                boolean resultDelete = serviceUtil.deleteVideo(id);
                LOGGER.debug(id + " " + resultDelete);

            }
        }
        return serviceUtil.searchVideo("", Integer.parseInt(request.getParameter(Constants.START)), Integer.parseInt(request.getParameter(Constants.LIMIT)), request.getParameter(Constants.SORT), false);
    }

    private ObjectNode createPlaylist(SlingHttpServletRequest request) throws IOException {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
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
        ObjectNode videoItem = brAPI.cms.createPlaylist(playlist);
        LOGGER.info("New Playlist id: " + videoItem.toPrettyString());
        if (!videoItem.has(Constants.ID)) {
            result.put(Constants.ERROR, 409);
        } else {
            result = null;
        }
        return result;
    }

    private ObjectNode createLabel(SlingHttpServletRequest request) throws IOException {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        RequestParameter requestParameter = request.getRequestParameter(Constants.LABEL);

        if (requestParameter == null) {
            result.put(Constants.ERROR, 500);
            return result;
        }

        LOGGER.info("Creating a Label");
        ObjectNode labelResult = brAPI.cms.createLabel(requestParameter.toString());

        if (!labelResult.has(Constants.ID)) {
            result.put(Constants.ERROR, 409);
        } else {
            result = null;
        }
        return result;
    }

    private ObjectNode createVideo(SlingHttpServletRequest request) throws IOException {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
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
        ObjectNode videoItem = brAPI.cms.createVideo(video);
        if (!videoItem.has(Constants.ID)) {
            result.put(Constants.ERROR, "createVideo failed");
            return result;
        }
        String newVideoId = videoItem.get(Constants.ID).asText();
        ObjectNode videoIngested = JsonNodeFactory.instance.objectNode();
        try {
            videoIngested = brAPI.cms.createIngest(new com.coresecure.brightcove.wrapper.objects.Video(videoItem), ingest);
            if (videoIngested != null && videoIngested.has(Constants.ID)) {
                LOGGER.info("New video id: {}", newVideoId);
                result.put(Constants.VIDEOID, newVideoId);
                result.set("output", videoIngested);
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

    private ObjectNode updateVideo(SlingHttpServletRequest request) throws IOException {
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
        ObjectNode videoItem = brAPI.cms.updateVideo(video);
        //LOGGER.debug("videoItem", videoItem);

        return null;
    }

    private ObjectNode updatePlaylist(SlingHttpServletRequest request) throws IOException {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        if ( (request.getParameter("videos") != null) && (request.getParameter("playlistId") != null) ) {
            String[] videos = request.getParameterValues("videos");
            String playlistId = request.getParameter("playlistId");
            result = brAPI.cms.updatePlaylist(playlistId, videos);
        }

        return result;
    }

    private ObjectNode updateLabels(SlingHttpServletRequest request) throws IOException {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        if ( (request.getParameter("labels") != null) && (request.getParameter("videoId") != null) ) {
            String[] labels = request.getParameterValues("labels");
            String videoId = request.getParameter("videoId");
            result = brAPI.cms.updateLabels(videoId, labels);
        }
        return result;
    }

    private ObjectNode removeTextTrack(SlingHttpServletRequest request) throws IOException {
        try {
            String trackID = request.getParameter("track");
            String videoID = request.getParameter(Constants.ID);
            LOGGER.trace("TRACK DELETION ACTIVATED FOR TRACK {}", trackID);
            //PUT TOGETHER THE TEXT TRACKS JSON OBJECT IN ORDER TO SEND
            LOGGER.trace("VideoID: {}", videoID);

            //GET VIDEO FOR THIS VIDEO ID  - REMOVE FORM THE JSON OBJECT AND RESEND UP
            //GET VIDEO AND UPDATE TEXT TRACKS JSON
            ObjectNode down_video = brAPI.cms.getVideo(request.getParameter(Constants.ID));

            //DELETE THE TRACK
            ArrayNode trackslist = down_video.has(Constants.TEXT_TRACKS) ? (ArrayNode) down_video.get(Constants.TEXT_TRACKS) : JsonNodeFactory.instance.arrayNode();
            String curID = "";

            //CONSTRUCTED CORRECTLY
            ArrayNode updated_tracks = JsonNodeFactory.instance.arrayNode();

            LOGGER.trace("OLD TRACKS LIST {}", trackslist.size());
            for (int x = 0; x < trackslist.size(); x++) {
                ObjectNode track = (ObjectNode) trackslist.get(x);
                curID = track.get(Constants.ID).asText();
                if (!trackID.equals(curID)) {
                    Text_track currentTrack = new Text_track(track);
                    updated_tracks.add(currentTrack.toJSON());
                }
            }
            LOGGER.trace("UPDATED TRACKS LIST {}", updated_tracks.size());

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

            //LOGGER.debug("GOT VIDEO: "+ down_video.toPrettyString());
            LOGGER.debug("REBUILT VIDEO: {}", video.toJSON().toPrettyString());
            ObjectNode videoItem = brAPI.cms.updateVideo(video);
            LOGGER.trace("RESP TXT TRACK : {}", videoItem.toPrettyString());
        } catch (Exception e) {
            LOGGER.error(Constants.ERROR_LOG_TMPL, e);
        }
        return null;
    }

    private ObjectNode uploadTextTrack(SlingHttpServletRequest request, SlingHttpServletResponse response) throws UnsupportedEncodingException, IOException {
        ObjectNode text_track_payload = JsonNodeFactory.instance.objectNode();
        ArrayNode text_track_arr = JsonNodeFactory.instance.arrayNode();
        ObjectNode text_track = JsonNodeFactory.instance.objectNode();

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
        //LOGGER.trace(text_track.toPrettyString());


        //FILE UPLOAD CASE***
        //HERE IT GETS THE TRACK SOURCE - HANDLE CASE OF FILE UPLOAD
        if ("".equals(request.getParameter(Constants.TRACK_SOURCE)) && !"".equals(request.getParameter(Constants.TRACK_FILEPATH))) {
            LOGGER.trace("FILEPATH: {} ", request.getParameter(Constants.TRACK_FILEPATH));
            //DO PUSH OF THE FILE GIVEN THE FILEPATH AND THEN PUSH THE NEW OBJECT TRACK TO VIDEO AS BEFORE
            //CHECK THAT IT IS A VTT FILE??? END OF NAME???

            InputStream is = new ByteArrayInputStream(request.getParameter(Constants.TRACK_FILEPATH).getBytes("UTF-8"));

            //REQUEST INGEST URL
            ObjectNode s3_url_resp = serviceUtil.createAssetS3(request.getParameter(Constants.ID), filename, is);
            //IF SUCCESS
            if (s3_url_resp != null && s3_url_resp.has(Constants.SENT) && s3_url_resp.get(Constants.SENT).asBoolean()) {
                //text_track.put("url", s3_url_resp.get("signed_url").asText());
                text_track.put(Constants.URL, s3_url_resp.get(Constants.API_REQUEST_URL).asText());
                LOGGER.trace("S3URLRESP: {}", s3_url_resp);
            } else {
                LOGGER.error("FAILED TO INITIALIZE BUCKET");
            }

        } else if (!"".equals(request.getParameter(Constants.TRACK_SOURCE))) {
            LOGGER.trace("SOURCEPATH: {}", request.getParameter(Constants.TRACK_SOURCE));

            text_track.put(Constants.URL, request.getParameter(Constants.TRACK_SOURCE));

        }

        text_track_arr.add(text_track);
        text_track_payload.set(Constants.TEXT_TRACKS, text_track_arr);


        ObjectNode videoItem = brAPI.cms.uploadInjest(request.getParameter(Constants.ID), text_track_payload);
        //DEBUGGER PRINT - LOGGER.trace("**:" + videoItem.toPrettyString());

        if (videoItem.has(Constants.RESPONSE) && !videoItem.get(Constants.RESPONSE).isNull()) {
            com.fasterxml.jackson.databind.JsonNode parsedResponse = MAPPER.readTree(videoItem.get(Constants.RESPONSE).asText());
            if (parsedResponse != null && parsedResponse.isObject()) {
                ObjectNode responseOBJ = (ObjectNode) parsedResponse;
                LOGGER.trace("**has id object: {}", responseOBJ.has(Constants.ID));
            }
            LOGGER.trace("Text Track Upload Complete");
        } else {
            response.sendError(500, "Check logs");
        }
        return null;
    }

    private ObjectNode uploadImage(SlingHttpServletRequest request) throws IOException {
        LOGGER.trace("upload_thumbnail");

        String posterSource = request.getParameter(Constants.POSTER_SOURCE);
        String thumbnailSource = request.getParameter(Constants.THUMBNAIL_SOURCE);

        ObjectNode images_payload = JsonNodeFactory.instance.objectNode();

        if (thumbnailSource != null) {
            ObjectNode thumbnail = JsonNodeFactory.instance.objectNode();
            thumbnail.put(Constants.URL, thumbnailSource);
            images_payload.set(Constants.THUMBNAIL, thumbnail);
        }
        if (posterSource != null) {
            ObjectNode poster = JsonNodeFactory.instance.objectNode();
            poster.put(Constants.URL, posterSource);
            images_payload.set(Constants.POSTER, poster);
        }

        LOGGER.trace("UploadImagesPayload>> {}", images_payload.toPrettyString());

        ObjectNode videoItem = brAPI.cms.uploadInjest(request.getParameter(Constants.ID), images_payload);
        LOGGER.trace(videoItem.toPrettyString());

        if (videoItem.has(Constants.RESPONSE) && !videoItem.get(Constants.RESPONSE).isNull()) {
            try {
                String videoId = request.getParameter(Constants.ID);
                String accountId = AccountUtil.getSelectedAccount(request);
                String confPath = cs.getAssetIntegrationPath();
                String accountFolder = (confPath.endsWith("/") ? confPath : confPath + "/") + accountId + "/";
                String filename = videoId + ".mp4";
                ResourceResolver resourceResolver = request.getResourceResolver();

                // check root of account folder first, then one level of subfolders (Brightcove folders)
                Resource assetResource = resourceResolver.getResource(accountFolder + filename);
                if (assetResource == null) {
                    Resource accountFolderRes = resourceResolver.getResource(accountFolder);
                    if (accountFolderRes != null) {
                        Iterator<Resource> children = accountFolderRes.listChildren();
                        while (children.hasNext() && assetResource == null) {
                            Resource candidate = children.next().getChild(filename);
                            if (candidate != null) {
                                assetResource = candidate;
                            }
                        }
                    }
                }

                if (assetResource != null) {
                    Asset asset = assetResource.adaptTo(Asset.class);
                    if (asset != null) {
                        if (posterSource != null) {
                            java.net.URL srcURL = new java.net.URL(posterSource);
                            if (!"https".equalsIgnoreCase(srcURL.getProtocol())) {
                                LOGGER.warn("Skipping poster rendition update for video {} — URL must use HTTPS", videoId);
                            } else {
                                try (InputStream is = HttpServices.getSSLConnection(srcURL, posterSource).getInputStream()) {
                                    asset.addRendition(Constants.BRC_POSTER_PNG, is, "image/png");
                                }
                            }
                        }
                        if (thumbnailSource != null) {
                            java.net.URL srcURL = new java.net.URL(thumbnailSource);
                            if (!"https".equalsIgnoreCase(srcURL.getProtocol())) {
                                LOGGER.warn("Skipping thumbnail rendition update for video {} — URL must use HTTPS", videoId);
                            } else {
                                try (InputStream is = HttpServices.getSSLConnection(srcURL, thumbnailSource).getInputStream()) {
                                    asset.addRendition(Constants.BRC_THUMBNAIL_PNG, is, "image/png");
                                }
                            }
                        }
                        resourceResolver.commit();
                        LOGGER.trace("DAM renditions updated for video {}", videoId);
                    }
                } else {
                    LOGGER.warn("Could not find DAM asset for video {} — rendition not updated", videoId);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to update DAM rendition after image upload for video {}", request.getParameter(Constants.ID), e);
            }
        }

        return null;
    }

    private ObjectNode apiLogic(SlingHttpServletRequest request, SlingHttpServletResponse response, ObjectNode jsonObject) throws IOException {
        ObjectNode result = jsonObject;
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
            result = (ObjectNode) MAPPER.readTree(serviceUtil.getList(true, Integer.parseInt(request.getParameter(Constants.START)), Integer.parseInt(request.getParameter(Constants.LIMIT)), true, request.getParameter(Constants.QUERY)));
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
        } else if ("update_labels".equals(requestedAPI)) {
            result = updateLabels(request);
        } else if ("list_labels".equals(requestedAPI)) {
            result = getLabels(request);
        } else if ("create_label".equals(requestedAPI)) {
            result = createLabel(request);
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
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        String resultstr = "{\"" + Constants.ITEMS + "\":[],\"" + Constants.TOTALS + "\":0,\"" + Constants.ERROR + "\":" + error_code + "}";

        try_loop:
        try {
            result.set("items", JsonNodeFactory.instance.arrayNode());
            result.put(Constants.TOTALS, 0);
            result.putNull("error");
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
                StringBuilder builder = new StringBuilder();
                if (result.has("items") && result.get("items").isArray()) {
                    ArrayNode itemsArray = (ArrayNode) result.get("items");
                    for (int i = 0; i < itemsArray.size(); i++) {
                        if (!itemsArray.get(i).isObject()) continue;
                        ObjectNode item = (ObjectNode) itemsArray.get(i);
                        String iName = item.has("name") && !item.get("name").isNull() ? item.get("name").asText() : "";
                        String iId = item.has("id") && !item.get("id").isNull() ? item.get("id").asText() : "";
                        builder.append("<li class=\"coral-SelectList-item coral-SelectList-item--option\" data-value=\"" + iName + " [" + iId + "]\">" + iName + " [" + iId + "]</li>");
                    }
                }
                LOGGER.debug("dropdown values requested");
                response.getWriter().write(builder.toString());
                break try_loop;
            }

            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(resultstr);

            if (result.has(Constants.ERROR) && !result.get(Constants.ERROR).isNull() && result.get(Constants.ERROR).asInt() >= 400) {
                error_code = result.get(Constants.ERROR).asInt();
            }
        } catch (Exception je) {
            LOGGER.error(je.getClass().getName(), je);
            error_code = 500;
        }

        if (error_code >= 400) {
            response.setStatus(error_code);
        }

    }


}
