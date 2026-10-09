package com.coresecure.brightcove.wrapper.utils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

/**
 * Test helper: an ImageIO reader that claims every input and throws on read, registered AHEAD of
 * every other reader. It stands in for day-commons-gfx's CMYK JPEG reader on a Java 17+ JVM
 * without the java.desktop export (docs/imageio-java21.md), without depending on that JVM flag or
 * on the registry's otherwise unordered iteration. Always {@link #uninstall()} it.
 */
public final class FailingImageReader extends ImageReader {

    private static final Spi SPI = new Spi();

    private FailingImageReader(ImageReaderSpi spi) {
        super(spi);
    }

    public static void install() {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        List<ImageReaderSpi> others = new ArrayList<>();
        Iterator<ImageReaderSpi> it = registry.getServiceProviders(ImageReaderSpi.class, false);
        while (it.hasNext()) {
            others.add(it.next());
        }
        registry.registerServiceProvider(SPI, ImageReaderSpi.class);
        for (ImageReaderSpi other : others) {
            registry.setOrdering(ImageReaderSpi.class, SPI, other);
        }
    }

    public static void uninstall() {
        IIORegistry.getDefaultInstance().deregisterServiceProvider(SPI, ImageReaderSpi.class);
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IIOException {
        throw new IIOException("test: this reader always fails, like the CMYK reader without its JDK backend");
    }

    @Override public int getNumImages(boolean allowSearch) { return 1; }
    @Override public int getWidth(int imageIndex) { return 1; }
    @Override public int getHeight(int imageIndex) { return 1; }
    @Override public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) { return Collections.emptyIterator(); }
    @Override public IIOMetadata getStreamMetadata() { return null; }
    @Override public IIOMetadata getImageMetadata(int imageIndex) { return null; }

    private static final class Spi extends ImageReaderSpi {
        Spi() {
            super("test", "1", new String[] {"jpeg", "JPEG", "jpg", "png"}, new String[] {"jpg", "png"},
                    new String[] {"image/jpeg", "image/png"}, FailingImageReader.class.getName(),
                    new Class<?>[] {ImageInputStream.class}, null, false, null, null, null, null,
                    false, null, null, null, null);
        }

        @Override public boolean canDecodeInput(Object source) { return true; }
        @Override public ImageReader createReaderInstance(Object extension) { return new FailingImageReader(this); }
        @Override public String getDescription(Locale locale) { return "always-failing test reader"; }
    }
}
