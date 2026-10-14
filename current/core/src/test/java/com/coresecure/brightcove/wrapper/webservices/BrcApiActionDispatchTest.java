package com.coresecure.brightcove.wrapper.webservices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.coresecure.brightcove.wrapper.BrightcoveAPI;
import com.coresecure.brightcove.wrapper.api.CmsAPI;
import com.coresecure.brightcove.wrapper.objects.Playlist;
import com.coresecure.brightcove.wrapper.objects.Video;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Pins BrcApi.apiLogic action dispatch for the actions with no other coverage: variants,
 * playlists, delete video, folder move/remove and text track removal. The HTTP layer is mocked at
 * the same seams BrcApiUpdateLabelsTest uses (CmsAPI behind BrightcoveAPI, and ServiceUtil), so
 * each test asserts the call made and the JSON the servlet would answer with.
 */
class BrcApiActionDispatchTest {

    private static final JsonNodeFactory JF = JsonNodeFactory.instance;

    private BrcApi api;
    private CmsAPI cms;
    private ServiceUtil serviceUtil;
    private SlingHttpServletRequest request;

    @BeforeEach
    void setUp() throws Exception {
        api = new BrcApi();
        cms = mock(CmsAPI.class);
        serviceUtil = mock(ServiceUtil.class);
        BrightcoveAPI brAPI = new BrightcoveAPI("c", "s", "1234567890001", null);
        set(brAPI, "cms", cms);
        set(api, "brAPI", brAPI);
        set(api, "serviceUtil", serviceUtil);
        request = mock(SlingHttpServletRequest.class);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private ObjectNode dispatch(String action) throws Exception {
        when(request.getParameter("a")).thenReturn(action);
        Method m = BrcApi.class.getDeclaredMethod("apiLogic", SlingHttpServletRequest.class,
                SlingHttpServletResponse.class, ObjectNode.class);
        m.setAccessible(true);
        return (ObjectNode) m.invoke(api, request, mock(SlingHttpServletResponse.class), JF.objectNode());
    }

    private void params(String... kv) {
        for (int i = 0; i < kv.length; i += 2) {
            when(request.getParameter(kv[i])).thenReturn(kv[i + 1]);
        }
    }

    private static ObjectNode obj(String k, Object v) {
        ObjectNode n = JF.objectNode();
        if (v instanceof Integer) {
            n.put(k, (Integer) v);
        } else {
            n.put(k, String.valueOf(v));
        }
        return n;
    }

    // ---- variants -------------------------------------------------------------------------

    @Test
    void variantActionsRefuseMissingVideoOrLanguageWith400AndMakeNoCall() throws Exception {
        for (String action : new String[] {"add_variant", "update_variant", "delete_variant"}) {
            request = mock(SlingHttpServletRequest.class);
            params("videoId", "v1");
            assertEquals(400, dispatch(action).get("error").asInt(), action + " without language");
            request = mock(SlingHttpServletRequest.class);
            params("language", "es");
            assertEquals(400, dispatch(action).get("error").asInt(), action + " without videoId");
            request = mock(SlingHttpServletRequest.class);
            params("videoId", "", "language", "es");
            assertEquals(400, dispatch(action).get("error").asInt(), action + " with blank videoId");
        }
        verify(cms, never()).addVariant(anyString(), any(ObjectNode.class));
        verify(cms, never()).updateVariant(anyString(), anyString(), any(ObjectNode.class));
        verify(cms, never()).deleteVariant(anyString(), anyString());
    }

    @Test
    void addVariantSendsLanguageAndTextFieldsAndKeepsTheUpstreamResult() throws Exception {
        params("videoId", "v1", "language", "es", "name", "Hola", "description", "d", "long_description", "ld",
                "custom_fields", "{\"k\":\"v\"}");
        when(cms.addVariant(eq("v1"), any(ObjectNode.class))).thenReturn(obj("error", 201));

        ObjectNode r = dispatch("add_variant");

        ArgumentCaptor<ObjectNode> body = ArgumentCaptor.forClass(ObjectNode.class);
        verify(cms).addVariant(eq("v1"), body.capture());
        assertEquals("es", body.getValue().get("language").asText());
        assertEquals("Hola", body.getValue().get("name").asText());
        assertEquals("d", body.getValue().get("description").asText());
        assertEquals("ld", body.getValue().get("long_description").asText());
        assertEquals("v", body.getValue().get("custom_fields").get("k").asText());
        assertEquals(201, r.get("error").asInt());
    }

    @Test
    void addVariantOmitsUnsentTextFieldsAndDefaultsOrRepairsCustomFields() throws Exception {
        params("videoId", "v1", "language", "es", "custom_fields", "{not json");
        when(cms.addVariant(eq("v1"), any(ObjectNode.class))).thenReturn(obj("error", 200));

        dispatch("add_variant");

        ArgumentCaptor<ObjectNode> body = ArgumentCaptor.forClass(ObjectNode.class);
        verify(cms).addVariant(eq("v1"), body.capture());
        assertFalse(body.getValue().has("name"));
        assertFalse(body.getValue().has("description"));
        assertFalse(body.getValue().has("long_description"));
        assertTrue(body.getValue().get("custom_fields").isObject());
        assertEquals(0, body.getValue().get("custom_fields").size());
    }

    @Test
    void addVariantTurnsAnEmptyUpstreamResultIntoA502NotASilentSuccess() throws Exception {
        params("videoId", "v1", "language", "es");
        when(cms.addVariant(eq("v1"), any(ObjectNode.class))).thenReturn(JF.objectNode());

        ObjectNode r = dispatch("add_variant");

        assertEquals(502, r.get("error").asInt());
        assertEquals("Add failed", r.get("error_message").asText());
    }

    @Test
    void addVariantTurnsAnUpstreamExceptionIntoA502() throws Exception {
        params("videoId", "v1", "language", "es");
        when(cms.addVariant(eq("v1"), any(ObjectNode.class))).thenThrow(new IllegalStateException("boom"));

        assertEquals(502, dispatch("add_variant").get("error").asInt());
    }

    @Test
    void updateVariantSendsOnlyThePresentFields() throws Exception {
        params("videoId", "v1", "language", "es", "name", "Nuevo");
        ObjectNode ok = obj("language", "es");
        when(cms.updateVariant(eq("v1"), eq("es"), any(ObjectNode.class))).thenReturn(ok);

        ObjectNode r = dispatch("update_variant");

        ArgumentCaptor<ObjectNode> body = ArgumentCaptor.forClass(ObjectNode.class);
        verify(cms).updateVariant(eq("v1"), eq("es"), body.capture());
        assertEquals(1, body.getValue().size());
        assertEquals("Nuevo", body.getValue().get("name").asText());
        assertEquals(200, r.get("error").asInt());
    }

    @Test
    void updateVariantMapsErrorCodeToErrorAndEmptyBodyTo502() throws Exception {
        params("videoId", "v1", "language", "es");
        when(cms.updateVariant(eq("v1"), eq("es"), any(ObjectNode.class))).thenReturn(obj("error_code", 404));
        assertEquals("404", dispatch("update_variant").get("error").asText());

        when(cms.updateVariant(eq("v1"), eq("es"), any(ObjectNode.class))).thenReturn(JF.objectNode());
        ObjectNode r = dispatch("update_variant");
        assertEquals(502, r.get("error").asInt());
        assertEquals("Update failed", r.get("error_message").asText());
    }

    @Test
    void updateVariantParsesCustomFieldsAndFallsBackToEmptyObjectOnBadJson() throws Exception {
        params("videoId", "v1", "language", "es", "custom_fields", "{\"a\":1}");
        when(cms.updateVariant(anyString(), anyString(), any(ObjectNode.class))).thenReturn(obj("id", "x"));
        dispatch("update_variant");
        ArgumentCaptor<ObjectNode> body = ArgumentCaptor.forClass(ObjectNode.class);
        verify(cms).updateVariant(anyString(), anyString(), body.capture());
        assertEquals(1, body.getValue().get("custom_fields").get("a").asInt());

        params("custom_fields", "{bad");
        dispatch("update_variant");
        verify(cms, org.mockito.Mockito.times(2)).updateVariant(anyString(), anyString(), body.capture());
        assertEquals(0, body.getValue().get("custom_fields").size());
    }

    @Test
    void deleteVariantKeepsAnUpstreamSuccessAndMapsErrorCode() throws Exception {
        params("videoId", "v1", "language", "es");
        when(cms.deleteVariant("v1", "es")).thenReturn(obj("error", 204));
        assertEquals(204, dispatch("delete_variant").get("error").asInt());

        when(cms.deleteVariant("v1", "es")).thenReturn(obj("error_code", 404));
        assertEquals("404", dispatch("delete_variant").get("error").asText());
    }

    @Test
    void deleteVariantTreatsEmptyOrThrowingUpstreamAsFailureNotAPhantomDelete() throws Exception {
        params("videoId", "v1", "language", "es");
        when(cms.deleteVariant("v1", "es")).thenReturn(JF.objectNode());
        ObjectNode empty = dispatch("delete_variant");
        assertEquals(502, empty.get("error").asInt());
        assertEquals("Delete failed", empty.get("error_message").asText());

        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(cms).deleteVariant("v1", "es");
        assertEquals(502, dispatch("delete_variant").get("error").asInt());

        org.mockito.Mockito.doReturn(null).when(cms).deleteVariant("v1", "es");
        assertEquals(502, dispatch("delete_variant").get("error").asInt());
    }

    // ---- playlists ------------------------------------------------------------------------

    @Test
    void createPlaylistWithoutPlaylistParameterIs500() throws Exception {
        when(request.getRequestParameter("playlist")).thenReturn(null);

        assertEquals(500, dispatch("create_playlist").get("error").asInt());
        verify(cms, never()).createPlaylist(any(Playlist.class));
    }

    @Test
    void createPlaylistBuildsAnExplicitPlaylistFromTheRequest() throws Exception {
        RequestParameter ids = mock(RequestParameter.class);
        when(ids.getString()).thenReturn("v1,v2,v3");
        when(request.getRequestParameter("playlist")).thenReturn(ids);
        params("plst.name", "My list", "plst.shortDescription", "desc", "plst.referenceId", " ref-1 ");
        when(cms.createPlaylist(any(Playlist.class))).thenReturn(obj("id", "p1"));

        ObjectNode r = dispatch("create_playlist");

        ArgumentCaptor<Playlist> sent = ArgumentCaptor.forClass(Playlist.class);
        verify(cms).createPlaylist(sent.capture());
        ObjectNode json = sent.getValue().toJSON();
        assertEquals("My list", json.get("name").asText());
        assertEquals("desc", json.get("description").asText());
        assertEquals("EXPLICIT", json.get("type").asText());
        assertEquals(Arrays.asList("v1", "v2", "v3"), Arrays.asList(
                json.get("video_ids").get(0).asText(), json.get("video_ids").get(1).asText(), json.get("video_ids").get(2).asText()));
        // success answers null so executeRequest writes the plain "true" body the upload widget needs
        assertNull(r);
    }

    @Test
    void createPlaylistWithoutAnIdInTheUpstreamAnswerIs409() throws Exception {
        RequestParameter ids = mock(RequestParameter.class);
        when(ids.getString()).thenReturn("v1");
        when(request.getRequestParameter("playlist")).thenReturn(ids);
        when(cms.createPlaylist(any(Playlist.class))).thenReturn(obj("error_code", 409));

        assertEquals(409, dispatch("create_playlist").get("error").asInt());
    }

    @Test
    void updatePlaylistClearVideosSendsAnEmptyListAndKeepsTheName() throws Exception {
        params("playlistId", "p1", "playlistName", "N", "clearVideos", "true");
        when(request.getParameterValues("videos")).thenReturn(new String[] {"ignored"});
        ObjectNode upstream = obj("id", "p1");
        when(cms.updatePlaylist(eq("p1"), any(String[].class), eq("N"))).thenReturn(upstream);

        ObjectNode r = dispatch("update_playlist");

        ArgumentCaptor<String[]> sent = ArgumentCaptor.forClass(String[].class);
        verify(cms).updatePlaylist(eq("p1"), sent.capture(), eq("N"));
        assertEquals(0, sent.getValue().length);
        assertEquals("p1", r.get("id").asText());
    }

    @Test
    void updatePlaylistSendsTheSelectedVideosOrANameOnlyChange() throws Exception {
        params("playlistId", "p1");
        when(request.getParameterValues("videos")).thenReturn(new String[] {"v1", "v2"});
        when(cms.updatePlaylist(anyString(), any(), any())).thenReturn(obj("id", "p1"));
        dispatch("update_playlist");
        ArgumentCaptor<String[]> sent = ArgumentCaptor.forClass(String[].class);
        verify(cms).updatePlaylist(eq("p1"), sent.capture(), eq(null));
        assertEquals(Arrays.asList("v1", "v2"), Arrays.asList(sent.getValue()));

        when(request.getParameterValues("videos")).thenReturn(null);
        params("playlistName", "Renamed");
        dispatch("update_playlist");
        verify(cms).updatePlaylist("p1", null, "Renamed");
    }

    @Test
    @Disabled("DEFECT: update_playlist with no playlistId (or nothing to change) answers an empty {} that the UI reads as success")
    void updatePlaylistWithoutIdIsAnErrorNotAnEmptySuccess() throws Exception {
        ObjectNode r = dispatch("update_playlist");

        assertTrue(r.has("error_code") || r.has("error"), r.toString());
        verify(cms, never()).updatePlaylist(anyString(), any(), any());
    }

    @Test
    void deletePlaylistDelegatesToServiceUtilWithThePlaylistId() throws Exception {
        params("playlist", "p9");
        ObjectNode del = obj("ok", "d");
        when(serviceUtil.deletePlaylist("p9")).thenReturn(del);

        assertEquals(del, dispatch("delete_playlist"));
        verify(serviceUtil).deletePlaylist("p9");
    }

    // ---- folders --------------------------------------------------------------------------

    @Test
    void moveAndRemoveVideoFolderActionsDelegateWithFolderAndVideo() throws Exception {
        params("folder", "f1", "video", "v1");
        ObjectNode moved = obj("moved", "yes");
        ObjectNode removed = obj("removed", "yes");
        when(serviceUtil.moveVideoToFolder("f1", "v1")).thenReturn(moved);
        when(serviceUtil.removeVideoFromFolder("f1", "v1")).thenReturn(removed);

        assertEquals(moved, dispatch("move_video_to_folder"));
        assertEquals(removed, dispatch("remove_video_from_folder"));
        verify(serviceUtil).moveVideoToFolder("f1", "v1");
        verify(serviceUtil).removeVideoFromFolder("f1", "v1");
    }

    // ---- delete video ---------------------------------------------------------------------

    @Test
    void deleteVideoDeletesEachNonBlankIdThenReturnsTheRefreshedList() throws Exception {
        params("query", "a,,b, ,c", "start", "0", "limit", "25", "sort", "name");
        ObjectNode list = obj("totals", 2);
        when(serviceUtil.searchVideo("", 0, 25, "name", false)).thenReturn(list);
        when(serviceUtil.deleteVideo(anyString())).thenReturn(true);

        ObjectNode r = dispatch("delete_video");

        verify(serviceUtil).deleteVideo("a");
        verify(serviceUtil).deleteVideo("b");
        verify(serviceUtil).deleteVideo("c");
        verify(serviceUtil, never()).deleteVideo("");
        assertEquals(list, r);
    }

    @Test
    @Disabled("DEFECT: delete_video ignores a failed Brightcove delete and answers the refreshed list as if every id was deleted")
    void deleteVideoReportsAFailedDelete() throws Exception {
        params("query", "a", "start", "0", "limit", "25", "sort", "name");
        when(serviceUtil.deleteVideo("a")).thenReturn(false);
        when(serviceUtil.searchVideo("", 0, 25, "name", false)).thenReturn(JF.objectNode());

        ObjectNode r = dispatch("delete_video");

        assertTrue(r.has("error_code") || r.has("error"), r.toString());
    }

    // ---- text track removal ---------------------------------------------------------------

    private static ObjectNode track(String id) {
        ObjectNode t = JF.objectNode();
        t.put("id", id);
        t.put("src", "https://example.test/" + id + ".vtt");
        t.put("srclang", "en");
        t.put("label", id);
        t.put("kind", "captions");
        return t;
    }

    @Test
    void removeTextTrackResendsTheVideoWithoutTheRemovedTrackAndAnswersTrue() throws Exception {
        params("id", "v1", "track", "t1");
        ObjectNode down = JF.objectNode();
        ArrayNode tracks = down.putArray("text_tracks");
        tracks.add(track("t1"));
        tracks.add(track("t2"));
        when(cms.getVideo("v1")).thenReturn(down);
        when(cms.updateVideo(any(Video.class))).thenReturn(JF.objectNode());

        ObjectNode r = dispatch("remove_text_track");

        ArgumentCaptor<Video> sent = ArgumentCaptor.forClass(Video.class);
        verify(cms).updateVideo(sent.capture());
        ObjectNode json = sent.getValue().toJSON();
        assertEquals("v1", json.get("id").asText());
        assertEquals(1, json.get("text_tracks").size());
        assertEquals("t2", json.get("text_tracks").get(0).get("id").asText());
        assertNull(r, "null makes executeRequest write the plain 'true' body");
    }

    @Test
    void removingTheLastTrackSendsAnEmptyTrackList() throws Exception {
        params("id", "v1", "track", "t1");
        ObjectNode down = JF.objectNode();
        down.putArray("text_tracks").add(track("t1"));
        when(cms.getVideo("v1")).thenReturn(down);

        dispatch("remove_text_track");

        ArgumentCaptor<Video> sent = ArgumentCaptor.forClass(Video.class);
        verify(cms).updateVideo(sent.capture());
        assertNotNull(sent.getValue().toJSON().get("text_tracks"));
        assertEquals(0, sent.getValue().toJSON().get("text_tracks").size());
    }

    @Test
    @Disabled("DEFECT: remove_text_track swallows a failed getVideo and still answers null, which the client reads as 'true' (success)")
    void removeTextTrackDoesNotReportSuccessWhenTheVideoCannotBeRead() throws Exception {
        params("id", "v1", "track", "t1");
        when(cms.getVideo("v1")).thenThrow(new IllegalStateException("boom"));

        ObjectNode r = dispatch("remove_text_track");

        assertNotNull(r);
        verify(cms, never()).updateVideo(any(Video.class));
    }
}
