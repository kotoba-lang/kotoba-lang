#!/usr/bin/env python3
"""Observe native source-string extraction against historical CSS output.

Syntax-theme CSS is an oracle input here; this does not generate the complete
stylesheet or qualify an asset for release.
"""
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

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'site/native/source_concat.kotoba'
ENTRY = ROOT / 'test/site/native/source_concat_file.kotoba'
PINS = ROOT / 'site/native/metadata-sources.json'
THEME_PINS = ROOT / 'site/native/css-source-dependencies.json'
GENERATOR = ROOT / 'site/generate.cljk'
REFS = [ROOT / 'site/dist/blog/infra-cost-measured-path/index.html', ROOT / 'site/dist/blog/csf2-attack-graphs/index.html']

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

class Style(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.inside = False
        self.data = []
    def handle_starttag(self, tag, attrs):
        if tag == 'style':
            if self.inside or self.data:
                raise ValueError('unexpected extra style')
            self.inside = True
    def handle_endtag(self, tag):
        if tag == 'style':
            self.inside = False
    def handle_data(self, data):
        if self.inside:
            self.data.append(data)

def slices(path):
    p = Style()
    p.feed(path.read_text())
    p.close()
    text = ''.join(p.data)
    marks = ['.kot-skip{position:absolute', '#kot-theme{min-width', ':root{--kot-syntax-', '.kot-morph{margin:0',
             '.kot-anim .kot-chart:not(.is-in) .kot-line-label{opacity:1}}']
    if any(text.count(x) != 1 for x in marks):
        raise ValueError('reference markers changed')
    a, b, c, d, e = [text.index(x) for x in marks]
    if not a < b < c < d < e:
        raise ValueError('reference marker order changed')
    return text[b:c], text[c:d], text[a:e + len(marks[-1])]

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'theme-source', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires native macOS ARM64')
    compiler, packager, theme_source = [p.resolve(strict=True) for p in (a.compiler, a.packager, a.theme_source)]
    pins = json.loads(PINS.read_text())
    theme_pins = json.loads(THEME_PINS.read_text())
    if (pins['files']['site/generate.cljk'] != sha(GENERATOR) or
        theme_pins['schema'] != 'kotoba.site.native-css-source-dependencies.v1' or
        theme_pins['themeSource']['sha256'] != sha(theme_source)):
        raise ValueError('source drift')
    tracked = [compiler, packager, theme_source, SOURCE, ENTRY, PINS, THEME_PINS, GENERATOR, *REFS, Path(__file__).resolve()]
    before = {str(p): sha(p) for p in tracked}
    expected = slices(REFS[0])
    if slices(REFS[1]) != expected:
        raise ValueError('reference CSS differs between articles')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)], check=True, capture_output=True, timeout=30)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    files = [(SOURCE, 'src/site/native/source_concat.kotoba'), (ENTRY, 'src/site/native/source_concat_file.kotoba')]
    for src, name in files:
        dst = out / name
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/source_concat_file.kotoba\nroot /src\n' +
                    ''.join('file ' + sha(out / name) + ' /' + name + '\n' for _, name in sorted(files, key=lambda x: x[1])))
    fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY)
    saved = None
    try:
        if fd != 3:
            try:
                saved = os.dup(3)
            except OSError:
                pass
            os.dup2(fd, 3)
        def run(image, *args, expected_exit=0):
            p = subprocess.run([str(image), *args], cwd=out, env={'PATH': '/nonexistent', 'LANG': 'C'},
                               pass_fds=(3,), capture_output=True, timeout=300)
            if p.returncode != expected_exit or p.stderr:
                raise RuntimeError(('native command', args, p.returncode, p.stderr[-500:]))
            return p.stdout
        run(compiler, 'compile', '/src/site/native/source_concat_file.kotoba', '--target', 'aarch64-macos',
            '--output', 'source.kseed', '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
        info = run(compiler, 'extract-native', 'source.kseed', '--symbol', 'main', '--output', 'source.raw')
        match = re.search(rb':offset (\d+), :length (\d+), :arity 0}', info)
        if not match or int(match[2]) != (out / 'source.raw').stat().st_size:
            raise ValueError('invalid entry metadata')
        run(packager, 'source-unsigned.macho', 'source.raw', 'fs2-cli-stdio-general', match[1].decode())
        image = out / 'source.macho'
        shutil.copyfile(out / 'source-unsigned.macho', image)
        image.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(image)], check=True, capture_output=True, timeout=30)
        (out / 'theme.txt').write_text('')
        (out / 'syntax.txt').write_text('')
        (out / 'source.txt').write_bytes(theme_source.read_bytes())
        theme = run(image, 'theme', expected_exit=42)
        if theme.decode() != expected[0]:
            raise ValueError('theme CSS mismatch')
        (out / 'theme.txt').write_bytes(theme)
        (out / 'syntax.txt').write_text(expected[1])
        (out / 'source.txt').write_bytes(GENERATOR.read_bytes())
        app = run(image, 'app', expected_exit=42)
        if app.decode() != expected[2]:
            raise ValueError('app CSS mismatch')
        source = (out / 'source.txt').read_text()
        needle = '   syntax-theme-css\n'
        if source.count(needle) != 1:
            raise ValueError('negative-control anchor drift')
        (out / 'source.txt').write_text(source.replace(needle, '   (unknown-css)\n'))
        run(image, 'app', expected_exit=7)
        (out / 'source.txt').write_text(source.replace(needle, ''))
        run(image, 'app', expected_exit=7)
        run(image, 'invalid', expected_exit=7)
    finally:
        if fd != 3:
            if saved is None:
                os.close(3)
            else:
                os.dup2(saved, 3)
                os.close(saved)
        os.close(fd)
    if any(sha(p) != before[str(p)] for p in tracked):
        raise ValueError('source changed during gate')
    receipt = {'status': 'PASS_NATIVE_SOURCE_CONCAT', 'inputs': before, 'sourceLockSha256': sha(lock),
               'binarySha256': sha(image), 'themeSha256': hashlib.sha256(theme).hexdigest(),
               'appSha256': hashlib.sha256(app).hexdigest(), 'unknownExpressionRefused': True,
               'missingDependencyRefused': True, 'syntaxThemeFromHistoricalOracle': True,
               'completeStylesheetGenerated': False, 'siteGenerated': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'])

if __name__ == '__main__':
    main()
