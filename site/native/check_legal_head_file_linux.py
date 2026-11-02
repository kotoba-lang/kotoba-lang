#!/usr/bin/env python3
"""Qualify an explicitly staged experimental Linux x86 legal-page head or body generator.

The caller supplies a private staging root, a native compiler and runner, and a
previously qualified 17-locale head receipt. This is an experimental comparison,
not a production compiler or whole-site release gate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import time

TAGS = ('en', 'ar', 'arz', 'bn', 'de', 'es', 'fr', 'hi', 'id', 'ja', 'ko', 'mr', 'pcm', 'pt', 'ru', 'ur', 'zh-Hans')

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--kind', choices=('head', 'body'), default='head')
    for name in ('stage', 'compiler', 'runner', 'reference', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    for name in ('compiler-sha256', 'runner-sha256', 'reference-sha256'):
        ap.add_argument('--' + name, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Linux', 'x86_64'):
        raise ValueError('requires Linux x86_64')
    stage, compiler, runner, reference = (p.resolve(strict=True) for p in (a.stage, a.compiler, a.runner, a.reference))
    for path, expected in ((compiler, a.compiler_sha256), (runner, a.runner_sha256), (reference, a.reference_sha256)):
        if sha(path) != expected:
            raise ValueError('pinned input differs: ' + str(path))
    ref = json.loads(reference.read_text())
    observations = ref['observations']
    expected = {row['tag']: row['sha256'] for row in observations}
    if len(observations) != len(TAGS) or set(expected) != set(TAGS):
        raise ValueError('reference must contain all 17 locales exactly once')
    out = a.output.resolve()
    if not out.is_relative_to(stage) or out == stage:
        raise ValueError('output must be a new private directory inside the stage')
    out.mkdir(parents=True, exist_ok=False)
    src = stage / 'src'
    entry = src / ('site/native/legal_' + a.kind + '_file_linux_file.kotoba')
    files = [p for p in sorted(src.rglob('*.kotoba')) if p.name not in ('legal_' + a.kind + '_file.kotoba', 'legal_' + a.kind + '_page.kotoba')]
    if entry not in files:
        raise ValueError('entry missing')
    resources = ('dds.css', 'language.css', 'box.css', 'menu.css', 'behavior.cljk', 'core.cljk', 'tokens.cljk', 'generator.cljk', 'theme-source.cljk', 'theme.json') if a.kind == 'head' else ('generator.cljk',)
    inputs = files + [stage / n for n in resources] + [stage / 'site/i18n' / (tag + '.json') for tag in TAGS]
    pins = {str(p): sha(p) for p in inputs}
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry ' + str(entry) + '\nroot ' + str(src) + '\n' + ''.join('file ' + sha(p) + ' ' + str(p) + '\n' for p in files))
    env = {'PATH': '/nonexistent', 'LANG': 'C', 'KEXE_CAP_RESOURCES_35': str(stage), 'KEXE_STACK_BYTES': '8388608'}
    image = out / 'head.kx64'
    r = subprocess.run([str(compiler), 'compile', str(entry), '--target', 'x86_64-linux', '--output', str(image), '--format', 'kx64-v1', '--source-path', str(src), '--source-lock', str(lock), '--source-lock-sha256', sha(lock)], env=env, cwd=stage, capture_output=True, timeout=900)
    if r.returncode or r.stderr:
        raise RuntimeError(('compile', r.returncode, r.stdout[-500:], r.stderr[-500:]))
    header, payload = image.read_bytes().split(b'\n\n', 1)
    m = re.fullmatch(rb'KX64V1 x86_64-linux sysv-ctx-v11 ([0-9]+) 0', header)
    if not m or int(m[1]) != len(payload):
        raise ValueError('invalid KX64 payload')
    binary = out / 'head.bin'
    binary.write_bytes(payload)
    def run(tag, path, result):
        start = time.monotonic()
        r = subprocess.run([str(runner), str(binary), '0', '0', 'x86_64', '35,37,38,39', '--', tag, str(path), str(stage)], env=dict(env, KEXE_ALLOW='35,37,38,39', KEXE_STRUCTURED_REPORT='1', KEXE_FUEL='off', KEXE_STRING_POOL='268435456'), cwd=stage, capture_output=True, timeout=120)
        if r.returncode or r.stderr or not re.search(rb':status :ok\s+:result ' + str(result).encode() + rb'\b', r.stdout):
            raise RuntimeError((tag, r.returncode, r.stdout[-500:], r.stderr[-500:]))
        return time.monotonic() - start
    rows = []
    for tag in TAGS:
        path = out / (tag + '.html')
        seconds = run(tag, path, 42)
        if sha(path) != expected[tag]:
            raise ValueError('head differs from reference: ' + tag)
        rows.append({'tag': tag, 'sha256': sha(path), 'bytes': path.stat().st_size, 'seconds': seconds})
        print(tag + ' passed', flush=True)
    occupied = out / 'occupied.html'
    occupied.write_text('preserve')
    run('en', occupied, 7)
    if occupied.read_text() != 'preserve':
        raise ValueError('existing file modified')
    invalid = out / 'invalid.html'
    run('../ja', invalid, 7)
    if invalid.exists():
        raise ValueError('invalid tag wrote a file')
    generator = stage / 'generator.cljk'
    original = generator.read_bytes()
    try:
        generator.write_bytes(original + b' ')
        modified = out / 'modified.html'
        run('en', modified, 7)
        if modified.exists():
            raise ValueError('modified generator wrote a file')
    finally:
        generator.write_bytes(original)
    if any(sha(Path(p)) != digest for p, digest in pins.items()) or sha(compiler) != a.compiler_sha256 or sha(runner) != a.runner_sha256 or sha(reference) != a.reference_sha256:
        raise ValueError('input changed during qualification')
    receipt = {'status': 'PASS_EXPERIMENTAL_LINUX_LEGAL_' + a.kind.upper(), 'compilerSha256': sha(compiler), 'runnerSha256': sha(runner), 'referenceSha256': sha(reference), 'inputs': pins, 'binarySha256': sha(image), 'sourceLockSha256': sha(lock), 'observations': rows, 'existingFilePreserved': True, 'invalidInputsRefused': True, 'experimentalCompiler': True, 'linuxSpawnQualified': False, 'fullSiteGenerated': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)

if __name__ == '__main__':
    main()
