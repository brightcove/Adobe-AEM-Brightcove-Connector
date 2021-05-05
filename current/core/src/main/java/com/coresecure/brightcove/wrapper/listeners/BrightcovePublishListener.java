package com.coresecure.brightcove.wrapper.listeners;

import com.day.cq.replication.ReplicationAction;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.sling.commons.json.JSONObject;

import javax.jcr.RepositoryException;
import java.io.InputStream;

import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.objects.Video;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.JcrUtil;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.DamConstants;

import org.apache.sling.api.resource.*;
import org.apache.sling.settings.SlingSettingsService;
 
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import java.util.Collections;
import java.util.Date;

@Component(service = EventHandler.class,
    immediate = true,
    property = {
            "service.description" + "=Brightcove Distribution Event Listener ",
            EventConstants.EVENT_TOPIC + "=" + ReplicationAction.EVENT_TOPIC
    }
)
@Designate(ocd = BrightcovePublishListener.EventListenerPageActivationListenerConfiguration.class)
public class BrightcovePublishListener implements EventHandler {

    @ObjectClassDefinition(name = "Brightcove Distribution Listener")
    public @interface EventListenerPageActivationListenerConfiguration {
 
        @AttributeDefinition(
                name = "Enabled",
                description = "Enable Distribution Event Listener",
                type = AttributeType.BOOLEAN
        )
        boolean isEnabled() default false;
    }
 
    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    SlingSettingsService slingSettings;
 
    private static final Logger LOG = LoggerFactory.getLogger(BrightcovePublishListener.class);
    private static final String SERVICE_ACCOUNT_IDENTIFIER = "brightcoveWrite";

    private boolean enabled = false;
    private Map<String, String> paths = null;

    @Activate
    @Modified
    protected void activate(EventListenerPageActivationListenerConfiguration config) {
        enabled = config.isEnabled();
        LOG.info("Brightcove Distribution Event Listener is enabled: " + enabled);
    }

