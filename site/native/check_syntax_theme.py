#!/usr/bin/env python3
"""Observe pinned native syntax-theme CSS against historical site output."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
from check_source_concat import slices, REFS
ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'site/native/syntax_theme.kotoba'
ENTRY = ROOT / 'test/site/native/syntax_theme_file.kotoba'
PINS = ROOT / 'site/native/css-source-dependencies.json'
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'json-source', 'theme-json', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'): raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, json_source, theme_json = [p.resolve(strict=True) for p in
        (a.compiler, a.packager, a.sha256_source, a.json_source, a.theme_json)]
    p = json.loads(PINS.read_text()); deps = json.loads((ROOT / 'site/native/dependencies.json').read_text())
    if (p['syntaxTheme']['sha256'] != sha(theme_json) or p['jsonScalar']['sha256'] != sha(json_source) or
            deps['dependencies'][0]['sha256'] != sha(sha_source)):
        raise ValueError('dependency digest mismatch')
    for binary in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(binary)], check=True, capture_output=True, timeout=30)
    expected = slices(REFS[0])[1]
    if slices(REFS[1])[1] != expected: raise ValueError('historical references differ')
    tracked = [compiler, packager, sha_source, json_source, theme_json, SOURCE, ENTRY, PINS, ROOT / 'site/native/dependencies.json',
               ROOT / 'site/native/check_source_concat.py', *REFS, Path(__file__).resolve()]
    before = {str(p): sha(p) for p in tracked}
    out = a.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    files = [(SOURCE, 'src/site/native/syntax_theme.kotoba'), (ENTRY, 'src/site/native/syntax_theme_file.kotoba'),
             (sha_source, 'src/multiformats/sha256.kotoba'), (json_source, 'src/json_scalar.kotoba')]
    for src, name in files:
        dst = out / name; dst.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(src, dst)
    (out / 'theme.json').write_bytes(theme_json.read_bytes())
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/syntax_theme_file.kotoba\nroot /src\n' +
                    ''.join('file ' + sha(out / name) + ' /' + name + '\n' for _, name in sorted(files, key=lambda x: x[1])))
    fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY); saved = None
    try:
        if fd != 3:
            try: saved = os.dup(3)
            except OSError: pass
            os.dup2(fd, 3)
        def run(image, *args, expected_exit=0):
            p = subprocess.run([str(image), *args], cwd=out, env={'PATH': '/nonexistent', 'LANG': 'C'},
                               pass_fds=(3,), capture_output=True, timeout=300)
            if p.returncode != expected_exit or p.stderr:
                raise RuntimeError(('native command', args, p.returncode, p.stderr[-500:]))
            return p.stdout
        run(compiler, 'compile', '/src/site/native/syntax_theme_file.kotoba', '--target', 'aarch64-macos',
            '--output', 'theme.kseed', '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
        info = run(compiler, 'extract-native', 'theme.kseed', '--symbol', 'main', '--output', 'theme.raw')
        m = re.search(rb':offset (\d+), :length (\d+), :arity 0}', info)
        if not m or int(m[2]) != (out / 'theme.raw').stat().st_size: raise ValueError('bad entry metadata')
        run(packager, 'theme-unsigned.macho', 'theme.raw', 'fs2-cli-stdio-general', m[1].decode())
        binary = out / 'theme.macho'; shutil.copyfile(out / 'theme-unsigned.macho', binary); binary.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(binary)], check=True, capture_output=True, timeout=30)
        result = run(binary, expected_exit=42)
        if result.decode() != expected: raise ValueError('syntax CSS mismatch')
        bad = (out / 'theme.json').read_bytes().replace(b'#72706f', b'#72706e', 1)
        (out / 'theme.json').write_bytes(bad)
        run(binary, expected_exit=7)
    finally:
        if fd != 3:
            if saved is None: os.close(3)
            else: os.dup2(saved, 3); os.close(saved)
        os.close(fd)
    if any(sha(p) != before[str(p)] for p in tracked): raise ValueError('source changed during gate')
    receipt = {'status':'PASS_NATIVE_SYNTAX_THEME', 'inputs':before, 'binarySha256':sha(binary),
               'sourceLockSha256':sha(lock), 'outputSha256':hashlib.sha256(result).hexdigest(), 'modifiedInputRefused':True,
               'completeStylesheetGenerated':False, 'siteGenerated':False, 'publicationQualified':False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n'); print(receipt['status'])
if __name__ == '__main__': main()
