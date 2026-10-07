package com.coresecure.brightcove.wrapper.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.jcr.Node;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * 🔴 Defect pin, row 32 (BGS-1600 folder sync) on the classic replication-agent path.
 *
 * <p>{@link FolderSyncUtil#resolveAccountId} walks up exactly ONE level past a synced
 * subfolder. Video Cloud folders are flat, but DAM folders nest, and every DAM folder
 * an asset is published from becomes a synced folder (it gets brc_folder_id). For an
 * asset at {@code <account>/A/B/x.mp4} with A and B both synced, the walk stops at A
 * and returns "A" as the account id. {@code BrcReplicationHandler.replicateAssets}
 * then finds no configuration for "A", logs "Account not existing" and returns OK, so
 * the asset is silently never pushed. BrightcovePublishListener and the workflow step
 * are not affected: they match the payload path against damIntegrationPath instead.</p>
 *
 * <p>Disabled because it fails on the current code (verified red when written). Enable
 * it with the fix, e.g. walking up until the parent no longer carries brc_folder_id.
 * Context: current/docs/core-folder-sync.md "Trap 3".</p>
 */
@ExtendWith(AemContextExtension.class)
class FolderSyncUtilNestedSubfolderTest {

    private final AemContext context =
            new AemContext(org.apache.sling.testing.mock.sling.ResourceResolverType.JCR_MOCK);

    @Disabled("defect: resolveAccountId stops one level up; nested synced DAM folders resolve to the outer folder name")
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
