#!/usr/bin/env python3
"""Observe a native site candidate; optionally compare an independent reference.

This observer never invokes either renderer. Reference comparison is optional,
explicit, and records the reference hashes; it is not a native-build fallback.
"""
import argparse,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urljoin
from check_footers import Tree,normalize
from build_support import sha
STAMPS={'site/native/'+n+'.kotoba' for n in ['home_body_shell','library_body_shell','article_body','blog_body','legal_body','sponsor_body','start_card_body','aiueos_body']}
def dom(data,url,native=False):
 tree=Tree();tree.feed(data.decode());tree.close()
 if len(tree.stack)!=1:raise ValueError('unclosed document '+url)
 def walk(n):
  if not isinstance(n,list):return
  attrs=dict(n[1])
  if n[0]=='a' and 'hreflang' in attrs and attrs.get('aria-current') in ('true','page'):attrs['aria-current']='page'
  if n[0]=='article' and attrs.get('lang')=='en' and attrs.get('translate')=='no':
   if native and re.search(r'/((ar)|(arz)|(ur))/blog/$',url) and attrs.get('dir')!='ltr':raise ValueError('English island lacks LTR direction')
   attrs.setdefault('dir','ltr')
  n[1]=tuple(sorted((k,urljoin(url,v) if k in ('href','src','poster') and v else v) for k,v in attrs.items()))
  if native and n[0]=='code' and n[2] and len(n[2])==1 and n[2][0] in STAMPS:n[2]=['site/generate.cljk']
  for c in n[2]:walk(c)
  if n[0]=='head':
   metadata=[c for c in n[2] if isinstance(c,list) and (c[0]=='meta' or (c[0]=='link' and dict(c[1]).get('rel')!='stylesheet'))]
   rest=[c for c in n[2] if c not in metadata]
   n[2]=rest+sorted(metadata,key=repr)
 walk(tree.root);return normalize(tree.root)
def differences(a,b,path=''):
 if a==b:return []
 if type(a)!=type(b) or not isinstance(a,tuple):return [(path,str(a)[:500],str(b)[:500])]
 if len(a)!=len(b):return [(path+'#length',len(a),len(b))]
 out=[]
 for i,(x,y) in enumerate(zip(a,b)):out+=differences(x,y,path+'/'+str(i))
 return out


DATED=('embench-c-parity-20261015','embench-progress-20261012','kotoba-murakumo-how-to-use-20261015')
def find(node,tag):
 result=[]
 def visit(n):
  if not isinstance(n,tuple) or len(n)!=3 or not isinstance(n[0],str):return
  if n[0]==tag:result.append(n)
  for c in n[2]:visit(c)
 visit(node);return result

def one(node,tag):
 result=find(node,tag)
 if len(result)!=1:raise ValueError(('expected one',tag,len(result)))
 return result[0]

def rewrite(node,fn):
 if not isinstance(node,tuple) or len(node)!=3 or not isinstance(node[0],str):return node
 return fn((node[0],node[1],tuple(rewrite(c,fn) for c in node[2])))

def correct_japanese_library(data):
 # Exact existing native corrections, already covered by section observers.
 text=data.decode()
 text=text.replace('href="/ja/libraries/">日本語</a>','href="/libraries/">English</a>')
 old='<p class="kot-lead">その 2,215 公開リポジトリ内の <code class="kot-code">kotoba-lang</code> 組織、タグ語彙と共に 組織の GitHub トピック使用。名前、説明、またはでフィルター。 任意のタグの組み合わせ。</p>'
 new='<p class="kot-lead"><code class="kot-code">kotoba-lang</code> の公開リポジトリ 2,215 件を掲載しています。GitHub のトピックと共通のタグを使い、名前・説明・タグの組み合わせで絞り込めます。</p>'
 return text.replace(old,new).encode()

def qualify_identity(actual,port,url,ordinary):
 expected=dom((port/'identity-article.html').read_bytes(),url)
 if one(actual,'article')!=one(expected,'article'):raise ValueError('identity article differs from authored source observation')
 meta=json.loads((port/'identity-metadata.json').read_text())
 if one(actual,'title')[2]!=(meta['title'],):raise ValueError('identity title')
 descriptions=[dict(n[1]).get('content') for n in find(actual,'meta') if dict(n[1]).get('name')=='description']
 if descriptions!=[meta['description']]:raise ValueError('identity description')
 if find(actual,'style')!=find(ordinary,'style') or find(actual,'script')!=find(ordinary,'script'):raise ValueError('identity shared style/scripts')
 header=one(actual,'header');links=[dict(n[1]) for n in find(header,'a') if 'hreflang' in dict(n[1])]
 if [(n['hreflang'],n['href']) for n in links]!=[('en',url)]:raise ValueError('identity unpublished locale link')

