#!/usr/bin/env python3
"""Compose native localized home documents; benchmark remains a fixture."""
import argparse,base64,hashlib,json,os,platform,re,shutil,subprocess,sys
from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
class Shape(HTMLParser):
 def __init__(self):super().__init__();self.html=[];self.head=0;self.body=0
 def handle_starttag(self,t,a):
  if t=='html':self.html.append(dict(a))
  elif t=='head':self.head+=1
  elif t=='body':self.body+=1
def worker(tag,folder):
 folder=Path(folder);fd=os.open(folder,os.O_RDONLY|os.O_DIRECTORY)
 if fd!=3:os.dup2(fd,3)
 try:r=subprocess.run([str(folder/'head.macho'),tag],cwd=folder,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
 finally:
  if fd!=3:os.close(fd)
 if r.returncode!=42 or r.stderr:raise ValueError(('head',tag,r.returncode,r.stderr[-500:]))
 obs=json.loads((folder/'receipt.json').read_text())['observations'];expected=[x['sha256'] for x in obs if x['tag']==tag]
 if len(expected)!=1 or hashlib.sha256(r.stdout).hexdigest()!=expected[0]:raise ValueError(('head digest',tag))
 return base64.b64encode(r.stdout).decode('ascii')
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for n in ('compiler','packager','sha256-source','head-dir','body-dir','output'):p.add_argument('--'+n,type=Path,required=True)
 a=p.parse_args()
 if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
 compiler,packager,source,head,body=(x.resolve(strict=True) for x in (a.compiler,a.packager,a.sha256_source,a.head_dir,a.body_dir))
 if sha(source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
 for x in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(x)],check=True,capture_output=True,timeout=30)
 hr=json.loads((head/'receipt.json').read_text());br=json.loads((body/'receipt.json').read_text())
 if hr.get('status')!='PASS_NATIVE_HOME_HEAD' or sha(head/'head.macho')!=hr['binarySha256']:raise ValueError('unqualified head')
 if len(hr['observations'])!=len(TAGS) or {x['tag'] for x in hr['observations']}!=set(TAGS):raise ValueError('head locale coverage')
 if br.get('status')!='PASS_NATIVE_HOME_BODY_REAL_CHAPTERS' or not br.get('persistedLocalizedBodies') or not br.get('nativeLibrary') or not br.get('fixtureBenchmark'):raise ValueError('unqualified body')
 expected={x['tag']:x['sha256'] for x in br['observations']}
 if len(br['observations'])!=len(TAGS) or set(expected)!=set(TAGS):raise ValueError('body locale coverage')
 body_files=[body/'pages'/(t+'.html') for t in TAGS]
 if any(sha(x)!=expected[t] for t,x in zip(TAGS,body_files)):raise ValueError('body digest drift')
 modules=[(ROOT/'site/native/home_document.kotoba','src/site/native/home_document.kotoba'),(ROOT/'test/site/native/home_document_file.kotoba','src/site/native/home_document_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
 inputs=[compiler,packager,ROOT/'site/native/dependencies.json',Path(__file__).resolve(),*[x for x,_ in modules],head/'receipt.json',head/'head.macho',body/'receipt.json',*body_files];pins={str(x):sha(x) for x in inputs}
 out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
 for f,n in modules:
  q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(f,q)
 lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_document_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
 def run(image,d,*args,expected=0):
  fd=os.open(d,os.O_RDONLY|os.O_DIRECTORY)
  if fd!=3:os.dup2(fd,3)
  try:r=subprocess.run([str(image),*map(str,args)],cwd=d,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
  finally:
   if fd!=3:os.close(fd)
  if r.returncode!=expected or r.stderr:raise ValueError((image.name,args,r.returncode,r.stderr[-500:]))
  return r.stdout
 run(compiler,out,'compile','/src/site/native/home_document_file.kotoba','--target','aarch64-macos','--output','page.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
 info=run(compiler,out,'extract-native','page.kseed','--symbol','main','--output','page.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
 if not m or int(m[2])!=(out/'page.raw').stat().st_size:raise ValueError('invalid entry')
 run(packager,out,'page-unsigned.macho','page.raw','fs2-cli-stdio-general',m[1].decode());image=out/'page.macho';shutil.copyfile(out/'page-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
 def get(tag):
  r=subprocess.run([sys.executable,str(Path(__file__).resolve()),'--fragment-worker',tag,str(head)],capture_output=True,timeout=900,check=True)
  return tag,base64.b64decode(json.loads(r.stdout),validate=True)
 with ThreadPoolExecutor(max_workers=4) as pool:heads=dict(pool.map(get,TAGS))
 def manifest(tag):(out/'document-manifest.edn').write_text('{:tag "'+tag+'" :head "'+sha(out/'head.html')+'" :body "'+sha(out/'body.html')+'"}')
 results=[]
 for tag in TAGS:
  (out/'head.html').write_bytes(heads[tag]);shutil.copyfile(body/'pages'/(tag+'.html'),out/'body.html');manifest(tag)
  b=run(image,out,tag,expected=42);s=b.decode();shape=Shape();shape.feed(s);shape.close();rtl=tag in ('ar','arz','ur');attrs={'lang':tag}
  if rtl:attrs['dir']='rtl'
  expected_html='<!DOCTYPE html>\n<html lang="'+tag+('" dir="rtl">' if rtl else '">')+(out/'head.html').read_text()+(out/'body.html').read_text()+'</html>'
  if s!=expected_html or shape.html!=[attrs] or shape.head!=1 or shape.body!=1 or 'KOTOBA_' in s or 'freebuff' in s.lower() or 'TEST BENCHMARK' not in s:raise ValueError(('document mismatch',tag))
  q=out/'pages'/(tag+'.html');q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(b);results.append({'tag':tag,'sha256':hashlib.sha256(b).hexdigest()})
 for name in ('head.html','body.html'):
  q=out/name;old=q.read_bytes()
  try:
   q.write_bytes(old+b' ')
   if run(image,out,TAGS[-1],expected=7):raise ValueError('partial output on digest refusal')
  finally:q.write_bytes(old)
 manifest('ja')
 if run(image,out,TAGS[-1],expected=7):raise ValueError('partial output on locale mismatch')
 manifest('../ja')
 if run(image,out,'../ja',expected=7):raise ValueError('partial output on invalid locale')
 for name,old,new in [('head.html','<head>','<bad>'),('body.html','</body>','</bad>'),('body.html','TEST BENCHMARK','KOTOBA_HERO_SLOT')]:
  q=out/name;original=q.read_bytes();s=original.decode()
  if s.count(old)!=1:raise ValueError('negative control anchor')
  q.write_text(s.replace(old,new,1));manifest(TAGS[-1])
  try:
   if run(image,out,TAGS[-1],expected=7):raise ValueError('partial output on structure refusal')
  finally:q.write_bytes(original)
 if any(sha(Path(k))!=v for k,v in pins.items()):raise ValueError('input drift')
 rec={'status':'PASS_NATIVE_HOME_DOCUMENT_REAL_BODY','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'observations':results,'nativeLibrary':True,'fixtureBenchmark':True,'persistedLocalizedDocuments':True,'fullHomepage':False,'publicationQualified':False}
 (out/'receipt.json').write_text(json.dumps(rec,indent=2)+'\n');print(rec['status'])
if __name__=='__main__':
 if len(sys.argv)==4 and sys.argv[1]=='--fragment-worker':print(json.dumps(worker(sys.argv[2],sys.argv[3])))
 else:main()
