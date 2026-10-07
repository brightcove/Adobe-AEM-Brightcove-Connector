# Credentials in logs

Read before adding or changing a log statement in `core` that touches an HTTP request, a
response body, headers, or an OAuth token.

The shipped `LogManager~brightcove` config enables DEBUG on non-prod runmodes, so anything logged
at DEBUG or TRACE reaches `brightcove.log`. Until BGS-1600 the connector logged the OAuth Basic
header, `Base64(client_id:client_secret)`, the OAuth response body (with the access token) and the
Bearer token at DEBUG.

## Rules

- Never log a client secret, an `Authorization` value, `Base64(id:secret)`, a Bearer token or an
  `access_token` value, at any level.
- Route every header you log through `LogRedactor.header(key, value)` (scheme only: `Basic ***`,
  `Bearer ***`) and every request or response body that might carry credentials through
  `LogRedactor.body(text)`. The log line stays; only the value goes.
- `HttpServices.executePost` handles the OAuth call, so its payload, header, and body logs are all
  redacted there. A new generic HTTP helper needs the same treatment.

## Pinned by

`CredentialLogRedactionTest` runs a real OAuth login and CMS reads against a loopback HTTPS server
with a known fake secret, Basic value and token, and asserts none of them appears in any captured
log event (every level, message, arguments, stack traces). It fails on the pre-fix code. A new log
site that leaks is only caught if a code path in that test reaches it: extend the test with the
call that exercises it.

Not redacted on purpose: the CMS request and response bodies (video metadata, not credentials),
and URLs. Pre-signed S3 upload URLs carry a signature in the query and are logged in
`S3UploadUtil` and the `HttpServices` URL lines; they are short-lived but are credentials in
effect. Treat that as open if the logs leave the host.
