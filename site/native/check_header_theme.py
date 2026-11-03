#!/usr/bin/env python3
"""Check source-driven theme control; this is not a whole header/site gate."""
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
MODULES = ['header_theme', 'locale_tree', 'locale_filter', 'locale_message', 'locale_catalog', 'locale_placeholders']

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def nav(html, fragment):
    tree = Tree()
    tree.feed(html)
    tree.close()
    def find(node):
        if node[0] == 'button' and ('id', 'kot-theme') in node[1]:
            return node
        for item in node[2]:
            if isinstance(item, list):
                found = find(item)
                if found is not None:
                    return found
        return None
    n = find(tree.root)
    if n is None or len(tree.stack) != 1 or (fragment and tree.root[2] != [n]):
        raise ValueError('invalid navigation fragment')
    return normalize(n)

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'html-core', 'html-hiccup', 'dds-button', 'dds-theme', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, core, hiccup, button, theme = [p.resolve(strict=True) for p in
        (a.compiler, a.packager, a.sha256_source, a.html_core, a.html_hiccup, a.dds_button, a.dds_theme)]
    deps = ROOT / 'site/native/dependencies.json'
    html_deps = ROOT / 'site/native/html-dependencies.json'
    button_deps = ROOT / 'site/native/header-dependencies.json'
    source_pins = ROOT / 'site/native/metadata-sources.json'
    d, h, b, s = [json.loads(p.read_text()) for p in (deps, html_deps, button_deps, source_pins)]
    if (d['dependencies'][0]['sha256'] != sha(sha_source) or
        [x['sha256'] for x in h['dependencies']] != [sha(core), sha(hiccup)] or
        b['schema'] != 'kotoba.site.native-header-dependencies.v1' or b['sha256'] != sha(button) or b['theme']['sha256'] != sha(theme) or
        any(sha(ROOT / name) != digest for name, digest in s['files'].items())):
        raise ValueError('dependency or source drift')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)], check=True, capture_output=True, timeout=30)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = [(ROOT / 'site/native' / (n + '.kotoba'), 'src/site/native/' + n + '.kotoba') for n in MODULES]
    source += [(ROOT / 'test/site/native/header_theme_file.kotoba', 'src/site/native/header_theme_file.kotoba'),
               (sha_source, 'src/multiformats/sha256.kotoba'), (core, 'src/html_core.kotoba'),
               (hiccup, 'src/html_hiccup.kotoba'), (button, 'src/button.kotoba'), (theme, 'src/theme_toggle.kotoba')]
    catalogs = [ROOT / 'site/i18n' / (tag + '.json') for tag in TAGS]
    references = [ROOT / 'site/dist' / ('' if tag == 'en' else tag) / route / 'index.html'
                  for tag in TAGS for _, route in ARTICLES.values()]
    tracked = [compiler, packager, deps, html_deps, button_deps, source_pins, ROOT / 'site/generate.cljk',
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
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/header_theme_file.kotoba\nroot /src\n' +
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
        invoke(compiler, 'compile', '/src/site/native/header_theme_file.kotoba', '--target', 'aarch64-macos',
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
        def check(pair):
            tag, name = pair
            _, route = ARTICLES[name]
            r = invoke(image, name, tag, expected=42)
            reference = ROOT / 'site/dist' / ('' if tag == 'en' else tag) / route / 'index.html'
            if nav(r.stdout.decode(), True) != nav(reference.read_text(), False):
                raise ValueError(('navigation DOM mismatch', name, tag))
            print(name, tag, 'navigation pass', flush=True)
            return {'page': name, 'tag': tag, 'sha256': hashlib.sha256(r.stdout).hexdigest()}
        with ThreadPoolExecutor(max_workers=3) as pool:
            results = list(pool.map(check, [(tag, name) for tag in TAGS for name in ARTICLES]))
        invoke(image, 'infra', '../ja', expected=7)
        invoke(image, 'unknown', 'en', expected=7)
        ja = out / 'site/i18n/ja.json'
        original = ja.read_bytes()
        try:
            ja.write_bytes(b'{}\n')
            invoke(image, 'home', 'ja', expected=7)
        finally:
            ja.write_bytes(original)
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
    receipt = {'status': 'PASS_NATIVE_HEADER_THEME', 'inputs': pins, 'sourceLockSha256': sha(lock),
               'binarySha256': sha(image), 'observations': results, 'invalidPathsRefused': True,
               'emptyCatalogRefused': True, 'completeHeader': False, 'siteGenerated': False,
               'translationQualityQualified': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)

if __name__ == '__main__':
    main()
