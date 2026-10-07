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

Unchanged since 6.0.12. Not a code change: recorded so a quiet "nothing synced" on-prem
is checked against the group config first. Observed 2026-10-07 on a 6.5 LTS author
(`tests/parity/matrix.md` row 31).

## How it is proved

- `scripts/check-dam-sync-packaging.py <cloud zip> <prem zip>`: the `-prem` zip carries the
  agent, enabled, `brightcove://`, under a merge filter, and nothing else in that package;
  the cloud zip carries no `/etc/replication`; neither ships a listener config, and the
  listener's metatype default is `isEnabled=false`. Negative control: run against a
  `-prem` zip built before `ui.content.onprem` existed, it fails on the agent check.
- `core/.../listeners/BrightcovePublishListenerGateTest`: the listener does nothing unless
  enabled and on author.
- Live: `tests/e2e/specs/dam-publish-tier.spec.js`, run through the agent path on an
  on-prem author and through the listener path on a cloud author.
