package com.coresecure.brightcove.wrapper.workflow;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.day.cq.dam.api.Asset;
import org.apache.sling.api.resource.ValueMap;

@Service
@Component(immediate = true, metatype = true)
@Properties({
	@Property(name = org.osgi.framework.Constants.SERVICE_DESCRIPTION, value = "Brightcove: Delete Asset"),
    @Property(name = org.osgi.framework.Constants.SERVICE_VENDOR, value = "Brightcove"),
    @Property(name = "process.label", value = "Brightcove: Delete Asset") })
public class BrightcoveDeleteAssetWorkflowStep  implements WorkflowProcess{
	
	@Reference
    private ResourceResolverFactory resourceResolverFactory;

    private static final Logger LOG = LoggerFactory.getLogger(BrightcoveDeleteAssetWorkflowStep.class);
    private static final String SERVICE_ACCOUNT_IDENTIFIER = "brightcoveWrite";
    private Map<String, String> paths = null;
    private String brightcoveAssetId;

	@Override
	public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap processArguments) throws WorkflowException {
		// TODO Auto-generated method stub
		String payloadPath = workItem.getWorkflowData().getPayload().toString();
        LOG.info("********************* payloadpath:" + payloadPath);

        ResourceResolver rr = null;
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
                    
                    boolean bcDeleteComplete = deleteBrightcoveAsset(rr, payloadPath, serviceUtil);
                    
                    if (bcDeleteComplete) {
                    	Session session = null;
                        try {
                            session = rr.adaptTo(Session.class);
                            if (null != session) {
                                Node assetNode = session.getNode(asset);
                                if (null != assetNode) {
                                    LOG.debug("Deleting asset node {}", assetNode.getPath());
                                    assetNode.remove();
                                    session.save();
                                }
                            }
                        } catch (RepositoryException e) {
                            LOG.error("RepositoryException occurred while deleting the asset", e);
                        } finally {
                            if (null != session) {
                                session.logout();
                            }
                        }
                    } else {
                    	LOG.error("Error when deleting the Brightcove video: {" + payloadPath + "}");
                    }
                    
                    rr.commit();
                }

            }
        	
        } catch (LoginException e) {
            LOG.error("There was an error using the Brightcove system user.");

        } catch (Exception e) {
            LOG.error("Error when deleting the Brightcove video: {}", e.getMessage());

        } finally {
            if (rr != null && rr.isLive()) {
                rr.close();
            }
        }
	}
	
	private boolean deleteBrightcoveAsset(ResourceResolver rr, String payloadPath, ServiceUtil serviceUtil) {
		
		boolean bcDeleteComplete = false;
        try {
			Resource videoResource = rr.resolve(payloadPath);
	        Resource metadataRes = videoResource.getChild(Constants.ASSET_METADATA_PATH);
	        ValueMap map = metadataRes.adaptTo(ValueMap.class);
	        String videoId = map.get(Constants.BRC_ID, String.class);
			bcDeleteComplete = serviceUtil.deleteVideo(videoId);
        } catch (Exception e) {

            // a general error
            LOG.error("Error when deleting the Brightcove video: {}", e.getMessage());

        }
        
        return bcDeleteComplete;
	}

}
