"""Observe native Docs output and real refusal controls; never render Markdown."""
import argparse,hashlib,json,re,shutil
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit
from build_support import ROOT,native_run,program_image,sha,write_bytes
from docs_build import SLUGS,source_name,route,stage

class Page(HTMLParser):
 def __init__(self):
  super().__init__(convert_charrefs=True);self.stack=[];self.main=False;self.pre=False;self.maintext=[];self.h1=[];self.h1open=False;self.codes=[];self.code=[];self.links=[];self.meta=[];self.lang='';self.scripts=[]
 def handle_starttag(self,tag,attrs):
  a=dict(attrs)
  if tag=='html':self.lang=a.get('lang','')
  if tag=='main':self.main=True
  if tag=='h1':self.h1open=True
  if tag=='pre':self.pre=True;self.code=[]
  if tag=='a':self.links.append(a)
  if tag=='link':self.meta.append(a)
  if tag=='script':self.scripts.append(a)
 def handle_endtag(self,tag):
  if tag=='main':self.main=False
  if tag=='h1':self.h1open=False
  if tag=='pre':self.pre=False;self.codes.append(''.join(self.code))
 def handle_data(self,data):
  if self.main:self.maintext.append(data)
  if self.h1open:self.h1.append(data)
  if self.pre:self.code.append(data)
class Tokens(HTMLParser):
 """Observe existing-page structure/content; never serialize or stage HTML."""
 def __init__(self):super().__init__(convert_charrefs=True);self.events=[]
 def handle_starttag(self,tag,attrs):self.events.append(('start',tag,attrs))
 def handle_endtag(self,tag):self.events.append(('end',tag))
 def handle_data(self,data):self.events.append(('data',data))
 def handle_comment(self,data):self.events.append(('comment',data))
 def handle_decl(self,data):self.events.append(('decl',data))
def observe_integration(build,receipt):
 old_links={'#docs','/#docs','https://github.com/kotoba-lang/kotoba-lang/tree/main/docs'}
 from build_support import TAGS
 old_links.update('/'+t+'/#docs' for t in TAGS if t!='en')
 observed=0
 for name,pin in receipt['baseFilesBeforeDocs'].items():
  if not name.endswith('.html'):continue
  original=build/'docs-base-pages'/name
  if sha(original)!=pin:raise ValueError('base page drift '+name)
  before=Tokens();before.feed(original.read_text());before.close()
  after=Tokens();after.feed((build/'candidate'/name).read_text());after.close()
  if len(before.events)!=len(after.events):raise ValueError('site structure changed '+name)
  dest='/'+route('ja' if name.startswith('ja/') else 'en','')
  for a,b in zip(before.events,after.events):
   if a==b:continue
   if a[0]!='start' or b[0]!='start' or a[1]!=b[1]:raise ValueError('site content changed '+name)
   expected=[(k,dest if k=='href' and v in old_links else v) for k,v in a[2]]
   if b[2]!=expected:raise ValueError('unexpected site attribute change '+name)
  observed+=1
 return observed
def compact(s):return re.sub(r'\s+','',s)
def expected_lines(md):
 fence=False;fence_kind=''
 for line in md.splitlines():
  if line.startswith('```'):
   fence=not fence;fence_kind=line[3:];continue
  if fence or not line.strip() or line.startswith('**English** |') or line.startswith('[English](') or line.startswith('|---'):continue
  line=re.sub(r'^#{1,2} ','',line)
  line=re.sub(r'^(?:- |\d+\. )','',line)
  line=re.sub(r'\[([^\]]+)\]\([^)]+\)',r'\1',line)
  line=line.replace('**','').replace('`','').replace('|','')
  if compact(line):yield compact(line)
