"""Recover a preserved unity source. No compilation, execution or networking."""
from pathlib import Path
import argparse, hashlib, json, re
p=argparse.ArgumentParser();p.add_argument('--manifest',required=True);p.add_argument('--base',required=True);p.add_argument('--candidate',required=True);p.add_argument('--output',required=True);args=p.parse_args()
manifest=Path(args.manifest);meta=json.loads(manifest.read_text());base=Path(args.base).read_bytes();assert hashlib.sha256(base).hexdigest()==meta['patchBaseline']['sourceUnitySHA256']
row=next(x for x in meta['candidates']if x['id']==args.candidate);out=Path(args.output);assert not out.exists(),'Never overwrite an existing candidate or WIP'
result=base
if row['patch']:
 data=(manifest.parent/row['patch']).read_bytes();assert hashlib.sha256(data).hexdigest()==row['patchSHA256'];src=base.decode().splitlines(True);lines=data.decode().splitlines(True);res=[];cursor=0;i=2
 while i<len(lines):
  m=re.match(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@',lines[i]);assert m;start=int(m[1])-1;res.extend(src[cursor:start]);cursor=start;i+=1
  while i<len(lines)and not lines[i].startswith('@@ '):
   z=lines[i];assert z[0]in ' +-'
   if z[0]in ' -':assert src[cursor]==z[1:];cursor+=1
   if z[0]in ' +':res.append(z[1:])
   i+=1
 res.extend(src[cursor:]);result=''.join(res).encode()
assert len(result)==row['sourceUnityBytes']and hashlib.sha256(result).hexdigest()==row['sourceUnitySHA256'];out.write_bytes(result);print('SOURCE_RECOVERED_ONLY',row['id'],row['sourceUnitySHA256'])
