#!/usr/bin/env python3
"""Fetch only immutable official native compiler/source inputs for observation."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--profile', choices=['release-source', 'current-source'], default='release-source')
    a = p.parse_args()
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    spec = json.loads((ROOT / 'site/native/linux_toolchain.json').read_text())
    spec = {**spec, **spec['profiles'][a.profile]}
    fetched = []

    def fetch(repo, rev, source, target, expected):
        url = f'https://raw.githubusercontent.com/kotoba-lang/{repo}/{rev}/{source}'
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
        digest = hashlib.sha256(data).hexdigest()
        if digest != expected:
            raise ValueError('immutable input digest mismatch: ' + source)
        path = out / target
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        fetched.append(dict(url=url, path=target, bytes=len(data), sha256=digest))

    fetch('amu', spec['amuRevision'], spec['compilerPath'], 'amu.elf', spec['compilerSha256'])
    fetch('amu', spec['amuRevision'], spec['runnerSourcePath'], 'runner.kotoba', spec['runnerSourceSha256'])
    (out / 'amu.elf').chmod(0o700)
    pins = json.loads((ROOT / 'site/native/build-library-pins.json').read_text())
    for pin in pins.values():
        fetch('kotoba', spec['librariesRevision'], 'lib/' + pin['path'],
              'lib/' + pin['path'], pin['sha256'])
    (out / 'fetch.json').write_text(json.dumps(fetched, indent=2) + '\n')
    print('Fetched and verified', len(fetched), 'immutable inputs')


if __name__ == '__main__':
    main()
