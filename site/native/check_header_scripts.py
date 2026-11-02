#!/usr/bin/env python3
"""Compare native source-derived header scripts with historical page scripts."""
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
from check_source_concat import REFS
ROOT = Path(__file__).resolve().parents[2]
PINS = ROOT / 'site/native/css-source-dependencies.json'
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
class HeadScripts(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True); self.head = False; self.script = False; self.current = []; self.scripts = []
    def handle_starttag(self, tag, attrs):
        if tag == 'head': self.head = True
        if self.head and tag == 'script': self.script = True; self.current = []
    def handle_endtag(self, tag):
        if self.head and tag == 'script': self.scripts.append(''.join(self.current)); self.script = False
        if tag == 'head': self.head = False
    def handle_data(self, data):
        if self.script: self.current.append(data)
def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'dds-root', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'): raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, dds = [p.resolve(strict=True) for p in (a.compiler, a.packager, a.sha256_source, a.dds_root)]
    theme = dds / 'src/jp_go_dds/theme_toggle.cljk'; behavior = dds / 'src/jp_go_dds/behavior.cljk'
    generator = ROOT / 'site/generate.cljk'; source_pins = ROOT / 'site/native/metadata-sources.json'
    spec = json.loads(PINS.read_text()); deps = json.loads((ROOT / 'site/native/dependencies.json').read_text())
    if (spec['themeSource']['sha256'] != sha(theme) or spec['ddsBase']['files']['src/jp_go_dds/behavior.cljk'] != sha(behavior) or
        json.loads(source_pins.read_text())['files']['site/generate.cljk'] != sha(generator) or deps['dependencies'][0]['sha256'] != sha(sha_source)):
        raise ValueError('source pin mismatch')
    for binary in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(binary)], check=True, capture_output=True, timeout=30)
    refs = []
    for p in REFS:
        parser = HeadScripts(); parser.feed(p.read_text()); parser.close()
        if len(parser.scripts) < 2: raise ValueError('missing reference scripts')
        refs.append(parser.scripts[:2])
    if refs[0] != refs[1]: raise ValueError('reference header scripts differ')
    modules = [(ROOT / 'site/native/header_scripts.kotoba', 'src/site/native/header_scripts.kotoba'),
               (ROOT / 'site/native/source_concat.kotoba', 'src/site/native/source_concat.kotoba'),
               (ROOT / 'test/site/native/header_scripts_file.kotoba', 'src/site/native/header_scripts_file.kotoba'),
               (sha_source, 'src/multiformats/sha256.kotoba')]
    tracked = [compiler, packager, *[p for p,_ in modules], theme, behavior, generator, PINS, source_pins,
               ROOT / 'site/native/dependencies.json', *REFS, Path(__file__).resolve()]
    before = {str(p): sha(p) for p in tracked}; out = a.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    for src, name in modules:
        dst = out / name; dst.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(src, dst)
    for src, name in ((theme, 'theme.cljk'), (behavior, 'behavior.cljk'), (generator, 'generator.cljk')): shutil.copyfile(src, out / name)
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/header_scripts_file.kotoba\nroot /src\n' +
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
        run(compiler, 'compile', '/src/site/native/header_scripts_file.kotoba', '--target', 'aarch64-macos',
            '--output', 'scripts.kseed', '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
        info = run(compiler, 'extract-native', 'scripts.kseed', '--symbol', 'main', '--output', 'scripts.raw')
        m = re.search(rb':offset (\d+), :length (\d+), :arity 0}', info)
        if not m or int(m[2]) != (out / 'scripts.raw').stat().st_size: raise ValueError('bad entry metadata')
        run(packager, 'scripts-unsigned.macho', 'scripts.raw', 'fs2-cli-stdio-general', m[1].decode())
        binary = out / 'scripts.macho'; shutil.copyfile(out / 'scripts-unsigned.macho', binary); binary.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(binary)], check=True, capture_output=True, timeout=30)
        observed = [run(binary, mode, expected_exit=42) for mode in ('theme','menu')]
        if [x.decode() for x in observed] != refs[0]: raise ValueError('header scripts differ from reference')
        for filename, mode in (('theme.cljk','theme'), ('behavior.cljk','menu'), ('generator.cljk','menu')):
            path = out / filename; original = path.read_bytes(); path.write_bytes(original + b' ')
            run(binary, mode, expected_exit=7); path.write_bytes(original)
        run(binary, 'invalid', expected_exit=7)
    finally:
        if fd != 3:
            if saved is None: os.close(3)
            else: os.dup2(saved, 3); os.close(saved)
        os.close(fd)
    if any(sha(p) != before[str(p)] for p in tracked): raise ValueError('source changed during gate')
    receipt = {'status':'PASS_NATIVE_HEADER_SCRIPTS', 'inputs':before, 'binarySha256':sha(binary),
               'sourceLockSha256':sha(lock), 'outputSha256':[hashlib.sha256(x).hexdigest() for x in observed],
               'modifiedInputsRefused':True, 'browserInteractionQualified':False, 'siteGenerated':False, 'publicationQualified':False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n'); print(receipt['status'])
if __name__ == '__main__': main()
