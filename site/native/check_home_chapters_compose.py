#!/usr/bin/env python3
"""Exercise native chapter assembly; benchmark introduction, startup card and library are native; remaining benchmark is a fixture."""
import argparse, base64, hashlib, json, os, platform, re, shutil, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def fragment_worker(tag, directories):
    result={}
    for name,directory in zip(('opening','evidence','developer','closing','library','intro','startup'),map(Path,directories)):
        fd=os.open(directory,os.O_RDONLY|os.O_DIRECTORY)
        if fd!=3: os.dup2(fd,3)
        try: r=subprocess.run([str(directory/'nav.macho'),*([name,tag] if name in ('opening','closing','library','intro','startup') else [tag])],cwd=directory,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        finally:
            if fd!=3: os.close(fd)
        if r.returncode!=42 or r.stderr: raise RuntimeError((name,tag,r.returncode,r.stderr[-500:]))
        observations=json.loads((directory/'receipt.json').read_text())['observations']
        recorded=[o['sha256'] for o in observations if o['tag']==tag]
        digest=hashlib.sha256(r.stdout).hexdigest()
        if not recorded or any(h!=digest for h in recorded): raise ValueError(('fragment receipt mismatch',name,tag))
        result[name]=base64.b64encode(r.stdout).decode('ascii')
    return result
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','opening-dir','evidence-dir','developer-dir','closing-dir','library-dir','intro-dir','startup-dir','output'): ap.add_argument('--'+n,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'): raise ValueError('requires macOS arm64')
    compiler,packager,source,opening,evidence,developer,closing,library,intro,startup=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.opening_dir,a.evidence_dir,a.developer_dir,a.closing_dir,a.library_dir,a.intro_dir,a.startup_dir))
    if sha(source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']: raise ValueError('SHA dependency drift')
    for p in (compiler,packager): subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    parts=[(opening,'PASS_NATIVE_HOME_OPENING_CHAPTERS'),(evidence,'PASS_NATIVE_HOME_EVIDENCE_TEMPLATE'),(developer,'PASS_NATIVE_HOME_DEVELOPER_TEMPLATE'),(closing,'PASS_NATIVE_HOME_CLOSING_CHAPTER'),(library,'PASS_NATIVE_HOME_LIBRARY'),(intro,'PASS_NATIVE_BENCHMARK_INTRO'),(startup,'PASS_NATIVE_BENCHMARK_STARTUP_CARD')]
    for d,status in parts:
        receipt=json.loads((d/'receipt.json').read_text())
        observations=receipt.get('observations',[])
        if receipt.get('status')!=status or sha(d/'nav.macho')!=receipt['binarySha256']: raise ValueError('component qualification or binary drift')
        if len(observations)!=len(TAGS) or {x.get('tag') for x in observations}!=set(TAGS): raise ValueError('component locale coverage mismatch')
    modules=[(ROOT/'site/native/home_chapters_compose.kotoba','src/site/native/home_chapters_compose.kotoba'),(ROOT/'test/site/native/home_chapters_compose_file.kotoba','src/site/native/home_chapters_compose_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
    tracked=[compiler,packager,ROOT/'site/native/dependencies.json',Path(__file__).resolve(),*[p for p,_ in modules],*[d/'receipt.json' for d,_ in parts],*[d/'nav.macho' for d,_ in parts]]
    pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,n in modules:
        q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_chapters_compose_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    def run_in(image,d,*args,expected=0):
        fd=os.open(d,os.O_RDONLY|os.O_DIRECTORY)
        if fd!=3: os.dup2(fd,3)
        try:r=subprocess.run([str(image),*map(str,args)],cwd=d,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        finally:
            if fd!=3: os.close(fd)
        if r.returncode!=expected or r.stderr: raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r.stdout
    run_in(compiler,out,'compile','/src/site/native/home_chapters_compose_file.kotoba','--target','aarch64-macos','--output','chapters.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=run_in(compiler,out,'extract-native','chapters.kseed','--symbol','main','--output','chapters.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not m or int(m[2])!=(out/'chapters.raw').stat().st_size: raise ValueError('invalid native entry')
    run_in(packager,out,'chapters-unsigned.macho','chapters.raw','fs2-cli-stdio-general',m[1].decode())
    image=out/'chapters.macho';shutil.copyfile(out/'chapters-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    names=('opening','evidence','benchmark','developer','library','closing')
    def manifest(tag): (out/'manifest.edn').write_text('{:tag "'+tag+'" '+ ' '.join(':'+n+' "'+sha(out/(n+'.html'))+'"' for n in names)+'}')
    def make_fragments(tag):
        r=subprocess.run([sys.executable,str(Path(__file__).resolve()),'--fragment-worker',tag,*map(str,(opening,evidence,developer,closing,library,intro,startup))],capture_output=True,timeout=1200,check=True)
        return tag,json.loads(r.stdout)
    with ThreadPoolExecutor(max_workers=4) as pool:
        fragments=dict(pool.map(make_fragments,TAGS))
    results=[]
    for tag in TAGS:
        for dest,encoded in fragments[tag].items(): (out/(dest+'.html')).write_bytes(base64.b64decode(encoded,validate=True))
        intro_html=(out/'intro.html').read_text()
        if not intro_html.endswith('</section>') or intro_html.count('id="benchmark"')!=1 or 'TEST BENCHMARK' in intro_html: raise ValueError('invalid benchmark introduction')
        card=(out/'startup.html').read_text()
        if not card.startswith('<div class="dds-ext-card">') or not card.endswith('</div>') or 'TEST BENCHMARK' in card: raise ValueError('invalid startup card')
        (out/'benchmark.html').write_text(intro_html[:-len('</section>')]+'<div class="dds-ext-grid" style="--dds-ext-grid-min:16rem;">'+card+'<div>TEST REMAINING CARDS</div></div><div>TEST BENCHMARK</div></section>')
        manifest(tag);got=run_in(image,out,tag,expected=42).decode()
        data={n:(out/(n+'.html')).read_text() for n in names}
        expected=data['opening']+data['evidence'].replace('KOTOBA_BENCHMARK_SLOT',data['benchmark'])+data['developer'].replace('KOTOBA_LIBRARY_SLOT',data['library'])+data['closing']
        if got!=expected or 'KOTOBA_' in got: raise ValueError(('composition mismatch',tag))
        page=out/'pages'/(tag+'.html');page.parent.mkdir(parents=True,exist_ok=True);page.write_text(got)
        results.append({'tag':tag,'sha256':hashlib.sha256(got.encode()).hexdigest()})
    if {p.name for p in (out/'pages').iterdir()}!={tag+'.html' for tag in TAGS}: raise ValueError('chapter output set mismatch')
    for n in names:
        p=out/(n+'.html');old=p.read_bytes()
        try:p.write_bytes(old+b' ');r=run_in(image,out,TAGS[-1],expected=7)
        finally:p.write_bytes(old)
        if r: raise ValueError('partial output on digest refusal')
    manifest('ja')
    if run_in(image,out,TAGS[-1],expected=7): raise ValueError('partial output on tag refusal')
    manifest('../ja')
    if run_in(image,out,'../ja',expected=7): raise ValueError('partial output on locale refusal')
    for n,old,new in [('evidence','KOTOBA_BENCHMARK_SLOT','KOTOBA_BENCHMARK_SLOTKOTOBA_BENCHMARK_SLOT'),('developer','KOTOBA_LIBRARY_SLOT',''),('library','id="libraries"','id="bad"'),('opening','class="kot-chapter-block"','class="bad"')]:
        p=out/(n+'.html');original=p.read_bytes();p.write_text(original.decode().replace(old,new,1));manifest(TAGS[-1])
        if run_in(image,out,TAGS[-1],expected=7): raise ValueError('partial output on structure refusal')
        p.write_bytes(original)
    if any(sha(p)!=pins[str(p)] for p in tracked): raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_HOME_CHAPTERS_COMPOSE','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'locales':results,'fixtureBenchmark':True,'nativeBenchmarkIntro':True,'nativeStartupCard':True,'nativeLibrary':True,'persistedLocalizedChapters':True,'digestAndStructureRefusals':True,'fullHomepage':False,'siteGenerated':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':
    if len(sys.argv)==10 and sys.argv[1]=='--fragment-worker': print(json.dumps(fragment_worker(sys.argv[2],sys.argv[3:])))
    else: main()
