#!/usr/bin/env python3
"""Stage and observe the native scaling-axis fixture on macOS ARM64; not full JSON qualification."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / 'site/native/scaling_axis.kotoba'
TEST = ROOT / 'test/site/native/scaling_axis_test.kotoba'


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('compiler', 'packager', 'output'):
        parser.add_argument('--' + key, type=Path, required=True)
    for key in ('compiler-sha256', 'packager-sha256'):
        parser.add_argument('--' + key, required=True)
    a = parser.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires macOS arm64')
    compiler, packager = a.compiler.resolve(strict=True), a.packager.resolve(strict=True)
    if sha(compiler) != a.compiler_sha256 or sha(packager) != a.packager_sha256:
        raise ValueError('native compiler or packager digest mismatch')
    for binary in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(binary)], check=True, capture_output=True, timeout=30)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    tracked = [compiler, packager, CORE, TEST, Path(__file__).resolve()]
    pins = {str(p): sha(p) for p in tracked}
    fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY)
    if fd != 3:
        os.dup2(fd, 3)
    env = {'PATH': '/nonexistent', 'LANG': 'C'}

    def run(image, *args, expected=0):
        result = subprocess.run([str(image), *map(str, args)], cwd=out, env=env, pass_fds=(3,), capture_output=True, timeout=300)
        if result.returncode != expected or result.stderr:
            raise RuntimeError((str(image), args, result.returncode, result.stderr[-500:]))
        return result

    def fixture(name, mutate, expected):
        src = out / 'src'
        (src / 'site/native').mkdir(parents=True, exist_ok=True)
        shutil.copyfile(CORE, src / 'site/native/scaling_axis.kotoba')
        test = TEST.read_text()
        if mutate:
            needle = '315.6363636363636'
            replacement = '316.6363636363636'
            if test.count(needle) != 1:
                raise ValueError('negative-control anchor drift')
            test = test.replace(needle, replacement)
        (src / 'test.kotoba').write_text(test)
        paths = [src / 'site/native/scaling_axis.kotoba', src / 'test.kotoba']
        lock = out / (name + '.ksl')
        lock.write_text('KSL1\nentry /src/test.kotoba\nroot /src\n' +
                        ''.join('file ' + sha(p) + ' /' + str(p.relative_to(out)) + '\n' for p in paths))
        artifact, raw = name + '.kseed', name + '.raw'
        run(compiler, 'compile', '/src/test.kotoba', '--target', 'aarch64-macos', '--output', artifact,
            '--source-path', '/src', '--source-lock', lock.name, '--source-lock-sha256', sha(lock))
        r = run(compiler, 'extract-native', artifact, '--symbol', 'main', '--output', raw)
        match = re.search(rb':offset (\d+), :length (\d+), :arity 0}', r.stdout)
        if not match or int(match[2]) != (out / raw).stat().st_size:
            raise ValueError('invalid native entry metadata')
        unsigned, binary = out / (name + '-unsigned.macho'), out / (name + '.macho')
        run(packager, unsigned.name, raw, 'fs2-cli-stdio-general', match[1].decode())
        shutil.copyfile(unsigned, binary)
        binary.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(binary)], check=True, capture_output=True, timeout=30)
        run(binary, expected=expected)
        return {'lockSha256': sha(lock), 'artifactSha256': sha(out / artifact), 'result': expected}

    positive = fixture('json-native', False, 42)
    negative = fixture('json-native-negative', True, 7)
    if any(sha(p) != pins[str(p)] for p in tracked):
        raise ValueError('input changed during native observation')
    receipt = {'status': 'PASS_BOUNDED_SCALING_AXIS_MACOS', 'inputs': pins,
               'positive': positive, 'negative': negative, 'generalLogQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'])


if __name__ == '__main__':
    main()
