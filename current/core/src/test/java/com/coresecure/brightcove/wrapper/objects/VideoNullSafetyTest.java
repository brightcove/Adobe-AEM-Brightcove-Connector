package com.coresecure.brightcove.wrapper.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.coresecure.brightcove.wrapper.utils.Constants;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * BCON-90: {@code Video(ObjectNode)} lost {@code has()/isNull()} guards for the
 * projection/geo/schedule/link/economics/images fields during the Jackson
 * migration, so a Media API response that explicitly sets one of them to JSON
 * {@code null} (rather than omitting the key) threw a {@code ClassCastException}
 * casting {@code NullNode} to {@code ObjectNode} (9a1bda7 restored the guards).
 *
 * <p>The constructor wraps its entire field-parsing block in one
 * {@code try { ... } catch (Exception e) { log }}, so without the per-field
 * guard the exception is swallowed too -- but everything parsed AFTER the
 * failing field in source order (tags, labels) silently comes back {@code
 * null} along with it. That's the sharper, more useful regression to pin:
 * a thin/absent-optional-field payload must not blank out fields that come
 * later in the same parse.</p>
 */
class VideoNullSafetyTest {

    @Test
    void allOptionalFieldsAbsentConstructsWithoutException() throws Exception {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put(Constants.ID, "v1");
        json.put(Constants.NAME, "sample");
        // geo, schedule, link, economics, images, projection, tags, labels,
        // text_tracks, variants, account_id, reference_id, description,
        // long_description, state, folder_id, complete: all absent.

        Video video = new Video(json);

        assertEquals("v1", video.id);
        assertEquals("sample", video.name);
        assertNull(video.geo);
        assertNull(video.schedule);
        assertNull(video.link);
        assertNull(video.economics);
        assertNull(video.images);
        assertNull(video.tags);
        assertNull(video.labels);
        assertNull(video.account_id);
        assertNull(video.reference_id);
        assertNull(video.description);
    }

    /**
     * The discriminating case: geo/schedule/link/economics/images are explicit
     * JSON {@code null} (not merely absent) -- the exact shape that produced a
     * ClassCastException pre-fix -- while tags and labels, which are parsed
     * AFTER those fields in {@code Video(ObjectNode)}'s source order, carry
     * real data. If any one of the earlier guards is missing, the resulting
     * exception is caught by the constructor's outer catch block and tags/
     * labels come back null too, because the shared try block never reaches
     * them. Passing this requires every earlier guard to hold, not just the
     * one under test.
     */
    @Test
    void explicitJsonNullOnNestedFieldsDoesNotBlankOutLaterFields() throws Exception {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put(Constants.ID, "v2");
        json.putNull(Constants.PROJECTION);
        json.putNull(Constants.GEO);
        json.putNull(Constants.SCHEDULE);
        json.putNull(Constants.LINK);
        json.putNull(Constants.ECONOMICS);
        json.putNull(Constants.IMAGES);

        ArrayNode tags = JsonNodeFactory.instance.arrayNode();
        tags.add("news");
        tags.add("weather");
        json.set(Constants.TAGS, tags);

        ArrayNode labels = JsonNodeFactory.instance.arrayNode();
        labels.add("featured");
        json.set(Constants.LABELS, labels);

        Video video = new Video(json);

        assertEquals("v2", video.id, "a field parsed before the null-guarded "
            + "block must still come through");
        assertNull(video.projection, "Projection ctor guard");
        assertNull(video.geo, "Geo cast guard");
        assertNull(video.schedule, "Schedule cast guard");
        assertNull(video.link, "RelatedLink cast guard");
        assertNull(video.economics, "EconomicsEnum.valueOf guard");
        assertNull(video.images, "Images cast guard");

        assertNotNull(video.tags, "tags is parsed AFTER geo/schedule/link/"
            + "economics/images in Video(ObjectNode) -- if any of those guards "
            + "were missing, the ClassCastException they'd throw is caught by "
            + "the constructor's shared catch block and tags never gets set");
        assertEquals(Arrays.asList("news", "weather"), video.tags);
        assertEquals(Arrays.asList("featured"), video.labels);
    }
}
