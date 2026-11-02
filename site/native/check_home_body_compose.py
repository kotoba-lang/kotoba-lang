#!/usr/bin/env python3
"""Exercise native body composition with verified parts and bounded controls."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','shell-dir','hero-dir','scripts-dir','output'):ap.add_argument('--'+n,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
    compiler,packager,sha_source,shell_dir,hero_dir,scripts_dir=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.shell_dir,a.hero_dir,a.scripts_dir))
    if sha(sha_source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    parts=[(shell_dir,'nav.macho',('en',)),(hero_dir,'nav.macho',('hero','en')),(scripts_dir,'bundle.macho',())]
    for d,name,_ in parts:
        receipt=json.loads((d/'receipt.json').read_text())
        if sha(d/name)!=receipt['binarySha256']:raise ValueError('component binary drift')
    modules=[(ROOT/'site/native/home_body_compose.kotoba','src/site/native/home_body_compose.kotoba'),(ROOT/'test/site/native/home_body_compose_file.kotoba','src/site/native/home_body_compose_file.kotoba'),(sha_source,'src/multiformats/sha256.kotoba')]
    tracked=[compiler,packager,ROOT/'site/native/dependencies.json',Path(__file__).resolve(),*[p for p,_ in modules],*[d/'receipt.json' for d,_,_ in parts],*[d/n for d,n,_ in parts]]
    pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,name in modules:
        q=out/name;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_body_compose_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    def run_in(image,d,*args,expected=0):
        fd=os.open(d,os.O_RDONLY|os.O_DIRECTORY)
        if fd!=3:os.dup2(fd,3)
        try:r=subprocess.run([str(image),*map(str,args)],cwd=d,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        finally:
            if fd!=3:os.close(fd)
        if r.returncode!=expected or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r.stdout
    run_in(compiler,out,'compile','/src/site/native/home_body_compose_file.kotoba','--target','aarch64-macos','--output','body.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=run_in(compiler,out,'extract-native','body.kseed','--symbol','main','--output','body.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not m or int(m[2])!=(out/'body.raw').stat().st_size:raise ValueError('invalid native entry')
    run_in(packager,out,'body-unsigned.macho','body.raw','fs2-cli-stdio-general',m[1].decode())
    image=out/'body.macho';shutil.copyfile(out/'body-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    for (directory,name,args),dest in zip(parts,('shell.html','hero.html','scripts.html')):
        (out/dest).write_bytes(run_in(directory/name,directory,*args,expected=42))
    # Placeholder-only fixture: real native chapter generation is still incomplete.
    (out/'chapters.html').write_text(''.join(f'<div class="kot-chapter-block">TEST CHAPTER {i}</div>' for i in range(1,6)))
    names=('shell','hero','chapters','scripts')
    def manifest(tag='en'):
        (out/'manifest.edn').write_text('{:tag "'+tag+'" '+ ' '.join(':'+n+' "'+sha(out/(n+'.html'))+'"' for n in names)+'}')
    manifest();got=run_in(image,out,'en',expected=42).decode()
    shell=(out/'shell.html').read_text();hero=(out/'hero.html').read_text();chapters=(out/'chapters.html').read_text();scripts=(out/'scripts.html').read_text()
    slots=''.join('<script>KOTOBA_'+n+'_SCRIPT_SLOT</script>' for n in ('SEARCH','PLAY','HERO','CHART','MORPH'))
    expected=shell.replace('KOTOBA_HERO_SLOT',hero).replace('KOTOBA_CHAPTERS_SLOT',chapters).replace(slots,scripts)
    if got!=expected or 'KOTOBA_' in got:raise ValueError('body composition mismatch')
    for n in names:
        p=out/(n+'.html');old=p.read_bytes()
        try:p.write_bytes(old+b' ');r=run_in(image,out,'en',expected=7)
        finally:p.write_bytes(old)
        if r:raise ValueError('partial output on digest refusal')
    manifest('ja')
    if run_in(image,out,'en',expected=7):raise ValueError('partial output on tag refusal')
    manifest('../ja')
    if run_in(image,out,'../ja',expected=7):raise ValueError('partial output on invalid locale')
    manifest()
    for name,old,new in [('shell','KOTOBA_HERO_SLOT','KOTOBA_HERO_SLOTKOTOBA_HERO_SLOT'),('chapters','class="kot-chapter-block"','class="other"'),('scripts','</script>','')]:
        p=out/(name+'.html');old_bytes=p.read_bytes();text=old_bytes.decode();p.write_text(text.replace(old,new,1));manifest()
        if run_in(image,out,'en',expected=7):raise ValueError('partial output on structure refusal')
        p.write_bytes(old_bytes)
    manifest()
    if any(sha(p)!=pins[str(p)] for p in tracked):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_HOME_BODY_COMPOSE','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'testBodySha256':hashlib.sha256(got.encode()).hexdigest(),'verifiedNativeParts':['shell','hero','scripts'],'fixtureChapters':True,'digestAndStructureRefusals':True,'fullHomepage':False,'siteGenerated':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
