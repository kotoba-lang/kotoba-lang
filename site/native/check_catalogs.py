#!/usr/bin/env python3
"""Stage native Amu catalog checks; this is not a site generation gate.

The catalog reader admits a bounded JSON string-object subset with unescaped
lowercase hash keys. Python stages raw input, locks and observations; the
native Kotoba executable performs parsing and comparison. Large raw argv is
used only for this Mac-host diagnostic; a portable file-capability host remains
required for a complete native site build.
"""

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / 'site/native/locale_catalog.kotoba'
PLACEHOLDERS = ROOT / 'site/native/locale_placeholders.kotoba'
TEST = ROOT / 'test/site/native/locale_catalog_test.kotoba'
ARGV = ROOT / 'test/site/native/locale_catalog_argv.kotoba'
TAGS = ('ar', 'arz', 'bn', 'de', 'es', 'fr', 'hi', 'id',
        'ja', 'ko', 'mr', 'pcm', 'pt', 'ru', 'ur', 'zh-Hans')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def lock_for(out, name, entry, entry_ns, catalog):
    modules = [('site.native.locale-catalog', catalog),
               ('site.native.locale-placeholders', PLACEHOLDERS), (entry_ns, entry)]
    files = sorted(p.resolve() for _, p in modules)
    text = 'KSL2\nentry ' + str(entry.resolve()) + '\n'
    text += ''.join('module ' + n + ' ' + str(p.resolve()) + '\n' for n, p in sorted(modules))
    text += ''.join('file ' + digest(p.read_bytes()) + ' ' + str(p) + '\n' for p in files)
    lock = out / (name + '.ksl')
    lock.write_text(text)
    return lock


def compile_native(amu, out, name, entry, entry_ns, catalog):
    lock = lock_for(out, name, entry, entry_ns, catalog)
    program = out / name
    result = subprocess.run([str(amu), 'compile', str(entry), '--source-lock', str(lock), '-o', str(program)],
                            cwd=ROOT, env={'PATH': '/nonexistent', 'LANG': 'C'},
                            capture_output=True, timeout=150, check=False)
    (out / (name + '.compile.stderr')).write_bytes(result.stderr)
    if result.returncode != 0 or result.stderr or not program.is_file():
        raise RuntimeError((name, 'compile', result.returncode, result.stderr[-500:]))
    return program


def observe(program, arguments, expected):
    result = subprocess.run([str(program), *arguments], cwd=ROOT,
                            env={'PATH': '/nonexistent', 'LANG': 'C'},
                            capture_output=True, timeout=150, check=False)
    if result.returncode != 0 or result.stdout != expected or result.stderr:
        raise RuntimeError(('native observation failed', result.returncode,
                            result.stdout[-100:], result.stderr[-300:]))
    return result.stdout.decode().strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--amu', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    amu = args.amu.resolve(strict=True)
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    tracked = (amu, CATALOG, PLACEHOLDERS, TEST, ARGV)
    identities = {str(path): digest(path.read_bytes()) for path in tracked}
    test_ns = 'site.native.locale-catalog-test'
    positive = compile_native(amu, out, 'catalog-test', TEST, test_ns, CATALOG)
    observe(positive, [], b'42\n')
    text = CATALOG.read_text()
    needle = '(and (= (vector-at s 0) 0) (= (vector-at t 0) 0) (>= (vector-at s 1) minimum) (> minimum 0)\n         (every-entry? source s target t))'
    if text.count(needle) != 1:
        raise ValueError('negative-control mutation anchor changed')
    mutated = out / 'negative.kotoba'
    mutated.write_text(text.replace(needle, 'true'))
    negative = compile_native(amu, out, 'catalog-negative', TEST, test_ns, mutated)
    observe(negative, [], b'7\n')
    program = compile_native(amu, out, 'catalog-argv', ARGV, 'site.native.locale-catalog-argv', CATALOG)
    source = (ROOT / 'site/i18n/en.json').read_bytes()

    def check(tag):
        target = (ROOT / 'site/i18n' / (tag + '.json')).read_bytes()
        result = observe(program, [source, target], b'42\n')
        print(tag, result, flush=True)
        return {'tag': tag, 'sha256': digest(target), 'result': result}

    with ThreadPoolExecutor(max_workers=3) as pool:
        observations = list(pool.map(check, TAGS))
    if any(digest(path.read_bytes()) != identities[str(path)] for path in tracked):
        raise ValueError('compiler or source changed during native catalog gate')
    receipt = {'status': 'PASS_NATIVE_CURRENT_CATALOG_SUBSET', 'compilerCliSha256': identities[str(amu)],
               'catalogReaderSha256': identities[str(CATALOG)],
               'placeholderReaderSha256': identities[str(PLACEHOLDERS)],
               'testSha256': identities[str(TEST)], 'argvEntrySha256': identities[str(ARGV)],
               'sourceSha256': digest(source), 'catalogs': observations,
               'unitAndMutationControls': True, 'unicodeEscapedValues': True,
               'escapedKeysSupported': False,
               'nativeFileHostQualified': False, 'renderedPagesValidated': False, 'siteGenerated': False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(receipt['status'], flush=True)


if __name__ == '__main__':
    main()
