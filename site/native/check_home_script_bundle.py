#!/usr/bin/env python3
"""Observe native homepage script bundle against 17 historical locale pages."""
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
from check_footers import TAGS
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
class Scripts(HTMLParser):
    def __init__(self,fragment=False):
        super().__init__(convert_charrefs=True);self.in_body=fragment;self.inside=False;self.current=[];self.scripts=[]
    def handle_starttag(self,tag,attrs):
        if tag=='body':self.in_body=True
        if tag=='script' and self.in_body:self.inside=True;self.current=[]
    def handle_endtag(self,tag):
        if tag=='script' and self.inside:self.scripts.append(''.join(self.current));self.inside=False
        if tag=='body':self.in_body=False
    def handle_data(self,data):
        if self.inside:self.current.append(data)
def scripts(text,fragment=False):
    p=Scripts(fragment);p.feed(text);p.close()
    if p.inside or len(p.scripts)!=5:raise ValueError('expected exactly five scripts')
    return p.scripts
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','output'):ap.add_argument('--'+n,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
    compiler,packager,sha_source=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source))
    if sha(sha_source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    names=['home_script_bundle','home_scripts','home_play','home_hero_script','home_hero_svg','source_concat']
    modules=[(ROOT/'site/native'/(n+'.kotoba'),'src/site/native/'+n+'.kotoba') for n in names]
    modules += [(ROOT/'test/site/native/home_script_bundle_file.kotoba','src/site/native/home_script_bundle_file.kotoba'),(sha_source,'src/multiformats/sha256.kotoba')]
    inputs=[(ROOT/'site/generate.cljk','generator.cljk'),(ROOT/'site/src/kotoba/site/hero.cljk','hero.cljk'),(ROOT/'site/assets/play/double-21.kotoba','play.kotoba'),(ROOT/'site/assets/play/double-21.wasm','play.wasm'),(ROOT/'site/assets/play/double-21.wasm.provenance.edn','provenance.edn')]
    refs=[ROOT/'site/dist'/('' if tag=='en' else tag)/'index.html' for tag in TAGS]
    tracked=[compiler,packager,ROOT/'site/native/dependencies.json',ROOT/'site/native/check_footers.py',Path(__file__).resolve(),*[p for p,_ in modules],*[p for p,_ in inputs],*refs]
    pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,name in modules:
        q=out/name;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    for p,name in inputs:shutil.copyfile(p,out/name)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_script_bundle_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
    if fd!=3:os.dup2(fd,3)
    def run(image,*args,expected=0):
        r=subprocess.run([str(image),*args],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        if r.returncode!=expected or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r.stdout
    run(compiler,'compile','/src/site/native/home_script_bundle_file.kotoba','--target','aarch64-macos','--output','bundle.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=run(compiler,'extract-native','bundle.kseed','--symbol','main','--output','bundle.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not m or int(m[2])!=(out/'bundle.raw').stat().st_size:raise ValueError('invalid native metadata')
    run(packager,'bundle-unsigned.macho','bundle.raw','fs2-cli-stdio-general',m[1].decode())
    image=out/'bundle.macho';shutil.copyfile(out/'bundle-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    output=run(image,expected=42);actual=scripts(output.decode(),True)
    if b'freebuff' in output.lower() or b'bfcid' in output.lower():raise ValueError('removed attribution in bundle')
    replacements = (
        ('let c=vec2f(u.ux,u.uy)+vec2f(', 'let c=p+(vec2f(u.ux,u.uy)-vec2f(0.5,0.5))+vec2f('),
        ("buffers:[{arrayStride:4,attributes:", "buffers:[{arrayStride:4,stepMode:'instance',attributes:"),
        ('vec2 c=u+vec2(', 'vec2 c=a+(u-vec2(0.5))+vec2('),
        ('(ndc.y*0.5+0.5)*u.h', '(0.5-ndc.y*0.5)*u.h'),
        ('gl.useProgram(prog);var buf=', 'gl.useProgram(prog);gl.enable(gl.BLEND);gl.blendFuncSeparate(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA,gl.ONE,gl.ONE_MINUS_SRC_ALPHA);var buf='),
    )
    for tag,ref in zip(TAGS,refs):
        expected=scripts(ref.read_text())
        removed="if(window.freebuff){window.freebuff('conversion','trial_started');}"
        if expected[1].count(removed)!=1:raise ValueError('historical conversion hook changed')
        expected[1]=expected[1].replace(removed,'')
        for before,after in replacements:
            if expected[2].count(before)!=1:raise ValueError('historical shader form changed')
            expected[2]=expected[2].replace(before,after)
        if expected!=actual:raise ValueError(('script mismatch',tag))
    for _,name in inputs:
        q=out/name;old=q.read_bytes()
        try:q.write_bytes(old+b' ');run(image,expected=7)
        finally:q.write_bytes(old)
    if any(sha(p)!=pins[str(p)] for p in tracked):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_HOME_SCRIPT_BUNDLE','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'outputSha256':hashlib.sha256(output).hexdigest(),'referenceLocales':TAGS,'modifiedInputsRefused':True,'freebuffAbsent':True,'browserBehaviorQualified':False,'completeHomepage':False,'siteGenerated':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
