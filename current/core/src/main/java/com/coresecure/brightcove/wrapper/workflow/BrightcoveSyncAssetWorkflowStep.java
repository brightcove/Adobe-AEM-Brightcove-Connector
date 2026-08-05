package com.coresecure.brightcove.wrapper.workflow;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.coresecure.brightcove.wrapper.listeners.BrightcovePublishListener;
import com.coresecure.brightcove.wrapper.objects.Video;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.JcrUtil;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.version.VersionException;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component(property = {
    org.osgi.framework.Constants.SERVICE_DESCRIPTION + "=Sync Asset to Brightcove",
    org.osgi.framework.Constants.SERVICE_VENDOR + "=Adobe Systems",
    "process.label" + "=Brightcove Sync Asset to Brightcove"
})
public class BrightcoveSyncAssetWorkflowStep implements WorkflowProcess{

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    private static final Logger LOG = LoggerFactory.getLogger(BrightcoveSyncAssetWorkflowStep.class);
    private static final String SERVICE_ACCOUNT_IDENTIFIER = "brightcoveWrite";

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
            LOG.error("*************************** There was an error using the Brightcove system user.");

        } catch (Exception e) {

            // a general error
            LOG.error("*************************** Error when handling the Brightcove video sync: {}", e.getMessage());

        } finally {
            if (rr != null && rr.isLive()) {
                rr.close();
            }
        }
    }
    
    private void syncBrightcoveData(String brightcoveAssetId, ServiceUtil serviceUtil, Asset _asset, ResourceResolver rr, String brightcoveAccountId) {
        
        try {
            ObjectNode result = serviceUtil.getSelectedVideo(brightcoveAssetId);


            serviceUtil.updateAsset(_asset, result, rr, brightcoveAccountId);
        } catch (PersistenceException | RepositoryException e) {
            LOG.error("Error when updating Brightcove metadata and renditions: {}", e.getMessage());
        } catch (Exception e) {
            LOG.error("Error when updating Brightcove metadata and renditions: {}", e.getMessage());
        }
    }

    /**
     * BGS-1705: flush pending Brightcove sync-marker changes for this asset so that a
     * concurrent or redelivered publish event, running in its own JCR session, can see
     * them. Never throws — a failed commit is logged and the caller carries on.
     */
    private void commitSyncMarker(Asset _asset) {
        try {
            Resource assetRes = _asset.adaptTo(Resource.class);
            if (assetRes == null) {
                return;
            }
            ResourceResolver resolver = assetRes.getResourceResolver();
            if (resolver != null && resolver.hasChanges()) {
                resolver.commit();
            }
        } catch (PersistenceException e) {
            LOG.error("Failed to persist Brightcove sync marker for {}: {}", _asset.getPath(), e.getMessage());
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
            ObjectNode api_resp = serviceUtil.createVideoS3(video, _asset.getName(), is);

            // LOGGER.trace("API-RESP >>" + api_resp.toPrettyString());
            boolean sent = api_resp.has(Constants.SENT) && api_resp.get(Constants.SENT).asBoolean();
            if (sent) {
                brightcoveAssetId = api_resp.get(Constants.VIDEOID).asText();

                // BGS-1705: write the COMPLETE sync marker and commit it right here, before
                // any further work. Two reasons:
                //  1. Everything below is slow — updateRenditions uploads renditions, and
                //     syncFolder's retry path can sleep 15s — so deferring the commit leaves
                //     a wide window in which a concurrent or redelivered AEMaaCS publish
                //     still reads brc_lastsync == null and creates a second video.
                //  2. Both calls run on this same JCR session and can discard pending state,
                //     which could otherwise persist brc_lastsync with no brc_id and route the
                //     next publish to updateVideo with a null id. Committing up front makes
                //     that half-written marker impossible by construction.
                brc_lastsync_map.put(Constants.BRC_ID, brightcoveAssetId);
                brc_lastsync_map.put(DamConstants.DC_TITLE, video.name);
                brc_lastsync_map.put(Constants.BRC_LASTSYNC, JcrUtil.now2calendar());
                commitSyncMarker(_asset);

                LOG.trace("UPDATING RENDITIONS FOR THIS ASSET");
                serviceUtil.updateRenditions(_asset, video);

                Node assetNode = _asset.adaptTo(Node.class);
                syncFolder(serviceUtil, api_resp, assetNode);

                LOG.info("BC: ACTIVATION SUCCESSFUL >> {}", _asset.getPath());

            } else {

                // log the error
                LOG.error(Constants.REP_ACTIVATION_SUCCESS_TMPL, _asset.getName());
                LOG.error("*************************** Error sending data to Brightcove: {}", _asset.getName());
                
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
            ObjectNode api_resp = serviceUtil.updateVideo(video);
            LOG.info("Brightcove Asset Modification Response: {}", api_resp.toString());

            boolean sent = api_resp.has(Constants.SENT) && api_resp.get(Constants.SENT).asBoolean();
            if (sent) {
            	brightcoveAssetId = api_resp.get(Constants.VIDEOID).asText();
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
                LOG.error("*************************** Error sending data to Brightcove: {}", _asset.getName());
                
            }
        } catch (Exception e) {

            // log the error
            LOG.error("General Error: {}", _asset.getName());
            LOG.error("*************************** Error sending data to Brightcove: {}", _asset.getName());

        }
        
        return brightcoveAssetId;

    }

	private void syncFolder(ServiceUtil serviceUtil, ObjectNode api_resp, Node assetNode) {
		try {
			LOG.trace("CHECKING PARENT FOR BRC_FOLDER_ID: " + assetNode.getParent().getPath());
			Node parentNode = assetNode.getParent();
			String videoId = api_resp.get(Constants.VIDEOID).asText();

			// Skip folder sync if the asset lives directly in the account root folder.
			// The account root (e.g. /content/dam/brightcove_assets/{accountId}) has no
			// brc_folder_id, so without this guard syncFolder would create a spurious
			// Brightcove folder named after the account ID and move the video into it.
			ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
			for (String accountId : cg.getAvailableServices()) {
				ConfigurationService cs = cg.getConfigurationService(accountId);
				if (cs == null) continue;
				String integrationPath = cs.getAssetIntegrationPath();
				String normalized = integrationPath.endsWith("/")
						? integrationPath.substring(0, integrationPath.length() - 1)
						: integrationPath;
				String accountRootPath = normalized + "/" + accountId;
				if (parentNode.getPath().equals(accountRootPath)) {
					LOG.info("Asset is at account root level ({}), skipping Brightcove folder sync", accountRootPath);
					return;
				}
			}

			if (!parentNode.hasProperty("brc_folder_id")) {
				String folderId = serviceUtil.createFolder(assetNode.getParent().getName());
				if (folderId != null && !folderId.isEmpty()) {
					setFolderIdMoveAssetInBC(serviceUtil, parentNode, videoId, folderId);
				} else {
					LOG.error("*************************** No folder created ***************************");
					TimeUnit.SECONDS.sleep(15);
					// Re-read the parent from the persistent store in case a concurrent publish
					// created the folder. keepChanges=true: in Oak refresh() is session-wide, so
					// refresh(false) would discard pending metadata (e.g. BRC_ID) set before this
					// call, leaving the BGS-1705 marker commit to persist brc_lastsync with no
					// brc_id — which routes the next publish to updateVideo with a null id.
					parentNode.refresh(true);
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

    // Package-private (not private) so the BGS-1705 concurrency regression test can
    // drive the create-vs-update gate directly with a mocked ServiceUtil.
    String activateAsset(ResourceResolver rr, Asset _asset, ServiceUtil serviceUtil) {

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

        // BGS-1705: persist the sync marker (brc_id / brc_lastsync / brc_state) NOW,
        // before execute() runs its ~15s ingest-wait. Previously the marker was only
        // committed at the end of execute(), so a concurrent or redelivered AEMaaCS
        // publish event would still read brc_lastsync == null and create a second,
        // duplicate Brightcove video. Committing here closes that window. On the create
        // path activateNew has already committed the marker; this catches brc_state and
        // the update path.
        commitSyncMarker(_asset);

        return brightcoveAssetId;

    }
}