package com.coresecure.brightcove.wrapper.webservices;

/**
 * Created by pablo.kropilnicki on 7/13/17.
 */



import com.coresecure.brightcove.wrapper.enums.GeoFilterCodeEnum;
import com.coresecure.brightcove.wrapper.objects.Geo;
import com.coresecure.brightcove.wrapper.objects.Schedule;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.AccountUtil;
import com.coresecure.brightcove.wrapper.utils.S3UploadUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.day.cq.replication.*;
import org.apache.felix.scr.annotations.*;
import com.coresecure.brightcove.wrapper.objects.Video;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.*;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.coresecure.brightcove.wrapper.objects.RelatedLink;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@Service(TransportHandler.class)
@Component(label = "Brightcove: Replication Agents", immediate = true, metatype = true)
public class BrcReplicationHandler implements TransportHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrcReplicationHandler.class);
    /** Protocol for replication agent transport URI that triggers this transport handler. */
    private static Map<String, Object> properties;

    private static final String DEFAULT_BRIGHTCOVE_PROTOCOL = "brightcove://";
    @Property(label = "Azure Replication Protocol", value = DEFAULT_BRIGHTCOVE_PROTOCOL)
    public static final String BRIGHTCOVE_PROTOCOL = "brightcove_protocol";


    private static final String DEFAULT_TARGET_DIRECTORY = "/content/dam";
    @Property(label = "Target Directory", description = "Add the folder to process this action on. (Limiting to specific folders in the DAM will improve performance)", value = DEFAULT_TARGET_DIRECTORY)
    public static final String TARGET_DIRECTORY = "target_directory";


    @Reference
    ConfigurationGrabber configurationGrabber;


    ConfigurationService cs;



            @Reference
    ResourceResolverFactory resourceResolverFactory;

    ReplicationLog replicationLog = null;

    @Activate
    protected final void activate(final Map<String, Object> properties) throws Exception {
        LOGGER.debug("activate");
        // Read in OSGi Properties for use by the OSGi Service in the Activate method
        update(properties);
    }

    @Deactivate
    protected final void deactivate(final Map<String, String> properties) {
        LOGGER.debug("deactivate");
        // Remove method if not used
    }

    @Modified
    protected void update(final Map<String, Object> properties) {
        this.properties = properties;
    }

    private Map<String, Object> getProperties() {
        if (this.properties == null) {
            return new Hashtable<String, Object>();
        }

        return this.properties;
    }

    public String getTargetDirectory() {
        return PropertiesUtil.toString(getProperties().get(TARGET_DIRECTORY), DEFAULT_TARGET_DIRECTORY);
    }

    public String getBrightcoveProtocol() {
        return PropertiesUtil.toString(getProperties().get(BRIGHTCOVE_PROTOCOL), DEFAULT_BRIGHTCOVE_PROTOCOL);
    }

    @Override
    public boolean canHandle(AgentConfig agentConfig)
    {
        final String transportURI = agentConfig.getTransportURI();
        return (agentConfig.isEnabled() && (transportURI != null) ? transportURI.toLowerCase().startsWith(getBrightcoveProtocol()) : false);
    }
    @Override
    public ReplicationResult deliver(TransportContext ctx, ReplicationTransaction tx) throws ReplicationException
    {
        replicationLog = tx.getLog();
        ReplicationResult result = null;

        //Global Assignments
        ReplicationAction replicationAction = tx.getAction();
        if (replicationAction != null)
        {
            final ReplicationActionType replicationType = replicationAction.getType();
            if (replicationType != ReplicationActionType.DELETE)
            {
                final String[] assetPaths = replicationAction.getPaths();


                //REPLOGS
                replicationLog.debug("Deliver Method Successfully Called");
                replicationLog.debug(ctx.getName() + tx.toString());
                replicationLog.debug(ctx.getConfig().toString() + " " + ctx.getConfig().getTransportUser() + " " + replicationAction.getUserId());
                replicationLog.debug("Action: " + replicationAction.getType().toString());
                replicationLog.debug("Path: " + replicationAction.getPath().toString());
                replicationLog.debug("Time: " + replicationAction.getTime());
                replicationLog.debug(Arrays.toString(assetPaths));

                //SERVICELOGS
                LOGGER.debug("Deliver Method Successfully Called");
                LOGGER.debug(ctx.getName() + tx.toString());
                LOGGER.debug(ctx.getConfig().toString() + " " + ctx.getConfig().getTransportUser() + " " + replicationAction.getUserId());
                LOGGER.debug("Action: " + replicationAction.getType().toString());
                LOGGER.debug("Path: " + replicationAction.getPath().toString());
                LOGGER.debug("Time: " + replicationAction.getTime());
                LOGGER.debug(Arrays.toString(assetPaths));

                //GET ACTION TYPE
                LOGGER.trace("ACTION-TYPE: " + replicationType + " @ " + assetPaths);

                //GET VIDEOS FOR ACTION - (ERASE MIGHT BE NO VIDEO AT ALL?)


                String path = replicationAction.getPath();
                String targetDirectory = getTargetDirectory();

                if (!path.startsWith(targetDirectory))
                {
                    LOGGER.debug(String.format("SKIP -- Asset: %s is not in Target Directory: %s ", path, targetDirectory));
                    return ReplicationResult.OK;
                }
                else {
                    LOGGER.debug("READY TO REPLICATE! RESOURCE IS A BRIGHTCOVE RESOURCE");
                    try {
                        ResourceResolver rr = resourceResolverFactory.getAdministrativeResourceResolver(null);
                        Resource asset_res;


                        for (String current_path : assetPaths) {
                            asset_res = rr.getResource(current_path);
                            if (asset_res != null) {
                                String account_id = asset_res.getParent().getName();
                                Asset _asset = asset_res.adaptTo(Asset.class);

                                LOGGER.trace(account_id);

                                cs = configurationGrabber.getConfigurationService(account_id);
                                if (cs != null) {
                                    List<String> allowedGroups = cs.getAllowedGroupsList();

                                    //AUTHORIZATION CHECK
                                    boolean is_authorized = false;
                                    UserManager userManager = rr.adaptTo(UserManager.class);
                                    try {
                                        Authorizable auth = userManager.getAuthorizable(replicationAction.getUserId());

                                        if (auth != null) {
                                            Iterator<Group> groups = auth.memberOf();
                                            while (groups.hasNext() && !is_authorized) {
                                                Group group = groups.next();
                                                if (allowedGroups.contains(group.getID()))
                                                    is_authorized = true; //<-Authorization
                                            }
                                        }
                                    } catch (RepositoryException e) {
                                        LOGGER.error("executeRequest", e);
                                        result = new ReplicationResult(false, 0, "Replication error: " + e.getMessage());

                                    }
                                    //tag amanger
                                    //get tags
                                    //do check of funciton allreayd implemented

                                    if (is_authorized) {
                                        Resource metadataRes = asset_res.getChild("jcr:content/metadata");

                                        String[] tagsList = metadataRes.getValueMap().get("cq:tags", String[].class);
                                        Collection<String> tags = tagsToCollection(tagsList);

                                        if (assetPaths != null && _asset != null && path.startsWith(cs.getAssetIntegrationPath())) {//isBrightcoveAsset(tags)) {

                                            //ACTION SWITCH
                                            if (replicationType == ReplicationActionType.TEST) {
                                                return testVideo();
                                            } else if (replicationType == ReplicationActionType.ACTIVATE) {
                                                //TESTING
                                                result = activateVideo(_asset, account_id);
                                            } else if (replicationType == ReplicationActionType.DEACTIVATE) {
                                                result = deactivateVideo(_asset, account_id);
                                            } else {
                                                //return ReplicationResult.OK;
                                                throw new ReplicationException("Replication action type " + replicationType + " not supported.");
                                            }

                                        } else {
                                            LOGGER.debug("No Brightcove Tag in cq:tags", Arrays.toString(tagsList));

                                        }
                                    } else {
                                        LOGGER.debug("Not authorized");
                                    }
                                } else {
                                    LOGGER.debug("Not Brightcove");
                                    return ReplicationResult.OK;
                                }
                            } else {
                                LOGGER.warn("Asset removed or not existing");
                                result = ReplicationResult.OK;
                            }
                        }
                        rr.commit();
                    } catch (LoginException e) {
                        LOGGER.error("LoginException: ", e);
                        replicationLog.error("Replication action type " + replicationType + " LoginException.");
                        throw new ReplicationException("Replication action type " + replicationType + " LoginException.");
                    } catch (NullPointerException e) {
                        LOGGER.error("NullPointer RepHandler", e);
                        replicationLog.error("Replication action type " + replicationType + " NullPointer RepHandler.");
                        throw new ReplicationException("Replication action type " + replicationType + " NullPointer RepHandler.");

                    } catch (ReplicationException e) {
                        LOGGER.error("ReplicationException - RepHandler", e);
                        replicationLog.error("Replication action type " + replicationType + " ReplicationException - RepHandler.");
                        throw new ReplicationException("Replication action type " + replicationType + " ReplicationException - RepHandler.");
                    } catch (Exception e) {
                        LOGGER.error("Exception RepHandler", e);
                        replicationLog.error("Replication action type " + replicationType + " Exception RepHandler.");
                        throw new ReplicationException("Replication action type " + replicationType + " Exception RepHandler.");
                    }

                    //END FORLOOP
                }
            }
            else
            {
                LOGGER.trace("DELETE REPLICATION NOT SUPPORTED");
                result =  ReplicationResult.OK;
            }

        //END MAIN
        }
        //result =  ReplicationResult.OK; - default ok
        //result =  ReplicationResult.OK;
        replicationLog.info("REPLICATION STATUS >> Code: %s Message: %s Success: %s", result.getCode(), result.getMessage(), result.isSuccess());

        return result;
    }

    private ReplicationResult doTest(TransportContext ctx, ReplicationTransaction tx, String accountId) throws ReplicationException
    {
        replicationLog.debug("TEST");
        return new ReplicationResult(false, 0, "Replication test not available");
    }

    private ReplicationResult doActivate(TransportContext ctx, ReplicationTransaction tx, String accountId, Map<String, Object> propertiesMap) throws ReplicationException
    {
        ReplicationResult result = null;
        String path = tx.getAction().getPath();
        String activatorUserID=tx.getAction().getUserId();
        replicationLog.info("ACTIVATING >> User: %s Path: %s Account ID: %s", activatorUserID,path,accountId);

        boolean success = false;
        try {
                result = ReplicationResult.OK;

        }
        catch (Exception e)
        {
            replicationLog.error("Replication Failed" , e );
            result =  new ReplicationResult(false, 0, "Replication failed: "+e.getMessage());
        }
        return result;
    }

    private ReplicationResult doDeactivate(TransportContext ctx, ReplicationTransaction tx, String accountId, Map<String, Object> propertiesMap) throws ReplicationException {
        ReplicationResult result = null;
        String path = tx.getAction().getPath();
        String activatorUserID=tx.getAction().getUserId();
        replicationLog.info("DE-ACTIVATING[account:"+accountId+"]>>%s" ,path );
        boolean success = false;
        try
        {
                result =  ReplicationResult.OK;
        }
        catch (Exception e) {
            replicationLog.error("Deactivation Failed" , e );
            result =  new ReplicationResult(false, 0, "Replication failed: "+e.getMessage());
        }
        return result;
    }
    private ReplicationResult doDelete(TransportContext ctx, ReplicationTransaction tx, String accountId, Map<String, Object> propertiesMap)
            throws ReplicationException {
        return doDeactivate(ctx, tx, accountId, propertiesMap);
    }


    private ReplicationResult activateVideo(Asset _asset, String account_id)
    {
        ServiceUtil serviceUtil = new ServiceUtil(account_id);


        ReplicationResult result = null;
        replicationLog.info("ACTIVATING >> "+_asset.getName() +" ");
        String path = _asset.getPath();


        Long jcr_lastmod = _asset.getLastModified();
        //String brc_lastsync = _asset.getMetadataValue("brc_lastsync");

        Video video = createVideo(path, _asset, "ACTIVE");



        Resource metadataRes = _asset.adaptTo(Resource.class).getChild("jcr:content/metadata");
        ModifiableValueMap brc_lastsync_map = metadataRes.adaptTo(ModifiableValueMap.class); //RETURNED NULL EACH TIME
        Long brc_lastsync_time = Long.parseLong(brc_lastsync_map.get("brc_lastsync","0"));


        brc_lastsync_map.put("brc_state","ACTIVE");

        LOGGER.trace("0 to long = " + Long.parseLong("0"));
        LOGGER.debug("brc_lastsync: "+ brc_lastsync_map.get("brc_lastsync",""));




            LOGGER.trace("JCRLASTMOD>>:     " + jcr_lastmod);
            LOGGER.trace("BRCLASTSYNC>>:    " + brc_lastsync_time );

            if (brc_lastsync_time == null || brc_lastsync_time <= 0)
            {
                LOGGER.trace("brc_lastsync was NULL!!!!!!! OR ZERO");
                try {
                InputStream is = _asset.getOriginal().getStream();      //VIDEO ORIGINAL BINARY FOR BC DATABASE
                //FOR POSTER/THUMBNAIL UPLOAD
                InputStream poster_rendition = _asset.getRendition("brc_poster.png") != null ? _asset.getRendition("brc_poster.png").getStream() : null;
                InputStream thumbnail_rendition = _asset.getRendition("brc_thumbnail.png") != null ? _asset.getRendition("brc_thumbnail.png").getStream(): null;


                JSONObject api_resp = serviceUtil.createVideoS3(video, _asset.getName(), is); //ACTUAL VIDEO UPLOAD CALL - WITH METADATA

                    //LOGGER.trace("API-RESP >>" + api_resp.toString(1));
                    boolean sent = api_resp.getBoolean("sent");
                    if (sent) {
                        replicationLog.info("BC: ACTIVATION SUCCESSFUL >> " + _asset.getPath());
                        result = ReplicationResult.OK;
                        long current_time_millisec = new java.util.Date().getTime();
                        brc_lastsync_map.put("brc_lastsync", current_time_millisec);
                        brc_lastsync_map.put("brc_id", api_resp.getString("videoid"));
                        //rr.commit()?
                    } else {
                        replicationLog.error("BC: ACTIVATION FAILED >> " + _asset.getName());
                        result = new ReplicationResult(false, 0, "Replication failed: S3UploadUtil.uploadFile");
                    }
                } catch (Exception e) {
                    result = new ReplicationResult(false, 0, "Replication failed: " + e.getMessage());
                }
            }
            else if(jcr_lastmod > brc_lastsync_time)
            {
                //https://cms.api.brightcove.com/v1/accounts/:account_id/videos/:video_id/assets/poster
                try
                {
                    LOGGER.trace("CREATE VIDEO - THUMBNAIL / POSTER TEST>>");
                    LOGGER.trace(video.toJSON().toString(1));
                    JSONObject images = new JSONObject();
                    JSONObject poster = new JSONObject();
                    JSONObject thumbnail = new JSONObject();
                    JSONObject poster_src = new JSONObject();
                    poster_src.put("src","null");
                    poster_src.put("remote",false);
                    poster_src.put("width","");
                    //do update video
                    JSONObject api_resp = serviceUtil.updateVideo(video); //ONLY UPDATE METADATA - DO NOT SEND BINARY
                    //LOGGER.trace("API-RESP >>"+api_resp.toString(1));
                    boolean sent = api_resp.getBoolean("sent");
                    if (sent)
                    {
                        replicationLog.info("BC: ACTIVATION SUCCESSFUL >> "+_asset.getPath());
                        long current_time_millisec = new java.util.Date().getTime();
                        brc_lastsync_map.put("brc_lastsync", current_time_millisec);
                        result = ReplicationResult.OK;
                    }
                    else
                    {
                        replicationLog.error("BC: ACTIVATION FAILED >> "+_asset.getName());
                        result = new ReplicationResult(false, 0, "Replication failed: S3UploadUtil.uploadFile");
                    }
                }
                catch (Exception e)
                {
                    result =  new ReplicationResult(false, 0, "Replication failed: "+e.getMessage());
                }
            }
        return result;
    }

    private ReplicationResult deactivateVideo(Asset _asset, String account_id)
    {

        ServiceUtil serviceUtil = new ServiceUtil(account_id);
        ReplicationResult result = null;

        Resource metadataRes = _asset.adaptTo(Resource.class).getChild("jcr:content/metadata");
        ModifiableValueMap brc_lastsync_map = metadataRes.adaptTo(ModifiableValueMap.class); //RETURNED NULL EACH TIME
        Long brc_lastsync_time = Long.parseLong(brc_lastsync_map.get("brc_lastsync","0"));

        brc_lastsync_map.put("brc_state","INACTIVE");

        replicationLog.info("DEACTIVATING >> "+_asset.getName() +" ");
        String path = _asset.getPath();

        Video video = createVideo(path, _asset, "INACTIVE");

        LOGGER.trace("VIDEO GOING TO API CALL>>>>>>>"+video.toString());

//        UPDATE VIDEO CREATES A NEW VIDEO CORRECTLY INACTIVE AS THE ONE ABOVE
        //JSONObject update_resp = serviceUtil.updateVideo(video); //

        //MUST BE DEACTIVATION CALL
        try
        {
            LOGGER.trace("DEACTIVATION");
            LOGGER.trace(video.toString());
            JSONObject update_resp = serviceUtil.updateVideo(video);
            boolean sent = true ;//update_resp.getBoolean("sent");
            if (sent)
            {
                replicationLog.info("BC: ACTIVATION SUCCESSFUL >> "+_asset.getPath());
                result = ReplicationResult.OK;
            }
            else
            {
                replicationLog.error("BC: ACTIVATION FAILED >> "+_asset.getName());
                result = new ReplicationResult(false, 0, "Replication failed: S3UploadUtil.uploadFile");
            }
        }
        catch (Exception e)
        {
            result =  new ReplicationResult(false, 0, "Replication failed: "+e.getMessage());
        }
        result = ReplicationResult.OK;
        return result;
    }

    private ReplicationResult testVideo()
    {

        LOGGER.trace(">>TEST VIDEO");
        return ReplicationResult.OK;
    }

    private ReplicationResult deleteVideo(Video video, InputStream is)
    {

        LOGGER.trace(">>DELETE VIDEO");
        return ReplicationResult.OK;
    }




    private Video createVideo(String request, Asset asset, String aState)
    {
        LOGGER.trace("VIDEO CREATION CALLED FOR"  + asset.getName() + " req? : " + request);

        Resource assetRes = asset.adaptTo(Resource.class);
        Resource metadataRes = assetRes.getChild("jcr:content/metadata");
        //SUB ASSETS
        Resource links_node =  metadataRes.getChild("brc_link") != null ? metadataRes.getChild("brc_link") : null;
        Resource schedule_node = metadataRes.getChild("brc_schedule") != null ? metadataRes.getChild("brc_schedule") : null;
        Resource geo_node = metadataRes.getChild("brc_geo")!= null ? metadataRes.getChild("brc_geo") : null;
        Resource custom_node = metadataRes.getChild("brc_custom_fields")!= null ? metadataRes.getChild("brc_custom_fields") : null;

        //MAIN MAP
        ValueMap map = metadataRes.adaptTo(ValueMap.class);

        //SUBMAPS
        ValueMap links_node_map = links_node != null ? links_node.adaptTo(ValueMap.class) : null;
        ValueMap schedule_node_map = schedule_node != null ? schedule_node.adaptTo(ValueMap.class) : null ;
        ValueMap geo_node_map = geo_node !=  null ? geo_node.adaptTo(ValueMap.class) : null;
        ValueMap custom_node_map = custom_node != null ? custom_node.adaptTo(ValueMap.class) : null;

        //RELATED LINK
        String aUrl;
        String aText;
        RelatedLink alink = null;
        if(links_node_map!=null)
        {
            aUrl = links_node_map.get("url", "");
            aText = links_node_map.get("text", "");
            alink = new RelatedLink(aText, aUrl);
        }

        //SCHEDULE
        String sched_start;
        String sched_ends;
        Schedule schedule = null;
        if(schedule_node_map!=null)
        {
            sched_start = schedule_node_map.get("starts_at","");
            sched_ends = schedule_node_map.get("ends_at","");
            schedule = new Schedule(sched_start, sched_ends);
        }



        Geo geo = null; //NOT SUPPORTED
        //****************************************************************************************
        //GEO NOT SUPPORTED IN THIS RELEASE
        //        String[] countries;
        //        Collection<GeoFilterCodeEnum> aCountries = new ArrayList<GeoFilterCodeEnum>();
        //        boolean aExclude_countries = false;
        //        boolean aRestricted = false;
        //
        //        if(geo_node_map!=null)
        //        {
        //            //GEO
        //            aExclude_countries = geo_node_map.get("exclude_countries",false);
        //            aRestricted = geo_node_map.get("restricted",false);
        //            //ISO3166 countries list should be array
        //            countries = geo_node_map.get("countries");
        //            //GEO - COUNTIRES
        //
        //
        //        }
        //
        //        Geo geo = new Geo(aExclude_countries, aRestricted, aCountries);
        //****************************************************************************************

        //TAGS
        String[] tagsList = metadataRes.getValueMap().get("cq:tags",new String[]{});
        List<String> list = new ArrayList<String>(Arrays.asList(tagsList));
        tagsList = list.toArray(new String[0]);
        //REMOVE BRIGHTCOVE TAG BEFORE PUSH
        Collection<String> tags = tagsToCollection(tagsList);
        list = null;



        //STO FROM LOCAL VIDEOS INITIALIZE THESE SO THAT YOU CAN SEND -- COULD COME FROM PROPERTIES VALUE MAP
        String name = map.get("dc:title", "");
        String id = map.get("brc_id", null);
        String referenceId = map.get("brc_reference_id", "");
        String shortDescription = map.get("brc_description","");
        String longDescription = map.get("brc_long_description","");

        //ININTIALIZING WRAPPER OBJECTS

        //COMPLETE
        Boolean complete = map.get("brc_complete",false);


        // WCMUtils.getKeywords(currentPage, false);

        LOGGER.trace(">>>>>>>>>>///>>>>>>>>>>");
        LOGGER.trace("VIDEO OBJ INITIALIZATION");
        LOGGER.trace("name "+name);
        LOGGER.trace("referenceId "+referenceId);
        LOGGER.trace("shortDescription "+shortDescription);
        LOGGER.trace("longDescription "+longDescription);
        LOGGER.trace("tags "+tags);
        LOGGER.trace("geo "+geo);
        LOGGER.trace("schedule "+schedule);
        LOGGER.trace("complete "+complete);
        LOGGER.trace("link "+alink);
        LOGGER.trace(">>>>>>>>>>///>>>>>>>>>>");

        Video video;
        //TODO: CONSIDER PUTTING AN IF NO ID = NO ACTION UNLESS NEW VIDEO CREATION

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
                    alink
            );

        return video;
    }

//    private Boolean isBrightcoveAsset(Collection<String> tags)
//    {
//        return tags.contains("brightcove");
//    }

    private Collection<String> tagsToCollection(String[] tag_array)
    {
        Collection<String> tags;
        tags = tag_array != null ? Arrays.asList(tag_array):new ArrayList<String>();
        return tags;
    }

}