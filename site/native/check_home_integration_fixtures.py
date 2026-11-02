#!/usr/bin/env python3
"""Replay native homepage components with explicit benchmark/library fixtures.

This is an integration observation, not a complete homepage or release gate.
Each component directory is independently qualified and is copied to a private
stage so current locale catalog bytes can be exercised without changing it.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
TAGS = ('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')

def sha_bytes(data): return hashlib.sha256(data).hexdigest()
def sha(path): return sha_bytes(path.read_bytes())

def run(image, directory, *args, expected=42):
    fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY)
    saved = None
    try:
        if fd != 3:
            try: saved = os.dup(3)
            except OSError: pass
            os.dup2(fd, 3)
        r = subprocess.run([str(image), *args], cwd=directory, env={'PATH':'/nonexistent','LANG':'C'}, pass_fds=(3,), capture_output=True, timeout=600)
    finally:
        if fd != 3:
            if saved is None: os.close(3)
            else: os.dup2(saved, 3); os.close(saved)
        os.close(fd)
    if r.returncode != expected or r.stderr: raise RuntimeError((image.name, args, r.returncode, r.stderr[-500:]))
    return r.stdout

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    names = ('opening','evidence','developer','closing','shell','hero','scripts','chapters','body','head','document')
    for name in names: ap.add_argument('--'+name+'-dir', type=Path, required=True)
    ap.add_argument('--output',type=Path,required=True)
    ap.add_argument('--tag',choices=TAGS,action='append')
    a = ap.parse_args()
    if (platform.system(),platform.machine()) != ('Darwin','arm64'): raise ValueError('requires macOS arm64')
    originals={n:getattr(a,n+'_dir').resolve(strict=True) for n in names}
    bins={'opening':'nav.macho','evidence':'nav.macho','developer':'nav.macho','closing':'nav.macho','shell':'nav.macho','hero':'nav.macho','scripts':'bundle.macho','chapters':'chapters.macho','body':'body.macho','head':'head.macho','document':'page.macho'}
    receipts={}; pins={}
    for n,d in originals.items():
        receipt=d/'receipt.json'; r=json.loads(receipt.read_text()); image=d/bins[n]
        if sha(image)!=r['binarySha256']: raise ValueError('binary differs from receipt: '+n)
        subprocess.run(['/usr/bin/codesign','--verify','--strict',str(image)],check=True,capture_output=True,timeout=30)
        receipts[n]=r; pins[str(receipt)]=sha(receipt); pins[str(image)]=sha(image)
    out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    stages={}
    for n in ('opening','evidence','developer','closing','shell','hero','scripts','head'):
        d=out/'inputs'/n; shutil.copytree(originals[n],d);stages[n]=d
        for tag in TAGS:
            p=d/'site/i18n'/(tag+'.json'); current=ROOT/'site/i18n'/(tag+'.json')
            if p.exists(): shutil.copyfile(current,p)
    for tag in TAGS: pins[str(ROOT/'site/i18n'/(tag+'.json'))]=sha(ROOT/'site/i18n'/(tag+'.json'))
    def render(n,tag,*args):
        data=run(stages[n]/bins[n],stages[n],*args)
        obs=[x['sha256'] for x in receipts[n].get('observations',[]) if x.get('tag')==tag]
        if len(obs)!=1 or sha_bytes(data)!=obs[0]:raise ValueError(('native fragment changed from qualified reference',n,tag))
        return data
    script=run(stages['scripts']/bins['scripts'],stages['scripts'])
    if sha_bytes(script)!=receipts['scripts']['outputSha256']:raise ValueError('script bundle drift')
    selected=a.tag or TAGS;results=[]
    for tag in selected:
        d=out/'pages'/tag;d.mkdir(parents=True)
        fragments={n:render(n,tag,tag) for n in ('opening','evidence','developer','closing')}
        fragments['benchmark']=b'<section id="benchmark">TEST BENCHMARK</section>'
        fragments['library']=b'<section id="libraries">TEST LIBRARY</section>'
        for n,v in fragments.items():(d/(n+'.html')).write_bytes(v)
        def manifest(names):
            (d/'manifest.edn').write_text('{:tag "'+tag+'" '+' '.join(':'+n+' "'+sha(d/(n+'.html'))+'"' for n in names)+'}')
        order=('opening','evidence','benchmark','developer','library','closing');manifest(order)
        ch=run(originals['chapters']/bins['chapters'],d,tag)
        expected=[x['sha256'] for x in receipts['chapters']['locales'] if x['tag']==tag]
        if len(expected)!=1 or sha_bytes(ch)!=expected[0]:raise ValueError(('chapter mismatch',tag))
        (d/'chapters.html').write_bytes(ch)
        (d/'shell.html').write_bytes(render('shell',tag,tag));(d/'hero.html').write_bytes(render('hero',tag,tag));(d/'scripts.html').write_bytes(script)
        order=('shell','hero','chapters','scripts');manifest(order)
        body=run(originals['body']/bins['body'],d,tag);(d/'body.html').write_bytes(body)
        head=render('head',tag,tag);(d/'head.html').write_bytes(head)
        (d/'document-manifest.edn').write_text('{:tag "'+tag+'" :head "'+sha_bytes(head)+'" :body "'+sha_bytes(body)+'"}')
        page=run(originals['document']/bins['document'],d,tag)
        rtl=tag in ('ar','arz','ur');expected=('<!DOCTYPE html>\n<html lang="'+tag+('" dir="rtl">' if rtl else '">')).encode()+head+body+b'</html>'
        if page!=expected or b'KOTOBA_' in page or b'freebuff' in page.lower():raise ValueError(('document mismatch',tag))
        (d/'index.html').write_bytes(page);results.append({'tag':tag,'sha256':sha_bytes(page),'bytes':len(page)})
        print(tag+' integration passed',flush=True)
    d=out/'pages'/selected[-1];p=d/'body.html';old=p.read_bytes()
    try:
        p.write_bytes(old+b' ')
        if run(originals['document']/bins['document'],d,selected[-1],expected=7):raise ValueError('digest refusal emitted partial output')
    finally:p.write_bytes(old)
    if any(sha(Path(p))!=v for p,v in pins.items()):raise ValueError('input changed during integration')
    receipt={'status':'PASS_NATIVE_HOME_INTEGRATION_FIXTURES','inputs':pins,'observations':results,'fixtureBenchmarkAndLibrary':True,'nativeChapterFragments':True,'digestRefusal':True,'completeHomepage':False,'fullSiteGenerated':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
