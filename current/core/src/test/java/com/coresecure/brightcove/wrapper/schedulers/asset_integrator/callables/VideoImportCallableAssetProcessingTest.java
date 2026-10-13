package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.callables;

import com.coresecure.brightcove.wrapper.schedulers.asset_integrator.AssetProcessingRequester;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.day.cq.dam.api.Asset;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.mime.MimeTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BGS-1747: on AEM as a Cloud Service an asset made by {@code AssetManager.createAsset} is never
 * processed until the stuck-asset watchdog (~60 min), and until processing writes {@code dc:format}
 * the Assets side panel's video filter does not list it. The import therefore (1) writes dc:format
 * itself and (2) asks for processing once its last write is done. Runs under both profiles; the
 * requester is absent (null) in the -prem build, which must still import normally.
 * Context: current/docs/asset-processing.md
 */
@ExtendWith(AemContextExtension.class)
class VideoImportCallableAssetProcessingTest {

    private static final String ROOT = "/content/dam/brightcove_assets";
    private static final String VIDEO_ID = "1234567890";
    private static final String ASSET = ROOT + "/acct1/" + VIDEO_ID + ".mp4";

    private final AemContext context = new AemContext();
    private ResourceResolverFactory factory;
    private MimeTypeService mime;
    private ServiceUtil serviceUtil;

    @BeforeEach
    void setUp() throws Exception {
        context.create().resource(ROOT + "/acct1", "jcr:primaryType", "sling:OrderedFolder");
        ResourceResolver resolver = spy(context.resourceResolver());
        doNothing().when(resolver).close(); // the callable closes its resolver; the context owns this one
        factory = mock(ResourceResolverFactory.class);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        mime = mock(MimeTypeService.class);
        when(mime.getMimeType(anyString())).thenReturn("video/mp4");
        serviceUtil = mock(ServiceUtil.class);
    }

    private static ObjectNode video() {
        ObjectNode video = JsonNodeFactory.instance.objectNode();
        video.put(Constants.ID, VIDEO_ID);
        video.put(Constants.NAME, "a video");
        video.put(Constants.STATE, "ACTIVE");
        video.put(Constants.THUMBNAIL_URL, Constants.DEFAULT_THUMBNAIL_LOCATION); // bundled placeholder
        return video;
    }

    @Test
    void aNewlyImportedAssetCarriesTheFormatTheSidePanelFiltersOn() throws Exception {
        new VideoImportCallable(video(), ROOT, "acct1", factory, mime, serviceUtil, null).call();

        Resource metadata = context.resourceResolver().getResource(ASSET + "/jcr:content/metadata");
        assertNotNull(metadata, "the import did not create " + ASSET);
        assertEquals("video/mp4", metadata.getValueMap().get("dc:format", String.class));
        // The mock resolver also shows uncommitted writes: prove the value was committed, not just set.
        assertFalse(context.resourceResolver().hasChanges(), "the import left uncommitted changes");
    }

    @Test
    void processingIsRequestedOnceAfterTheImportsLastWrite() throws Exception {
        AssetProcessingRequester requester = mock(AssetProcessingRequester.class);

        new VideoImportCallable(video(), ROOT, "acct1", factory, mime, serviceUtil, requester).call();

        InOrder order = inOrder(serviceUtil, requester);
        order.verify(serviceUtil).updateAsset(any(Asset.class), any(ObjectNode.class), any(ResourceResolver.class), eq("acct1"));
        order.verify(requester).requestProcessing(any(ResourceResolver.class), eq(ASSET));
    }

    @Test
    void anAssetThatAlreadyExistsIsNotSentForProcessingAgain() throws Exception {
        new VideoImportCallable(video(), ROOT, "acct1", factory, mime, serviceUtil, null).call();
        AssetProcessingRequester requester = mock(AssetProcessingRequester.class);

        new VideoImportCallable(video(), ROOT, "acct1", factory, mime, serviceUtil, requester).call();

        verify(requester, never()).requestProcessing(any(), anyString());
    }
}
