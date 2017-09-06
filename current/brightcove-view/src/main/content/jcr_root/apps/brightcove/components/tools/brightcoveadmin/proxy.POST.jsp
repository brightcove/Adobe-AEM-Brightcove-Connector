<%--

    Adobe CQ5 Brightcove Connector

    Copyright (C) 2015 Coresecure Inc.

        Authors:    Alessandro Bonfatti
                    Yan Kisen

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

--%>
<%@page trimDirectiveWhitespaces="true"
        import="com.brightcove.proserve.mediaapi.wrapper.ReadApi,
                com.brightcove.proserve.mediaapi.wrapper.WriteApi,
                com.brightcove.proserve.mediaapi.wrapper.apiobjects.Image,
                com.brightcove.proserve.mediaapi.wrapper.apiobjects.Playlist,
                com.brightcove.proserve.mediaapi.wrapper.apiobjects.Video,
                com.brightcove.proserve.mediaapi.wrapper.apiobjects.enums.*,
                com.brightcove.proserve.mediaapi.wrapper.utils.CollectionUtils,
                com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber,
                com.coresecure.brightcove.wrapper.sling.ConfigurationService,
                com.coresecure.brightcove.wrapper.sling.ServiceUtil,
                org.apache.sling.api.request.RequestParameter,
                org.apache.sling.commons.json.JSONObject,
                org.slf4j.Logger,
                org.slf4j.LoggerFactory,
                java.io.File,
                java.io.FileOutputStream,
                java.io.InputStream,
                java.util.*" %>

<%@include file="/libs/foundation/global.jsp" %>

