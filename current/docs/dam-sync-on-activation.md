# DAM to Video Cloud sync on activation: defaults per line

Which mechanism pushes an activated DAM asset to Video Cloud, whether it is on after a
fresh install, and why. Read before changing `ui.content.onprem`, the
`BrightcovePublishListener` config, or `BrcReplicationHandler`. If you change what this
describes, update it in the same PR.

## The rule: whatever each line shipped historically

| Line | Mechanism | Default | History |
|---|---|---|---|
| On-prem (`-prem`) | Replication agent `/etc/replication/agents.author/brightcove`, transport `brightcove://`, handled by `webservices/BrcReplicationHandler` | **on** | Shipped enabled in 5.x - 6.0.12-prem. Removed in `03edc1a` (2020-07) as "extraneous", re-added in `2661f22` (2020-12) because upload sync needs it. |
| Cloud | `listeners/BrightcovePublishListener` (OSGi EventHandler, replication + Sling Distribution topics) | **off** (opt-in) | The agent was dropped in the first cloud commit `386fbfa` (2021-05). The listener's `isEnabled` metatype default is false and no config has ever shipped, through 7.2.3-cloud. |

The unified 7.4.0 build briefly shipped neither on-prem, which silently turned sync off
on a FRESH on-prem install. Upgrades hid it, because 6.0.x had already created the agent
node and nothing removed it (the same masking pattern as the thumbnail ACL in
`README.md`). `ui.content.onprem` restores it.

## How it is packaged

- `ui.content.onprem` is built and embedded only under `-Daem.platform=onprem` (parent
  and `all/` `onprem` profiles). The cloud artifact never carries `/etc/replication`.
- ⚠️ Its filter is `mode="merge"`: the agent is created when absent and never touched
  when present. 6.0.x owned the node in replace mode, so an upgraded instance already has
  it, possibly edited (transport, user, enabled flag). `replace` or `update` would
  overwrite those edits; dropping the filter would leave fresh installs without sync.
- The agent XML is the 6.0.12 definition minus instance state (`jcr:uuid`,
  `cq:lastReplicated*`, mixins). The handler matches only the `brightcove://` prefix; the
  host in the URI is unused.
- ⚠️ `BrightcovePublishListener` stays disabled on BOTH lines. On-prem with the agent AND
  the listener enabled would process every activation twice.

## Turning sync on (cloud) or off (on-prem)

- Cloud: OSGi config PID `com.coresecure.brightcove.wrapper.listeners.BrightcovePublishListener`,
  `"isEnabled": true`, on the author (`config.author`). The listener ignores events on
  publish regardless.
- On-prem: disable the agent (Tools > Deployment > Replication > Agents on author >
  Brightcove Replication Agent > Edit > uncheck Enabled). Reinstalling the package will not
  re-enable it (merge filter).

## A DAM move alone does not move the video

The video's Video Cloud folder follows the asset **only when the asset is activated
again**. Moving an asset between DAM folders changes nothing in Video Cloud by itself.

- `listeners/BrightcoveMoveListener` is a leftover template: it logs `Listened <path>`
  and has no Brightcove behaviour (`BrightcoveMoveListenerNoOpTest` pins that).
- The re-activation that does move it goes through `activateModified` ->
  `FolderSyncUtil.syncFolder` on whichever publish path is live
  (`core-folder-sync.md`).
- ⚠️ Moving an asset back to the **account root** and re-activating it leaves the video
  in its previous Video Cloud folder: the account-root branch returns without calling
  `removeVideoFromFolder`, because nothing records which folder the video was in.
  Measured on a clean root 2026-10-07 (`tests/parity/matrix.md` row 44).
- Pinned as `test.fixme` in `tests/e2e/specs/dam-publish-tier.spec.js` ("move alone",
  "moving an asset back to the account root"). Not a fix: documented behaviour.

## On-prem: `allowedGroups` must name a declared group, not `everyone`