    private void activateNew(Asset _asset, ServiceUtil serviceUtil, Video video, ModifiableValueMap brc_lastsync_map) {
        
        LOG.trace("brc_lastsync was null or zero : asset should be initialized");

        try {

            // get the binary
            InputStream is = _asset.getOriginal().getStream();

            // make the actual video upload call
            JSONObject api_resp = serviceUtil.createVideoS3(video, _asset.getName(), is);

            //LOGGER.trace("API-RESP >>" + api_resp.toString(1));
            boolean sent = api_resp.getBoolean(Constants.SENT);
            if (sent) {

                brc_lastsync_map.put(Constants.BRC_ID, api_resp.getString(Constants.VIDEOID));

                LOG.trace("UPDATING RENDITIONS FOR THIS ASSET");
                serviceUtil.updateRenditions(_asset, video);


                LOG.info("BC: ACTIVATION SUCCESSFUL >> {}" , _asset.getPath());
                
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
        
    }

    private void activateModified(Asset _asset, ServiceUtil serviceUtil, Video video, ModifiableValueMap brc_lastsync_map) {

        LOG.info("Entering activateModified()");

        try {

            // do update video
            LOG.info("About to make Brightcove API call with video: {}", _asset.getPath());
            JSONObject api_resp = serviceUtil.updateVideo(video);
            LOG.info("Brightcove Asset Modification Response: {}", api_resp.toString());

            boolean sent = api_resp.getBoolean(Constants.SENT);
            if (sent) {

                LOG.info("Brightcove video updated successfully: {}", _asset.getPath());
                serviceUtil.updateRenditions(_asset, video);
                LOG.info("Updated renditions for Brightcove video: {}", _asset.getPath());

                long current_time_millisec = new Date().getTime();
                brc_lastsync_map.put(Constants.BRC_LASTSYNC, current_time_millisec);

            } else {

                // log the error
                LOG.error("Error sending data to Brightcove: {}" , _asset.getName());
                
            }
        } catch (Exception e) {

            // log the error
            LOG.error("General Error: {}", _asset.getName());

        }

    }

    private void activateAsset(ResourceResolver rr, Asset _asset, String accountId) {

        // need to either activate a new asset or an updated existing
        ServiceUtil serviceUtil = new ServiceUtil(accountId);
        String path = _asset.getPath();

        Video video = serviceUtil.createVideo(path, _asset, "ACTIVE");
        Resource assetRes = _asset.adaptTo(Resource.class);

        if ( assetRes == null ) {
            return;
        }

        Resource metadataRes = assetRes.getChild(Constants.ASSET_METADATA_PATH);
        if ( metadataRes == null ) {
            return;
        }

        ModifiableValueMap brc_lastsync_map = metadataRes.adaptTo(ModifiableValueMap.class);
        if ( brc_lastsync_map == null ) {
            return;
        }

        Long jcr_lastmod = _asset.getLastModified();
        Long brc_lastsync_time = brc_lastsync_map.get(Constants.BRC_LASTSYNC, Long.class);

        brc_lastsync_map.put(Constants.BRC_STATE, "ACTIVE");

        if (brc_lastsync_time == null) {

            // we need to activate a new asset here
            LOG.info("Activating New Brightcove Asset: {}", _asset.getPath());
            activateNew(_asset, serviceUtil, video, brc_lastsync_map);

        } else if (jcr_lastmod > brc_lastsync_time) {

            // we need to modify an existing asset here
            LOG.info("Activating Modified Brightcove Asset: {}", _asset.getPath());
            activateModified(_asset, serviceUtil, video, brc_lastsync_map);

        }

    }

    private void deactivateAsset(ResourceResolver rr, Asset _asset, String accountId) {
        
        // need to deactivate (delete) an existing asset
        Resource assetRes = _asset.adaptTo(Resource.class);
        ServiceUtil serviceUtil = new ServiceUtil(accountId);

        if ( assetRes != null ) {

            Resource metadataRes = assetRes.getChild(Constants.ASSET_METADATA_PATH);
            if ( metadataRes != null ) {

                ModifiableValueMap brc_lastsync_map = metadataRes.adaptTo(ModifiableValueMap.class);

                if ( brc_lastsync_map != null ) {

                    brc_lastsync_map.put(Constants.BRC_STATE, "INACTIVE");

                    LOG.info("Deactivating New Brightcove Asset: {}", _asset.getPath());
                    String path = _asset.getPath();

                    Video video = serviceUtil.createVideo(path, _asset, "INACTIVE");

                    LOG.trace("Sending Brightcove Video to API Call: {}" , video.toString());

                    try {

                        JSONObject update_resp = serviceUtil.updateVideo(video);
                        boolean sent = update_resp.getBoolean(Constants.SENT);

                        if (sent) {

                            LOG.info("Brightcove update successful for: {}" , _asset.getPath());

                        } else {

                            LOG.error("Brightcove update unsuccessful for: {}" , _asset.getName());

                        }

                    } catch (Exception e) {

                        LOG.error("Error!: {}"  , e.getMessage());

                    }
                }
            }
        }

    }
 
    @Override
    public void handleEvent(Event event) {

        // check that the service is enabled and that we are running on Author
        if ( enabled && slingSettings.getRunModes().contains("author") )  {

            try {

                // grab a resource resolver to pass to all the activation methods
                final Map<String, Object> authInfo = Collections.singletonMap(
                    ResourceResolverFactory.SUBSERVICE,
                    (Object) SERVICE_ACCOUNT_IDENTIFIER);

                // Get the Service resource resolver
                ResourceResolver rr = resourceResolverFactory.getServiceResourceResolver(authInfo);

                // grab all the configured services
                ConfigurationGrabber cg = ServiceUtil.getConfigurationGrabber();
                Set<String> services = cg.getAvailableServices();

                // set up a variable to store the paths
                paths = new LinkedHashMap<>();
                
                // for each service, check to see the integration path
                for ( String service : services ) {

                    // get the service from that account ID
                    ConfigurationService brcService = cg.getConfigurationService(service);

                    // add the account ID to a HashMap with a key of the path
                    paths.put(brcService.getAssetIntegrationPath(), brcService.getAccountID());

                }

                String[] activatedAssets = (String[]) event.getProperty("paths");

                // iterate through all assets that were part of this replication event
                for (String asset : activatedAssets) {

                    // get all the account IDs of the integration paths
                    Set<String> keys = paths.keySet();

                    // check against the paths for each ConfigurationService
                    for (String key : keys) {

                        // check if this asset lives underneath a Brightcove managed folder
                        if ( asset.contains(key) ) {

                            // get the account ID
                            String brightcoveAccountId = paths.get(key);
                            LOG.info("Found Brightcove Account ID #{} for {}", brightcoveAccountId, asset);

                            // get a proper Asset object from the path
                            Resource assetResource = rr.getResource(asset);
                            Asset _asset = assetResource.adaptTo(Asset.class);

                            // check the activation type
                            if ( "ACTIVATE".equals(event.getProperty("type")) ) {

                                // upload or modify the asset
                                activateAsset(rr, _asset, brightcoveAccountId);

                            } else if ( "DEACTIVATE".equals(event.getProperty("type")) ) {

                                // delete the asset
                                deactivateAsset(rr, _asset, brightcoveAccountId);

                            }
                            
                        }

                    }

                }

            } catch (LoginException e) {

                // there is some issue with the system user used
                LOG.error("There was an error using the Brightcove system user.");

            } catch (Exception e) {

                // a general error
                LOG.error("Error when handling the Brightcove video publish event: {}", e.getMessage());

            }

        }

    }
    
}