<%

    ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
    Set<String> services = cg.getAvailableServices();
    String defaultAccount = (String) services.toArray()[0];
    String cookieAccount = ServiceUtil.getAccountFromCookie(slingRequest);
    String selectedAccount = (cookieAccount.trim().isEmpty()) ? defaultAccount : cookieAccount;

    ConfigurationService cs = cg.getConfigurationService(selectedAccount);
    com.coresecure.brightcove.wrapper.BrightcoveAPI brAPI = new com.coresecure.brightcove.wrapper.BrightcoveAPI(cs.getClientID(), cs.getClientSecret(), selectedAccount);

    if (cs.getProxy()!=null && cs.getProxy().length()>0) {
      brAPI.setProxy(cs.getProxy());
    }
    File tempDir = new File (cs.getTempPath());

    String ReadToken = cs.getReadToken();
    String WriteToken = cs.getWriteToken();
    response.reset();
    response.setContentType("application/json");
    UUID uuid = new UUID(64L, 64L);
    String RandomID = new String(uuid.randomUUID().toString().replaceAll("-", ""));

    final List<String> write_methods = Arrays.asList(new String[]{"create_video", "update_video", "get_upload_status", "create_playlist", "update_playlist", "share_video", "add_image", "add_video_image"});

    final String apiReadToken = ReadToken;
    final String apiWriteToken = WriteToken;
    String apiToken = apiReadToken;
    String[] ids = null;
    Logger logger = LoggerFactory.getLogger("Brightcove");
    response.setContentType("text/html");
    WriteApi wapi = new WriteApi(logger);
    ReadApi rapi = new ReadApi(logger);
    if (cs.getProxy()!=null && cs.getProxy().length()>0) {
      wapi.setProxy(cs.getProxy());
      rapi.setProxy(cs.getProxy());
    }

    boolean success = false;
    JSONObject root = new JSONObject();
    String msg = "";


    if (slingRequest.getMethod().equals("POST")) {
        String command = slingRequest.getRequestParameter("command").getString();
        logger.info(command + "   " + String.valueOf(write_methods.indexOf(command)));
        if (write_methods.contains(command)) {
            apiToken = apiWriteToken;
            Long VideoId = null;
            File tempImageFile = null;
            InputStream fileImageStream;
            RequestParameter thumbnailFile = null;
            String thumbnailFilename = null;
            FileOutputStream outImageStream = null;
            byte[] imagebuf = null;


            switch (write_methods.indexOf(command)) {
                case 0:
                    //"create_video"
                    String ingestURL = slingRequest.getRequestParameter("filePath_Ingest").getString();
                    String ingestProfile = slingRequest.getRequestParameter("profile_Ingest") != null ? slingRequest.getRequestParameter("profile_Ingest").getString() : "balanced-high-definition";
                    if (ingestURL != null && !ingestURL.trim().isEmpty()) {

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
                                logger.info("New video id: '" + newVideoId + "'.");
                                success = true;
                                root.put("videoid", newVideoId);
                                root.put("output", videoIngested);
                            } else {
                                success = false;
                                msg = "createIngest Error";
                                brAPI.cms.deleteVideo(newVideoId);
                            }

                        } catch (Exception exIngest) {
                            success = false;
                            msg = "createIngest Exception";
                            brAPI.cms.deleteVideo(newVideoId);
                        }
                    } else {
                        File tempFile = null;
                        InputStream fileStream;
                        Video video = new Video();
                        RequestParameter videoFile = slingRequest.getRequestParameter("filePath");
                        String videoFilename = RandomID + "_" + videoFile.getFileName().replaceAll("[^a-zA-Z0-9\\._]+", "_");
                        fileStream = videoFile.getInputStream();
                        tempFile = new File(tempDir, videoFilename);
                        FileOutputStream outStream = new FileOutputStream(tempFile);
                        byte[] buf = new byte[1024];
                        //Fortify Fix
                        try{
                            outStream = new FileOutputStream(tempFile);
                            for (int byLen = 0; (byLen = fileStream.read(buf, 0, 1024)) > 0; ) {
                                outStream.write(buf, 0, byLen);
                                //if(tempFile.length()/1000 > 2){}//maximum file size is 2gigs
                            }
                        }finally{
                            if(outStream!=null)
                                outStream.close();
                        }
                        // Required fields
                        video.setName(request.getParameter("name"));
                        video.setShortDescription(request.getParameter("shortDescription"));

                        video.setItemState(ItemStateEnum.ACTIVE);
                        video.setLinkText(request.getParameter("linkText"));
                        video.setLinkUrl(request.getParameter("linkURL"));
                        video.setLongDescription(request.getParameter("longDescription"));
                        video.setReferenceId(request.getParameter("referenceId"));


                        if (request.getParameter("tags") != null) {

                            List<String> tags = Arrays.asList(request.getParameterValues("tags"));
                            List<String> tagsToAdd = new ArrayList<String>();
                            for (String tag : tags) {
                                if (tag.startsWith("+")) tagsToAdd.add(tag.substring(1));
                            }
                            video.setTags(tagsToAdd);

                        }
                        // Some miscellaneous fields for the Media API (not the video objects)
                        Boolean createMultipleRenditions = true;
                        Boolean preserveSourceRendition = true;
                        Boolean h264NoProcessing = false;

                        try {
                            // Write the video
                            logger.info("Writing video to Media API");
                            Long newVideoId = wapi.CreateVideo(apiToken, video, tempFile.getAbsolutePath(), TranscodeEncodeToEnum.FLV, createMultipleRenditions, preserveSourceRendition, h264NoProcessing);
                            logger.info("New video id: '" + newVideoId + "'.");
                            tempFile.delete();
                            success = true;
                            root.put("videoid", newVideoId);


                        } catch (Exception e) {
                            logger.error("Exception caught: '" + e + "'.");
                        }
                    }
                    break;
                case 1:
                    Video video = new Video();
                    EnumSet<VideoFieldEnum> videoFields = VideoFieldEnum.CreateEmptyEnumSet();
                    videoFields.add(VideoFieldEnum.ID);
                    videoFields.add(VideoFieldEnum.NAME);
                    videoFields.add(VideoFieldEnum.SHORTDESCRIPTION);
                    videoFields.add(VideoFieldEnum.LINKTEXT);
                    videoFields.add(VideoFieldEnum.LINKURL);
                    videoFields.add(VideoFieldEnum.ECONOMICS);
                    videoFields.add(VideoFieldEnum.REFERENCEID);
                    videoFields.add(VideoFieldEnum.TAGS);

                    Set<String> customFields = CollectionUtils.CreateEmptyStringSet();
                    Long videoId = Long.parseLong(slingRequest.getRequestParameter("meta.id").getString());

                    video = rapi.FindVideoById(apiReadToken, videoId, videoFields, customFields);
                    // Required fields
                    String name = new String(request.getParameter("meta.name").getBytes("iso-8859-1"), "UTF-8");
                    video.setName(name);
                    String shortDescription = new String(request.getParameter("meta.shortDescription").getBytes("iso-8859-1"), "UTF-8");
                    shortDescription = shortDescription.replaceAll("\n", "");
                    video.setShortDescription(shortDescription);
                    logger.info("description: " + shortDescription);
                    // Optional fields
                    video.setLinkText(request.getParameter("meta.linkText"));
                    video.setLinkUrl(request.getParameter("meta.linkURL"));
                    video.setEconomics(EconomicsEnum.valueOf(request.getParameter("meta.economics")));
                    video.setReferenceId(request.getParameter("meta.referenceId"));


                    List<String> tagsToAdd = new ArrayList<String>();

                    if (request.getParameter("meta.existingTags") != null && !request.getParameter("meta.existingTags").trim().isEmpty()) {
                        tagsToAdd.addAll(Arrays.asList(request.getParameter("meta.existingTags").split(",")));
                    }


                    if (request.getParameter("meta.tags") != null) {

                        List<String> tags = Arrays.asList(request.getParameterValues("meta.tags"));
                        for (String tag : tags) {
                            if (tag.startsWith("+")) {
                                tagsToAdd.add(tag.substring(1));
                            } else if (tag.startsWith("-")) {
                                tagsToAdd.remove(tag.substring(1));

                            }
                        }


                    }
                    video.setTags(tagsToAdd);

                    try {
                        // Write the video
                        logger.info("Updating video to Media API " + shortDescription);
                        Video responseUpdate = wapi.UpdateVideo(apiToken, video);
                        logger.info("Updated video: '" + responseUpdate.getId() + "'.");
                        success = true;
                        root.put("videoid", responseUpdate.getId());

                    } catch (Exception e) {
                        logger.error("Exception caught: '" + e + "'.");

                    }
                    break;
                case 3:
                    ids = slingRequest.getRequestParameter("playlist").getString().split(",");

                    logger.info("Creating a Playlist");
                    Playlist playlist = new Playlist();
                    // Required fields
                    playlist.setName(request.getParameter("plst.name"));
                    playlist.setShortDescription(request.getParameter("plst.shortDescription"));
                    playlist.setPlaylistType(PlaylistTypeEnum.EXPLICIT);
                    // Optional Fields
                    if (request.getParameter("plst.referenceId") != null && request.getParameter("plst.referenceId").trim().length() > 0)
                        playlist.setReferenceId(request.getParameter("plst.referenceId"));
                    if (request.getParameter("plst.thumbnailURL") != null && request.getParameter("plst.thumbnailURL").trim().length() > 0)
                        playlist.setThumbnailUrl(request.getParameter("plst.thumbnailURL"));

                    List<Long> videoIDs = new ArrayList<Long>();
                    for (String idStr : ids) {
                        Long id = Long.parseLong(idStr);
                        logger.info("Video ID: " + idStr);
                        videoIDs.add(id);
                    }
                    logger.info("Writing Playlist to Media API");

                    playlist.setVideoIds(videoIDs);
                    Long newPlaylistId = wapi.CreatePlaylist(apiToken, playlist);
                    logger.info("New Playlist id: '" + newPlaylistId + "'.");

                    break;
                case 6:
                    VideoId = Long.valueOf(request.getParameter("videoidthumb"));
                    thumbnailFile = slingRequest.getRequestParameter("filePath");
                    thumbnailFilename = RandomID + "_" + thumbnailFile.getFileName().replaceAll("[^a-zA-Z0-9\\._]+", "_");
                    fileImageStream = thumbnailFile.getInputStream();
                    tempImageFile = new File(tempDir, thumbnailFilename);
                    try{
                        outImageStream = new FileOutputStream(tempImageFile);
                        imagebuf = new byte[1024];
                        for (int byLen = 0; (byLen = fileImageStream.read(imagebuf, 0, 1024)) > 0; ) {
                            outImageStream.write(imagebuf, 0, byLen);
                            //if(tempFile.length()/1000 > 2){}//maximum file size is 2gigs
                        }
                    }finally{
                        //Fortify Fix
                        if(null != outImageStream)
                            outImageStream.close();
                    }
                    // Required fields
                    // Image meta data
                    Image thumbnail = new Image();
                    //Image videoStill = new Image();

                    thumbnail.setReferenceId(request.getParameter("referenceId"));
                    //videoStill.setReferenceId(request.getParameter("referenceId"));

                    thumbnail.setDisplayName(request.getParameter("name"));
                    //videoStill.setDisplayName(request.getParameter("name"));

                    thumbnail.setType(ImageTypeEnum.THUMBNAIL);
                    //videoStill.setType(ImageTypeEnum.VIDEO_STILL);

                    try {
                        // Write the image
                        Boolean resizeImage = false;

                        Image thumbReturn = wapi.AddImage(apiWriteToken, thumbnail, tempImageFile.getAbsolutePath(), VideoId, null, resizeImage);
                        logger.info("Thumbnail image: " + thumbReturn + ".");
                        //Image stillReturn = wapi.AddImage(apiWriteToken, videoStill, thumbnailFilename, VideoId, null, resizeImage);
                        //logger.info("Video still image: " + stillReturn + ".");

                        tempImageFile.delete();


                    } catch (Exception e) {
                        logger.error("Exception caught: '" + e + "'.");

                    }
                    break;
                case 7:
                    VideoId = Long.valueOf(request.getParameter("videoidthumb"));
                    thumbnailFile = slingRequest.getRequestParameter("filePath");
                    thumbnailFilename = RandomID + "_" + thumbnailFile.getFileName().replaceAll("[^a-zA-Z0-9\\._]+", "_");
                    fileImageStream = thumbnailFile.getInputStream();
                    tempImageFile = new File(tempDir, thumbnailFilename);
                    try{
                        outImageStream = new FileOutputStream(tempImageFile);
                        imagebuf = new byte[1024];
                        for (int byLen = 0; (byLen = fileImageStream.read(imagebuf, 0, 1024)) > 0; ) {
                            outImageStream.write(imagebuf, 0, byLen);
                            //if(tempFile.length()/1000 > 2){}//maximum file size is 2gigs
                        }
                    }finally{
                        //Fortify Fix
                        if(null != outImageStream)
                            outImageStream.close();
                    }
                    // Required fields
                    // Image meta data
                    Image videoStill = new Image();

                    videoStill.setReferenceId(request.getParameter("referenceId"));

                    videoStill.setDisplayName(request.getParameter("name"));

                    videoStill.setType(ImageTypeEnum.VIDEO_STILL);

                    try {
                        // Write the image
                        Boolean resizeImage = false;

                        Image stillReturn = wapi.AddImage(apiWriteToken, videoStill, tempImageFile.getAbsolutePath(), VideoId, null, resizeImage);
                        logger.info("Video still image: " + stillReturn + ".");

                        tempImageFile.delete();

                    } catch (Exception e) {
                        logger.error("Exception caught: '" + e + "'.");

                    }
                    break;
            }
        }

    }

    root.put("success", success);
    root.put("msg", msg);
    out.write(root.toString());
%>