def qualify_dated(name,expected,actual,port,url):
 tag='ja' if name.startswith('ja/') else 'en';slug=next(s for s in DATED if '/'+s+'/' in '/'+name)
 observed={kind:one(dom((port/(tag+'-'+slug+'-'+kind+'.html')).read_bytes(),url),kind) for kind in ('header','footer')}
 if tag=='ja':
  def local_aiueos(n):
   if n[0]=='a' and dict(n[1]).get('href')=='https://kotoba-lang.org/aiueos/':
    attrs=dict(n[1]);attrs['href']='https://kotoba-lang.org/ja/aiueos/';return (n[0],tuple(sorted(attrs.items())),n[2])
   return n
  observed['header']=rewrite(observed['header'],local_aiueos)
 for kind,node in observed.items():
  if one(actual,kind)!=node:raise ValueError(('dated chrome does not match source observation',name,kind))
 def adjust(n):
  if n[0] in observed:return observed[n[0]]
  if tag=='ja' and n[0]=='a' and dict(n[1]).get('class')=='kot-skip':return (n[0],n[1],(json.loads((Path(__file__).resolve().parents[2]/'site/i18n/ja.json').read_text())[hashlib.sha256(b'Skip to content').hexdigest()],))
  return n
 expected=rewrite(expected,adjust)
 if tag=='ja':
  guard='\n\npre,code,kbd,samp{direction:ltr;unicode-bidi:isolate}pre{text-align:left}'
  def strip(n):
   if n[0]=='style':
    if len(n[2])!=1 or not n[2][0].endswith(guard):raise ValueError('unexpected native bilingual code guard')
    return (n[0],n[1],(n[2][0][:-len(guard)],))
   return n
  actual=rewrite(actual,strip)
 return expected,actual

