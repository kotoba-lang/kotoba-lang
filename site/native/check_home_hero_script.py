#!/usr/bin/env python3
"""Compare native source-derived homepage dynamic script with historical page scripts."""
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
REFS = [Path(__file__).resolve().parents[2] / 'site/dist/index.html', Path(__file__).resolve().parents[2] / 'site/dist/ja/index.html']
ROOT = Path(__file__).resolve().parents[2]
PINS = ROOT / 'site/native/css-source-dependencies.json'
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
class HeadScripts(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True); self.head = False; self.script = False; self.current = []; self.scripts = []
    def handle_starttag(self, tag, attrs):
        if tag == 'html': self.head = True
        if self.head and tag == 'script': self.script = True; self.current = []
    def handle_endtag(self, tag):
        if self.head and tag == 'script': self.scripts.append(''.join(self.current)); self.script = False
        if tag == 'html': self.head = False
    def handle_data(self, data):
        if self.script: self.current.append(data)
def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'): raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source = [p.resolve(strict=True) for p in (a.compiler, a.packager, a.sha256_source)]
    generator = ROOT / 'site/generate.cljk'; source_pins = ROOT / 'site/native/metadata-sources.json'
    spec = json.loads(PINS.read_text()); deps = json.loads((ROOT / 'site/native/dependencies.json').read_text())
    if (json.loads(source_pins.read_text())['files']['site/generate.cljk'] != sha(generator) or deps['dependencies'][0]['sha256'] != sha(sha_source)):
        raise ValueError('source pin mismatch')
    for binary in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(binary)], check=True, capture_output=True, timeout=30)
    refs = []
    for p in REFS:
        parser = HeadScripts(); parser.feed(p.read_text())
        matches = [x for x in parser.scripts if x.startswith('var P=new Uint8Array([')]
        if len(matches) != 1: raise ValueError('missing historical hero script')
        refs.append(matches[0])
    if refs[0] != refs[1]: raise ValueError('historical point tables differ')
    hero = ROOT / 'site/src/kotoba/site/hero.cljk'
    if sha(hero) != 'f324125c00d1135da1432bab4eaec186c56ece61f222329ea7de8cee897693fa': raise ValueError('hero source pin mismatch')
    modules = [(ROOT / 'site/native/home_hero_svg.kotoba', 'src/site/native/home_hero_svg.kotoba'),
               (ROOT / 'site/native/home_hero_script.kotoba', 'src/site/native/home_hero_script.kotoba'),
               (ROOT / 'site/native/source_concat.kotoba', 'src/site/native/source_concat.kotoba'),
               (ROOT / 'test/site/native/home_hero_script_file.kotoba', 'src/site/native/home_hero_script_file.kotoba'),
               (sha_source, 'src/multiformats/sha256.kotoba')]
    tracked = [hero, ROOT / 'site/native/check_footers.py', compiler, packager, *[p for p,_ in modules], generator, PINS, source_pins,
               ROOT / 'site/native/dependencies.json', *REFS, Path(__file__).resolve()]
    before = {str(p): sha(p) for p in tracked}; out = a.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    for src, name in modules:
        dst = out / name; dst.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(src, dst)
    for src, name in ((generator, 'generator.cljk'),): shutil.copyfile(src, out / name)
    shutil.copyfile(hero, out / 'hero.cljk')
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/home_hero_script_file.kotoba\nroot /src\n' +
                    ''.join('file ' + sha(out / name) + ' /' + name + '\n' for _, name in sorted(modules, key=lambda x: x[1])))
    fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY); saved = None
    try:
        if fd != 3:
            try: saved = os.dup(3)
            except OSError: pass
            os.dup2(fd, 3)
        def run(image, *args, expected_exit=0):
            p = subprocess.run([str(image), *args], cwd=out, env={'PATH':'/nonexistent','LANG':'C'},
                               pass_fds=(3,), capture_output=True, timeout=300)
            if p.returncode != expected_exit or p.stderr: raise RuntimeError(('native command', args, p.returncode, p.stderr[-500:]))
            return p.stdout
        run(compiler, 'compile', '/src/site/native/home_hero_script_file.kotoba', '--target', 'aarch64-macos',
            '--output', 'scripts.kseed', '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
        info = run(compiler, 'extract-native', 'scripts.kseed', '--symbol', 'main', '--output', 'scripts.raw')
        m = re.search(rb':offset (\d+), :length (\d+), :arity 0}', info)
        if not m or int(m[2]) != (out / 'scripts.raw').stat().st_size: raise ValueError('bad entry metadata')
        run(packager, 'scripts-unsigned.macho', 'scripts.raw', 'fs2-cli-stdio-general', m[1].decode())
        binary = out / 'scripts.macho'; shutil.copyfile(out / 'scripts-unsigned.macho', binary); binary.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(binary)], check=True, capture_output=True, timeout=30)
        observed = [run(binary, expected_exit=42)]
        if observed[0].decode() != refs[0]: raise ValueError('dynamic script differs')
        for filename in ('generator.cljk', 'hero.cljk'):
            path = out / filename; original = path.read_bytes(); path.write_bytes(original + b' ')
            run(binary, expected_exit=7); path.write_bytes(original)
    finally:
        if fd != 3:
            if saved is None: os.close(3)
            else: os.dup2(saved, 3); os.close(saved)
        os.close(fd)
    if any(sha(p) != before[str(p)] for p in tracked): raise ValueError('source changed during gate')
    receipt = {'status':'PASS_NATIVE_HOME_HERO_SCRIPT', 'inputs':before, 'binarySha256':sha(binary),
               'sourceLockSha256':sha(lock), 'outputSha256':[hashlib.sha256(x).hexdigest() for x in observed],
               'modifiedInputsRefused':True, 'browserInteractionQualified':False, 'siteGenerated':False, 'publicationQualified':False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n'); print(receipt['status'])
if __name__ == '__main__': main()
