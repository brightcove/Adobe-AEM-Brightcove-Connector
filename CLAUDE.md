# AEM Brightcove Connector

Before claiming any change works, run the gate and report its verdicts (PASS, FAIL, NOT_MEASURED). A change is done when the gate says so, not when it compiles.

```bash
tests/e2e/pre-qa-gate.sh --unit-only --platform both --json /tmp/gate.json   # Java change, ~20s, no AEM
# full gate on a running local AEM (source your credentials env file first, same shell):
tests/e2e/pre-qa-gate.sh --platform cloud --json /tmp/gate-cloud.json
tests/e2e/pre-qa-gate.sh --platform both --json /tmp/gate-both.json
```

- Before editing tests or the gate, read `docs/testing.md` (stages, verdicts, how to pin a defect, how to compare against a release baseline, feature-to-test map).
- If you change behaviour a doc describes, update that doc in the same PR (`docs/testing.md`, and the feature docs in `current/docs/`).
- A repo skill drives the whole loop: `.claude/skills/connector-verify/SKILL.md`.
- Public repo: no customer names, real account IDs, personal paths, internal hostnames or secrets in commits, tests, fixtures or docs. Credentials come from the environment only.
