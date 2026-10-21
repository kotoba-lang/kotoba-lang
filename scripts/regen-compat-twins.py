#!/usr/bin/env python3
"""scripts/regen-compat-twins.py -- regenerate the lang/compat .kotoba twins from their upstream .cljk/.cljc with
`amu refactor to-kotoba` (JUN-240). BOOTSTRAP-TOOL (python, host only: it picks inputs; every byte written is amu's).

  python3 scripts/regen-compat-twins.py [--manifest lang/compat-twins.tsv] [--repos ~/github/kotoba-lang]
                                        [--amu ../amu] [--only ns1,ns2] [--check] [--log FILE]

The manifest (lang/compat-twins.tsv) names, per twin: the upstream repo, the upstream commit, the repo-relative source
path, and the action. `regen` rows are rebuilt: the source is read with `git show <sha>:<path>` from a local clone
(<repos>/<repo>; no network, no fallback revision -- a missing commit fails the row), converted with
`amu refactor to-kotoba <src> --out lang/compat/<twin>`, and amu's own round-trip check (re-parse + idempotence) gates
the write. `keep` rows are hand-authored or hand-fixed twins whose semantics are not upstream yet; they are never
written, and the note says what has to move upstream (as a :kotoba arm) before they can become `regen`.

--check converts into a temp dir and exits 1 when any regen twin differs from what is checked in (drift).
The log (default stdout) has one row per twin: ns, action, verdict, :rc map, :rc-no-arm count."""
import os, re, shutil, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def opt(args, name, default):
    return args[args.index(name) + 1] if name in args else default


def main():
    a = sys.argv[1:]
    manifest = opt(a, '--manifest', os.path.join(ROOT, 'lang', 'compat-twins.tsv'))
    repos = os.path.expanduser(opt(a, '--repos', '~/github/kotoba-lang'))
    amu = os.path.abspath(opt(a, '--amu', os.path.join(ROOT, '..', 'amu')))
    only = set(opt(a, '--only', '').split(',')) - {''}
    check = '--check' in a
    log = open(opt(a, '--log', '/dev/stdout'), 'w')
    compat = os.path.join(ROOT, 'lang', 'compat')
    tmp = tempfile.mkdtemp(prefix='compat-regen-')
    drift = fail = 0
    for line in open(manifest):
        if not line.strip() or line.startswith('#'):
            continue
        ns, twin, repo, sha, path, action, note = line.rstrip('\n').split('\t')
        if only and ns not in only:
            continue
        if action != 'regen':
            log.write('%s\t%s\tKEPT\t-\t-\t%s\n' % (ns, action, note)); continue
        src = os.path.join(tmp, 'src', repo, path)
        os.makedirs(os.path.dirname(src), exist_ok=True)
        g = subprocess.run(['git', '-C', os.path.join(repos, repo), 'show', '%s:%s' % (sha, path)], capture_output=True)
        if g.returncode != 0:
            fail += 1
            log.write('%s\tregen\tBLOCKED\t-\t-\tgit show %s:%s in %s failed\n' % (ns, sha[:8], path, repo)); continue
        open(src, 'wb').write(g.stdout)
        dst = os.path.join(compat, twin)
        out = os.path.join(tmp, 'out', twin) if check else dst
        os.makedirs(os.path.dirname(out), exist_ok=True)
        r = subprocess.run([os.path.join(amu, 'bin', 'amu'), 'refactor', 'to-kotoba', src, '--out', out],
                           capture_output=True, text=True, cwd=amu)
        txt = r.stdout + r.stderr
        rc = (re.search(r':rc (\{[^}]*\})', txt) or [None, '-'])[1]
        noarm = txt.count(':kind :rc-no-arm')
        ok = ':ok true' in txt and ':roundtrip {:parse true, :idempotent true}' in txt and os.path.isfile(out)
        if not ok:
            fail += 1
            log.write('%s\tregen\tREFUSED\t%s\t%d\t%s\n' % (ns, rc, noarm, ' '.join(txt.split())[:300])); continue
        verdict = 'PASS'
        if check:
            same = os.path.isfile(dst) and open(dst, 'rb').read() == open(out, 'rb').read()
            if not same:
                drift += 1; verdict = 'DRIFT'
        log.write('%s\tregen\t%s\t%s\t%d\t-\n' % (ns, verdict, rc, noarm))
    shutil.rmtree(tmp, ignore_errors=True)
    if fail:
        sys.exit(2)
    if check and drift:
        sys.exit(1)


if __name__ == '__main__':
    main()
