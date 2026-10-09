package com.coresecure.brightcove.wrapper.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
import com.sun.net.httpserver.HttpServer;

/**
 * BGS-1600: when the CMS PATCH behind {@code update_labels} failed, the servlet returned
 * {@code {}}, which the admin UI reads as success ("Labels saved" while nothing persisted).
 * Every failure now carries {@code error_code} and {@code message}.
 *
 * <p>The CMS is a plain-HTTP loopback server so the PATCH cases run on every JVM: on 11 PATCH goes
 * through HttpURLConnection, on 17+ through the Apache client, which does not use the HTTPS
 * trust override. OAuth stays on the HTTPS loopback.</p>
 */
class CmsApiUpdateLabelsTest {

    private LoopbackHttps server;
    private HttpServer cmsServer;
    private final AtomicReference<String> patchMethod = new AtomicReference<>();
    private final AtomicReference<String> patchRequestBody = new AtomicReference<>();
    private final AtomicInteger patchStatus = new AtomicInteger(200);
    private final AtomicReference<String> patchBody = new AtomicReference<>("{\"id\":\"v1\",\"labels\":[\"a\"]}");

    @BeforeEach
    void setUp() throws Exception {
        server = new LoopbackHttps()
                .on("/oauth/access_token", ex -> LoopbackHttps.reply(ex, 200,
                        "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":300}"))
                .start();
        cmsServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        cmsServer.createContext("/cms", ex -> {
            patchMethod.set(ex.getRequestMethod());
            patchRequestBody.set(new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8));
            byte[] body = patchBody.get().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(patchStatus.get(), body.length == 0 ? -1 : body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        cmsServer.start();
    }

    @AfterEach
    void tearDown() {
        cmsServer.stop(0);
        server.close();
    }

    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    private String plainCms() {
        return "http://127.0.0.1:" + cmsServer.getAddress().getPort() + "/cms";
    }

    private CmsAPI cms(String apiBase) {
        Platform platform = new Platform(server.base() + "/oauth", apiBase, server.base() + "/di", server.base() + "/p");
        return new CmsAPI(new Account(platform, "id", "secret", "123"));
    }

    @Test
    void refusedConnectionIsAn502ErrorNotAnEmptyObject() {
        ObjectNode r = cms("https://127.0.0.1:1/cms").updateLabels("v1", new String[] {"a"});

        assertEquals(502, r.path("error_code").asInt(), r.toString());
        assertEquals("No response from Brightcove", r.path("message").asText(), r.toString());
    }

    /** Row 13: an empty label list is sent as an explicit empty array, which clears them. */
    @Test
    void anEmptyLabelListPatchesAnEmptyArray() throws Exception {
        patchBody.set("{\"id\":\"v1\",\"labels\":[]}");

        ObjectNode r = cms(plainCms()).updateLabels("v1", new String[0]);

        assertEquals("PATCH", patchMethod.get());
        assertEquals("[]", com.coresecure.brightcove.wrapper.utils.JsonReader.readJsonTree(patchRequestBody.get())
                .path("labels").toString(), patchRequestBody.get());
        assertFalse(r.has("error_code"), r.toString());
    }

    @Test
    void validationArrayKeepsTheCmsMessage() {
        patchStatus.set(422);
        patchBody.set("[{\"error_code\":\"VALIDATION_ERROR\",\"message\":\"labels: ILLEGAL_VALUE\"}]");

        ObjectNode r = cms(plainCms()).updateLabels("v1", new String[] {"a"});

        assertEquals("VALIDATION_ERROR", r.path("error_code").asText(), r.toString());
        assertEquals("labels: ILLEGAL_VALUE", r.path("message").asText());
        assertEquals(422, r.path("status").asInt());
    }

    @Test
    void serverErrorWithEmptyBodyCarriesTheStatus() {
        patchStatus.set(503);
        patchBody.set("");

        ObjectNode r = cms(plainCms()).updateLabels("v1", new String[] {"a"});

        assertEquals(503, r.path("error_code").asInt(), r.toString());
    }

    @Test
    void successReturnsTheUpdatedVideoWithoutAnErrorCode() {

        ObjectNode r = cms(plainCms()).updateLabels("v1", new String[] {"a"});

        assertFalse(r.has("error_code"), r.toString());
        assertEquals("v1", r.path("id").asText());
        assertEquals("PATCH", patchMethod.get(), "the CMS must have received a PATCH");
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

        // no response and a local failure: the failure text (may name the proxy) stays in the log
        ObjectNode none = CmsAPI.toUpdateResult(new PatchResponse(0, null, "ConnectException: proxy.internal:3128 boom"));
        assertEquals(502, none.path("error_code").asInt());
        assertFalse(none.path("message").asText().contains("boom"), none.toString());
        assertFalse(none.path("message").asText().contains("3128"), none.toString());

        // success object
        ObjectNode ok = CmsAPI.toUpdateResult(new PatchResponse(200, "{\"id\":\"v1\"}", null));
        assertFalse(ok.has("error_code"));
    }

    /** L2: nothing that is not a video object may come back as {} (the UI reads {} as saved). */
    @Test
    void anythingThatIsNotAVideoObjectIsAnExplicitError() throws Exception {
        PatchResponse[] notAVideo = {
            new PatchResponse(204, "", null),
            new PatchResponse(200, null, null),
            new PatchResponse(302, "", null),
            new PatchResponse(302, "{\"id\":\"v1\"}", null),
            new PatchResponse(200, "[]", null),
            new PatchResponse(200, "\"ok\"", null),
            new PatchResponse(200, "42", null),
            new PatchResponse(200, "[1,2]", null),
        };
        for (PatchResponse p : notAVideo) {
            ObjectNode r = CmsAPI.toUpdateResult(p);
            String shape = "HTTP " + p.status + " body " + p.body + " -> " + r;
            assertEquals(502, r.path("error_code").asInt(), shape);
            assertTrue(r.path("message").asText().startsWith("Unexpected response"), shape);
        }
    }

    @Test
    void executePatchFullReportsTheStatusAndTheFailure() {
        PatchResponse refused = HttpServices.executePatchFull("https://127.0.0.1:1/x", "{}", new java.util.HashMap<>());
        assertEquals(0, refused.status);
        assertTrue(refused.failure != null && !refused.failure.isEmpty());
    }
}
