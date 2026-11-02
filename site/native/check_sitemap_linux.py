#!/usr/bin/env python3
"""Check bounded native sitemap sorting and refusals on Linux x86-64."""
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
TEST = ROOT / 'test/site/native/sitemap_test.kotoba'
SOURCES = [ROOT / 'site/native/sitemap.kotoba']


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def image(path, expected):
    if path.is_symlink() or not path.is_file() or sha(path) != expected:
        raise ValueError('native image digest mismatch: ' + str(path))
    return path.resolve(strict=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for arg in ('compiler', 'runner', 'output'):
        ap.add_argument('--' + arg, type=Path, required=True)
    ap.add_argument('--compiler-sha256', required=True)
    ap.add_argument('--runner-sha256', required=True)
    a = ap.parse_args()
    if platform.system() != 'Linux' or platform.machine() not in ('x86_64', 'AMD64'):
        raise ValueError('requires native Linux x86-64')
    compiler, runner = image(a.compiler, a.compiler_sha256), image(a.runner, a.runner_sha256)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    tracked = [compiler, runner, *SOURCES, TEST, Path(__file__).resolve()]
    pins = {str(p): sha(p) for p in tracked}
    env = {'PATH': '/nonexistent', 'KEXE_CAP_RESOURCES_35': str(out), 'KEXE_STACK_BYTES': '8388608'}

    def run(name, mutant, expected):
        src = out / name / 'src'
        for parent in ('site/native',):
            (src / parent).mkdir(parents=True)
        for p in SOURCES:
            shutil.copyfile(p, src / 'site/native' / p.name)
        entry = src / 'site/native/sitemap_test.kotoba'
        shutil.copyfile(TEST, entry)
        if mutant:
            text = entry.read_text()
            needle = 'https://kotoba-lang.org/a/</loc>'
            if text.count(needle) != 1: raise ValueError('mutation anchor changed')
            entry.write_text(text.replace(needle, 'https://kotoba-lang.org/wrong/</loc>'))
        paths = sorted(src.rglob('*.kotoba'))
        lock = out / (name + '.ksl')
        lock.write_text('KSL1\nentry ' + str(entry) + '\nroot ' + str(src) + '\n' +
                        ''.join('file ' + sha(p) + ' ' + str(p) + '\n' for p in paths))
        artifact = out / (name + '.kx64')
        build = subprocess.run([str(compiler), 'compile', str(entry), '--target', 'x86_64-linux',
                                '--output', str(artifact), '--format', 'kx64-v1', '--source-path', str(src),
                                '--source-lock', str(lock), '--source-lock-sha256', sha(lock)],
                               cwd=out, env=env, capture_output=True, timeout=900)
        if build.returncode or build.stderr:
            raise RuntimeError(('native compile failed', name, build.returncode, build.stderr[-500:]))
        header, payload = artifact.read_bytes().split(b'\n\n', 1)
        match = re.fullmatch(rb'KX64V1 x86_64-linux sysv-ctx-v11 ([0-9]+) 0', header)
        if not match or int(match[1]) != len(payload) or payload[0] != 0xe9:
            raise ValueError('invalid compiler-emitted runner payload')
        raw = out / (name + '.bin')
        raw.write_bytes(payload)
        run_env = dict(env, KEXE_ALLOW='-', KEXE_FUEL='off', KEXE_STRUCTURED_REPORT='1',
                       KEXE_STRING_POOL='268435456')
        result = subprocess.run([str(runner), str(raw), '0', '0', 'x86_64', '-', '--'],
                                cwd=out, env=run_env, capture_output=True, timeout=180)
        if result.returncode or result.stderr or not re.search(rb':status :ok\s+:result ' + str(expected).encode() + rb'\b', result.stdout):
            raise RuntimeError(('native verdict mismatch', name, result.returncode, result.stdout[-500:], result.stderr[-500:]))
        return {'artifactSha256': sha(artifact), 'lockSha256': sha(lock), 'result': expected}

    positive = run('sitemap-test', False, 42)
    negative = run('sitemap-negative', True, 7)
    if any(sha(p) != pins[str(p)] for p in tracked):
        raise ValueError('source or native image changed during gate')
    receipt = {'status': 'PASS_BOUNDED_NATIVE_SITEMAP_X86_64', 'inputs': pins,
               'positive': positive, 'negative': negative, 'siteGenerated': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)


if __name__ == '__main__':
    main()
