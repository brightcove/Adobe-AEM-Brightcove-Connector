package com.coresecure.brightcove.wrapper.utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code ImageIO.read} that falls through to the next registered reader when one throws.
 * Context: docs/imageio-java21.md
 */
public final class ImageDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageDecoder.class);
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private ImageDecoder() {
    }

    /**
     * Decodes {@code in} (read fully, not closed) with the first reader that succeeds. Returns null
     * when no reader recognises the data; throws the last failure when every candidate failed.
     */
    public static BufferedImage read(InputStream in) throws IOException {
        byte[] bytes = readAll(in);
        IOException last = null;
        Iterator<ImageReader> readers;
        try (ImageInputStream probe = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (probe == null) {
                return null;
            }
            readers = ImageIO.getImageReaders(probe);
            while (true) {
                ImageReader reader;
                try {
                    if (!readers.hasNext()) {
                        break;
                    }
                    reader = readers.next();
                } catch (RuntimeException e) {
                    // The JDK iterator wraps a reader that cannot be instantiated.
                    last = warn("<uninstantiable reader>", e);
                    continue;
                }
                try (ImageInputStream attempt = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    reader.setInput(attempt, true, true);
                    BufferedImage image = reader.read(0, reader.getDefaultReadParam());
                    if (image != null) {
                        return image;
                    }
                } catch (IIOException | RuntimeException e) {
                    last = warn(reader.getClass().getName(), e);
                } finally {
                    reader.dispose();
                }
            }
        }
        if (last != null) {
            throw last;
        }
        return null;
    }

    private static IOException warn(String readerClass, Exception e) {
        if (WARNED.add(readerClass)) {
            LOGGER.warn("Image reader {} failed ({}); trying the next registered reader. Logged once per reader.",
                    readerClass, e.toString());
        }
        return e instanceof IOException ? (IOException) e : new IIOException(readerClass + " failed", e);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
