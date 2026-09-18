# Why the Brightcove repoinit script is deliberately plain

`org.apache.sling.jcr.repoinit.RepositoryInitializer-brightcove.config` uses only
`create path`, `create service user` and `set ACL`.

⚠️ Do not add `set properties on … end` (or other newer repoinit grammar) here. AEM 6.5.0 GA
ships `org.apache.sling.repoinit.parser 1.2.2`, which throws
`RepoInitParsingException: Encountered "set"` at that block; a parse error aborts the WHOLE
script, so on a fresh 6.5 the connector then has no `brightcove_admin` service user and no
ACLs on `/content` (measured 2026-09-17 on the local 6.5.0 test bed). The folder's title and
`cq:conf` live in `ui.content` instead (`/content/dam/brightcove_assets/jcr:content`, filter
mode `merge`, so a customer's existing folder is never overwritten).
Context: ONPREM-PARITY-PLAN.md §3 Phase 2 step 3.
