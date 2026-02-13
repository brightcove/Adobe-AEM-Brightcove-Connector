package com.coresecure.brightcove.wrapper.workflow;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.coresecure.brightcove.wrapper.objects.Video;
import com.coresecure.brightcove.wrapper.sling.CertificateListService;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.JcrUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;

import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

@Service
@Component(immediate = true, metatype = true)
@Properties({
        @Property(name = org.osgi.framework.Constants.SERVICE_DESCRIPTION, value = "Brightcove: Sync Asset to Brightcove"),
        @Property(name = org.osgi.framework.Constants.SERVICE_VENDOR, value = "Brightcove"),
        @Property(name = "process.label", value = "Brightcove: Sync Asset to Brightcove")})
public class BrightcoveSyncAssetWorkflowStep implements WorkflowProcess {

    private static final Logger LOG = LoggerFactory.getLogger(BrightcoveSyncAssetWorkflowStep.class);
    private static final String SERVICE_ACCOUNT_IDENTIFIER = "brightcoveWrite";
    @Reference
    private ResourceResolverFactory resourceResolverFactory;
//    private String brightcoveAssetId;
    private Map<String, String> paths = null;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap processArguments) throws WorkflowException {

        String payloadPath = workItem.getWorkflowData().getPayload().toString();
        LOG.info("********************* payloadpath:" + payloadPath);

        ResourceResolver rr = null;
        String brightcoveAssetId = null;

        try {

            // grab a resource resolver to pass to all the activation methods
            final Map<String, Object> authInfo = Collections.singletonMap(
                    ResourceResolverFactory.SUBSERVICE,
                    (Object) SERVICE_ACCOUNT_IDENTIFIER);

            // Get the Service resource resolver
            rr = resourceResolverFactory.getServiceResourceResolver(authInfo);

            // grab all the configured services
            ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
            Set<String> services = cg.getAvailableServices();

            // set up a variable to store the paths
            paths = new LinkedHashMap<>();

            // for each service, check to see the integration path
            for (String service : services) {

                // get the service from that account ID
                ConfigurationService brcService = cg.getConfigurationService(service);

                // add the account ID to a HashMap with a key of the path
                paths.put(brcService.getAssetIntegrationPath(), brcService.getAccountID());

            }

            String asset = payloadPath;

            // get all the account IDs of the integration paths
            Set<String> keys = paths.keySet();

            // check against the paths for each ConfigurationService
            for (String key : keys) {

                // check if this asset lives underneath a Brightcove managed folder
                if (asset.contains(key)) {

                    // get the account ID
                    String brightcoveAccountId = paths.get(key);
                    LOG.info("Found Brightcove Account ID #{} for {}", brightcoveAccountId, asset);

                    // get a proper Asset object from the path
                    Resource assetResource = rr.getResource(asset);
                    Asset _asset = assetResource.adaptTo(Asset.class);
                    ServiceUtil serviceUtil = new ServiceUtil(brightcoveAccountId);
                    // upload or modify the asset
                    brightcoveAssetId = activateAsset(rr, _asset, serviceUtil);
                    TimeUnit.SECONDS.sleep(15);
                    if (brightcoveAssetId != null && !brightcoveAssetId.isEmpty()) {
                        syncBrightcoveData(brightcoveAssetId, serviceUtil, _asset, rr, brightcoveAccountId);
                    }
                    rr.commit();


                }

            }


        } catch (LoginException e) {

            // there is some issue with the system user used
            LOG.error("There was an error using the Brightcove system user.");

        } catch (Exception e) {

            // a general error
            LOG.error("Error when handling the Brightcove video sync: {}", e.getMessage());

        } finally {
            if (rr != null && rr.isLive()) {
                rr.close();
            }
        }
    }

    private void syncBrightcoveData(String brightcoveAssetId, ServiceUtil serviceUtil, Asset _asset, ResourceResolver rr, String brightcoveAccountId) {

        try {
            JSONObject result = serviceUtil.getSelectedVideo(brightcoveAssetId);


            serviceUtil.updateAsset(_asset, result, rr, brightcoveAccountId);
        } catch (PersistenceException | JSONException | RepositoryException e) {
            LOG.error("Error when updating Brightcove metadata and renditions: {}", e.getMessage());
        }
    }

    private String activateNew(Asset _asset, ServiceUtil serviceUtil, Video video, ModifiableValueMap brc_lastsync_map) {

        LOG.trace("brc_lastsync was null or zero : asset should be initialized");
        LOG.info("activate new asset");
        String brightcoveAssetId = null;
        
        try {

            // get the binary
            InputStream is = _asset.getOriginal().getStream();

            // // make the actual video upload call
            JSONObject api_resp = serviceUtil.createVideoS3(video, _asset.getName(), is);

            // LOGGER.trace("API-RESP >>" + api_resp.toString(1));
            boolean sent = api_resp.getBoolean(Constants.SENT);
            if (sent) {
                brightcoveAssetId = api_resp.getString(Constants.VIDEOID);
                brc_lastsync_map.put(Constants.BRC_ID, brightcoveAssetId);

                LOG.trace("UPDATING RENDITIONS FOR THIS ASSET");
                serviceUtil.updateRenditions(_asset, video);

                Node assetNode = _asset.adaptTo(Node.class);
                syncFolder(serviceUtil, api_resp, assetNode);

                LOG.info("BC: ACTIVATION SUCCESSFUL >> {}", _asset.getPath());

                // update the metadata to show the last sync time
                brc_lastsync_map.put(DamConstants.DC_TITLE, video.name);
                brc_lastsync_map.put(Constants.BRC_LASTSYNC, JcrUtil.now2calendar());

            } else {

                // log the error
                LOG.error(Constants.REP_ACTIVATION_SUCCESS_TMPL, _asset.getName());

            }

        } catch (Exception e) {

            LOG.error("Error: {}", e.getMessage());

        }
        
        return brightcoveAssetId;

    }

    private String activateModified(Asset _asset, ServiceUtil serviceUtil, Video video,
                                  ModifiableValueMap brc_lastsync_map) {

        LOG.info("Entering activateModified()");
        String brightcoveAssetId = null;

        try {

            // do update video
            LOG.info("About to make Brightcove API call with video: {}", _asset.getPath());
            JSONObject api_resp = serviceUtil.updateVideo(video);
            LOG.info("Brightcove Asset Modification Response: {}", api_resp.toString());

            boolean sent = api_resp.getBoolean(Constants.SENT);
            if (sent) {
            	brightcoveAssetId = api_resp.getString(Constants.VIDEOID);
                LOG.info("Brightcove video updated successfully: {}", _asset.getPath());
                serviceUtil.updateRenditions(_asset, video);
                LOG.info("Updated renditions for Brightcove video: {}", _asset.getPath());

                Node assetNode = _asset.adaptTo(Node.class);
                LOG.trace("CHECKING PARENT FOR BRC_FOLDER_ID: " + assetNode.getParent().getPath());
                syncFolder(serviceUtil, api_resp, assetNode);

                long current_time_millisec = new Date().getTime();
                brc_lastsync_map.put(Constants.BRC_LASTSYNC, current_time_millisec);

            } else {

                // log the error
                LOG.error("Error sending data to Brightcove: {}", _asset.getName());

            }
        } catch (Exception e) {

            // log the error
            LOG.error("General Error: {}", _asset.getName());

        }
        
        return brightcoveAssetId;

    }

    private void syncFolder(ServiceUtil serviceUtil, JSONObject api_resp, Node assetNode) {
		try {
			LOG.trace("CHECKING PARENT FOR BRC_FOLDER_ID: " + assetNode.getParent().getPath());
			Node parentNode = assetNode.getParent();
			String videoId = api_resp.getString(Constants.VIDEOID);
			
			if (!parentNode.hasProperty("brc_folder_id")) {
				String folderId = serviceUtil.createFolder(assetNode.getParent().getName());
				if (folderId != null && !folderId.isEmpty()) {
					setFolderIdMoveAssetInBC(serviceUtil, parentNode, videoId, folderId);
				} else {
					LOG.error("*************************** No folder created ***************************");
					TimeUnit.SECONDS.sleep(15);
					parentNode.refresh(false);
					if (!parentNode.hasProperty("brc_folder_id")) {
						folderId = serviceUtil.createFolder(assetNode.getParent().getName());
						if (folderId != null && !folderId.isEmpty()) {
							setFolderIdMoveAssetInBC(serviceUtil, parentNode, videoId, folderId);
						} else {
							LOG.error("*************************** No folder created attempt 2 ***************************");
						}
					} else {
						// this is in a subfolder so we need to formally move the asset to this folder
					    String brc_folder_id = parentNode.getProperty("brc_folder_id").getString();
					    LOG.trace("SUBFOLDER FOUND - SETTING THE FOLDER ID to '" + brc_folder_id + "'");
					    serviceUtil.moveVideoToFolder(brc_folder_id, videoId);
					}
				}
			} else {
				// this is in a subfolder so we need to formally move the asset to this folder
			    String brc_folder_id = parentNode.getProperty("brc_folder_id").getString();
			    LOG.trace("SUBFOLDER FOUND - SETTING THE FOLDER ID to '" + brc_folder_id + "'");
			    serviceUtil.moveVideoToFolder(brc_folder_id, videoId);
			}
			
			
		} catch (Exception e) {

            // log the error
            LOG.error("Error syncing folder");

        }
	}

	private void setFolderIdMoveAssetInBC(ServiceUtil serviceUtil, Node parentNode, String videoId, String folderId) throws Exception {
		parentNode.setProperty("brc_folder_id", folderId);
		parentNode.getSession().save();
		
		LOG.trace("SUBFOLDER FOUND - SETTING THE FOLDER ID to '" + folderId + "'");
		serviceUtil.moveVideoToFolder(folderId, videoId);
	}

    private String activateAsset(ResourceResolver rr, Asset _asset, ServiceUtil serviceUtil) {

        // need to either activate a new asset or an updated existing
        // ServiceUtil serviceUtil = new ServiceUtil(accountId);
        String path = _asset.getPath();
        String brightcoveAssetId = null;
        
        Video video = serviceUtil.createVideo(path, _asset, "ACTIVE");
        Resource assetRes = _asset.adaptTo(Resource.class);

        if (assetRes == null) {
            return "";
        }

        Resource metadataRes = assetRes.getChild(Constants.ASSET_METADATA_PATH);
        if (metadataRes == null) {
            return "";
        }

        ModifiableValueMap brc_lastsync_map = metadataRes.adaptTo(ModifiableValueMap.class);
        if (brc_lastsync_map == null) {
            return "";
        }

        Long jcr_lastmod = _asset.getLastModified();
        Long brc_lastsync_time = brc_lastsync_map.get(Constants.BRC_LASTSYNC, Long.class);

        brc_lastsync_map.put(Constants.BRC_STATE, "ACTIVE");

        if (brc_lastsync_time == null) {

            // we need to activate a new asset here
            LOG.info("Activating New Brightcove Asset: {}", _asset.getPath());
            brightcoveAssetId = activateNew(_asset, serviceUtil, video, brc_lastsync_map);

        } else {
            // we need to modify an existing asset here
            LOG.info("Activating Modified Brightcove Asset: {}", _asset.getPath());
            brightcoveAssetId = activateModified(_asset, serviceUtil, video, brc_lastsync_map);
        }
        
        return brightcoveAssetId;

    }
}