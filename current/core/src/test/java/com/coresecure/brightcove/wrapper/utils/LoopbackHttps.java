package com.coresecure.brightcove.wrapper.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/**
 * Test helper: an HTTPS server on 127.0.0.1 (self-signed {@code /loopback-test.p12}) plus
 * a JVM-wide trust override, because HttpServices only speaks HTTPS to the OAuth and CMS hosts.
 * Always {@link #close()} it: that stops the server and restores the JVM defaults.
 */
public final class LoopbackHttps implements AutoCloseable {

    private final HttpsServer server;
    private final SSLSocketFactory previousFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
    private final HostnameVerifier previousVerifier = HttpsURLConnection.getDefaultHostnameVerifier();

    public LoopbackHttps() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = LoopbackHttps.class.getResourceAsStream("/loopback-test.p12")) {
            ks.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());
        SSLContext serverCtx = SSLContext.getInstance("TLS");
        serverCtx.init(kmf.getKeyManagers(), null, null);
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverCtx));

        SSLContext clientCtx = SSLContext.getInstance("TLS");
        clientCtx.init(null, new TrustManager[] {new X509TrustManager() {
            public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) { }
            public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) { }
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
        }}, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(clientCtx.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true);
    }

    public LoopbackHttps on(String path, HttpHandler handler) {
        server.createContext(path, handler);
        return this;
    }

    public LoopbackHttps start() {
        server.start();
        return this;
    }

    public String base() {
        return "https://127.0.0.1:" + server.getAddress().getPort();
    }

    public static void reply(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    public static void reply(HttpExchange ex, int status, String json) throws IOException {
        reply(ex, status, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        server.stop(0);
        HttpsURLConnection.setDefaultSSLSocketFactory(previousFactory);
        HttpsURLConnection.setDefaultHostnameVerifier(previousVerifier);
    }
}
