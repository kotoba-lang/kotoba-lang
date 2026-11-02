#!/usr/bin/env python3
"""Qualify native staging of the three standalone starter pages."""
import argparse, hashlib, json, os, platform, re, shutil, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','head-dir','body-dir','document-dir','asset-stager-dir','output'):ap.add_argument('--'+n,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
    compiler,packager,source,head,body,document,asset=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.head_dir,a.body_dir,a.document_dir,a.asset_stager_dir))
    deps=ROOT/'site/native/dependencies.json';head_image,body_image,doc_image,asset_image=head/'head.macho',body/'nav.macho',document/'page.macho',asset/'stage.macho'
    if sha(source)!=json.loads(deps.read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
    for d,p in ((head,head_image),(body,body_image),(document,doc_image),(asset,asset_image)):
        if json.loads((d/'receipt.json').read_text())['binarySha256']!=sha(p):raise ValueError('binary drift')
    ref=json.loads((document/'receipt.json').read_text());expected={r['tag']:r['sha256'] for r in ref['observations']}
    if ref['status']!='PASS_NATIVE_START_CARD_DOCUMENTS' or set(expected)!={'en','ta','uk'}:raise ValueError('unqualified document reference')
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    modules=[(ROOT/'site/native/start_card_stage.kotoba','src/site/native/start_card_stage.kotoba'),(ROOT/'test/site/native/start_card_stage_file.kotoba','src/site/native/start_card_stage_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
    tracked=[compiler,packager,deps,Path(__file__).resolve(),*[p for p,_ in modules],*[d/'receipt.json' for d in (head,body,document,asset)],head_image,body_image,doc_image,asset_image]
    tracked += [p for d in (head,body) for p in d.rglob('*') if p.is_file()];pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,n in modules:
        q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/start_card_stage_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    def build(image,*args):
        fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
        if fd!=3:os.dup2(fd,3)
        try:r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        finally:
            if fd!=3:os.close(fd)
        if r.returncode or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r.stdout
    build(compiler,'compile','/src/site/native/start_card_stage_file.kotoba','--target','aarch64-macos','--output','stage.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=build(compiler,'extract-native','stage.kseed','--symbol','main','--output','stage.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not m or int(m[2])!=(out/'stage.raw').stat().st_size:raise ValueError('bad entry')
    build(packager,'stage-unsigned.macho','stage.raw','amu-cli-host',m[1].decode());image=out/'stage.macho';shutil.copyfile(out/'stage-unsigned.macho',image);image.chmod(0o700)
    subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    data={'starter-head-dir':str(head),'starter-head':str(head_image),'starter-head-sha':sha(head_image),'starter-body-dir':str(body),'starter-body':str(body_image),'starter-body-sha':sha(body_image),'starter-document':str(doc_image),'starter-document-sha':sha(doc_image),'asset':str(asset_image),'asset-sha':sha(asset_image)}
    def fixture(name,wrong=False):
        base=out/name;private=base/'private';site=base/'site';private.mkdir(parents=True);site.mkdir();d=dict(data)
        if wrong:d['starter-head-sha']='0'*64
        m=base/'manifest.edn';m.write_text('{'+ ' '.join(':'+k+' '+json.dumps(v) for k,v in d.items())+'}');return private,site,m
    def run(private,site,m,expected_exit):
        r=subprocess.run([str(image),str(m),str(private),str(site)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},capture_output=True,timeout=900)
        if r.returncode!=expected_exit or r.stdout or r.stderr:raise RuntimeError((r.returncode,r.stdout[:100],r.stderr[-500:]))
    private,site,m=fixture('bad-sha',True);run(private,site,m,7)
    if list(private.iterdir()) or list(site.iterdir()):raise ValueError('invalid digest wrote output')
    private,site,m=fixture('bad-source');tokens=head/'tokens.cljk';original=tokens.read_bytes()
    try:
        tokens.write_bytes(original+b' ');run(private,site,m,7)
        if any(p.is_file() for p in site.rglob('*')):raise ValueError('failed head published partial output')
    finally:tokens.write_bytes(original)
    private,site,m=fixture('good');(site/'ta').mkdir();(site/'uk').mkdir();run(private,site,m,42)
    wanted={('start' if tag=='en' else tag)+'/index.html':expected[tag] for tag in ('en','ta','uk')}
    actual={str(p.relative_to(site)):sha(p) for p in site.rglob('*') if p.is_file()}
    if actual!=wanted or any(b'freebuff' in p.read_bytes().lower() for p in site.rglob('*.html')):raise ValueError('output mismatch')
    run(private,site,m,7)
    if {str(p.relative_to(site)):sha(p) for p in site.rglob('*') if p.is_file()}!=wanted:raise ValueError('existing output changed')
    fresh_private,fresh_site,fresh_m=fixture('fresh-roots');run(fresh_private,fresh_site,fresh_m,42)
    if {str(p.relative_to(fresh_site)):sha(p) for p in fresh_site.rglob('*') if p.is_file()}!=wanted:raise ValueError('fresh locale roots mismatch')
    if any(sha(Path(p))!=h for p,h in pins.items()):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_START_CARD_STAGE_MACOS','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'pages':wanted,'existingAndFreshLocaleRoots':True,'badDigestRefused':True,'failedPartialOutputNotStaged':True,'existingStagePreserved':True,'privateImmutableStageRequired':True,'completeSiteGenerated':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
