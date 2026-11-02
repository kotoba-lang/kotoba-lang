#!/usr/bin/env python3
"""Observe native document assembly using native head and a fixture body."""
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
class Shape(HTMLParser):
    def __init__(self):super().__init__();self.html=[];self.head=0;self.body=0
    def handle_starttag(self,tag,attrs):
        if tag=='html':self.html.append(dict(attrs))
        if tag=='head':self.head+=1
        if tag=='body':self.body+=1
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','head-dir','body-dir','output'):ap.add_argument('--'+n,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
    compiler,packager,sha_source,head_dir,body_dir=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.head_dir,a.body_dir))
    if sha(sha_source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    for d,n in ((head_dir,'head.macho'),(body_dir,'body.macho')):
        if sha(d/n)!=json.loads((d/'receipt.json').read_text())['binarySha256']:raise ValueError('component binary drift')
    modules=[(ROOT/'site/native/home_document.kotoba','src/site/native/home_document.kotoba'),(ROOT/'test/site/native/home_document_file.kotoba','src/site/native/home_document_file.kotoba'),(sha_source,'src/multiformats/sha256.kotoba')]
    tracked=[compiler,packager,ROOT/'site/native/dependencies.json',Path(__file__).resolve(),*[p for p,_ in modules],head_dir/'receipt.json',head_dir/'head.macho',body_dir/'receipt.json',body_dir/'body.macho']
    pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,name in modules:
        q=out/name;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_document_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    def run(image,d,*args,expected=0):
        fd=os.open(d,os.O_RDONLY|os.O_DIRECTORY)
        if fd!=3:os.dup2(fd,3)
        try:r=subprocess.run([str(image),*map(str,args)],cwd=d,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        finally:
            if fd!=3:os.close(fd)
        if r.returncode!=expected or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r.stdout
    run(compiler,out,'compile','/src/site/native/home_document_file.kotoba','--target','aarch64-macos','--output','page.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=run(compiler,out,'extract-native','page.kseed','--symbol','main','--output','page.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not m or int(m[2])!=(out/'page.raw').stat().st_size:raise ValueError('invalid native entry')
    run(packager,out,'page-unsigned.macho','page.raw','fs2-cli-stdio-general',m[1].decode())
    image=out/'page.macho';shutil.copyfile(out/'page-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    (out/'head.html').write_bytes(run(head_dir/'head.macho',head_dir,'en',expected=42))
    (out/'body.html').write_bytes(run(body_dir/'body.macho',body_dir,'en',expected=42))
    def manifest(tag):
        (out/'document-manifest.edn').write_text('{:tag "'+tag+'" :head "'+sha(out/'head.html')+'" :body "'+sha(out/'body.html')+'"}')
    manifest('en');output=run(image,out,'en',expected=42);doc=output.decode();p=Shape();p.feed(doc);p.close()
    if p.html!=[{'lang':'en'}] or p.head!=1 or p.body!=1 or 'KOTOBA_' in doc or 'TEST CHAPTER 5' not in doc:raise ValueError('document structure mismatch')
    for name in ('head.html','body.html'):
        q=out/name;old=q.read_bytes()
        try:q.write_bytes(old+b' ');r=run(image,out,'en',expected=7)
        finally:q.write_bytes(old)
        if r:raise ValueError('partial output on digest refusal')
    manifest('ja')
    if run(image,out,'en',expected=7):raise ValueError('tag mismatch emitted output')
    manifest('../ja')
    if run(image,out,'../ja',expected=7):raise ValueError('invalid locale emitted output')
    manifest('ar');rtl=run(image,out,'ar',expected=42).decode();p=Shape();p.feed(rtl);p.close()
    if p.html!=[{'lang':'ar','dir':'rtl'}]:raise ValueError('RTL document metadata mismatch')
    for name,old,new in [('head.html','<head>','<bad>'),('body.html','</body>','</bad>'),('body.html','TEST CHAPTER 1','KOTOBA_HERO_SLOT')]:
        q=out/name;original=q.read_bytes();text=original.decode()
        if text.count(old)!=1:raise ValueError('fixture anchor mismatch')
        q.write_text(text.replace(old,new,1));manifest('en')
        if run(image,out,'en',expected=7):raise ValueError('partial output on structure refusal')
        q.write_bytes(original)
    manifest('en')
    if any(sha(p)!=pins[str(p)] for p in tracked):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_HOME_DOCUMENT','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'fixtureHtmlSha256':hashlib.sha256(output).hexdigest(),'headNative':True,'bodyContainsSyntheticChapters':True,'digestLocaleStructureRefusals':True,'fullSiteGenerated':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
