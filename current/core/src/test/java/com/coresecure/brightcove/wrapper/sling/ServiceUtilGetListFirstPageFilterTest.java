package com.coresecure.brightcove.wrapper.sling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.coresecure.brightcove.wrapper.BrightcoveAPI;
import com.coresecure.brightcove.wrapper.api.CmsAPI;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Pins the CORRECT behaviour for open defect 3b item 10 (parity plan): the 8-arg
 * {@code ServiceUtil.getList} fetches its first page with the 4-arg
 * {@code CmsAPI.getVideos}, which hard-codes dam_only=true / clips_only=false,
 * so the caller's dam_only and clips_only are ignored on page one while
 * later pages (and the count) honour them.
 *
 * Disabled until the defect is fixed; it was run enabled first and failed on the
 * current code (the first page was requested with the 4-arg overload).
 */
class ServiceUtilGetListFirstPageFilterTest {

    @Test
    @Disabled("3b item 10: getList 8-arg ignores dam_only/clips_only on the first page; remove this line when fixed")
    void firstPageHonoursDamOnlyAndClipsOnly() throws Exception {
        ServiceUtil serviceUtil = mock(ServiceUtil.class);
        when(serviceUtil.getList(any(), anyInt(), anyInt(), anyBoolean(), anyString(),
                anyString(), anyBoolean(), anyBoolean())).thenCallRealMethod();

        CmsAPI cms = mock(CmsAPI.class);
        ArrayNode empty = JsonNodeFactory.instance.arrayNode();
        when(cms.getVideos(anyString(), anyInt(), anyInt(), anyString())).thenReturn(empty);
        when(cms.getVideos(anyString(), anyInt(), anyInt(), anyString(), anyBoolean(),
                anyBoolean())).thenReturn(empty);
        when(cms.addThumbnail(any(ArrayNode.class))).thenReturn(empty);

        BrightcoveAPI api = mock(BrightcoveAPI.class);
        Field cmsField = BrightcoveAPI.class.getDeclaredField("cms");
        cmsField.setAccessible(true);
        cmsField.set(api, cms);
        Field apiField = ServiceUtil.class.getDeclaredField("brAPI");
        apiField.setAccessible(true);
        apiField.set(serviceUtil, api);

        serviceUtil.getList(false, 0, 100, false, "q", "name", false, true);

        verify(cms, times(1)).getVideos(eq("q"), eq(100), eq(0), eq("name"), eq(false), eq(true));
    }
}
