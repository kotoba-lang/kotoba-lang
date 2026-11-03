#!/usr/bin/env python3
"""Check native macOS staging of real binary site assets and refusal paths."""
import argparse
import hashlib
import json
import os
import platform
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires macOS arm64')
    compiler, packager, source = (p.resolve(strict=True) for p in (args.compiler, args.packager, args.sha256_source))
    deps = ROOT/'site/native/dependencies.json'
    if sha(source) != json.loads(deps.read_text())['dependencies'][0]['sha256']:
        raise ValueError('SHA dependency drift')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    modules = [(ROOT/'site/native/asset_stage.kotoba','src/site/native/asset_stage.kotoba'),
               (ROOT/'test/site/native/asset_stage_file.kotoba','src/site/native/asset_stage_file.kotoba'),
               (source,'src/multiformats/sha256.kotoba')]
    assets = [ROOT/'site/assets/kotoba-wordmark.png', ROOT/'site/assets/kotoba-favicon.ico', ROOT/'site/assets/play/double-21.wasm']
    tracked = [compiler,packager,deps,Path(__file__).resolve(),*[p for p,_ in modules],*assets]
    pins = {str(p):sha(p) for p in tracked}
    out=args.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for src,name in modules:
        target=out/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,target)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/asset_stage_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    def build(image,*argv):
        fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
        if fd!=3:os.dup2(fd,3)
        try:r=subprocess.run([str(image),*map(str,argv)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        finally:
            if fd!=3:os.close(fd)
        if r.returncode!=0 or r.stderr:raise RuntimeError((image.name,argv,r.returncode,r.stderr[-500:]))
        return r.stdout
    build(compiler,'compile','/src/site/native/asset_stage_file.kotoba','--target','aarch64-macos','--output','stage.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=build(compiler,'extract-native','stage.kseed','--symbol','main','--output','stage.raw');match=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not match or int(match[2])!=(out/'stage.raw').stat().st_size:raise ValueError('invalid native entry')
    build(packager,'stage-unsigned.macho','stage.raw','amu-cli-host',match[1].decode())
    image=out/'stage.macho';shutil.copyfile(out/'stage-unsigned.macho',image);image.chmod(0o700)
    subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    stage=out/'assets';stage.mkdir()
    def run(src,digest,name,expected):
        r=subprocess.run([str(image),str(src),digest,str(stage),name],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},capture_output=True,timeout=300)
        if r.returncode!=expected or r.stdout or r.stderr:raise RuntimeError((name,r.returncode,r.stdout[:100],r.stderr[-300:]))
    observations=[]
    for p in assets:
        run(p,sha(p),p.name,42)
        if (stage/p.name).read_bytes()!=p.read_bytes() or (stage/(p.name+'.part')).exists():raise ValueError('asset mismatch')
        observations.append({'name':p.name,'sha256':sha(p)})
    run(assets[1],sha(assets[1]),'favicon.ico',42)
    if (stage/'favicon.ico').read_bytes()!=assets[1].read_bytes():raise ValueError('renamed asset mismatch')
    original=(stage/assets[0].name).read_bytes()
    run(assets[0],sha(assets[0]),assets[0].name,7)
    if (stage/assets[0].name).read_bytes()!=original:raise ValueError('existing output changed')
    for name,digest,src in [('bad.png','0'*64,assets[0]),('../escape',sha(assets[0]),assets[0]),('missing.png',sha(assets[0]),out/'missing')]:
        run(src,digest,name,7)
        if (stage/name).exists():raise ValueError('refusal created output')
    for name in ('', '.hidden', 'nested/file', 'x..png'):
        run(assets[0],sha(assets[0]),name,7)
    (stage/'busy.png.part').write_bytes(b'preserve');run(assets[0],sha(assets[0]),'busy.png',7)
    if (stage/'busy.png.part').read_bytes()!=b'preserve' or (stage/'busy.png').exists():raise ValueError('temporary file changed')
    if any(sha(p)!=v for p,v in ((Path(p),h) for p,h in pins.items())):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_ASSET_STAGE_MACOS','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'observations':observations,'digestPathAndExistingRefusals':True,'privateImmutableStageRequired':True,'completeSiteGenerated':False,'linuxQualified':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])


if __name__=='__main__':main()
