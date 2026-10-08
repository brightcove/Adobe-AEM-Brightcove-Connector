package com.coresecure.brightcove.wrapper.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.coresecure.brightcove.wrapper.sling.ServiceUtil;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * An account root the pre-guard bug poisoned: it carries a {@code brc_folder_id}
 * naming a Video Cloud folder called after the account. Checking that property before
 * the account-root question filed every root-level video into that folder.
 *
 * <p>The account root means "no folder": the stored id is ignored, the property is
 * removed, and the Video Cloud folder is not touched. A real subfolder's id is still
 * used. Context: current/docs/core-folder-sync.md "Trap 4".</p>
 */
@ExtendWith(AemContextExtension.class)
class FolderSyncUtilPoisonedRootTest {

    private final AemContext context =
            new AemContext(org.apache.sling.testing.mock.sling.ResourceResolverType.JCR_MOCK);

    private static final String INTEGRATION = "/content/dam/brightcove_assets";
    private static final String ACCOUNT = "12345";
    private static final String ROOT = INTEGRATION + "/" + ACCOUNT;
    private static final String SUBFOLDER = ROOT + "/synced-folder";
    private static final String STALE_ID = "bc-folder-named-after-account";
    private static final String REAL_ID = "bc-folder-real";
    private static final String VIDEO_ID = "999111";

    private ServiceUtil serviceUtil;

    @BeforeEach
    void setUp() throws Exception {
        context.create().resource(ROOT, "jcr:primaryType", "sling:OrderedFolder",
                FolderSyncUtil.BRC_FOLDER_ID, STALE_ID);
        context.create().resource(SUBFOLDER, "jcr:primaryType", "sling:OrderedFolder",
                FolderSyncUtil.BRC_FOLDER_ID, REAL_ID);
        context.create().resource(ROOT + "/root-level.mp4", "jcr:primaryType", "nt:unstructured");
        context.create().resource(SUBFOLDER + "/sub.mp4", "jcr:primaryType", "nt:unstructured");
        context.resourceResolver().commit();
        serviceUtil = mock(ServiceUtil.class);
        FolderSyncUtilSeam.configureAccount(ACCOUNT, INTEGRATION + "/");
    }

    @AfterEach
    void tearDown() {
        FolderSyncUtilSeam.reset();
    }

    @Test
    void poisonedRootGivesNoFolderAndLosesTheStoredId() throws Exception {
        FolderSyncUtil.syncFolder(serviceUtil, VIDEO_ID, node(ROOT + "/root-level.mp4"),
                FolderSyncUtil.NO_RETRY_DELAY);

        verify(serviceUtil, never()).moveVideoToFolder(anyString(), anyString());
        verify(serviceUtil, never()).createFolder(anyString());
        // The folder may hold a customer's videos: neither emptied nor deleted.
        verify(serviceUtil, never()).removeVideoFromFolder(anyString(), anyString());
        Node root = node(ROOT);
        assertFalse(root.hasProperty(FolderSyncUtil.BRC_FOLDER_ID),
                "the stale brc_folder_id must be removed from the account root");
        assertFalse(root.getSession().hasPendingChanges(), "the removal must be saved, not left pending");
    }

    @Test
    void realSubfolderStillUsesItsStoredId() throws Exception {
        FolderSyncUtil.syncFolder(serviceUtil, VIDEO_ID, node(SUBFOLDER + "/sub.mp4"),
                FolderSyncUtil.NO_RETRY_DELAY);

        verify(serviceUtil, times(1)).moveVideoToFolder(REAL_ID, VIDEO_ID);
        verify(serviceUtil, never()).createFolder(anyString());
        assertEquals(REAL_ID, node(SUBFOLDER).getProperty(FolderSyncUtil.BRC_FOLDER_ID).getString());
        assertTrue(node(ROOT).hasProperty(FolderSyncUtil.BRC_FOLDER_ID),
                "a subfolder publish must not touch the root");
    }

