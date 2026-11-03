#!/usr/bin/env python3
"""Compose six source-qualified native benchmark cards, not the full benchmark."""
import argparse,base64,hashlib,json,os,platform,re,shutil,subprocess,sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from check_footers import Tree,normalize,TAGS
ROOT=Path(__file__).resolve().parents[2]
NAMES=('startup','coverage','speed','loop','scaling','domain')
STATUS=('PASS_NATIVE_BENCHMARK_STARTUP_CARD','PASS_NATIVE_BENCHMARK_RUNTIME_COVERAGE_CARD','PASS_NATIVE_BENCHMARK_RUNTIME_SPEED_CARD','PASS_NATIVE_BENCHMARK_LOOP_CARD','PASS_NATIVE_BENCHMARK_SCALING_CARD','PASS_NATIVE_BENCHMARK_DOMAIN_CARD')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def grid(html,fragment):
 t=Tree();t.feed(html);t.close();found=[]
 def walk(n):
  if not isinstance(n,list):return
  if fragment and n[0]=='div' and dict(n[1]).get('class')=='dds-ext-grid':found.append(n);return
  if not fragment and n[0]=='section' and dict(n[1]).get('id')=='benchmark':
   g=[x for x in n[2] if isinstance(x,list) and x[0]=='div' and dict(x[1]).get('class')=='dds-ext-grid']
   if not g:raise ValueError('grid missing')
   found.append(g[0]);return
  for x in n[2]:walk(x)
 walk(t.root)
 if len(t.stack)!=1 or len(found)!=1 or len(found[0][2])!=6:raise ValueError('grid shape mismatch')
 return normalize(found[0])
