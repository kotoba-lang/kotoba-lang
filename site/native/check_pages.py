#!/usr/bin/env python3
"""Observe native locale-page checks against supplied existing HTML artifacts.

This diagnostic does not generate the site or qualify its publication. Amu
compiles a locked Kotoba project; the native executable reads only its staged
FD3 directory. Python copies inputs and records process observations/hashes.
"""

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

ROOT = Path(__file__).resolve().parents[2]
TAGS = ('en', 'ar', 'arz', 'bn', 'de', 'es', 'fr', 'hi', 'id',
        'ja', 'ko', 'mr', 'pcm', 'pt', 'ru', 'ur', 'zh-Hans')
ROUTES = ('/', '/blog/infra-cost-measured-path/', '/blog/csf2-attack-graphs/',
          '/blog/', '/libraries/', '/legal/', '/sponsor/')
PAGES = ROOT / 'site/native/locale_pages.kotoba'
FILE_ENTRY = ROOT / 'test/site/native/locale_pages_file.kotoba'
TEST_ENTRY = ROOT / 'test/site/native/locale_pages_test.kotoba'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compiler', type=Path, required=True)
    parser.add_argument('--packager', type=Path, required=True)
    parser.add_argument('--dist', type=Path, required=True, help='existing site artifact root')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('this diagnostic currently requires native macOS arm64')
    compiler, packager, dist = (p.resolve(strict=True) for p in (args.compiler, args.packager, args.dist))
    compiler_hash, packager_hash = sha(compiler), sha(packager)
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    for path in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(path)],
                       check=True, capture_output=True, timeout=30)
    staged_pages = {}
    for tag in TAGS:
        for route in ROUTES:
            relative = Path('' if tag == 'en' else tag) / route.lstrip('/') / 'index.html'
            source = dist / relative
            if source.is_symlink() or not source.is_file() or not source.resolve().is_relative_to(dist):
                raise ValueError(('missing or non-regular page', str(relative)))
            target = out / 'site/dist' / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            staged_pages[(tag, route)] = target

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
            result = subprocess.run([str(image), *map(str, argv)], cwd=out,
                                    env={'PATH': '/usr/bin:/bin', 'LANG': 'C'},
                                    pass_fds=(3,), capture_output=True, timeout=150, check=False)
            if result.returncode != expected or result.stderr:
                raise RuntimeError(('native page command failed', image.name, result.returncode,
                                    result.stderr[-400:]))
            return result

        def build(name, entry, mutated=False):
            src = out / name / 'site/native'
            src.mkdir(parents=True)
            implementation = PAGES.read_text()
            if mutated:
                needle = '(same-code? english html)'
                if implementation.count(needle) != 1:
                    raise ValueError('negative-control mutation anchor changed')
                implementation = implementation.replace(needle, 'true')
            (src / 'locale_pages.kotoba').write_text(implementation)
            shutil.copyfile(entry, src / entry.name)
            base = '/' + name
            entry_virtual = base + '/site/native/' + entry.name
            inputs = [(base + '/site/native/' + p.name, p) for p in src.iterdir()]
            lock = out / (name + '.ksl')
            lock.write_text('KSL1\nentry ' + entry_virtual + '\nroot ' + base + '\n'
                            + ''.join('file ' + sha(path) + ' ' + virtual + '\n'
                                      for virtual, path in sorted(inputs)))
            invoke(compiler, 'compile', entry_virtual, '--target', 'aarch64-macos',
                   '--output', name + '.kseed', '--source-path', base,
                   '--source-lock', lock.name, '--source-lock-sha256', sha(lock))
            result = invoke(compiler, 'extract-native', name + '.kseed', '--symbol', 'main', '--output', name + '.raw')
            match = re.search(rb':offset (\d+), :length (\d+), :arity 0}', result.stdout)
            if not match or int(match.group(2)) != (out / (name + '.raw')).stat().st_size:
                raise ValueError('invalid native page entry metadata')
            invoke(packager, name + '-unsigned.macho', name + '.raw', 'fs2-cli-stdio-general', match.group(1).decode())
            program = out / (name + '.macho')
            shutil.copyfile(out / (name + '-unsigned.macho'), program)
            subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(program)],
                           check=True, capture_output=True, timeout=30)
            subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(program)],
                           check=True, capture_output=True, timeout=30)
            program.chmod(0o700)
            return program, lock

        unit, unit_lock = build('page-unit', TEST_ENTRY)
        invoke(unit, expected=42)
        negative, negative_lock = build('page-negative', TEST_ENTRY, mutated=True)
        invoke(negative, expected=7)
        program, lock = build('page-file', FILE_ENTRY)

        def check(pair):
            tag, route = pair
            invoke(program, tag, route, expected=42)
            print(tag, route, 42, flush=True)
            return {'tag': tag, 'route': route, 'sha256': sha(staged_pages[pair]), 'result': 42}

        with ThreadPoolExecutor(max_workers=3) as pool:
            observations = list(pool.map(check, staged_pages))
        invoke(program, '../ja', '/', expected=7)
        invoke(program, 'ja', '/../', expected=7)
        target = staged_pages[('ja', '/')]
        original = target.read_bytes()
        needle = b'id="architecture"'
        if needle not in original:
            raise ValueError('landing negative-control anchor absent')
        try:
            target.write_bytes(original.replace(needle, b'id="removed-architecture"'))
            invoke(program, 'ja', '/', expected=7)
        finally:
            target.write_bytes(original)
    finally:
        if fd != 3:
            if saved is None:
                os.close(3)
            else:
                os.dup2(saved, 3)
                os.close(saved)
        os.close(fd)

    if sha(compiler) != compiler_hash or sha(packager) != packager_hash:
        raise ValueError('native toolchain changed during page gate')
    receipt = {'status': 'PASS_NATIVE_EXISTING_PAGE_CHECKS', 'compilerSha256': compiler_hash,
               'packagerSha256': packager_hash, 'programSha256': sha(program),
               'sourceLockSha256': sha(lock), 'unitLockSha256': sha(unit_lock),
               'negativeLockSha256': sha(negative_lock), 'pages': observations,
               'unitAndMutationControls': True, 'invalidPathsRefused': True, 'missingLandmarkRefused': True,
               'inputsGeneratedByGate': False, 'siteGenerated': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], len(observations), flush=True)


if __name__ == '__main__':
    main()
