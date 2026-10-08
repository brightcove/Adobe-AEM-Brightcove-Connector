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
  the listener enabled would process every activation twice. The listener now guards that
  itself (next section).

## The agent and the listener together

The merge filter creates the agent whenever it is absent, enabled. Two upgrade cases follow:

- **(a) A 7.x-prem install with no agent node that syncs through the listener** (an admin
  enabled the listener because no agent was there). Installing this package adds the agent,
  enabled, next to the listener: both would handle every activation, and two handlers racing
  on a new asset is the duplicate-video pattern of BGS-1705.
- **(b) A 6.0.x install whose admin deleted the agent** (to turn sync off, or because they
  moved to the listener). Installing this package brings the agent back, enabled.

The guard: when the listener is enabled on author and `AgentManager` reports an **enabled**
agent whose transport URI starts with `brightcove://`, the listener logs a WARN naming the
agent and skips the event; the agent handles it. A disabled agent, no such agent, or no
`AgentManager` (AEMaaCS) leaves the listener in charge. `BrightcovePublishListenerGateTest`
pins both branches (enabled agent: no service resolver is even requested; disabled or absent:
the event is processed).

What the guard does not do: in case (b) the restored agent is live, so sync turns back on
after the upgrade. An admin who wants sync off must disable the agent again (below); the merge
filter will not re-enable it after that. In case (a) the agent wins; to keep the listener
instead, disable the agent.

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

## On-prem: `allowedGroups` and the replicating user's groups

The agent path authorizes the **replicating user**, not the service user:
`BrcReplicationHandler.isAuthorized` walks `Authorizable.memberOf()` and looks for a
group in the account's `allowedGroups`.

`allowedGroups=[everyone]` **does** authorize on a fresh 7.4.0-prem install. Measured
2026-10-07 on a fresh 6.5 LTS author (Oak 1.68.1, Java 21, `/home` read grant, `admin` also a
declared member of `administrators`): with only `allowedGroups` changed to `[everyone]`, row 31
passed twice, `brightcove.log` showed the handler's `Path:` line and no `Not authorized`
(`tests/parity/runs/2026-10-07/everyone-fresh/`). An earlier version of this section said
`memberOf()` never returns `everyone` and that `everyone` could never authorize. That was
wrong. The mechanism: in Oak 1.68 `AuthorizableImpl.memberOf()` merges stored membership with
the `DynamicMembershipProvider`, and Oak's default `EveryoneMembershipProvider` returns the
`everyone` group for every authorizable except `everyone` itself (read from the bytecode of the
bed's `oak-core` bundle; older Oak lines were not inspected, though the accounts servlet,
which also uses `memberOf()`, matched `everyone` on 6.5.0 GA). The mocked unit test
`noMatchingGroupIsNotAuthorized` only pins "no listed group matches", not anything about
`everyone`.

Why the earlier :4702 run failed: not `everyone`. The `brightcove_admin` service resolver
could not read `/home/users` and `/home/groups` (the `/home` read grant was missing), so
`getAuthorizable()` returned null and every user was `Not authorized` whichever group was
configured.

- ⚠️ A failed check is silent: `Not authorized` at DEBUG, and the handler returns
  `ReplicationResult.OK`, so the activation succeeds and the asset never reaches Video
  Cloud.
- `everyone` means every user, so it turns the group check off. Prefer a group the
  publishing users are declared members of (e.g. `administrators` or a customer group)
  if activation should be restricted.

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
  ⚠️ On a FRESH install only. An in-place upgrade keeps the 6.0.x root entry: nothing in
  7.x owns `/rep:policy` (`onprem-upgrade-6.0-to-7.md`).
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
  enabled and on author, and skips when an enabled `brightcove://` agent exists.
- Live: `tests/e2e/specs/dam-publish-tier.spec.js`, run through the agent path on an
  on-prem author and through the listener path on a cloud author.
