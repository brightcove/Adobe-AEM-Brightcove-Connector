# Processing of imported video assets (BGS-1747)

Read before changing how `VideoImportCallable` creates DAM assets, `AssetProcessingRequester(Impl)`,
or the core `onprem` profile's compiler excludes.

## The problem

The Brightcove -> DAM import (`/bin/brightcove/dataload`, the asset-integrator cron) creates each
video's asset with `AssetManager.createAsset` and commits. What happens next depends on the platform:

| Platform | Who processes the new asset | Result before the fix |
|---|---|---|
| AEM 6.5 | DAM Update Asset workflow launcher on the original rendition | `processed` in seconds |
| AEMaaCS SDK (local) | SDK-only launchers `asset_processing_on_sdk_create` / `_mod` (an in-JVM stand-in for Asset Compute) | `processed` in seconds |
| AEM as a Cloud Service | Asset Compute, started only by the direct-binary "complete upload" step or a reprocess | `unProcessed` until the stuck-asset watchdog, about 60 minutes |

Until processing runs on AEMaaCS, the asset also has no `dc:format`. The Assets side panel's
"Brightcove Videos" finder (`clientlib-assetfinder`) queries `/bin/wcm/contentfinder/asset/view.html`
with a video `mimeType` filter, which matches `dc:format`, so the new video is missing from the panel
for that hour. That is the reported symptom.

Adobe's position: every binary-creating `AssetManager` method is `@Deprecated` in the AEMaaCS API,
"Create original" through the Java/JCR APIs is listed as not supported, and the supported
direct-binary upload is to be run "in an external application and not within the Experience Manager
JVM". There is no public Java API to request processing.

## The fix

1. **`dc:format` at creation, both platforms.** `VideoImportCallable.setFormatIfAbsent` writes the MIME
   type onto the new asset's metadata right after `createAsset` (which has already saved the asset) and
   commits it with the import's first commit, so the side panel lists it at once. Processing
   writes the same value later. Pinned by
   `VideoImportCallableAssetProcessingTest.aNewlyImportedAssetCarriesTheFormatTheSidePanelFiltersOn`.
2. **A processing request after the import's last write, cloud build only.** `AssetProcessingRequesterImpl`
   sends what the Assets UI "Reprocess Assets" action sends (`POST /bin/asynccommand`,
   `operation=PROCESS`, `asset=<path>`) as an in-process Sling request on the import's service resolver
   (`SlingRequestProcessor` + the Sling request builder), so no credentials are involved.
   - Only when the asset reads `dam:assetState=unProcessed`; anything else (already processing, state
     absent) is left alone, so a future AEMaaCS that processes `createAsset` itself is not run twice.
     Pinned by `AssetProcessingRequesterImplTest`.
   - Never throws into the import: a refused or failing request is a WARN and the watchdog still
     processes the asset later. The connector logs to `logs/brightcove.log` (Debug on default run modes)
     but at Error on `prod`, so on a production tier that WARN is not written.
   - Success is judged by the HTTP status of the in-process response only; the response body is not
     parsed.
   - Called from `VideoImportCallable` after `ServiceUtil.updateAsset`, never for an asset that already
     existed. Pinned by `VideoImportCallableAssetProcessingTest`.

### Cloud only, by build

- ⚠️ The core `onprem` profile excludes `AssetProcessingRequesterImpl` (and its test) from compilation.
  On 6.5 the DAM Update Asset launcher already processes the asset, so a PROCESS request would run it
  twice; and the class uses the Sling request-builder API (Sling API 2.25+), which the local 6.5.0 GA
  floor jar does not have.
- The servlet and the cron component hold an OPTIONAL, greedy reference; in the `-prem` bundle it stays
  empty and the import behaves as before.
- Packaging check (on a `clean` build: the compiler exclude does not delete a class a previous cloud
  build left in `target/classes`): the cloud core jar contains the class, its DS component and imports of
  `org.apache.sling.api.request.builder` and `org.apache.sling.engine`; the `-prem` jar contains none of them.
- Unit tests need the real `org.apache.sling.api` (test scope in core/pom.xml): the platform API jars
  ship `Builders` without its implementation classes.

## Alternatives rejected

- **Direct Binary Upload from the import.** Adobe's supported upload, but Adobe says to run it outside
  the AEM JVM, and the SDK's `initiateUpload` returns no upload URIs, so it cannot be exercised locally
  and would need a fallback path anyway.
- **A launcher plus the `AssetProcessorProcess` workflow step** (the AEMaaCS step that submits to Asset
  Compute). Config-only, but its label reads "Only use upon recommendation from Adobe engineering".
  Worth revisiting if Adobe sanctions it; it would replace the requester.
- **`dc:format` alone.** Fixes the panel but leaves the asset unprocessed (no renditions) for the hour.

## How it is verified

`tests/parity/probes/cloud/bgs1747-asset-processing-probe.js` (guards: loopback AEM, `e2e-throwaway-*`
video only) creates a throwaway video, runs a dataload, and checks: the asset is created; the side panel
query lists it; the processing request count (one accepted `PROCESS` command from the connector's
service user on AEMaaCS, none on 6.5); and the asset reaches `processed`.

- To make the local SDK behave like AEMaaCS, disable its two `asset_processing_on_sdk_*` launchers with
  a `/conf/global/settings/workflow/launcher/config/<name>` copy set to `enabled=false`, and delete the
  copies afterwards. With them disabled the SDK has no Asset Compute behind the PROCESS command, so the
  probe reports the last check NOT MEASURED there by design.
- ⚠️ The end state "processed by Asset Compute" can only be measured on a real AEMaaCS environment
  (an RDE is enough): run the probe with `--aemaacs --allow-remote`, a Developer Console token in
  `AEM_TOKEN` and the author log captured to `--log-file` (usage in the probe header).
- The probe gives its throwaway video remote poster/thumbnail images (this repo's placeholder JPEG),
  so the import adds the connector's `brc_poster.png` / `brc_thumbnail.png` renditions and check 5
  can show whether processing keeps them. Kept on the stock SDK and on 6.5.
- On a stock SDK the request check accepts 0 or 1 requests: the SDK's launcher and the requester race,
  and the requester stands down once the asset reads `processing`.
- On a stock SDK (launchers on), when the launcher has not marked the asset yet, a PROCESS command is
  sent too; the SDK leaves that job in "processing" forever. Dev-only noise; AEMaaCS has no such launcher.
- The probe counts the connector's per-asset "Requested processing for <path>" line in
  `/logs/brightcove.log` and AEM's accepted `PROCESS` commands from the service user (`AsyncJobInitializer`,
  which does not name the asset) in `/logs/error.log`. Appender names need the leading slash; a wrong
  name still answers HTTP 200 ("No appender with name ..."), which the probe reports as NOT MEASURED.
