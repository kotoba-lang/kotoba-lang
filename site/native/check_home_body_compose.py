#!/usr/bin/env python3
"""Compose native home bodies with native chapters; benchmark remains a fixture."""
import argparse,base64,hashlib,json,os,platform,re,shutil,subprocess,sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def worker(tag,dirs):
 result={}
 for name,folder,args in [('shell',Path(dirs[0]),[tag]),('hero',Path(dirs[1]),['hero',tag])]:
  fd=os.open(folder,os.O_RDONLY|os.O_DIRECTORY)
  if fd!=3:os.dup2(fd,3)
  try:r=subprocess.run([str(folder/'nav.macho'),*args],cwd=folder,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
  finally:
   if fd!=3:os.close(fd)
  if r.returncode!=42 or r.stderr:raise ValueError((name,tag,r.returncode,r.stderr[-500:]))
  obs=json.loads((folder/'receipt.json').read_text())['observations'];expected=[x['sha256'] for x in obs if x['tag']==tag]
  if len(expected)!=1 or hashlib.sha256(r.stdout).hexdigest()!=expected[0]:raise ValueError(('receipt mismatch',name,tag))
  result[name]=base64.b64encode(r.stdout).decode('ascii')
 return result
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for n in ('compiler','packager','sha256-source','shell-dir','hero-dir','scripts-dir','chapters-dir','output'):p.add_argument('--'+n,type=Path,required=True)
 a=p.parse_args()
 if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
 compiler,packager,source,shell,hero,scripts,chapters=(x.resolve(strict=True) for x in (a.compiler,a.packager,a.sha256_source,a.shell_dir,a.hero_dir,a.scripts_dir,a.chapters_dir))
 if sha(source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
 for x in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(x)],check=True,capture_output=True,timeout=30)
 for d,name,status in [(shell,'nav.macho','PASS_NATIVE_HOME_BODY_SHELL'),(hero,'nav.macho','PASS_NATIVE_HOME_COMMAND'),(scripts,'bundle.macho','PASS_NATIVE_HOME_SCRIPT_BUNDLE')]:
  rec=json.loads((d/'receipt.json').read_text())
  if rec.get('status')!=status or rec['binarySha256']!=sha(d/name):raise ValueError('unqualified component')
  if d!=scripts and (len(rec['observations'])!=len(TAGS) or {x['tag'] for x in rec['observations']}!=set(TAGS)):raise ValueError('locale coverage mismatch')
 cr=json.loads((chapters/'receipt.json').read_text())
 if cr.get('status')!='PASS_NATIVE_HOME_CHAPTERS_COMPOSE' or not cr.get('persistedLocalizedChapters') or not cr.get('nativeLibrary') or not cr.get('fixtureBenchmark'):raise ValueError('unqualified chapters')
 expected={x['tag']:x['sha256'] for x in cr['locales']}
 if len(cr['locales'])!=len(TAGS) or set(expected)!=set(TAGS):raise ValueError('chapter locales mismatch')
 chapter_files=[chapters/'pages'/(t+'.html') for t in TAGS]
 if any(sha(x)!=expected[t] for t,x in zip(TAGS,chapter_files)):raise ValueError('chapter digest mismatch')
 modules=[(ROOT/'site/native/home_body_compose.kotoba','src/site/native/home_body_compose.kotoba'),(ROOT/'test/site/native/home_body_compose_file.kotoba','src/site/native/home_body_compose_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
 inputs=[compiler,packager,ROOT/'site/native/dependencies.json',Path(__file__).resolve(),*[x for x,_ in modules],*[d/'receipt.json' for d in (shell,hero,scripts,chapters)],shell/'nav.macho',hero/'nav.macho',scripts/'bundle.macho',*chapter_files];pins={str(x):sha(x) for x in inputs}
 out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
 for f,n in modules:
  q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(f,q)
 lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_body_compose_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
 def run(image,d,*args,expected=0):
  fd=os.open(d,os.O_RDONLY|os.O_DIRECTORY)
  if fd!=3:os.dup2(fd,3)
  try:r=subprocess.run([str(image),*map(str,args)],cwd=d,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
  finally:
   if fd!=3:os.close(fd)
  if r.returncode!=expected or r.stderr:raise ValueError((image.name,args,r.returncode,r.stderr[-500:]))
  return r.stdout
 run(compiler,out,'compile','/src/site/native/home_body_compose_file.kotoba','--target','aarch64-macos','--output','body.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
 info=run(compiler,out,'extract-native','body.kseed','--symbol','main','--output','body.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
 if not m or int(m[2])!=(out/'body.raw').stat().st_size:raise ValueError('invalid entry')
 run(packager,out,'body-unsigned.macho','body.raw','fs2-cli-stdio-general',m[1].decode());image=out/'body.macho';shutil.copyfile(out/'body-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
 bundle=run(scripts/'bundle.macho',scripts,expected=42)
 if hashlib.sha256(bundle).hexdigest()!=json.loads((scripts/'receipt.json').read_text())['outputSha256']:raise ValueError('bundle receipt mismatch')
 def get(tag):
  r=subprocess.run([sys.executable,str(Path(__file__).resolve()),'--fragment-worker',tag,str(shell),str(hero)],capture_output=True,timeout=900,check=True)
  return tag,json.loads(r.stdout)
 with ThreadPoolExecutor(max_workers=4) as pool: fragments=dict(pool.map(get,TAGS))
 names=('shell','hero','chapters','scripts');results=[];slots=''.join('<script>KOTOBA_'+n+'_SCRIPT_SLOT</script>' for n in ('SEARCH','PLAY','HERO','CHART','MORPH'))
 def manifest(tag):(out/'manifest.edn').write_text('{:tag "'+tag+'" '+ ' '.join(':'+n+' "'+sha(out/(n+'.html'))+'"' for n in names)+'}')
 for tag in TAGS:
  for n,b in fragments[tag].items():(out/(n+'.html')).write_bytes(base64.b64decode(b,validate=True))
  shutil.copyfile(chapters/'pages'/(tag+'.html'),out/'chapters.html');(out/'scripts.html').write_bytes(bundle);manifest(tag)
  got=run(image,out,tag,expected=42);data={n:(out/(n+'.html')).read_text() for n in names}
  expected_body=data['shell'].replace('KOTOBA_HERO_SLOT',data['hero']).replace('KOTOBA_CHAPTERS_SLOT',data['chapters']).replace(slots,data['scripts'])
  if got.decode()!=expected_body or b'KOTOBA_' in got or b'UNQUALIFIED EVIDENCE FOR TEST' not in got:raise ValueError(('body mismatch',tag))
  q=out/'pages'/(tag+'.html');q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(got);results.append({'tag':tag,'sha256':hashlib.sha256(got).hexdigest()})
 for n in names:
  q=out/(n+'.html');old=q.read_bytes()
  try:
   q.write_bytes(old+b' ')
   if run(image,out,TAGS[-1],expected=7):raise ValueError('partial output on digest refusal')
  finally:q.write_bytes(old)
 manifest('ja')
 if run(image,out,TAGS[-1],expected=7):raise ValueError('partial output on locale mismatch')
 manifest('../ja')
 if run(image,out,'../ja',expected=7):raise ValueError('partial output on invalid locale')
 for n,old,new in [('shell','KOTOBA_HERO_SLOT','KOTOBA_HERO_SLOTKOTOBA_HERO_SLOT'),('chapters','class="kot-chapter-block"','class="other"'),('scripts','</script>','')]:
  q=out/(n+'.html');original=q.read_bytes();q.write_text(original.decode().replace(old,new,1));manifest(TAGS[-1])
  try:
   if run(image,out,TAGS[-1],expected=7):raise ValueError('partial output on structure refusal')
  finally:q.write_bytes(original)
 if any(sha(x)!=h for x,h in ((Path(k),v) for k,v in pins.items())):raise ValueError('input drift')
 rec={'status':'PASS_NATIVE_HOME_BODY_REAL_CHAPTERS','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'observations':results,'nativeLibrary':True,'fixtureBenchmark':True,'persistedLocalizedBodies':True,'fullHomepage':False,'publicationQualified':False}
 (out/'receipt.json').write_text(json.dumps(rec,indent=2)+'\n');print(rec['status'])
if __name__=='__main__':
 if len(sys.argv)==5 and sys.argv[1]=='--fragment-worker':print(json.dumps(worker(sys.argv[2],sys.argv[3:])))
 else:main()
