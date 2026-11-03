#!/usr/bin/env python3
"""Compare native page footer fragments with existing rendered pages.

Python stages files, runs native programs and compares DOM-shaped results. It
is not a site generator, translation-quality review, or publication gate.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
TAGS = 'en ar arz bn de es fr hi id ja ko mr pcm pt ru ur zh-Hans'.split()
ARTICLES = {'home': ('infra-cost-article.edn', ''), 'infra': ('infra-cost-article.edn', 'blog/infra-cost-measured-path'),
            'security': ('security-article.edn', 'blog/csf2-attack-graphs')}
MODULES = ['footer', 'locale_tree', 'locale_rebase', 'locale_links', 'locale_filter', 'locale_message', 'locale_catalog', 'locale_placeholders']
TEST = ROOT / 'test/site/native/footer_file.kotoba'
DEPS = ROOT / 'site/native/dependencies.json'
HTML_DEPS = ROOT / 'site/native/html-dependencies.json'
DDS_DEPS = ROOT / 'site/native/dds-dependencies.json'
SOURCE_PINS = ROOT / 'site/native/metadata-sources.json'
VOID = frozenset('area base br col embed hr img input link meta param source track wbr'.split())


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Tree(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.root = ['root', (), []]
        self.stack = [self.root]

    def handle_starttag(self, tag, attrs):
        item = [tag, tuple(sorted(attrs)), []]
        self.stack[-1][2].append(item)
        if tag not in VOID:
            self.stack.append(item)

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if tag not in VOID:
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if len(self.stack) <= 1 or self.stack[-1][0] != tag:
            raise ValueError('unexpected close tag: ' + tag)
        self.stack.pop()

    def handle_data(self, data):
        if data.strip():
            self.stack[-1][2].append(data)


def normalize(node):
    return (node[0], node[1], tuple(normalize(x) if isinstance(x, list) else x for x in node[2]))


def find_article(node):
    if node[0] == 'footer' and ('class', 'kot-footer') in node[1]:
        return node
    for child in node[2]:
        if isinstance(child, list):
            found = find_article(child)
            if found is not None:
                return found
    return None


def sections(html, reference):
    tree = Tree()
    tree.feed(html)
    tree.close()
    if len(tree.stack) != 1:
        raise ValueError('unclosed HTML')
    parent = find_article(tree.root)
    if parent is None:
        raise ValueError('footer landmark missing')
    if not reference and (len(tree.root[2]) != 1 or tree.root[2][0] is not parent):
        raise ValueError('unexpected output outside footer')
    return normalize(parent)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'html-core', 'html-hiccup', 'dds-components', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, core, hiccup, components = [p.resolve(strict=True) for p in
        (a.compiler, a.packager, a.sha256_source, a.html_core, a.html_hiccup, a.dds_components)]
    checks = [(DEPS, 'kotoba.site.native-dependencies.v1', [('multiformats.sha256', sha_source)]),
              (HTML_DEPS, 'kotoba.site.native-html-dependencies.v1', [('html-core', core), ('html-hiccup', hiccup)]),
              (DDS_DEPS, 'kotoba.site.native-dds-dependencies.v1', [('components', components)])]
    for path, schema, inputs in checks:
        spec = json.loads(path.read_text())
        entries = spec.get('dependencies', [])
        if path == DDS_DEPS:
            if [x['namespace'] for x in entries] != ['components', 'table']:
                raise ValueError('DDS dependency set changed')
            entries = entries[:1]
        if (spec.get('schema') != schema or [x['namespace'] for x in entries] != [n for n, _ in inputs] or
                any(sha(p) != x['sha256'] for x, (_, p) in zip(entries, inputs))):
            raise ValueError('shared source digest mismatch: ' + str(path))
    source_pins = json.loads(SOURCE_PINS.read_text())
    expected_sources = ('site/generate.cljk', 'site/src/kotoba/site/locales.cljk')
    if (source_pins.get('schema') != 'kotoba.site.metadata-source-pins.v1' or
            set(source_pins.get('files', {})) != set(expected_sources) or
            any(sha(ROOT / name) != source_pins['files'][name] for name in expected_sources)):
        raise ValueError('footer source contract changed')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)], check=True, capture_output=True, timeout=30)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = [(ROOT / 'site/native' / (n + '.kotoba'), 'src/site/native/' + n + '.kotoba') for n in MODULES]
    source += [(TEST, 'src/site/native/footer_file.kotoba'), (sha_source, 'src/multiformats/sha256.kotoba'),
               (core, 'src/html_core.kotoba'), (hiccup, 'src/html_hiccup.kotoba'),
               (components, 'src/components.kotoba')]
    catalogs = [ROOT / 'site/i18n' / (tag + '.json') for tag in TAGS]
    articles = [ROOT / 'site' / v[0] for v in ARTICLES.values()]
    references = [ROOT / 'site/dist' / ('' if tag == 'en' else tag) / route / 'index.html'
                  for tag in TAGS for _, route in ARTICLES.values()]
    manual_reference = ROOT / 'site/dist/ja/aiueos/index.html'
    tracked = [compiler, packager, DEPS, HTML_DEPS, DDS_DEPS, SOURCE_PINS, *[ROOT / name for name in expected_sources], *[p for p, _ in source],
               *catalogs, *articles, *references, manual_reference, Path(__file__).resolve()]
    pins = {str(p): sha(p) for p in tracked}
    for src, relative in source:
        target = out / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, target)
    for src in catalogs:
        target = out / 'site/i18n' / src.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, target)
    for src in articles:
        target = out / 'site' / src.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, target)
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/footer_file.kotoba\nroot /src\n' +
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

        entry = '/src/site/native/footer_file.kotoba'
        invoke(compiler, 'compile', entry, '--target', 'aarch64-macos', '--output', 'article.kseed',
               '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
        r = invoke(compiler, 'extract-native', 'article.kseed', '--symbol', 'main', '--output', 'article.raw')
        match = re.search(rb':offset (\d+), :length (\d+), :arity 0}', r.stdout)
        if not match or int(match[2]) != (out / 'article.raw').stat().st_size:
            raise ValueError('invalid native entry metadata')
        invoke(packager, 'article-unsigned.macho', 'article.raw', 'fs2-cli-stdio-general', match[1].decode())
        image = out / 'article.macho'
        shutil.copyfile(out / 'article-unsigned.macho', image)
        image.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(image)], check=True, capture_output=True, timeout=30)
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(image)], check=True, capture_output=True, timeout=30)

        def check(pair):
            tag, name = pair
            _, route = ARTICLES[name]
            r = invoke(image, name, tag, expected=42)
            reference = ROOT / 'site/dist' / ('' if tag == 'en' else tag) / route / 'index.html'
            actual_sections = sections(r.stdout.decode('utf-8'), False)
            reference_sections = sections(reference.read_text(), True)
            if name == 'home':
                # The historical home footer advertised paid attribution. The
                # requested change uses each locale's existing article privacy
                # sentence instead; every other DOM node remains the baseline.
                privacy_reference = ROOT / 'site/dist' / ('' if tag == 'en' else tag) / ARTICLES['infra'][1] / 'index.html'
                privacy_note = sections(privacy_reference.read_text(), True)[2][0][2][2][2][-1]
                footer, attrs, children = reference_sections
                container, cattrs, paragraphs = children[0]
                paragraph, pattrs, parts = paragraphs[2]
                new_para = (paragraph, pattrs, (*parts[:-1], privacy_note))
                new_container = (container, cattrs, (*paragraphs[:2], new_para, *paragraphs[3:]))
                reference_sections = (footer, attrs, (new_container, *children[1:]))
            if actual_sections != reference_sections:
                raise ValueError(('footer DOM mismatch', name, tag))
            output = out / (name + '-' + tag + '.html')
            output.write_bytes(r.stdout)
            print(name, tag, 'footer pass', flush=True)
            return {'article': name, 'tag': tag, 'outputSha256': sha(output)}

        with ThreadPoolExecutor(max_workers=3) as pool:
            results = list(pool.map(check, [(tag, name) for tag in TAGS for name in ARTICLES]))
        invoke(image, 'infra', '../ja', expected=7)
        manual = invoke(image, 'aiueos', 'ja', 'manual', expected=42)
        if sections(manual.stdout.decode('utf-8'), False) != sections(manual_reference.read_text(), True):
            raise ValueError('authored Japanese footer mismatch')
        invoke(image, 'aiueos', 'en', 'manual', expected=7)
        sentinel = invoke(image, 'home', 'en', 'native', expected=42)
        if b'native-sentinel.kotoba' not in sentinel.stdout or b'site/generate.cljk' in sentinel.stdout:
            raise ValueError('generator source label was not taken from caller')
        japanese = out / 'site/i18n/ja.json'
        original = japanese.read_bytes()
        try:
            japanese.write_bytes(b'{}\n')
            invoke(image, 'infra', 'ja', expected=7)
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
        raise ValueError('source, catalog, reference or compiler changed during gate')
    receipt = {'status': 'PASS_NATIVE_SOURCE_FOOTERS', 'inputs': pins, 'sourceLockSha256': sha(lock),
               'binarySha256': sha(image), 'observations': results, 'emptyCatalogRefused': True, 'invalidTagRefused': True, 'generatorLabelChecked': True, 'authoredJapaneseFooterChecked': True, 'referenceIsExistingDist': True, 'homePrivacyNoteFromSameLocaleArticle': True,
               'fullPageGenerated': False, 'siteGenerated': False, 'translationQualityQualified': False,
               'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)


if __name__ == '__main__':
    main()
