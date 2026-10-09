package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.callables;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * BGS-1600 BugBot finding (a4b292e): {@code VideoImportCallable.getAsset}'s
 * JCR-SQL2 fallback query interpolated {@code videoId} straight into the
 * query string ({@code "...brc_id] = \"" + videoId + "\""}); an unexpected
 * character (quote, bracket) from an upstream API response could break out of
 * the string literal. The fix validates {@code videoId.matches("[A-Za-z0-9_-]+")}
 * before building the query and returns {@code null} instead if it doesn't.
 *
 * <p>{@code getAsset} is {@code private} and reads its {@code resourceResolver}
 * from an instance field set only inside {@code call()}, so there's no
 * public/package-private seam without changing main code. This uses
 * reflection (test-only) to invoke the private method and set the private
 * field directly -- no production code was touched.</p>
 *
 * <p>Discrimination: rather than asserting on the return value (both the
 * rejected and accepted paths can return {@code null} -- the accepted path
 * because the mocked JCR query blows up too, since building real JCR/Session
 * mocks all the way to a query result is out of scope here), this verifies
 * the {@code Session}'s interaction: a rejected videoId must never reach
 * {@code session.getWorkspace()}, while an accepted one must.</p>
 */
class VideoImportCallableGetAssetVideoIdGuardTest {

    private VideoImportCallable newCallableWithMockedResolver(ResourceResolver resourceResolver)
            throws Exception {
        ObjectNode innerObj = JsonNodeFactory.instance.objectNode();
        VideoImportCallable callable = new VideoImportCallable(innerObj,
                "/content/dam/brightcove_assets", "acct1", null, null, null);

        Field resolverField = VideoImportCallable.class.getDeclaredField("resourceResolver");
        resolverField.setAccessible(true);
        resolverField.set(callable, resourceResolver);

        return callable;
    }

    private Object invokeGetAsset(VideoImportCallable callable, String oldpath, String localpath,
            String videoId) throws Exception {
        Method getAsset = VideoImportCallable.class.getDeclaredMethod("getAsset", String.class,
                String.class, String.class);
        getAsset.setAccessible(true);
        return getAsset.invoke(callable, oldpath, localpath, videoId);
    }

    @Test
    void videoIdWithUnsafeCharactersIsRejectedBeforeTouchingTheQueryManager() throws Exception {
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        when(resourceResolver.getResource(anyString())).thenReturn(null);
        Session session = mock(Session.class);
        when(resourceResolver.adaptTo(Session.class)).thenReturn(session);

        VideoImportCallable callable = newCallableWithMockedResolver(resourceResolver);

        Object result = invokeGetAsset(callable, null, "/content/dam/brightcove_assets/acct1/x.mp4",
                "123\" OR \"1\"=\"1");

        assertNull(result);
        verify(session, never()).getWorkspace();
    }

    @Test
    void plainAlphanumericVideoIdPassesValidationAndReachesTheQueryManager() throws Exception {
        ResourceResolver resourceResolver = mock(ResourceResolver.class);
        when(resourceResolver.getResource(anyString())).thenReturn(null);
        Session session = mock(Session.class);
        when(resourceResolver.adaptTo(Session.class)).thenReturn(session);
        // getAsset's brc_id fallback wraps the whole query attempt in a
        // catch(Exception), so throwing here from getWorkspace() is enough to
        // prove the guard let a valid id through without needing a full JCR
        // QueryManager/QueryResult mock chain.
        when(session.getWorkspace()).thenThrow(new RuntimeException("marker: reached query step"));

        VideoImportCallable callable = newCallableWithMockedResolver(resourceResolver);

        Object result = invokeGetAsset(callable, null, "/content/dam/brightcove_assets/acct1/x.mp4",
                "123456789");

        assertNull(result);
        verify(session, times(1)).getWorkspace();
    }
}
