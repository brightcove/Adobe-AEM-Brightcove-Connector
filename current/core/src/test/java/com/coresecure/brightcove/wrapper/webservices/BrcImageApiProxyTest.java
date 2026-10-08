package com.coresecure.brightcove.wrapper.webservices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.coresecure.brightcove.wrapper.utils.HttpServices;
import com.coresecure.brightcove.wrapper.utils.LoopbackHttps;

/**
 * BGS-1600: the image cache servlet read the poster with {@code ImageIO.read(URL)}, which opens
 * its own connection and ignores {@code HttpServices.PROXY}. A dead proxy is the discriminator:
 * through the proxy the fetch must fail (explicit 502), direct it would succeed (200).
 */
class BrcImageApiProxyTest {

    private LoopbackHttps server;
    private byte[] png;
    private int posterStatus;
    private String posterContentType;
    private byte[] posterBody;

    @BeforeEach
    void setUp() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        png = bos.toByteArray();
        posterStatus = 200;
        posterContentType = "image/png";
        posterBody = png;
        server = new LoopbackHttps().on("/poster.png", ex ->
                LoopbackHttps.reply(ex, posterStatus, posterContentType, posterBody)).start();
        HttpServices.setProxy(Proxy.NO_PROXY);
    }

    @AfterEach
    void tearDown() {
        HttpServices.setProxy(Proxy.NO_PROXY);
        server.close();
    }

    @Test
    void servesThePosterDirectlyWhenNoProxyIsConfigured() throws Exception {
        Result r = get(server.base() + "/poster.png?sig=abc");
        assertEquals(200, r.status);
        assertEquals("image/jpeg", r.contentType);
        assertTrue(r.body.length > 0 && com.coresecure.brightcove.wrapper.utils.ImageDecoder.read(new java.io.ByteArrayInputStream(r.body)) != null,
                "body must decode as an image");
    }

    @Test
    void fetchGoesThroughTheConfiguredProxyAndFailsExplicitlyWhenItIsDead() throws Exception {
        // Port 1 on loopback refuses connections: reachable only if the proxy is bypassed.
        HttpServices.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 1)));

        Result r = get(server.base() + "/poster.png");

        assertEquals(502, r.status, "a poster that cannot be fetched through the proxy must not be a 200");
        assertTrue(r.text().contains("\"error_code\":502"), r.text());
    }

    @Test
    void upstreamErrorIsAnExplicitBadGatewayNotAnEmpty200() throws Exception {
        posterStatus = 403;
        posterContentType = "text/plain";
        posterBody = "denied".getBytes(StandardCharsets.UTF_8);

        Result r = get(server.base() + "/poster.png");

        assertEquals(502, r.status);
        assertTrue(r.text().contains("\"error_code\":502"), r.text());
    }

    @Test
    void nonImageBodyIsAnExplicit404WithAMessageNotASilent500() throws Exception {
        posterContentType = "text/html";
        posterBody = "<html>nope</html>".getBytes(StandardCharsets.UTF_8);

        Result r = get(server.base() + "/poster.png");

        assertEquals(404, r.status);
        assertTrue(r.text().contains("not a readable image"), r.text());
    }

    /** A reader that throws, ordered first: the poster is still served (docs/imageio-java21.md). */
    @Test
    void aFailingFirstImageReaderStillServesThePoster() throws Exception {
        HttpServices.setProxy(Proxy.NO_PROXY);
        com.coresecure.brightcove.wrapper.utils.FailingImageReader.install();
        Result r;
        try {
            r = get(server.base() + "/poster.png?sig=abc");
        } finally {
            com.coresecure.brightcove.wrapper.utils.FailingImageReader.uninstall();
        }
        assertEquals(200, r.status, r.text());
        assertEquals("image/jpeg", r.contentType);
        assertTrue(r.body.length > 0 && com.coresecure.brightcove.wrapper.utils.ImageDecoder.read(new java.io.ByteArrayInputStream(r.body)) != null,
                "body must decode as an image");
    }

    @Test
    void videoWithoutPosterIs404() throws Exception {
        Result r = get(null);
        assertEquals(404, r.status);
        assertTrue(r.text().contains("\"error_code\":404"), r.text());
    }

    private static final class Result {
        int status = 200;
        String contentType;
        byte[] body = new byte[0];
        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private Result get(String posterUrl) throws Exception {
        BrcImageApi servlet = new BrcImageApi() {
            @Override
            String getPoster(String accountKeyStr, String videoIdStr) {
                return posterUrl;
            }
        };
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getParameter("id")).thenReturn("1");
        when(request.getParameter("key")).thenReturn("acct");
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        Result result = new Result();
        ByteArrayOutputStream binary = new ByteArrayOutputStream();
        StringWriter text = new StringWriter();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            public boolean isReady() { return true; }
            public void setWriteListener(WriteListener l) { }
            public void write(int b) throws IOException { binary.write(b); }
        });
        when(response.getWriter()).thenReturn(new PrintWriter(text, true));
        org.mockito.Mockito.doAnswer(i -> { result.status = i.getArgument(0); return null; })
                .when(response).setStatus(any(int.class));
        org.mockito.Mockito.doAnswer(i -> { result.contentType = i.getArgument(0); return null; })
                .when(response).setContentType(any(String.class));

        servlet.doGet(request, response);

        result.body = binary.size() > 0 ? binary.toByteArray() : text.toString().getBytes(StandardCharsets.UTF_8);
        return result;
    }
}
