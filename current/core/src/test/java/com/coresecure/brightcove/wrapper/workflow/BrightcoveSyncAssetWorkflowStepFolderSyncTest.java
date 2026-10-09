package com.coresecure.brightcove.wrapper.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import javax.jcr.Node;

import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.coresecure.brightcove.wrapper.objects.Video;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.FolderSyncUtil;
import com.coresecure.brightcove.wrapper.utils.FolderSyncUtilSeam;
import com.day.cq.dam.api.Asset;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * The workflow-step caller of FolderSyncUtil on an account root the pre-guard bug
 * poisoned. Context: current/docs/core-folder-sync.md "Trap 4".
 */
@ExtendWith(AemContextExtension.class)
class BrightcoveSyncAssetWorkflowStepFolderSyncTest {

    private final AemContext context =
            new AemContext(org.apache.sling.testing.mock.sling.ResourceResolverType.JCR_MOCK);

    private static final String ROOT = "/content/dam/brightcove_assets/12345";
    private static final String ASSET = ROOT + "/root-level.mp4";

    @AfterEach
    void tearDown() {
        FolderSyncUtilSeam.reset();
    }

    @Test
    void poisonedAccountRootGivesNoFolderAndIsRepaired() throws Exception {
        context.create().resource(ROOT, "jcr:primaryType", "sling:OrderedFolder",
                FolderSyncUtil.BRC_FOLDER_ID, "bc-folder-named-after-account");
        context.create().asset(ASSET, new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), "video/mp4");
        ResourceResolver rr = context.resourceResolver();
        rr.commit();
        FolderSyncUtilSeam.configureAccount("12345", "/content/dam/brightcove_assets");

        ServiceUtil serviceUtil = mock(ServiceUtil.class);
        when(serviceUtil.createVideo(anyString(), any(Asset.class), anyString())).thenReturn(new Video("root-level.mp4"));
        when(serviceUtil.createVideoS3(any(Video.class), anyString(), any())).thenReturn(sent("999111"));

        new BrightcoveSyncAssetWorkflowStep().activateAsset(rr, rr.getResource(ASSET).adaptTo(Asset.class), serviceUtil);

        verify(serviceUtil, never()).moveVideoToFolder(anyString(), anyString());
        verify(serviceUtil, never()).createFolder(anyString());
        assertFalse(rr.getResource(ROOT).adaptTo(Node.class).hasProperty(FolderSyncUtil.BRC_FOLDER_ID),
                "the stale brc_folder_id must be removed from the account root");
    }

    private static ObjectNode sent(String videoId) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(Constants.SENT, true);
        node.put(Constants.VIDEOID, videoId);
        return node;
    }
}
