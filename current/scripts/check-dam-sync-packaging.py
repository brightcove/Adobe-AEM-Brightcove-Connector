#!/usr/bin/env python3
"""Packaging check for the DAM -> Video Cloud sync defaults of both artifacts.

Usage: check-dam-sync-packaging.py all/target/brightcove.all-<v>.zip all/target/brightcove.all-<v>-prem.zip
(either or both; the platform is read from the file name: "-prem.zip" is on-prem).

Asserts, per docs/dam-sync-on-activation.md:
  on-prem  the embedded brightcove.ui.content.onprem package carries
           /etc/replication/agents.author/brightcove, enabled, transport brightcove://,
           under a filter in mode="merge" (an upgraded instance's agent is left alone)
           and the embedded brightcove.ui.config.onprem package carries exactly one repoinit
           config granting brightcove_admin jcr:read on /home/groups and /home/users (restricted to
           authorizable node types, rep:password denied),
           nothing broader and nothing on / (the agent's group check needs it)
  cloud    no embedded package carries any /etc/replication content, and no repoinit
           script anywhere in it mentions /home
  both     BrightcovePublishListener stays OFF: its metatype default for isEnabled is
           false and no embedded package ships an OSGi config for its PID (so on-prem
           does not handle each activation twice, agent + listener)
Prints PASS / FAIL lines and exits non-zero on any FAIL.
"""
import io
import sys
import zipfile
import xml.etree.ElementTree as ET

LISTENER_PID = "com.coresecure.brightcove.wrapper.listeners.BrightcovePublishListener"
AGENT = "jcr_root/etc/replication/agents.author/brightcove/.content.xml"
JCR = "{http://www.jcp.org/jcr/1.0}"
REPOINIT = "org.apache.sling.jcr.repoinit.RepositoryInitializer"
HOME_GRANT = "RepositoryInitializer-brightcove-onprem.config"

failures = []


def check(ok, msg):
    print(("PASS " if ok else "FAIL ") + msg)
    if not ok:
        failures.append(msg)


def walk(zf, prefix=""):
    """Yield (path, ZipFile) for this zip and every nested .zip/.jar, recursively."""
    yield prefix, zf
    for name in zf.namelist():
        if name.endswith((".zip", ".jar")):
            inner = zipfile.ZipFile(io.BytesIO(zf.read(name)))
            yield from walk(inner, prefix + name + "!")


def listener_off(packages):
    configs = [(p, n) for p, z in packages for n in z.namelist() if LISTENER_PID in n and "/config" in n]
    check(not configs, f"no OSGi config for {LISTENER_PID} is shipped {configs or ''}")
    meta = [(p, z, n) for p, z in packages for n in z.namelist()
            if n.startswith("OSGI-INF/metatype/") and LISTENER_PID in n]
    if not meta:
        check(False, "metatype for the listener found in brightcove.core (NOT MEASURED without it)")
        return
    _, z, n = meta[0]
    ad = [e for e in ET.fromstring(z.read(n)).iter() if e.tag.endswith("AD") and e.get("id") == "isEnabled"]
    got = ad[0].get("default") if ad else "absent"
    check(got == "false", f"listener metatype default isEnabled=false (got {got})")


def onprem(packages):
    hits = [(p, z) for p, z in packages if AGENT in z.namelist()]
    check(len(hits) == 1, f"exactly one embedded package carries the Brightcove agent ({[p for p, _ in hits]})")
    if not hits:
        return
    p, z = hits[0]
    check("brightcove.ui.content.onprem" in p, f"the agent comes from brightcove.ui.content.onprem ({p})")
    root = ET.fromstring(z.read(AGENT))
    content = root.find("jcr:content", {"jcr": JCR[1:-1]})
    check(content is not None, "agent has jcr:content")
    if content is not None:
        check(content.get("enabled") == "true", f'agent enabled="true" (got {content.get("enabled")})')
        uri = content.get("transportUri") or ""
        check(uri.startswith("brightcove://"), f"agent transportUri starts with brightcove:// (got {uri!r})")
        check(content.get(JCR + "title") == "Brightcove Replication Agent", "agent title")
    filt = ET.fromstring(z.read("META-INF/vault/filter.xml"))
    modes = {f.get("root"): f.get("mode") for f in filt.iter("filter")}
    check(modes.get("/etc/replication/agents.author/brightcove") == "merge",
          f"agent filter mode is merge (got {modes.get('/etc/replication/agents.author/brightcove')})")
    check(set(modes) == {"/etc/replication/agents.author/brightcove"},
          f"the on-prem package owns nothing else ({sorted(modes)})")


