package com.coresecure.brightcove.wrapper.sling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * BCON-90 (57cd231): {@code ServiceUtil.searchVideo(String,int,int,String,boolean)}
 * used to do {@code (ObjectNode) MAPPER.readTree(result)} unconditionally.
 * {@code MAPPER.readTree("")} returns a Jackson {@code MissingNode}, not an
 * {@code ObjectNode}, so an empty upstream response (e.g. an auth failure that
 * short-circuits {@code getList} before any HTTP body comes back) threw a
 * {@code ClassCastException} instead of surfacing an empty result. The fix
 * checks {@code parsed.isObject()} before casting and falls back to an empty
 * {@code ObjectNode}.
 *
 * <p>Mocking strategy follows {@link ServiceUtilDamOnlyDefaultTest}: a Mockito
 * partial mock runs the real {@code searchVideo} method while {@code getList}
 * (which would otherwise make a live Brightcove CMS call) is stubbed.</p>
 */
class ServiceUtilSearchVideoMissingNodeGuardTest {

    private ServiceUtil partialMockReturning(String upstreamBody) {
        ServiceUtil serviceUtil = mock(ServiceUtil.class);
        when(serviceUtil.searchVideo(anyString(), anyInt(), anyInt(), anyString(), anyBoolean()))
                .thenCallRealMethod();
        when(serviceUtil.getList(any(), anyInt(), anyInt(), anyBoolean(), anyString(), anyString(),
                anyBoolean())).thenReturn(upstreamBody);
        return serviceUtil;
    }

    /**
     * The thin case: an empty upstream body. {@code MAPPER.readTree("")} does
     * not throw -- it returns {@code MissingNode}, which is exactly what the
     * {@code isObject()} guard exists to catch. Pre-fix this threw a
     * ClassCastException out of searchVideo instead of returning here.
     */
    @Test
    void emptyUpstreamResponseReturnsEmptyObjectInsteadOfThrowing() {
        ServiceUtil serviceUtil = partialMockReturning("");

        ObjectNode result = serviceUtil.searchVideo("some query", 0, 10, "name", false);

        assertNotNull(result);
        assertTrue(result.isObject());
        assertEquals(0, result.size());
    }

    /**
     * A malformed (non-empty, non-JSON) body takes the catch(Exception) path
     * inside searchVideo rather than the isObject() guard, but must land on
     * the same safe empty-object result rather than propagating.
     */
    @Test
    void malformedUpstreamResponseReturnsEmptyObjectInsteadOfThrowing() {
        ServiceUtil serviceUtil = partialMockReturning("not json at all {{{");

        ObjectNode result = serviceUtil.searchVideo("some query", 0, 10, "name", false);

        assertNotNull(result);
        assertTrue(result.isObject());
        assertEquals(0, result.size());
    }

    /**
     * The positive case: a well-formed object response must still come
     * through as-is, proving the guard doesn't discard good data.
     */
    @Test
    void validJsonObjectResponseIsReturnedUnchanged() {
        ServiceUtil serviceUtil = partialMockReturning("{\"total_count\":5,\"items\":[]}");

        ObjectNode result = serviceUtil.searchVideo("some query", 0, 10, "name", false);

        assertNotNull(result);
        assertTrue(result.isObject());
        assertEquals(5, result.get("total_count").asLong());
    }
}
