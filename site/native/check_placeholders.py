#!/usr/bin/env python3
"""Stage and observe the native placeholder predicate and its negative control.

This is one locale-validation primitive, not a catalog parser or site build.
The supplied Amu CLI compiles and runs the Kotoba test; Python stages the
source lock, records identities and checks the observed results.
"""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'site/native/locale_placeholders.kotoba'
TEST = ROOT / 'test/site/native/locale_placeholders_test.kotoba'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(amu, out, name, implementation, expected):
    entry = TEST.resolve()
    files = sorted([entry, implementation])
    lock = out / (name + '.ksl')
    lock.write_text('KSL2\nentry ' + str(entry) + '\n'
                    + 'module site.native.locale-placeholders ' + str(implementation) + '\n'
                    + 'module site.native.locale-placeholders-test ' + str(entry) + '\n'
                    + ''.join('file ' + digest(path) + ' ' + str(path) + '\n' for path in files))
    result = subprocess.run([str(amu), 'run', str(entry), '--source-lock', str(lock)],
                            cwd=ROOT, env={'PATH': '/nonexistent', 'LANG': 'C'},
                            capture_output=True, timeout=150, check=False)
    (out / (name + '.stdout')).write_bytes(result.stdout)
    (out / (name + '.stderr')).write_bytes(result.stderr)
    if result.returncode != 0 or result.stdout != expected or result.stderr:
        raise RuntimeError((name, result.returncode, result.stdout, result.stderr))
    return {'exit': result.returncode, 'stdout': result.stdout.decode().strip(),
            'implementationSha256': digest(implementation)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--amu', type=Path, required=True, help='qualified native Amu CLI')
    parser.add_argument('--output', type=Path, required=True, help='new evidence directory')
    args = parser.parse_args()
    amu = args.amu.resolve(strict=True)
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = SOURCE.resolve(strict=True)
    original = source.read_text()
    needle = '(and (contained-counts? source target) (contained-counts? target source))'
    if original.count(needle) != 1:
        raise ValueError('negative-control mutation anchor changed')
    mutated = out / 'negative.kotoba'
    mutated.write_text(original.replace(needle, 'true'))
    cases = {'positive': run(amu, out, 'positive', source, b'42\n'),
             'negative': run(amu, out, 'negative', mutated, b'7\n')}
    receipt = {'status': 'PASS_NATIVE_PLACEHOLDER_PRIMITIVE', 'compilerCliSha256': digest(amu),
               'testSha256': digest(TEST), 'cases': cases,
               'catalogsValidated': False, 'siteGenerated': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'])


if __name__ == '__main__':
    main()
