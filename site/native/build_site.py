#!/usr/bin/env python3
"""Build the static site with pinned Amu native programs, without CLJK fallback.

Python stages bytes, hashes files, and observes child exit codes. Kotoba owns
all authority parsing, localization, HTML/JSON rendering and composition.
The new output directory is private until every native producer succeeds.
"""
import argparse,concurrent.futures,hashlib,json,os,shutil
from pathlib import Path
from build_support import Build,ROOT,TAGS,native_run,sha,write_bytes,program_image

def manifest(folder,tag,parts,name='manifest.edn'):
 for k,data in parts.items():write_bytes(folder/(k+'.html'),data)
 (folder/name).write_text('{:tag '+json.dumps(tag)+' '+' '.join(':'+k+' "'+sha(folder/(k+'.html'))+'"' for k in parts)+'}')

class Job:
 def __init__(self,output,tag):
  self.output=Path(output);self.tag=tag;self.folder=self.output/'jobs'/tag
  self.folder.mkdir(parents=True,exist_ok=False)
  # Immutable input copies are shared by this job's sequential native processes.
  data=json.loads((self.output/'data.json').read_text())
  for name,path in data.items():write_bytes(self.folder/name,Path(path).read_bytes())
  self.fragments={}
 def run(self,name,*args):
  result=native_run(program_image(self.output/name),self.folder,args)
  write_bytes(self.output/'fragments'/self.tag/(name+'-'+('-'.join(args) or 'data')+'.html'),result)
  return result
 def compose(self,name,parts,document=False,tag=None):
  manifest(self.folder,tag or self.tag,parts,'document-manifest.edn' if document else 'manifest.edn')
  return self.run(name,tag or self.tag)
 def publish(self,path,data):
  if b'KOTOBA_' in data or b'UNQUALIFIED EVIDENCE FOR TEST' in data or b'UNQUALIFIED LIBRARY STATUS FOR TEST' in data:raise ValueError('unfilled/test fragment '+path)
  write_bytes(self.output/'candidate'/path,data)
 def home(self):
  t=self.tag
  cards={key:self.run('home_benchmark_'+which+'_card',which,t) for key,which in [('startup','startup'),('coverage','runtime_coverage'),('speed','runtime_speed'),('loop','loop'),('scaling','scaling'),('domain','domain')]}
  grid=self.compose('home_benchmark_cards',cards)
  tail=self.compose('home_benchmark_tail',{'domain':self.run('home_benchmark_domain_section',t),'evidence':self.run('home_benchmark_evidence',t)})
  bench=self.compose('home_benchmark_compose',{'intro':self.run('home_benchmark_intro','intro',t),'cards':grid,'scaling':self.run('home_benchmark_scaling_section',t),'runtime':self.run('home_benchmark_runtime_section',t),'compile':self.run('home_benchmark_compile_section',t),'remainder':tail})
  chapters=self.compose('home_chapters_compose',{'opening':self.run('home_opening_chapters','opening',t),'evidence':self.run('home_evidence_template',t),'benchmark':bench,'developer':self.run('home_developer_template',t),'library':self.run('home_library','library',t),'closing':self.run('home_closing_chapter','closing',t)})
  body=self.compose('home_body_compose',{'shell':self.run('home_body_shell',t),'hero':self.run('home_command','hero',t),'chapters':chapters,'scripts':self.run('home_script_bundle')})
  doc=self.compose('home_document',{'head':self.run('home_head',t),'body':body},True)
  self.publish(('' if t=='en' else t+'/')+'index.html',doc)
  print(t,'home',len(doc),flush=True)
 def library(self):
  t=self.tag;write_bytes(self.folder/'rows.html',(self.output/'rows.html').read_bytes())
  body=self.compose('library_body_compose',{'shell':self.run('library_body_shell',t),'hero':self.run('library_hero','top',t),'identity':self.run('library_static_section','identity',t),'publish':self.run('library_publish','publish',t),'catalog':self.run('library_catalog_section',t),'status':self.run('library_status','status',t),'compare':self.run('library_static_section','compare',t)})
  doc=self.compose('library_document',{'head':self.run('library_head',t),'body':body},True)
  self.publish(('' if t=='en' else t+'/')+'libraries/index.html',doc)
  print(t,'libraries',len(doc),flush=True)
 def sections(self):
  t=self.tag;prefix='' if t=='en' else t+'/'
  for page in ['blog','legal','sponsor']:
   doc=self.compose('home_document',{'head':self.run(page+'_head',t),'body':self.run(page+'_body',t)},True)
   self.publish(prefix+page+'/index.html',doc)
  for name,path in [('infra','blog/infra-cost-measured-path'),('security','blog/csf2-attack-graphs')]:self.publish(prefix+path+'/index.html',self.run('article_page',name,t))
  if t in ('en','ja'):
   for name,path in [('parity','blog/embench-c-parity-20261015'),('progress','blog/embench-progress-20261012'),('usage','blog/kotoba-murakumo-how-to-use-20261015')]:self.publish(prefix+path+'/index.html',self.run('article_page',name,t))
   self.publish(prefix+'aiueos/index.html',self.compose('home_document',{'head':self.run('aiueos_head',t),'body':self.run('aiueos_body',t)},True))
  if t=='en':self.publish('spec/org-identity/index.html',self.run('article_page','identity','en'))
  print(t,'sections complete',flush=True)

