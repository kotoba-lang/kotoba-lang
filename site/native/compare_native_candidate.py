#!/usr/bin/env python3
"""Compare complete candidate bytes with the dated native Mac Docs candidate."""
import argparse
import json
from pathlib import Path
from build_support import ROOT, sha


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--build',type=Path,required=True)
    a=p.parse_args()
    build=a.build.resolve(strict=True)
    baseline=ROOT/'docs/evidence/native-site-docs-candidate-20261106.json'
    expected=json.loads(baseline.read_text())['files']
    actual={str(p.relative_to(build/'candidate')):sha(p) for p in (build/'candidate').rglob('*') if p.is_file()}
    changed={n:dict(expected=expected.get(n),actual=actual.get(n)) for n in sorted(expected.keys()|actual.keys()) if expected.get(n)!=actual.get(n)}
    result=dict(status='PASS_NATIVE_HOST_BYTE_PARITY' if not changed else 'FAIL_NATIVE_HOST_BYTE_PARITY',
        baselineReceiptSha256=sha(baseline),buildReceiptSha256=sha(build/'receipt.json'),
        files=len(actual),differences=changed,published=False)
    (build/'host-parity.json').write_text(json.dumps(result,indent=2)+'\n')
    print(result['status'],len(actual),'files;',len(changed),'differences')
    if changed:raise SystemExit(1)


if __name__=='__main__':
    main()
