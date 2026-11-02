#!/usr/bin/env python3
"""Qualify native staging of translated article pages into a private site."""
import argparse
import hashlib
import json
import os
import platform
from pathlib import Path
import re
import shutil
import subprocess
ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','article-dir','reference-dir','asset-stager-dir','output'):ap.add_argument('--'+n,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
    compiler,packager,source,article,reference,asset=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.article_dir,a.reference_dir,a.asset_stager_dir))
    deps=ROOT/'site/native/dependencies.json';article_image=article/'base.macho';asset_image=asset/'stage.macho'
    if sha(source)!=json.loads(deps.read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
    for d,p in ((article,article_image),(asset,asset_image)):
        if json.loads((d/'receipt.json').read_text())['binarySha256']!=sha(p):raise ValueError('binary drift')
    article_receipt=json.loads((article/'receipt.json').read_text());ref=json.loads((reference/'receipt.json').read_text())
    if not article_receipt.get('englishIdentitySourceChecked') or ref['status']!='PASS_NATIVE_MULTILINGUAL_ARTICLE_PAGE':raise ValueError('unqualified references')
    expected={(r['route'],r['tag']):r['sha256'] for r in ref['observations']}
    bilingual=(('parity','embench-c-parity-20261015'),('progress','embench-progress-20261012'),('usage','kotoba-murakumo-how-to-use-20261015'))
    if set(expected)!={(r,t) for r in ('infra','security') for t in TAGS}|{(r,t) for r,_ in bilingual for t in ('en','ja')}:raise ValueError('missing article reference')
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    modules=[(ROOT/'site/native/article_stage.kotoba','src/site/native/article_stage.kotoba'),(ROOT/'test/site/native/article_stage_file.kotoba','src/site/native/article_stage_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
    inputs=[compiler,packager,deps,Path(__file__).resolve(),*[p for p,_ in modules],article_image,article/'receipt.json',reference/'receipt.json',asset_image,asset/'receipt.json']
    inputs.extend(p for p in article.rglob('*') if p.is_file());pins={str(p):sha(p) for p in inputs}
    out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,n in modules:
        q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/article_stage_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    def build(image,*args):
        fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
        if fd!=3:os.dup2(fd,3)
        try:r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        finally:
            if fd!=3:os.close(fd)
        if r.returncode or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r.stdout
    build(compiler,'compile','/src/site/native/article_stage_file.kotoba','--target','aarch64-macos','--output','article.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=build(compiler,'extract-native','article.kseed','--symbol','main','--output','article.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not m or int(m[2])!=(out/'article.raw').stat().st_size:raise ValueError('bad entry')
    build(packager,'article-unsigned.macho','article.raw','amu-cli-host',m[1].decode());image=out/'article.macho';shutil.copyfile(out/'article-unsigned.macho',image);image.chmod(0o700)
    subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    def manifest(path,wrong=False):
        data={'article-dir':str(article),'article':str(article_image),'article-sha':'0'*64 if wrong else sha(article_image),'asset':str(asset_image),'asset-sha':sha(asset_image)}
        path.write_text('{'+ ' '.join(':'+k+' '+json.dumps(v) for k,v in data.items())+'}')
    def fixture(name,wrong=False):
        base=out/name;private=base/'private';site=base/'site';private.mkdir(parents=True);site.mkdir()
        for tag in TAGS:(site/('' if tag=='en' else tag)/'blog').mkdir(parents=True)
        (site/'spec').mkdir();m=base/'manifest.edn';manifest(m,wrong)
        return private,site,m
    def run(private,site,m,expected_exit):
        r=subprocess.run([str(image),str(m),str(private),str(site)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},capture_output=True,timeout=1800)
        if r.returncode!=expected_exit or r.stdout or r.stderr:raise RuntimeError((r.returncode,r.stdout[:100],r.stderr[-500:]))
    private,site,m=fixture('bad-sha',True);run(private,site,m,7)
    if any(p.is_file() for p in site.rglob('*')) or list(private.iterdir()):raise ValueError('invalid digest wrote output')
    private,site,m=fixture('bad-source');tokens=article/'tokens.cljk';original=tokens.read_bytes()
    try:
        tokens.write_bytes(original+b' ');run(private,site,m,7)
        if any(p.is_file() for p in site.rglob('*')):raise ValueError('failed article published partial stdout')
    finally:tokens.write_bytes(original)
    private,site,m=fixture('good');run(private,site,m,42)
    wanted={}
    for tag in TAGS:
        for route,slug in (('infra','infra-cost-measured-path'),('security','csf2-attack-graphs')):
            path=site/('' if tag=='en' else tag)/'blog'/slug/'index.html';wanted[str(path.relative_to(site))]=expected[(route,tag)]
            if sha(path)!=expected[(route,tag)] or b'freebuff' in path.read_bytes().lower():raise ValueError(('article mismatch',route,tag))
    for route,slug in bilingual:
        for tag in ('en','ja'):
            path=site/('' if tag=='en' else tag)/'blog'/slug/'index.html';wanted[str(path.relative_to(site))]=expected[(route,tag)]
            if sha(path)!=expected[(route,tag)] or b'freebuff' in path.read_bytes().lower():raise ValueError(('bilingual article mismatch',route,tag))
    identity=site/'spec/org-identity/index.html';wanted[str(identity.relative_to(site))]='1eddf303567a0ea780ab1938cf06598c19df9e08b4d2565e04925f06eaf83dcf'
    if sha(identity)!=wanted[str(identity.relative_to(site))] or b'freebuff' in identity.read_bytes().lower():raise ValueError('identity mismatch')
    if {str(p.relative_to(site)):sha(p) for p in site.rglob('*') if p.is_file()}!=wanted:raise ValueError('output set mismatch')
    run(private,site,m,7)
    if {str(p.relative_to(site)):sha(p) for p in site.rglob('*') if p.is_file()}!=wanted:raise ValueError('existing output changed')
    if any(sha(Path(p))!=h for p,h in pins.items()):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_ARTICLE_STAGE_MACOS','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'locales':list(TAGS),'articleCount':len(wanted),'badDigestRefused':True,'failedPartialOutputNotStaged':True,'existingStagePreserved':True,'privateImmutableStageRequired':True,'completeSiteGenerated':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
