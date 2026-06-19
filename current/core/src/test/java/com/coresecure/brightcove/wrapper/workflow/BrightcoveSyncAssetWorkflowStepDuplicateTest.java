package com.coresecure.brightcove.wrapper.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.coresecure.brightcove.wrapper.objects.Video;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.day.cq.dam.api.Asset;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * BGS-1705 regression test for the AEMaaCS duplicate-video defect.
 *
 * Root cause: {@link BrightcoveSyncAssetWorkflowStep} decides create-vs-update by
 * reading the brc_lastsync marker, but that marker used to be committed only at the
 * very end of execute() — AFTER a ~15s ingest wait. A second, near-simultaneous (or
 * redelivered) AEMaaCS publish event running in its own JCR session therefore still
 * read brc_lastsync == null and created a SECOND Brightcove video.
 *
 * Two invariants make duplicates impossible; this test pins both:
 *   1. activateAsset() COMMITS the sync marker before returning (so a concurrent /
 *      redelivered event in another session can see it). Pre-fix the marker stayed
 *      uncommitted in the session — this test then fails on {@code hasChanges()}.
 *   2. The create-vs-update gate routes on the persisted marker: no marker -> create,
 *      marker present -> update (so the redelivered event updates instead of
 *      re-creating).
 */
@ExtendWith(AemContextExtension.class)
class BrightcoveSyncAssetWorkflowStepDuplicateTest {

    // JCR_MOCK gives real JCR session semantics (save / pending-changes), which is
    // what invariant #1 asserts on.
    private final AemContext context =
            new AemContext(org.apache.sling.testing.mock.sling.ResourceResolverType.JCR_MOCK);

    private static final String ASSET_PATH = "/content/dam/brightcove_assets/12345/sample.mp4";

    private BrightcoveSyncAssetWorkflowStep step;
    private ServiceUtil serviceUtil;

    @BeforeEach
    void setUp() throws Exception {
        step = new BrightcoveSyncAssetWorkflowStep();

        // A real (binary-backed) asset so activateNew()'s getOriginal().getStream() works.
        context.create().asset(ASSET_PATH, new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), "video/mp4");
        context.resourceResolver().commit();

        // Mock the Brightcove boundary: create / update both "succeed" with an id.
        serviceUtil = mock(ServiceUtil.class);
        when(serviceUtil.createVideo(anyString(), any(Asset.class), anyString()))
                .thenReturn(new Video("sample.mp4"));
        when(serviceUtil.createVideoS3(any(Video.class), anyString(), any(InputStream.class)))
                .thenReturn(sentResponse("999111"));
        when(serviceUtil.updateVideo(any(Video.class))).thenReturn(sentResponse("999111"));
    }

    /**
     * Invariant #1: a brand-new asset is created exactly once AND the sync marker is
     * committed (not left pending) before activateAsset returns — closing the window a
     * redelivered event would otherwise exploit.
     */
    @Test
    void newAssetCreatesOnceAndCommitsMarkerBeforeReturning() throws Exception {
        ResourceResolver rr = context.resourceResolver();
        Asset asset = rr.getResource(ASSET_PATH).adaptTo(Asset.class);

        step.activateAsset(rr, asset, serviceUtil);

        verify(serviceUtil, times(1)).createVideoS3(any(Video.class), anyString(), any(InputStream.class));
        verify(serviceUtil, never()).updateVideo(any(Video.class));

        // The marker must be persisted, not sitting uncommitted in this session.
        assertFalse(rr.hasChanges(),
                "BGS-1705: sync marker must be committed before activateAsset returns");

        ModifiableValueMap meta = metadata(rr);
        assertEquals("999111", meta.get(Constants.BRC_ID, String.class), "brc_id must be persisted");
        assertNotNull(meta.get(Constants.BRC_LASTSYNC), "brc_lastsync must be persisted");
    }

    /**
     * Invariant #2: once the marker is present (i.e. a prior event already synced the
     * asset), a subsequent event takes the UPDATE path and never creates a second video.
     * This is the state the redelivered event observes after invariant #1 commits.
     */
    @Test
    void alreadySyncedAssetUpdatesInsteadOfCreatingDuplicate() throws Exception {
        ResourceResolver rr = context.resourceResolver();

        // Simulate the marker a prior (committed) event would have written.
        ModifiableValueMap meta = metadata(rr);
        meta.put(Constants.BRC_ID, "999111");
        meta.put(Constants.BRC_LASTSYNC, System.currentTimeMillis());
        rr.commit();

        Asset asset = rr.getResource(ASSET_PATH).adaptTo(Asset.class);
        step.activateAsset(rr, asset, serviceUtil);

        verify(serviceUtil, never()).createVideoS3(any(Video.class), anyString(), any(InputStream.class));
        verify(serviceUtil, times(1)).updateVideo(any(Video.class));
    }

    private static ModifiableValueMap metadata(ResourceResolver rr) {
        Resource metaRes = rr.getResource(ASSET_PATH).getChild(Constants.ASSET_METADATA_PATH);
        return metaRes.adaptTo(ModifiableValueMap.class);
    }

    private static ObjectNode sentResponse(String videoId) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(Constants.SENT, true);
        node.put(Constants.VIDEOID, videoId);
        return node;
    }
}
