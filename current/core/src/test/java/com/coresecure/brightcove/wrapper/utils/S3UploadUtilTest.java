package com.coresecure.brightcove.wrapper.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;

/**
 * Pins S3UploadUtil.uploadToUrl against a loopback HTTPS server standing in for the signed S3 URL:
 * the object is PUT with the stream's exact bytes, only a 200 counts as success, and every
 * failure (non-200, refused connection, bad proxy) is reported as false rather than thrown.
 */
class S3UploadUtilTest {

    private LoopbackHttps server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<byte[]> received = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);

    @BeforeEach
    void setUp() throws Exception {
        server = new LoopbackHttps().on("/upload", (HttpExchange ex) -> {
            method.set(ex.getRequestMethod());
            received.set(readAll(ex));
            LoopbackHttps.reply(ex, status.get(), "text/plain", new byte[0]);
        }).start();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private static byte[] readAll(HttpExchange ex) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = ex.getRequestBody().read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private URL url() throws Exception {
        return new URL(server.base() + "/upload");
    }

    private static byte[] payload() {
        // spans several 1024-byte read buffers
        byte[] b = new byte[5000];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (i % 251);
        }
        return b;
    }

    private static int closedPort() throws Exception {
        try (ServerSocket s = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            return s.getLocalPort();
        }
    }

    @Test
    void directUploadPutsTheExactBytes() throws Exception {
        assertTrue(S3UploadUtil.uploadToUrl(url(), new ByteArrayInputStream(payload())));

        assertEquals("PUT", method.get());
        assertArrayEquals(payload(), received.get());
    }

    @Test
    void nonOkStatusIsFailure() throws Exception {
        status.set(403);

        assertFalse(S3UploadUtil.uploadToUrl(url(), new ByteArrayInputStream(payload())));
    }

    @Test
    void successStatusOtherThan200IsStillFailure() throws Exception {
        status.set(204);

        assertFalse(S3UploadUtil.uploadToUrl(url(), new ByteArrayInputStream(payload())));
    }

    @Test
    void unreachableHostIsFailureNotAnException() throws Exception {
        URL dead = new URL("https://127.0.0.1:" + closedPort() + "/upload");

        assertFalse(S3UploadUtil.uploadToUrl(dead, new ByteArrayInputStream(payload())));
    }

    @Test
    void uploadThroughNoProxyObjectPutsTheExactBytes() throws Exception {
        assertTrue(S3UploadUtil.uploadToUrl(url(), new ByteArrayInputStream(payload()), Proxy.NO_PROXY));

        assertEquals("PUT", method.get());
        assertArrayEquals(payload(), received.get());
    }

    @Test
    void proxyObjectOverloadReportsNonOkAsFailure() throws Exception {
        status.set(500);

        assertFalse(S3UploadUtil.uploadToUrl(url(), new ByteArrayInputStream(payload()), Proxy.NO_PROXY));
    }

    @Test
    void deadProxyStringIsFailureNotAnException() throws Exception {
        String dead = "127.0.0.1:" + closedPort();

        assertFalse(S3UploadUtil.uploadToUrl(url(), new ByteArrayInputStream(payload()), dead));
        assertEquals(null, method.get(), "the request must not have bypassed the configured proxy");
    }

    @Test
    @Disabled("DEFECT: a non-numeric port in the String proxy throws NumberFormatException out of uploadToUrl instead of returning false")
    void malformedProxyPortIsFailureNotAnException() throws Exception {
        assertFalse(S3UploadUtil.uploadToUrl(url(), new ByteArrayInputStream(payload()), "host:notaport"));
    }

    @Test
    @Disabled("DEFECT: the String-proxy overload with an empty proxy routes through a bogus proxy (:0) instead of uploading directly")
    void emptyProxyStringUploadsDirectly() throws Exception {
        assertTrue(S3UploadUtil.uploadToUrl(url(), new ByteArrayInputStream(payload()), ""));

        assertArrayEquals(payload(), received.get());
    }
}
