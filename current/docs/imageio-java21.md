# ImageIO JPEG decoding on Java 17+

Read before changing the surefire `argLine` in `core/pom.xml`, or code that calls
`ImageIO.read` (`VideoImportCallable` thumbnail import, `BrcImageApi` poster crop).

## What happens

`day-commons-gfx` (on the test classpath through the AEM API jar, and a runtime bundle in AEM)
registers `ch.randelshofer.media.jpeg.CMYKJPEGImageReaderSpi` into the JVM-wide `IIORegistry`
(`com.day.image.ImageSupport`). Neither the API jar nor the runtime bundle sets an ordering between
it and the JDK's own JPEG reader, so which reader `ImageIO.read` picks first is not fixed.

When the CMYK reader is picked, it builds its backend by reflectively constructing
`com.sun.imageio.plugins.jpeg.JPEGImageReader`. Java 17+ does not export that package, so without
`--add-exports java.desktop/com.sun.imageio.plugins.jpeg=ALL-UNNAMED` the read fails with
`IIOException: Cannot create Sun JPEGImageReader backend` (cause `IllegalAccessException`). Java 11
only warns.

## In the unit tests

`BrcReplicationHandlerFolderSyncTest` creates DAM assets through the AEM mocks, which initialises
`ImageSupport` and registers the CMYK reader for the rest of the forked JVM. Every later
`ImageIO.read` of a JPEG in the same fork could then pick it. On the cloud profile on Java 21 this
failed `VideoImportCallableThumbnaillessVideoTest` (3) and
`BrcImageApiProxyTest.servesThePosterDirectlyWhenNoProxyIsConfigured`; each passes when run alone,
and the on-prem profile passed in the same order. A green run without the export is therefore not
evidence the decode works.

The surefire `argLine` adds the export to the forked test JVM (harmless on Java 11). That is the
fix for the test environment only.

## Production (open question)

The runtime bundle (`day-commons-gfx` 2.1.52 on both local beds) uses the same reflective
construction and also sets no ordering. A local AEM 6.5 LTS author on Java 21, started without any
`--add-exports`, listed the JDK reader before the CMYK reader in the Felix console's "GFX ImageIO
Formats" printer, and the e2e thumbnail-import and poster-crop checks passed against it. Whether a Java 21 AEM can list
the CMYK reader first after a restart, and whether AEMaaCS starts its JVM with this export, has not
been measured. If it can, a JPEG thumbnail import or poster crop fails on that instance with the
`IIOException` above.
