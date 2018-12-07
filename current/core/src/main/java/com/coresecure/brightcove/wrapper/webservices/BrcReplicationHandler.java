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

import com.coresecure.brightcove.wrapper.objects.Video;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.JcrUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;
import com.day.cq.replication.*;
import org.apache.felix.scr.annotations.*;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.*;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.jcr.RepositoryException;
import java.io.InputStream;
import java.util.*;

@Service(TransportHandler.class)
@Component(label = "Brightcove: Replication Agents", immediate = true, metatype = true)
public class BrcReplicationHandler implements TransportHandler {
    private static final String SERVICE_ACCOUNT_IDENTIFIER = "brightcoveWrite";

    private static final String ISO_8601_24H_FULL_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";


    private static final Logger LOGGER = LoggerFactory.getLogger(BrcReplicationHandler.class);
    /** Protocol for replication agent transport URI that triggers this transport handler. */
    private Map<String, Object> properties;

    private static final String DEFAULT_BRIGHTCOVE_PROTOCOL = "brightcove://";
    @Property(label = "Brightcove Replication Protocol", value = DEFAULT_BRIGHTCOVE_PROTOCOL)
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
    protected void update(final Map<String, Object> aProperties) {
        properties = aProperties;
    }

    private Map<String, Object> getProperties() {
        if (properties == null) {
            return new Hashtable<String, Object>();
        }
        return properties;
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
        return ((agentConfig.isEnabled() && (transportURI != null)) && transportURI.toLowerCase().startsWith(getBrightcoveProtocol()));
    }
    private boolean isAuthorized(ResourceResolver rr, ReplicationAction replicationAction, List<String> allowedGroups) throws RepositoryException {
        boolean is_authorized = false;
        UserManager userManager = rr.adaptTo(UserManager.class);
        if (userManager == null) {
            is_authorized = false;
            return is_authorized;
        }
        Authorizable auth = userManager.getAuthorizable(replicationAction.getUserId());

        if (auth == null) {
            is_authorized = false;
            return is_authorized;
        }
        Iterator<Group> groups = auth.memberOf();
        while (groups.hasNext() && !is_authorized) {
            Group group = groups.next();
            if (allowedGroups.contains(group.getID()))
                is_authorized = true; //<-Authorization
        }

       return is_authorized;
    }

    private ReplicationResult execAction(ReplicationActionType replicationType, Asset _asset, String account_id) throws ReplicationException{
        //ACTION SWITCH
        ReplicationResult result = ReplicationResult.OK;
        if (replicationType == ReplicationActionType.TEST) {
            result = testVideo();
        } else if (replicationType == ReplicationActionType.ACTIVATE) {
            //TESTING
            result = activateVideo(_asset, account_id);
        } else if (replicationType == ReplicationActionType.DEACTIVATE) {
            result = deactivateVideo(_asset, account_id);
        } else {
            throw new ReplicationException("Replication action type " + replicationType + " not supported.");
        }
        return result;
    }

