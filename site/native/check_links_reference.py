#!/usr/bin/env python3
"""Check native rebasing against a frozen one-off URL oracle and site links.

The native program decides each case; Python stages reference strings and
observes results. Existing dist is a test input, not a current build proof.
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
SOURCE = ROOT / 'site/native/locale_links.kotoba'
TEST = ROOT / 'test/site/native/locale_links_file.kotoba'
REFERENCE = ROOT / 'test/site/native/locale_links_reference_20261102.json'


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires native macOS ARM64')
    compiler, packager = a.compiler.resolve(strict=True), a.packager.resolve(strict=True)
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)], check=True, capture_output=True, timeout=30)
    ref = json.loads(REFERENCE.read_text())
    if (ref['schema'] != 'kotoba.site.locale-links-oracle.v1' or
            ref['format'] != ['tag', 'base', 'href', 'reference', 'mustAccept'] or
            ref['referenceSourceSha256'] != sha(ROOT / 'site/generate.cljk')):
        raise ValueError('reference schema or source changed')
    pages = [ROOT / 'site/dist' / route.strip('/') / 'index.html' for route in ref['referencePageSha256']]
    if any(sha(p) != expected for p, expected in zip(pages, ref['referencePageSha256'].values())):
        raise ValueError('reference page changed')
    cases = ref['cases']
    if len(cases) < 1800 or sum(x[4] is False for x in cases) != 9:
        raise ValueError('reference corpus incomplete')
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    tracked = [compiler, packager, SOURCE, TEST, REFERENCE, ROOT / 'site/generate.cljk', *pages, Path(__file__).resolve()]
    pins = {str(p): sha(p) for p in tracked}
    staged = []
    for src, name in ((SOURCE, 'locale_links.kotoba'), (TEST, 'locale_links_file.kotoba')):
        target = out / 'src/site/native' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, target)
        staged.append(('/src/site/native/' + name, target))
    fixtures = out / 'fixtures'
    fixtures.mkdir()
    chunks = []
    for offset in range(0, len(cases), 60):
        lines = []
        for row in cases[offset:offset + 60]:
            if len(row) != 5 or not isinstance(row[4], bool) or any(not isinstance(v, str) or any(c in v for c in '\t\r\n') for v in row[:4]):
                raise ValueError('invalid oracle case')
            lines.append('\t'.join([*row[:4], 'accept' if row[4] else 'refuse']) + '\n')
        chunk = fixtures / (str(len(chunks)) + '.txt')
        chunk.write_text(''.join(lines))
        chunks.append(chunk)
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/locale_links_file.kotoba\nroot /src\n' +
                    ''.join('file ' + sha(p) + ' ' + v + '\n' for v, p in sorted(staged)))
    fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY)
    saved = None
    try:
        if fd != 3:
            try:
                saved = os.dup(3)
            except OSError:
                pass
            os.dup2(fd, 3)

        def invoke(image, *args, expected=0):
            r = subprocess.run([str(image), *args], cwd=out, env={'PATH': '/nonexistent', 'LANG': 'C'},
                               pass_fds=(3,), capture_output=True, timeout=300)
            if r.returncode != expected or r.stderr:
                raise RuntimeError(('native command failed', image.name, args, r.returncode, r.stderr[-500:]))
            return r

        invoke(compiler, 'compile', '/src/site/native/locale_links_file.kotoba', '--target', 'aarch64-macos',
               '--output', 'links.kseed', '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
        r = invoke(compiler, 'extract-native', 'links.kseed', '--symbol', 'main', '--output', 'links.raw')
        match = re.search(rb':offset (\d+), :length (\d+), :arity 0}', r.stdout)
        if not match or int(match[2]) != (out / 'links.raw').stat().st_size:
            raise ValueError('invalid native entry metadata')
        invoke(packager, 'links-unsigned.macho', 'links.raw', 'fs2-cli-stdio-general', match[1].decode())
        binary = out / 'links.macho'
        shutil.copyfile(out / 'links-unsigned.macho', binary)
        binary.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(binary)], check=True, capture_output=True, timeout=30)
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(binary)], check=True, capture_output=True, timeout=30)

        def check(chunk):
            invoke(binary, chunk.stem, expected=42)
            print('chunk', chunk.stem, 'pass', flush=True)
            return {'index': chunk.stem, 'sha256': sha(chunk)}

        with ThreadPoolExecutor(max_workers=3) as pool:
            observations = list(pool.map(check, chunks))
        invoke(binary, '../0', expected=7)
        first = chunks[0]
        original = first.read_bytes()
        parts = original.split(b'\n', 1)
        fields = parts[0].split(b'\t')
        if len(fields) != 5 or fields[4] != b'accept':
            raise ValueError('negative anchor changed')
        fields[3] += b'-incorrect'
        try:
            first.write_bytes(b'\t'.join(fields) + b'\n' + parts[1])
            invoke(binary, first.stem, expected=7)
        finally:
            first.write_bytes(original)
    finally:
        if fd != 3:
            if saved is None:
                os.close(3)
            else:
                os.dup2(saved, 3)
                os.close(saved)
        os.close(fd)
    if any(sha(p) != pins[str(p)] for p in tracked):
        raise ValueError('input changed during native gate')
    receipt = {'status': 'PASS_NATIVE_LOCALE_LINK_REFERENCE', 'inputs': pins, 'sourceLockSha256': sha(lock),
               'binarySha256': sha(binary), 'cases': len(cases), 'chunks': observations,
               'mutatedExpectedRefused': True, 'invalidIndexRefused': True,
               'arbitraryWhatwgUrlsQualified': False, 'wholeSiteGenerated': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)


if __name__ == '__main__':
    main()
