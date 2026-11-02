#!/usr/bin/env python3
"""Locally check that a late competing staging file is never overwritten."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('controller-dir', 'head-dir', 'body-dir', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    controller, head, body = (p.resolve(strict=True) for p in (args.controller_dir, args.head_dir, args.body_dir))
    image, head_image, body_image = controller/'controller.macho', head/'head.macho', body/'nav.macho'
    for directory, binary in ((controller, image), (head, head_image), (body, body_image)):
        if json.loads((directory/'receipt.json').read_text())['binarySha256'] != sha(binary):
            raise ValueError('binary differs from qualified receipt')
    scratch = args.output.resolve()
    scratch.mkdir(parents=True, exist_ok=False)
    command = [str(image), 'en', str(head), str(head_image), sha(head_image), str(body), str(body_image), sha(body_image), str(scratch)]
    proc = subprocess.Popen(command, cwd=controller, env={'PATH':'/nonexistent','LANG':'C'}, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        deadline = time.monotonic() + 60
        while not (scratch/'head.out').exists() and proc.poll() is None and time.monotonic() < deadline:
            time.sleep(.05)
        if not (scratch/'head.out').exists():
            raise RuntimeError('child capture not observed')
        with (scratch/'index.part').open('xb') as f:
            f.write(b'preserve')
        stdout, stderr = proc.communicate(timeout=300)
        if proc.returncode != 7 or stdout or stderr or (scratch/'index.html').exists() or (scratch/'index.part').read_bytes() != b'preserve':
            raise RuntimeError(('staging race was not refused', proc.returncode, stdout[:100], stderr[:200]))
    finally:
        if proc.poll() is None:
            proc.kill()
            proc.communicate()
    print('PASS_NATIVE_BLOG_FILE_STAGING_RACE')


if __name__ == '__main__':
    main()
