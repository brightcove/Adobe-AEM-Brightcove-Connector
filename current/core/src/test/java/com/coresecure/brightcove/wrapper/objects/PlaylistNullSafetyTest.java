package com.coresecure.brightcove.wrapper.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.coresecure.brightcove.wrapper.utils.Constants;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * BCON-90 (6bf47c7): {@code Playlist.finishConstruction} called {@code
 * jsonObj.get(key).asText()} guarded only by {@code has()}, not {@code
 * isNull()}. Jackson's {@code NullNode.asText()} returns the four-character
 * string {@code "null"}, not Java {@code null} -- so a Media API response
 * that sets a field to explicit JSON null (as opposed to omitting it) put the
 * literal text "null" into the Java field. The fix added {@code
 * !jsonObj.get(key).isNull()} to every {@code asText()}/{@code asBoolean()}
 * call in the method.
 */
class PlaylistNullSafetyTest {

    @Test
    void missingOptionalFieldsLeaveGettersNull() throws Exception {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put(Constants.ID, "p1");
        // name, account_id, description, reference_id: all absent.

        Playlist playlist = new Playlist(json);

        assertEquals("p1", playlist.getId());
        assertNull(playlist.getName());
        assertNull(playlist.getAccountId());
        assertNull(playlist.getDescription());
        assertNull(playlist.getReferenceId());
    }

    /**
     * The discriminating case: explicit JSON null (not absence). Without the
     * {@code isNull()} guard this codebase's own fix commit records that the
     * result was the literal string {@code "null"}, so this assertion is a
     * true negative check, not merely "doesn't throw".
     */
    @Test
    void explicitJsonNullFieldsAreJavaNullNotTheStringNull() throws Exception {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put(Constants.ID, "p2");
        json.putNull(Constants.NAME);
        json.putNull(Constants.ACCOUNT_ID);
        json.putNull(Constants.DESCRIPTION);
        json.putNull(Constants.REFERENCE_ID);

        ArrayNode videoIds = JsonNodeFactory.instance.arrayNode();
        videoIds.add("v1");
        videoIds.add("v2");
        json.set(Constants.VIDEO_IDS, videoIds);

        Playlist playlist = new Playlist(json);

        assertEquals("p2", playlist.getId());
        assertNull(playlist.getName(), "explicit JSON null must not become the "
            + "string \"null\" (NullNode.asText() returns \"null\" unguarded)");
        assertNull(playlist.getAccountId());
        assertNull(playlist.getDescription());
        assertNull(playlist.getReferenceId());
        // video_ids parsing is unguarded by has()/isNull() in the same method
        // (it's an array, not a text field) and comes after the guarded
        // fields in source order -- confirms the guarded fields don't throw
        // and abort the rest of finishConstruction.
        assertEquals(Arrays.asList("v1", "v2"), playlist.getVideoIds());
    }

    @Test
    void stringConstructorAppliesTheSameGuards() throws Exception {
        String json = "{\"id\":\"p3\",\"name\":null,\"description\":null}";

        Playlist playlist = new Playlist(json);

        assertEquals("p3", playlist.getId());
        assertNull(playlist.getName());
        assertNull(playlist.getDescription());
    }
}
