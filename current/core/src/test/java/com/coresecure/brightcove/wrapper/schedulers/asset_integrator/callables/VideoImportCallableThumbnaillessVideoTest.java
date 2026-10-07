package com.coresecure.brightcove.wrapper.schedulers.asset_integrator.callables;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.mime.MimeTypeService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import com.coresecure.brightcove.wrapper.utils.Constants;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Pins the CORRECT behaviour for a newly diagnosed defect: a video with no
 * {@code images.thumbnail} gets {@code Constants.DEFAULT_THUMBNAIL_LOCATION}
 * (a JCR path under /apps) as its thumbnail URL from {@code CmsAPI.addThumbnail}.
 * {@code VideoImportCallable.createAsset} then reads that path, and its own
 * hard-coded /apps noThumbnail.jpg fallback, through the {@code brightcove_admin}
 * service resolver, but the shipped repoinit grants that user rights on /content
 * only. {@code getResource} returns null, {@code LOGGER.trace("FAIL EXTERNAL")}
 * fires and the asset is silently skipped, so thumbnail-less videos never import
 * on a fresh install (26 of 54 on the test account).
 *
 * <p>The resolver here returns null for every resource, which is what the
 * service user sees for /apps. Any fix (a classpath placeholder, a /content
 * copy, a repoinit grant) must still reach {@code AssetManager.createAsset}.</p>
 *
 * Disabled until the defect is fixed; it was run enabled first and failed on the
 * current code (createAsset was never called).
 */
class VideoImportCallableThumbnaillessVideoTest {

    @Test
    @Disabled("thumbnail-less videos skipped on fresh installs: /apps fallback unreadable by brightcove_admin")
    void thumbnaillessVideoStillProducesAnAssetWhenTheAppsFallbackIsUnreadable() throws Exception {
        ObjectNode video = JsonNodeFactory.instance.objectNode();
        video.put(Constants.ID, "1234567890");
        video.put(Constants.NAME, "no thumbnail");
        video.put(Constants.STATE, "ACTIVE");
        // what CmsAPI.addThumbnail writes for a video without images.thumbnail
        video.put(Constants.THUMBNAIL_URL, Constants.DEFAULT_THUMBNAIL_LOCATION);

        ResourceResolver resolver = mock(ResourceResolver.class);
        when(resolver.getResource(anyString())).thenReturn(null); // /apps is invisible to the service user
        AssetManager assetManager = mock(AssetManager.class);
        Asset created = mock(Asset.class);
        when(resolver.adaptTo(AssetManager.class)).thenReturn(assetManager);
        when(assetManager.createAsset(anyString(), any(InputStream.class), anyString(), anyBoolean()))
                .thenReturn(created);

        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        MimeTypeService mime = mock(MimeTypeService.class);
        when(mime.getMimeType(anyString())).thenReturn("video/mp4");
        ServiceUtil serviceUtil = mock(ServiceUtil.class);

        new VideoImportCallable(video, "/content/dam/brightcove_assets", "acct1", factory, mime, serviceUtil)
                .call();

        verify(assetManager, times(1)).createAsset(
                eq("/content/dam/brightcove_assets/acct1/1234567890.mp4"),
                any(InputStream.class), eq("video/mp4"), eq(true));
    }
}
