package com.coresecure.brightcove.wrapper.schedulers.asset_integrator;

import org.apache.sling.api.resource.ResourceResolver;

/**
 * Asks AEM to process a DAM asset the Brightcove import has just created and committed.
 * On AEM as a Cloud Service nothing else does: an asset written with {@code AssetManager.createAsset}
 * stays {@code unProcessed} until the stuck-asset watchdog (~60 min). Cloud build only: the -prem
 * bundle ships no implementation, because the DAM Update Asset launcher already processes it on 6.5.
 * Context: current/docs/asset-processing.md
 */
public interface AssetProcessingRequester {

    /**
     * @return true only when a processing request was sent and accepted; false when it was not
     *         needed (on-prem build, asset already processing) or failed. Never throws: the import
     *         must not fail because processing could not be requested.
     */
    boolean requestProcessing(ResourceResolver resolver, String assetPath);
}
