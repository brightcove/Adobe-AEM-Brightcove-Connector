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

package com.coresecure.brightcove.wrapper.webservices;

import com.coresecure.brightcove.wrapper.enums.PlaylistTypeEnum;
import com.coresecure.brightcove.wrapper.objects.Playlist;
import com.coresecure.brightcove.wrapper.objects.Text_track;
import com.coresecure.brightcove.wrapper.objects.Video;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.AccountUtil;
import com.coresecure.brightcove.wrapper.utils.TextUtil;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
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
import java.util.*;

@Service
@Component
@Properties(value = {
        @Property(name = "sling.servlet.extensions", value = {"json","js"}),
        @Property(name = "sling.servlet.paths", value = "/bin/brightcove/api")
})
public class BrcApi extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrcApi.class);
    private ServiceUtil serviceUtil = null;
    private List<String> allowedGroups = new ArrayList<String>();
    @Override
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws ServletException, IOException {
        executeRequest(request, response);
    }

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws ServletException, IOException {
        executeRequest(request,response);
    }
    private void executeRequest(final SlingHttpServletRequest request,
                                final SlingHttpServletResponse response) throws ServletException, IOException {
        String extension = request.getRequestPathInfo().getExtension();
        boolean js = "js".equals(extension);
        JSONObject result = new JSONObject();
        String resultstr="";
        try {
            result.put("items", new JSONArray());
            result.put("totals", 0);
            result.put("error", JSONObject.NULL);
            if (request.getParameter("a") != null) {
                String requestedAccount = AccountUtil.getSelectedAccount(request);
                ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
                ConfigurationService cs = cg.getConfigurationService(requestedAccount);

                Set<String> services = cg.getAvailableServices();
                if (services.contains(requestedAccount)) {
                    com.coresecure.brightcove.wrapper.BrightcoveAPI brAPI = new com.coresecure.brightcove.wrapper.BrightcoveAPI(cs.getClientID(), cs.getClientSecret(), requestedAccount);
                    if (cs.getProxy() != null && cs.getProxy().length() > 0) {
                        brAPI.setProxy(cs.getProxy());
                    }

                    allowedGroups = cs.getAllowedGroupsList();
                    serviceUtil = new ServiceUtil(requestedAccount);
                    String requestedAPI = "";
                    boolean is_authorized = false;
                    Session session = request.getResourceResolver().adaptTo(Session.class);
                    UserManager userManager = request.getResourceResolver().adaptTo(UserManager.class);
                    try {
                        Authorizable auth = userManager.getAuthorizable(session.getUserID());

                        if (auth != null) {
                            Iterator<Group> groups = auth.memberOf();
                            while (groups.hasNext() && !is_authorized) {
                                Group group = groups.next();
                                if (allowedGroups.contains(group.getID())) is_authorized = true;
                            }
                        }
                    } catch (RepositoryException re) {
                        LOGGER.error("executeRequest", re);
                    }
                    if (is_authorized) {
                        requestedAPI = request.getParameter("a");
                        if ("test".equals(requestedAPI)) { //no commands
                            result.put("mode", "test");
                        } else if ("players".equals(requestedAPI)) { //getPlayers
                            JSONObject players = null;
                            String type = request.getParameter("players_type");
                            result = serviceUtil.getPlayers();
                        } else if ("list_videos".equals(requestedAPI)) {
                            LOGGER.debug("query: " + request.getParameter("query"));
                            if (request.getParameter("query") != null && !request.getParameter("query").trim().isEmpty()) {
                                int start = 0;
                                try {
                                    start = Integer.parseInt(request.getParameter("start"));
                                } catch (NumberFormatException e) {
                                    LOGGER.error("NumberFormatException", e);

                                }
                                int limit = serviceUtil.DEFAULT_LIMIT;
                                try {
                                    limit = Integer.parseInt(request.getParameter("limit"));
                                } catch (NumberFormatException e) {
                                    LOGGER.error("NumberFormatException", e);

                                }
                                result = new JSONObject(serviceUtil.getList(false, start, limit, false, request.getParameter("query")));
                            } else {
                                LOGGER.debug("getListSideMenu");
                                result = new JSONObject(serviceUtil.getListSideMenu(request.getParameter("limit")));
                            }
                        } else if ("export".equals(requestedAPI)) {
                            response.setHeader("Content-type", "application/xls");
                            response.setHeader("Content-disposition", "inline; filename=Brightcove_Library_Export.csv");
                            result = new JSONObject(serviceUtil.getList(true, Integer.parseInt(request.getParameter("start")), Integer.parseInt(request.getParameter("limit")), true, request.getParameter("query")));
                        } else if ("list_playlists".equals(requestedAPI)) {
                            if (request.getParameter("query") != null && !request.getParameter("query").trim().isEmpty()) {
                                result = new JSONObject(serviceUtil.getPlaylistByID(request.getParameter("query")).toString());
                            } else {
                                result = new JSONObject(serviceUtil.getListPlaylistsSideMenu(request.getParameter("limit")));
                            }
                        } else if ("search_videos".equals(requestedAPI)) {
                            LOGGER.debug("query: " + request.getParameter("query"));
                            if ("true".equals(request.getParameter("isID"))) {
                                LOGGER.debug("isID");

                                JSONArray videos = new JSONArray();
                                try {
                                    JSONObject video = serviceUtil.getSelectedVideo(request.getParameter("query"));

                                    long totalItems = 0;
                                    if (video.has("id")) {
                                        totalItems = 1;
                                        videos.put(video);
                                    }
                                    result.put("items", (JSONArray) videos);
                                    result.put("totals", totalItems);

                                } catch (JSONException je) {
                                    LOGGER.error("search_videos", je);
                                }
                            } else {
                                LOGGER.debug("NOT isID");
                                result = new JSONObject(serviceUtil.searchVideo(request.getParameter("query"), Integer.parseInt(request.getParameter("start")), Integer.parseInt(request.getParameter("limit")), request.getParameter("sort")));
                            }
                        } else if ("search_playlists".equals(requestedAPI)) {
                            if ("true".equals(request.getParameter("isID"))) {
                                JSONArray playlists = new JSONArray();
                                try {
                                    JSONObject playlist = serviceUtil.getPlaylistByID(request.getParameter("query"));

                                    long totalItems = 0;
                                    if (playlist.has("id")) {
                                        totalItems = 1;
                                        playlists.put(playlist);
                                    }
                                    result.put("items", (JSONArray) playlists);
                                    result.put("totals", totalItems);

                                } catch (JSONException je) {
                                    LOGGER.error("search_playlists", je);
                                }
                            } else {
                                result = new JSONObject(serviceUtil.getPlaylists(request.getParameter("query"), Integer.parseInt(request.getParameter("start")), Integer.parseInt(request.getParameter("limit")), false, false));
                            }
                        } else if ("delete_video".equals(requestedAPI)) {
                            String[] ids = ((String) request.getParameter("query")).split(",");
                            for (String id : ids) {
                                if (!TextUtil.isEmpty(id)) {

                                    boolean resultDelete = serviceUtil.deleteVideo(id);
                                    LOGGER.debug(id + " " + resultDelete);

                                }
                            }
                            result = new JSONObject(serviceUtil.searchVideo("", Integer.parseInt(request.getParameter("start")), Integer.parseInt(request.getParameter("limit")), request.getParameter("sort")));
                        } else if ("create_playlist".equals(requestedAPI)) {
                                String[] ids = request.getRequestParameter("playlist").getString().split(",");

                                LOGGER.info("Creating a Playlist");
                                Playlist playlist = new Playlist();
                                // Required fields
                                playlist.setName(request.getParameter("plst.name"));
                                playlist.setDescription(request.getParameter("plst.shortDescription"));
                                playlist.setPlaylistType(PlaylistTypeEnum.EXPLICIT);
                                // Optional Fields
                                if (request.getParameter("plst.referenceId") != null && request.getParameter("plst.referenceId").trim().length() > 0)
                                    playlist.setReferenceId(request.getParameter("plst.referenceId"));

                                List<Long> videoIDs = new ArrayList<Long>();
                                for (String idStr : ids) {
                                    Long id = Long.parseLong(idStr);
                                    LOGGER.info("Video ID: " + idStr);
                                    videoIDs.add(id);
                                }
                                LOGGER.info("Writing Playlist to Media API");

                                playlist.setVideoIds(videoIDs);
                                JSONObject videoItem = brAPI.cms.createPlaylist(playlist);
                                LOGGER.info("New Playlist id: " + videoItem.toString(1));

                        } else if ("create_video".equals(requestedAPI)) {
                                String ingestURL = request.getRequestParameter("filePath_Ingest").getString();
                                String ingestProfile = request.getRequestParameter("profile_Ingest") != null ? request.getRequestParameter("profile_Ingest").getString() : "balanced-high-definition";

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
                                        request.getParameter("name"),
                                        request.getParameter("referenceId"),
                                        request.getParameter("shortDescription"),
                                        request.getParameter("longDescription"),
                                        "",
                                        tagsToAdd,
                                        null,
                                        null,
                                        false,
                                        link
                                );
                                JSONObject videoItem = brAPI.cms.createVideo(video);
                                String newVideoId = videoItem.getString("id");
                                JSONObject videoIngested = new JSONObject();
                                try {
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
                                    result.put("error", "createIngest Exception");
                                    brAPI.cms.deleteVideo(newVideoId);
                                }

                            //result = new JSONObject(serviceUtil.searchVideo("", Integer.parseInt(request.getParameter("start")), Integer.parseInt(request.getParameter("limit")), request.getParameter("sort")));
                        } else if ("update_video".equals(requestedAPI)) {

                                Collection<String> tagsToAdd = new ArrayList<String>();
                                if (request.getParameter("tags") != null) {

                                    List<String> tags = Arrays.asList(request.getParameterValues("tags"));
                                    for (String tag : tags) {
                                        if (tag.startsWith("+")) tagsToAdd.add(tag.substring(1));
                                    }

                                }
                                com.coresecure.brightcove.wrapper.objects.RelatedLink link = new com.coresecure.brightcove.wrapper.objects.RelatedLink(request.getParameter("linkText"), request.getParameter("linkURL"));
                                com.coresecure.brightcove.wrapper.objects.Video video = new com.coresecure.brightcove.wrapper.objects.Video(
                                        request.getParameter("id"),
                                        request.getParameter("name"),
                                        request.getParameter("referenceId"),
                                        request.getParameter("shortDescription"),
                                        request.getParameter("longDescription"),
                                        "",
                                        tagsToAdd,
                                        null,
                                        null,
                                        false,
                                        link
                                );
                                JSONObject videoItem = brAPI.cms.updateVideo(video);
                                //LOGGER.debug("videoItem", videoItem);

                            result = null;

                        }
                        else if("remove_text_track".equals(requestedAPI))
                        {


                            try {
                                String trackID = request.getParameter("track");
                                String videoID = request.getParameter("id");
                                LOGGER.trace("TRACK DELETION ACTIVATED FOR TRACK " + trackID);
                                //PUT TOGETHER THE TEXT TRACKS JSON OBJECT IN ORDER TO SEND
                                LOGGER.trace("VideoID: " + videoID);

                                //GET VIDEO FOR THIS VIDEO ID  - REMOVE FORM THE JSON OBJECT AND RESEND UP
                                //GET VIDEO AND UPDATE TEXT TRACKS JSON
                                JSONObject down_video = brAPI.cms.getVideo(request.getParameter("id"));

                                //DELETE THE TRACK
                                JSONArray trackslist = down_video.has("text_tracks") ? down_video.getJSONArray("text_tracks") : null;
                                String curID = "";

                                //CONSTRUCTED CORRECTLY
                                JSONArray updated_tracks = new JSONArray();

                                LOGGER.trace("OLD TRACKS LIST " + trackslist.length());
                                for(int x = 0 ; x < trackslist.length(); x++)
                                {
                                    JSONObject track = trackslist.getJSONObject(x);
                                    curID = track.getString("id");
                                    if(!trackID.equals(curID))
                                    {
                                        Text_track currentTrack = new Text_track(track);
                                        updated_tracks.put(currentTrack.toJSON());
                                    }
                                }
                                LOGGER.trace("UPDATED TRACKS LIST " + updated_tracks.length());

                                com.coresecure.brightcove.wrapper.objects.Video video = new Video(
                                        request.getParameter("id"),
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
                                LOGGER.debug("REBUILT VIDEO: "+ video.toJSON().toString(1));
                                JSONObject videoItem = brAPI.cms.updateVideo(video);
                                LOGGER.trace("RESP TXT TRACK :" + videoItem.toString(1) );
                            }
                            catch (JSONException e)
                            {
                                LOGGER.error("ERROR! JSON WTF", e);
                            }

                            result = null;
                        }
                        else if ("upload_text_track".equals(requestedAPI))
                        {

                            JSONObject text_track_payload = new JSONObject();
                            JSONArray text_track_arr = new JSONArray();
                            JSONObject text_track = new JSONObject();

                            text_track.put("srclang", request.getParameter("track_lang"));
                            text_track.put("kind", request.getParameter("track_kind"));
                            String label = request.getParameter("track_label");
                            if (label!= null && !label.isEmpty()) text_track.put("label", label);
                            text_track.put("default", "true".equals(request.getParameter("track_default")));
                            text_track.put("mime_type", request.getParameter("track_mime_type"));
                            //LOGGER.trace(text_track.toString(1));


                            //FILE UPLOAD CASE***
                            //HERE IT GETS THE TRACK SOURCE - HANDLE CASE OF FILE UPLOAD
                            if("".equals(request.getParameter("track_source")) && !"".equals(request.getParameter("track_filepath"))  )
                            {
                                LOGGER.trace("FILEPATH: "  + request.getParameter("track_filepath"));
                                //DO PUSH OF THE FILE GIVEN THE FILEPATH AND THEN PUSH THE NEW OBJECT TRACK TO VIDEO AS BEFORE
                                //CHECK THAT IT IS A VTT FILE??? END OF NAME???

                                InputStream is = new ByteArrayInputStream(request.getParameter("track_filepath").getBytes("UTF-8" ));

                                //REQUEST INGEST URL
                                JSONObject s3_url_resp = serviceUtil.createAssetS3(request.getParameter("id"),label.replaceAll(" ","_")+".vtt", is);
                                //IF SUCCESS
                                if (s3_url_resp != null && s3_url_resp.has("sent") && s3_url_resp.getBoolean("sent"))
                                {
                                    //text_track.put("url", s3_url_resp.getString("signed_url"));
                                    text_track.put("url", s3_url_resp.getString("api_request_url"));
                                    LOGGER.trace("S3URLRESP: " + s3_url_resp.toString(1));
                                }
                                else
                                {
                                    LOGGER.error("FAILED TO INITIALIZE BUCKET");
                                }

                            }
                            else if (!"".equals(request.getParameter("track_source")))
                            {
                                LOGGER.trace("SOURCEPATH: " +request.getParameter("track_source"));

                                text_track.put("url", request.getParameter("track_source"));

                            }

                            text_track_arr.put(text_track);
                            text_track_payload.put("text_tracks", text_track_arr);


                            JSONObject videoItem = brAPI.cms.uploadInjest(request.getParameter("id"), text_track_payload);
                            //DEBUGGER PRINT - LOGGER.trace("**:" + videoItem.toString(1));

                            if(videoItem.has("response"))
                            {
                                JSONObject responseOBJ = new JSONObject(videoItem.getString("response"));
                                LOGGER.trace("**has id object: " + responseOBJ.has("id") );
                                //response.sendError(422, "Incompatible Payload for Audio Track");


                                LOGGER.trace("Text Track Upload Complete");

                            } else {
                                response.sendError(500, "Check logs");
                            }
                            result = null;



                        } else if ("upload_image".equals(requestedAPI)) {
                            LOGGER.trace("upload_thumbnail");


                            JSONObject images_payload = new JSONObject();

                            if (request.getParameter("thumbnail_source") != null) {
                                JSONObject thumbnail = new JSONObject();
                                thumbnail.put("url", request.getParameter("thumbnail_source"));
                                images_payload.put("thumbnail", thumbnail);
                            }
                            if (request.getParameter("poster_source") != null) {
                                JSONObject poster = new JSONObject();
                                poster.put("url", request.getParameter("poster_source"));
                                images_payload.put("poster", poster);
                            }

                            LOGGER.trace("UploadImagesPayload>>" + images_payload.toString(1));

                            JSONObject videoItem = brAPI.cms.uploadInjest(request.getParameter("id"), images_payload);
                            LOGGER.trace(videoItem.toString(1));

                            result = null;

                        } else {
                            result.put("error", 404);
                        }
                    } else {
                        result.put("error", 403);
                    }
                } else {
                    result.put("error", 404);
                }
            } else {
                result.put("error", 400);
            }
            resultstr = result != null ? result.toString(): "";
        } catch (JSONException je) {
            LOGGER.error("JSONException", je);
            resultstr = "{\"items\":[],\"totals\":0,\"error\":500}";
        }
        if (result!= null) {
            if (js) {
                response.setContentType("text/javascript;charset=UTF-8");
                String callback = request.getParameter("callback");
                if (callback == null || callback.isEmpty() || callback.matches("[^0-9a-zA-Z\\$_]|^(abstract|boolean|break|byte|case|catch|char|class|const|continue|debugger|default|delete|do|double|else|enum|export|extends|false|final|finally|float|for|function|goto|if|implements|import|in|instanceof|int|interface|long|native|new|null|package|private|protected|public|return|short|static|super|switch|synchronized|this|throw|throws|transient|true|try|typeof|var|volatile|void|while|with|NaN|Infinity|undefined)$")) {
                    callback = "callback";
                }
                response.getWriter().write(callback + "(" + resultstr + ");");
            } else {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(resultstr);
            }
        } else {
            //NOTE : This had to be added to fix a limitation of etx.js where a json response was non acceptable on file submission
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("true");
        }
    }


}