def worker(tag,dirs):
 result={}
 for name,directory in zip(NAMES,map(Path,dirs)):
  fd=os.open(directory,os.O_RDONLY|os.O_DIRECTORY)
  if fd!=3:os.dup2(fd,3)
  try:r=subprocess.run([str(directory/'nav.macho'),name,tag],cwd=directory,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
  finally:
   if fd!=3:os.close(fd)
  if r.returncode!=42 or r.stderr:raise ValueError((name,tag,r.returncode,r.stderr[-500:]))
  obs=json.loads((directory/'receipt.json').read_text())['observations'];expected=[x['sha256'] for x in obs if x['tag']==tag]
  if len(expected)!=1 or hashlib.sha256(r.stdout).hexdigest()!=expected[0]:raise ValueError(('receipt mismatch',name,tag))
  result[name]=base64.b64encode(r.stdout).decode('ascii')
 return result
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for n in ('compiler','packager','sha256-source',*[x+'-dir' for x in NAMES],'output'):p.add_argument('--'+n,type=Path,required=True)
 a=p.parse_args()
 if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
 compiler,packager,source=(x.resolve(strict=True) for x in (a.compiler,a.packager,a.sha256_source));dirs=[getattr(a,n+'_dir').resolve(strict=True) for n in NAMES]
 if sha(source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
 for x in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(x)],check=True,capture_output=True,timeout=30)
 for d,status in zip(dirs,STATUS):
  rec=json.loads((d/'receipt.json').read_text());obs=rec.get('observations',[])
  if rec.get('status')!=status or sha(d/'nav.macho')!=rec['binarySha256']:raise ValueError('unqualified card')
  if len(obs)!=len(TAGS) or {x['tag'] for x in obs}!=set(TAGS):raise ValueError('card locale coverage')
 modules=[(ROOT/'site/native/home_benchmark_cards.kotoba','src/site/native/home_benchmark_cards.kotoba'),(ROOT/'test/site/native/home_benchmark_cards_file.kotoba','src/site/native/home_benchmark_cards_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
 refs=[ROOT/'site/dist'/('' if t=='en' else t)/'index.html' for t in TAGS]
 inputs=[compiler,packager,ROOT/'site/native/dependencies.json',Path(__file__).resolve(),ROOT/'site/native/check_footers.py',*[x for x,_ in modules],*[d/'receipt.json' for d in dirs],*[d/'nav.macho' for d in dirs],*refs];pins={str(x):sha(x) for x in inputs}
 out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
 for f,n in modules:
  q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(f,q)
 lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_benchmark_cards_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
 def run(image,d,*args,expected=0):
  fd=os.open(d,os.O_RDONLY|os.O_DIRECTORY)
  if fd!=3:os.dup2(fd,3)
  try:r=subprocess.run([str(image),*map(str,args)],cwd=d,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
  finally:
   if fd!=3:os.close(fd)
  if r.returncode!=expected or r.stderr:raise ValueError((image.name,args,r.returncode,r.stderr[-500:]))
  return r.stdout
 run(compiler,out,'compile','/src/site/native/home_benchmark_cards_file.kotoba','--target','aarch64-macos','--output','cards.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
 info=run(compiler,out,'extract-native','cards.kseed','--symbol','main','--output','cards.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
 if not m or int(m[2])!=(out/'cards.raw').stat().st_size:raise ValueError('invalid entry')
 run(packager,out,'cards-unsigned.macho','cards.raw','fs2-cli-stdio-general',m[1].decode());image=out/'cards.macho';shutil.copyfile(out/'cards-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
 def get(tag):
  r=subprocess.run([sys.executable,str(Path(__file__).resolve()),'--fragment-worker',tag,*map(str,dirs)],capture_output=True,timeout=1200,check=True)
  return tag,json.loads(r.stdout)
 with ThreadPoolExecutor(max_workers=4) as pool:fragments=dict(pool.map(get,TAGS))
 def manifest(tag):(out/'manifest.edn').write_text('{:tag "'+tag+'" '+ ' '.join(':'+n+' "'+sha(out/(n+'.html'))+'"' for n in NAMES)+'}')
 results=[]
 for tag,ref in zip(TAGS,refs):
  for n,b in fragments[tag].items():(out/(n+'.html')).write_bytes(base64.b64decode(b,validate=True))
  manifest(tag);b=run(image,out,tag,expected=42)
  if grid(b.decode(),True)!=grid(ref.read_text(),False):raise ValueError(('grid mismatch',tag))
  q=out/'pages'/(tag+'.html');q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(b);results.append({'tag':tag,'sha256':hashlib.sha256(b).hexdigest()})
 for n in NAMES:
  q=out/(n+'.html');old=q.read_bytes()
  try:
   q.write_bytes(old+b' ')
   if run(image,out,TAGS[-1],expected=7):raise ValueError('partial digest refusal')
  finally:q.write_bytes(old)
 manifest('ja')
 if run(image,out,TAGS[-1],expected=7):raise ValueError('partial locale mismatch')
 manifest('../ja')
 if run(image,out,'../ja',expected=7):raise ValueError('partial invalid locale')
 for name,old,new in [('startup','dds-ext-card','bad'),('coverage','</div>','</bad>'),('speed','dds-ext-card','KOTOBA_')]:
  q=out/(name+'.html');original=q.read_bytes();q.write_text(original.decode().replace(old,new,1));manifest(TAGS[-1])
  try:
   if run(image,out,TAGS[-1],expected=7):raise ValueError('partial structure refusal')
  finally:q.write_bytes(original)
 if any(sha(Path(k))!=v for k,v in pins.items()):raise ValueError('input drift')
 rec={'status':'PASS_NATIVE_BENCHMARK_CARDS','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'observations':results,'persistedLocalizedGrids':True,'fullBenchmark':False,'publicationQualified':False}
 (out/'receipt.json').write_text(json.dumps(rec,indent=2)+'\n');print(rec['status'])
if __name__=='__main__':
 if len(sys.argv)==9 and sys.argv[1]=='--fragment-worker':print(json.dumps(worker(sys.argv[2],sys.argv[3:])))
 else:main()
