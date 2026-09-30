#!/usr/bin/env python3
"""Resolve + download the transitive AAR/JAR closure for Media3 from Maven repos."""
import os, re, sys, urllib.request, xml.etree.ElementTree as ET

GMAVEN = "https://dl.google.com/dl/android/maven2/"
CENTRAL = "https://repo1.maven.org/maven2/"
OUT = os.path.expanduser("~/workspace/zyne-iptv/libs")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def repo_for(group):
    return GMAVEN if group.startswith("androidx.") else CENTRAL


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=40) as r:
        return r.read()


def text(el, tag):
    c = el.find("m:" + tag, NS)
    return c.text.strip() if c is not None and c.text else ""


def parse_pom(data):
    root = ET.fromstring(data)
    parent = root.find("m:parent", NS)
    parent_info = None
    if parent is not None:
        parent_info = (text(parent, "groupId"), text(parent, "artifactId"), text(parent, "version"))
    props, dm, deps = {}, {}, []
    props_el = root.find("m:properties", NS)
    if props_el is not None:
        for p in list(props_el):
            props[p.tag.split("}")[-1]] = (p.text or "").strip()
    for d in root.findall("m:dependencyManagement/m:dependencies/m:dependency", NS):
        dm[(text(d, "groupId"), text(d, "artifactId"))] = text(d, "version")
    for d in root.findall("m:dependencies/m:dependency", NS):
        deps.append({
            "group": text(d, "groupId"), "artifact": text(d, "artifactId"),
            "version": text(d, "version"), "scope": text(d, "scope") or "compile",
            "optional": text(d, "optional") == "true",
        })
    return parent_info, props, dm, deps, (text(root, "packaging") or "jar")


def sub_props(s, props, project_version):
    def rep(m):
        k = m.group(1)
        if k == "project.version":
            return project_version or ""
        return props.get(k, m.group(0))
    prev = None
    while prev != s:
        prev = s
        s = re.sub(r"\$\{([^}]+)\}", rep, s or "")
    return s


seen = {}
order = []


def resolve(g, a, v, depth=0):
    key = (g, a)
    if key in seen:
        return
    seen[key] = v
    repo = repo_for(g)
    path = "%s/%s/%s/%s-%s.pom" % (g.replace(".", "/"), a, v, a, v)
    try:
        data = fetch(repo + path)
    except Exception as e:
        print("WARN: no pom for %s:%s:%s (%s)" % (g, a, v, e), file=sys.stderr)
        return
    parent_info, props, dm, deps, packaging = parse_pom(data)
    # merge parent pom properties + dependencyManagement (child wins)
    if parent_info and depth < 4:
        pg, pa, pv = parent_info
        try:
            pdata = fetch(repo_for(pg) + "%s/%s/%s/%s-%s.pom" % (
                pg.replace(".", "/"), pa, pv, pa, pv))
            _, pprops, pdm, _, _ = parse_pom(pdata)
            for k, val in pprops.items():
                props.setdefault(k, val)
            for k, val in pdm.items():
                dm.setdefault(k, val)
        except Exception as e:
            print("WARN: no parent pom %s:%s:%s" % (pg, pa, pv), file=sys.stderr)
    for d in deps:
        if d["optional"] or d["scope"] not in ("compile", "runtime"):
            continue
        dv = sub_props(d["version"], props, v)
        if not dv or "${" in dv:
            dmv = dm.get((d["group"], d["artifact"]))
            dv = sub_props(dmv, props, v) if dmv else ""
        if not dv or "${" in dv:
            print("WARN: unresolved version for %s:%s (wanted by %s:%s)"
                  % (d["group"], d["artifact"], g, a), file=sys.stderr)
            continue
        resolve(d["group"], d["artifact"], dv, depth + 1)
    order.append((g, a, v, packaging))


for g, a, v in [
    ("androidx.media3", "media3-exoplayer", "1.4.1"),
    ("androidx.media3", "media3-exoplayer-hls", "1.4.1"),
    ("androidx.media3", "media3-exoplayer-dash", "1.4.1"),
    ("androidx.media3", "media3-exoplayer-rtsp", "1.4.1"),
]:
    resolve(g, a, v)

os.makedirs(OUT, exist_ok=True)
total = 0
for g, a, v, packaging in order:
    exts = ["aar", "jar"] if packaging == "aar" else ["jar"]
    got = False
    for ext in exts:
        dest = os.path.join(OUT, "%s-%s.%s" % (a, v, ext))
        if os.path.exists(dest):
            print("have %s-%s.%s" % (a, v, ext))
            got = True
            break
        url = "%s%s/%s/%s/%s-%s.%s" % (
            repo_for(g), g.replace(".", "/"), a, v, a, v, ext)
        try:
            data = fetch(url)
            with open(dest, "wb") as f:
                f.write(data)
            total += len(data)
            print("got  %s-%s.%s (%d KB)" % (a, v, ext, len(data) // 1024))
            got = True
            break
        except Exception:
            continue
    if not got:
        print("FAIL: %s:%s:%s" % (g, a, v), file=sys.stderr)

print("TOTAL: %d KB in %s" % (total // 1024, OUT))
