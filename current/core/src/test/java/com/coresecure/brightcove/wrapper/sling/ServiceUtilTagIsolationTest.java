package com.coresecure.brightcove.wrapper.sling;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.day.cq.tagging.TagManager;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Pins the CORRECT behaviour for open defect 3b item 8 (parity plan): a video
 * whose tag id makes the TagManager throw (on 6.5.0: "error while checking tag
 * creation permissions" for ids containing ';' or ':') must lose only that tag.
 * Today the exception escapes the per-tag loop, is swallowed by the outer
 * catch, and the video's whole tag list is dropped (and in the reported case
 * the import for that video aborted).
 *
 * Disabled until fixed; it was run enabled first and failed on the current
 * code (the valid tag was missing from the saved tags).
 */
class ServiceUtilTagIsolationTest {

    @Test
    @Disabled("3b item 8: a tag whose id breaks TagManager takes the video's other tags (import) down with it; remove this line when fixed")
    void badTagIsDroppedButValidTagsAreStillSaved() throws Exception {
        TagManager tagManager = mock(TagManager.class);
        when(tagManager.canCreateTag("good")).thenReturn(true);
        when(tagManager.canCreateTag(";:a sampletag:;:yeaah"))
                .thenThrow(new RuntimeException("error while checking tag creation permissions"));
        ResourceResolver resolver = mock(ResourceResolver.class);
        when(resolver.adaptTo(TagManager.class)).thenReturn(tagManager);

        ModifiableValueMap map = mock(ModifiableValueMap.class);
        final HashMap<String, Object> store = new HashMap<>();
        when(map.put(anyString(), org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        });

        ArrayNode tags = JsonNodeFactory.instance.arrayNode().add(";:a sampletag:;:yeaah").add("good");

        ServiceUtil serviceUtil = mock(ServiceUtil.class);
        Method m = ServiceUtil.class.getDeclaredMethod("setMapJSONArray", String.class,
                ArrayNode.class, ResourceResolver.class, ModifiableValueMap.class);
        m.setAccessible(true);
        m.invoke(serviceUtil, "cq:tags", tags, resolver, map);

        assertArrayEquals(new Object[] {"good"}, (Object[]) store.get("cq:tags"));
    }
}
