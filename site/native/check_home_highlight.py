#!/usr/bin/env python3
"""Compare the pinned demo's native syntax markup with historical pages."""
import argparse
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
from check_footers import Tree, normalize

ROOT = Path(__file__).resolve().parents[2]
REFS = [ROOT / 'site/dist/index.html', ROOT / 'site/dist/ja/index.html']

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def code_node(value, fragment=False):
    tree = Tree(); tree.feed(value); tree.close(); found = []
    def walk(node, inside):
        if not isinstance(node, list): return
        attrs = dict(node[1])
        inside = inside or (node[0] == 'section' and attrs.get('id') == 'code')
        if node[0] == 'code' and attrs.get('class') == 'kot-source' and (inside or fragment):
            found.append(node); return
        for item in node[2]: walk(item, inside)
    walk(tree.root, False)
    if len(tree.stack) != 1 or len(found) != 1: raise ValueError('missing unique highlighted code')
    return found[0]

class CodeText(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True); self.depth = 0; self.parts = []; self.matches = 0
    def handle_starttag(self, tag, attrs):
        if not self.depth and tag == 'code' and dict(attrs).get('class') == 'kot-source':
            self.depth = 1; self.matches += 1
        elif self.depth: self.depth += 1
    def handle_endtag(self, tag):
        if self.depth: self.depth -= 1
    def handle_data(self, data):
        if self.depth: self.parts.append(data)

def code_text(value):
    parser = CodeText(); parser.feed(value); parser.close()
    if parser.matches != 1 or parser.depth: raise ValueError('missing unique code text')
    return ''.join(parser.parts)

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'html-core', 'html-hiccup', 'grammar-source', 'tokenizer-source', 'theme-json', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'): raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, core, hiccup, grammar, tokenizer, theme = [p.resolve(strict=True) for p in
        (a.compiler, a.packager, a.sha256_source, a.html_core, a.html_hiccup, a.grammar_source, a.tokenizer_source, a.theme_json)]
    pins_path = ROOT / 'site/native/highlight-source-dependencies.json'
    css_path = ROOT / 'site/native/css-source-dependencies.json'
    deps_path = ROOT / 'site/native/dependencies.json'
    html_path = ROOT / 'site/native/html-dependencies.json'
    pins, css, deps, html = [json.loads(p.read_text()) for p in (pins_path, css_path, deps_path, html_path)]
    if (sha(grammar) != pins['grammar']['catalogSha256'] or sha(tokenizer) != pins['grammar']['tokenizerSha256'] or
        sha(theme) != css['syntaxTheme']['sha256'] or sha(sha_source) != deps['dependencies'][0]['sha256'] or
        [sha(core), sha(hiccup)] != [x['sha256'] for x in html['dependencies']]):
        raise ValueError('pinned dependency mismatch')
    for binary in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(binary)], check=True, capture_output=True, timeout=30)
    source = ROOT / 'site/assets/play/double-21.kotoba'
    refs = [code_node(p.read_text()) for p in REFS]
    if any(code_text(p.read_text()) != source.read_text() for p in REFS) or normalize(refs[0]) != normalize(refs[1]):
        raise ValueError('historical sample mismatch')
    modules = [(ROOT / 'site/native/home_highlight.kotoba', 'src/site/native/home_highlight.kotoba'),
               (ROOT / 'test/site/native/home_highlight_file.kotoba', 'src/site/native/home_highlight_file.kotoba'),
               (sha_source, 'src/multiformats/sha256.kotoba'), (core, 'src/html_core.kotoba'), (hiccup, 'src/html_hiccup.kotoba')]
    tracked = [compiler, packager, source, grammar, tokenizer, theme, pins_path, css_path, deps_path, html_path,
               *[p for p, _ in modules], *REFS, Path(__file__).resolve(), ROOT / 'site/native/check_footers.py']
    before = {str(p): sha(p) for p in tracked}; out = a.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    for src, name in modules:
        dst = out / name; dst.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(src, dst)
    for src, name in ((source, 'play.kotoba'), (grammar, 'grammar.edn'), (tokenizer, 'highlight.cljk'), (theme, 'theme.json')):
        shutil.copyfile(src, out / name)
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/home_highlight_file.kotoba\nroot /src\n' +
                    ''.join('file ' + sha(out / name) + ' /' + name + '\n' for _, name in sorted(modules, key=lambda x: x[1])))
    fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY); saved = None
    try:
        if fd != 3:
            try: saved = os.dup(3)
            except OSError: pass
            os.dup2(fd, 3)
        def run(image, *args, expected=0):
            p = subprocess.run([str(image), *args], cwd=out, env={'PATH': '/nonexistent', 'LANG': 'C'}, pass_fds=(3,), capture_output=True, timeout=300)
            if p.returncode != expected or p.stderr: raise RuntimeError((args, p.returncode, p.stderr[-500:]))
            return p.stdout
        run(compiler, 'compile', '/src/site/native/home_highlight_file.kotoba', '--target', 'aarch64-macos', '--output', 'highlight.kseed',
            '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
        meta = run(compiler, 'extract-native', 'highlight.kseed', '--symbol', 'main', '--output', 'highlight.raw')
        match = re.search(rb':offset (\d+), :length (\d+), :arity 0}', meta)
        if not match or int(match[2]) != (out / 'highlight.raw').stat().st_size: raise ValueError('invalid native metadata')
        run(packager, 'highlight-unsigned.macho', 'highlight.raw', 'fs2-cli-stdio-general', match[1].decode())
        binary = out / 'highlight.macho'; shutil.copyfile(out / 'highlight-unsigned.macho', binary); binary.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(binary)], check=True, capture_output=True, timeout=30)
        observed = run(binary, expected=42); actual = code_node(observed.decode(), True)
        if normalize(actual) != normalize(refs[0]) or code_text(observed.decode()) != source.read_text(): raise ValueError('native highlight mismatch')
        for name in ('play.kotoba', 'grammar.edn', 'highlight.cljk', 'theme.json'):
            p = out / name; old = p.read_bytes()
            try:
                p.write_bytes(old + b' '); run(binary, expected=7)
            finally: p.write_bytes(old)
    finally:
        if fd != 3:
            if saved is None: os.close(3)
            else: os.dup2(saved, 3); os.close(saved)
        os.close(fd)
    if any(sha(p) != before[str(p)] for p in tracked): raise ValueError('input changed during check')
    receipt = {'status': 'PASS_NATIVE_PINNED_DEMO_HIGHLIGHT', 'inputs': before, 'binarySha256': sha(binary),
               'sourceLockSha256': sha(lock), 'outputSha256': hashlib.sha256(observed).hexdigest(),
               'modifiedInputsRefused': True, 'generalTokenizerQualified': False,
               'completeHomepage': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n'); print(receipt['status'])
if __name__ == '__main__': main()
