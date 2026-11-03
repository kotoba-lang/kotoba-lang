#!/usr/bin/env python3
"""Render bounded translated fragments from 16 catalogs via pinned shared HTML sources."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEPS = ROOT / 'site/native/dependencies.json'
HTML_DEPS = ROOT / 'site/native/html-dependencies.json'
SOURCES = [ROOT / 'site/native' / n for n in
           ('locale_tree.kotoba', 'locale_mark.kotoba', 'locale_filter.kotoba', 'locale_message.kotoba', 'locale_catalog.kotoba', 'locale_placeholders.kotoba')]
ENTRY = ROOT / 'test/site/native/locale_render_catalog_file.kotoba'
TAGS = 'ar arz bn de es fr hi id ja ko mr pcm pt ru ur zh-Hans'.split()
CATALOGS = [ROOT / 'site/i18n' / (tag + '.json') for tag in TAGS]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for arg in ('compiler', 'packager', 'sha256-source', 'html-core', 'html-hiccup', 'output'):
        ap.add_argument('--' + arg, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires native macOS ARM64')
    compiler, packager, source = (p.resolve(strict=True) for p in
                                  (a.compiler, a.packager, a.sha256_source))
    spec = json.loads(DEPS.read_text())
    if (spec['schema'] != 'kotoba.site.native-dependencies.v1' or len(spec['dependencies']) != 1 or
            spec['dependencies'][0]['namespace'] != 'multiformats.sha256' or
            sha(source) != spec['dependencies'][0]['sha256']):
        raise ValueError('shared SHA-256 source digest mismatch')
    html_core, html_hiccup = (p.resolve(strict=True) for p in (a.html_core, a.html_hiccup))
    html_spec = json.loads(HTML_DEPS.read_text())
    if (html_spec['schema'] != 'kotoba.site.native-html-dependencies.v1' or
            [x['namespace'] for x in html_spec['dependencies']] != ['html-core', 'html-hiccup'] or
            any(sha(p) != spec['sha256'] for p, spec in zip((html_core, html_hiccup), html_spec['dependencies']))):
        raise ValueError('shared HTML source digest mismatch')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)],
                       check=True, capture_output=True, timeout=30)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    tracked = [compiler, packager, source, html_core, html_hiccup, DEPS, HTML_DEPS, *SOURCES, ENTRY, *CATALOGS, Path(__file__).resolve()]
    pins = {str(p): sha(p) for p in tracked}
    staged = []
    for p in [*SOURCES, ENTRY]:
        target = out / 'src/site/native' / p.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(p, target)
        staged.append(('/src/site/native/' + p.name, target))
    target = out / 'src/multiformats/sha256.kotoba'
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
    staged.append(('/src/multiformats/sha256.kotoba', target))
    for path in (html_core, html_hiccup):
        target = out / 'src' / path.name
        shutil.copyfile(path, target)
        staged.append(('/src/' + path.name, target))
    for catalog in CATALOGS:
        target = out / 'site/i18n' / catalog.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(catalog, target)
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/' + ENTRY.name + '\nroot /src\n' +
                    ''.join('file ' + sha(p) + ' ' + virtual + '\n' for virtual, p in sorted(staged)))
    fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY)
    saved = None
    try:
        if fd != 3:
            try:
                saved = os.dup(3)
            except OSError:
                pass
            os.dup2(fd, 3)

        def invoke(binary, *args, expected=0):
            x = subprocess.run([str(binary), *args], cwd=out, env={'PATH': '/nonexistent', 'LANG': 'C'},
                               pass_fds=(3,), capture_output=True, timeout=300)
            if x.returncode != expected or x.stderr:
                raise RuntimeError(('native command failed', binary.name, x.returncode, x.stderr[-500:]))
            return x

        invoke(compiler, 'compile', '/src/site/native/' + ENTRY.name, '--target', 'aarch64-macos',
               '--output', 'ids.kseed', '--source-path', '/src', '--source-lock', 'project.ksl',
               '--source-lock-sha256', sha(lock))
        result = invoke(compiler, 'extract-native', 'ids.kseed', '--symbol', 'main', '--output', 'ids.raw')
        match = re.search(rb':offset (\d+), :length (\d+), :arity 0}', result.stdout)
        if not match or int(match[2]) != (out / 'ids.raw').stat().st_size:
            raise ValueError('invalid native entry metadata')
        invoke(packager, 'ids-unsigned.macho', 'ids.raw', 'fs2-cli-stdio-general', match[1].decode())
        binary = out / 'ids.macho'
        shutil.copyfile(out / 'ids-unsigned.macho', binary)
        binary.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(binary)],
                       check=True, capture_output=True, timeout=30)
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(binary)],
                       check=True, capture_output=True, timeout=30)
        def check(tag):
            invoke(binary, tag, expected=42)
            print(tag, 42, flush=True)
        with ThreadPoolExecutor(max_workers=3) as pool:
            list(pool.map(check, TAGS))
        invoke(binary, '../ja', expected=7)
        japanese = out / 'site/i18n/ja.json'
        original = japanese.read_bytes()
        key = 'コンテンツへスキップ'.encode('utf-8')
        if original.count(key) != 1:
            raise ValueError('negative-control anchor changed')
        try:
            japanese.write_bytes(original.replace(key, b'wrong translation', 1))
            invoke(binary, 'ja', expected=7)
            japanese.write_bytes(b'{}\n')
            invoke(binary, 'ja', expected=7)
        finally:
            japanese.write_bytes(original)
    finally:
        if fd != 3:
            if saved is None:
                os.close(3)
            else:
                os.dup2(saved, 3)
                os.close(saved)
        os.close(fd)
    if any(sha(p) != pins[str(p)] for p in tracked):
        raise ValueError('source or compiler changed during message ID gate')
    receipt = {'status': 'PASS_NATIVE_TRANSLATED_FRAGMENT_RENDER', 'inputs': pins,
               'sourceLockSha256': sha(lock), 'nativeBinarySha256': sha(binary),
               'tags': TAGS, 'translationMutationRefused': True, 'emptyCatalogRefused': True,
               'invalidTagRefused': True, 'siteGenerated': False,
               'translationQualityQualified': False, 'wholePageGenerated': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'])


if __name__ == '__main__':
    main()
