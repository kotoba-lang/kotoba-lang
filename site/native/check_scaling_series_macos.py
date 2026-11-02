#!/usr/bin/env python3
"""Observe source-bound native scaling line geometry against the checked-in SVG."""
import argparse
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
class Svg(HTMLParser):
    def __init__(self):super().__init__();self.inside=False;self.depth=0;self.paths={};self.labels=[];self.in_label=False;self.ends=[];self.endkind=None
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='svg' and a.get('class')=='kot-lines':self.inside=True;self.depth=1;return
        if not self.inside:return
        self.depth+=1
        if tag=='path' and a.get('class')=='kot-line':self.paths[a['data-series']]=a['d']
        if tag=='text' and a.get('class')=='kot-line-label':self.in_label=True
        if tag=='g' and 'kot-end ' in a.get('class',''):self.endkind=a['class']
        if tag=='path' and self.endkind:self.ends.append((self.endkind,a['d']))
        if tag=='circle' and a.get('class')=='kot-end':self.ends.append(('circle',(a['cx'],a['cy'])))
    def handle_endtag(self,tag):
        if not self.inside:return
        if tag=='text':self.in_label=False
        if tag=='g':self.endkind=None
        self.depth-=1
        if self.depth==0:self.inside=False
    def handle_data(self,data):
        if self.inside and self.in_label:self.labels.append(data)

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for k in ('compiler','packager','sha256-source','json-core','output'):ap.add_argument('--'+k,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires native macOS ARM64')
    compiler,packager,hash_src,json_core=[p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.json_core)]
    dep=json.loads((ROOT/'site/native/dependencies.json').read_text());jdep=json.loads((ROOT/'site/native/json-dependencies.json').read_text())
    if sha(hash_src)!=dep['dependencies'][0]['sha256'] or sha(json_core)!=jdep['dependencies'][0]['sha256']:raise ValueError('dependency pin mismatch')
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    src=[(ROOT/'site/native/scaling_series.kotoba','src/site/native/scaling_series.kotoba'),(ROOT/'site/native/scaling_axis.kotoba','src/site/native/scaling_axis.kotoba'),
         (ROOT/'test/site/native/scaling_series_file.kotoba','src/site/native/scaling_series_file.kotoba'),(hash_src,'src/multiformats/sha256.kotoba'),(json_core,'src/json/core.kotoba')]
    report=ROOT/'bench/public-build-scaling/latest.json';reference=ROOT/'site/dist/index.html'
    tracked=[compiler,packager,report,reference,ROOT/'site/native/json-dependencies.json',*[p for p,_ in src],Path(__file__).resolve()];pins={str(p):sha(p) for p in tracked}
    for p,path in src:
        dest=out/path;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest)
    shutil.copyfile(report,out/'scaling-benchmark.json')
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/scaling_series_file.kotoba\nroot /src\n'+''.join('file '+sha(out/path)+' /'+path+'\n' for _,path in sorted(src,key=lambda x:x[1])))
    fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
    if fd!=3:os.dup2(fd,3)
    def run(image,*args,expected=0):
        r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        if r.returncode!=expected or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r
    run(compiler,'compile','/src/site/native/scaling_series_file.kotoba','--target','aarch64-macos','--output','p.kseed','--source-path','/src','--source-lock',lock.name,'--source-lock-sha256',sha(lock))
    result=run(compiler,'extract-native','p.kseed','--symbol','main','--output','p.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',result.stdout)
    if not m or int(m[2])!=(out/'p.raw').stat().st_size:raise ValueError('native entry mismatch')
    run(packager,'p-unsigned.macho','p.raw','fs2-cli-stdio-general',m[1].decode());image=out/'p.macho';shutil.copyfile(out/'p-unsigned.macho',image);image.chmod(0o700)
    subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    data=json.loads(run(image,expected=42).stdout);ref=Svg();ref.feed(reference.read_text());rows=data['series']
    if len(rows)!=7 or {r['id']:r['path'] for r in rows}!=ref.paths or sorted(r['label'] for r in rows)!=sorted(ref.labels):raise ValueError('series or labels differ from reference SVG')
    if {r['id']:r['end'] for r in rows}!={'kotoba-wasm-cli':'cross','amu-wasm':'bar','amu-native':'bar','rustc-wasm':'circle','rustc-native':'circle','clang-native':'circle','javac':'circle'}:raise ValueError('end-kind mismatch')
    for r in rows:
        end=re.search(r'[ML]([\d.]+) ([\d.]+)$',r['path'])
        if not end or f"{r['ex']:.1f}"!=end[1] or f"{r['ey']:.1f}"!=end[2]:raise ValueError(('endpoint mismatch',r['id']))
    modified=out/'scaling-benchmark.json';modified.write_bytes(modified.read_bytes()+b' ');run(image,expected=7)
    modified.write_text('['*128);run(image,expected=7)
    if any(sha(p)!=pins[str(p)] for p in tracked):raise ValueError('input drift')
    receipt={'status':'PASS_BOUNDED_SCALING_SERIES_GEOMETRY','inputs':pins,'seriesCount':len(rows),'pathsMatch':True,'siteGenerated':False,'completeSvg':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
