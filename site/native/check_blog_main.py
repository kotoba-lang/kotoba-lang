#!/usr/bin/env python3
"""Check the native blog index main content against localized reference pages."""
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
MODULES = ['blog_main', 'home_section', 'source_concat', 'locale_tree', 'locale_filter', 'locale_message', 'locale_catalog', 'locale_placeholders', 'locale_links', 'locale_rebase']

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def main_node(html):
    tree=Tree();tree.feed(html);tree.close();found=[]
    def walk(node):
        if not isinstance(node,list): return
        if node[0]=='main' and dict(node[1]).get('id')=='main': found.append(node);return
        for child in node[2]: walk(child)
    walk(tree.root)
    if len(tree.stack)!=1 or len(found)!=1: raise ValueError('expected one main')
    return normalize(found[0])

def fresh_japanese_reference(html, tag, catalog):
    # Existing rendered pages predate these explicitly reviewed corrections.
    if tag != 'ja': return html
    old = {
        '29年2026月 · ベンチマーク': '6db96809c6464c5576292689bd52206fbcf36753ef9b07d4a35ddc31ba016e44',
        '31 年 2026 月 · ベンチマーク': 'dd6a5a49be9374d3a070f94383bfa06f580fbf115aa1d74a0ac038213ec2a8f1',
        '28 年 2026 月 · 言語設計': 'd99d1ac252cceb74021b97806ed105a35672b0ae00018c923bac59a66b18ade0',
        'スローガンの前の証拠': '95342850ce2983e9d6aef64a14be408eefdccb69ab73d58347ba69466028798c',
        '言語設計、測定、出荷された境界、そしてまだ資格がないものに関する短いメモ。': 'c94ef6194529ccdedbd983ac5eb205252e78cd1b5705967cdf220d2936a71986',
    }
    for text, key in old.items(): html = html.replace(text, catalog[key])
    return html

def current_reference(node, source, catalog, protected=False):
    """Apply current catalog to English text left untranslated in old HTML.

    Historical site/dist predates some catalog updates. Only exact English
    catalog entries are adjusted; translate=no and code islands stay intact.
    """
    if isinstance(node, str):
        key = hashlib.sha256(node.strip().encode()).hexdigest()
        if not protected and source.get(key) == node.strip() and key in catalog:
            return re.match(r'^\s*', node).group() + catalog[key] + re.search(r'\s*$', node).group()
        return node
    if isinstance(node, tuple) and len(node) == 3 and isinstance(node[0], str) and isinstance(node[1], tuple):
        tag, attrs, children = node
        attributes = dict(attrs)
        if tag == 'article' and attributes.get('lang') == 'en' and attributes.get('translate') == 'no':
            attributes['dir'] = 'ltr'
            attrs = tuple(sorted(attributes.items()))
        protected = protected or attributes.get('translate') == 'no' or tag in ('script','style','pre','code','kbd','samp')
        return tag, attrs, tuple(current_reference(c, source, catalog, protected) for c in children)
    return node

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'html-core', 'html-hiccup', 'dds-components', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    ap.add_argument('--tag', choices=TAGS)
    ap.add_argument('--catalog-root', type=Path, default=ROOT/'site/i18n')
    a = ap.parse_args()
    selected = [a.tag] if a.tag else TAGS
    catalog_root = a.catalog_root.resolve(strict=True)
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, core, hiccup, components = [p.resolve(strict=True) for p in
        (a.compiler, a.packager, a.sha256_source, a.html_core, a.html_hiccup, a.dds_components)]
    deps = ROOT / 'site/native/dependencies.json'
    html_deps = ROOT / 'site/native/html-dependencies.json'
    source_pins = ROOT / 'site/native/metadata-sources.json'
    d, h, s = [json.loads(p.read_text()) for p in (deps, html_deps, source_pins)]
    if (d['dependencies'][0]['sha256'] != sha(sha_source) or
        [x['sha256'] for x in h['dependencies']] != [sha(core), sha(hiccup)] or
        any(sha(ROOT / name) != digest for name, digest in s['files'].items())):
        raise ValueError('dependency or source drift')
    component_spec = json.loads((ROOT / 'site/native/dds-dependencies.json').read_text())
    if sha(components) != component_spec['dependencies'][0]['sha256']: raise ValueError('component source drift')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)], check=True, capture_output=True, timeout=30)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = [(ROOT / 'site/native' / (n + '.kotoba'), 'src/site/native/' + n + '.kotoba') for n in MODULES]
    source += [(ROOT / 'test/site/native/blog_main_file.kotoba', 'src/site/native/blog_main_file.kotoba'),
               (sha_source, 'src/multiformats/sha256.kotoba'), (core, 'src/html_core.kotoba'), (components, 'src/components.kotoba'),
               (hiccup, 'src/html_hiccup.kotoba')]
    catalogs = [catalog_root / (tag + '.json') for tag in TAGS]
    references = [ROOT / 'site/dist' / ('' if tag == 'en' else tag) / 'blog' / 'index.html' for tag in TAGS]
    authorities = [ROOT/'site'/(name+'-article.edn') for name in ('embench-c-parity','usage','embench-progress','infra-cost','security','org-identity')]
    tracked = [*authorities, components, ROOT / 'site/native/dds-dependencies.json', compiler, packager, deps, html_deps, source_pins, ROOT / 'site/generate.cljk',
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
    for authority in authorities: shutil.copyfile(authority, out / authority.name)
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/blog_main_file.kotoba\nroot /src\n' +
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
        invoke(compiler, 'compile', '/src/site/native/blog_main_file.kotoba', '--target', 'aarch64-macos',
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
            r = invoke(image, tag, expected=42)
            reference = ROOT / 'site/dist' / ('' if tag == 'en' else tag) / 'blog' / 'index.html'
            catalog = json.loads((catalog_root/(tag+'.json')).read_text())
            reference_node = main_node(fresh_japanese_reference(reference.read_text(), tag, catalog))
            reference_node = current_reference(reference_node, json.loads((catalog_root/'en.json').read_text()), catalog)
            if main_node(r.stdout.decode()) != reference_node: raise ValueError(('blog main mismatch',tag))
            print(tag, 'blog main pass', flush=True)
            return {'tag':tag,'sha256':hashlib.sha256(r.stdout).hexdigest()}
        with ThreadPoolExecutor(max_workers=3) as pool: results = list(pool.map(check, selected))
        invoke(image, '../ja', expected=7)
        for name in ('generator.cljk', *[p.name for p in authorities]):
            src=out/name; original=src.read_bytes()
            try:
                src.write_bytes(original+b' ');invoke(image, 'en', expected=7)
            finally: src.write_bytes(original)
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
    receipt = {'status': 'PASS_NATIVE_BLOG_MAIN', 'testedLocales': selected, 'inputs': pins, 'sourceLockSha256': sha(lock),
               'binarySha256': sha(image), 'observations': results, 'invalidTagRefused': True,
               'modifiedSourceRefused': True, 'historicalReferenceUpdatedFromCurrentCatalog': True, 'completeBlogPage': False, 'siteGenerated': False,
               'translationQualityQualified': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)

if __name__ == '__main__':
    main()