The agent path authorizes the **replicating user**, not the service user:
`BrcReplicationHandler.isAuthorized` walks `Authorizable.memberOf()` and looks for a
group in the account's `allowedGroups`. `memberOf()` returns declared (and inherited)
memberships only; it **never** includes the dynamic `everyone` group. So:

- `allowedGroups=[everyone]` can never authorize anyone on this path, even `admin`.
- ⚠️ It fails silently: `Not authorized` at DEBUG, and the handler returns
  `ReplicationResult.OK`, so the activation succeeds and the asset never reaches Video
  Cloud.
- Name a group the publishing users are declared members of (e.g. `administrators`
  or a customer group).

⚠️ A declared group is necessary but not sufficient: the lookup runs on the
`brightcoveWrite` service resolver (`brightcove_admin`), so that user must be able to read
the replicating user and its groups. If it cannot, `getAuthorizable()` returns null and the
check fails before any group is compared (same silent `Not authorized`). Measured
2026-10-07 on 6.5 LTS: with only the shared repoinit (read on `/content`), every
activation by a declared `administrators` member was `Not authorized`.

### The on-prem `/home` read grant (`ui.config.onprem`)

The `-prem` package therefore ships a second repoinit factory config,
`/apps/brightcove-onprem/osgiconfig/config/org.apache.sling.jcr.repoinit.RepositoryInitializer-brightcove-onprem.config`:
`jcr:read` for `brightcove_admin` on `/home/groups` and `/home/users`, nothing else.

- It replaces what 6.0.12-prem got from `ui.apps` `jcr_root/_rep_policy.xml` (the policy
  on `/`), which granted `brightcove_admin` read, `rep:write`, replicate, version, lock and
  access-control rights on the whole repository (and so on `/home`). That was far wider than the check needs; this is read on two subtrees only.
- On-prem only: the cloud artifact never embeds `ui.config.onprem` (AEMaaCS has no
  `brightcove://` agent, and the listener does no group check).
- ⚠️ Its own filter root `/apps/brightcove-onprem`, not `/apps/brightcove/osgiconfig`:
  `ui.config` owns that root in replace mode and would delete this file whenever it
  installed after it.
- ⚠️ Plain grammar only (`create service user`, `set ACL on … allow … end`): the 6.5.0 GA
  repoinit parser aborts the whole script on newer syntax (`ui.config/.../README-repoinit.md`).
  It repeats `create service user brightcove_admin` because the two scripts run in no
  guaranteed order and a grant to a missing principal fails.
- Measured 2026-10-07 on 6.5 LTS: after install, `/home/groups` and `/home/users` each carry
  a `rep:GrantACE` for `brightcove_admin` with `jcr:read`, and every agent-path activation
  in `dam-publish-tier.spec.js` was authorized and handled (`tests/parity/matrix.md` rows 31-33, 43, 44).

The `memberOf()` behaviour is unchanged since 6.0.12 and not a code change: check the group
config first when on-prem quietly syncs nothing. `BrcReplicationHandlerAuthorizationTest`
pins the check (null authorizable, declared match, no match, no UserManager).

## How it is proved

- `scripts/check-dam-sync-packaging.py <cloud zip> <prem zip>`: the `-prem` zip carries the
  agent, enabled, `brightcove://`, under a merge filter, and nothing else in that package;
  it carries exactly one `/home` grant, from `ui.config.onprem`, read-only for
  `brightcove_admin` on `/home/groups` and `/home/users`, in 6.5.0-GA-safe grammar, and no
  repoinit sets an ACL on `/`; the cloud zip grants nothing on `/home` and does not embed
  `ui.config.onprem` (negative control for the grant: a `-prem` zip built before it fails);
  the cloud zip carries no `/etc/replication`; neither ships a listener config, and the
  listener's metatype default is `isEnabled=false`. Negative control: run against a
  `-prem` zip built before `ui.content.onprem` existed, it fails on the agent check.
- `core/.../listeners/BrightcovePublishListenerGateTest`: the listener does nothing unless
  enabled and on author.
- Live: `tests/e2e/specs/dam-publish-tier.spec.js`, run through the agent path on an
  on-prem author and through the listener path on a cloud author.
