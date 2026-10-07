package com.coresecure.brightcove.wrapper.webservices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.sling.api.SlingHttpServletRequest;
import org.junit.jupiter.api.Test;

import com.coresecure.brightcove.wrapper.BrightcoveAPI;
import com.coresecure.brightcove.wrapper.api.CmsAPI;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** BGS-1600: the update_labels action must never answer a failure or a malformed call with {}. */
class BrcApiUpdateLabelsTest {

    private static ObjectNode call(SlingHttpServletRequest request, CmsAPI cms) throws Exception {
        BrcApi api = new BrcApi();
        BrightcoveAPI brAPI = new BrightcoveAPI("c", "s", "123", null);
        set(brAPI, "cms", cms);
        set(api, "brAPI", brAPI);
        Method m = BrcApi.class.getDeclaredMethod("updateLabels", SlingHttpServletRequest.class);
        m.setAccessible(true);
        return (ObjectNode) m.invoke(api, request);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void upstreamErrorIsPassedThroughUnchanged() throws Exception {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getParameter("labels")).thenReturn("a");
        when(request.getParameter("videoId")).thenReturn("v1");
        when(request.getParameterValues("labels")).thenReturn(new String[] {"a"});
        CmsAPI cms = mock(CmsAPI.class);
        ObjectNode error = JsonNodeFactory.instance.objectNode();
        error.put("error_code", 502);
        error.put("message", "No response from Brightcove");
        when(cms.updateLabels(any(String.class), any(String[].class))).thenReturn(error);

        ObjectNode r = call(request, cms);

        assertEquals(502, r.get("error_code").asInt());
        assertEquals("No response from Brightcove", r.get("message").asText());
    }

    @Test
    void missingParametersAre400NotAnEmptySuccess() throws Exception {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        CmsAPI cms = mock(CmsAPI.class);

        ObjectNode r = call(request, cms);

        assertEquals(400, r.get("error_code").asInt());
        assertTrue(r.get("message").asText().contains("required"));
        verify(cms, never()).updateLabels(any(String.class), any(String[].class));
    }
}
