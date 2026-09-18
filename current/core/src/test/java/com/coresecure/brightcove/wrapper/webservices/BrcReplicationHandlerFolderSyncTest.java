package com.coresecure.brightcove.wrapper.webservices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.coresecure.brightcove.wrapper.objects.Video;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.coresecure.brightcove.wrapper.utils.FolderSyncUtil;
import com.day.cq.dam.api.Asset;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * ONPREM-PARITY-PLAN.md §3 Phase 3 item 2: the classic replication agent path
 * ({@code /etc/replication/agents.author/brightcove} -> {@link BrcReplicationHandler})
 * never got the subfolder handling that the DAM publish listener and the workflow step
 * have. On-prem fixed it in {@code d923bdd} (resolve the account id past a synced
 * subfolder) and {@code 1911f57} (move the video into that subfolder after activation);
 * neither reached this line.
 *
 * <p>Pre-port, tests 1 and 2 fail: no {@code moveVideoToFolder} call happens on either
 * activation path. Test 3 fails on the subfoldered case, which read the SUBFOLDER's name
 * as the account id.</p>
 *
 * <p>Test 4 is the thin case. It carries its weight because the account-root guard
 * needs the OSGi configuration to answer "is this the account root?", and there is no
 * OSGi container here: the question has no answer, which is a third state distinct from
 * "no, go ahead and create a folder". If it ever collapses into the latter, this test
 * goes red instead of the connector silently creating a Brightcove folder named after
 * an account id.</p>
 */
@ExtendWith(AemContextExtension.class)
class BrcReplicationHandlerFolderSyncTest {

    private final AemContext context =
            new AemContext(org.apache.sling.testing.mock.sling.ResourceResolverType.JCR_MOCK);

    private static final String ACCOUNT_FOLDER = "/content/dam/brightcove_assets/12345";
    private static final String SUBFOLDER = ACCOUNT_FOLDER + "/synced-folder";
    private static final String SUBFOLDERED_ASSET = SUBFOLDER + "/sample.mp4";
    private static final String ROOT_LEVEL_ASSET = ACCOUNT_FOLDER + "/root-level.mp4";
    private static final String BRIGHTCOVE_FOLDER_ID = "bc-folder-1";
    private static final String VIDEO_ID = "999111";

    private BrcReplicationHandler handler;
    private ServiceUtil serviceUtil;

    @BeforeEach
    void setUp() throws Exception {
        handler = new BrcReplicationHandler();

        context.create().resource(ACCOUNT_FOLDER, "jcr:primaryType", "sling:Folder");
        // brc_folder_id is the marker that makes a DAM folder a SYNCED Brightcove
        // subfolder; sling:OrderedFolder is what the folder sync itself creates.
        context.create().resource(SUBFOLDER,
                "jcr:primaryType", "sling:OrderedFolder",
                FolderSyncUtil.BRC_FOLDER_ID, BRIGHTCOVE_FOLDER_ID);

        // Binary-backed assets so activateNew()'s getOriginal().getStream() works.
        context.create().asset(SUBFOLDERED_ASSET,
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), "video/mp4");
        context.create().asset(ROOT_LEVEL_ASSET,
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), "video/mp4");
        context.resourceResolver().commit();

        serviceUtil = mock(ServiceUtil.class);
        when(serviceUtil.createVideoS3(any(Video.class), anyString(), any(InputStream.class)))
                .thenReturn(sentResponse(VIDEO_ID));
        when(serviceUtil.updateVideo(any(Video.class))).thenReturn(sentResponse(VIDEO_ID));
    }

    @Test
    void newSubfolderedVideoIsMovedIntoItsBrightcoveFolder() {
        handler.activateNew(asset(SUBFOLDERED_ASSET), serviceUtil, new Video("sample.mp4"),
                metadata(SUBFOLDERED_ASSET));

        verify(serviceUtil, times(1)).moveVideoToFolder(BRIGHTCOVE_FOLDER_ID, VIDEO_ID);
        // The folder already exists; nothing should be created.
        verify(serviceUtil, never()).createFolder(anyString());
    }

    @Test
    void modifiedSubfolderedVideoIsMovedIntoItsBrightcoveFolder() {
        handler.activateModified(asset(SUBFOLDERED_ASSET), serviceUtil, new Video("sample.mp4"),
                metadata(SUBFOLDERED_ASSET));

        verify(serviceUtil, times(1)).moveVideoToFolder(BRIGHTCOVE_FOLDER_ID, VIDEO_ID);
        verify(serviceUtil, never()).createFolder(anyString());
    }

    /**
     * The seam replicateAssets() uses, not the helper behind it: pre-port this read the
     * parent's own name, so a subfoldered asset produced a Brightcove FOLDER id where an
     * account id was expected and replication bailed out with "Account not existing".
     */
    @Test
    void accountIdResolutionWalksUpPastASyncedSubfolder() {
        assertEquals("12345", BrcReplicationHandler.accountIdFor(resource(SUBFOLDER)),
                "an asset in a synced subfolder must resolve to the ACCOUNT folder above "
                        + "it, not to the subfolder's own name");
        assertEquals("12345", BrcReplicationHandler.accountIdFor(resource(ACCOUNT_FOLDER)),
                "an asset at the account root resolves to that folder's name");
        assertNull(FolderSyncUtil.resolveAccountId(null),
                "no parent means no answer, not a guessed account id");
    }

    @Test
    void unsyncedParentCreatesNothingWhileTheAccountRootQuestionIsUnanswerable() {
        // FolderSyncUtilTest pins the "no answer" verdict itself; this pins the
        // consequence: nothing is created on the strength of an unanswered question.
        handler.activateNew(asset(ROOT_LEVEL_ASSET), serviceUtil, new Video("root-level.mp4"),
                metadata(ROOT_LEVEL_ASSET));

        verify(serviceUtil, never()).createFolder(anyString());
        verify(serviceUtil, never()).moveVideoToFolder(anyString(), anyString());
    }

    private Asset asset(String path) {
        return context.resourceResolver().getResource(path).adaptTo(Asset.class);
    }

    private Resource resource(String path) {
        return context.resourceResolver().getResource(path);
    }

    private ModifiableValueMap metadata(String assetPath) {
        Resource metaRes = context.resourceResolver().getResource(assetPath)
                .getChild(Constants.ASSET_METADATA_PATH);
        return metaRes.adaptTo(ModifiableValueMap.class);
    }

    private static ObjectNode sentResponse(String videoId) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(Constants.SENT, true);
        node.put(Constants.VIDEOID, videoId);
        return node;
    }
}
