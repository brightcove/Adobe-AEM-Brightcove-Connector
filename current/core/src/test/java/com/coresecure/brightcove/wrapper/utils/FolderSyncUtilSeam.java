package com.coresecure.brightcove.wrapper.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;

/**
 * Lets tests in other packages answer the account-root question that
 * {@link FolderSyncUtil#isAccountRoot} normally asks OSGi. Without it the answer is
 * always "unknown" in a unit test, and the TRUE branch is unreachable.
 *
 * <p>Always call {@link #reset()} in {@code @AfterEach}: the seam is static, and
 * leaking a configured answer would turn the three-state pins in FolderSyncUtilTest
 * and BrcReplicationHandlerFolderSyncTest into false greens.</p>
 */
public final class FolderSyncUtilSeam {

    private FolderSyncUtilSeam() {
    }

    /** One configured account whose DAM integration path is {@code integrationPath}. */
    public static void configureAccount(String accountId, String integrationPath) {
        ConfigurationService cs = mock(ConfigurationService.class);
        when(cs.getAssetIntegrationPath()).thenReturn(integrationPath);
        ConfigurationGrabber cg = mock(ConfigurationGrabber.class);
        when(cg.getAvailableServices()).thenReturn(Collections.singleton(accountId));
        when(cg.getConfigurationService(accountId)).thenReturn(cs);
        FolderSyncUtil.configurationSource = () -> cg;
    }

    public static void reset() {
        FolderSyncUtil.configurationSource = ServiceUtil::getConfigurationGrabber;
    }
}
