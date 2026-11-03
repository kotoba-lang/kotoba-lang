#!/usr/bin/env python3
"""Check native sponsor-page head DOM and CSS against 17 historical pages."""
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
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def head(html,reference):
    t=Tree();t.feed(html);t.close();found=[]
    def walk(n):
        if not isinstance(n,list):return
        if n[0]=='head':found.append(n);return
        for c in n[2]:walk(c)
    walk(t.root)
    if len(t.stack)!=1 or len(found)!=1:raise ValueError('head missing')
    node=found[0]
    def freebuff(n):return isinstance(n,list) and n[0]=='script' and any(isinstance(x,str) and 'window.freebuff' in x for x in n[2])
    if not reference and any(freebuff(n) for n in node[2]):raise ValueError('Freebuff present')
    node[2][:]=[n for n in node[2] if not freebuff(n)]
    return normalize(node)
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','dds-root','css-source','json-source','theme-json','html-core','html-hiccup','output'):ap.add_argument('--'+n,type=Path,required=True)
    ap.add_argument('--tag',choices=TAGS)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
    compiler,packager,sha_source,dds,css_source,json_source,theme_json,core,hiccup=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.dds_root,a.css_source,a.json_source,a.theme_json,a.html_core,a.html_hiccup))
    spec=json.loads((ROOT/'site/native/css-source-dependencies.json').read_text());html=json.loads((ROOT/'site/native/html-dependencies.json').read_text())
    files=[('resources/jp_go_dds/dds.css','dds.css'),('resources/jp_go_dds/components/language-selector.css','language.css'),('resources/jp_go_dds/components/menu-list-box.css','box.css'),('resources/jp_go_dds/components/menu-list.css','menu.css'),('src/jp_go_dds/behavior.cljk','behavior.cljk'),('src/jp_go_dds/core.cljk','core.cljk'),('src/jp_go_dds/tokens.cljk','tokens.cljk')]
    inputs=[(dds/p,n) for p,n in files]+[(dds/'src/jp_go_dds/theme_toggle.cljk','theme-source.cljk'),(theme_json,'theme.json'),(ROOT/'site/generate.cljk','generator.cljk')]
    if any(sha(dds/p)!=spec['ddsBase']['files'][p] for p,_ in files) or sha(inputs[-3][0])!=spec['themeSource']['sha256'] or sha(theme_json)!=spec['syntaxTheme']['sha256'] or sha(css_source)!=spec['cssSheet']['sha256'] or sha(json_source)!=spec['jsonScalar']['sha256'] or sha(sha_source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256'] or [sha(core),sha(hiccup)]!=[d['sha256'] for d in html['dependencies']]:raise ValueError('source drift')
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    names=['sponsor_head_page','page_head','dds_base_css','dds_ext_css','dds_ext_data','dds_palette','dds_skin_css','dds_bridge_css','source_concat','syntax_theme','article_head','header_scripts','page_metadata','locale_links','locale_message','locale_catalog','locale_placeholders']
    modules=[(ROOT/'site/native'/(n+'.kotoba'),'src/site/native/'+n+'.kotoba') for n in names]+[(ROOT/'test/site/native/sponsor_head_file.kotoba','src/site/native/sponsor_head_file.kotoba'),(sha_source,'src/multiformats/sha256.kotoba'),(css_source,'src/css_sheet.kotoba'),(json_source,'src/json_scalar.kotoba'),(core,'src/html_core.kotoba'),(hiccup,'src/html_hiccup.kotoba')]
    catalogs=[ROOT/'site/i18n'/(tag+'.json') for tag in TAGS];refs=[ROOT/'site/dist'/('' if tag=='en' else tag)/'sponsor'/'index.html' for tag in TAGS]
    tracked=[compiler,packager,ROOT/'site/native/css-source-dependencies.json',ROOT/'site/native/html-dependencies.json',ROOT/'site/native/dependencies.json',ROOT/'site/native/check_footers.py',Path(__file__).resolve(),*[p for p,_ in modules],*[p for p,_ in inputs],*catalogs,*refs]
    pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,name in modules:
        q=out/name;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    for p,name in inputs:shutil.copyfile(p,out/name)
    for p in catalogs:
        q=out/'site/i18n'/p.name;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/sponsor_head_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
    if fd!=3:os.dup2(fd,3)
    def run(image,*args,expected=0):
        r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        if r.returncode!=expected or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r.stdout
    run(compiler,'compile','/src/site/native/sponsor_head_file.kotoba','--target','aarch64-macos','--output','head.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=run(compiler,'extract-native','head.kseed','--symbol','main','--output','head.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not m or int(m[2])!=(out/'head.raw').stat().st_size:raise ValueError('bad native entry')
    run(packager,'head-unsigned.macho','head.raw','fs2-cli-stdio-general',m[1].decode())
    image=out/'head.macho';shutil.copyfile(out/'head-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    def check(case):
        tag,ref=case;output=run(image,tag,expected=42).decode()
        if head(output,False)!=head(ref.read_text(),True):raise ValueError(('head mismatch',tag))
        print(tag,'head pass',flush=True)
        return {'tag':tag,'sha256':hashlib.sha256(output.encode()).hexdigest()}
    with ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(check,[(tag,ref) for tag,ref in zip(TAGS,refs) if not a.tag or tag==a.tag]))
    run(image,'../ja',expected=7)
    for _,name in inputs:
        p=out/name;old=p.read_bytes()
        try:p.write_bytes(old+b' ');run(image,'en',expected=7)
        finally:p.write_bytes(old)
    if any(sha(p)!=pins[str(p)] for p in tracked):raise ValueError('source changed during gate')
    receipt={'status':'PASS_NATIVE_SPONSOR_HEAD','inputs':pins,'observations':results,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'invalidTagRefused':True,'modifiedInputsRefused':True,'freebuffAbsent':True,'fullPageGenerated':False,'siteGenerated':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
