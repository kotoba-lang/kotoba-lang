#!/usr/bin/env python3
"""Exercise bounded native link rebasing, not whole-page generation."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'site/native/locale_rebase.kotoba'
LINKS = ROOT / 'site/native/locale_links.kotoba'
TEST = ROOT / 'test/site/native/locale_rebase_test.kotoba'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--amu', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    a = ap.parse_args()
    amu = a.amu.resolve(strict=True)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    tracked = [amu, SOURCE, LINKS, TEST, Path(__file__).resolve()]
    pins = {str(p): sha(p) for p in tracked}

    def run(name, source, expected):
        modules = {'site.native.locale-rebase': source, 'site.native.locale-links': LINKS, 'site.native.locale-rebase-test': TEST}
        lock = out / (name + '.ksl')
        lock.write_text('KSL2\nentry ' + str(TEST.resolve()) + '\n' +
                        ''.join('module ' + n + ' ' + str(p.resolve()) + '\n' for n, p in sorted(modules.items())) +
                        ''.join('file ' + sha(p) + ' ' + str(p.resolve()) + '\n' for p in sorted(modules.values())))
        binary = out / name
        env = {'PATH': '/nonexistent', 'LANG': 'C'}
        build = subprocess.run([str(amu), 'compile', str(TEST), '--source-lock', str(lock), '-o', str(binary)],
                               cwd=ROOT, env=env, capture_output=True, timeout=180)
        if build.returncode or build.stderr or not binary.is_file():
            raise RuntimeError(('native compile failed', name, build.returncode, build.stderr[-500:]))
        result = subprocess.run([str(binary)], cwd=ROOT, env=env, capture_output=True, timeout=180)
        if result.returncode or result.stderr or result.stdout != expected:
            raise RuntimeError(('native verdict mismatch', name, result.returncode, result.stdout, result.stderr[-500:]))
        return {'binarySha256': sha(binary), 'lockSha256': sha(lock)}

    positive = run('mark', SOURCE, b'42\n')
    needle = '(protected? (kw name))'
    text = SOURCE.read_text()
    if text.count(needle) != 1:
        raise ValueError('negative mutation anchor changed')
    mutant = out / 'mutant.kotoba'
    mutant.write_text(text.replace(needle, 'false'))
    negative = run('mark-negative', mutant, b'7\n')
    if any(sha(p) != pins[str(p)] for p in tracked):
        raise ValueError('source or compiler changed during mark gate')
    receipt = {'status': 'PASS_BOUNDED_NATIVE_LINK_REBASE', 'inputs': pins,
               'positive': positive, 'negative': negative, 'arbitraryUrlsQualified': False,
               'siteGenerated': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'])


if __name__ == '__main__':
    main()
