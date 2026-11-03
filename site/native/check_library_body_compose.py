#!/usr/bin/env python3
"""Compose historical native library sections with an explicit test-only marker."""
import argparse,hashlib,json,os,platform,re,shutil,subprocess
from pathlib import Path
from urllib.parse import urljoin
from check_footers import Tree,normalize,sections,ARTICLES,TAGS
ROOT=Path(__file__).resolve().parents[2]
NAMES=('shell','hero','identity','publish','catalog','status','compare')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def body(html,base,reference,privacy=None):
 t=Tree();t.feed(html);t.close();found=[]
 def walk(n):
  if not isinstance(n,list):return
  if n[0]=='body':found.append(n);return
  for c in n[2]:walk(c)
 walk(t.root)
 if len(t.stack)!=1 or len(found)!=1:raise ValueError('invalid body')
 n=found[0]
 if reference:
  wrap=n[2][0];footer=[c for c in wrap[2] if isinstance(c,list) and c[0]=='footer']
  if len(footer)!=1 or privacy is None:raise ValueError('missing footer')
  footer[0][2][0][2][2][2][-1]=privacy
 def resolve(n):
  if not isinstance(n,list):return
  n[1]=tuple((k,urljoin(base,v) if k in ('href','src') and v else v) for k,v in n[1])
  for c in n[2]:resolve(c)
 resolve(n);return normalize(n)
