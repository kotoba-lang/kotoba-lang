#!/usr/bin/env python3
"""Observe Linux native execution and the current site's actual source closures.

No rendering, source rewriting, hosted compiler or publication occurs here.
Native verdicts and refusal observations remain separate from compilation.
The existing macOS-only generator and retained CLJK route are unchanged.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import struct
import subprocess
import time
from build_support import ALIASES, ROOT, sha, write_bytes


def static_elf(path):
    data = path.read_bytes()
    if data[:7] != b'\x7fELF\x02\x01\x01' or struct.unpack_from('<HH', data, 16) != (2, 62):
        raise ValueError('expected x86-64 ET_EXEC ELF')
    offset = struct.unpack_from('<Q', data, 32)[0]
    width, count = struct.unpack_from('<HH', data, 54)
    if width < 56 or offset + width * count > len(data):
        raise ValueError('invalid ELF program table')
    if any(struct.unpack_from('<I', data, offset + i * width)[0] in (2, 3) for i in range(count)):
        raise ValueError('dynamic section or interpreter is forbidden')
    return sha(path)


class Observation:
    def __init__(self, out, compiler):
        self.out, self.compiler = out, compiler
        self.calls = []

    def invoke(self, name, image, folder, argv=(), allow='35,37,38,39'):
        env = dict(PATH='/nonexistent', LANG='C', KEXE_ALLOW=allow,
                   KEXE_CAP_RESOURCES_35=str(folder), KEXE_STACK_BYTES='8388608',
                   KEXE_FUEL='off', KEXE_STRING_POOL='268435456')
        start = time.monotonic()
        try:
            r = subprocess.run([str(image), *map(str, argv)], cwd=folder, env=env,
                               capture_output=True, timeout=900)
            code, stdout, stderr = r.returncode, r.stdout, r.stderr
        except subprocess.TimeoutExpired as e:
            code, stdout, stderr = None, e.stdout or b'', e.stderr or b''
        write_bytes(self.out / 'logs' / (name + '.stdout'), stdout)
        write_bytes(self.out / 'logs' / (name + '.stderr'), stderr)
        row = dict(name=name, imageSha256=sha(image), argv=list(map(str, argv)),
                   exit=code, seconds=time.monotonic()-start,
                   stdoutSha256=hashlib.sha256(stdout).hexdigest(),
                   stderrSha256=hashlib.sha256(stderr).hexdigest(), stderrTail=stderr[-600:].decode(errors='replace'),
                   PATH=env['PATH'], allow=allow, scope=str(folder))
        self.calls.append(row)
        (self.out / 'calls.json').write_text(json.dumps(self.calls, indent=2) + '\n')
        return row, stdout, stderr

    def compile(self, name, sources, entry, native=True):
        folder = self.out / name
        folder.mkdir()
        for target, data in sources.items():
            write_bytes(folder / target, data)
        entry_path = folder / entry
        lock = folder / 'source.ksl'
        lock.write_text('KSL1\nentry ' + str(entry_path) + '\nroot ' + str(folder / 'src') + '\n' +
                        ''.join('file ' + sha(folder / n) + ' ' + str(folder / n) + '\n' for n in sorted(sources)))
        image = folder / ('entry.elf' if native else 'entry.kx64')
        row, _, stderr = self.invoke(name + '-compile', self.compiler, folder,
            ['compile', entry_path, '--target', 'x86_64-linux', '--output', image,
             '--format', 'static' if native else 'kx64-v1', '--source-path', folder / 'src',
             '--source-lock', lock, '--source-lock-sha256', sha(lock)])
        row = dict(row, sourceLockSha256=sha(lock), sources={n:sha(folder/n) for n in sources},
                   artifactExists=image.is_file())
        row['compiled'] = row['exit'] == 0 and not stderr and image.is_file()
        if row['compiled']:
            if native:
                static_elf(image)
                image.chmod(0o700)
            row['artifactSha256'] = sha(image)
        return row, image, folder


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--inputs', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    if (platform.system(), platform.machine()) != ('Linux', 'x86_64'):
        raise ValueError('native Linux x86-64 required; emulation is not qualification')
    inputs = a.inputs.resolve(strict=True)
    spec = json.loads((ROOT / 'site/native/linux_toolchain.json').read_text())
    compiler = inputs / 'amu.elf'
    if static_elf(compiler) != spec['compilerSha256']:
        raise ValueError('unqualified observation compiler bytes')
    pins = json.loads((ROOT / 'site/native/build-library-pins.json').read_text())
    for n, path in ALIASES.items():
        if pins[n]['path'] != path or sha(inputs / 'lib' / path) != pins[n]['sha256']:
            raise ValueError('library input drift: ' + n)
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    o = Observation(out, compiler)
    report = dict(status='LINUX_NATIVE_OBSERVATION_INCOMPLETE', host=dict(system=platform.system(),
        machine=platform.machine(), kernel=platform.release()), compilerSha256=sha(compiler),
        amuRevision=spec['amuRevision'], nativeChecks=[], components=[], fullSiteGenerated=False,
        publicationQualified=False, hostedFallback=False, cljkInvoked=False,
        macosBuilderChanged=False, limitations=['The upstream toolchain descriptor is a candidate, not complete compiler qualification.'])

    def save():
        (out / 'receipt.json').write_text(json.dumps(report, indent=2) + '\n')
    save()

    # Actual native execution, including the negative f64 comparison that was
    # a historical site blocker. A compile-success alone cannot pass this.
    fixtures = {
      'float-negative-order': '(ns probe (:export [main]))\n(defn main [] :i64 (if (< -2.0 -1.0) 42 7))\n',
      'scoped-read': '(ns probe (:export [main]) (:capabilities [:cli/args :fs/app-data]))\n(defn main [] :i64 (if (string=? (typed-cap-call :fs/app-data :string :string (typed-cap-call :cli/args :string :string "0")) "native-input") 42 7))\n'
    }
    required = True
    for name, source in fixtures.items():
        row, image, folder = o.compile(name, {'src/probe.kotoba':source.encode()}, 'src/probe.kotoba')
        item = dict(name=name, compilation=row, passed=False)
        if row['compiled']:
            target = folder / 'input.txt'
            target.write_text('native-input')
            run, stdout, stderr = o.invoke(name + '-run', image, folder, [str(target)] if name == 'scoped-read' else ())
            item['execution'] = run
            item['passed'] = run['exit'] == 42 and not stdout and not stderr
            if name == 'scoped-read':
                sibling = out / 'outside-scope.txt'
                sibling.write_text('native-input')
                refusal, stdout, stderr = o.invoke(name + '-outside', image, folder, [str(sibling)])
                item['outsideScopeRefusal'] = refusal
                item['passed'] &= refusal['exit'] not in (None, 0, 42) and not stdout and b'KEXE_TRAP' in stderr
        required &= item['passed']
        report['nativeChecks'].append(item)
        save()

    for mutant in (False, True):
        name = 'sitemap-negative' if mutant else 'sitemap-positive'
        source = (ROOT / 'test/site/native/sitemap_test.kotoba').read_bytes()
        if mutant:
            needle = b'https://kotoba-lang.org/a/</loc>'
            if source.count(needle) != 1:
                raise ValueError('sitemap mutation anchor changed')
            source = source.replace(needle, b'https://kotoba-lang.org/wrong/</loc>')
        row, image, folder = o.compile(name, {
          'src/site/native/sitemap.kotoba':(ROOT / 'site/native/sitemap.kotoba').read_bytes(),
          'src/site/native/sitemap_test.kotoba':source}, 'src/site/native/sitemap_test.kotoba')
        item = dict(name=name, compilation=row, passed=False)
        if row['compiled']:
            run, stdout, stderr = o.invoke(name + '-run', image, folder, allow='-')
            item['execution'] = run
            item['passed'] = run['exit'] == (7 if mutant else 42) and not stdout and not stderr
        required &= item['passed']
        report['nativeChecks'].append(item)
        save()

    # Observe every real producer closure without changing a source spelling.
    # KX64 compilation isolates project/admission/backend support from the
    # separately unported virtual-path runtime bridge; it is not execution.
    recipes = json.loads((ROOT / 'site/native/build-components.json').read_text())
    for name, recipe in recipes.items():
        sources = {'src/site/native/'+n+'.kotoba':(ROOT/'site/native'/(n+'.kotoba')).read_bytes() for n in recipe['modules']}
        for n in recipe['entries']:
            sources[n] = (ROOT/'test/site/native'/Path(n).name).read_bytes()
        for n in recipe['aliases']:
            sources['src/'+n] = (inputs/'lib'/ALIASES[n]).read_bytes()
        row, _, _ = o.compile('component-'+name, sources, recipe.get('entry') or recipe['entries'][0], native=False)
        report['components'].append(dict(name=name, **row))
        print(name, 'compiled' if row['compiled'] else 'BLOCKED', flush=True)
        save()
    report.update(status='PASS_LINUX_NATIVE_PREFLIGHT_OBSERVATION' if required else 'FAIL_LINUX_NATIVE_PREFLIGHT_OBSERVATION',
                  compiledComponents=sum(x['compiled'] for x in report['components']), totalComponents=len(recipes),
                  allProducerClosuresCompiled=all(x['compiled'] for x in report['components']),
                  remainingBlockers=['Current producers use macOS FD3 virtual absolute paths; Linux uses explicitly scoped real paths.',
                    'Full-site rendering, 119 page/16 catalog checks, output parity and native controller are not yet qualified on Linux.',
                    'No production build/deploy trigger is enabled by this observation.'])
    save()
    if sha(compiler) != spec['compilerSha256']:
        raise ValueError('compiler changed during observation')
    print(report['status'], report['compiledComponents'], '/', report['totalComponents'], flush=True)
    if not required:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
