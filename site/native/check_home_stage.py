#!/usr/bin/env python3
"""Exercise native private home staging; test inputs are not release-qualified."""
import argparse,hashlib,json,os,platform,re,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for n in ('compiler','packager','sha256-source','documents-dir','output'):p.add_argument('--'+n,type=Path,required=True)
 a=p.parse_args()
 if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
 compiler,packager,source,documents=(x.resolve(strict=True) for x in (a.compiler,a.packager,a.sha256_source,a.documents_dir))
 if sha(source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
 for x in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(x)],check=True,capture_output=True,timeout=30)
 rec=json.loads((documents/'receipt.json').read_text());obs=rec.get('observations',[])
 if rec.get('status')!='PASS_NATIVE_HOME_DOCUMENT_REAL_BODY' or not rec.get('fixtureBenchmark') or rec.get('publicationQualified') is not False or len(obs)!=len(TAGS) or {x.get('tag') for x in obs}!=set(TAGS):raise ValueError('expected private fixture documents')
 expected={x['tag']:x['sha256'] for x in obs};pages=[documents/'pages'/(t+'.html') for t in TAGS]
 if any(sha(x)!=expected[t] for t,x in zip(TAGS,pages)):raise ValueError('document digest drift')
 modules=[(ROOT/'site/native/home_stage.kotoba','src/site/native/home_stage.kotoba'),(ROOT/'test/site/native/home_stage_file.kotoba','src/site/native/home_stage_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
 tracked=[compiler,packager,ROOT/'site/native/dependencies.json',Path(__file__).resolve(),*[x for x,_ in modules],documents/'receipt.json',*pages];pins={str(x):sha(x) for x in tracked}
 out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
 for x,n in modules:
  q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(x,q)
 lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_stage_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
 def build(image,*args):
  fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
  if fd!=3:os.dup2(fd,3)
  try:r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
  finally:
   if fd!=3:os.close(fd)
  if r.returncode or r.stderr:raise ValueError((image.name,args,r.returncode,r.stderr[-500:]))
  return r.stdout
 build(compiler,'compile','/src/site/native/home_stage_file.kotoba','--target','aarch64-macos','--output','stage.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
 info=build(compiler,'extract-native','stage.kseed','--symbol','main','--output','stage.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
 if not m or int(m[2])!=(out/'stage.raw').stat().st_size:raise ValueError('invalid entry')
 build(packager,'stage-unsigned.macho','stage.raw','amu-cli-host',m[1].decode());image=out/'stage.macho';shutil.copyfile(out/'stage-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
 clean=out/'test-pages';clean.mkdir()
 for tag,p in zip(TAGS,pages):
  b=p.read_bytes();marker=b'UNQUALIFIED EVIDENCE FOR TEST'
  if b.count(marker)!=1:raise ValueError('fixture marker missing')
  (clean/(tag+'.html')).write_bytes(b.replace(marker,b'SYNTHETIC STAGING TEST ONLY'))
 def make_root(name):
  d=out/name;d.mkdir()
  for tag in TAGS[1:]:(d/tag).mkdir()
  return d
 def manifest(name,paths,tags=TAGS,hash_override=None):
  entries=[]
  for i,(tag,path) in enumerate(zip(tags,paths)):
   digest=hash_override if i==0 and hash_override is not None else sha(path)
   entries.append('{:tag '+json.dumps(tag)+' :path '+json.dumps(str(path))+' :sha256 '+json.dumps(digest)+'}')
  q=out/(name+'.edn');q.write_text('{:entries ['+' '.join(entries)+']}');return q
 clean_pages=[clean/(t+'.html') for t in TAGS]; fixture=manifest('fixture',pages);valid=manifest('valid',clean_pages)
 def run(m,stage,expected):
  r=subprocess.run([str(image),str(m),str(stage)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},capture_output=True,timeout=600)
  if r.returncode!=expected or r.stdout or r.stderr:raise ValueError((m.name,r.returncode,r.stdout[:100],r.stderr[-500:]))
 def output_path(root,tag):return root/('' if tag=='en' else tag)/'index.html'
 def no_outputs(root):
  if any(output_path(root,t).exists() or output_path(root,t).with_name('index.html.part').exists() for t in TAGS):raise ValueError('preflight left output')
 root=make_root('fixture-stage');run(fixture,root,7);no_outputs(root)
 root=make_root('bad-hash-stage');run(manifest('bad-hash',clean_pages,hash_override='0'*64),root,7);no_outputs(root)
 root=make_root('bad-tag-stage');run(manifest('bad-tag',clean_pages,('ar',)+TAGS[1:]),root,7);no_outputs(root)
 broken=out/'mixed-freebuff.html';broken.write_bytes(clean_pages[0].read_bytes().replace(b'</body>',b'<p>FrEeBuFf</p></body>',1));root=make_root('freebuff-stage');run(manifest('freebuff',[broken,*clean_pages[1:]]),root,7);no_outputs(root)
 broken2=out/'wrong-lang.html';broken2.write_bytes(clean_pages[0].read_bytes().replace(b'<html lang="en">',b'<html lang="ja">',1));root=make_root('wrong-lang-stage');run(manifest('wrong-lang',[broken2,*clean_pages[1:]]),root,7);no_outputs(root)
 root=make_root('occupied-stage');(root/'index.html').write_bytes(b'keep');run(valid,root,7)
 if (root/'index.html').read_bytes()!=b'keep' or any(output_path(root,t).exists() for t in TAGS[1:]):raise ValueError('existing output changed')
 root=make_root('clean-stage');run(valid,root,42)
 if any(output_path(root,t).read_bytes()!=p.read_bytes() or output_path(root,t).with_name('index.html.part').exists() for t,p in zip(TAGS,clean_pages)):raise ValueError('staged output mismatch')
 if any(sha(x)!=h for x,h in ((Path(p),v) for p,v in pins.items())):raise ValueError('input drift')
 receipt={'status':'PASS_NATIVE_HOME_STAGE','inputs':pins,'binarySha256':sha(image),'locales':[{'tag':t,'sha256':sha(output_path(root,t))} for t in TAGS],'fixtureRefused':True,'negativeControls':True,'syntheticPositiveInputs':True,'fullSiteGenerated':False,'publicationQualified':False}
 (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