def main():
 p=argparse.ArgumentParser()
 for n in ('compiler','packager','sha256-source','shell-dir','hero-dir','static-dir','publish-dir','catalog-dir','status-dir','output'):p.add_argument('--'+n,type=Path,required=True)
 a=p.parse_args()
 if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS ARM64')
 compiler,packager,source=[x.resolve(strict=True) for x in (a.compiler,a.packager,a.sha256_source)]
 dirs={n:getattr(a,n+'_dir').resolve(strict=True) for n in ('shell','hero','static','publish','catalog','status')}
 specs={'shell':('PASS_NATIVE_LIBRARY_BODY_SHELL','nav.macho'),'hero':('PASS_NATIVE_LIBRARY_HERO','nav.macho'),'static':('PASS_NATIVE_LIBRARY_STATIC_SECTIONS','nav.macho'),'publish':('PASS_NATIVE_LIBRARY_PUBLISH','nav.macho'),'catalog':('PASS_NATIVE_CATALOG_SECTION','nav.macho'),'status':('PASS_NATIVE_LIBRARY_STATUS','nav.macho')}
 if sha(source)!=json.loads((ROOT/'site/native/dependencies.json').read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
 for x in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(x)],check=True,capture_output=True,timeout=30)
 receipts={}
 for n,(status,image) in specs.items():
  d=dirs[n];r=json.loads((d/'receipt.json').read_text())
  if r.get('status')!=status or r['binarySha256']!=sha(d/image):raise ValueError(('component mismatch',n))
  receipts[n]=r
 def fragment(name,tag):
  key='static' if name in ('identity','compare') else name;d=dirs[key]
  path=d/'pages'/(tag+'.html') if name in ('shell','catalog') else d/'pages'/tag/(('top' if name=='hero' else name)+'.html')
  obs=receipts[key]['observations'];matches=[x['sha256'] for x in obs if x['tag']==tag and (name not in ('identity','compare') or x.get('section')==name)]
  if len(matches)!=1 or sha(path)!=matches[0]:raise ValueError(('fragment mismatch',name,tag))
  return path
 files={(tag,n):fragment(n,tag) for tag in TAGS for n in NAMES}
 modules=[(ROOT/'site/native/library_body_compose.kotoba','src/site/native/library_body_compose.kotoba'),(ROOT/'test/site/native/library_body_compose_file.kotoba','src/site/native/library_body_compose_file.kotoba'),(source,'src/multiformats/sha256.kotoba')]
 refs=[ROOT/'site/dist'/('' if t=='en' else t)/'libraries/index.html' for t in TAGS];privacyrefs=[ROOT/'site/dist'/('' if t=='en' else t)/ARTICLES['infra'][1]/'index.html' for t in TAGS]
 tracked=[compiler,packager,ROOT/'site/native/dependencies.json',Path(__file__).resolve(),ROOT/'site/native/check_footers.py',*[x for x,_ in modules],*[d/'receipt.json' for d in dirs.values()],*[dirs[n]/v[1] for n,v in specs.items()],*files.values(),*refs,*privacyrefs]
 pins={str(x):sha(x) for x in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
 for x,n in modules:
  q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(x,q)
 lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/library_body_compose_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda z:z[1])))
 def run(image,*args,expected=0):
  fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
  if fd!=3:os.dup2(fd,3)
  try:r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=900)
  finally:
   if fd!=3:os.close(fd)
  if r.returncode!=expected or r.stderr:raise ValueError((Path(image).name,args,r.returncode,r.stderr[-500:]))
  return r.stdout
 run(compiler,'compile','/src/site/native/library_body_compose_file.kotoba','--target','aarch64-macos','--output','body.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
 info=run(compiler,'extract-native','body.kseed','--symbol','main','--output','body.raw');m=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
 if not m or int(m[2])!=(out/'body.raw').stat().st_size:raise ValueError('invalid entry')
 run(packager,'body-unsigned.macho','body.raw','fs2-cli-stdio-general',m[1].decode());image=out/'body.macho';shutil.copyfile(out/'body-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
 def manifest(tag):(out/'manifest.edn').write_text('{:tag '+json.dumps(tag)+' '+ ' '.join(':'+n+' '+json.dumps(sha(out/(n+'.html'))) for n in NAMES)+'}')
 results=[]
 for tag,ref,pref in zip(TAGS,refs,privacyrefs):
  for n in NAMES:shutil.copyfile(files[(tag,n)],out/(n+'.html'))
  manifest(tag);got=run(image,tag,expected=42)
  if b'UNQUALIFIED LIBRARY STATUS FOR TEST' not in got or b'KOTOBA_' in got:raise ValueError(('fixture marker',tag))
  original=ref.read_text().replace('site/generate.cljk','site/native/library_body_shell.kotoba')
  if tag=='ja':
   old='href="/ja/libraries/">日本語</a>';new='href="/libraries/">English</a>'
   if original.count(old)!=1:raise ValueError('unexpected Japanese navigation')
   original=original.replace(old,new)
   old_lead='<p class="kot-lead">その 2,215 公開リポジトリ内の <code class="kot-code">kotoba-lang</code> 組織、タグ語彙と共に 組織の GitHub トピック使用。名前、説明、またはでフィルター。 任意のタグの組み合わせ。</p>'
   new_lead='<p class="kot-lead"><code class="kot-code">kotoba-lang</code> の公開リポジトリ 2,215 件を掲載しています。GitHub のトピックと共通のタグを使い、名前・説明・タグの組み合わせで絞り込めます。</p>'
   if original.count(old_lead)!=1:raise ValueError('unexpected Japanese reference lead')
   original=original.replace(old_lead,new_lead)
  privacy=sections(pref.read_text(),True)[2][0][2][2][2][-1];base='https://kotoba-lang.org/'+('' if tag=='en' else tag+'/')+'libraries/'
  if body(got.decode(),base,False)!=body(original,base,True,privacy):raise ValueError(('body mismatch',tag))
  q=out/'pages'/(tag+'.html');q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(got);results.append({'tag':tag,'sha256':hashlib.sha256(got).hexdigest()});print(tag,'body pass',flush=True)
 for n in NAMES:
  q=out/(n+'.html');old=q.read_bytes()
  try:
   q.write_bytes(old+b' ')
   if run(image,TAGS[-1],expected=7):raise ValueError('modified fragment emitted output')
  finally:q.write_bytes(old)
 manifest('ja')
 if run(image,TAGS[-1],expected=7):raise ValueError('locale mismatch emitted output')
 manifest('../ja')
 if run(image,'../ja',expected=7):raise ValueError('invalid locale emitted output')
 for n,old,new in [('shell','KOTOBA_LIBRARY_SECTIONS_SLOT','KOTOBA_LIBRARY_SECTIONS_SLOTKOTOBA_LIBRARY_SECTIONS_SLOT'),('status','id="status"','id="missing"')]:
  q=out/(n+'.html');original=q.read_bytes();q.write_text(original.decode().replace(old,new,1));manifest(TAGS[-1])
  try:
   if run(image,TAGS[-1],expected=7):raise ValueError('malformed structure emitted output')
  finally:q.write_bytes(original)
 if any(sha(Path(k))!=v for k,v in pins.items()):raise ValueError('input drift')
 rec={'status':'PASS_NATIVE_LIBRARY_BODY_COMPOSE','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'observations':results,'historicalClaimsOnly':True,'testMarker':True,'persistedLocalizedBodies':True,'fullSiteGenerated':False,'publicationQualified':False}
 (out/'receipt.json').write_text(json.dumps(rec,indent=2)+'\n');print(rec['status'],flush=True)
if __name__=='__main__':main()
