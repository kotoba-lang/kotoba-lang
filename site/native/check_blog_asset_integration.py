#!/usr/bin/env python3
"""Observe native page and asset stagers working together in a private test tree."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
TAGS = ('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('page-controller-dir','asset-stager-dir','head-dir','body-dir','reference-dir','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    a=parser.parse_args()
    controller,stager,head,body,reference=(p.resolve(strict=True) for p in (a.page_controller_dir,a.asset_stager_dir,a.head_dir,a.body_dir,a.reference_dir))
    page_image,asset_image,head_image,body_image=controller/'controller.macho',stager/'stage.macho',head/'head.macho',body/'nav.macho'
    for d,p in ((controller,page_image),(stager,asset_image),(head,head_image),(body,body_image)):
        if json.loads((d/'receipt.json').read_text())['binarySha256']!=sha(p):raise ValueError('binary differs from receipt')
    reference_receipt=json.loads((reference/'receipt.json').read_text())
    if reference_receipt['status']!='PASS_NATIVE_BLOG_DOCUMENTS':raise ValueError('unqualified document reference')
    expected={item['tag']:item['sha256'] for item in reference_receipt['observations']}
    wordmark=ROOT/'site/assets/kotoba-wordmark.png';favicon=ROOT/'site/assets/kotoba-favicon.ico';wasm=ROOT/'site/assets/play/double-21.wasm'
    inputs=[Path(__file__).resolve(),controller/'receipt.json',page_image,stager/'receipt.json',asset_image,head/'receipt.json',head_image,body/'receipt.json',body_image,reference/'receipt.json',wordmark,favicon,wasm]
    pins={str(p):sha(p) for p in inputs}
    out=a.output.resolve();out.mkdir(parents=True,exist_ok=False);private=out/'private';site=out/'site';private.mkdir();site.mkdir();(site/'play').mkdir()
    def invoke(image,args):
        r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},capture_output=True,timeout=300)
        if r.returncode!=42 or r.stdout or r.stderr:raise RuntimeError((image.name,r.returncode,r.stderr[-300:]))
    def page(tag):
        scratch=private/tag;scratch.mkdir()
        dest=site/('' if tag=='en' else tag)/'blog';dest.mkdir(parents=True)
        invoke(page_image,[tag,head,head_image,sha(head_image),body,body_image,sha(body_image),scratch])
        output=scratch/'index.html'
        if sha(output)!=expected[tag]:raise ValueError(('document drift',tag))
        invoke(asset_image,[output,expected[tag],dest,'index.html'])
        if sha(dest/'index.html')!=expected[tag]:raise ValueError(('staged page mismatch',tag))
        return {'tag':tag,'sha256':expected[tag]}
    with ThreadPoolExecutor(max_workers=3) as pool:observations=list(pool.map(page,TAGS))
    for src,dest,name in ((wordmark,site,'kotoba-wordmark.png'),(favicon,site,'favicon.ico'),(wasm,site/'play','double-21.wasm')):
        invoke(asset_image,[src,sha(src),dest,name])
        if sha(dest/name)!=sha(src):raise ValueError('staged asset mismatch')
    actual={str(p.relative_to(site)) for p in site.rglob('*') if p.is_file()}
    wanted={('' if tag=='en' else tag+'/')+'blog/index.html' for tag in TAGS}
    wanted.update({'kotoba-wordmark.png','favicon.ico','play/double-21.wasm'})
    if actual!=wanted:raise ValueError(('unexpected public stage contents',actual))
    if any(sha(Path(p))!=digest for p,digest in pins.items()):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_BLOG_ASSET_INTEGRATION_MACOS','inputs':pins,'observations':observations,'nativeStaging':True,'onlyExpectedPublicFiles':True,'testOrchestration':'python observer','completeSiteGenerated':False,'linuxQualified':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])


if __name__=='__main__':main()
