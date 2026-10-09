package com.coresecure.brightcove.wrapper.webservices;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.coresecure.brightcove.wrapper.BrightcoveAPI;
import com.coresecure.brightcove.wrapper.api.CmsAPI;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

/**
 * BGS-1600 BugBot finding (a4b292e): {@code BrcApi.uploadImage} passed the
 * user-supplied poster/thumbnail source URL straight to
 * {@code HttpServices.getSSLConnection(...).getInputStream()} inside a
 * {@code try (InputStream is = ...)}. A non-HTTPS URL both risked casting an
 * {@code HttpURLConnection} to {@code HttpsURLConnection}
 * (ClassCastException) and let the connector make an SSRF-style request to
 * whatever host the field named, over plain HTTP. The fix checks
 * {@code "https".equalsIgnoreCase(srcURL.getProtocol())} BEFORE calling
 * {@code getSSLConnection} at all and logs+skips instead.
 *
 * <p>{@code uploadImage} is {@code private} and reaches the guard only
 * through several private fields ({@code cs}, {@code brAPI}) and a real DAM
 * asset at the expected path, so this wires those up with reflection +
 * Mockito + an {@link AemContext} DAM fixture (same fixture style as
 * {@link BrcReplicationHandlerFolderSyncTest}) rather than changing
 * visibility in main code.</p>
 *
 * <p>Discrimination: this cannot assert on {@code asset.addRendition(...)}
 * having run, because the code path calls {@code getInputStream()} (an
 * actual connect attempt) before ever reaching {@code addRendition} -- true
 * on both the rejected and accepted paths once real sockets are involved.
 * Discrimination instead uses the guard's only observable side effect: the
 * {@code LOGGER.warn(".. must use HTTPS")} message, captured with
 * slf4j-test's {@code TestLoggerFactory} (already a declared test dependency,
 * previously unused). The negative test asserts the warning fires for both
 * poster and thumbnail; the positive test asserts it does NOT fire for an
 * https:// source (which instead fails later, at the network layer, once
 * past the guard -- using {@code https://127.0.0.1:1/...} so that failure is
 * an immediate local "connection refused" rather than a real network call or
 * DNS lookup).</p>
 */
@ExtendWith(AemContextExtension.class)
class BrcApiUploadImageHttpsGuardTest {

    private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

    private static final String ASSET_ROOT = "/content/dam/brightcove_assets";
    private static final String ACCOUNT_ID = "acct1";
    private static final String VIDEO_ID = "12345";
    private static final String ASSET_PATH = ASSET_ROOT + "/" + ACCOUNT_ID + "/" + VIDEO_ID + ".mp4";

    private TestLogger logger;

    @BeforeEach
    void setUp() throws Exception {
        TestLoggerFactory.clear();
        logger = TestLoggerFactory.getTestLogger(BrcApi.class);

        context.create().asset(ASSET_PATH, new ByteArrayInputStream(new byte[] {1, 2, 3, 4}),
                "video/mp4");
        context.resourceResolver().commit();
    }

    private BrcApi buildApiWithMockedDependencies() throws Exception {
        BrcApi api = new BrcApi();

        ConfigurationService cs = mock(ConfigurationService.class);
        when(cs.getAssetIntegrationPath()).thenReturn(ASSET_ROOT);
        setPrivateField(api, "cs", cs);

        // BrightcoveAPI's real constructor just wires up plain field objects
        // (Platform/Account/CmsAPI) -- no network I/O -- so it's safe to
        // construct directly and then swap its `cms` field for a mock.
        BrightcoveAPI brAPI = new BrightcoveAPI("client-id", "client-secret", ACCOUNT_ID, null);
        CmsAPI mockCms = mock(CmsAPI.class);
        when(mockCms.uploadInjest(anyString(), any(ObjectNode.class))).thenReturn(queuedIngestResponse());
        setPrivateField(brAPI, "cms", mockCms);
        setPrivateField(api, "brAPI", brAPI);

        return api;
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static ObjectNode queuedIngestResponse() {
        ObjectNode ingestResp = JsonNodeFactory.instance.objectNode();
        ingestResp.put(Constants.RESPONSE, "{\"id\":\"job-1\"}");
        return ingestResp;
    }

    private void invokeUploadImage(BrcApi api, MockSlingHttpServletRequest request) throws Exception {
        Method uploadImage = BrcApi.class.getDeclaredMethod("uploadImage",
                org.apache.sling.api.SlingHttpServletRequest.class);
        uploadImage.setAccessible(true);
        uploadImage.invoke(api, request);
    }

    private MockSlingHttpServletRequest requestWith(String posterSource, String thumbnailSource) {
        MockSlingHttpServletRequest request = context.request();
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.ID, VIDEO_ID);
        params.put(Constants.ACCOUNT_ID, ACCOUNT_ID);
        params.put(Constants.POSTER_SOURCE, posterSource);
        params.put(Constants.THUMBNAIL_SOURCE, thumbnailSource);
        request.setParameterMap(params);
        return request;
    }

    @Test
    void httpSourcesAreRejectedWithoutOpeningAConnection() throws Exception {
        BrcApi api = buildApiWithMockedDependencies();
        MockSlingHttpServletRequest request = requestWith(
                "http://evil.example/poster.png", "http://evil.example/thumb.png");

        invokeUploadImage(api, request);

        boolean posterWarned = logger.getLoggingEvents().stream().anyMatch(e ->
                e.getMessage().contains("must use HTTPS") && e.getMessage().toLowerCase().contains("poster"));
        boolean thumbnailWarned = logger.getLoggingEvents().stream().anyMatch(e ->
                e.getMessage().contains("must use HTTPS") && e.getMessage().toLowerCase().contains("thumbnail"));

        assertTrue(posterWarned, "expected the poster HTTPS guard to log a warning; got: "
                + logger.getLoggingEvents());
        assertTrue(thumbnailWarned, "expected the thumbnail HTTPS guard to log a warning; got: "
                + logger.getLoggingEvents());

        boolean anyConnectionAttempted = logger.getLoggingEvents().stream()
                .anyMatch(e -> e.getMessage().contains("Failed to update DAM rendition"));
        assertFalse(anyConnectionAttempted, "http:// sources must be rejected BEFORE any "
                + "connection is opened -- no network-layer failure should be logged at "
                + "all here; got: " + logger.getLoggingEvents());
    }

    @Test
    void httpsSourcesAreNotRejectedByTheGuard() throws Exception {
        BrcApi api = buildApiWithMockedDependencies();
        // Loopback, unassigned low port: getInputStream() gets an immediate
        // local "connection refused" with no DNS lookup and no external
        // network dependency, so this stays fast and deterministic while
        // still proving the guard let the URL past the scheme check.
        MockSlingHttpServletRequest request = requestWith(
                "https://127.0.0.1:1/poster.png", "https://127.0.0.1:1/thumb.png");

        invokeUploadImage(api, request);

        boolean guardFired = logger.getLoggingEvents().stream()
                .anyMatch(e -> e.getMessage().contains("must use HTTPS"));
        boolean reachedConnectionAttempt = logger.getLoggingEvents().stream()
                .anyMatch(e -> e.getMessage().contains("Failed to update DAM rendition"));

        assertFalse(guardFired, "an https:// source must not trip the HTTPS guard; got: "
                + logger.getLoggingEvents());
        assertTrue(reachedConnectionAttempt, "expected execution to reach the real "
                + "getInputStream() connection attempt (and fail there, on loopback "
                + "port 1) -- confirms the guard actually let the https:// URL "
                + "through rather than bailing out earlier for an unrelated reason; got: "
                + logger.getLoggingEvents());
    }
}
