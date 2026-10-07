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

        List<String> logged = new ArrayList<>();
        for (LoggingEvent e : TestLoggerFactory.getAllLoggingEvents()) {
            StringBuilder sb = new StringBuilder(String.valueOf(e.getMessage()));
            for (Object arg : e.getArguments()) {
                sb.append(' ').append(arg);
            }
            if (e.getThrowable().isPresent()) {
                sb.append(' ').append(e.getThrowable().get());
            }
            logged.add(sb.toString());
        }
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
    void redactorMasksCredentialsInHeadersAndBodies() {
        assertEquals("Authorization: Basic ***", LogRedactor.header("Authorization", "Basic abc123"));
        assertEquals("authorization: Bearer ***", LogRedactor.header("authorization", "Bearer tok"));
        assertEquals("Content-Type: application/json", LogRedactor.header("Content-Type", "application/json"));
        assertEquals("{\"access_token\":\"***\",\"expires_in\":300}",
                LogRedactor.body("{\"access_token\":\"abc\",\"expires_in\":300}"));
        assertEquals("grant_type=x&client_secret=***", LogRedactor.body("grant_type=x&client_secret=zzz"));
        assertEquals("[]", LogRedactor.body("[]"));
    }

    private static String scrub(String s) {
        // Never echo the fake values back into a failure message; show only the shape.
        return s.replace(CLIENT_SECRET, "<SECRET>").replace(BASIC_VALUE, "<BASIC>").replace(ACCESS_TOKEN, "<TOKEN>");
    }
}
