package com.coresecure.brightcove.wrapper.sling;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/**
 * ONPREM-PARITY-PLAN.md §3 Phase 3 item 3 (on-prem {@code ccdaeb2}): a full-scroll
 * import did not respect the AEM_NO_DAM tag.
 *
 * <p>{@code CmsAPI.getVideos(q, limit, offset, sort)} hardcodes {@code dam_only = true},
 * so the FIRST page of a listing always excluded tagged videos, while the full-scroll
 * continuation pages passed whatever the caller supplied. This overload supplied
 * {@code false}, so an import pulled AEM_NO_DAM videos in from page 2 onwards: a defect
 * that is invisible in any account small enough to fit on one page.</p>
 *
 * <p>Asserting the delegation rather than the HTTP call is deliberate: the query string
 * is built two layers down in {@code CmsAPI}, behind an authenticated Brightcove call,
 * and the defect was entirely in which value this overload passed on.</p>
 */
class ServiceUtilDamOnlyDefaultTest {

    @Test
    void sixArgOverloadRequestsDamOnlyVideos() {
        ServiceUtil serviceUtil = mock(ServiceUtil.class);
        when(serviceUtil.getList(anyBoolean(), anyInt(), anyInt(), anyBoolean(), anyString(),
                anyString())).thenCallRealMethod();
        when(serviceUtil.getList(anyBoolean(), anyInt(), anyInt(), anyBoolean(), anyString(),
                anyString(), anyBoolean(), anyBoolean())).thenReturn("{}");

        serviceUtil.getList(false, 0, 100, true, "some-query", "name");

        verify(serviceUtil, times(1)).getList(false, 0, 100, true, "some-query", "name",
                true, false);
    }

    /**
     * The sibling overload that takes dam_only explicitly must keep honouring the
     * caller, so the fix above cannot be mistaken for "always filter".
     */
    @Test
    void explicitDamOnlyIsStillHonoured() {
        ServiceUtil serviceUtil = mock(ServiceUtil.class);
        when(serviceUtil.getList(anyBoolean(), anyInt(), anyInt(), anyBoolean(), anyString(),
                anyString(), anyBoolean())).thenCallRealMethod();
        when(serviceUtil.getList(anyBoolean(), anyInt(), anyInt(), anyBoolean(), anyString(),
                anyString(), anyBoolean(), anyBoolean())).thenReturn("{}");

        serviceUtil.getList(false, 0, 100, true, "some-query", "name", false);

        verify(serviceUtil, times(1)).getList(false, 0, 100, true, "some-query", "name",
                false, false);
    }
}
