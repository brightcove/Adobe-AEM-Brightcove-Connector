#!/usr/bin/env python3
"""Packaging check for the DAM -> Video Cloud sync defaults of both artifacts.

Usage: check-dam-sync-packaging.py all/target/brightcove.all-<v>.zip all/target/brightcove.all-<v>-prem.zip
(either or both; the platform is read from the file name: "-prem.zip" is on-prem).

Asserts, per docs/dam-sync-on-activation.md:
  on-prem  the embedded brightcove.ui.content.onprem package carries
           /etc/replication/agents.author/brightcove, enabled, transport brightcove://,
           under a filter in mode="merge" (an upgraded instance's agent is left alone)
           and exactly one embedded repoinit script, from brightcove.ui.config.onprem,
           grants brightcove_admin on /home: jcr:read and nothing else, on /home/groups
           and /home/users only (the agent's group check needs it), every allow restricted
           by rep:ntNames, only jcr:read denies (rep:password among them), each under
           those two paths. Nothing broader, nothing on /.
  cloud    no embedded package carries any /etc/replication content, and no repoinit
           script anywhere in it sets an ACL under /home
  both     no repoinit script sets an allow ACL on /
  both     BrightcovePublishListener stays OFF: its metatype default for isEnabled is
           false and no embedded package ships an OSGi config for its PID (so on-prem
           does not handle each activation twice, agent + listener)
Repoinit is found BY CONTENT (any embedded file with a `set ACL` / `set principal ACL`
statement), not by file name, and every `set ACL` form is parsed: `on <path>[, <path>]` with
`allow|deny <privs> for <principals> [restriction(..)]` lines, and `for <principal>` with
`allow|deny <privs> on <paths> [restriction(..)]` lines. A statement it cannot parse is a FAIL,
never skipped. Not covered: scripts loaded from a classpath `references=` entry whose text is
not itself in an embedded file.
Prints PASS / FAIL lines and exits non-zero on any FAIL.
"""
import io
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

LISTENER_PID = "com.coresecure.brightcove.wrapper.listeners.BrightcovePublishListener"
AGENT = "jcr_root/etc/replication/agents.author/brightcove/.content.xml"
JCR = "{http://www.jcp.org/jcr/1.0}"

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


# anchored to a statement start so prose that merely mentions `set ACL` (a README) is not a script
ACL_STMT = re.compile(r"^[\s\"]*set\s+(?:principal\s+)?ACL\b", re.MULTILINE)
OPTS = r"(?:\s+\(ACLOptions=[^)]*\))?"
SET_ON = re.compile(r"^set\s+(?:principal\s+)?ACL\s+on\s+(.+?)" + OPTS + "$")
SET_FOR = re.compile(r"^set\s+(?:principal\s+)?ACL\s+for\s+(.+?)" + OPTS + "$")
ACE_FOR = re.compile(r"^(allow|deny)\s+(.+?)\s+for\s+(.+)$")   # inside `set ACL on`
ACE_ON = re.compile(r"^(allow|deny)\s+(.+?)\s+on\s+(.+)$")     # inside `set ACL for`
RESTRICTION = re.compile(r"restriction\(([^)]*)\)")


def repoinits(packages):
    """[(package, entry, text)] for every embedded file that contains a `set ACL` statement."""
    out = []
    for p, z in packages:
        for n in z.namelist():
            if n.endswith("/") or n.endswith((".zip", ".jar", ".class", ".png", ".jpg", ".gif", ".mp4")):
                continue
            text = z.read(n).decode("utf-8", "replace")
            if ACL_STMT.search(text):
                out.append((p, n, text))
    return out


def csv(text):
    return [t.strip() for t in text.split(",") if t.strip()]


def parse_acl(script):
    """(aces, unparsed). Each ACE is {principal, effect, privs(set), path, restrictions(list)}."""
    aces, unparsed = [], []
    lines = []
    for raw in script.splitlines():
        l = raw.strip().strip(",").strip().strip('"').strip()
        if l and not re.match(r"^\"?scripts\"?\s*[=:]\s*\[", l) and l != "]":
            lines.append(l)
    mode = None  # ("on", [paths]) or ("for", [principals])
    for l in lines:
        if mode is None:
            m_on, m_for = SET_ON.match(l), SET_FOR.match(l)
            if m_on:
                mode = ("on", csv(m_on.group(1)))
            elif m_for:
                mode = ("for", csv(m_for.group(1)))
            elif ACL_STMT.search(l):
                unparsed.append(l)
            # other repoinit statements (create service user, create path, ...) carry no ACL
            continue
        if l == "end":
            mode = None
            continue
        restrictions = RESTRICTION.findall(l)
        body = re.sub(r"\s*restriction\(.*$", "", l)
        m = (ACE_FOR if mode[0] == "on" else ACE_ON).match(body)
        if not m:
            unparsed.append(l)
            continue
        effect, privs, target = m.group(1), set(csv(m.group(2))), csv(m.group(3))
        paths, principals = (mode[1], target) if mode[0] == "on" else (target, mode[1])
        for path in paths:
            for principal in principals:
                aces.append({"principal": principal, "effect": effect, "privs": privs,
                             "path": path, "restrictions": restrictions})
    if mode is not None:
        unparsed.append("(statement not closed with `end`)")
    return aces, unparsed


