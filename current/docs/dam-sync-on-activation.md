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