def observe_pages(build):
 candidate=build/'candidate';receipt=json.loads((build/'receipt.json').read_text())
 expected=[route(t,s)+'index.html' for t in ('en','ja') for s in SLUGS]
 if receipt.get('docsPages')!=expected or len(receipt.get('docsFiles',[]))!=40:raise ValueError('Docs route set mismatch')
 for tag in ('en','ja'):
  for slug in SLUGS:
   name=route(tag,slug)+'index.html';source=ROOT/'docs/learn/amu'/source_name(tag,slug)
   md=source.read_text();p=Page();html=(candidate/name).read_text();p.feed(html);p.close()
   if p.lang!=tag or ''.join(p.h1)!=md.splitlines()[0][2:] or html.count('<h1>')!=1:raise ValueError('Docs heading/lang '+name)
   canonical='https://kotoba-lang.org/'+route(tag,slug)
   if [x.get('href') for x in p.meta if x.get('rel')=='canonical']!=[canonical]:raise ValueError('Docs canonical '+name)
   for lang in ('en','ja','x-default'):
    href='https://kotoba-lang.org/'+route('en' if lang=='x-default' else lang,slug)
    if not any(x.get('rel')=='alternate' and x.get('hreflang')==lang and x.get('href')==href for x in p.meta):raise ValueError('Docs alternate '+name)
   for lang in ('en','ja'):
    if not any(x.get('hreflang')==lang and x.get('href')=='/'+route(lang,slug) for x in p.links):raise ValueError('Docs language switch '+name)
   if p.scripts:raise ValueError('unexpected Docs script')
   visible=compact(''.join(p.maintext))
   for line in expected_lines(md):
    if line not in visible:raise ValueError(('lost Markdown content',name,line[:100]))
   blocks=[body for kind,body in re.findall(r'```([^\n]+)\n(.*?)```',md,re.S) if kind!='mermaid']
   if blocks!=p.codes:raise ValueError('Docs code bytes '+name)
   graphs=re.findall(r'```mermaid\n(.*?)```',md,re.S)
   if html.count('class="arrow"')!=sum(g.count('-->') for g in graphs) or html.count('class="arrow dotted"')!=sum(g.count('-.->') for g in graphs):raise ValueError('Docs diagram edges '+name)
   for graph in graphs:
    for label in re.findall(r'\["?([^\]\n]+?)"?\]',graph):
     if compact(label.strip('"')) not in compact(html):raise ValueError('Docs diagram label '+name)
   for link in p.links:
    target=link.get('href','')
    if target.startswith('/'):
     path=urlsplit(target).path;dest=candidate/path.lstrip('/')
     if path.endswith('/'):dest=dest/'index.html'
     if path!='/' and not dest.is_file():raise ValueError(('Docs broken route',name,target))
   index=SLUGS.index(slug)
   for rel,step in [('prev',-1),('next',1)]:
    nxt=index+step
    if 0<=nxt<len(SLUGS) and not any(x.get('rel')==rel and x.get('href')=='/'+route(tag,SLUGS[nxt]) for x in p.links):raise ValueError('Docs pager '+name)
 # The site's actual entry links must now lead to same-origin Docs.
 for tag in ('','ja/'):
  p=Page();p.feed((candidate/tag/'index.html').read_text())
  if not any(x.get('href')=='/'+route('ja' if tag else 'en','') for x in p.links):raise ValueError('home Docs entry missing')
 integrated=observe_integration(build,receipt)
 return {'existingPagesPreserved':integrated,'pages':len(expected),'languagePairs':14,'markdownContentPreserved':True,'executableCodeBytesPreserved':True,'sameOriginNavigation':True}
def controls(build):
 folder=build/'docs-controls';shutil.copytree(build/'docs_page',folder)
 image=program_image(folder);results=[]
 def run(name,args,expected,check=None):
  raw=native_run(image,folder,args,expected=expected)
  passed=(not raw if expected==7 else bool(check(raw)))
  if not passed:raise ValueError('Docs control failed '+name)
  results.append({'name':name,'exit':expected,'stdoutSha256':hashlib.sha256(raw).hexdigest(),'passed':True})
 stage(folder,'en')
 run('unknown-locale',['render','fr',''],7)
 run('unknown-chapter',['render','en','missing'],7)
 path=folder/'docs-source/README.md';original=path.read_bytes();pins=folder/'docs-pins.edn';original_pins=pins.read_text()
 path.write_bytes(original+b'changed')
 run('source-hash-mismatch',['render','en',''],7)
 def mutate(text):
  path.write_text(text)
  values=json.loads(original_pins.replace('" "','","'))
  values[0]=sha(path);pins.write_text('['+' '.join(json.dumps(x) for x in values)+']')
 mutate('# Test\n\n[unsafe](javascript:alert)\n')
 run('unsafe-link-refused',['render','en',''],7)
 mutate('# Test\n\n```clojure\n(+ 1 2)\n')
 run('unclosed-code-fence',['render','en',''],7)
 mutate('# Test\n\n```mermaid\nnot a graph\n```\n')
 run('unsupported-diagram',['render','en',''],7)
 mutate('# 日本語🙂\n\n6本の例で文字の境界を保持する。\n')
 run('utf8-boundaries-preserved',['render','en',''],42,lambda b:'<h1>日本語🙂</h1>'.encode() in b and '6本の例で文字の境界を保持する。'.encode() in b)
 mutate('# Test\n\n<script>alert(1)</script>\n')
 run('raw-html-is-text',['render','en',''],42,lambda b:b'&lt;script&gt;alert(1)&lt;/script&gt;' in b and b'<script>' not in b)
 mutate('# Test\n\n'+('x'*65536))
 run('source-size-bound',['render','en',''],7)
 stage(folder,'en')
 return results

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);a=p.parse_args();build=a.build.resolve(strict=True)
 before={str(p.relative_to(build/'candidate')):sha(p) for p in (build/'candidate').rglob('*') if p.is_file()}
 observed=observe_pages(build);cases=controls(build)
 after={str(p.relative_to(build/'candidate')):sha(p) for p in (build/'candidate').rglob('*') if p.is_file()}
 if before!=after:raise ValueError('Docs controls changed candidate')
 result={'status':'PASS_NATIVE_DOCS','buildReceiptSha256':sha(build/'receipt.json'),'pages':json.loads((build/'receipt.json').read_text())['docsPages'],'observation':observed,'controls':cases,'candidateUnchanged':True,'rendererManifestSha256':sha(build/'docs_page/build.json')}
 (build/'docs-validation.json').write_text(json.dumps(result,indent=2)+'\n');print(result['status'],observed)
if __name__=='__main__':main()
