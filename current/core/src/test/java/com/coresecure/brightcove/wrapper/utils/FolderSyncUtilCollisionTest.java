package com.coresecure.brightcove.wrapper.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import javax.jcr.Node;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * A Video Cloud folder name that is already taken, and an asset that sits in the
 * integration path root.
 *
 * <p>Video Cloud enforces case-insensitive unique folder names, so a DAM folder whose
 * name a Video Cloud folder already has makes {@code POST /folders} return
 * {@code 409 FOLDER_NAME_IN_USE}. {@code ServiceUtil.createFolder} reads only {@code id},
 * so any non-201 arrives here as {@code ""}: indistinguishable from a network failure.
 * Before this fix the helper retried the same create, got the same 409, logged
 * "No folder created" and returned, leaving the video at the account root while the
 * activation still logged success (measured 2026-11-20 on 7.4.3-cloud: two same-named
 * DAM parents, second video unfiled). The rule now: a name that is taken means the
 * folder EXISTS, so look it up and file the video into it.
 *
 * <p>The integration path root is the other half: {@code /content/dam/brightcove_assets}
 * is not a folder the customer made for videos, so an asset sitting directly in it gets
 * no Video Cloud folder and a WARN, exactly like the account root. Filing it into a
 * Video Cloud folder that merely shares the last path segment's name would attach it to
 * a folder that has nothing to do with this asset.
 *
 * <p>Context: current/docs/core-folder-sync.md "Trap 5" and "Trap 6".
 */
@ExtendWith(AemContextExtension.class)
class FolderSyncUtilCollisionTest {

    private final AemContext context =
            new AemContext(org.apache.sling.testing.mock.sling.ResourceResolverType.JCR_MOCK);

    private static final String INTEGRATION = "/content/dam/brightcove_assets";
    private static final String ACCOUNT = "12345";
    private static final String ROOT = INTEGRATION + "/" + ACCOUNT;
    private static final String SUBFOLDER = ROOT + "/same";
    private static final String ASSET = SUBFOLDER + "/clip.mp4";
    private static final String EXISTING_FOLDER_ID = "bc-existing-folder";
    private static final String VIDEO_ID = "999111";
    /**
     * The same retry branch as {@link FolderSyncUtil#WORKFLOW_RETRY_DELAY_SECONDS} (the
     * sleep only runs when the delay is positive), kept short so the unit suite does not
     * pay 15 seconds for one assertion.
     */
    private static final long ONE_SECOND_RETRY = 1L;

    private ServiceUtil serviceUtil;

