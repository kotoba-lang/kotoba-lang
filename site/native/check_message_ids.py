#!/usr/bin/env python3
"""Verify existing English catalog IDs in native code; this does not build a site."""
import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEPS = ROOT / 'site/native/dependencies.json'
SOURCES = [ROOT / 'site/native' / n for n in
           ('locale_message.kotoba', 'locale_catalog.kotoba', 'locale_placeholders.kotoba')]
ENTRY = ROOT / 'test/site/native/locale_message_ids_file.kotoba'
ENGLISH = ROOT / 'site/i18n/en.json'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for arg in ('compiler', 'packager', 'sha256-source', 'output'):
        ap.add_argument('--' + arg, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires native macOS ARM64')
    compiler, packager, source = (p.resolve(strict=True) for p in
                                  (a.compiler, a.packager, a.sha256_source))
    spec = json.loads(DEPS.read_text())
    if (spec['schema'] != 'kotoba.site.native-dependencies.v1' or len(spec['dependencies']) != 1 or
            spec['dependencies'][0]['namespace'] != 'multiformats.sha256' or
            sha(source) != spec['dependencies'][0]['sha256']):
        raise ValueError('shared SHA-256 source digest mismatch')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)],
                       check=True, capture_output=True, timeout=30)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    tracked = [compiler, packager, source, DEPS, *SOURCES, ENTRY, ENGLISH, Path(__file__).resolve()]
    pins = {str(p): sha(p) for p in tracked}
    staged = []
    for p in [*SOURCES, ENTRY]:
        target = out / 'src/site/native' / p.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(p, target)
        staged.append(('/src/site/native/' + p.name, target))
    target = out / 'src/multiformats/sha256.kotoba'
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
    staged.append(('/src/multiformats/sha256.kotoba', target))
    english = out / 'site/i18n/en.json'
    english.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ENGLISH, english)
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/' + ENTRY.name + '\nroot /src\n' +
                    ''.join('file ' + sha(p) + ' ' + virtual + '\n' for virtual, p in sorted(staged)))
    fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY)
    saved = None
    try:
        if fd != 3:
            try:
                saved = os.dup(3)
            except OSError:
                pass
            os.dup2(fd, 3)

        def invoke(binary, *args, expected=0):
            x = subprocess.run([str(binary), *args], cwd=out, env={'PATH': '/nonexistent', 'LANG': 'C'},
                               pass_fds=(3,), capture_output=True, timeout=300)
            if x.returncode != expected or x.stderr:
                raise RuntimeError(('native command failed', binary.name, x.returncode, x.stderr[-500:]))
            return x

        invoke(compiler, 'compile', '/src/site/native/' + ENTRY.name, '--target', 'aarch64-macos',
               '--output', 'ids.kseed', '--source-path', '/src', '--source-lock', 'project.ksl',
               '--source-lock-sha256', sha(lock))
        result = invoke(compiler, 'extract-native', 'ids.kseed', '--symbol', 'main', '--output', 'ids.raw')
        match = re.search(rb':offset (\d+), :length (\d+), :arity 0}', result.stdout)
        if not match or int(match[2]) != (out / 'ids.raw').stat().st_size:
            raise ValueError('invalid native entry metadata')
        invoke(packager, 'ids-unsigned.macho', 'ids.raw', 'fs2-cli-stdio-general', match[1].decode())
        binary = out / 'ids.macho'
        shutil.copyfile(out / 'ids-unsigned.macho', binary)
        binary.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(binary)],
                       check=True, capture_output=True, timeout=30)
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(binary)],
                       check=True, capture_output=True, timeout=30)
        invoke(binary, expected=42)
        original = english.read_bytes()
        key = b'87dd9bbb3e2154cde4dd498e45e6c1c651736bec5e3252a8131094fe3d3829bc'
        if original.count(key) != 1 or original.count(b'"perfgate"') != 1:
            raise ValueError('negative-control anchors changed')
        try:
            english.write_bytes(original.replace(key, b'0' * 64, 1))
            invoke(binary, expected=7)
            english.write_bytes(original.replace(b'"perfgate"', b'"perfgatex"', 1))
            invoke(binary, expected=7)
            english.write_bytes(b'{}\n')
            invoke(binary, expected=7)
        finally:
            english.write_bytes(original)
    finally:
        if fd != 3:
            if saved is None:
                os.close(3)
            else:
                os.dup2(saved, 3)
                os.close(saved)
        os.close(fd)
    if any(sha(p) != pins[str(p)] for p in tracked):
        raise ValueError('source or compiler changed during message ID gate')
    receipt = {'status': 'PASS_NATIVE_EXISTING_ENGLISH_MESSAGE_IDS', 'inputs': pins,
               'sourceLockSha256': sha(lock), 'nativeBinarySha256': sha(binary),
               'sourceMinimum': 901, 'keyMutationRefused': True, 'textMutationRefused': True,
               'emptySourceRefused': True, 'inputGeneratedByGate': False,
               'siteGenerated': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'])


if __name__ == '__main__':
    main()
