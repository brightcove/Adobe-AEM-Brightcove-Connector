package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.callables;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.mime.MimeTypeService;
import org.junit.jupiter.api.Test;

import com.coresecure.brightcove.wrapper.objects.BinaryObj;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A video with no {@code images.thumbnail} gets {@code Constants.DEFAULT_THUMBNAIL_LOCATION}
 * (a JCR path under /apps) as its thumbnail URL from {@code CmsAPI.addThumbnail}. The import runs
 * as the {@code brightcove_admin} service user, which the shipped repoinit grants on /content only,
 * so a JCR read of that path returns nothing. Before the fix {@code createAsset} read it from JCR,
 * logged {@code FAIL EXTERNAL} at TRACE and skipped the asset: 26 of 54 test-account videos never
 * imported on a fresh install. The placeholder now comes from the bundle classpath.
 *
 * <p>The resolver here returns null for every resource, which is what the service user sees for
 * /apps. Each import test asserts that {@code AssetManager.createAsset} receives a real MP4 encoded
 * from the placeholder, not just that it was called.</p>
 */
class VideoImportCallableThumbnaillessVideoTest {

    private static final String VIDEO_ID = "1234567890";

    @Test
    void thumbnaillessVideoStillProducesAnAssetWhenTheAppsFallbackIsUnreadable() throws Exception {
        // what CmsAPI.addThumbnail writes for a video without images.thumbnail
        byte[] mp4 = importVideoWithThumbnail(Constants.DEFAULT_THUMBNAIL_LOCATION);
        assertIsMp4(mp4);
    }

    @Test
    void unreadableLocalThumbnailFallsBackToTheBundledPlaceholder() throws Exception {
        // a local thumbnail path the service user cannot resolve takes the generic fallback branch
        byte[] mp4 = importVideoWithThumbnail("/content/dam/somewhere/missing.jpg");
        assertIsMp4(mp4);
    }

    /**
     * A reader that throws, ordered first (the CMYK reader on Java 17+ without the java.desktop
     * export): the import must fall through to the next reader instead of failing the video.
     */
    @Test
    void aFailingFirstImageReaderDoesNotStopTheImport() throws Exception {
        com.coresecure.brightcove.wrapper.utils.FailingImageReader.install();
        try {
            assertIsMp4(importVideoWithThumbnail(Constants.DEFAULT_THUMBNAIL_LOCATION));
        } finally {
            com.coresecure.brightcove.wrapper.utils.FailingImageReader.uninstall();
        }
    }

    @Test
    void bundledPlaceholderIsADecodableImageIdenticalToTheUiAppsCopy() throws Exception {
        BinaryObj placeholder = VideoImportCallable.defaultThumbnailBinary();
        assertNotNull(placeholder.binary, "placeholder missing from the classpath: "
                + Constants.DEFAULT_THUMBNAIL_CLASSPATH_RESOURCE);
        byte[] bundled = readAll(placeholder.binary);
        assertEquals("image/jpeg", placeholder.mime_type);

        BufferedImage image = com.coresecure.brightcove.wrapper.utils.ImageDecoder.read(new ByteArrayInputStream(bundled));
        assertNotNull(image, "bundled placeholder does not decode as an image");
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0);

        // The admin UI still shows the /apps copy (DEFAULT_THUMBNAIL_LOCATION); keep the two identical.
        Path uiApps = Paths.get("..", "ui.apps", "src", "main", "content", "jcr_root"
                + Constants.DEFAULT_THUMBNAIL_LOCATION);
        assertTrue(Files.exists(uiApps), "ui.apps copy not found at " + uiApps.toAbsolutePath());
        assertArrayEquals(Files.readAllBytes(uiApps), bundled,
                "core/src/main/resources placeholder differs from the ui.apps copy");
    }

    private static byte[] importVideoWithThumbnail(String thumbnailUrl) throws Exception {
        ObjectNode video = JsonNodeFactory.instance.objectNode();
        video.put(Constants.ID, VIDEO_ID);
        video.put(Constants.NAME, "no thumbnail");
        video.put(Constants.STATE, "ACTIVE");
        video.put(Constants.THUMBNAIL_URL, thumbnailUrl);

        ResourceResolver resolver = mock(ResourceResolver.class);
        when(resolver.getResource(anyString())).thenReturn(null); // /apps is invisible to the service user
        AssetManager assetManager = mock(AssetManager.class);
        Asset created = mock(Asset.class);
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);
        AtomicReference<byte[]> received = new AtomicReference<>();
        when(assetManager.createAsset(anyString(), any(InputStream.class), anyString(), anyBoolean()))
                .thenAnswer(inv -> {
                    received.set(readAll(inv.getArgument(1)));
                    return created;
                });

        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        MimeTypeService mime = mock(MimeTypeService.class);
        when(mime.getMimeType(anyString())).thenReturn("video/mp4");
        ServiceUtil serviceUtil = mock(ServiceUtil.class);

        new VideoImportCallable(video, "/content/dam/brightcove_assets", "acct1", factory, mime, serviceUtil)
                .call();

        verify(assetManager, times(1)).createAsset(
                eq("/content/dam/brightcove_assets/acct1/" + VIDEO_ID + ".mp4"),
                any(InputStream.class), eq("video/mp4"), eq(true));
        verify(serviceUtil, times(1)).updateAsset(eq(created), eq(video), eq(resolver), eq("acct1"));
        return received.get();
    }

    private static void assertIsMp4(byte[] bytes) {
        assertNotNull(bytes, "createAsset was called without a readable stream");
        assertTrue(bytes.length > 8, "asset binary is empty (" + bytes.length + " bytes)");
        String box = new String(bytes, 4, 4, StandardCharsets.US_ASCII);
        assertEquals("ftyp", box, "asset binary is not an MP4 container");
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