    @BeforeEach
    void setUp() throws Exception {
        context.create().resource(ROOT, "jcr:primaryType", "sling:OrderedFolder");
        context.create().resource(SUBFOLDER, "jcr:primaryType", "sling:OrderedFolder");
        context.create().asset(ASSET, new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), "video/mp4");
        context.create().asset(INTEGRATION + "/top-level.mp4",
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), "video/mp4");
        context.resourceResolver().commit();
        serviceUtil = mock(ServiceUtil.class);
        FolderSyncUtilSeam.configureAccount(ACCOUNT, INTEGRATION);
    }

    @AfterEach
    void tearDown() {
        FolderSyncUtilSeam.reset();
    }

    /**
     * The measured defect. The create is refused because the name is taken; the folder
     * with that name is the one the video belongs in.
     */
    @Test
    void takenFolderNameFilesTheVideoIntoTheExistingFolder() throws Exception {
        when(serviceUtil.createFolderResponse("same")).thenReturn(collision("FOLDER_NAME_IN_USE"));
        when(serviceUtil.findFolderIdByName("same")).thenReturn(EXISTING_FOLDER_ID);

        FolderSyncUtil.syncFolder(serviceUtil, VIDEO_ID, node(ASSET), FolderSyncUtil.NO_RETRY_DELAY);

        verify(serviceUtil, times(1)).moveVideoToFolder(EXISTING_FOLDER_ID, VIDEO_ID);
        assertEquals(EXISTING_FOLDER_ID,
                node(SUBFOLDER).getProperty(FolderSyncUtil.BRC_FOLDER_ID).getString(),
                "the DAM folder is now synced to the existing Video Cloud folder, so later "
                        + "videos skip the create entirely");
        verify(serviceUtil, times(1)).createFolderResponse("same");
    }

    /**
     * Case is not a difference: Video Cloud refuses {@code Clips} when {@code clips}
     * exists, so the lookup has to match the same way. Driven through the helper, with
     * the create refused exactly as Video Cloud refuses it.
     */
    @Test
    void aFolderNameThatDiffersOnlyByCaseIsTheSameFolder() throws Exception {
        context.create().resource(ROOT + "/Clips", "jcr:primaryType", "sling:OrderedFolder");
        context.create().asset(ROOT + "/Clips/clip.mp4",
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), "video/mp4");
        context.resourceResolver().commit();
        when(serviceUtil.createFolderResponse("Clips")).thenReturn(collision("FOLDER_NAME_IN_USE"));
        when(serviceUtil.findFolderIdByName("Clips")).thenReturn("bc-lowercase-clips");

        FolderSyncUtil.syncFolder(serviceUtil, VIDEO_ID, node(ROOT + "/Clips/clip.mp4"),
                FolderSyncUtil.NO_RETRY_DELAY);

        verify(serviceUtil, times(1)).moveVideoToFolder("bc-lowercase-clips", VIDEO_ID);
    }

    /**
     * The integration path root is not a customer folder, so it means "no folder", like
     * the account root. The old behaviour created a Video Cloud folder named after the
     * path segment ({@code brightcove_assets}), which on an account that already has one
     * failed with the same 409 and left the video unfiled.
     */
    @Test
    void assetInTheIntegrationPathRootGetsNoFolder() throws Exception {
        FolderSyncUtil.syncFolder(serviceUtil, VIDEO_ID, node(INTEGRATION + "/top-level.mp4"),
                FolderSyncUtil.NO_RETRY_DELAY);

        verify(serviceUtil, never()).createFolderResponse(anyString());
        verify(serviceUtil, never()).findFolderIdByName(anyString());
        verify(serviceUtil, never()).moveVideoToFolder(anyString(), anyString());
        assertFalse(node(INTEGRATION).hasProperty(FolderSyncUtil.BRC_FOLDER_ID),
                "the integration path root must not be marked as synced to a folder");
    }

    /**
     * Nothing to reuse and nothing created: the video stays unfiled, but loudly, and the
     * DAM folder is not marked as synced (a stored id would file later videos into a
     * folder that does not exist).
     */
    @Test
    void unresolvableFolderLeavesTheVideoUnfiledAndUnmarked() throws Exception {
        when(serviceUtil.createFolderResponse("same")).thenReturn(collision("FOLDER_NAME_IN_USE"));
        when(serviceUtil.findFolderIdByName("same")).thenReturn("");

        FolderSyncUtil.syncFolder(serviceUtil, VIDEO_ID, node(ASSET), FolderSyncUtil.NO_RETRY_DELAY);

        verify(serviceUtil, never()).moveVideoToFolder(anyString(), anyString());
        assertFalse(node(SUBFOLDER).hasProperty(FolderSyncUtil.BRC_FOLDER_ID),
                "an unresolved folder must not be recorded on the DAM folder");
    }

    /**
     * A create that fails for a reason other than a collision (here: an auth failure with
     * no error body) must still look the name up, because a folder of that name may exist.
     */
    @Test
    void anEmptyCreateResponseStillLooksTheNameUp() throws Exception {
        when(serviceUtil.createFolderResponse("same")).thenReturn(null);
        when(serviceUtil.findFolderIdByName("same")).thenReturn(EXISTING_FOLDER_ID);

        FolderSyncUtil.syncFolder(serviceUtil, VIDEO_ID, node(ASSET), FolderSyncUtil.NO_RETRY_DELAY);

        verify(serviceUtil, times(1)).moveVideoToFolder(EXISTING_FOLDER_ID, VIDEO_ID);
    }

    /**
     * The workflow path waits and re-reads before its one retry. If a concurrent publish
     * created the folder while it waited, the retry's create is refused and the lookup
     * finds it: the video is still filed, not left at the account root.
     */
    @Test
    void theRetryFindsAFolderCreatedWhileTheWorkflowWaited() throws Exception {
        when(serviceUtil.createFolderResponse("same"))
                .thenReturn(collision("FOLDER_NAME_IN_USE"))
                .thenReturn(collision("FOLDER_NAME_IN_USE"));
        // absent on the first lookup, present after the retry
        when(serviceUtil.findFolderIdByName("same")).thenReturn("", "bc-created-concurrently");

        FolderSyncUtil.syncFolder(serviceUtil, VIDEO_ID, withNoOpRefresh(node(ASSET)),
                ONE_SECOND_RETRY);

        verify(serviceUtil, times(2)).createFolderResponse("same");
        verify(serviceUtil, times(1)).moveVideoToFolder("bc-created-concurrently", VIDEO_ID);
    }

    /**
     * The pre-collision code wrote {@code brc_folder_id} onto the integration path root
     * whenever it managed to create the folder named after the path segment. That root now
     * means "no folder", so the stale id is ignored and removed exactly like the account
     * root's: otherwise the new rule would be bypassed on every instance the old one
     * touched, and those videos would keep being filed into that folder forever.
     */
    @Test
    void integrationPathRootCarryingAStaleIdIsRepairedRatherThanUsed() throws Exception {
        Node root = node(INTEGRATION);
        root.setProperty(FolderSyncUtil.BRC_FOLDER_ID, "bc-folder-named-after-the-path-segment");
        root.getSession().save();

        FolderSyncUtil.syncFolder(serviceUtil, VIDEO_ID, node(INTEGRATION + "/top-level.mp4"),
                FolderSyncUtil.NO_RETRY_DELAY);

        verify(serviceUtil, never()).moveVideoToFolder(anyString(), anyString());
        verify(serviceUtil, never()).createFolderResponse(anyString());
        assertFalse(node(INTEGRATION).hasProperty(FolderSyncUtil.BRC_FOLDER_ID),
                "the stale id must be removed from the integration path root");
    }

    /**
     * JCR_MOCK cannot do {@code Node.refresh(boolean)} at all: {@code AbstractItem.refresh}
     * throws {@code UnsupportedOperationException} whatever the flag, which would make the
     * retry branch unreachable here even though a real Oak session runs it (measured live:
     * the refused create is attempted twice). The proxy turns the refresh into the no-op
     * that {@code refresh(true)} is when nothing changed on the server.
     */
    private static Node withNoOpRefresh(Node asset) throws Exception {
        Node parent = asset.getParent();
        Node parentProxy = (Node) Proxy.newProxyInstance(Node.class.getClassLoader(),
                new Class<?>[] {Node.class}, (p, method, args) -> {
                    if ("refresh".equals(method.getName())) {
                        return null;
                    }
                    try {
                        return method.invoke(parent, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        return (Node) Proxy.newProxyInstance(Node.class.getClassLoader(),
                new Class<?>[] {Node.class}, (p, method, args) -> {
                    if ("getParent".equals(method.getName())) {
                        return parentProxy;
                    }
                    try {
                        return method.invoke(asset, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /** The CMS response {@code utils/HttpServices.executePost} builds for a refused create. */
    private static ObjectNode collision(String errorCode) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("error", errorCode);
        node.put("error_message", "Folder names must be unique (case-insensitive.)");
        return node;
    }

    private Node node(String path) {
        return context.resourceResolver().getResource(path).adaptTo(Node.class);
    }
}
