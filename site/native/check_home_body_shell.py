#!/usr/bin/env python3
"""Check source-driven complete header; this is not a whole header/site gate."""
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
from check_footers import Tree, normalize, TAGS, ARTICLES, sections

ROOT = Path(__file__).resolve().parents[2]
MODULES = ['home_body_shell', 'home_contents', 'source_concat', 'footer', 'header', 'header_inline', 'header_menu', 'header_theme', 'header_selector', 'locale_mark', 'locale_tree', 'locale_rebase', 'locale_links', 'locale_filter', 'locale_message', 'locale_catalog', 'locale_placeholders']

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def nav(html, fragment, privacy=None):
    tree=Tree();tree.feed(html);tree.close();found=[]
    def walk(n):
        if not isinstance(n,list):return
        if n[0]=='body':found.append(n);return
        for c in n[2]:walk(c)
    walk(tree.root)
    if len(tree.stack)!=1 or len(found)!=1:raise ValueError('expected body')
    node=found[0]
    if not fragment:
        if len(node[2])!=1 or node[2][0][0]!='div':raise ValueError('unexpected body wrapper')
        wrap=node[2][0]; footers=[c for c in wrap[2] if isinstance(c,list) and c[0]=='footer']
        if len(footers)!=1 or privacy is None:raise ValueError('footer missing')
        footers[0][2][0][2][2][2][-1]=privacy
        mains=[c for c in wrap[2] if isinstance(c,list) and c[0]=='main']
        scripts=[c for c in wrap[2] if isinstance(c,list) and c[0]=='script']
        if len(mains)!=1 or len(scripts)!=5:raise ValueError('unexpected body layout')
        main=mains[0];containers=[c for c in main[2] if isinstance(c,list) and dict(c[1]).get('class')=='dds-ext-container']
        if len(containers)!=1 or len(containers[0][2])!=6:raise ValueError('unexpected chapters')
        container=containers[0];container[2]=[container[2][0], 'KOTOBA_CHAPTERS_SLOT']
        main[2]=['KOTOBA_HERO_SLOT',container]
        for script,marker in zip(scripts,('SEARCH','PLAY','HERO','CHART','MORPH')):script[2]=['KOTOBA_'+marker+'_SCRIPT_SLOT']
    return normalize(node)

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'html-core', 'html-hiccup', 'dds-components', 'dds-button', 'dds-theme', 'dds-selector', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
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
        any(sha(ROOT / name) != digest for name, digest in s['files'].items())):
        raise ValueError('dependency or source drift')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)], check=True, capture_output=True, timeout=30)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = [(ROOT / 'site/native' / (n + '.kotoba'), 'src/site/native/' + n + '.kotoba') for n in MODULES]
    source += [(ROOT / 'test/site/native/home_body_shell_file.kotoba', 'src/site/native/home_body_shell_file.kotoba'),
               (sha_source, 'src/multiformats/sha256.kotoba'), (core, 'src/html_core.kotoba'),
               (hiccup, 'src/html_hiccup.kotoba'), (components, 'src/components.kotoba'), (button, 'src/button.kotoba'), (theme, 'src/theme_toggle.kotoba'), (selector, 'src/language_selector.kotoba')]
    catalogs = [ROOT / 'site/i18n' / (tag + '.json') for tag in TAGS]
    references = [ROOT / 'site/dist' / ('' if tag == 'en' else tag) / route / 'index.html' for tag in TAGS for route in ('', ARTICLES['infra'][1])]
    tracked = [component_deps, compiler, packager, deps, html_deps, button_deps, source_pins, ROOT / 'site/generate.cljk',
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
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/home_body_shell_file.kotoba\nroot /src\n' +
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
        invoke(compiler, 'compile', '/src/site/native/home_body_shell_file.kotoba', '--target', 'aarch64-macos',
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
            ref=ROOT/'site/dist'/('' if tag=='en' else tag)/'index.html'
            original=ref.read_text().replace('site/generate.cljk','site/native/home_body_shell.kotoba')
            privacy_page=ROOT/'site/dist'/('' if tag=='en' else tag)/ARTICLES['infra'][1]/'index.html'
            privacy=sections(privacy_page.read_text(),True)[2][0][2][2][2][-1]
            if nav(result.stdout.decode(),True)!=nav(original,False,privacy):raise ValueError(('body shell mismatch',tag))
            print(tag,'shell pass',flush=True)
            return {'tag':tag,'sha256':hashlib.sha256(result.stdout).hexdigest()}
        with ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(check,TAGS))
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
    receipt = {'status': 'PASS_NATIVE_HOME_BODY_SHELL', 'inputs': pins, 'sourceLockSha256': sha(lock),
               'binarySha256': sha(image), 'observations': results, 'invalidPathsRefused': True,
               'emptyCatalogRefused': True, 'templateOnly': True, 'completeHomepage': False, 'siteGenerated': False,
               'translationQualityQualified': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)

if __name__ == '__main__':
    main()
