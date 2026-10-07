package com.coresecure.brightcove.wrapper.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.coresecure.brightcove.wrapper.objects.Account;
import com.coresecure.brightcove.wrapper.objects.Platform;
import com.coresecure.brightcove.wrapper.utils.HttpServices;
import com.coresecure.brightcove.wrapper.utils.HttpServices.PatchResponse;
import com.coresecure.brightcove.wrapper.utils.LoopbackHttps;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * BGS-1600: when the CMS PATCH behind {@code update_labels} failed, the servlet returned
 * {@code {}}, which the admin UI reads as success ("Labels saved" while nothing persisted).
 * Every failure now carries {@code error_code} and {@code message}.
 */
class CmsApiUpdateLabelsTest {

    private LoopbackHttps server;
    private final AtomicInteger patchStatus = new AtomicInteger(200);
    private final AtomicReference<String> patchBody = new AtomicReference<>("{\"id\":\"v1\",\"labels\":[\"a\"]}");

    @BeforeEach
    void setUp() throws Exception {
        server = new LoopbackHttps()
                .on("/oauth/access_token", ex -> LoopbackHttps.reply(ex, 200,
                        "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":300}"))
                .on("/cms", ex -> LoopbackHttps.reply(ex, patchStatus.get(), "application/json",
                        patchBody.get().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .start();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private CmsAPI cms(String apiBase) {
        Platform platform = new Platform(server.base() + "/oauth", apiBase, server.base() + "/di", server.base() + "/p");
        return new CmsAPI(new Account(platform, "id", "secret", "123"));
    }

    /** The PATCH to a TLS server only trusts the loopback certificate on the HttpURLConnection path (JDK 11). */
    private static void assumeHttpUrlConnectionPatch() {
        assumeTrue(Runtime.version().feature() < 17,
                "JDK 17+ routes PATCH through the Apache client, which does not use the test trust override");
    }

    @Test
    void refusedConnectionIsAn502ErrorNotAnEmptyObject() {
        ObjectNode r = cms("https://127.0.0.1:1/cms").updateLabels("v1", new String[] {"a"});

        assertEquals(502, r.path("error_code").asInt(), r.toString());
        assertTrue(r.path("message").asText().startsWith("No response from Brightcove"), r.toString());
    }

    @Test
    void validationArrayKeepsTheCmsMessage() {
        assumeHttpUrlConnectionPatch();
        patchStatus.set(422);
        patchBody.set("[{\"error_code\":\"VALIDATION_ERROR\",\"message\":\"labels: ILLEGAL_VALUE\"}]");

        ObjectNode r = cms(server.base() + "/cms").updateLabels("v1", new String[] {"a"});

        assertEquals("VALIDATION_ERROR", r.path("error_code").asText(), r.toString());
        assertEquals("labels: ILLEGAL_VALUE", r.path("message").asText());
        assertEquals(422, r.path("status").asInt());
    }

    @Test
    void serverErrorWithEmptyBodyCarriesTheStatus() {
        assumeHttpUrlConnectionPatch();
        patchStatus.set(503);
        patchBody.set("");

        ObjectNode r = cms(server.base() + "/cms").updateLabels("v1", new String[] {"a"});

        assertEquals(503, r.path("error_code").asInt(), r.toString());
    }

    @Test
    void successReturnsTheUpdatedVideoWithoutAnErrorCode() {
        assumeHttpUrlConnectionPatch();

        ObjectNode r = cms(server.base() + "/cms").updateLabels("v1", new String[] {"a"});

        assertFalse(r.has("error_code"), r.toString());
        assertEquals("v1", r.path("id").asText());
    }

    @Test
    void mapperCoversTheOtherShapes() throws Exception {
        // HTTP error, plain-object body with the CMS's own code and message
        ObjectNode obj = CmsAPI.toUpdateResult(new PatchResponse(404, "{\"error_code\":\"RESOURCE_NOT_FOUND\",\"message\":\"no\"}", null));
        assertEquals("RESOURCE_NOT_FOUND", obj.path("error_code").asText());
        assertEquals("no", obj.path("message").asText());

        // HTTP error, body that is not JSON (an HTML gateway page)
        ObjectNode html = CmsAPI.toUpdateResult(new PatchResponse(502, "<html>bad gateway</html>", null));
        assertEquals(502, html.path("error_code").asInt());

        // HTTP error, bare object body without a CMS code
        ObjectNode bare = CmsAPI.toUpdateResult(new PatchResponse(500, "{\"oops\":true}", null));
        assertEquals(500, bare.path("error_code").asInt());

        // no response and a local failure
        ObjectNode none = CmsAPI.toUpdateResult(new PatchResponse(0, null, "IOException: boom"));
        assertEquals(502, none.path("error_code").asInt());
        assertTrue(none.path("message").asText().contains("boom"));

        // success object
        ObjectNode ok = CmsAPI.toUpdateResult(new PatchResponse(200, "{\"id\":\"v1\"}", null));
        assertFalse(ok.has("error_code"));
    }

    @Test
    void executePatchFullReportsTheStatusAndTheFailure() {
        PatchResponse refused = HttpServices.executePatchFull("https://127.0.0.1:1/x", "{}", new java.util.HashMap<>());
        assertEquals(0, refused.status);
        assertTrue(refused.failure != null && !refused.failure.isEmpty());
    }
}
