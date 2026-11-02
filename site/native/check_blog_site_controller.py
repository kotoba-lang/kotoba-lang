#!/usr/bin/env python3
"""Qualify a native macOS controller for 17 blog pages and three static assets."""
import argparse
import hashlib
import json
import os
import platform
from pathlib import Path
import re
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','page-controller-dir','asset-stager-dir','head-dir','body-dir','reference-dir','output'):
        ap.add_argument('--'+n,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
    compiler,packager,source,page,asset,head,body,reference=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.page_controller_dir,a.asset_stager_dir,a.head_dir,a.body_dir,a.reference_dir))
    deps=ROOT/'site/native/dependencies.json'
    if sha(source)!=json.loads(deps.read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    page_image,asset_image,head_image,body_image=page/'controller.macho',asset/'stage.macho',head/'head.macho',body/'nav.macho'
    for d,p in ((page,page_image),(asset,asset_image),(head,head_image),(body,body_image)):
        if json.loads((d/'receipt.json').read_text())['binarySha256']!=sha(p):raise ValueError('component drift')
    ref=json.loads((reference/'receipt.json').read_text())
    if ref['status']!='PASS_NATIVE_BLOG_DOCUMENTS':raise ValueError('unqualified document reference')
    expected={x['tag']:x['sha256'] for x in ref['observations']}
    if set(expected)!=set(TAGS):raise ValueError('locale coverage mismatch')
    logo=ROOT/'site/assets/kotoba-wordmark.png';icon=ROOT/'site/assets/kotoba-favicon.ico';wasm=ROOT/'site/assets/play/double-21.wasm'
    modules=[(ROOT/'site/native/blog_site_controller.kotoba','src/site/native/blog_site_controller.kotoba'),(ROOT/'test/site/native/blog_site_controller_file.kotoba','src/site/native/blog_site_controller_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
    tracked=[compiler,packager,deps,Path(__file__).resolve(),*[p for p,_ in modules],page/'receipt.json',page_image,asset/'receipt.json',asset_image,head/'receipt.json',head_image,body/'receipt.json',body_image,reference/'receipt.json',logo,icon,wasm]
    pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,n in modules:
        q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/blog_site_controller_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    def build(image,*args):
        fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
        if fd!=3:os.dup2(fd,3)
        try:r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        finally:
            if fd!=3:os.close(fd)
        if r.returncode or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r.stdout
    build(compiler,'compile','/src/site/native/blog_site_controller_file.kotoba','--target','aarch64-macos','--output','site.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=build(compiler,'extract-native','site.kseed','--symbol','main','--output','site.raw');match=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not match or int(match[2])!=(out/'site.raw').stat().st_size:raise ValueError('invalid native entry')
    build(packager,'site-unsigned.macho','site.raw','amu-cli-host',match[1].decode())
    image=out/'site.macho';shutil.copyfile(out/'site-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    data={'page':str(page_image),'page-sha':sha(page_image),'asset':str(asset_image),'asset-sha':sha(asset_image),'head-dir':str(head),'head':str(head_image),'head-sha':sha(head_image),'body-dir':str(body),'body':str(body_image),'body-sha':sha(body_image),'logo':str(logo),'logo-sha':sha(logo),'icon':str(icon),'icon-sha':sha(icon),'wasm':str(wasm),'wasm-sha':sha(wasm)}
    def manifest(path,values):path.write_text('{'+ ' '.join(':'+k+' '+json.dumps(v,ensure_ascii=False) for k,v in values.items())+'}')
    def run(m,private,site,code):
        r=subprocess.run([str(image),str(m),str(private),str(site)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},capture_output=True,timeout=1800)
        if r.returncode!=code or r.stdout or r.stderr:raise RuntimeError((r.returncode,r.stdout[:100],r.stderr[-400:]))
    bad=out/'bad.edn';wrong=dict(data);wrong['logo-sha']='0'*64;manifest(bad,wrong)
    private=out/'private';site=out/'site';private.mkdir();site.mkdir()
    run(bad,private,site,7)
    if list(private.iterdir()) or list(site.iterdir()):raise ValueError('invalid manifest created output')
    good=out/'manifest.edn';manifest(good,data);run(good,private,site,42)
    wanted={('' if tag=='en' else tag+'/')+'blog/index.html' for tag in TAGS};wanted.update({'kotoba-wordmark.png','favicon.ico','play/double-21.wasm'})
    actual={str(p.relative_to(site)) for p in site.rglob('*') if p.is_file()}
    if actual!=wanted:raise ValueError(('unexpected site files',actual))
    for tag in TAGS:
        if sha(site/('' if tag=='en' else tag)/'blog/index.html')!=expected[tag]:raise ValueError(('page mismatch',tag))
    for src,dst in ((logo,site/'kotoba-wordmark.png'),(icon,site/'favicon.ico'),(wasm,site/'play/double-21.wasm')):
        if sha(src)!=sha(dst):raise ValueError('asset mismatch')
    before={str(p):sha(p) for p in site.rglob('*') if p.is_file()};run(good,private,site,7)
    if any(sha(Path(p))!=h for p,h in before.items()):raise ValueError('existing output modified')
    if any(sha(Path(p))!=h for p,h in pins.items()):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_BLOG_SITE_CONTROLLER_MACOS','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'locales':list(TAGS),'badDigestRefused':True,'existingStagePreserved':True,'nativeOrchestration':True,'privateImmutableStageRequired':True,'completeSiteGenerated':False,'linuxQualified':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])

if __name__=='__main__':main()