def observe(build,reference=None,port_reference=None):
 root=build/'candidate';receipt=json.loads((build/'receipt.json').read_text())
 if sha(build/'inputs.json')!=receipt['inputsSha256']:raise ValueError('input manifest drift')
 if receipt.get('status')!='BUILT_NATIVE_SITE_CANDIDATE' or receipt.get('cljkInvoked') is not False:raise ValueError('not a native build receipt')
 files={str(p.relative_to(root)):sha(p) for p in root.rglob('*') if p.is_file()}
 if files!=receipt['files'] or len(files)!=189:raise ValueError('candidate file/hash drift')
 sitemap=ET.parse(root/'sitemap.xml');canonical_urls=set();json.loads((root/'.well-known/kotoba-libraries.json').read_text())
 report={'status':'PASS_NATIVE_SITE_OBSERVATION','buildReceiptSha256':sha(build/'receipt.json'),'files':files,'reference':{},'differences':{},'generatorStampNormalization':sorted(STAMPS),'published':False,'reviewedPortDifferences':{},'normalizationRules':['URL resolution','explicit native generator stamps','head metadata field order (style/script order retained)','equivalent hreflang aria-current true/page','LTR English source islands']}
 programs={}
 for manifest in sorted(build.glob('*/build.json')):
  record=json.loads(manifest.read_text());image=manifest.parent/record.get('image','entry.macho');lock=manifest.parent/'project.ksl'
  if sha(image)!=record['binarySha256'] or sha(lock)!=record['sourceLockSha256']:raise ValueError('native program drift '+manifest.parent.name)
  programs[manifest.parent.name]={'binarySha256':sha(image),'sourceLockSha256':sha(lock),'buildManifestSha256':sha(manifest)}
  if record.get('profile')=='Linux-x86_64':
   runner=Path(record['runner']['path'])
   if sha(runner)!=record['runner']['sha256']:raise ValueError('Linux runner drift')
   programs[manifest.parent.name].update(image=record['image'],runnerSha256=sha(runner))
 report['nativePrograms']=programs
 if port_reference:
  report['portReference']={str(p.relative_to(port_reference)):sha(p) for p in port_reference.rglob('*') if p.is_file()}
 if reference:
  refs={str(p.relative_to(reference)):sha(p) for p in reference.rglob('*') if p.is_file()}
  if refs.keys()!=files.keys():raise ValueError(('route sets differ',sorted(refs.keys()-files.keys()),sorted(files.keys()-refs.keys())))
  report['reference']=refs
 for name in sorted(files):
  f=root/name;data=f.read_bytes()
  if name.endswith('.html'):
   if name.startswith('anatomy/'):continue # Authored static assets, digest checked by build.
   if not data.startswith(b'<!DOCTYPE html>\n<html '):raise ValueError('document envelope '+name)
   if b'KOTOBA_' in data or b'UNQUALIFIED EVIDENCE FOR TEST' in data or b'UNQUALIFIED LIBRARY STATUS FOR TEST' in data:raise ValueError('fixture/slot '+name)
   path='/' + (name[:-10] if name.endswith('index.html') else name)
   canonical='https://kotoba-lang.org'+path
   matches=re.findall(rb'<link\b[^>]*rel="canonical"[^>]*>',data)
   if len(matches)!=1 or ('href="'+canonical+'"').encode() not in matches[0]:raise ValueError('canonical '+name)
   canonical_urls.add(canonical)
   actual=dom(data,canonical,True)
   if reference:
    raw_expected=(reference/name).read_bytes()
    if name=='ja/libraries/index.html':
     raw_expected=correct_japanese_library(raw_expected)
    expected=dom(raw_expected,canonical)
    if port_reference and name=='spec/org-identity/index.html':
     qualify_identity(actual,port_reference,canonical,dom((reference/'blog/infra-cost-measured-path/index.html').read_bytes(),'https://kotoba-lang.org/blog/infra-cost-measured-path/'))
     report['reviewedPortDifferences'][name]='Legacy CLJK emits an empty body and undefined metadata for this registered route. Native article/title/description match the separately observed authored identity view; CSS and control scripts match the ordinary source article.'
     ds=[]
    elif port_reference and any('/'+slug+'/' in '/'+name for slug in DATED):
     expected,actual=qualify_dated(name,expected,actual,port_reference,canonical)
     report['reviewedPortDifferences'][name]='Metadata ordering/current-link enum normalized; Japanese chrome is checked against explicit catalog-localized source observations and the native LTR code guard is checked exactly.'
     ds=differences(actual,expected)
    else:ds=differences(actual,expected)
    if ds:report['differences'][name]=ds
  elif reference:
   expected=(reference/name).read_bytes()
   if name=='.well-known/kotoba-libraries.json':equal=json.loads(data)==json.loads(expected)
   elif name=='sitemap.xml':
    urls=lambda content:[e.text for e in ET.fromstring(content).iter('{http://www.sitemaps.org/schemas/sitemap/0.9}loc')]
    actual_urls=urls(data);expected_urls=urls(expected)
    if port_reference and expected_urls.count('https://kotoba-lang.org')==1:
     expected_urls=[('https://kotoba-lang.org/spec/org-identity/' if u=='https://kotoba-lang.org' else u) for u in expected_urls]
     report['reviewedPortDifferences'][name]='Replace legacy undefined identity canonical with the authored spec route; all canonical URLs and duplicate counts are checked.'
    equal=sorted(actual_urls)==sorted(expected_urls) and len(actual_urls)==len(set(actual_urls))
   else:equal=data==expected
   if not equal:report['differences'][name]=[('bytes',hashlib.sha256(data).hexdigest(),hashlib.sha256(expected).hexdigest())]
 listed=[e.text for e in sitemap.iter('{http://www.sitemaps.org/schemas/sitemap/0.9}loc')]
 if set(listed)!=canonical_urls or len(listed)!=len(canonical_urls):raise ValueError('sitemap differs from actual page canonicals')
 if report['differences']:report['status']='NATIVE_SITE_DIFFERENCES_REVIEW_REQUIRED'
 (build/'observation.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
 return report

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--reference',type=Path);p.add_argument('--port-reference',type=Path);a=p.parse_args()
 result=observe(a.build.resolve(strict=True),a.reference.resolve(strict=True) if a.reference else None,a.port_reference.resolve(strict=True) if a.port_reference else None)
 print(result['status'],len(result['files']),'files',len(result['differences']),'differences')
 if result['differences']:raise SystemExit(1)
if __name__=='__main__':main()
