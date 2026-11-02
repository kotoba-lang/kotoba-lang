#!/usr/bin/env python3
"""Check bounded native message hashing/translation; this does not build a site."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DEPENDENCIES = ROOT / 'site/native/dependencies.json'
MESSAGE = ROOT / 'site/native/locale_message.kotoba'
CATALOG = ROOT / 'site/native/locale_catalog.kotoba'
PLACEHOLDERS = ROOT / 'site/native/locale_placeholders.kotoba'
TEST = ROOT / 'test/site/native/locale_message_test.kotoba'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--amu', type=Path, required=True)
    ap.add_argument('--sha256-source', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    a = ap.parse_args()
    amu = a.amu.resolve(strict=True)
    source = a.sha256_source.resolve(strict=True)
    data = json.loads(DEPENDENCIES.read_text())
    entries = data['dependencies']
    if data['schema'] != 'kotoba.site.native-dependencies.v1' or len(entries) != 1:
        raise ValueError('unexpected native dependency manifest')
    dep = entries[0]
    if dep['namespace'] != 'multiformats.sha256' or sha(source) != dep['sha256']:
        raise ValueError('shared SHA-256 source digest mismatch')
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    tracked = [amu, source, DEPENDENCIES, MESSAGE, CATALOG, PLACEHOLDERS, TEST, Path(__file__).resolve()]
    pins = {str(p): sha(p) for p in tracked}
    env = {'PATH': '/nonexistent', 'LANG': 'C'}

    def build(name, message):
        modules = [('multiformats.sha256', source), ('site.native.locale-catalog', CATALOG),
                   ('site.native.locale-message', message), ('site.native.locale-message-test', TEST),
                   ('site.native.locale-placeholders', PLACEHOLDERS)]
        lock = out / (name + '.ksl')
        lock.write_text('KSL2\nentry ' + str(TEST.resolve()) + '\n' +
                        ''.join('module ' + n + ' ' + str(p.resolve()) + '\n' for n, p in sorted(modules)) +
                        ''.join('file ' + sha(p) + ' ' + str(p.resolve()) + '\n' for p in sorted(p for _, p in modules)))
        binary = out / name
        result = subprocess.run([str(amu), 'compile', str(TEST), '--source-lock', str(lock), '-o', str(binary)],
                                cwd=ROOT, env=env, capture_output=True, timeout=180)
        if result.returncode or result.stderr or not binary.is_file():
            raise RuntimeError(('compile failed', name, result.returncode, result.stderr[-500:]))
        return binary, lock

    def observe(binary, expected):
        result = subprocess.run([str(binary)], cwd=ROOT, env=env, capture_output=True, timeout=180)
        if result.returncode or result.stderr or result.stdout != expected:
            raise RuntimeError(('wrong native result', binary.name, result.returncode, result.stdout, result.stderr[-500:]))

    good, good_lock = build('message-test', MESSAGE)
    observe(good, b'42\n')
    text = MESSAGE.read_text()
    needle = '(sha/digest-hex (bytes s start end))'
    if text.count(needle) != 1:
        raise ValueError('mutation anchor changed')
    mutated = out / 'mutated-message.kotoba'
    mutated.write_text(text.replace(needle, '(sha/digest-hex (vector-alloc 0))'))
    bad, bad_lock = build('message-negative', mutated)
    observe(bad, b'7\n')
    if any(sha(p) != pins[str(p)] for p in tracked):
        raise ValueError('source or compiler changed during gate')
    receipt = {'status': 'PASS_BOUNDED_NATIVE_MESSAGE_HASH', 'inputs': pins,
               'dependency': dep, 'lockSha256': sha(good_lock), 'negativeLockSha256': sha(bad_lock),
               'nativeBinarySha256': sha(good), 'negativeMutationRefused': True,
               'completeSiteGenerated': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'])


if __name__ == '__main__':
    main()
