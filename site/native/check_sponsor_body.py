#!/usr/bin/env python3
"""Check the complete native sponsor body against localized references."""
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
from urllib.parse import urljoin
from check_footers import Tree, normalize, TAGS, ARTICLES, sections

ROOT = Path(__file__).resolve().parents[2]
MODULES = ['sponsor_body_page', 'sponsor_body', 'sponsor_main', 'home_section', 'source_concat', 'footer', 'header', 'header_inline', 'header_menu', 'header_theme', 'header_selector', 'locale_mark', 'locale_tree', 'locale_rebase', 'locale_links', 'locale_filter', 'locale_message', 'locale_catalog', 'locale_placeholders']

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def nav(html, tag):
    tree=Tree();tree.feed(html);tree.close();found=[]
    def walk(n):
        if not isinstance(n,list): return
        if n[0]=='body': found.append(n);return
        for c in n[2]: walk(c)
    walk(tree.root)
    if len(tree.stack)!=1 or len(found)!=1: raise ValueError('expected body')
    base='https://kotoba-lang.org/'+('' if tag=='en' else tag+'/')+'sponsor/'
    def resolve(n):
        if not isinstance(n,list): return
        n[1]=tuple((k,urljoin(base,v) if k in ('href','src') and v is not None else v) for k,v in n[1])
        for c in n[2]: resolve(c)
    resolve(found[0]);return normalize(found[0])

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'html-core', 'html-hiccup', 'dds-components', 'dds-button', 'dds-theme', 'dds-selector', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    ap.add_argument('--tag', choices=TAGS)
    a = ap.parse_args()
    selected = [a.tag] if a.tag else TAGS
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, core, hiccup, components, button, theme, selector = [p.resolve(strict=True) for p in
        (a.compiler, a.packager, a.sha256_source, a.html_core, a.html_hiccup, a.dds_components, a.dds_button, a.dds_theme, a.dds_selector)]
    deps = ROOT / 'site/native/dependencies.json'
    html_deps = ROOT / 'site/native/html-dependencies.json'
    button_deps = ROOT / 'site/native/header-dependencies.json'
    component_deps = ROOT / 'site/native/dds-dependencies.json'
    if sha(components)!=json.loads(component_deps.read_text())['dependencies'][0]['sha256']:raise ValueError('component drift')
    source_pins = ROOT / 'site/native/metadata-sources.json'
    d, h, b, s = [json.loads(p.read_text()) for p in (deps, html_deps, button_deps, source_pins)]
    if (d['dependencies'][0]['sha256'] != sha(sha_source) or
        [x['sha256'] for x in h['dependencies']] != [sha(core), sha(hiccup)] or
        b['schema'] != 'kotoba.site.native-header-dependencies.v1' or b['sha256'] != sha(button) or b['theme']['sha256'] != sha(theme) or b['selector']['sha256'] != sha(selector) or
        sha(ROOT/'site/generate.cljk') != 'c69982760682f834d96fd0f20c9dffd1c92681a9ddb1a3687b488117360d5c67' or sha(ROOT/'site/sponsorship.edn') != 'f7eed846369300609667cbfa3e0fa79150acd1f88f63bc742bde54ea46f6b380'):
        raise ValueError('dependency or source drift')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)], check=True, capture_output=True, timeout=30)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = [(ROOT / 'site/native' / (n + '.kotoba'), 'src/site/native/' + n + '.kotoba') for n in MODULES]
    source += [(ROOT / 'test/site/native/sponsor_body_file.kotoba', 'src/site/native/sponsor_body_file.kotoba'),
               (sha_source, 'src/multiformats/sha256.kotoba'), (core, 'src/html_core.kotoba'),
               (hiccup, 'src/html_hiccup.kotoba'), (components, 'src/components.kotoba'), (button, 'src/button.kotoba'), (theme, 'src/theme_toggle.kotoba'), (selector, 'src/language_selector.kotoba')]
    catalogs = [ROOT / 'site/i18n' / (tag + '.json') for tag in TAGS]
    references = [ROOT / 'site/dist' / ('' if tag == 'en' else tag) / 'sponsor' / 'index.html' for tag in TAGS]
    tracked = [component_deps, compiler, packager, deps, html_deps, button_deps, source_pins, ROOT / 'site/generate.cljk', ROOT / 'site/sponsorship.edn',
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
    shutil.copyfile(ROOT / 'site/sponsorship.edn', out / 'sponsorship.edn')
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/sponsor_body_file.kotoba\nroot /src\n' +
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
        invoke(compiler, 'compile', '/src/site/native/sponsor_body_file.kotoba', '--target', 'aarch64-macos',
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
        def check(tag):
            result=invoke(image,tag,expected=42)
            ref=ROOT/'site/dist'/('' if tag=='en' else tag)/'sponsor'/'index.html'
            original=ref.read_text().replace('site/generate.cljk','site/native/sponsor_body.kotoba')
            if nav(result.stdout.decode(),tag)!=nav(original,tag): raise ValueError(('sponsor body mismatch',tag))
            print(tag,'sponsor body pass',flush=True)
            return {'tag':tag,'sha256':hashlib.sha256(result.stdout).hexdigest()}
        with ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(check,selected))
        invoke(image,'../ja',expected=7)
        target=out/'generator.cljk';old=target.read_bytes()
        try:
            target.write_bytes(old+b' ');invoke(image,'en',expected=7)
        finally:target.write_bytes(old)
        target=out/'site/i18n/ja.json';old=target.read_bytes()
        try:
            target.write_bytes(b'{}');invoke(image,'ja',expected=7)
        finally:target.write_bytes(old)
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
    receipt = {'status': 'PASS_NATIVE_SPONSOR_BODY', 'inputs': pins, 'sourceLockSha256': sha(lock),
               'binarySha256': sha(image), 'observations': results, 'invalidPathsRefused': True,
               'emptyCatalogRefused': True, 'completeSponsorPage': False, 'siteGenerated': False,
               'translationQualityQualified': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)

if __name__ == '__main__':
    main()
