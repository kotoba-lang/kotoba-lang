#!/usr/bin/env python3
"""Exercise private native direct site, home and library staging with synthetic fixtures."""
import argparse,hashlib,json,os,platform,re,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 ap=argparse.ArgumentParser(description=__doc__)
 for n in ('compiler','packager','sha256-source','sections-dir','home-dir','library-dir','output'):ap.add_argument('--'+n,type=Path,required=True)
 a=ap.parse_args()
 if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
 compiler,packager,source,sections,home,library=(x.resolve(strict=True) for x in (a.compiler,a.packager,a.sha256_source,a.sections_dir,a.home_dir,a.library_dir))
 deps=ROOT/'site/native/dependencies.json'
 if sha(source)!=json.loads(deps.read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
 for x in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(x)],check=True,capture_output=True,timeout=30)
 sr=json.loads((sections/'receipt.json').read_text());hr=json.loads((home/'receipt.json').read_text())
 sec_image=sections/'site.macho';home_image=home/'stage.macho';sec_manifest=sections/'manifest.edn'
 if sr.get('status')!='PASS_NATIVE_SITE_SECTIONS_CONTROLLER_MACOS' or sr.get('publicationQualified') is not False or sha(sec_image)!=sr['binarySha256']:raise ValueError('unqualified site sections')
 if hr.get('status')!='PASS_NATIVE_HOME_STAGE' or not hr.get('syntheticPositiveInputs') or hr.get('publicationQualified') is not False or sha(home_image)!=hr['binarySha256']:raise ValueError('unqualified test home stage')
 lr=json.loads((library/'receipt.json').read_text())
 if lr.get('status')!='PASS_NATIVE_LIBRARY_STAGE' or not lr.get('syntheticPositiveInputs') or lr.get('publicationQualified') is not False or sha(library/'stage.macho')!=lr['binarySha256']:raise ValueError('unqualified test library stage')
 libraries=[library/'test-pages'/(tag+'.html') for tag in TAGS]
 library_hashes={x['tag']:x['sha256'] for x in lr['locales']}
 if set(library_hashes)!=set(TAGS) or len(lr['locales'])!=len(TAGS) or any(not p.is_file() or sha(p)!=library_hashes[t] for t,p in zip(TAGS,libraries)):raise ValueError('library fixture digest drift')
 pages=[home/'test-pages'/(tag+'.html') for tag in TAGS];ref=sections/'site';ref_files=[p for p in ref.rglob('*') if p.is_file()]
 if len(ref_files)!=147 or any(not p.is_file() for p in pages):raise ValueError('reference file coverage mismatch')
 modules=[(ROOT/'site/native/site_library_direct.kotoba','src/site/native/site_library_direct.kotoba'),(ROOT/'test/site/native/site_library_direct_file.kotoba','src/site/native/site_library_direct_file.kotoba'),(ROOT/'site/native/library_stage.kotoba','src/site/native/library_stage.kotoba'),(ROOT/'site/native/site_home_direct.kotoba','src/site/native/site_home_direct.kotoba'),(ROOT/'site/native/site_sections_controller.kotoba','src/site/native/site_sections_controller.kotoba'),(ROOT/'site/native/home_stage.kotoba','src/site/native/home_stage.kotoba'),(ROOT/'site/native/hosting_metadata.kotoba','src/site/native/hosting_metadata.kotoba'),(ROOT/'site/native/article_stage.kotoba','src/site/native/article_stage.kotoba'),(ROOT/'site/native/start_card_stage.kotoba','src/site/native/start_card_stage.kotoba'),(source,'src/multiformats/sha256.kotoba')]
 tracked=[compiler,packager,deps,Path(__file__).resolve(),*[p for p,_ in modules],sections/'receipt.json',home/'receipt.json',sec_image,sec_manifest,home_image,library/'receipt.json',library/'stage.macho',*libraries,*pages,*ref_files]
 tracked += [Path(p) for p in sr['inputs']] + [Path(p) for p in hr['inputs']]
 pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
 for src,n in modules:
  q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,q)
 lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/site_library_direct_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
 def build(image,*args):
  fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
  if fd!=3:os.dup2(fd,3)
  try:r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
  finally:
   if fd!=3:os.close(fd)
  if r.returncode or r.stderr:raise ValueError((image.name,args,r.returncode,r.stderr[-500:]))
  return r.stdout
 build(compiler,'compile','/src/site/native/site_library_direct_file.kotoba','--target','aarch64-macos','--output','site.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
 info=build(compiler,'extract-native','site.kseed','--symbol','main','--output','site.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
 if not m or int(m[2])!=(out/'site.raw').stat().st_size:raise ValueError('invalid entry')
 build(packager,'site-unsigned.macho','site.raw','amu-cli-host',m[1].decode());image=out/'site.macho';shutil.copyfile(out/'site-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
 entries=['{:tag '+json.dumps(t)+' :path '+json.dumps(str(p))+' :sha256 '+json.dumps(sha(p))+'}' for t,p in zip(TAGS,pages)]
 hm=out/'home-manifest.edn';hm.write_text('{:entries ['+' '.join(entries)+']}')
 library_entries=['{:tag '+json.dumps(t)+' :path '+json.dumps(str(p))+' :sha256 '+json.dumps(sha(p))+'}' for t,p in zip(TAGS,libraries)]
 lm=out/'library-manifest.edn';lm.write_text('{:entries ['+' '.join(library_entries)+']}')
 def roots(name):
  private=out/(name+'-private');site=out/(name+'-site');private.mkdir();site.mkdir();return private,site
 def run(private,site,expected,secsha=None,hmsha=None,lmsha=None,section_manifest=sec_manifest):
  args=[str(section_manifest),secsha or sha(section_manifest),str(hm),hmsha or sha(hm),str(lm),lmsha or sha(lm),str(private),str(site)]
  r=subprocess.run([str(image),*args],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},capture_output=True,timeout=5400)
  if r.returncode!=expected or r.stdout or r.stderr:raise ValueError((r.returncode,r.stdout[:100],r.stderr[-500:]))
 private,site=roots('bad-sections');run(private,site,7,secsha='0'*64)
 if list(private.iterdir()) or list(site.iterdir()):raise ValueError('bad section manifest digest left output')
 private,site=roots('bad-home');run(private,site,7,hmsha='0'*64)
 if list(private.iterdir()) or list(site.iterdir()):raise ValueError('bad manifest left output')
 private,site=roots('bad-library');run(private,site,7,lmsha='0'*64)
 if list(private.iterdir()) or list(site.iterdir()):raise ValueError('bad library digest left output')
 bad=out/'bad-section-manifest.edn';bad.write_text('{}');private,site=roots('failed-child');run(private,site,7,section_manifest=bad)
 if any(site.iterdir()):raise ValueError('section failure created site output')
 private,site=roots('candidate');run(private,site,42)
 wanted={str(p.relative_to(ref)) for p in ref_files}|{str(Path('' if t=='en' else t)/'index.html') for t in TAGS}|{str(Path('' if t=='en' else t)/'libraries/index.html') for t in TAGS}
 actual={str(p.relative_to(site)) for p in site.rglob('*') if p.is_file()}
 if len(wanted)!=181 or actual!=wanted:raise ValueError(('site file coverage mismatch',len(actual),len(wanted)))
 for p in ref_files:
  if sha(p)!=sha(site/p.relative_to(ref)):raise ValueError(('section mismatch',str(p)))
 for t,p in zip(TAGS,pages):
  q=site/('' if t=='en' else t)/'index.html'
  if sha(q)!=sha(p) or b'SYNTHETIC STAGING TEST ONLY' not in q.read_bytes():raise ValueError(('home mismatch',t))
 for t,p in zip(TAGS,libraries):
  q=site/('' if t=='en' else t)/'libraries/index.html'
  if sha(q)!=sha(p) or b'SYNTHETIC STAGING TEST ONLY' not in q.read_bytes():raise ValueError(('library mismatch',t))
 if any(b'freebuff' in p.read_bytes().lower() for p in site.rglob('*') if p.is_file()):raise ValueError('Freebuff in candidate')
 before={str(p):sha(p) for p in site.rglob('*') if p.is_file()};run(private,site,7)
 if any(sha(Path(p))!=h for p,h in before.items()):raise ValueError('occupied stage changed')
 if any(sha(Path(p))!=h for p,h in pins.items()):raise ValueError('input drift')
 rec={'status':'PASS_NATIVE_SITE_LIBRARY_DIRECT','inputs':pins,'binarySha256':sha(image),'sectionFiles':147,'homeLocales':list(TAGS),'libraryLocales':list(TAGS),'totalFiles':181,'fixtureHome':True,'fixtureLibrary':True,'fullSiteGenerated':False,'publicationQualified':False,'preflightAndChildRefusals':True}
 (out/'receipt.json').write_text(json.dumps(rec,indent=2)+'\n');print(rec['status'])
if __name__=='__main__':main()
