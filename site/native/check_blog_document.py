#!/usr/bin/env python3
"""Assemble qualified native blog head and body for 17 locales."""
import argparse, base64, hashlib, json, os, platform, re, shutil, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
class Shape(HTMLParser):
    def __init__(self): super().__init__();self.html=[];self.head=0;self.body=0
    def handle_starttag(self,tag,attrs):
        if tag=='html': self.html.append(dict(attrs))
        if tag=='head': self.head+=1
        if tag=='body': self.body+=1
def run(image,d,*args,expected=0):
    fd=os.open(d,os.O_RDONLY|os.O_DIRECTORY)
    if fd!=3: os.dup2(fd,3)
    try:r=subprocess.run([str(image),*map(str,args)],cwd=d,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
    finally:
        if fd!=3: os.close(fd)
    if r.returncode!=expected or r.stderr: raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
    return r.stdout
def fragment_worker(tag,dirs):
    result={}
    for name,directory,image in zip(('head','body'),map(Path,dirs),('head.macho','nav.macho')):
        data=run(directory/image,directory,tag,expected=42)
        obs=json.loads((directory/'receipt.json').read_text())['observations']
        expected=[o['sha256'] for o in obs if o['tag']==tag]
        if len(expected)!=1 or hashlib.sha256(data).hexdigest()!=expected[0]: raise ValueError(('fragment receipt mismatch',name,tag))
        result[name]=base64.b64encode(data).decode('ascii')
    return result
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','head-dir','body-dir','output'): ap.add_argument('--'+n,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'): raise ValueError('requires macOS arm64')
    compiler,packager,source,head_dir,body_dir=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.head_dir,a.body_dir))
    if sha(source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']: raise ValueError('SHA dependency drift')
    for p in (compiler,packager): subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    for d,n in ((head_dir,'head.macho'),(body_dir,'nav.macho')):
        if sha(d/n)!=json.loads((d/'receipt.json').read_text())['binarySha256']: raise ValueError('component binary drift')
    modules=[(ROOT/'site/native/blog_document_page.kotoba','src/site/native/blog_document_page.kotoba'),(ROOT/'site/native/home_document.kotoba','src/site/native/home_document.kotoba'),(ROOT/'test/site/native/blog_document_file.kotoba','src/site/native/blog_document_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
    tracked=[compiler,packager,ROOT/'site/native/dependencies.json',Path(__file__).resolve(),*[p for p,_ in modules],head_dir/'receipt.json',head_dir/'head.macho',body_dir/'receipt.json',body_dir/'nav.macho']
    pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,n in modules:
        q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/blog_document_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    run(compiler,out,'compile','/src/site/native/blog_document_file.kotoba','--target','aarch64-macos','--output','page.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=run(compiler,out,'extract-native','page.kseed','--symbol','main','--output','page.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not m or int(m[2])!=(out/'page.raw').stat().st_size: raise ValueError('invalid native entry')
    run(packager,out,'page-unsigned.macho','page.raw','fs2-cli-stdio-general',m[1].decode())
    image=out/'page.macho';shutil.copyfile(out/'page-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    def worker(tag):
        r=subprocess.run([sys.executable,str(Path(__file__).resolve()),'--fragment-worker',tag,str(head_dir),str(body_dir)],capture_output=True,timeout=1200,check=True)
        return tag,json.loads(r.stdout)
    with ThreadPoolExecutor(max_workers=3) as pool: fragments=dict(pool.map(worker,TAGS))
    def manifest(tag): (out/'document-manifest.edn').write_text('{:tag "'+tag+'" :head "'+sha(out/'head.html')+'" :body "'+sha(out/'body.html')+'"}')
    results=[]
    for tag in TAGS:
        for n,data in fragments[tag].items(): (out/(n+'.html')).write_bytes(base64.b64decode(data,validate=True))
        manifest(tag);output=run(image,out,tag,expected=42);text=output.decode();shape=Shape();shape.feed(text);shape.close()
        rtl=tag in ('ar','arz','ur');attrs={'lang':tag};attrs.update({'dir':'rtl'} if rtl else {})
        expected='<!DOCTYPE html>\n<html lang="'+tag+('" dir="rtl">' if rtl else '">')+(out/'head.html').read_text()+(out/'body.html').read_text()+'</html>'
        if text!=expected or shape.html!=[attrs] or shape.head!=1 or shape.body!=1 or 'KOTOBA_' in text or 'freebuff' in text.lower(): raise ValueError(('document mismatch',tag))
        path=out/'pages'/('' if tag=='en' else tag)/'blog'/'index.html';path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(output)
        results.append({'tag':tag,'sha256':hashlib.sha256(output).hexdigest()})
    for n in ('head.html','body.html'):
        q=out/n;old=q.read_bytes()
        try:q.write_bytes(old+b' ');r=run(image,out,TAGS[-1],expected=7)
        finally:q.write_bytes(old)
        if r: raise ValueError('partial output on digest refusal')
    manifest('ja')
    if run(image,out,TAGS[-1],expected=7): raise ValueError('tag refusal emitted output')
    manifest('../ja')
    if run(image,out,'../ja',expected=7): raise ValueError('locale refusal emitted output')
    for name,old,new in [('head.html','<head>','<bad>'),('body.html','</body>','</bad>')]:
        q=out/name;original=q.read_bytes();text=original.decode();q.write_text(text.replace(old,new,1));manifest(TAGS[-1])
        if run(image,out,TAGS[-1],expected=7): raise ValueError('structure refusal emitted output')
        q.write_bytes(original)
    if any(sha(p)!=pins[str(p)] for p in tracked): raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_BLOG_DOCUMENTS','inputs':pins,'observations':results,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'qualifiedFragmentsReplayed':True,'digestLocaleStructureRefusals':True,'fullSiteGenerated':False,'translationQualityQualified':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':
    if len(sys.argv)==5 and sys.argv[1]=='--fragment-worker': print(json.dumps(fragment_worker(sys.argv[2],sys.argv[3:])))
    else: main()
