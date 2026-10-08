# Credentials in logs

Read before adding or changing a log statement in `core` that touches an HTTP request, a
response body, headers, or an OAuth token.

The shipped `LogManager~brightcove` config enables DEBUG on non-prod runmodes, so anything logged
at DEBUG or TRACE reaches `brightcove.log`. Until BGS-1600 the connector logged the OAuth Basic
header, `Base64(client_id:client_secret)`, the OAuth response body (with the access token) and the
Bearer token at DEBUG.

## Rules

- Never log a client secret, an `Authorization` value, `Base64(id:secret)`, a Bearer token, an
  `access_token` value, an AWS key, secret or session token, or an `X-Amz-*` signing parameter, at
  any level.
- Route every header you log through `LogRedactor.header(key, value)` (scheme only: `Basic ***`,
  `Bearer ***`) and every request or response body that might carry credentials through
  `LogRedactor.body(text)`. The log line stays; only the value goes.
- `HttpServices.executePost` handles the OAuth call, so its payload, header, and body logs are all
  redacted there. A new generic HTTP helper needs the same treatment.
- `Platform` traces every API response body through `LogRedactor.body`. That matters for
  `getDI_API`: the DI `upload-urls` response carries `access_key_id`, `secret_access_key`,
  `session_token`, and `signed_url`/`api_request_url` with `X-Amz-Credential`,
  `X-Amz-Security-Token` and `X-Amz-Signature` in the query. The `createAssetS3` result and the
  `S3RESP`/`S3URLRESP` trace lines in `ServiceUtil` and `BrcApi` carry the same URLs and go
  through `body` too.

## How `body` masks

- Text that parses as a JSON object or array is masked by walking the tree: any field whose name,
  ignoring case, `_` and `-`, is a sensitive key gets `"***"`, and every other string value goes
  through the text patterns (a pre-signed URL inside `signed_url`). Parsing is what stops an
  escaped quote leaking a tail: a regex on `{"password":"ab\"cdS"}` used to leave `cdS"`.
- Anything else (form bodies, query strings, `key: value` header lines, a log prefix followed by
  JSON) goes through one pattern: the key in snake, camel or kebab case, optionally in `"` or `'`,
  then `:`, `=` or `%3D`, then a quoted value (escapes included) or an unquoted one up to `&`,
  `;`, `,`, whitespace, a quote, `}`/`]`, `%26` or `%3B`.
- Sensitive keys: `access_token`, `refresh_token`, `client_secret`, `password`, `access_key_id`,
  `secret_access_key`, `session_token`, `X-Amz-Security-Token`, `X-Amz-Signature`,
  `X-Amz-Credential`. Headers: `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`,
  `X-Api-Key` and the three `X-Amz-*` above.

## Pinned by

`CredentialLogRedactionTest`:

- `oauthLoginAndCmsRequestsNeverLogTheSecretTheBasicValueOrTheToken` runs a real OAuth login and
  CMS reads against a loopback HTTPS server with a known fake secret, Basic value and token.
- `uploadUrlsResponseNeverLogsAwsKeysSessionTokenOrSignature` drives `CmsAPI.getIngestURL`
  against a loopback DI endpoint returning an upload-urls shaped body with fake AWS values.
- Both assert none of the fake values appears in any captured log event: every level, message,
  arguments, and the full printed stack trace of any attached throwable. Both fail on the pre-fix
  code.
- `redactorMasksEverySpellingAndEncodingOfACredential` pins each spelling and encoding above,
  including the escaped-quote case.

A new log site that leaks is only caught if a code path in that test reaches it: extend the test
with the call that exercises it. The `S3RESP`/`S3URLRESP`/`createAssetS3` lines need the OSGi
service registry and are not driven by the test; they are covered only by the `body` cases.

Not redacted on purpose: CMS request and response bodies other than the fields above (video
metadata, not credentials), and URLs logged as URLs. `S3UploadUtil` logs byte counts and status
only, never the URL; it does log a PUT `IOException` with its stack trace as-is, and
`createAssetS3` logs any exception the same way. JDK messages for connect failures name the host,
not the query, but no test pins that. The request URL lines in `HttpServices` print the target URL as given;
nothing the connector sends today puts credentials in it.
