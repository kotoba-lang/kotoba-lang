#!/usr/bin/env python3
"""Observe native aggregate counts against the existing catalogue output."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import time
from check_footers import Tree

ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def expected_rows(html):
    t=Tree();t.feed(html);t.close();out=[]
    def name_of(n):
        if not isinstance(n,list):return None
        if dict(n[1]).get('class')=='kot-lib-name':return ''.join(x for x in n[2] if isinstance(x,str))
        for x in n[2]:
            found=name_of(x)
            if found is not None:return found
    def walk(n):
        if not isinstance(n,list):return
        a=dict(n[1])
        if n[0]=='li' and a.get('class')=='kot-lib':
            name=name_of(n)
            if not name or 'data-t' not in a:raise ValueError('catalogue row malformed')
            out.append((name,a['data-t']));return
        for x in n[2]:walk(x)
    walk(t.root)
    if len(t.stack)!=1 or len(out)!=2215:raise ValueError(('catalogue row count',len(out)))
    return out

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for name in ('compiler','packager','sha256-source','output'):ap.add_argument('--'+name,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS ARM64')
    compiler,packager,sha_source=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source))
    if sha(sha_source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']:raise ValueError('hash source drift')
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    files=[(ROOT/'site/native/library_classifier.kotoba','src/site/native/library_classifier.kotoba'),
           (ROOT/'site/native/library_aggregate_stream.kotoba','src/site/native/library_aggregate_stream.kotoba'),
           (ROOT/'test/site/native/library_aggregate_stream_file.kotoba','src/site/native/library_aggregate_stream_file.kotoba'),
           (ROOT/'site/native/library_catalog_stream.kotoba','src/site/native/library_catalog_stream.kotoba'),
           (sha_source,'src/multiformats/sha256.kotoba')]
    catalog=ROOT/'site/library-catalog.edn';taxonomy=ROOT/'site/library-taxonomy.edn';reference=ROOT/'site/dist/libraries/index.html'
    tracked=[compiler,packager,catalog,taxonomy,reference,ROOT/'site/src/kotoba/site/catalog.cljk',Path(__file__).resolve(),*[p for p,_ in files]]
    pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,dest in files:
        q=out/dest;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    for p in (catalog,taxonomy):shutil.copyfile(p,out/p.name)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/library_aggregate_stream_file.kotoba\nroot /src\n'+''.join('file '+sha(out/path)+' /'+path+'\n' for _,path in sorted(files,key=lambda x:x[1])))
    fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
    if fd!=3:os.dup2(fd,3)
    def run(image,*args,expected=0,timeout=900):
        r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=timeout)
        if r.returncode!=expected or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r
    run(compiler,'compile','/src/site/native/library_aggregate_stream_file.kotoba','--target','aarch64-macos','--output','page.kseed','--source-path','/src','--source-lock',lock.name,'--source-lock-sha256',sha(lock))
    r=run(compiler,'extract-native','page.kseed','--symbol','main','--output','page.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',r.stdout)
    if not m or int(m[2])!=(out/'page.raw').stat().st_size:raise ValueError('entry mismatch')
    run(packager,'page-unsigned.macho','page.raw','fs2-cli-stdio-general',m[1].decode())
    image=out/'page.macho';shutil.copyfile(out/'page-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    got={}
    started=time.monotonic()
    actual=run(image,expected=42)
    execution_seconds=round(time.monotonic()-started,3)
    for line in actual.stdout.decode().splitlines():
        name,sep,count=line.partition('\t')
        if not sep or name in got or not count.isdecimal():raise ValueError('native output malformed')
        got[name]=int(count)
    reference_rows=expected_rows(reference.read_text())
    counts={}
    for _,tags in reference_rows:
        for topic in tags.split():counts[topic]=counts.get(topic,0)+1
    total=got.pop('total',None);untagged=got.pop('untagged',None)
    if total!=len(reference_rows) or untagged!=sum(not tags for _,tags in reference_rows):raise ValueError(('total mismatch',total,untagged))
    if any(got.get(topic)!=count for topic,count in counts.items()) or any(counts.get(topic,0)!=count for topic,count in got.items()):
        raise ValueError(('aggregate mismatch',got,counts))
    for name in ('library-catalog.edn','library-taxonomy.edn'):
        q=out/name;original=q.read_bytes();q.write_bytes(original+b' ');run(image,expected=7);q.write_bytes(original)
    if any(sha(p)!=pins[str(p)] for p in tracked):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_STREAM_CATALOG_AGGREGATE','inputs':pins,'repos':total, 'tags':len(got), 'executionSeconds':execution_seconds,'siteGenerated':False,'currentGithubQualified':False,'generalRegexQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
