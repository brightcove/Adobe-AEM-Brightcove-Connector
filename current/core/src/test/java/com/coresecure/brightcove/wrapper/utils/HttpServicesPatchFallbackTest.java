package com.coresecure.brightcove.wrapper.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * BGS-1600: Java 17+ refuses both {@code setRequestMethod("PATCH")} and the reflection
 * hack behind it, so CMS metadata PATCHes must fall back to the Apache client.
 */
class HttpServicesPatchFallbackTest {

    /**
     * ⚠️ The verdict depends on the JVM running the test. The build runs on 11, where the
     * reflection hack still works; run it on 17+ to measure the fallback trigger:
     * {@code mvn -pl core test -Dtest=HttpServicesPatchFallbackTest -Djvm=<jdk21>/bin/java}
     */
    @Test
    void realHttpsConnectionNeedsTheFallbackExactlyWhenTheJvmBlocksTheReflection() throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL("https://127.0.0.1:1/v1/accounts/x/videos/y").openConnection();

        boolean jvmBlocksReflection = Runtime.version().feature() >= 17;

        assertEquals(!jvmBlocksReflection, HttpServices.configurePatchMethod(connection),
                "Java " + Runtime.version().feature() + ": PATCH via HttpsURLConnection should be "
                        + (jvmBlocksReflection ? "refused, routing to the Apache client" : "accepted"));
    }

    @Test
    void apacheFallbackSendsAPatchWithThePayloadAndHeadersAndReturnsTheBody() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(readAll(exchange.getRequestBody()));
            byte[] reply = "{\"id\":\"y\",\"name\":\"renamed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(reply);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/accounts/x/videos/y";

            String response = HttpServices.executePatchUsingApacheHttpClient(
                    url, "{\"name\":\"renamed\"}", Collections.singletonMap("Authorization", "Bearer t"));

            assertEquals("PATCH", method.get());
            assertEquals("{\"name\":\"renamed\"}", body.get());
            assertEquals("Bearer t", auth.get());
            assertEquals("{\"id\":\"y\",\"name\":\"renamed\"}", response);
        } finally {
            server.stop(0);
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }
}
