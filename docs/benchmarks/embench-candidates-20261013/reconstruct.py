"""Recover a preserved unity source. No compilation, execution or networking."""
from pathlib import Path
import argparse
import gzip
import hashlib
import json
import re


def require(condition, message):
    if not condition:
        raise ValueError(message)


p = argparse.ArgumentParser()
p.add_argument('--manifest', required=True)
p.add_argument('--base')
p.add_argument('--candidate', required=True)
p.add_argument('--output', required=True)
args = p.parse_args()
manifest = Path(args.manifest)
meta = json.loads(manifest.read_text())
if args.base:
    base = Path(args.base).read_bytes()
else:
    archive = (manifest.parent / meta['patchBaseline']['preservedArchive']).read_bytes()
    require(hashlib.sha256(archive).hexdigest() == meta['patchBaseline']['preservedArchiveSHA256'],
            'Frozen archive checksum mismatch')
    base = gzip.decompress(archive)
require(hashlib.sha256(base).hexdigest() == meta['patchBaseline']['sourceUnitySHA256'],
        'Frozen baseline checksum mismatch')
matches = [x for x in meta['candidates'] if x['id'] == args.candidate]
require(len(matches) == 1, 'Candidate must have exactly one manifest entry')
row = matches[0]
out = Path(args.output)
require(not out.exists(), 'Never overwrite an existing candidate or WIP')
result = base
if row['patch']:
    data = (manifest.parent / row['patch']).read_bytes()
    require(hashlib.sha256(data).hexdigest() == row['patchSHA256'], 'Patch checksum mismatch')
    src = base.decode().splitlines(True)
    lines = data.decode().splitlines(True)
    require(len(lines) >= 2 and lines[0].startswith('--- ') and lines[1].startswith('+++ '),
            'Invalid unified patch headers')
    res, cursor, i = [], 0, 2
    while i < len(lines):
        m = re.match(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@', lines[i])
        require(m is not None, 'Invalid unified patch hunk')
        old_count = int(m[2]) if m[2] is not None else 1
        new_count = int(m[4]) if m[4] is not None else 1
        start = int(m[1]) - (1 if old_count else 0)
        require(cursor <= start <= len(src), 'Invalid or overlapping source hunk')
        res.extend(src[cursor:start])
        cursor, old_seen, new_seen = start, 0, 0
        i += 1
        while i < len(lines) and not lines[i].startswith('@@ '):
            z = lines[i]
            require(z and z[0] in ' +-', 'Invalid unified patch line')
            if z[0] in ' -':
                require(cursor < len(src) and src[cursor] == z[1:], 'Source hunk mismatch')
                cursor += 1
                old_seen += 1
            if z[0] in ' +':
                res.append(z[1:])
                new_seen += 1
            i += 1
        require((old_seen, new_seen) == (old_count, new_count), 'Hunk length mismatch')
    res.extend(src[cursor:])
    result = ''.join(res).encode()
require(len(result) == row['sourceUnityBytes'] and
        hashlib.sha256(result).hexdigest() == row['sourceUnitySHA256'], 'Recovered source mismatch')
# Exclusive creation also protects a file created after the earlier existence check.
with out.open('xb') as stream:
    stream.write(result)
print('SOURCE_RECOVERED_ONLY', row['id'], row['sourceUnitySHA256'])