    @Test
    void poisonedRootStillResolvesToItsOwnAccount() throws Exception {
        assertEquals(ACCOUNT, FolderSyncUtil.resolveAccountId(node(ROOT)),
                "walking up from a poisoned root yields the integration folder, and the agent "
                        + "path then skips the asset as 'Account not existing'");
        assertEquals(ACCOUNT, FolderSyncUtil.resolveAccountId(node(SUBFOLDER)));
    }

    @Test
    void unknownAccountRootKeepsTheStoredIdAndCreatesNothing() throws Exception {
        // Three states: with no configuration the root cannot be recognised, so the
        // helper neither repairs (a claim it cannot make) nor creates a folder.
        FolderSyncUtilSeam.reset();
        FolderSyncUtil.syncFolder(serviceUtil, VIDEO_ID, node(ROOT + "/root-level.mp4"),
                FolderSyncUtil.NO_RETRY_DELAY);

        verify(serviceUtil, never()).createFolder(anyString());
        // ...and keeps the move, which cannot create a folder (Trap 4, "three states").
        verify(serviceUtil, times(1)).moveVideoToFolder(STALE_ID, VIDEO_ID);
        assertTrue(node(ROOT).hasProperty(FolderSyncUtil.BRC_FOLDER_ID),
                "nothing verified this is the account root, so nothing is removed");
    }

    /**
     * The service user cannot save the removal: the property is put back in the session, with
     * its original type, so the caller's BGS-1705 marker commit on the same session does not
     * carry a removal it is not allowed to persist. Still no move and no create.
     */
    @Test
    void failedRepairSaveRestoresThePropertyWithItsOriginalType() throws Exception {
        Node root = node(ROOT);
        root.setProperty(FolderSyncUtil.BRC_FOLDER_ID, 4242L);
        root.getSession().save();
        Node asset = denySave(node(ROOT + "/root-level.mp4"));

        FolderSyncUtil.syncFolder(serviceUtil, VIDEO_ID, asset, FolderSyncUtil.NO_RETRY_DELAY);

        verify(serviceUtil, never()).moveVideoToFolder(anyString(), anyString());
        verify(serviceUtil, never()).createFolder(anyString());
        assertTrue(root.hasProperty(FolderSyncUtil.BRC_FOLDER_ID), "the failed removal must be undone in the session");
        assertEquals(PropertyType.LONG, root.getProperty(FolderSyncUtil.BRC_FOLDER_ID).getType(),
                "restored as " + PropertyType.nameFromValue(root.getProperty(FolderSyncUtil.BRC_FOLDER_ID).getType()));
        assertEquals(4242L, root.getProperty(FolderSyncUtil.BRC_FOLDER_ID).getLong());
    }

    /** The asset node, whose parent's session refuses save() as a read-only service user would. */
    private static Node denySave(Node asset) throws Exception {
        Node parent = asset.getParent();
        Session session = parent.getSession();
        Session denying = proxy(Session.class, session, (method, args) ->
                "save".equals(method) ? new AccessDeniedException("test: save denied") : null);
        Node parentProxy = proxy(Node.class, parent, (method, args) ->
                "getSession".equals(method) ? denying : null);
        return proxy(Node.class, asset, (method, args) -> "getParent".equals(method) ? parentProxy : null);
    }

    private interface Intercept {
        /** A replacement result, a Throwable to throw, or null to delegate. */
        Object apply(String method, Object[] args) throws Exception;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, T target, Intercept override) {
        InvocationHandler h = (p, m, args) -> {
            Object replaced = override.apply(m.getName(), args);
            if (replaced instanceof Throwable) {
                throw (Throwable) replaced;
            }
            if (replaced != null) {
                return replaced;
            }
            try {
                return m.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, h);
    }

    private Node node(String path) {
        return context.resourceResolver().getResource(path).adaptTo(Node.class);
    }
}
