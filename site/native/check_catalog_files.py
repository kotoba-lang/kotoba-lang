#!/usr/bin/env python3
"""Stage and observe a native macOS file-capability catalog diagnostic.

Amu compiles the locked Kotoba project and its native packager wraps the result.
The executable receives only a descriptor for this gate's staging directory.
This checks the current JSON subset; it is not a rendered-site release gate.
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
TAGS = ('ar', 'arz', 'bn', 'de', 'es', 'fr', 'hi', 'id',
        'ja', 'ko', 'mr', 'pcm', 'pt', 'ru', 'ur', 'zh-Hans')
SOURCES = ('site/native/locale_placeholders.kotoba', 'site/native/locale_catalog.kotoba',
           'test/site/native/locale_catalog_file.kotoba')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compiler', type=Path, required=True)
    parser.add_argument('--packager', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('this diagnostic currently requires native macOS arm64')
    compiler, packager = args.compiler.resolve(strict=True), args.packager.resolve(strict=True)
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    compiler_hash, packager_hash = sha(compiler), sha(packager)
    for path in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(path)],
                       check=True, capture_output=True, timeout=30)
    staged = []
    for source in SOURCES:
        relative = 'src/site/native/' + Path(source).name
        target = out / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / source, target)
        staged.append(('/' + relative, target))
    for tag in ('en', *TAGS):
        target = out / 'site/i18n' / (tag + '.json')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / 'site/i18n' / (tag + '.json'), target)
    entry = '/src/site/native/locale_catalog_file.kotoba'
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry ' + entry + '\nroot /src\n'
                    + ''.join('file ' + sha(path) + ' ' + virtual + '\n'
                              for virtual, path in sorted(staged)))

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
                raise RuntimeError(('native command failed', image.name, result.returncode,
                                    result.stderr[-400:]))
            return result

        invoke(compiler, 'compile', entry, '--target', 'aarch64-macos', '--output', 'catalog.kseed',
               '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
        result = invoke(compiler, 'extract-native', 'catalog.kseed', '--symbol', 'main', '--output', 'catalog.raw')
        match = re.search(rb':offset (\d+), :length (\d+), :arity 0}', result.stdout)
        if not match or int(match.group(2)) != (out / 'catalog.raw').stat().st_size:
            raise ValueError('invalid native entry metadata')
        invoke(packager, 'catalog-unsigned.macho', 'catalog.raw', 'fs2-cli-stdio-general', match.group(1).decode())
        program = out / 'catalog.macho'
        shutil.copyfile(out / 'catalog-unsigned.macho', program)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(program)],
                       check=True, capture_output=True, timeout=30)
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(program)],
                       check=True, capture_output=True, timeout=30)
        program.chmod(0o700)

        def check(tag):
            invoke(program, tag, expected=42)
            print(tag, 42, flush=True)
            return {'tag': tag, 'sha256': sha(out / 'site/i18n' / (tag + '.json')), 'result': 42}

        with ThreadPoolExecutor(max_workers=3) as pool:
            observations = list(pool.map(check, TAGS))
        invoke(program, '../ja', expected=7)
        target = out / 'site/i18n/ja.json'
        original = target.read_bytes()
        try:
            target.write_bytes(b'{}\n')
            invoke(program, 'ja', expected=7)
        finally:
            target.write_bytes(original)
        source_file = out / 'site/i18n/en.json'
        source_original = source_file.read_bytes()
        small = b'{"' + b'a' * 64 + b'":"translated"}\n'
        try:
            source_file.write_bytes(small)
            target.write_bytes(small)
            invoke(program, 'ja', expected=7)
        finally:
            source_file.write_bytes(source_original)
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
        raise ValueError('native toolchain changed during catalog gate')
    receipt = {'status': 'PASS_NATIVE_FILE_CATALOG_SUBSET', 'compilerSha256': compiler_hash,
               'packagerSha256': packager_hash, 'programSha256': sha(program),
               'sourceLockSha256': sha(lock), 'sourceFiles': {virtual: sha(path) for virtual, path in staged},
               'sourceSha256': sha(out / 'site/i18n/en.json'), 'catalogs': observations,
               'invalidPathRefused': True, 'missingKeysRefused': True, 'smallSourceRefused': True,
               'unicodeEscapedValues': True,
               'escapedKeysSupported': False,
               'renderedPagesValidated': False, 'siteGenerated': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)


if __name__ == '__main__':
    main()