def locale_job(output,tag):
 j=Job(output,tag);j.home();j.library();j.sections();return tag

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['compiler','libraries','grammar','output']:p.add_argument('--'+name,type=Path,required=True)
 p.add_argument('--packager',type=Path);p.add_argument('--linux-inputs',type=Path)
 p.add_argument('--jobs',type=int,default=3);a=p.parse_args()
 if a.jobs<1 or a.jobs>8:raise ValueError('jobs must be 1..8')
 if a.linux_inputs:
  from linux_build import LinuxBuild
  b=LinuxBuild(a.output,a.linux_inputs,a.libraries,a.grammar)
  if b.compiler!=a.compiler.resolve():raise ValueError('Linux compiler argument mismatch')
 else:
  if not a.packager:raise ValueError('macOS profile requires --packager')
  b=Build(a.output,a.compiler,a.packager,a.libraries,a.grammar)
 for name in b.recipes:b.compile(name)
 (b.output/'data.json').write_text(json.dumps({n:str(p) for n,p in b.data.items()},indent=2)+'\n')
 # Catalogue rows are rendered once natively and reused without translation.
 write_bytes(b.output/'rows.html',b.run('library_rows'))
 with concurrent.futures.ProcessPoolExecutor(max_workers=a.jobs) as pool:
  pending={pool.submit(locale_job,str(b.output),t):t for t in TAGS[:a.jobs]}
  rest=iter(TAGS[a.jobs:]);completed=[]
  while pending:
   done,_=concurrent.futures.wait(pending,return_when=concurrent.futures.FIRST_COMPLETED)
   for future in done:
    tag=pending.pop(future)
    try:completed.append(future.result())
    except BaseException:
     print('FAILED LOCALE',tag,flush=True)
     for other in pending:other.cancel()
     raise
    following=next(rest,None)
    if following:pending[pool.submit(locale_job,str(b.output),following)]=following
 if set(completed)!=set(TAGS):raise ValueError('locale set mismatch')
 j=Job(b.output,'extras')
 for tag,path in [('en','start'),('ta','ta'),('uk','uk')]:
  j.publish(path+'/index.html',j.compose('start_document',{'head':j.run('start_card_head',tag),'body':j.run('start_card_body',tag)},True,tag))
 static=json.loads((ROOT/'site/native/build-static-assets.json').read_text())
 for dest,source,pin in static:
  path=(ROOT/source).resolve(strict=True)
  if not path.is_relative_to(ROOT) or sha(path)!=pin:raise ValueError('static input drift '+source)
  target=b.output/'candidate'/dest
  if target.exists():raise ValueError('duplicate static destination '+dest)
  write_bytes(target,path.read_bytes());b.inputs[str(path)]=pin
 for name,kind in [('_headers','headers'),('_redirects','redirects'),('robots.txt','robots')]:
  for dest,src in [('headers','headers.txt'),('generator','generator.cljk'),('play-source','play.kotoba')]:write_bytes(j.folder/dest,(j.folder/src).read_bytes())
  write_bytes(b.output/'candidate'/name,j.run('hosting_metadata',kind))
 write_bytes(b.output/'candidate/.well-known/kotoba-libraries.json',j.run('publication_json'))
 pages=sorted((b.output/'candidate').rglob('*.html'))
 for f in pages:write_bytes(j.folder/'pages'/f.relative_to(b.output/'candidate'),f.read_bytes())
 (j.folder/'manifest.edn').write_text('['+' '.join(json.dumps('/pages/'+str(f.relative_to(b.output/'candidate'))) for f in pages)+']')
 write_bytes(b.output/'candidate/sitemap.xml',j.run('sitemap'))
 b.finish()
 files={str(f.relative_to(b.output/'candidate')):sha(f) for f in sorted((b.output/'candidate').rglob('*')) if f.is_file()}
 if len(files)!=189 or len(pages)!=133:raise ValueError(('release route count',len(files),len(pages)))
 receipt={'status':'BUILT_NATIVE_SITE_CANDIDATE','files':files,'htmlPages':len(pages),'locales':list(TAGS),'compilerSha256':sha(b.compiler),'packagerSha256':sha(b.packager),'inputsSha256':sha(b.output/'inputs.json'),'cljkInvoked':False,'published':False,'profile':getattr(b,'profile','macOS-ARM64')}
 (b.output/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'],len(files),flush=True)
if __name__=='__main__':main()
