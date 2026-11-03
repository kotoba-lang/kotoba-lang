#!/usr/bin/env python3
"""Verify and persist the native runtime subsection in all locales; not the full benchmark."""
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
MODULES = ['home_benchmark_runtime_section', 'home_benchmark_runtime_charts', 'home_benchmark_runtime_pairs', 'home_benchmark_optimization', 'locale_tree', 'locale_filter', 'locale_message', 'locale_catalog', 'locale_placeholders', 'source_concat', 'home_section', 'locale_links', 'locale_rebase']
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

def nodes(html, fragment):
    t = Tree(); t.feed(html); t.close()
    if len(t.stack) != 1: raise ValueError('invalid HTML')
    selected = []
    def walk(n, inside):
        if not isinstance(n, list): return
        a = dict(n[1]); inside = inside or (n[0] == 'section' and a.get('id') == 'benchmark')
        if n[0] == 'div' and a.get('class') == 'kot-table-scroll' and (fragment or inside): selected.append(n); return
        for c in n[2]: walk(c, inside)
    walk(t.root, False)
    return selected

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for key in ('compiler', 'packager', 'sha256-source', 'html-core', 'html-hiccup', 'dds-table', 'dds-button', 'dds-data-table', 'json-core', 'output'):
        ap.add_argument('--' + key, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'): raise ValueError('requires macOS arm64')
    compiler, packager, sha_source, core, hiccup, dds_table, dds_button, dds_data_table, json_core = [p.resolve(strict=True) for p in
        (a.compiler, a.packager, a.sha256_source, a.html_core, a.html_hiccup, a.dds_table, a.dds_button, a.dds_data_table, a.json_core)]
    dep = json.loads((ROOT/'site/native/dependencies.json').read_text())
    html = json.loads((ROOT/'site/native/html-dependencies.json').read_text())
    table = json.loads((ROOT/'site/native/dds-dependencies.json').read_text())
    jspec = json.loads((ROOT/'site/native/json-dependencies.json').read_text())
    if (sha(sha_source) != dep['dependencies'][0]['sha256'] or [sha(core), sha(hiccup)] != [x['sha256'] for x in html['dependencies']]
       or sha(dds_table) != table['dependencies'][0]['sha256'] or sha(dds_data_table) != table['dependencies'][1]['sha256'] or sha(dds_button) != json.loads((ROOT/'site/native/header-dependencies.json').read_text())['sha256'] or sha(json_core) != jspec['dependencies'][0]['sha256']): raise ValueError('dependency digest drift')
    for p in (compiler,packager): subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    src=[(ROOT/'site/native'/(n+'.kotoba'),'src/site/native/'+n+'.kotoba') for n in MODULES]
    src += [(ROOT/'test/site/native/home_benchmark_runtime_section_file.kotoba','src/site/native/home_benchmark_runtime_section_file.kotoba'),
            (sha_source,'src/multiformats/sha256.kotoba'),(core,'src/html_core.kotoba'),(hiccup,'src/html_hiccup.kotoba'),
            (dds_table,'src/components.kotoba'),(dds_data_table,'src/table.kotoba'),(json_core,'src/json/core.kotoba')]
    catalogs=[ROOT/'site/i18n'/(tag+'.json') for tag in TAGS]
    refs=[ROOT/'site/dist'/('' if tag=='en' else tag)/'index.html' for tag in TAGS]
    generator=ROOT/'site/generate.cljk';report=ROOT/'bench/public-runtime-comparison/latest.json';chart=ROOT/'site/src/kotoba/site/chart.cljk'
    tracked=[compiler,packager,generator,report,chart,ROOT/'site/native/json-dependencies.json',*[p for p,_ in src],*catalogs,*refs,Path(__file__).resolve()]
    pins={str(p):sha(p) for p in tracked}
    for p,relative in src:
        target=out/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
    for p in catalogs:
        target=out/'site/i18n'/p.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target)
    shutil.copyfile(generator,out/'generator.cljk');shutil.copyfile(report,out/'runtime-benchmark.json');shutil.copyfile(chart,out/'chart.cljk')
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_benchmark_runtime_section_file.kotoba\nroot /src\n'+''.join('file '+sha(out/path)+' /'+path+'\n' for _,path in sorted(src,key=lambda x:x[1])))
    fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
    if fd!=3: os.dup2(fd,3)
    def run(image,*args,expected=0):
        r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        if r.returncode!=expected or r.stderr: raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r
    run(compiler,'compile','/src/site/native/home_benchmark_runtime_section_file.kotoba','--target','aarch64-macos','--output','page.kseed','--source-path','/src','--source-lock',lock.name,'--source-lock-sha256',sha(lock))
    result=run(compiler,'extract-native','page.kseed','--symbol','main','--output','page.raw')
    m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',result.stdout)
    if not m or int(m[2])!=(out/'page.raw').stat().st_size:raise ValueError('native entry mismatch')
    run(packager,'page-unsigned.macho','page.raw','fs2-cli-stdio-general',m[1].decode())
    image=out/'page.macho';shutil.copyfile(out/'page-unsigned.macho',image);image.chmod(0o700)
    subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    def fragment_nodes(html, reference):
        t=Tree(); t.feed(html); t.close()
        if len(t.stack)!=1: raise ValueError('invalid HTML')
        if not reference: return t.root[2]
        found=[]
        def walk(n):
            if not isinstance(n,list): return
            if n[0]=='section' and dict(n[1]).get('id')=='benchmark': found.append(n); return
            for child in n[2]: walk(child)
        walk(t.root)
        if len(found)!=1 or len(found[0][2])<35: raise ValueError('benchmark section missing')
        selected=found[0][2][23:35]
        return selected
    def check(case):
        tag,ref=case
        output=run(image,tag,expected=42).stdout
        got=fragment_nodes(output.decode(),False)
        expected=fragment_nodes(ref.read_text(),True)
        if len(got)!=len(expected): raise ValueError(('count mismatch',tag,len(got),len(expected)))
        for i,(a,b) in enumerate(zip(got,expected)):
            if normalize(a)!=normalize(b): raise ValueError(('template mismatch',tag,i,str(a)[:500],str(b)[:500]))
        page=out/'pages'/(tag+'.html');page.parent.mkdir(parents=True,exist_ok=True);page.write_bytes(output)
        return {'tag':tag,'sha256':hashlib.sha256(output).hexdigest(),'normalizedSha256':hashlib.sha256(str([normalize(x) for x in got]).encode()).hexdigest()}
    with ThreadPoolExecutor(max_workers=3) as pool: results=list(pool.map(check,zip(TAGS,refs)))
    if {p.name for p in (out/'pages').iterdir()}!={tag+'.html' for tag in TAGS}: raise ValueError('page set mismatch')
    if run(image,'../ja',expected=7).stdout:raise ValueError('partial invalid-locale output')
    for name in ('generator.cljk','runtime-benchmark.json','chart.cljk'):
        target=out/name; original=target.read_bytes()
        try:
            target.write_bytes(original+b' ')
            if run(image,'en',expected=7).stdout: raise ValueError('partial modified-source output')
        finally: target.write_bytes(original)
    target=out/'runtime-benchmark.json'; original=target.read_bytes()
    try:
        target.write_text('['*128)
        if run(image,'en',expected=7).stdout: raise ValueError('partial invalid-report output')
    finally: target.write_bytes(original)
    if any(sha(p)!=pins[str(p)] for p in tracked):raise ValueError('input drift')
    receipt={'status':'PASS_BOUNDED_RUNTIME_SECTION','inputs':pins,'locales':results,'binarySha256':sha(image),'persistedLocalizedRuntime':True,'digestAndLocaleRefusals':True,'siteGenerated':False,'fullBenchmarkSection':False, 'runtimeSubsection':True}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
