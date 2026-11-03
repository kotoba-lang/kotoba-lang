#!/usr/bin/env python3
"""Observe the native historical homepage library section, not current catalog or publication."""
import argparse,hashlib,json,os,platform,re,shutil,subprocess
from pathlib import Path
from check_footers import Tree, normalize, TAGS
ROOT=Path(__file__).resolve().parents[2]
MODULES=['home_library_page','home_library','home_section','source_concat','locale_tree','locale_filter','locale_message','locale_catalog','locale_placeholders','locale_links','locale_rebase','library_aggregate_stream','library_catalog_stream','library_classifier']
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def section(html):
 t=Tree();t.feed(html);t.close();found=[]
 def walk(n):
  if not isinstance(n,list):return
  if n[0]=='section' and dict(n[1]).get('id')=='libraries':found.append(n);return
  for c in n[2]:walk(c)
 walk(t.root)
 if len(t.stack)!=1 or len(found)!=1:raise ValueError(('section count',len(found)))
 return normalize(found[0])
def main():
 p=argparse.ArgumentParser()
 for n in ('compiler','packager','sha256-source','html-core','html-hiccup','dds-components','output'):p.add_argument('--'+n,type=Path,required=True)
 p.add_argument('--tag',choices=TAGS,action='append');a=p.parse_args();tags=a.tag or TAGS
 if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS ARM64')
 compiler,packager,sha_source,core,hiccup,components=[v.resolve(strict=True) for v in (a.compiler,a.packager,a.sha256_source,a.html_core,a.html_hiccup,a.dds_components)]
 for x in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(x)],check=True,capture_output=True,timeout=30)
 deps=json.loads((ROOT/'site/native/dependencies.json').read_text());html=json.loads((ROOT/'site/native/html-dependencies.json').read_text());dds=json.loads((ROOT/'site/native/dds-dependencies.json').read_text())
 if sha(sha_source)!=deps['dependencies'][0]['sha256'] or [sha(core),sha(hiccup)]!=[x['sha256'] for x in html['dependencies']] or sha(components)!=dds['dependencies'][0]['sha256']:raise ValueError('dependency drift')
 modules=[(ROOT/'site/native'/(n+'.kotoba'),'src/site/native/'+n+'.kotoba') for n in MODULES]+[(ROOT/'test/site/native/home_library_file.kotoba','src/site/native/home_library_file.kotoba'),(sha_source,'src/multiformats/sha256.kotoba'),(core,'src/html_core.kotoba'),(hiccup,'src/html_hiccup.kotoba'),(components,'src/components.kotoba')]
 inputs=[(ROOT/'site/generate.cljk','generator.cljk'),(ROOT/'site/library-taxonomy.edn','library-taxonomy.edn'),(ROOT/'site/library-catalog.edn','library-catalog.edn'),(ROOT/'lang/package-registry.edn','package-registry.edn')]
 catalogs=[ROOT/'site/i18n'/(t+'.json') for t in tags];refs=[ROOT/'site/dist'/('' if t=='en' else t)/'index.html' for t in tags]
 tracked=[compiler,packager,Path(__file__).resolve(),ROOT/'site/native/check_footers.py',*[x for x,_ in modules],*[x for x,_ in inputs],*catalogs,*refs];pins={str(x):sha(x) for x in tracked}
 out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
 for x,n in modules+inputs:
  q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(x,q)
 for x in catalogs:
  q=out/'site/i18n'/x.name;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(x,q)
 lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/home_library_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda z:z[1])))
 fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
 if fd!=3:os.dup2(fd,3)
 def run(image,*args,expected=0):
  r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=900)
  if r.returncode!=expected or r.stderr:raise ValueError((Path(image).name,args,r.returncode,r.stderr[-800:]))
  return r.stdout
 try:
  run(compiler,'compile','/src/site/native/home_library_file.kotoba','--target','aarch64-macos','--output','nav.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
  info=run(compiler,'extract-native','nav.kseed','--symbol','main','--output','nav.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
  if not m or int(m[2])!=(out/'nav.raw').stat().st_size:raise ValueError('entry mismatch')
  run(packager,'nav-unsigned.macho','nav.raw','fs2-cli-stdio-general',m[1].decode());image=out/'nav.macho';shutil.copyfile(out/'nav-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
  obs=[]
  for tag,ref in zip(tags,refs):
   b=run(image,'library',tag,expected=42)
   if section(b.decode())!=section(ref.read_text()):raise ValueError(('section mismatch',tag))
   obs.append({'tag':tag,'sha256':hashlib.sha256(b).hexdigest()});print(tag,'library pass',flush=True)
  if run(image,'library','../ja',expected=7):raise ValueError('invalid locale emitted output')
  for name in ('generator.cljk','library-taxonomy.edn','library-catalog.edn','package-registry.edn'):
   q=out/name;old=q.read_bytes()
   try:
    q.write_bytes(old+b' ')
    if run(image,'library',tags[0],expected=7):raise ValueError(('modified input emitted output',name))
   finally:q.write_bytes(old)
  if 'ja' in tags:
   q=out/'site/i18n/ja.json';old=q.read_bytes()
   try:
    q.write_text('{}')
    if run(image,'library','ja',expected=7):raise ValueError('missing translations emitted output')
   finally:q.write_bytes(old)
 finally:
  if fd!=3:os.close(fd)
 if any(sha(x)!=pins[str(x)] for x in tracked):raise ValueError('input drift')
 receipt={'status':'PASS_NATIVE_HOME_LIBRARY','inputs':pins,'observations':obs,'binarySha256':sha(image),'invalidLocaleAndModifiedSourcesRefused':True,'currentGithubQualified':False,'fullHomepage':False,'publicationQualified':False}
 (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])
if __name__=='__main__':main()
