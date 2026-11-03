#!/usr/bin/env python3
"""Check deliberate language-boundary homepage section and its pinned authority across locales."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
from check_footers import Tree, normalize, TAGS, ARTICLES

ROOT = Path(__file__).resolve().parents[2]
MODULES = ['home_deliberate', 'home_section', 'source_concat', 'locale_tree', 'locale_filter', 'locale_message', 'locale_catalog', 'locale_placeholders', 'locale_links', 'locale_rebase']

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def nav(html, fragment, which):
    tree = Tree(); tree.feed(html); tree.close()
    found = []
    def walk(node):
        if not isinstance(node, list): return
        attrs = dict(node[1])
        if node[0] == 'section' and ((which == 'deliberate' and not attrs.get('id') and any(isinstance(x,list) and dict(x[1]).get('class') == 'kot-table-scroll' for x in node[2])) or (which != 'deliberate' and attrs.get('id') == which)):
            found.append(node); return
        for item in node[2]: walk(item)
    walk(tree.root)
    if len(tree.stack) != 1 or len(found) != 1: raise ValueError('expected selected section')
    node=found[0]
    if which == 'deliberate' and not fragment:
        def adjust(x):
            if not isinstance(x,list): return
            if x[0] == 'tbody':
                if len(x[2]) != 6: raise ValueError('unexpected reference rows')
                x[2].pop()
                code=x[2][2][2][0][2][0]
                if code[0] != 'code' or len(code[2]) != 1: raise ValueError('unexpected reference cell')
                code[2][0]='alter-var-root, binding, dosync, set!, var, volatile!'
            for y in x[2]: adjust(y)
        adjust(node)
    return (normalize(node),)

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'html-core', 'html-hiccup', 'dds-components', 'dds-table', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, core, hiccup, components, dds_table = [p.resolve(strict=True) for p in
        (a.compiler, a.packager, a.sha256_source, a.html_core, a.html_hiccup, a.dds_components, a.dds_table)]
    deps = ROOT / 'site/native/dependencies.json'
    html_deps = ROOT / 'site/native/html-dependencies.json'
    source_pins = ROOT / 'site/native/metadata-sources.json'
    d, h, s = [json.loads(p.read_text()) for p in (deps, html_deps, source_pins)]
    if (d['dependencies'][0]['sha256'] != sha(sha_source) or
        [x['sha256'] for x in h['dependencies']] != [sha(core), sha(hiccup)] or
        any(sha(ROOT / name) != digest for name, digest in s['files'].items())):
        raise ValueError('dependency or source drift')
    component_spec = json.loads((ROOT / 'site/native/dds-dependencies.json').read_text())
    if sha(components) != component_spec['dependencies'][0]['sha256'] or sha(dds_table) != component_spec['dependencies'][1]['sha256']: raise ValueError('component source drift')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)], check=True, capture_output=True, timeout=30)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = [(ROOT / 'site/native' / (n + '.kotoba'), 'src/site/native/' + n + '.kotoba') for n in MODULES]
    source += [(ROOT / 'test/site/native/home_deliberate_file.kotoba', 'src/site/native/home_deliberate_file.kotoba'),
               (sha_source, 'src/multiformats/sha256.kotoba'), (core, 'src/html_core.kotoba'), (components, 'src/components.kotoba'),
               (hiccup, 'src/html_hiccup.kotoba'), (dds_table, 'src/table.kotoba')]
    catalogs = [ROOT / 'site/i18n' / (tag + '.json') for tag in TAGS]
    references = [ROOT / 'site/dist' / ('' if tag == 'en' else tag) / 'index.html' for tag in TAGS]
    authority = ROOT / 'lang/surface-status.edn'
    if sha(authority) != 'dfc15d4a3cbc9075697dd27b6c0d8953ee04019ac2972285526d5d512aa1bb4e': raise ValueError('authority drift')
    tracked = [authority, components, ROOT / 'site/native/dds-dependencies.json', compiler, packager, deps, html_deps, source_pins, ROOT / 'site/generate.cljk',
               *[p for p, _ in source], *catalogs, *references, Path(__file__).resolve(), ROOT / 'site/native/check_footers.py']
    pins = {str(p): sha(p) for p in tracked}
    for src, relative in source:
        target = out / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, target)
    for src in catalogs:
        target = out / 'site/i18n' / src.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, target)
    shutil.copyfile(ROOT / 'site/generate.cljk', out / 'generator.cljk')
    shutil.copyfile(authority, out / 'surface-status.edn')
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/home_deliberate_file.kotoba\nroot /src\n' +
                    ''.join('file ' + sha(out / path) + ' /' + path + '\n' for _, path in sorted(source, key=lambda x: x[1])))
    fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY)
    saved = None
    try:
        if fd != 3:
            try:
                saved = os.dup(3)
            except OSError:
                pass
            os.dup2(fd, 3)
        def invoke(image, *argv, expected=0):
            r = subprocess.run([str(image), *argv], cwd=out, env={'PATH': '/nonexistent', 'LANG': 'C'},
                               pass_fds=(3,), capture_output=True, timeout=300)
            if r.returncode != expected or r.stderr:
                raise RuntimeError(('native command failed', image.name, argv, r.returncode, r.stderr[-500:]))
            return r
        invoke(compiler, 'compile', '/src/site/native/home_deliberate_file.kotoba', '--target', 'aarch64-macos',
               '--output', 'nav.kseed', '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
        r = invoke(compiler, 'extract-native', 'nav.kseed', '--symbol', 'main', '--output', 'nav.raw')
        match = re.search(rb':offset (\d+), :length (\d+), :arity 0}', r.stdout)
        if not match or int(match[2]) != (out / 'nav.raw').stat().st_size:
            raise ValueError('invalid native entry metadata')
        invoke(packager, 'nav-unsigned.macho', 'nav.raw', 'fs2-cli-stdio-general', match[1].decode())
        image = out / 'nav.macho'
        shutil.copyfile(out / 'nav-unsigned.macho', image)
        image.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(image)], check=True, capture_output=True, timeout=30)
        def check(case):
            tag, which = case
            r = invoke(image, tag, which, expected=42)
            reference = ROOT / 'site/dist' / ('' if tag == 'en' else tag) / 'index.html'
            if nav(r.stdout.decode(), True, which) != nav(reference.read_text(), False, which):
                raise ValueError(('homepage section mismatch', tag, which))
            print(tag, which, 'home section pass', flush=True)
            return {'tag': tag, 'section': which, 'sha256': hashlib.sha256(r.stdout).hexdigest()}
        with ThreadPoolExecutor(max_workers=3) as pool:
            results = list(pool.map(check, [(tag, 'deliberate') for tag in TAGS] + [('en', which) for which in ('why', 'what', 'docs', 'roadmap', 'community', 'cloud')]))
        invoke(image, '../ja', 'why', expected=7)
        invoke(image, 'en', 'unknown', expected=7)
        src = out / 'generator.cljk'; original = src.read_bytes()
        try:
            src.write_bytes(original + b' ')
            invoke(image, 'en', 'deliberate', expected=7)
        finally:
            src.write_bytes(original)
        src = out / 'surface-status.edn'; original = src.read_bytes()
        try:
            src.write_bytes(original + b' ')
            invoke(image, 'en', 'deliberate', expected=7)
        finally:
            src.write_bytes(original)
    finally:
        if fd != 3:
            if saved is None:
                os.close(3)
            else:
                os.dup2(saved, 3)
                os.close(saved)
        os.close(fd)
    if any(sha(p) != pins[str(p)] for p in tracked):
        raise ValueError('source changed during gate')
    receipt = {'status': 'PASS_NATIVE_HOME_DELIBERATE', 'inputs': pins, 'sourceLockSha256': sha(lock),
               'binarySha256': sha(image), 'observations': results, 'invalidTagRefused': True,
               'modifiedSourceRefused': True, 'modifiedAuthorityRefused': True, 'completeHomepage': False, 'siteGenerated': False,
               'translationQualityQualified': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)

if __name__ == '__main__':
    main()
