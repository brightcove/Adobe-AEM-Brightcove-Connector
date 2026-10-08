# ImageIO decoding on Java 17+

Read before changing `utils/ImageDecoder`, or adding code that decodes an image
(`VideoImportCallable` thumbnail import, `BrcImageApi` poster serve).

## The problem

`day-commons-gfx` (on the test classpath through the AEM API jar, and a runtime bundle in AEM)
registers `ch.randelshofer.media.jpeg.CMYKJPEGImageReaderSpi` into the JVM-wide `IIORegistry`
(`com.day.image.ImageSupport`). Neither the API jar nor the runtime bundle (2.1.52 on both local
beds, read with `javap`) sets an ordering between it and the JDK's JPEG reader, so which one
`ImageIO.read` picks first is not under the connector's control.

When the CMYK reader is picked, it builds its backend by reflectively constructing
`com.sun.imageio.plugins.jpeg.JPEGImageReader`. Java 17+ does not export that package, so without
`--add-exports java.desktop/com.sun.imageio.plugins.jpeg=ALL-UNNAMED` it throws
`IIOException: Cannot create Sun JPEGImageReader backend`, and `ImageIO.read` propagates it: it
never tries the next reader. Whether AEMaaCS starts its JVM with that export is not known; a local
AEM 6.5 LTS on Java 21 runs without it (and happened to list the JDK reader first).

Seen first in the unit tests: AEM-mock asset creation in `BrcReplicationHandlerFolderSyncTest`
registers the CMYK reader for the rest of the forked JVM, and on the cloud profile on Java 21 the
later JPEG decodes failed (each test passed alone).

## The fix (BGS-1600)

Every production decode goes through `ImageDecoder.read(InputStream)`: it buffers the bytes, asks
`ImageIO.getImageReaders` for every candidate, and on an `IIOException` or `RuntimeException` from
one reader (or from instantiating it) tries the next on a fresh stream. It WARNs once per failing
reader class, returns null when no reader recognises the data (as `ImageIO.read` does), and
throws the last failure only when every candidate failed.

- ⚠️ Do not call `ImageIO.read` directly for an image the connector must decode; use
  `ImageDecoder.read`. `ImageIO.write` is unaffected.
- The surefire `--add-exports` workaround was removed: with every decode (production and the
  tests' own checks) going through `ImageDecoder`, all four unit runs (cloud/on-prem x Java 11/21)
  are green without it, including the cloud Java 21 run that failed in the same test order before
  the fix (whether the real CMYK reader came first in a given run is not logged; the
  `FailingImageReader` tests are the deterministic proof).

## Pinned by

`FailingImageReader` (test helper) registers a reader that claims every input and throws, ordered
ahead of every other reader, so the check does not depend on JVM flags or registry order:

- `VideoImportCallableThumbnaillessVideoTest.aFailingFirstImageReaderDoesNotStopTheImport`
- `BrcImageApiProxyTest.aFailingFirstImageReaderStillServesThePoster`

Both fail on the code that called `ImageIO.read` (no asset created; a 500 from the servlet), on
Java 11 and 21 alike, and pass with `ImageDecoder`.
