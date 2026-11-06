"""Stage Markdown bytes and observe native Docs generation, never render in Python."""
import argparse,concurrent.futures,json,shutil
from pathlib import Path
from build_support import Build,ROOT,native_run,sha,write_bytes,program_image

SLUGS=('', '01-first-program','02-forms','03-reader-and-names','04-checking-and-ir','05-ipld','06-definition-identity','07-effects-and-authority','08-code-generation','09-kototama','10-runtime-performance','11-build-performance','12-reading-and-experiments','examples')
def source_name(tag,slug):
 stem='README' if not slug else ('examples/README' if slug=='examples' else slug)
 return stem+('.ja' if tag=='ja' else '')+'.md'
def route(tag,slug):return ('ja/' if tag=='ja' else '')+'docs/amu/'+(slug+'/' if slug else '')
def stage(folder,tag):
 pins=[]
 for slug in SLUGS:
  name=source_name(tag,slug);p=ROOT/'docs/learn/amu'/name
  write_bytes(folder/'docs-source'/name,p.read_bytes());pins.append(sha(p))
 (folder/'docs-pins.edn').write_text('['+' '.join(json.dumps(x) for x in pins)+']')
def render_job(build,tag,group):
 build=Path(build);folder=build/'docs-jobs'/('render-'+tag+'-'+str(group));folder.mkdir(parents=True)
 stage(folder,tag)
 for index,slug in enumerate(SLUGS):
  if index%3!=group:continue
  dest=route(tag,slug)+'index.html'
  write_bytes(build/'candidate'/dest,native_run(program_image(build/'docs_page'),folder,['render',tag,slug]))
  print('Docs',tag,slug or 'index',flush=True)
def integrate_job(build,names,group):
 build=Path(build);folder=build/'docs-jobs'/('integrate-'+str(group));folder.mkdir(parents=True)
 for index,name in enumerate(names):
  if index%3!=group:continue
  original=(build/'candidate'/name).read_bytes()
  write_bytes(build/'docs-base-pages'/name,original)
  write_bytes(folder/'page.html',original)
  write_bytes(build/'candidate'/name,native_run(program_image(build/'docs_page'),folder,['integrate','ja' if name.startswith('ja/') else 'en','']))
def extend(build,compiler,packager,libraries,grammar,linux_inputs=None):
 build=Path(build).resolve(strict=True);base=json.loads((build/'receipt.json').read_text());candidate=build/'candidate'
 actual={str(p.relative_to(candidate)):sha(p) for p in candidate.rglob('*') if p.is_file()}
 if base['files']!=actual or len(actual)!=189 or base['cljkInvoked'] is not False:raise ValueError('expected an intact native base candidate')
 inputs=json.loads((build/'inputs.json').read_text())
 if sha(build/'inputs.json')!=base['inputsSha256'] or any(sha(path)!=pin for path,pin in inputs.items()):raise ValueError('base input drift')
 if linux_inputs:
  from linux_build import LinuxBuild
  b=LinuxBuild(build/'docs-build',linux_inputs,libraries,grammar)
 else:b=Build(build/'docs-build',compiler,packager,libraries,grammar)
 built=b.compile('docs_page')
 program=build/'docs_page'
 if program.exists():
  if sha(program_image(program))!=sha(program_image(built)) or sha(program/'project.ksl')!=sha(built/'project.ksl'):raise ValueError('existing Docs program differs')
 else:shutil.copytree(built,program)
 examples=tuple(p for p in sorted((ROOT/'docs/learn/amu/examples').iterdir()) if p.suffix in ('.kotoba','.py','.json'))
 # Bind source bytes before workers read them; finish rejects mid-build edits.
 for p in (*tuple((ROOT/'docs/learn/amu').rglob('*.md')),*examples,ROOT/'site/docs-course.css',ROOT/'site/native/docs_build.py',ROOT/'site/native/check_docs.py',ROOT/'site/native/build-components.json'):
  b.inputs[str(p)]=sha(p)
 pages=[route(tag,slug)+'index.html' for tag in ('en','ja') for slug in SLUGS]
 extra=list(pages)
 # Each native worker receives its own scoped files and process log. Python
 # dispatches independent work; native programs alone render and rewrite HTML.
 with concurrent.futures.ProcessPoolExecutor(max_workers=3) as pool:
  futures=[pool.submit(render_job,str(build),tag,group) for tag in ('en','ja') for group in range(3)]
  for future in futures:future.result()
  names=sorted(n for n in base['files'] if n.endswith('.html'))
  futures=[pool.submit(integrate_job,str(build),names,group) for group in range(3)]
  for future in futures:future.result()
 for p in examples:
  dest='docs/amu/examples/'+p.name;write_bytes(candidate/dest,p.read_bytes());extra.append(dest)
 dest='docs/amu/course.css';write_bytes(candidate/dest,(ROOT/'site/docs-course.css').read_bytes());extra.append(dest)
 write_bytes(program/'page.html',(candidate/'_redirects').read_bytes())
 write_bytes(candidate/'_redirects',native_run(program_image(program),program,['redirects','en','']))
 # Re-run the existing native sitemap over the complete final page set.
 folder=build/'docs-sitemap';folder.mkdir()
 html=sorted(candidate.rglob('*.html'))
 for p in html:write_bytes(folder/'pages'/p.relative_to(candidate),p.read_bytes())
 (folder/'manifest.edn').write_text('['+' '.join(json.dumps('/pages/'+str(p.relative_to(candidate))) for p in html)+']')
 write_bytes(candidate/'sitemap.xml',native_run(program_image(build/'sitemap'),folder))
 b.finish()
 for path,pin in b.inputs.items():
  if path in inputs and inputs[path]!=pin:raise ValueError('shared base/Docs input drift '+path)
  inputs[path]=pin
 (build/'inputs.json').write_text(json.dumps(inputs,indent=2)+'\n')
 final={str(p.relative_to(candidate)):sha(p) for p in sorted(candidate.rglob('*')) if p.is_file()}
 base.update(files=final,htmlPages=len(html),docsFiles=extra,docsPages=pages,docsLanguages=['en','ja'],inputsSha256=sha(build/'inputs.json'),baseFilesBeforeDocs=actual)
 (build/'receipt.json').write_text(json.dumps(base,indent=2)+'\n')
 print('BUILT_NATIVE_DOCS',len(pages),'pages;',len(final),'total files',flush=True)
 return program

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for key in ('build','compiler','libraries','grammar'):p.add_argument('--'+key,type=Path,required=True)
 p.add_argument('--packager',type=Path);p.add_argument('--linux-inputs',type=Path)
 a=p.parse_args()
 if not a.linux_inputs and not a.packager:p.error('--packager is required for the macOS profile')
 extend(a.build,a.compiler,a.packager,a.libraries,a.grammar,a.linux_inputs)
if __name__=='__main__':main()
