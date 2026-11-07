"""Observe the lessons with the native macOS ARM64 Amu CLI.

Python only stages files, starts native processes, and records observations.
It does not read Kotoba forms, interpret them, or generate code.
"""
import argparse
import hashlib
import json
import platform
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--amu', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    amu = args.amu.resolve(strict=True)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    observations = []
    report = {
        'observedAt': datetime.now(timezone.utc).isoformat(),
        'host': {'system': platform.system(), 'machine': platform.machine()},
        'cliSHA256': digest(amu),
        'observerSHA256': digest(Path(__file__)),
        'cases': observations,
    }
    failure = False
    for source in sorted(Path(__file__).parent.glob('*.kotoba')):
        copied = output / source.name
        shutil.copyfile(source, copied)
        negative = source.name.startswith('negative-')
        for command in (['check'] if negative else ['check', 'run']):
            result = subprocess.run(
                [str(amu), command, str(copied)], cwd=output,
                capture_output=True, timeout=130,
            )
            if negative:
                diagnostic = {
                    'negative-name.kotoba': b'E2102',
                    'negative-operation.kotoba': b'E2101',
                    'negative-type.kotoba': b'E2104',
                }[source.name]
                passed = 0 < result.returncode < 128 and result.stdout == b'' and diagnostic in result.stderr
            else:
                passed = result.returncode == 0 and result.stderr == b'' and result.stdout == (b'ok\n' if command == 'check' else b'42\n')
            row = {
                'source': source.name, 'sourceSHA256': digest(copied),
                'command': command, 'exit': result.returncode,
                'stdout': result.stdout.decode('utf-8', errors='replace'),
                'stderr': result.stderr.decode('utf-8', errors='replace'),
                'passed': passed,
            }
            observations.append(row)
            (output / 'observations.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
            print(source.name, command, 'PASS' if passed else 'FAIL', flush=True)
            failure |= not passed
    raise SystemExit(1 if failure else 0)


if __name__ == '__main__':
    main()