def all_aces(packages):
    """[(package, entry, aces)] plus every unparsed line, over every embedded ACL script."""
    scripts, bad = [], []
    for p, n, text in repoinits(packages):
        aces, unparsed = parse_acl(text)
        scripts.append((p, n, aces))
        bad += [(p, n, u) for u in unparsed]
    return scripts, bad


def under(path, roots):
    return any(path == r or path.startswith(r + "/") for r in roots)


def common(scripts, bad):
    check(not bad, f"every `set ACL` statement parses (the 6.5.0 GA repoinit parser aborts a whole script on grammar it does not know) {bad or ''}")
    wide = [(p, n) for p, n, aces in scripts for a in aces
            if a["effect"] == "allow" and a["path"] in ("/", ":repository")]
    check(not wide, f"no repoinit script allows anything on / {wide or ''}")


def home_grant(scripts, packages):
    home = [(p, n, aces) for p, n, aces in scripts if any(a["path"].startswith("/home") for a in aces)]
    check(len(home) == 1, f"exactly one embedded repoinit script sets an ACL under /home ({[p + n for p, n, _ in home]})")
    if not home:
        return
    p, n, aces = home[0]
    check("brightcove.ui.config.onprem" in p, f"the /home grant comes from brightcove.ui.config.onprem ({p})")
    others = sorted({a["principal"] for a in aces} - {"brightcove_admin"})
    check(not others, f"the /home script concerns brightcove_admin only (also: {others})")
    roots = ["/home/groups", "/home/users"]
    allows = [a for a in aces if a["effect"] == "allow"]
    denies = [a for a in aces if a["effect"] == "deny"]
    check(sorted({a["path"] for a in allows}) == roots,
          f"the grant covers /home/groups and /home/users only (got {sorted({a['path'] for a in allows})})")
    privs = sorted({x for a in allows for x in a["privs"]})
    check(privs == ["jcr:read"], f"the only privilege granted is jcr:read (got {privs})")
    unrestricted = [a["path"] for a in allows if not any(r.startswith("rep:ntNames,") for r in a["restrictions"])]
    check(not unrestricted, f"every allow is restricted by rep:ntNames {unrestricted or ''}")
    stray = [(a["effect"], a["path"]) for a in denies if not under(a["path"], roots)]
    check(not stray, f"every deny sits under /home/groups or /home/users {stray or ''}")
    deny_privs = sorted({x for a in denies for x in a["privs"]})
    check(deny_privs in ([], ["jcr:read"]), f"only jcr:read is denied (got {deny_privs})")
    check(any("rep:itemNames,rep:password" in a["restrictions"] for a in denies),
          "rep:password is denied to brightcove_admin")
    # The 6.5.0 GA parser (repoinit.parser 1.2.2) aborts the whole script on newer grammar.
    text = next(t for pp, nn, t in repoinits(packages) if pp == p and nn == n)
    allowed = ("create service user ", "set ACL on ", "allow ", "deny ", "end", "scripts=[", "]", "")
    odd = [l for l in (x.strip().strip('"').strip() for x in text.splitlines()) if not l.startswith(allowed)]
    check(not odd, f"the grant uses only 6.5.0-GA-safe repoinit grammar {odd or ''}")
    restricted = [a["path"] + " " + " ".join(a["restrictions"]) for a in aces if a["restrictions"]]
    print(f"INFO restrictions in the /home script: {restricted or 'none'}")


def cloud(packages, scripts):
    hits = [(p, n) for p, z in packages for n in z.namelist() if n.startswith("jcr_root/etc/replication")]
    check(not hits, f"cloud artifact carries no /etc/replication content {hits[:3] or ''}")
    home = [(p, n) for p, n, aces in scripts if any(a["path"].startswith("/home") for a in aces)]
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
        scripts, bad = all_aces(packages)
        check(len(scripts) > 0, "at least one embedded repoinit script was found (NOT MEASURED otherwise)")
        common(scripts, bad)
        if path.endswith("-prem.zip"):
            onprem(packages)
            home_grant(scripts, packages)
        else:
            cloud(packages, scripts)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
