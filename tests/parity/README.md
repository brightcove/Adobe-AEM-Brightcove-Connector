# tests/parity

Parity evidence for the one-branch cloud + on-prem effort (`ONPREM-PARITY-PLAN.md`).

- `matrix.md` — the feature matrix, hand-maintained. Cells cite evidence files.
- `runs/<YYYY-MM-DD>/<line>/` — gitignored. Raw evidence: Playwright JSON reports,
  screenshots, curl output, bundle listings, setup-script logs. `<line>` is one of
  `cloud-<version>` / `onprem-<version>`.
- `onprem-commit-ledger.md` — Phase 3 classification of the on-prem-only commits.

Evidence files may contain the test account ID; that is why `runs/` is not committed.

## Probes

`probes/<line>/*.js` are one-off Playwright library scripts (run with
`NODE_PATH=../e2e/node_modules`, `AEM_BASE` set). They read the account id at run time
from `/bin/brightcove/accounts`. Mutation probes take their target objects from the
environment (`PARITY_VIDEO_ID_LABELS`, `PARITY_VIDEO_ID_FOLDER`) so no live-account ids
are committed.
