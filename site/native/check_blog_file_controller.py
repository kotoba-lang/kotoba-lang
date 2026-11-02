#!/usr/bin/env python3
"""Qualify native Mac blog page file output against verified native parts."""
import argparse, hashlib, json, os, platform, re, shutil, subprocess
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','head-dir','body-dir','reference-dir','output'): ap.add_argument('--'+n,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'): raise ValueError('requires macOS arm64')
    compiler,packager,source,head,body,reference=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.head_dir,a.body_dir,a.reference_dir))
    if sha(source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']: raise ValueError('SHA dependency drift')
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    head_image=head/'head.macho';body_image=body/'nav.macho'
    for d,p in ((head,head_image),(body,body_image)):
        if sha(p)!=json.loads((d/'receipt.json').read_text())['binarySha256']: raise ValueError('component binary drift')
    ref=json.loads((reference/'receipt.json').read_text())
    if ref['status']!='PASS_NATIVE_BLOG_DOCUMENTS': raise ValueError('unqualified reference')
    modules=[(ROOT/'site/native/blog_file_controller.kotoba','src/site/native/blog_file_controller.kotoba'),(ROOT/'site/native/home_document.kotoba','src/site/native/home_document.kotoba'),(ROOT/'test/site/native/blog_file_controller_file.kotoba','src/site/native/blog_file_controller_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
    tracked=[compiler,packager,ROOT/'site/native/dependencies.json',Path(__file__).resolve(),*[p for p,_ in modules],head/'receipt.json',head_image,body/'receipt.json',body_image,reference/'receipt.json']
    pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,n in modules:
        q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/blog_file_controller_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    def build(image,*args,expected=0):
        fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
        if fd!=3:os.dup2(fd,3)
        try:r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        finally:
            if fd!=3:os.close(fd)
        if r.returncode!=expected or r.stderr: raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r.stdout
    build(compiler,'compile','/src/site/native/blog_file_controller_file.kotoba','--target','aarch64-macos','--output','controller.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=build(compiler,'extract-native','controller.kseed','--symbol','main','--output','controller.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not m or int(m[2])!=(out/'controller.raw').stat().st_size:raise ValueError('invalid native entry')
    build(packager,'controller-unsigned.macho','controller.raw','amu-cli-host',m[1].decode())
    image=out/'controller.macho';shutil.copyfile(out/'controller-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    expected={o['tag']:o['sha256'] for o in ref['observations']};head_hash=sha(head_image);body_hash=sha(body_image)
    def invoke(tag,scratch,head_dir=head,head_digest=head_hash,body_digest=body_hash,expected_exit=42):
        r=subprocess.run([str(image),tag,str(head_dir),str(head_image),head_digest,str(body),str(body_image),body_digest,str(scratch)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},capture_output=True,timeout=300)
        if r.returncode!=expected_exit or r.stderr:raise RuntimeError((tag,r.returncode,r.stderr[-500:]))
        return r.stdout
    def check(tag):
        scratch=out/'scratch'/tag;scratch.mkdir(parents=True)
        output=invoke(tag,scratch)
        if output:raise ValueError('file controller emitted stdout')
        output=(scratch/'index.html').read_bytes()
        if hashlib.sha256(output).hexdigest()!=expected[tag]:raise ValueError(('page differs from qualified fragments',tag))
        return {'tag':tag,'sha256':hashlib.sha256(output).hexdigest()}
    with ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(check,TAGS))
    for name,tag,hs,bs in [('invalid','../ja',head_hash,body_hash),('head-digest','en','0'*64,body_hash),('body-digest','en',head_hash,'0'*64)]:
        scratch=out/name;scratch.mkdir()
        if invoke(tag,scratch,head_digest=hs,body_digest=bs,expected_exit=7):raise ValueError('refusal emitted output')
        if list(scratch.iterdir()):raise ValueError('refusal created captures')
    occupied=out/'occupied';occupied.mkdir();(occupied/'head.out').write_text('preserve')
    if invoke('en',occupied,expected_exit=7) or (occupied/'head.out').read_text()!='preserve' or (occupied/'body.out').exists():raise ValueError('existing capture was altered')
    bad=out/'bad-head';shutil.copytree(head,bad);p=bad/'generator.cljk';p.write_bytes(p.read_bytes()+b' ')
    scratch=out/'bad-child';scratch.mkdir()
    if invoke('en',scratch,head_dir=bad,expected_exit=7) or (scratch/'body.out').exists():raise ValueError('failed child leaked page or ran body')
    occupied_page=out/'occupied-page';occupied_page.mkdir();(occupied_page/'index.html').write_text('preserve')
    if invoke('en',occupied_page,expected_exit=7) or (occupied_page/'index.html').read_text()!='preserve' or (occupied_page/'head.out').exists():raise ValueError('existing page was altered')
    occupied_temp=out/'occupied-temp';occupied_temp.mkdir();(occupied_temp/'index.part').write_text('preserve')
    if invoke('en',occupied_temp,expected_exit=7) or (occupied_temp/'index.part').read_text()!='preserve' or (occupied_temp/'head.out').exists() or (occupied_temp/'index.html').exists():raise ValueError('existing staging file was altered')
    if any(sha(p)!=pins[str(p)] for p in tracked):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_BLOG_FILE_CONTROLLER_MACOS','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'observations':results,'invalidTagAndDigestRefused':True,'existingCapturePreserved':True,'failedChildEmittedNoPage':True,'privateImmutableStageRequired':True,'renameAfterSuccessfulWrite':True,'fullSiteGenerated':False,'linuxQualified':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
