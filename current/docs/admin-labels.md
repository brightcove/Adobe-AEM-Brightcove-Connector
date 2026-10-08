# Admin tool labels (`a=update_labels`)

Read before changing `BrcApi.updateLabels`, `CmsAPI.updateLabels`/`toUpdateResult`, or
`saveLabels` in the admin UI.

## Request contract

`/bin/brightcove/api.js?a=update_labels&videoId=<id>&labels=<l1>&labels=<l2>…`

- `videoId` is required; without it the answer is `{"error_code":400,"message":"videoId is required"}`.
- The `labels` values are the video's **complete** label list, sent to the CMS as
  `PATCH {"labels":[…]}`. Blank values are dropped.
- ⚠️ **No `labels` parameter at all means "clear every label"**, `PATCH {"labels":[]}`. The admin
  UI builds the query with jQuery's `$.param(…, true)`, which drops an empty array, so removing a
  video's last label arrives with no `labels` parameter. Before BGS-1600 the servlet ignored such a
  request and the UI still toasted "Labels saved" (the label stayed); for a while during BGS-1600 it
  was a 400. Do not reintroduce either: treating "absent" as "leave alone" makes the last label
  impossible to remove from the connector.

## Response contract

Success is the updated CMS video object (no `error_code`). Every failure carries `error_code` and
`message`, because the UI treats any response without `error_code` as saved:

| Upstream outcome | Response |
|---|---|
| No response (refused, proxy denied, timeout) | `502`, "No response from Brightcove" |
| HTTP error with the CMS's own `error_code`/`message` | those, plus `status` |
| HTTP error otherwise | the HTTP status |
| 2xx/3xx with no body, a redirect, `[]`, a JSON scalar, any non-object | `502`, "Unexpected response from Brightcove (…)" |
| Exception inside the connector | `500`, "Could not update the labels" |

Exception and connection-failure text stays in the log: it can name the proxy host and port.

## Pinned by

- `BrcApiUpdateLabelsTest`: missing `videoId` is a 400; no `labels` parameter clears (calls the
  CMS with an empty list); blank values are dropped.
- `CmsApiUpdateLabelsTest`: an empty list PATCHes `{"labels":[]}` over a real loopback server;
  every non-video shape is a 502; failure text is not returned.
- e2e `bcon-admin-regressions.spec.js` "§3b-1": removes the last label of a throwaway video in
  the admin UI and re-reads the video from the CMS to confirm `labels` is empty.
