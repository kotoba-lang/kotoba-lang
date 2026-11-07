#!/usr/bin/env python3
"""Stage a tree so `.cljk` sources are visible to an oracle as `.cljc` (JUN-579).

Owner decision 2026-11-07: `.cljk` is kept as a reference oracle source and is
treated as `.cljc` for oracle runs. JVM Clojure, Babashka, Basilisp and the other
`.cljc` readers do not read the `.cljk` extension, so this copies SRC to a new
empty DEST and renames each `.cljk` file. It is a byte stager: file contents are
copied unchanged, it resolves no namespace, compiles nothing and runs nothing, and
it renders no verdict. It is a local one-off tool, not for CI, gates, builds or
runtime paths, and the native route never depends on it.

Target extension per file, in this order:
  1. a name ending `.clj.cljk` or `.cljs.cljk` loses `.cljk` (the platform
     extension is already in the name);
  2. the value recorded for that path in the nearest `cljk-origin.edn`
     (the extension the file had before the 2026-09-11 rename);
  3. `.cljc`.

Usage: oracle-stage-cljc.py SRC DEST
Writes DEST/ORACLE-STAGE.edn with the source, its git commit if any, the date and
a sha256 for every renamed file. Then run the oracle yourself inside DEST and
record the command, tool version, input and output with the date.
"""
import datetime
import hashlib
import os
import re
import shutil
import subprocess
import sys

SKIP_DIRS = {".git", ".nbb", ".cpcache", ".shadow-cljs", "node_modules", "target", ".cache", ".clj-kondo"}
ORIGIN_RE = re.compile(r'"([^"]+\.cljk)"\s+"(\.[a-z]+)"')
_origin_cache = {}


def origins_for(dirpath):
    """(origin-file dir, {relative path: extension}) for the nearest cljk-origin.edn, or (None, {})."""
    d = dirpath
    while True:
        if d in _origin_cache:
            return _origin_cache[d]
        f = os.path.join(d, "cljk-origin.edn")
        if os.path.isfile(f):
            with open(f, encoding="utf-8", errors="replace") as fh:
                text = fh.read()
            _origin_cache[d] = (d, dict(ORIGIN_RE.findall(text)))
            return _origin_cache[d]
        parent = os.path.dirname(d)
        if parent == d or d == SRC:
            _origin_cache[d] = (None, {})
            return _origin_cache[d]
        d = parent


def target_name(path):
    base = os.path.basename(path)
    if base.endswith(".clj.cljk") or base.endswith(".cljs.cljk"):
        return base[: -len(".cljk")], "platform-suffix"
    root, rel = origins_for(os.path.dirname(path))
    if root is not None:
        ext = rel.get(os.path.relpath(path, root).replace(os.sep, "/"))
        if ext in (".cljc", ".clj", ".cljs"):
            return base[: -len(".cljk")] + ext, "cljk-origin.edn"
    return base[: -len(".cljk")] + ".cljc", "default"


def edn_str(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main(argv):
    global SRC
    if len(argv) != 3:
        sys.exit("usage: oracle-stage-cljc.py SRC DEST")
    SRC = os.path.abspath(argv[1])
    dest = os.path.abspath(argv[2])
    if not os.path.isdir(SRC):
        sys.exit("SRC is not a directory: " + SRC)
    if os.path.exists(dest) and os.listdir(dest):
        sys.exit("DEST must not exist or must be empty: " + dest)
    if dest == SRC or dest.startswith(SRC + os.sep):
        sys.exit("DEST must be outside SRC")
    os.makedirs(dest, exist_ok=True)
    renamed, copied, skipped_links, clashes = [], 0, 0, []
    for cur, dirs, files in os.walk(SRC):
        dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS)
        for name in sorted(files):
            src = os.path.join(cur, name)
            if os.path.islink(src):
                skipped_links += 1
                continue
            rel = os.path.relpath(src, SRC)
            out_rel = rel
            rule = None
            if name.endswith(".cljk"):
                new, rule = target_name(src)
                out_rel = os.path.join(os.path.dirname(rel), new)
            out = os.path.join(dest, out_rel)
            if os.path.exists(out):
                clashes.append(out_rel)
                continue
            os.makedirs(os.path.dirname(out), exist_ok=True)
            shutil.copyfile(src, out)
            copied += 1
            if rule:
                with open(out, "rb") as fh:
                    renamed.append((rel, out_rel, rule, hashlib.sha256(fh.read()).hexdigest()))
    commit = ""
    try:
        commit = subprocess.run(["git", "-C", SRC, "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        pass
    lines = ["{:format :kotoba.oracle-stage/v1",
             " :date " + edn_str(datetime.date.today().isoformat()),
             " :source " + edn_str(SRC),
             " :source-commit " + edn_str(commit),
             " :files-copied %d" % copied,
             " :symlinks-skipped %d" % skipped_links,
             " :name-clashes-skipped " + "[" + " ".join(edn_str(c) for c in clashes) + "]",
             " :renamed ["]
    for a, b, r, h in renamed:
        lines.append("  {:from %s :to %s :rule %s :sha256 %s}" % (edn_str(a), edn_str(b), edn_str(r), edn_str(h)))
    lines.append(" ]}")
    with open(os.path.join(dest, "ORACLE-STAGE.edn"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print("staged %d files (%d renamed, %d symlinks skipped, %d clashes) into %s" % (copied, len(renamed), skipped_links, len(clashes), dest))


if __name__ == "__main__":
    main(sys.argv)
