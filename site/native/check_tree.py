#!/usr/bin/env python3
"""Check native translation of bounded document fragments, not full pages."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DEPS = ROOT / 'site/native/dependencies.json'
TREE = ROOT / 'site/native/locale_tree.kotoba'
TEST = ROOT / 'test/site/native/locale_tree_test.kotoba'
SOURCES = {
    'site.native.locale-filter': ROOT / 'site/native/locale_filter.kotoba',
    'site.native.locale-message': ROOT / 'site/native/locale_message.kotoba',
    'site.native.locale-catalog': ROOT / 'site/native/locale_catalog.kotoba',
    'site.native.locale-placeholders': ROOT / 'site/native/locale_placeholders.kotoba',
    'site.native.locale-tree-test': TEST,
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--amu', type=Path, required=True)
    ap.add_argument('--sha256-source', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    a = ap.parse_args()
    amu, source = a.amu.resolve(strict=True), a.sha256_source.resolve(strict=True)
    spec = json.loads(DEPS.read_text())
    if (spec['schema'] != 'kotoba.site.native-dependencies.v1' or len(spec['dependencies']) != 1 or
            spec['dependencies'][0]['namespace'] != 'multiformats.sha256' or
            sha(source) != spec['dependencies'][0]['sha256']):
        raise ValueError('shared SHA-256 source digest mismatch')
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    tracked = [amu, source, DEPS, TREE, *SOURCES.values(), Path(__file__).resolve()]
    pins = {str(p): sha(p) for p in tracked}
    env = {'PATH': '/nonexistent', 'LANG': 'C'}

    def run(name, tree, expected):
        modules = dict(SOURCES, **{'site.native.locale-tree': tree, 'multiformats.sha256': source})
        lock = out / (name + '.ksl')
        lock.write_text('KSL2\nentry ' + str(TEST.resolve()) + '\n' +
                        ''.join('module ' + n + ' ' + str(p.resolve()) + '\n' for n, p in sorted(modules.items())) +
                        ''.join('file ' + sha(p) + ' ' + str(p.resolve()) + '\n' for p in sorted(modules.values())))
        binary = out / name
        build = subprocess.run([str(amu), 'compile', str(TEST), '--source-lock', str(lock), '-o', str(binary)],
                               cwd=ROOT, env=env, capture_output=True, timeout=180)
        if build.returncode or build.stderr or not binary.is_file():
            raise RuntimeError(('native compile failed', name, build.returncode, build.stderr[-500:]))
        result = subprocess.run([str(binary)], cwd=ROOT, env=env, capture_output=True, timeout=180)
        if result.returncode or result.stderr or result.stdout != expected:
            raise RuntimeError(('native verdict mismatch', name, result.returncode, result.stdout, result.stderr[-500:]))
        return {'binarySha256': sha(binary), 'lockSha256': sha(lock)}

    positive = run('tree-test', TREE, b'42\n')
    text = TREE.read_text()
    needle = '(protected? (keyword-value tag))'
    if text.count(needle) != 1:
        raise ValueError('negative mutation anchor changed')
    mutant = out / 'mutated-tree.kotoba'
    mutant.write_text(text.replace(needle, 'false'))
    negative = run('tree-negative', mutant, b'7\n')
    no_translate = '(string=? (string-value (value (document-get second :translate))) "no")'
    if text.count(no_translate) != 1:
        raise ValueError('translate=no mutation anchor changed')
    mutant_no = out / 'mutated-translate-no.kotoba'
    mutant_no.write_text(text.replace(no_translate, 'false'))
    negative_no = run('tree-negative-translate-no', mutant_no, b'7\n')
    if any(sha(p) != pins[str(p)] for p in tracked):
        raise ValueError('source or compiler changed during tree gate')
    receipt = {'status': 'PASS_BOUNDED_NATIVE_LOCALE_TREE', 'inputs': pins,
               'positive': positive, 'negative': negative, 'negativeTranslateNo': negative_no,
               'wholeSiteGenerated': False, 'publicationQualified': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'])


if __name__ == '__main__':
    main()
