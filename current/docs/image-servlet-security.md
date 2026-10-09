# Image servlet (`BrcImageApi`): remaining fetch surface

Read before changing `BrcImageApi` (`/bin/brightcove/image`, `/bin/services/brightcove/image`,
`/bin/services/brightcove/cache/image`). Sibling of `credential-logging.md`.

## What it does

`GET ?id=<video>&key=<account>` (or the `/<key>/<id>.jpg` suffix form) looks up the video's poster
through the CMS API with the account's credentials, fetches the poster URL through
`HttpServices.getRemoteBinary` (so the configured proxy applies), decodes it with `ImageIO` and
re-encodes it as JPEG.

BGS-1600 narrowed it: the fetch used to be `ImageIO.read(URL)`, which follows any scheme the JDK
supports and bypasses the proxy; now only `http`/`https` is fetched, through the proxy, and
failures return a JSON `{"error_code", "message"}`.

## Remaining surface (pre-existing, unchanged by BGS-1600)

- **No host allowlist.** Whatever URL the CMS `poster.src` holds is fetched server-side,
  including a private or link-local address if someone with CMS write access set one.
- **No size cap.** The whole response body is read into memory and then fully decoded by
  `ImageIO`; a very large or decompression-heavy image costs heap and CPU per request.
- **No per-account authorization on `key`.** Any caller that can reach the servlet path can ask
  for any configured account's posters; the servlet does not check the caller against the
  account's `allowedGroups`. Access is governed only by whatever dispatcher or Sling
  authentication rules front the path.

Recorded, not fixed (pre-existing behaviour). Closing any of these is a behaviour change for
consumers of the servlet and needs its own ticket.
