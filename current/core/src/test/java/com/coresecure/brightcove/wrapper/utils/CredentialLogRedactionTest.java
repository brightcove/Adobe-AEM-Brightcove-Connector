package com.coresecure.brightcove.wrapper.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.coresecure.brightcove.wrapper.api.CmsAPI;
import com.coresecure.brightcove.wrapper.objects.Account;
import com.coresecure.brightcove.wrapper.objects.Platform;

import uk.org.lidalia.slf4jtest.LoggingEvent;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

/**
 * BGS-1600: at DEBUG (which the shipped LogManager config enables on non-prod runmodes)
 * the connector logged the OAuth Basic header, the Base64 of id:secret, the OAuth response
 * body and the Bearer token. Drives a real OAuth login plus CMS reads against a loopback
 * HTTPS server and asserts that none of a known fake secret, Basic value or token reaches
 * ANY captured log event (every level, every logger, message, arguments and stack traces).
 *
 * <p>The server also checks the credentials it receives, so the requests demonstrably carried
 * the secrets; a client that sent nothing could not pass vacuously.</p>
 */
class CredentialLogRedactionTest {

    private static final String CLIENT_ID = "fake-client-id-7f3a91";
    private static final String CLIENT_SECRET = "fake-secret-Zq81xLmNp0Rt";
    private static final String ACCESS_TOKEN = "fake-token-A1b2C3d4E5f6G7h8";
    private static final String BASIC_VALUE = Base64.getEncoder()
            .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));

    // Shapes of a DI upload-urls response; fake values, never real keys.
    private static final String AWS_KEY_ID = "FAKEAKIDQ7W2E9R4T6Y8";
    private static final String AWS_SECRET = "fake-aws-secret-Hn3Jk5Lm7Np9Qr";
    private static final String AWS_SESSION = "fake-session-Uv2Wx4Yz6Ab8Cd0Ef";
    private static final String AMZ_SIGNATURE = "fakesig0a1b2c3d4e5f60718293a4b5c6d7e8f9";
    private static final String AMZ_CREDENTIAL_ID = "FAKEAKIDCRED5G7H9J1K";

    private LoopbackHttps server;
    private final AtomicInteger oauthCalls = new AtomicInteger();
    private final AtomicInteger cmsCallsWithToken = new AtomicInteger();

    @BeforeEach
    void startLoopbackServer() throws Exception {
        TestLoggerFactory.clear();
        server = new LoopbackHttps()
                .on("/oauth/access_token", ex -> {
                    String auth = ex.getRequestHeaders().getFirst("Authorization");
                    if (("Basic " + BASIC_VALUE).equals(auth)) {
                        oauthCalls.incrementAndGet();
                        LoopbackHttps.reply(ex, 200, "{\"access_token\":\"" + ACCESS_TOKEN
                                + "\",\"token_type\":\"Bearer\",\"expires_in\":300}");
                    } else {
                        LoopbackHttps.reply(ex, 401, "{\"error\":\"invalid_client\"}");
                    }
                })
                .on("/cms", ex -> {
                    if (("Bearer " + ACCESS_TOKEN).equals(ex.getRequestHeaders().getFirst("Authorization"))) {
                        cmsCallsWithToken.incrementAndGet();
                        LoopbackHttps.reply(ex, 200,
                                ex.getRequestURI().getPath().endsWith("/videos") ? "[]" : "{\"id\":\"v1\"}");
                    } else {
                        LoopbackHttps.reply(ex, 401, "[{\"error_code\":\"UNAUTHORIZED\"}]");
                    }
                })
                .on("/di", ex -> {
                    if (("Bearer " + ACCESS_TOKEN).equals(ex.getRequestHeaders().getFirst("Authorization"))
                            && ex.getRequestURI().getPath().contains("/upload-urls/")) {
                        cmsCallsWithToken.incrementAndGet();
                        String query = "X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=" + AMZ_CREDENTIAL_ID
                                + "%2F20261008%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Security-Token=" + AWS_SESSION
                                + "&X-Amz-Signature=" + AMZ_SIGNATURE + "&X-Amz-SignedHeaders=host";
                        LoopbackHttps.reply(ex, 200, "{\"bucket\":\"b\",\"object_key\":\"k/f.mp4\","
                                + "\"access_key_id\":\"" + AWS_KEY_ID + "\","
                                + "\"secret_access_key\":\"" + AWS_SECRET + "\","
                                + "\"session_token\":\"" + AWS_SESSION + "\","
                                + "\"signed_url\":\"https://b.s3.amazonaws.com/k/f.mp4?" + query + "\","
                                + "\"api_request_url\":\"https://b.s3.amazonaws.com/k/f.mp4?" + query + "\"}");
                    } else {
                        LoopbackHttps.reply(ex, 401, "[{\"error_code\":\"UNAUTHORIZED\"}]");
                    }
                })
                .start();
    }

    @AfterEach
    void stopLoopbackServer() {
        server.close();
    }

    @Test
    void oauthLoginAndCmsRequestsNeverLogTheSecretTheBasicValueOrTheToken() {
        String base = server.base();
        Platform platform = new Platform(base + "/oauth", base + "/cms", base + "/di", base + "/players");
        Account account = new Account(platform, CLIENT_ID, CLIENT_SECRET, "123456");
        CmsAPI cms = new CmsAPI(account);

        assertNotNull(cms.getVideo("v1"));
        try {
            cms.getVideos("", 5, 0, null);
        } catch (RuntimeException expectedWithoutOsgi) {
            // The tag lookup after the token log needs an OSGi service registry. The leak site
            // (CmsAPI.getVideos "authToken" debug line) runs before it.
        }

        assertTrue(oauthCalls.get() >= 2, "OAuth must have been called with the Basic header");
        assertTrue(cmsCallsWithToken.get() >= 1, "CMS must have been called with the Bearer token");

        List<String> logged = capturedLogLines();
        assertTrue(logged.size() > 10, "expected DEBUG output to be captured, got " + logged.size());

        for (String line : logged) {
            assertTrue(!line.contains(CLIENT_SECRET), "client secret logged: " + scrub(line));
            assertTrue(!line.contains(BASIC_VALUE), "Base64(id:secret) logged: " + scrub(line));
            assertTrue(!line.contains(ACCESS_TOKEN), "access token logged: " + scrub(line));
        }

        // The lines are kept, showing the scheme only.
        assertTrue(logged.stream().anyMatch(l -> l.contains("Basic ***")), "Basic scheme line missing");
        assertTrue(logged.stream().anyMatch(l -> l.contains("Bearer ***")), "Bearer scheme line missing");
    }

    @Test
    void uploadUrlsResponseNeverLogsAwsKeysSessionTokenOrSignature() {
        String base = server.base();
        Platform platform = new Platform(base + "/oauth", base + "/cms", base + "/di", base + "/players");
        CmsAPI cms = new CmsAPI(new Account(platform, CLIENT_ID, CLIENT_SECRET, "123456"));

        com.fasterxml.jackson.databind.node.ObjectNode urls = cms.getIngestURL("v1", "f.mp4");
        // The caller still gets the unredacted values: redaction is for the log only.
        assertEquals(AWS_SECRET, urls.path("secret_access_key").asText(), "upload-urls call did not round-trip");
        assertTrue(urls.path("signed_url").asText().contains(AMZ_SIGNATURE));

        List<String> logged = capturedLogLines();
        for (String line : logged) {
            for (String secret : new String[] {AWS_KEY_ID, AWS_SECRET, AWS_SESSION, AMZ_SIGNATURE,
                    AMZ_CREDENTIAL_ID, ACCESS_TOKEN, CLIENT_SECRET, BASIC_VALUE}) {
                assertTrue(!line.contains(secret), "credential logged: " + scrub(line));
            }
        }
        assertTrue(logged.stream().anyMatch(l -> l.contains("\"secret_access_key\":\"***\"")),
                "the upload-urls response trace line is missing (redacted, not dropped)");
    }

    @Test
    void redactorMasksEverySpellingAndEncodingOfACredential() {
        // JSON, parsed: snake, camel, AWS fields, nested, and an escaped quote that must not leak a tail.
        assertEquals("{\"secret_access_key\":\"***\",\"access_key_id\":\"***\",\"session_token\":\"***\"}",
                LogRedactor.body("{\"secret_access_key\":\"S\",\"access_key_id\":\"K\",\"session_token\":\"T\"}"));
        assertEquals("{\"accessToken\":\"***\",\"clientSecret\":\"***\",\"refreshToken\":\"***\"}",
                LogRedactor.body("{\"accessToken\":\"S\",\"clientSecret\":\"S\",\"refreshToken\":\"S\"}"));
        assertEquals("{\"password\":\"***\"}", LogRedactor.body("{\"password\":\"ab\\\"cdS\"}"));
        assertEquals("{\"a\":[{\"b\":{\"access_token\":\"***\"}}]}",
                LogRedactor.body("{\"a\":[{\"b\":{\"access_token\":\"S\"}}]}"));
        assertEquals("{\"signed_url\":\"https://h/k?X-Amz-Credential=***&X-Amz-Security-Token=***"
                        + "&X-Amz-Signature=***&X-Amz-SignedHeaders=host\"}",
                LogRedactor.body("{\"signed_url\":\"https://h/k?X-Amz-Credential=C%2F1%2Fs3&X-Amz-Security-Token=T"
                        + "&X-Amz-Signature=S&X-Amz-SignedHeaders=host\"}"));
        assertEquals("{\"id\":\"v1\",\"name\":\"n\",\"expires_in\":300}",
                LogRedactor.body("{\"id\":\"v1\",\"name\":\"n\",\"expires_in\":300}"));

        // Not JSON: single quotes, form/semicolon separators, URL-encoded '=', headers, escaped tails.
        assertEquals("{'access_token':'***'}", LogRedactor.body("{'access_token':'S'}"));
        assertEquals("x;access_token=***", LogRedactor.body("x;access_token=S"));
        assertEquals("\"client_secret=***\"", LogRedactor.body("\"client_secret=S\""));
        assertEquals("grant_type=x&client_secret%3D***%26a=b", LogRedactor.body("grant_type=x&client_secret%3DS%26a=b"));
        assertEquals("a=1; password=***; b=2", LogRedactor.body("a=1; password=S; b=2"));
        assertEquals("-H X-Amz-Security-Token: *** -H Host: h", LogRedactor.body("-H X-Amz-Security-Token: S -H Host: h"));
        assertEquals("S3RESP : {\"password\":\"***\"} tail", LogRedactor.body("S3RESP : {\"password\":\"ab\\\"cdS\"} tail"));
        assertEquals("X-Amz-Security-Token: ***", LogRedactor.header("X-Amz-Security-Token", "S"));
        assertEquals("Authorization: AWS4-HMAC-SHA256 ***",
                LogRedactor.header("Authorization", "AWS4-HMAC-SHA256 Credential=K/1/s3, Signature=S"));
    }

    @Test
    void redactorMasksCredentialsInHeadersAndBodies() {
        assertEquals("Authorization: Basic ***", LogRedactor.header("Authorization", "Basic abc123"));
        assertEquals("authorization: Bearer ***", LogRedactor.header("authorization", "Bearer tok"));
        assertEquals("Content-Type: application/json", LogRedactor.header("Content-Type", "application/json"));
        assertEquals("{\"access_token\":\"***\",\"expires_in\":300}",
                LogRedactor.body("{\"access_token\":\"abc\",\"expires_in\":300}"));
        assertEquals("grant_type=x&client_secret=***", LogRedactor.body("grant_type=x&client_secret=zzz"));
        assertEquals("[]", LogRedactor.body("[]"));
    }

    private static List<String> capturedLogLines() {
        List<String> logged = new ArrayList<>();
        for (LoggingEvent e : TestLoggerFactory.getAllLoggingEvents()) {
            StringBuilder sb = new StringBuilder(String.valueOf(e.getMessage()));
            for (Object arg : e.getArguments()) {
                sb.append(' ').append(arg);
            }
            if (e.getThrowable().isPresent()) {
                sb.append(' ').append(stackTrace(e.getThrowable().get()));
            }
            logged.add(sb.toString());
        }
        return logged;
    }

    private static String stackTrace(Throwable t) {
        java.io.StringWriter w = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(w));
        return w.toString();
    }

    private static String scrub(String s) {
        // Never echo the fake values back into a failure message; show only the shape.
        return s.replace(CLIENT_SECRET, "<SECRET>").replace(BASIC_VALUE, "<BASIC>").replace(ACCESS_TOKEN, "<TOKEN>")
                .replace(AWS_KEY_ID, "<AWS_KEY_ID>").replace(AWS_SECRET, "<AWS_SECRET>")
                .replace(AWS_SESSION, "<AWS_SESSION>").replace(AMZ_SIGNATURE, "<AMZ_SIG>")
                .replace(AMZ_CREDENTIAL_ID, "<AMZ_CRED>");
    }
}
