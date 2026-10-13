package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.impl;

import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.AssetProcessingRequester;
import com.day.cq.dam.api.DamConstants;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.request.builder.SlingHttpServletResponseResult;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.engine.SlingRequestProcessor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloud build only: the on-prem profile excludes this class from compilation (core/pom.xml), so the
 * -prem bundle has no requester and its callers' optional reference stays empty.
 *
 * Sends the same request as the Assets UI "Reprocess Assets" action ({@code POST /bin/asynccommand
 * operation=PROCESS}) as an in-process Sling request on the import's own service resolver, so no
 * credentials are involved.
 * Context: current/docs/asset-processing.md (why this endpoint, what was rejected, how it is verified)
 */
@Component(service = AssetProcessingRequester.class)
public class AssetProcessingRequesterImpl implements AssetProcessingRequester {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetProcessingRequesterImpl.class);

    static final String ASYNC_COMMAND_PATH = "/bin/asynccommand";

    @Reference
    private SlingRequestProcessor requestProcessor;

    public AssetProcessingRequesterImpl() {
    }

    AssetProcessingRequesterImpl(SlingRequestProcessor requestProcessor) {
        this.requestProcessor = requestProcessor;
    }

    @Override
    public boolean requestProcessing(ResourceResolver resolver, String assetPath) {
        if (resolver == null || assetPath == null) {
            return false;
        }
        try {
            Resource content = resolver.getResource(assetPath + "/" + JcrConstants.JCR_CONTENT);
            String state = content == null ? null
                    : content.getValueMap().get(DamConstants.DAM_ASSET_STATE, String.class);
            if (!DamConstants.DAM_ASSET_STATE_UNPROCESSED.equals(state)) {
                // Something already picked it up (or the state is unknown): do not queue a second run.
                LOGGER.debug("Not requesting processing for {}: {}={}", assetPath, DamConstants.DAM_ASSET_STATE, state);
                return false;
            }
            int status = InProcessPost.processCommand(requestProcessor, resolver, assetPath);
            if (status >= 200 && status < 300) {
                LOGGER.info("Requested processing for {} (HTTP {})", assetPath, status);
                return true;
            }
            LOGGER.warn("Processing request for {} was refused (HTTP {}); the AEM watchdog will process it later", assetPath, status);
        } catch (Exception | LinkageError e) {
            LOGGER.warn("Could not request processing for {}; the AEM watchdog will process it later", assetPath, e);
        }
        return false;
    }

    static final class InProcessPost {
        private InProcessPost() {
        }

        static int processCommand(SlingRequestProcessor processor, ResourceResolver resolver, String assetPath) throws Exception {
            SlingHttpServletRequest request = Builders.newRequestBuilder(resolver.resolve(ASYNC_COMMAND_PATH))
                    .withRequestMethod("POST")
                    .withParameter("operation", "PROCESS")
                    .withParameter("asset", assetPath)
                    .withParameter("description", "Brightcove import: " + assetPath)
                    .build();
            SlingHttpServletResponseResult response = Builders.newResponseBuilder().build();
            processor.processRequest(request, response, resolver);
            return response.getStatus();
        }
    }
}