    private ReplicationResult replicateAssets(ResourceResolver rr, String current_path, ReplicationAction replicationAction, ReplicationActionType replicationType) throws RepositoryException, ReplicationException{
        Resource asset_res = rr.getResource(current_path);
        ReplicationResult result = ReplicationResult.OK;
        if (asset_res == null) {
            return result;
        }
        Resource parent = asset_res.getParent();
        if (parent == null) {
            return result;
        }
        String account_id = parent.getName();
        Asset _asset = asset_res.adaptTo(Asset.class);
        if (_asset == null){
            LOGGER.warn("Asset removed or not existing");
            return result;
        }
        LOGGER.trace(account_id);

        cs = configurationGrabber.getConfigurationService(account_id);
        if (cs == null) {
            LOGGER.warn("Account not existing");
            result = ReplicationResult.OK;
            return result;
        }
        List<String> allowedGroups = cs.getAllowedGroupsList();

        //AUTHORIZATION CHECK
        boolean is_authorized = isAuthorized(rr, replicationAction, allowedGroups);
        //tag amanger
        //get tags
        //do check of funciton allreayd implemented

        if (!is_authorized) {
            LOGGER.debug("Not authorized");
            return result;
        }
        Resource metadataRes = asset_res.getChild(Constants.ASSET_METADATA_PATH);

        if (asset_res.getPath().startsWith(cs.getAssetIntegrationPath())) {

            result = execAction(replicationType, _asset, account_id);

        }
        return result;
    }
    @Override
    public ReplicationResult deliver(TransportContext ctx, ReplicationTransaction tx) throws ReplicationException
    {
        replicationLog = tx.getLog();
        ReplicationResult result = ReplicationResult.OK;

        //Global Assignments
        ReplicationAction replicationAction = tx.getAction();
        if (replicationAction == null) {
            LOGGER.trace("REPLICATION ACTION IS NULL");
            return ReplicationResult.OK;
        }

        final ReplicationActionType replicationType = replicationAction.getType();
        if (replicationType == ReplicationActionType.DELETE)
        {
            LOGGER.trace("DELETE REPLICATION NOT SUPPORTED");
            return ReplicationResult.OK;
        }
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

                final Map<String, Object> authInfo = Collections.singletonMap(
                        ResourceResolverFactory.SUBSERVICE,
                        (Object) SERVICE_ACCOUNT_IDENTIFIER);

                // Get the Service resource resolver
                ResourceResolver rr = resourceResolverFactory.getServiceResourceResolver(authInfo);
                Resource asset_res;
                for (String current_path : assetPaths) {
                    result = replicateAssets(rr, current_path, replicationAction, replicationType);
                }
                rr.commit();
            } catch (LoginException e) {
                LOGGER.error(e.getClass().getName(), e);
                replicationLog.error(Constants.REP_ACTION_TYPE_TMPL+"LoginException." ,replicationType);
                throw new ReplicationException(Constants.REP_ACTION_TYPE_TMPL + replicationType + " LoginException.");
            } catch (NullPointerException e) {
                LOGGER.error(e.getClass().getName()+" RepHandler", e);
                replicationLog.error(Constants.REP_ACTION_TYPE_TMPL+"NullPointer RepHandler." , replicationType);
                throw new ReplicationException(Constants.REP_ACTION_TYPE_TMPL + replicationType + " NullPointer RepHandler.");

            } catch (ReplicationException e) {
                LOGGER.error("ReplicationException - RepHandler", e);
                replicationLog.error(Constants.REP_ACTION_TYPE_TMPL+"RepHandler", replicationType);
                throw new ReplicationException(Constants.REP_ACTION_TYPE_TMPL + replicationType + " ReplicationException - RepHandler.");
            } catch (Exception e) {
                LOGGER.error("Exception RepHandler", e);
                replicationLog.error("Replication action type {} Exception RepHandler.", replicationType);
                throw new ReplicationException("Replication action type" + replicationType + " Exception RepHandler.");
            }
            //END FORLOOP
        }



        replicationLog.info("REPLICATION STATUS >> Code: %s Message: %s Success: %s", result.getCode(), result.getMessage(), result.isSuccess());

        return result;
    }

    private ReplicationResult doTest(TransportContext ctx, ReplicationTransaction tx, String accountId) throws ReplicationException
    {
        replicationLog.debug("Replication Test");
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
            replicationLog.error(Constants.REP_ERROR_LOG_TMPL , e );
            result =  new ReplicationResult(false, 0, Constants.REP_FAILED_ERROR+e.getMessage());
        }
        return result;
    }

    private ReplicationResult doDeactivate(TransportContext ctx, ReplicationTransaction tx, String accountId, Map<String, Object> propertiesMap) throws ReplicationException {
        ReplicationResult result = ReplicationResult.OK;
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
            result =  new ReplicationResult(false, 0, Constants.REP_FAILED_ERROR+e.getMessage());
        }
        return result;
    }
    private ReplicationResult doDelete(TransportContext ctx, ReplicationTransaction tx, String accountId, Map<String, Object> propertiesMap)
            throws ReplicationException {
        return doDeactivate(ctx, tx, accountId, propertiesMap);
    }
    private ReplicationResult activateNew(Asset _asset, ServiceUtil serviceUtil, Video video, ModifiableValueMap brc_lastsync_map) {
        ReplicationResult result = ReplicationResult.OK;
        LOGGER.trace("brc_lastsync was null or zero : asset should be initialized");
        try {
            InputStream is = _asset.getOriginal().getStream();      //VIDEO ORIGINAL BINARY FOR BC DATABASE

            JSONObject api_resp = serviceUtil.createVideoS3(video, _asset.getName(), is); //ACTUAL VIDEO UPLOAD CALL - WITH METADATA

            //LOGGER.trace("API-RESP >>" + api_resp.toString(1));
            boolean sent = api_resp.getBoolean(Constants.SENT);
            if (sent) {
                brc_lastsync_map.put(Constants.BRC_ID, api_resp.getString(Constants.VIDEOID));

                LOGGER.trace("UPDATING RENDITIONS FOR THIS ASSET");
                serviceUtil.updateRenditions(_asset, video);


                replicationLog.info("BC: ACTIVATION SUCCESSFUL >> {}" , _asset.getPath());
                result = ReplicationResult.OK;
                brc_lastsync_map.put(DamConstants.DC_TITLE, video.name);
                brc_lastsync_map.put(Constants.BRC_LASTSYNC, JcrUtil.now2calendar());
            } else {
                replicationLog.error(Constants.REP_ACTIVATION_SUCCESS_TMPL, _asset.getName());
                result = new ReplicationResult(false, 0, Constants.REP_FAILED_ERROR+" S3UploadUtil.uploadFile");
            }
        } catch (Exception e) {
            result = new ReplicationResult(false, 0, Constants.REP_FAILED_ERROR + e.getMessage());
        }
        return result;
    }
    private ReplicationResult activateModified(Asset _asset, ServiceUtil serviceUtil, Video video, ModifiableValueMap brc_lastsync_map) {
        ReplicationResult result = ReplicationResult.OK;
        try {
            LOGGER.trace("CREATE VIDEO - THUMBNAIL / POSTER TEST>>");
            LOGGER.trace(video.toJSON().toString(1));

            //do update video
            JSONObject api_resp = serviceUtil.updateVideo(video); //ONLY UPDATE METADATA - DO NOT SEND BINARY
            boolean sent = api_resp.getBoolean(Constants.SENT);
            if (sent) {
                //REPLICATION - AFTER METADATA HAS BEEN UPDATED - TRY TO UPDATE THE RENDITIONS
                LOGGER.trace("UPDATING RENDITIONS FOR THIS ASSET");
                serviceUtil.updateRenditions(_asset, video);

                replicationLog.info(Constants.REP_ACTIVATION_SUCCESS_TMPL, _asset.getPath());
                long current_time_millisec = new Date().getTime();
                brc_lastsync_map.put(Constants.BRC_LASTSYNC, current_time_millisec);
                result = ReplicationResult.OK;
            } else {
                replicationLog.error(Constants.REP_ACTIVATION_SUCCESS_TMPL , _asset.getName());
                result = new ReplicationResult(false, 0, Constants.REP_FAILED_ERROR + "S3UploadUtil.uploadFile");
            }
        } catch (Exception e) {
            result = new ReplicationResult(false, 0, "Replication failed: " + e.getMessage());
        }
        return result;
    }
    private ReplicationResult activateVideo(@Nonnull Asset _asset, String account_id)
    {

        replicationLog.info("ACTIVATING >> "+_asset.getName());
        ServiceUtil serviceUtil = new ServiceUtil(account_id);
        ReplicationResult result = ReplicationResult.OK;
        String path = _asset.getPath();

        Video video = serviceUtil.createVideo(path, _asset, "ACTIVE");
        Resource assetRes = _asset.adaptTo(Resource.class);
        if(assetRes==null) {
            return result;
        }
        Resource metadataRes = assetRes.getChild(Constants.ASSET_METADATA_PATH);
        if(metadataRes==null) {
            return result;
        }

        ModifiableValueMap brc_lastsync_map = metadataRes.adaptTo(ModifiableValueMap.class); //RETURNED NULL EACH TIME

        if(brc_lastsync_map==null) {
            return result;
        }

        Long jcr_lastmod = _asset.getLastModified();
        Long brc_lastsync_time = brc_lastsync_map.get(Constants.BRC_LASTSYNC, Long.class);
        brc_lastsync_map.put(Constants.BRC_STATE, "ACTIVE");

        if (brc_lastsync_time == null) {
            result = activateNew(_asset, serviceUtil, video, brc_lastsync_map);
        } else if (jcr_lastmod > brc_lastsync_time) {
            result = activateModified(_asset, serviceUtil, video, brc_lastsync_map);
        }

        return result;
    }

    private ReplicationResult deactivateVideo(@Nonnull Asset _asset, String account_id)
    {
        ServiceUtil serviceUtil = new ServiceUtil(account_id);
        ReplicationResult result = ReplicationResult.OK;
        Resource assetRes = _asset.adaptTo(Resource.class);
        if(assetRes!=null)
        {
            Resource metadataRes = assetRes.getChild(Constants.ASSET_METADATA_PATH);
            if (metadataRes!=null)
            {
                ModifiableValueMap brc_lastsync_map = metadataRes.adaptTo(ModifiableValueMap.class); //RETURNED NULL EACH TIME
                if(brc_lastsync_map!=null) {
                    brc_lastsync_map.put(Constants.BRC_STATE, "INACTIVE");

                    replicationLog.info("DEACTIVATING >> {}" , _asset.getName());
                    String path = _asset.getPath();

                    Video video = serviceUtil.createVideo(path, _asset, "INACTIVE");

                    LOGGER.trace("VIDEO GOING TO API CALL>>>>>>> {}" , video.toString());

                    //UPDATE VIDEO CREATES A NEW VIDEO CORRECTLY INACTIVE AS THE ONE ABOVE
                    //JSONObject update_resp = serviceUtil.updateVideo(video); //

                    //MUST BE DEACTIVATION CALL
                    try {
                        LOGGER.trace("DEACTIVATION");
                        LOGGER.trace(video.toString());
                        JSONObject update_resp = serviceUtil.updateVideo(video);
                        boolean sent = update_resp.getBoolean(Constants.SENT);
                        if (sent) {
                            replicationLog.info("BC: ACTIVATION SUCCESSFUL >> {}" , _asset.getPath());
                            result = ReplicationResult.OK;
                        } else {
                            replicationLog.error(Constants.REP_ACTIVATION_SUCCESS_TMPL , _asset.getName());
                            result = new ReplicationResult(false, 0, Constants.REP_FAILED_ERROR+": S3UploadUtil.uploadFile");
                        }
                    } catch (Exception e) {
                        result = new ReplicationResult(false, 0, Constants.REP_FAILED_ERROR+ e.getMessage());
                        LOGGER.error("Error!: {} {}"  ,result, e.getMessage());
                    }
                }
            }
        }
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



}