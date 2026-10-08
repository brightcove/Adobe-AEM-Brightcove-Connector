package com.coresecure.brightcove.wrapper.webservices;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.mockito.ArgumentCaptor;
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
    void missingVideoIdIs400NotAnEmptySuccess() throws Exception {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getParameterValues("labels")).thenReturn(new String[] {"a"});
        CmsAPI cms = mock(CmsAPI.class);

        ObjectNode r = call(request, cms);

        assertEquals(400, r.get("error_code").asInt());
        assertTrue(r.get("message").asText().contains("required"));
        verify(cms, never()).updateLabels(any(String.class), any(String[].class));
    }

    /**
     * Row 13 / §3b-1: removing a video's last label. jQuery's $.param drops the empty array, so
     * the request arrives with no labels parameter; that must clear the labels, not be refused
     * (BGS-1600 made it a 400) or ignored (before BGS-1600, a silent "Labels saved").
     */
    @Test
    void noLabelsParameterClearsTheLabels() throws Exception {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getParameter("videoId")).thenReturn("v1");
        CmsAPI cms = mock(CmsAPI.class);
        ObjectNode cleared = JsonNodeFactory.instance.objectNode();
        cleared.put("id", "v1");
        cleared.putArray("labels");
        when(cms.updateLabels(eq("v1"), any(String[].class))).thenReturn(cleared);

        ObjectNode r = call(request, cms);

        ArgumentCaptor<String[]> sent = ArgumentCaptor.forClass(String[].class);
        verify(cms).updateLabels(eq("v1"), sent.capture());
        assertArrayEquals(new String[0], sent.getValue());
        assertTrue(!r.has("error_code"), r.toString());
    }

    @Test
    void blankLabelValuesAreDroppedNotSentAsALabel() throws Exception {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getParameter("videoId")).thenReturn("v1");
        when(request.getParameter("labels")).thenReturn("");
        when(request.getParameterValues("labels")).thenReturn(new String[] {"", "/a/", " "});
        CmsAPI cms = mock(CmsAPI.class);
        when(cms.updateLabels(eq("v1"), any(String[].class))).thenReturn(JsonNodeFactory.instance.objectNode());

        call(request, cms);

        ArgumentCaptor<String[]> sent = ArgumentCaptor.forClass(String[].class);
        verify(cms).updateLabels(eq("v1"), sent.capture());
        assertArrayEquals(new String[] {"/a/"}, sent.getValue());
    }

    /** L3: an unknown action said only {"error":404}; it now says why. */
    @Test
    void unknownActionIs404WithAMessage() throws Exception {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getParameter("a")).thenReturn("update_video");
        BrcApi api = new BrcApi();
        Method m = BrcApi.class.getDeclaredMethod("apiLogic", SlingHttpServletRequest.class,
                SlingHttpServletResponse.class, ObjectNode.class);
        m.setAccessible(true);

        ObjectNode r = (ObjectNode) m.invoke(api, request, mock(SlingHttpServletResponse.class),
                JsonNodeFactory.instance.objectNode());

        assertEquals(404, r.get("error").asInt());
        assertTrue(r.path("message").asText().length() > 0, r.toString());
    }
}
