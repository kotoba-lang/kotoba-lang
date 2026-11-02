#!/usr/bin/env python3
"""Run bounded catalogue scanner and negative-control fixtures natively on macOS ARM64."""
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
    for name in ('compiler','packager','output'):ap.add_argument('--'+name,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS ARM64')
    compiler,packager=(p.resolve(strict=True) for p in (a.compiler,a.packager))
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    source=ROOT/'site/native/library_catalog_stream.kotoba';fixture=ROOT/'test/site/native/library_catalog_stream_test.kotoba'
    tracked=[compiler,packager,source,fixture,Path(__file__).resolve()];pins={str(p):sha(p) for p in tracked}
    out=a.output.resolve();out.mkdir(parents=True,exist_ok=False);fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
    if fd!=3:os.dup2(fd,3)
    def invoke(image,*args,expected=0):
        r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=180)
        if r.returncode!=expected or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r
    observations=[]
    for name,mutant,want in [('positive',False,42),('negative',True,7)]:
        target=out/name/'src/site/native';target.mkdir(parents=True);shutil.copyfile(source,target/source.name)
        text=fixture.read_text();needle='(= (scan/start "{:other []}") -1)'
        if text.count(needle)!=1:raise ValueError('negative control anchor changed')
        if mutant:text=text.replace(needle,'(= (scan/start "{:other []}") 0)')
        (target/fixture.name).write_text(text);src='/' + name + '/src';entry=src+'/site/native/'+fixture.name
        files=sorted((out/name/'src').rglob('*.kotoba'));lock=out/(name+'.ksl')
        lock.write_text('KSL1\nentry '+entry+'\nroot '+src+'\n'+''.join('file '+sha(p)+' /'+str(p.relative_to(out))+'\n' for p in files))
        invoke(compiler,'compile',entry,'--target','aarch64-macos','--output',name+'.kseed','--source-path',src,'--source-lock',lock.name,'--source-lock-sha256',sha(lock))
        r=invoke(compiler,'extract-native',name+'.kseed','--symbol','main','--output',name+'.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',r.stdout)
        if not m or int(m[2])!=(out/(name+'.raw')).stat().st_size:raise ValueError('entry mismatch')
        invoke(packager,name+'.macho',name+'.raw','fs2-cli-stdio-general',m[1].decode());image=out/(name+'.macho');image.chmod(0o700)
        subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
        invoke(image,expected=want);observations.append({'case':name,'result':want,'imageSha256':sha(image),'lockSha256':sha(lock)})
    if any(sha(p)!=pins[str(p)] for p in tracked):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_CATALOG_STREAM_CASES','inputs':pins,'observations':observations,'generalRegexQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
