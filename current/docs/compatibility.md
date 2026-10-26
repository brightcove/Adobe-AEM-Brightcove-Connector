# Compatibility matrix: connector x AEM x Java

Latest releases: 7.4.1-cloud and 7.4.1-prem (2026-10-14), 6.0.12-prem. Changes merged to
`cloud-master` after 7.4.1 (7.4.3) are not released yet and are marked as such.

| Connector | AEM | Java | Status | Measured basis |
|---|---|---|---|---|
| 7.4.x-cloud | AEM as a Cloud Service | as provided by the service | Supported | Cloud Service is the target of the `*-cloud` artifact |
| 7.4.x-prem | AEM 6.5 LTS | 21 | Supported | Measured on 6.5 LTS GA (`cq-quickstart-6.6.0`) on Java 21 |
| 7.4.3-prem or later (unreleased) | Classic AEM 6.5, service pack 6.5.22 | not stated | Supported once released | Verified installing on 6.5.22 |
| 7.4.0-prem, 7.4.1-prem (released) | Classic AEM 6.5 service packs | not stated | Does not install | Measured on 6.5.22: classic 6.5 ships FileVault 3.2.8, which rejects the package's `merge_properties` filter mode. Fixed in 7.4.3 |
| 7.4.x-prem | Classic AEM 6.5.0 GA, no service pack | not stated | Not supported: the instance fails to start on its next restart | The package's repoinit configuration fails on `jcr.repoinit` 1.1.8. Not reproduced on 6.5.22 |
| 7.4.3-prem or later | Classic AEM 6.5, service packs between GA and 6.5.22 | not stated | Not measured | Not yet tested (6.5.21 testing is in progress) |
| 6.0.x-prem (latest 6.0.12-prem) | Classic AEM 6.5 | not stated | Legacy, classic 6.5 only | Long-standing release line; not re-measured for this matrix |
| 6.0.x-prem | AEM 6.5 LTS, or any AEM on Java 17 or 21 | 17, 21 | Not supported | Metadata edits to existing videos do not sync from AEM to Brightcove: the 6.0.x PATCH path fails with `IllegalAccessError`, while install and uploads appear to work |

## Notes

- **Classic AEM 6.5 needs 7.4.3-prem or later.** For classic 6.5 the tested service pack is
  6.5.22. Earlier service packs are not verified, and AEM 6.5.0 GA without a service pack is not
  supported (see the table). The released 7.4.0-prem and 7.4.1-prem do not install on classic
  6.5 service packs.
- **AEM 6.5 LTS needs a 7.4.x-prem build.** Do not run 6.0.x there.
- **WCM Core Components (page v3, container v1) must already be installed** on any on-premise
  instance for 7.x. The connector does not ship them on-premise
  (`core-components-not-embedded.md`). AEM as a Cloud Service provides them.
- Upgrading an existing 6.0.x install to 7.4.x-prem: `onprem-upgrade-6.0-to-7.md`. Install the 7.x
  package over 6.0.x in place; do not uninstall 6.0.x first.
- A row marked "Not measured" is a gap in testing, not a claim that it works or fails.
