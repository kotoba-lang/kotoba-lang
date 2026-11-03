#!/usr/bin/env python3
"""Compare one native compile-benchmark chart in all locales; not the full section."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
from check_footers import Tree, normalize, TAGS

ROOT = Path(__file__).resolve().parents[2]
MODULES = ['home_embench_panel']
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

def nodes(html):
    t=Tree();t.feed(html);t.close();found=[]
    def walk(n):
        if not isinstance(n,list):return
        if n[0]=='div' and dict(n[1]).get('class')=='kot-speed':found.append(n);return
        for c in n[2]:walk(c)
    walk(t.root)
    if len(t.stack)!=1 or len(found)!=1:raise ValueError('missing Embench panel')
    return normalize(found[0])

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for key in ('compiler', 'packager', 'sha256-source', 'html-core', 'html-hiccup', 'dds-components', 'dds-button', 'json-core', 'output'):
        ap.add_argument('--' + key, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'): raise ValueError('requires macOS arm64')
    compiler, packager, sha_source, core, hiccup, components, button, json_core = [p.resolve(strict=True) for p in
        (a.compiler, a.packager, a.sha256_source, a.html_core, a.html_hiccup, a.dds_components, a.dds_button, a.json_core)]
    dep = json.loads((ROOT/'site/native/dependencies.json').read_text())
    html = json.loads((ROOT/'site/native/html-dependencies.json').read_text())
    table = json.loads((ROOT/'site/native/dds-dependencies.json').read_text())
    header = json.loads((ROOT/'site/native/header-dependencies.json').read_text())
    jspec = json.loads((ROOT/'site/native/json-dependencies.json').read_text())
    if (sha(sha_source) != dep['dependencies'][0]['sha256'] or [sha(core), sha(hiccup)] != [x['sha256'] for x in html['dependencies']]
       or sha(components) != table['dependencies'][0]['sha256'] or sha(button) != header['sha256'] or sha(json_core) != jspec['dependencies'][0]['sha256']): raise ValueError('dependency digest drift')
    for p in (compiler,packager): subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    src=[(ROOT/'site/native'/(n+'.kotoba'),'src/site/native/'+n+'.kotoba') for n in MODULES]
    src += [(ROOT/'test/site/native/home_embench_panel_file.kotoba','src/site/native/home_embench_panel_file.kotoba'),
            (sha_source,'src/multiformats/sha256.kotoba'),(core,'src/html_core.kotoba'),(hiccup,'src/html_hiccup.kotoba'),
            (json_core,'src/json/core.kotoba'),(components,'src/components.kotoba'),(button,'src/button.kotoba')]
    catalogs=[ROOT/'site/i18n'/(tag+'.json') for tag in TAGS]
    refs=[ROOT/'site/dist'/('' if tag=='en' else tag)/'index.html' for tag in TAGS]
    generator=ROOT/'site/generate.cljk';report=ROOT/'site/benchmarks/embench-c-parity-20261015.json';chart=ROOT/'site/src/kotoba/site/chart.cljk'
    tracked=[compiler,packager,generator,report,chart,ROOT/'site/native/json-dependencies.json',*[p for p,_ in src],*catalogs,*refs,Path(__file__).resolve()]
    pins={str(p):sha(p) for p in tracked}
    for p,relative in src:
        target=out/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
    for p in catalogs:
        target=out/'site/i18n'/p.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
    shutil.copyfile(generator,out/'generator.cljk');shutil.copyfile(report,out/'embench.json');shutil.copyfile(chart,out/'chart.cljk')
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_embench_panel_file.kotoba\nroot /src\n'+''.join('file '+sha(out/path)+' /'+path+'\n' for _,path in sorted(src,key=lambda x:x[1])))
    fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
    if fd!=3: os.dup2(fd,3)
    def run(image,*args,expected=0):
        r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        if r.returncode!=expected or r.stderr: raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r
    run(compiler,'compile','/src/site/native/home_embench_panel_file.kotoba','--target','aarch64-macos','--output','page.kseed','--source-path','/src','--source-lock',lock.name,'--source-lock-sha256',sha(lock))
    result=run(compiler,'extract-native','page.kseed','--symbol','main','--output','page.raw')
    m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',result.stdout)
    if not m or int(m[2])!=(out/'page.raw').stat().st_size:raise ValueError('native entry mismatch')
    run(packager,'page-unsigned.macho','page.raw','fs2-cli-stdio-general',m[1].decode())
    image=out/'page.macho';shutil.copyfile(out/'page-unsigned.macho',image);image.chmod(0o700)
    subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    actual=run(image,expected=42).stdout.decode(); rendered=nodes(actual)
    results=[]
    for tag,ref in zip(TAGS,refs):
        if nodes(ref.read_text()) != rendered: raise ValueError(('panel mismatch',tag))
        results.append(tag)
    for name in ('generator.cljk','embench.json','chart.cljk'):
        target=out/name; original=target.read_bytes()
        try:
            target.write_bytes(original+b' ');run(image,expected=7)
        finally: target.write_bytes(original)
    if any(sha(p)!=pins[str(p)] for p in tracked):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_EMBENCH_HERO_PANEL','inputs':pins,'locales':results,'siteGenerated':False,'fullHomepage':False, 'translationQualityQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
