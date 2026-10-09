# WCM Core Components are not embedded

Read before adding anything `com.adobe.cq:core.wcm.components.*` to a POM, or before changing the
`<embeddeds>` of `all/pom.xml`.

## The rule

Neither artifact (`brightcove.all-<v>.zip` for AEMaaCS, `brightcove.all-<v>-prem.zip` for AEM 6.5)
embeds the Core Components bundle (`core.wcm.components.core`) or their content/config packages
(`core.wcm.components.content`, `core.wcm.components.config`). The connector uses Core Components
but relies on the copy the platform already has.

Pinned by `scripts/check-dam-sync-packaging.py` (`no WCM Core Components embedded`, run on both
zips by the CI `packaging-check` job): it fails on any embedded `core.wcm.components.*` file, any
Core Components `pom.xml` inside a nested archive, and any content under `/apps/core`.

## Why

- **AEMaaCS ships them.** Core Components are part of the product (`/libs/core/wcm/components` plus
  the product's own bundle and OSGi configs) and are kept on the latest release by Adobe. The AEM
  project archetype embeds them only when `aemVersion != "cloud"`, and Adobe's Core Components
  documentation says a cloud build that includes them again is warned about now and will fail the
  pipeline in a future release.
- **An embedded copy does not replace the product's on cloud; it runs next to it.** From 6.1.11 through 7.3.x the
  cloud zip carried 2.27.0. On the AEMaaCS SDK that bundle installed from the connector's install
  folder and went Active alongside the product's newer bundle, so every Core Components service was
  registered twice. Its config package wrote `/apps/core/wcm/config`, which the OSGi installer
  ranks above the product's own copies of the same configurations, so older values overrode newer
  ones site-wide.
- **On 6.5 it replaced the customer's stock bundle.** The OSGi installer treated the higher version
  as an upgrade of the instance's stock Core Components and the new bundle did not resolve on older
  service packs. See `ONPREM-PARITY-PLAN.md` §3 Phase 2 step 4.
- **Security scanners read the POMs inside the embedded jar and ignore scope.** BGS-1746: a scan of
  the 7.2.0 cloud package reported 4 Critical and 3 High findings, all from test-scope or `provided`
  dependencies declared in the Core Components and jsoup POMs, none in connector code.

## What the connector depends on

These resolve against the platform's Core Components, so a target instance must have them (every
AEMaaCS environment does; on 6.5 see `onprem-upgrade-6.0-to-7.md`):

- `core/wcm/components/page/v3/page`: `sling:resourceSuperType` of the `brightcoveplayer` and
  `brightcoveIframe` page components (`ui.apps`).
- `core/wcm/components/container/v1/container`: the root/structure of the four templates and template
  types under `ui.content/.../conf/brightcove/settings/wcm/`.
- Context-aware configs for `DataLayerConfig` and `PdfViewerCaConfig` under
  `conf/brightcove/_sling_configs` (classes in the platform's Core Components bundle).

No connector Java class imports Core Components (`core/pom.xml` has no dependency on them).

## Alternatives considered

- **Bump the embedded version (2.33.0, the latest at the time).** Rejected: it clears only the jsoup
  (Jetty) and commons-io findings. logback 1.2.13, guava 15.0 and commons-collections 3.2.1 are
  unchanged in 2.33.0's dependency tree, so the scan still fails, and the duplicate-bundle and
  config-override problems above remain.
- **Embed on cloud only** (the 7.3.x layout). This is what produced the duplicate bundle and the
  scanner findings.

## Upgrading an instance that has the old embed

The `all` package owns `/apps/brightcove-packages` (filter mode replace), so installing a release
without the embed removes the three Core Components files from the install folder. The JCR
installer then uninstalls the old bundle AND the old config sub-package (its `/apps/core/wcm/config`
goes with it), and the product's own Core Components configurations apply again. No manual
cleanup of content. Seen on the AEMaaCS SDK author and publish, stepping 7.3.8 to 7.4.0.

⚠️ On a long-lived SDK, restart the product's Core Components bundle
(`com.adobe.cq.core.wcm.components.core`) once after that upgrade. Removing the duplicate bundle
from a running instance left Sling Models without an adapter factory for the Core Components
models, so every page built on them (the connector's page components and templates included)
returned 500 with `Could not yet find an adapter factory for the model interface
com.adobe.cq.wcm.core.components.models.Page`. A stop/start of that bundle cleared it on both
tiers. AEMaaCS does not hit this: each pipeline deploy starts a fresh image, so there is no live
bundle removal.