def repoinits(packages):
    return [(p, n, z.read(n).decode("utf-8", "replace")) for p, z in packages
            for n in z.namelist() if REPOINIT in n and "/config" in n and not n.endswith("/")]


def acl_blocks(script):
    """[(path, [privilege lines])] for each `set ACL on <path> ... end` block."""
    blocks, cur = [], None
    for line in (l.strip().strip('"').strip() for l in script.splitlines()):
        if line.startswith("set ACL on "):
            cur = (line[len("set ACL on "):].strip(), [])
        elif line == "end" and cur:
            blocks.append(cur)
            cur = None
        elif cur and line:
            cur[1].append(line)
    return blocks


def home_grant(packages):
    hits = [(p, n, s) for p, n, s in repoinits(packages) if n.endswith(HOME_GRANT)]
    check(len(hits) == 1, f"exactly one {HOME_GRANT} is shipped ({[p + n for p, n, _ in hits]})")
    if not hits:
        return
    p, n, script = hits[0]
    check("brightcove.ui.config.onprem" in p, f"the /home grant comes from brightcove.ui.config.onprem ({p})")
    blocks = acl_blocks(script)
    paths = sorted(b[0] for b in blocks)
    check(paths == ["/home/groups", "/home/users"], f"the grant covers /home/groups and /home/users only (got {paths})")
    lines = [l for _, ls in blocks for l in ls]
    allows = [l for l in lines if l.startswith("allow ")]
    check(allows and all(l.startswith("allow jcr:read for brightcove_admin restriction(rep:ntNames,")
                         for l in allows),
          f"every grant is jcr:read for brightcove_admin restricted by rep:ntNames (got {allows})")
    check(all(l.startswith(("allow ", "deny jcr:read for brightcove_admin ")) for l in lines),
          f"nothing but those grants and jcr:read denies for brightcove_admin ({lines})")
    check(any(l == "deny jcr:read for brightcove_admin restriction(rep:itemNames,rep:password)" for l in lines),
          "rep:password is denied to brightcove_admin")
    # The 6.5.0 GA parser (repoinit.parser 1.2.2) aborts the whole script on newer grammar.
    allowed = ("create service user ", "set ACL on ", "allow ", "deny ", "end", "scripts=[", "]", "")
    odd = [l for l in (x.strip().strip('"').strip() for x in script.splitlines()) if not l.startswith(allowed)]
    check(not odd, f"the grant uses only 6.5.0-GA-safe repoinit grammar {odd or ''}")
    wide = [(pp, n) for pp, n, s in repoinits(packages) for path, _ in acl_blocks(s) if path == "/"]
    check(not wide, f"no repoinit script sets an ACL on / {wide or ''}")


def cloud(packages):
    hits = [(p, n) for p, z in packages for n in z.namelist() if n.startswith("jcr_root/etc/replication")]
    check(not hits, f"cloud artifact carries no /etc/replication content {hits[:3] or ''}")
    home = [(p, n) for p, n, s in repoinits(packages) if any(b[0].startswith("/home") for b in acl_blocks(s))]
    check(not home, f"cloud artifact grants nothing on /home {home or ''}")
    extra = [p for p, _ in packages if "brightcove.ui.config.onprem" in p]
    check(not extra, f"cloud artifact does not embed brightcove.ui.config.onprem {extra or ''}")


def main(paths):
    if not paths:
        print(__doc__)
        return 2
    for path in paths:
        print(f"== {path}")
        packages = list(walk(zipfile.ZipFile(path)))
        listener_off(packages)
        if path.endswith("-prem.zip"):
            onprem(packages)
            home_grant(packages)
        else:
            cloud(packages)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
