package com.coresecure.brightcove.wrapper.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.jcr.Node;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * Row 32 (BGS-1600 folder sync) on the classic replication-agent path: nested synced DAM
 * folders, with NO account configuration readable (the fallback branch of
 * {@link FolderSyncUtil#resolveAccountId}).
 *
 * <p>For an asset at {@code <account>/A/B/x.mp4} with A and B both synced, the old walk
 * stopped one level up and returned "A" as the account id, so
 * {@code BrcReplicationHandler.replicateAssets} logged "Account not existing" and the
 * asset was silently never pushed. With configuration the account is resolved by path
 * (BrcReplicationHandlerFolderSyncTest); without it the walk now continues while the
 * folder carries brc_folder_id. Was a disabled red pin; enabled with the fix.
 * Context: current/docs/core-folder-sync.md "Trap 3".</p>
 */
@ExtendWith(AemContextExtension.class)
class FolderSyncUtilNestedSubfolderTest {

    private final AemContext context =
            new AemContext(org.apache.sling.testing.mock.sling.ResourceResolverType.JCR_MOCK);

    @Test
    void assetTwoSyncedFoldersDeepResolvesToTheAccountFolder() throws Exception {
        String account = "/content/dam/brightcove_assets/12345";
        context.create().resource(account, "jcr:primaryType", "sling:Folder");
        context.create().resource(account + "/outer",
                "jcr:primaryType", "sling:OrderedFolder", FolderSyncUtil.BRC_FOLDER_ID, "bc-outer");
        context.create().resource(account + "/outer/inner",
                "jcr:primaryType", "sling:OrderedFolder", FolderSyncUtil.BRC_FOLDER_ID, "bc-inner");
        context.resourceResolver().commit();

        Node inner = context.resourceResolver().getResource(account + "/outer/inner").adaptTo(Node.class);
        assertEquals("12345", FolderSyncUtil.resolveAccountId(inner));
    }
}
