package com.coresecure.brightcove.wrapper.utils;

import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jcr.Node;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * Three-state pin for the account-root guard.
 *
 * <p>{@link FolderSyncUtil#isAccountRoot} needs the OSGi ConfigurationGrabber to answer
 * "does this path equal some account's root folder?". Outside an OSGi container the
 * lookup cannot answer at all, and that is a THIRD state: distinct from FALSE, which
 * would license the caller to create a Brightcove folder named after whatever the parent
 * node happens to be called. Collapsing it into FALSE is the failure this test exists to
 * catch, and it is invisible in the two-fixture (account root / synced subfolder) pair
 * because both of those have a real answer.</p>
 */
@ExtendWith(AemContextExtension.class)
class FolderSyncUtilTest {

    private final AemContext context =
            new AemContext(org.apache.sling.testing.mock.sling.ResourceResolverType.JCR_MOCK);

    @Test
    void accountRootIsUnknownRatherThanFalseWhenConfigurationCannotBeRead() throws Exception {
        context.create().resource("/content/dam/brightcove_assets/12345",
                "jcr:primaryType", "sling:Folder");
        Node folder = context.resourceResolver()
                .getResource("/content/dam/brightcove_assets/12345").adaptTo(Node.class);

        assertNull(FolderSyncUtil.isAccountRoot(folder),
                "with no OSGi configuration available the answer is 'unknown', never 'false'");
    }
}
