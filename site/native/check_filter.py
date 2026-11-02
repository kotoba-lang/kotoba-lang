#!/usr/bin/env python3
"""Check the native translation filter against a recorded reference corpus."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
FILTER = ROOT / 'site/native/locale_filter.kotoba'
TEST = ROOT / 'test/site/native/locale_filter_test.kotoba'
REFERENCE = ROOT / 'test/site/native/locale_filter_reference_20261102.json'
SITE_SOURCE = ROOT / 'site/src/kotoba/site/locales.cljk'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--amu', type=Path, required=True)
    ap.add_argument('--reference-text', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    a = ap.parse_args()
    amu, text = a.amu.resolve(strict=True), a.reference_text.resolve(strict=True)
    ref = json.loads(REFERENCE.read_text())
    if (len(ref['cases']) != 792 or sha(TEST) != ref['generatedNativeTestSha256'] or
            sha(SITE_SOURCE) != ref['reference']['siteLocalesSha256'] or
            sha(text) != ref['reference']['kotobaTextSha256']):
        raise ValueError('reference source or native corpus digest mismatch')
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    tracked = [amu, text, FILTER, TEST, REFERENCE, SITE_SOURCE, Path(__file__).resolve()]
    pins = {str(p): sha(p) for p in tracked}
    env = {'PATH': '/nonexistent', 'LANG': 'C'}

    def run(name, source, expected):
        modules = [('site.native.locale-filter', source), ('site.native.locale-filter-test', TEST)]
        lock = out / (name + '.ksl')
        lock.write_text('KSL2\nentry ' + str(TEST.resolve()) + '\n' +
                        ''.join('module ' + n + ' ' + str(p.resolve()) + '\n' for n, p in modules) +
                        ''.join('file ' + sha(p) + ' ' + str(p.resolve()) + '\n' for p in sorted(p for _, p in modules)))
        binary = out / name
        build = subprocess.run([str(amu), 'compile', str(TEST), '--source-lock', str(lock), '-o', str(binary)],
                               cwd=ROOT, env=env, capture_output=True, timeout=180)
        if build.returncode or build.stderr or not binary.is_file():
            raise RuntimeError(('native compile failed', name, build.returncode, build.stderr[-500:]))
        result = subprocess.run([str(binary)], cwd=ROOT, env=env, capture_output=True, timeout=180)
        if result.returncode or result.stderr or result.stdout != expected:
            raise RuntimeError(('native verdict mismatch', name, result.returncode, result.stdout, result.stderr[-500:]))
        return {'binarySha256': sha(binary), 'lockSha256': sha(lock)}

    positive = run('filter-test', FILTER, b'42\n')
    data = FILTER.read_text()
    needle = '(ascii-letter? s)'
    if data.count(needle) != 1:
        raise ValueError('negative mutation anchor changed')
    mutant = out / 'mutated-filter.kotoba'
    mutant.write_text(data.replace(needle, 'false'))
    negative = run('filter-negative', mutant, b'1\n')
    if any(sha(p) != pins[str(p)] for p in tracked):
        raise ValueError('source or compiler changed during native filter gate')
    receipt = {'status': 'PASS_BOUNDED_NATIVE_TRANSLATION_FILTER', 'inputs': pins,
               'cases': 792, 'positive': positive, 'negative': negative,
               'siteGenerated': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'])


if __name__ == '__main__':
    main()
