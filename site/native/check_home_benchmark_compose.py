#!/usr/bin/env python3
"""Exercise native benchmark composition with an explicitly synthetic remainder."""
import argparse, hashlib, json, os, platform, re, shutil, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def run(image,folder,*args,expected=0):
 fd=os.open(folder,os.O_RDONLY|os.O_DIRECTORY)
 if fd!=3: os.dup2(fd,3)
 try: r=subprocess.run([str(image),*map(str,args)],cwd=folder,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
 finally:
  if fd!=3: os.close(fd)
 if r.returncode!=expected or r.stderr: raise ValueError((image.name,args,r.returncode,r.stderr[-500:]))
 return r.stdout
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ('compiler','packager','sha256-source','intro-dir','cards-dir','scaling-dir','output'): p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args()
 if (platform.system(),platform.machine())!=('Darwin','arm64'): raise ValueError('requires macOS arm64')
 compiler,packager,source,intro,cards,scaling=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.intro_dir,a.cards_dir,a.scaling_dir))
 if sha(source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']: raise ValueError('SHA dependency drift')
 for x in (compiler,packager): subprocess.run(['/usr/bin/codesign','--verify','--strict',str(x)],check=True,capture_output=True,timeout=30)
 checks=((intro,'PASS_NATIVE_BENCHMARK_INTRO','nav.macho','observations'),(cards,'PASS_NATIVE_BENCHMARK_CARDS','cards.macho','observations'),(scaling,'PASS_BOUNDED_SCALING_SECTION','page.macho','locales'))
 records={}
 for d,status,binary,field in checks:
  r=json.loads((d/'receipt.json').read_text()); obs=r.get(field,[])
  if r.get('status')!=status or sha(d/binary)!=r['binarySha256'] or len(obs)!=len(TAGS) or {x.get('tag') for x in obs}!=set(TAGS): raise ValueError('component qualification or locale mismatch')
  records[status]={x['tag']:x['sha256'] for x in obs}
 for d,status in ((cards,'PASS_NATIVE_BENCHMARK_CARDS'),(scaling,'PASS_BOUNDED_SCALING_SECTION')):
  if any(sha(d/'pages'/(t+'.html'))!=records[status][t] for t in TAGS): raise ValueError('fragment drift')
 modules=[(ROOT/'site/native/home_benchmark_compose.kotoba','src/site/native/home_benchmark_compose.kotoba'),(ROOT/'test/site/native/home_benchmark_compose_file.kotoba','src/site/native/home_benchmark_compose_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
 tracked=[compiler,packager,ROOT/'site/native/dependencies.json',Path(__file__).resolve(),*[p for p,_ in modules],*[d/'receipt.json' for d,_,_,_ in checks],*[d/b for d,_,b,_ in checks],*[d/'pages'/(t+'.html') for d in (cards,scaling) for t in TAGS]]
 pins={str(x):sha(x) for x in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
 for src,path in modules:
  dest=out/path;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dest)
 lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_benchmark_compose_file.kotoba\nroot /src\n'+''.join('file '+sha(out/path)+' /'+path+'\n' for _,path in sorted(modules,key=lambda x:x[1])))
 run(compiler,out,'compile','/src/site/native/home_benchmark_compose_file.kotoba','--target','aarch64-macos','--output','page.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
 info=run(compiler,out,'extract-native','page.kseed','--symbol','main','--output','page.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
 if not m or int(m[2])!=(out/'page.raw').stat().st_size: raise ValueError('invalid entry')
 run(packager,out,'page-unsigned.macho','page.raw','fs2-cli-stdio-general',m[1].decode());image=out/'page.macho';shutil.copyfile(out/'page-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
 def get_intro(tag):
  data=run(intro/'nav.macho',intro,'intro',tag,expected=42)
  if hashlib.sha256(data).hexdigest()!=records['PASS_NATIVE_BENCHMARK_INTRO'][tag]: raise ValueError('intro drift')
  return tag,data
 intros=dict(get_intro(tag) for tag in TAGS)
 names=('intro','cards','scaling','remainder');results=[]
 def manifest(tag): (out/'manifest.edn').write_text('{:tag "'+tag+'" '+' '.join(':'+n+' "'+sha(out/(n+'.html'))+'"' for n in names)+'}')
 for tag in TAGS:
  values=(intros[tag],(cards/'pages'/(tag+'.html')).read_bytes(),(scaling/'pages'/(tag+'.html')).read_bytes(),b'<h3>UNQUALIFIED REMAINDER FOR TEST</h3>')
  for n,data in zip(names,values): (out/(n+'.html')).write_bytes(data)
  manifest(tag);result=run(image,out,tag,expected=42);expected=values[0][:-len(b'</section>')]+values[1]+values[2]+values[3]+b'</section>'
  if result!=expected: raise ValueError(('composition mismatch',tag))
  results.append({'tag':tag,'sha256':hashlib.sha256(result).hexdigest()})
 for n in names:
  f=out/(n+'.html');old=f.read_bytes()
  try:
   f.write_bytes(old+b' ')
   if run(image,out,TAGS[-1],expected=7): raise ValueError('partial digest refusal')
  finally: f.write_bytes(old)
 manifest('ja')
 if run(image,out,TAGS[-1],expected=7): raise ValueError('partial tag refusal')
 manifest('../ja')
 if run(image,out,'../ja',expected=7): raise ValueError('partial invalid locale')
 for n in names:
  f=out/(n+'.html');old=f.read_bytes()
  try:
   f.write_bytes(b'TEST BENCHMARK');manifest(TAGS[-1])
   if run(image,out,TAGS[-1],expected=7): raise ValueError('partial structure refusal')
  finally: f.write_bytes(old)
 if any(sha(x)!=pins[str(x)] for x in tracked): raise ValueError('input drift')
 receipt={'status':'PASS_NATIVE_BENCHMARK_COMPOSE','inputs':pins,'binarySha256':sha(image),'locales':results,'syntheticRemainder':True,'fullBenchmark':False,'publicationQualified':False}
 (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__': main()
