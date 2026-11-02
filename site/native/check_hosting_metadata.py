#!/usr/bin/env python3
"""Qualify native generation of hosting metadata from pinned site sources."""
import argparse
import base64
import hashlib
import json
import os
import platform
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','output'): ap.add_argument('--'+n,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'): raise ValueError('requires Darwin arm64')
    compiler,packager,source=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source))
    deps=ROOT/'site/native/dependencies.json'; headers=ROOT/'site/_headers'; generator=ROOT/'site/generate.cljk'; play=ROOT/'site/assets/play/double-21.kotoba'
    if sha(source)!=json.loads(deps.read_text())['dependencies'][0]['sha256']: raise ValueError('SHA dependency drift')
    for p in (compiler,packager): subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    modules=[(ROOT/'site/native/hosting_metadata.kotoba','src/site/native/hosting_metadata.kotoba'),(ROOT/'test/site/native/hosting_metadata_file.kotoba','src/site/native/hosting_metadata_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
    tracked=[compiler,packager,deps,Path(__file__).resolve(),headers,generator,play,*[p for p,_ in modules]];pins={str(p):sha(p) for p in tracked}
    out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,n in modules:
        q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/hosting_metadata_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    def run(image,*args,expected=0):
        fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
        if fd!=3: os.dup2(fd,3)
        try:r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        finally:
            if fd!=3:os.close(fd)
        if r.returncode!=expected or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r.stdout
    run(compiler,'compile','/src/site/native/hosting_metadata_file.kotoba','--target','aarch64-macos','--output','hosting.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=run(compiler,'extract-native','hosting.kseed','--symbol','main','--output','hosting.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not m or int(m[2])!=(out/'hosting.raw').stat().st_size:raise ValueError('invalid entry')
    run(packager,'hosting-unsigned.macho','hosting.raw','fs2-cli-stdio-general',m[1].decode());image=out/'hosting.macho';shutil.copyfile(out/'hosting-unsigned.macho',image);image.chmod(0o700)
    subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    for src,name in ((headers,'headers'),(generator,'generator'),(play,'play-source')):shutil.copyfile(src,out/name)
    digest=hashlib.sha256(play.read_bytes()).digest();cid='b'+base64.b32encode(b'\x01\x55\x12\x20'+digest).decode().lower().rstrip('=')
    expected={'headers':headers.read_bytes()+('\n/ipfs/'+cid+'\n  Content-Type: text/plain; charset=utf-8\n  Content-Disposition: inline; filename=hello.kotoba\n').encode(),
              'redirects':b'/zh /zh-Hans/ 301\n/zh/* /zh-Hans/:splat 301\n',
              'robots':b'User-agent: *\nAllow: /\n\nSitemap: https://kotoba-lang.org/sitemap.xml\n'}
    observations={}
    for kind,want in expected.items():
        result=run(image,kind,expected=42)
        if result!=want or b'freebuf' in result.lower():raise ValueError(('mismatch or Freebuff',kind))
        observations[kind]=hashlib.sha256(result).hexdigest()
    for name in ('headers','generator','play-source'):
        f=out/name;original=f.read_bytes()
        try:
            f.write_bytes(original+b' ')
            if run(image,'headers',expected=7):raise ValueError(('mutation emitted data',name))
        finally:f.write_bytes(original)
    if run(image,'unknown',expected=7):raise ValueError('unknown kind emitted data')
    if any(sha(Path(p))!=h for p,h in pins.items()):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_HOSTING_METADATA_MACOS','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'outputs':observations,'playSourceCid':cid,'sourceMutationRefused':True,'freebuffAbsentFromOutputs':True,'completeSiteGenerated':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
