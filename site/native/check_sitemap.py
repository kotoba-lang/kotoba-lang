#!/usr/bin/env python3
"""Compare a native sitemap to canonical links in the existing static HTML."""
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
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for name in ('compiler','packager','output'):ap.add_argument('--'+name,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS ARM64')
    compiler,packager=(p.resolve(strict=True) for p in (a.compiler,a.packager))
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    pages=sorted((ROOT/'site/dist').rglob('*.html'));reference=ROOT/'site/dist/sitemap.xml'
    sources=[(ROOT/'site/native/sitemap.kotoba','src/site/native/sitemap.kotoba'),(ROOT/'test/site/native/sitemap_file.kotoba','src/site/native/sitemap_file.kotoba')]
    tracked=[compiler,packager,reference,Path(__file__).resolve(),*[p for p,_ in sources],*pages];pins={str(p):sha(p) for p in tracked}
    out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,dest in sources:
        target=out/dest;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
    files=[];urls=[]
    for i,p in enumerate(pages):
        dest=out/'pages'/f'{i:04}.html';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest);files.append('/pages/'+dest.name)
        urls.extend(re.findall(r'rel="canonical" href="([^"]+)"',p.read_text()))
    expected='<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'+''.join('<url><loc>'+x+'</loc></url>\n' for x in sorted(set(urls)))+'</urlset>\n'
    if expected!=reference.read_text():raise ValueError('existing sitemap disagrees with source HTML')
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/sitemap_file.kotoba\nroot /src\n'+''.join('file '+sha(out/path)+' /'+path+'\n' for _,path in sorted(sources,key=lambda x:x[1])))
    fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
    if fd!=3:os.dup2(fd,3)
    def run(image,*args,expected=0):
        r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        if r.returncode!=expected or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r
    run(compiler,'compile','/src/site/native/sitemap_file.kotoba','--target','aarch64-macos','--output','page.kseed','--source-path','/src','--source-lock',lock.name,'--source-lock-sha256',sha(lock))
    r=run(compiler,'extract-native','page.kseed','--symbol','main','--output','page.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',r.stdout)
    if not m or int(m[2])!=(out/'page.raw').stat().st_size:raise ValueError('entry mismatch')
    run(packager,'page-unsigned.macho','page.raw','fs2-cli-stdio-general',m[1].decode())
    image=out/'page.macho';shutil.copyfile(out/'page-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    def manifest(paths): (out/'manifest.edn').write_text(json.dumps(paths,ensure_ascii=True))
    manifest(files);actual=run(image,expected=42).stdout.decode()
    if actual!=expected:raise ValueError('native sitemap mismatch')
    (out/'fixture.html').write_text('<link rel="canonical" href="https://kotoba-lang.org/test/">')
    manifest(['/fixture.html','/fixture.html']);r=run(image,expected=42).stdout.decode()
    if r.count('<loc>https://kotoba-lang.org/test/</loc>')!=1:raise ValueError('deduplication failed')
    for html in ('<p>no canonical</p>', '<link rel="canonical" href="https://kotoba-lang.org.evil/x">', '<link rel="canonical" href="https://kotoba-lang.org/a&b">', '<link rel="canonical" href="https://kotoba-lang.org/a"><link rel="canonical" href="https://kotoba-lang.org/b">'):
        (out/'fixture.html').write_text(html);manifest(['/fixture.html']);run(image,expected=7)
    if any(sha(p)!=pins[str(p)] for p in tracked):raise ValueError('source drift')
    receipt={'status':'PASS_NATIVE_SITEMAP_PROJECTION','inputs':pins,'htmlCount':len(pages),'canonicalCount':len(set(urls)),'siteGenerated':False,'fullSiteQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
