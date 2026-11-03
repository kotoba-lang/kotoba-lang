#!/usr/bin/env python3
"""Check native homepage code/play section across locales."""
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
MODULES = ['home_code_play', 'home_highlight', 'home_play', 'home_section', 'source_concat', 'locale_tree', 'locale_filter', 'locale_message', 'locale_catalog', 'locale_placeholders', 'locale_links', 'locale_rebase']

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def nav(html, fragment, which, catalog):
    tree = Tree(); tree.feed(html); tree.close(); found = []
    def walk(node):
        if not isinstance(node, list): return
        attrs = dict(node[1])
        if node[0] == 'section' and attrs.get('id') == which:
            found.append(node); return
        for item in node[2]: walk(item)
    walk(tree.root)
    if len(tree.stack) != 1 or len(found) != 1: raise ValueError('expected selected section')
    if not fragment:
        old = '--target wasm32-browser --output double-21.wasm'
        cmd = 'Compile locally: kotoba compile double-21.kotoba --target wasm -o double-21.wasm'
        note = 'The locally built file is separate from the precompiled browser demo.'
        cmd_value = catalog[hashlib.sha256(cmd.encode()).hexdigest()]
        note_value = catalog[hashlib.sha256(note.encode()).hexdigest()]
        matches = []
        def update(parent):
            for index, child in enumerate(list(parent[2])):
                if not isinstance(child, list): continue
                if child[0] == 'p' and any(isinstance(x, str) and old in x for x in child[2]):
                    matches.append((parent, index, child))
                else: update(child)
        update(found[0])
        if len(matches) != 1: raise ValueError('historical compile caption missing')
        parent, index, caption = matches[0]
        caption[2] = [cmd_value]
        parent[2].insert(index + 1, ['p', caption[1], [note_value]])
    return normalize(found[0])

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'html-core', 'html-hiccup', 'dds-components', 'dds-button', 'grammar-source', 'tokenizer-source', 'theme-json', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, core, hiccup, components, button, grammar, tokenizer, theme = [p.resolve(strict=True) for p in
        (a.compiler, a.packager, a.sha256_source, a.html_core, a.html_hiccup, a.dds_components, a.dds_button, a.grammar_source, a.tokenizer_source, a.theme_json)]
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
    header_spec = json.loads((ROOT / 'site/native/header-dependencies.json').read_text())
    if sha(button) != header_spec['sha256']: raise ValueError('button source drift')
    grammar_spec = json.loads((ROOT / 'site/native/highlight-source-dependencies.json').read_text())['grammar']
    css_spec = json.loads((ROOT / 'site/native/css-source-dependencies.json').read_text())
    if (sha(grammar) != grammar_spec['catalogSha256'] or sha(tokenizer) != grammar_spec['tokenizerSha256'] or
        sha(theme) != css_spec['syntaxTheme']['sha256']): raise ValueError('highlight source drift')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)], check=True, capture_output=True, timeout=30)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = [(ROOT / 'site/native' / (n + '.kotoba'), 'src/site/native/' + n + '.kotoba') for n in MODULES]
    source += [(ROOT / 'test/site/native/home_code_play_file.kotoba', 'src/site/native/home_code_play_file.kotoba'),
               (sha_source, 'src/multiformats/sha256.kotoba'), (core, 'src/html_core.kotoba'), (components, 'src/components.kotoba'),
               (hiccup, 'src/html_hiccup.kotoba'), (button, 'src/button.kotoba')]
    catalogs = [ROOT / 'site/i18n' / (tag + '.json') for tag in TAGS]
    references = [ROOT / 'site/dist' / ('' if tag == 'en' else tag) / 'index.html' for tag in TAGS]
    tracked = [components, grammar, tokenizer, theme, ROOT / 'site/native/highlight-source-dependencies.json', ROOT / 'site/native/css-source-dependencies.json', ROOT / 'site/assets/play/double-21.kotoba', ROOT / 'site/assets/play/double-21.wasm', ROOT / 'site/assets/play/double-21.wasm.provenance.edn', ROOT / 'site/native/dds-dependencies.json', ROOT / 'site/native/header-dependencies.json', compiler, packager, deps, html_deps, source_pins, ROOT / 'site/generate.cljk',
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
    for src, name in ((ROOT / 'site/assets/play/double-21.kotoba', 'play.kotoba'),
                      (ROOT / 'site/assets/play/double-21.wasm', 'play.wasm'),
                      (ROOT / 'site/assets/play/double-21.wasm.provenance.edn', 'provenance.edn'),
                      (grammar, 'grammar.edn'), (tokenizer, 'highlight.cljk'), (theme, 'theme.json')):
        shutil.copyfile(src, out / name)
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/home_code_play_file.kotoba\nroot /src\n' +
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
        invoke(compiler, 'compile', '/src/site/native/home_code_play_file.kotoba', '--target', 'aarch64-macos',
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
            r = invoke(image, tag, expected=42)
            reference = ROOT / 'site/dist' / ('' if tag == 'en' else tag) / 'index.html'
            catalog = json.loads((ROOT / 'site/i18n' / (tag + '.json')).read_text())
            if nav(r.stdout.decode(), True, which, catalog) != nav(reference.read_text(), False, which, catalog):
                raise ValueError(('homepage section mismatch', tag, which))
            print(tag, which, 'home section pass', flush=True)
            return {'tag': tag, 'section': which, 'sha256': hashlib.sha256(r.stdout).hexdigest()}
        with ThreadPoolExecutor(max_workers=3) as pool:
            results = list(pool.map(check, [(tag, 'code') for tag in TAGS]))
        invoke(image, '../ja', expected=7)
        src = out / 'generator.cljk'; original = src.read_bytes()
        try:
            src.write_bytes(original + b' ')
            invoke(image, 'en', expected=7)
        finally:
            src.write_bytes(original)
        for name in ('play.kotoba', 'play.wasm', 'provenance.edn', 'grammar.edn', 'highlight.cljk', 'theme.json'):
            src = out / name; original = src.read_bytes()
            try:
                src.write_bytes(original + b' ')
                invoke(image, 'en', expected=7)
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
    receipt = {'status': 'PASS_NATIVE_HOME_CODE_PLAY', 'inputs': pins, 'sourceLockSha256': sha(lock),
               'binarySha256': sha(image), 'observations': results, 'invalidTagRefused': True,
               'modifiedSourceRefused': True, 'modifiedDemoAndGrammarRefused': True, 'completeHomepage': False, 'siteGenerated': False,
               'translationQualityQualified': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)

if __name__ == '__main__':
    main()
