"""Stage exact sources and observe the canonical native contract validator."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import platform
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--compiler', type=Path, required=True)
parser.add_argument('--compiler-sha256', required=True)
parser.add_argument('--amu', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
assert platform.system() == 'Linux' and platform.machine() == 'aarch64'
repo = Path(__file__).resolve().parents[1]
amu, compiler, out = args.amu.resolve(), args.compiler.resolve(), args.output.resolve()
assert hashlib.sha256(compiler.read_bytes()).hexdigest() == args.compiler_sha256
out.mkdir()
pin = lambda path: dict(bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest())
entry = repo / 'test/native_cli_contract.kotoba'
files = [entry, *[amu / 'src/kotoba' / name for name in
                   ['form.kotoba', 'compiler/decimal_bits.kotoba', 'compiler/kotoba_reader.kotoba']]]
lock = out / 'sources.ksl'
lock.write_text('KSL1\nentry ' + str(entry) + '\nroot ' + str(amu / 'src') + '\n' +
                ''.join('file ' + pin(path)['sha256'] + ' ' + str(path) + '\n' for path in sorted(files)))
env = dict(PATH='/nonexistent', KEXE_ALLOW='35,37,38,39', KEXE_STACK_BYTES='8388608',
           KEXE_CAP_RESOURCES_35=':'.join(map(str, [out, repo, amu])))
calls = []
def run(name, argv):
    result = subprocess.run(list(map(str, argv)), cwd=out, env=env, capture_output=True, timeout=900)
    (out / (name + '.stdout')).write_bytes(result.stdout)
    (out / (name + '.stderr')).write_bytes(result.stderr)
    calls.append(dict(name=name, exit=result.returncode))
    assert result.returncode == 0 and not result.stderr, (name, result.returncode, result.stdout, result.stderr)

run('compile', [compiler, 'compile', entry, '--source-path', amu / 'src', '--source-lock', lock,
                '--source-lock-sha256', pin(lock)['sha256'], '--target', 'aarch64-macos', '--output', out / 'reader.kseed'])
run('pack', [compiler, 'compile', amu / 'seed/x86_64-kx64-runner.kotoba', '--target', 'aarch64-linux',
             '--output', out / 'reader.elf', '--format', 'packed', '--payload', out / 'reader.kseed'])
(out / 'reader.elf').chmod(0o755)
env['KEXE_CAP_RESOURCES_35'] = str(repo / 'lang')
run('surface', [out / 'reader.elf', repo / 'lang/surface-status.edn', 'surface'])
run('cli', [out / 'reader.elf', repo / 'lang/cli.edn', 'cli'])
receipt = dict(status='PASS_NATIVE_CLI_CONTRACT_ALL12_AND_FLAGS', compiler=pin(compiler), sourceLock=pin(lock),
               sources={('contract:' + str(path.relative_to(repo)) if path.is_relative_to(repo) else
                         'amu:' + str(path.relative_to(amu))): pin(path) for path in files},
               contract=pin(repo / 'lang/cli.edn'), surface=pin(repo / 'lang/surface-status.edn'),
               image=pin(out / 'reader.elf'), calls=calls, commandExecutionQualified=False,
               wholePublicCLIQualified=False, x86Executed=False)
(out / 'native-gate.json').write_text(json.dumps(receipt, indent=2) + '\n')
print('PASS_NATIVE_CLI_CONTRACT_ALL12_AND_FLAGS')
