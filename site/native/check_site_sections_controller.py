#!/usr/bin/env python3
"""Qualify native macOS staging of blog and legal pages and static assets."""
import argparse
import base64
import hashlib
import json
import os
import platform
from pathlib import Path
import re
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    for n in ('compiler','packager','sha256-source','page-controller-dir','asset-stager-dir','static-controller-dir','article-dir','article-reference-dir','head-dir','body-dir','reference-dir','legal-head-dir','legal-body-dir','legal-reference-dir','sponsor-head-dir','sponsor-body-dir','sponsor-reference-dir','starter-head-dir','starter-body-dir','starter-document-dir','output'):
        ap.add_argument('--'+n,type=Path,required=True)
    a=ap.parse_args()
    if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('requires macOS arm64')
    compiler,packager,source,page,asset,head,body,reference=(p.resolve(strict=True) for p in (a.compiler,a.packager,a.sha256_source,a.page_controller_dir,a.asset_stager_dir,a.head_dir,a.body_dir,a.reference_dir))
    legal_head,legal_body,legal_ref=(p.resolve(strict=True) for p in (a.legal_head_dir,a.legal_body_dir,a.legal_reference_dir))
    sponsor_head,sponsor_body,sponsor_ref=(p.resolve(strict=True) for p in (a.sponsor_head_dir,a.sponsor_body_dir,a.sponsor_reference_dir))
    starter_head,starter_body,starter_document=(p.resolve(strict=True) for p in (a.starter_head_dir,a.starter_body_dir,a.starter_document_dir))
    starter_head_image,starter_body_image,starter_document_image=starter_head/'head.macho',starter_body/'nav.macho',starter_document/'page.macho'
    for d,p in ((starter_head,starter_head_image),(starter_body,starter_body_image),(starter_document,starter_document_image)):
        if json.loads((d/'receipt.json').read_text())['binarySha256']!=sha(p):raise ValueError('starter binary drift')
    starter_ref=json.loads((starter_document/'receipt.json').read_text())
    if starter_ref['status']!='PASS_NATIVE_START_CARD_DOCUMENTS':raise ValueError('unqualified starter documents')
    starter_expected={x['tag']:x['sha256'] for x in starter_ref['observations']}
    if set(starter_expected)!={'en','ta','uk'}:raise ValueError('starter locale coverage mismatch')
    static=a.static_controller_dir.resolve(strict=True)
    article,article_reference=(p.resolve(strict=True) for p in (a.article_dir,a.article_reference_dir))
    deps=ROOT/'site/native/dependencies.json'
    if sha(source)!=json.loads(deps.read_text())['dependencies'][0]['sha256']:raise ValueError('SHA dependency drift')
    for p in (compiler,packager):subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
    page_image,asset_image,head_image,body_image=page/'controller.macho',asset/'stage.macho',head/'head.macho',body/'nav.macho'
    legal_head_image,legal_body_image=legal_head/'head.macho',legal_body/'nav.macho'
    static_image=static/'assets.macho';article_image=article/'base.macho';sponsor_head_image,sponsor_body_image=sponsor_head/'head.macho',sponsor_body/'nav.macho'
    for d,p in ((page,page_image),(asset,asset_image),(head,head_image),(body,body_image),(legal_head,legal_head_image),(legal_body,legal_body_image),(static,static_image),(article,article_image),(sponsor_head,sponsor_head_image),(sponsor_body,sponsor_body_image)):
        if json.loads((d/'receipt.json').read_text())['binarySha256']!=sha(p):raise ValueError('component drift')
    ref=json.loads((reference/'receipt.json').read_text())
    if ref['status']!='PASS_NATIVE_BLOG_DOCUMENTS':raise ValueError('unqualified document reference')
    expected={x['tag']:x['sha256'] for x in ref['observations']}
    if set(expected)!=set(TAGS):raise ValueError('locale coverage mismatch')
    lr=json.loads((legal_ref/'receipt.json').read_text())
    if lr['status']!='PASS_NATIVE_LEGAL_DOCUMENTS':raise ValueError('unqualified legal document reference')
    legal_expected={x['tag']:x['sha256'] for x in lr['observations']}
    if set(legal_expected)!=set(TAGS):raise ValueError('legal locale coverage mismatch')
    sr=json.loads((sponsor_ref/'receipt.json').read_text())
    if sr['status']!='PASS_NATIVE_SPONSOR_DOCUMENTS':raise ValueError('unqualified sponsor document reference')
    sponsor_expected={x['tag']:x['sha256'] for x in sr['observations']}
    if set(sponsor_expected)!=set(TAGS):raise ValueError('sponsor locale coverage mismatch')
    ar=json.loads((article_reference/'receipt.json').read_text())
    article_receipt=json.loads((article/'receipt.json').read_text())
    if ar['status']!='PASS_NATIVE_MULTILINGUAL_ARTICLE_PAGE' or not article_receipt.get('englishIdentitySourceChecked'):raise ValueError('unqualified article references')
    article_expected={(x['route'],x['tag']):x['sha256'] for x in ar['observations']}
    if set(article_expected)!={(route,tag) for route in ('infra','security') for tag in TAGS}:raise ValueError('article locale coverage mismatch')
    assets=ROOT/'site/assets'
    hosting_headers=ROOT/'site/_headers';hosting_generator=ROOT/'site/generate.cljk';hosting_source=assets/'play/double-21.kotoba'
    mappings=[('', 'kotoba-wordmark.png', assets/'kotoba-wordmark.png'), ('', 'favicon.ico', assets/'kotoba-favicon.ico'),
              ('', 'kotoba-favicon.png', assets/'kotoba-favicon.png'), ('', 'kotoba-og-card.png', assets/'kotoba-og-card.png'),
              ('', 'agent-quickstart.md', assets/'agent-quickstart.md'), ('', 'llms.txt', assets/'llms.txt'),
              ('', 'llms-full.txt', assets/'llms-full.txt'), ('.well-known', 'security.txt', assets/'security.txt'),
              ('anatomy/kotoba-murakumo', 'light.html', assets/'anatomy/kotoba-murakumo-light.html'),
                ('anatomy/kotoba-murakumo', 'index.html', assets/'anatomy/kotoba-murakumo.html')]
    mappings += [('play',p.name,p) for p in sorted((assets/'play').iterdir()) if p.is_file()]
    mappings += [('ipfs',p.name,p) for p in sorted((assets/'ipfs').iterdir()) if p.is_file()]
    mappings += [('ipfs','bafkreiaeohkv2zuo2x4yq745euqm5wijrub5xpkxbbw5gre6tdp3sxzonm',assets/'play/double-21.kotoba')]
    mappings += [('', 'dependencies.edn', ROOT/'site/dependencies.edn'), ('', 'sponsorship.edn', ROOT/'site/sponsorship.edn'),
                 ('.well-known', 'kotoba-package-registry.edn', ROOT/'lang/package-registry.edn'),
                 ('.well-known', 'kotoba-cryptographic-boundaries.edn', ROOT/'security/cryptographic-boundaries.edn'),
                 ('security', 'csf2-threat-model.md', ROOT/'docs/security/csf2-threat-model.md'),
                 ('spec', 'org-identity-adr.md', ROOT/'site/spec/org-identity-adr.md'),
                 ('schemas/trust-profile', 'v1', ROOT/'site/vendor/identity/resources/public/schemas/trust-profile/v1.json')]
    for name in ('eas/kotobase-v1.json','erc8004/murakumo-v1.json','erc8004/murakumo-v1.json.signature.json','human-passport/itonami-v1.json'):
        p=ROOT/'site/vendor/identity/resources/public/policies/trust'/name
        mappings.append(('policies/trust/'+str(Path(name).parent),p.name,p))
    for name in ('embench-c-parity-20261015.json','embench-candidate-review-20261013.json','embench-progress-20261012.json',
                 'embench-resume-20261012.json','embench-resume-20261013.json','embench-work-status-20261013.json'):
        mappings.append(('benchmarks',name,ROOT/'site/benchmarks'/name))
    mappings += [('benchmarks',name,ROOT/'bench'/directory/'latest.json') for name,directory in (
        ('build-scaling-latest.json','public-build-scaling'),('compile-wasm-latest.json','public-compile-comparison'),
        ('domains-latest.json','public-domain-comparison'),('end-to-end-latest.json','public-end-to-end-comparison'),
        ('runtime-native-latest.json','public-runtime-comparison'))]
    anatomy_refs=[ROOT/'site/dist/anatomy/kotoba-murakumo'/n for n in ('index.html','light.html')]
    # Historical generated pages independently fix these two public routes.
    for name,src in (('index.html',assets/'anatomy/kotoba-murakumo.html'),('light.html',assets/'anatomy/kotoba-murakumo-light.html')):
        ref=ROOT/'site/dist/anatomy/kotoba-murakumo'/name
        if sha(src)!=sha(ref):raise ValueError(('anatomy reference drift',name))
    if len({(g,n) for g,n,_ in mappings})!=len(mappings):raise ValueError('duplicate asset destination')
    modules=[(ROOT/'site/native/site_sections_controller.kotoba','src/site/native/site_sections_controller.kotoba'),(ROOT/'test/site/native/site_sections_controller_file.kotoba','src/site/native/site_sections_controller_file.kotoba'),(ROOT/'site/native/hosting_metadata.kotoba','src/site/native/hosting_metadata.kotoba'),(ROOT/'site/native/article_stage.kotoba','src/site/native/article_stage.kotoba'),(ROOT/'site/native/start_card_stage.kotoba','src/site/native/start_card_stage.kotoba'),(source,'src/multiformats/sha256.kotoba')]
    tracked=[compiler,packager,deps,Path(__file__).resolve(),*[p for p,_ in modules],page/'receipt.json',page_image,asset/'receipt.json',asset_image,head/'receipt.json',head_image,body/'receipt.json',body_image,reference/'receipt.json',legal_head/'receipt.json',legal_head_image,legal_body/'receipt.json',legal_body_image,legal_ref/'receipt.json',static/'receipt.json',static_image,hosting_headers,hosting_generator,hosting_source,*[p for _,_,p in mappings],*anatomy_refs]
    tracked += [sponsor_head/'receipt.json',sponsor_head_image,sponsor_body/'receipt.json',sponsor_body_image,sponsor_ref/'receipt.json',article_reference/'receipt.json',*[p for p in article.rglob('*') if p.is_file()]]
    tracked += [starter_head/'receipt.json',starter_head_image,starter_body/'receipt.json',starter_body_image,starter_document/'receipt.json',starter_document_image,*[p for d in (starter_head,starter_body) for p in d.rglob('*') if p.is_file()]]
    pins={str(p):sha(p) for p in tracked};out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    for p,n in modules:
        q=out/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
    lock=out/'project.ksl';lock.write_text('KSL1\nentry /src/site/native/site_sections_controller_file.kotoba\nroot /src\n'+''.join('file '+sha(out/n)+' /'+n+'\n' for _,n in sorted(modules,key=lambda x:x[1])))
    def build(image,*args):
        fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
        if fd!=3:os.dup2(fd,3)
        try:r=subprocess.run([str(image),*map(str,args)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=300)
        finally:
            if fd!=3:os.close(fd)
        if r.returncode or r.stderr:raise RuntimeError((image.name,args,r.returncode,r.stderr[-500:]))
        return r.stdout
    build(compiler,'compile','/src/site/native/site_sections_controller_file.kotoba','--target','aarch64-macos','--output','site.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock))
    info=build(compiler,'extract-native','site.kseed','--symbol','main','--output','site.raw');match=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
    if not match or int(match[2])!=(out/'site.raw').stat().st_size:raise ValueError('invalid native entry')
    build(packager,'site-unsigned.macho','site.raw','amu-cli-host',match[1].decode())
    image=out/'site.macho';shutil.copyfile(out/'site-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    def manifest(path,values):path.write_text('{'+ ' '.join(':'+k+' '+json.dumps(v,ensure_ascii=False) for k,v in values.items())+'}')
    rows=[{'group':g,'name':n,'source':str(p.resolve()),'sha256':sha(p)} for g,n,p in mappings]
    entries=' '.join('{'+ ' '.join(':'+k+' '+json.dumps(v,ensure_ascii=False) for k,v in row.items())+'}' for row in rows)
    static_manifest=out/'static-assets.edn'
    static_manifest.write_text('{:stager '+json.dumps(str(asset_image))+' :stager-sha256 '+json.dumps(sha(asset_image))+' :assets ['+entries+']}')
    data={'page':str(page_image),'page-sha':sha(page_image),'asset':str(asset_image),'asset-sha':sha(asset_image),'head-dir':str(head),'head':str(head_image),'head-sha':sha(head_image),'body-dir':str(body),'body':str(body_image),'body-sha':sha(body_image),'static-controller':str(static_image),'static-controller-sha':sha(static_image),'static-manifest':str(static_manifest),'static-manifest-sha':sha(static_manifest),'hosting-headers':str(hosting_headers),'hosting-headers-sha':sha(hosting_headers),'hosting-generator':str(hosting_generator),'hosting-generator-sha':sha(hosting_generator),'hosting-source':str(hosting_source),'hosting-source-sha':sha(hosting_source),'article-dir':str(article),'article':str(article_image),'article-sha':sha(article_image),'legal-head-dir':str(legal_head),'legal-head':str(legal_head_image),'legal-head-sha':sha(legal_head_image),'legal-body-dir':str(legal_body),'legal-body':str(legal_body_image),'legal-body-sha':sha(legal_body_image),'sponsor-head-dir':str(sponsor_head),'sponsor-head':str(sponsor_head_image),'sponsor-head-sha':sha(sponsor_head_image),'sponsor-body-dir':str(sponsor_body),'sponsor-body':str(sponsor_body_image),'sponsor-body-sha':sha(sponsor_body_image)}
    data.update({'starter-head-dir':str(starter_head),'starter-head':str(starter_head_image),'starter-head-sha':sha(starter_head_image),'starter-body-dir':str(starter_body),'starter-body':str(starter_body_image),'starter-body-sha':sha(starter_body_image),'starter-document':str(starter_document_image),'starter-document-sha':sha(starter_document_image)})
    def run(m,private,site,code):
        r=subprocess.run([str(image),str(m),str(private),str(site)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},capture_output=True,timeout=3000)
        if r.returncode!=code or r.stdout or r.stderr:raise RuntimeError((r.returncode,r.stdout[:100],r.stderr[-400:]))
    bad=out/'bad.edn';wrong=dict(data);wrong['legal-body-sha']='0'*64;manifest(bad,wrong)
    private=out/'private';site=out/'site';private.mkdir();site.mkdir()
    run(bad,private,site,7)
    if list(private.iterdir()) or list(site.iterdir()):raise ValueError('invalid manifest created output')
    bad_host=out/'bad-host.edn';wrong=dict(data);wrong['hosting-headers-sha']='0'*64;manifest(bad_host,wrong);run(bad_host,private,site,7)
    if list(private.iterdir()) or list(site.iterdir()):raise ValueError('invalid hosting input created output')
    bad_article=out/'bad-article.edn';wrong=dict(data);wrong['article-sha']='0'*64;manifest(bad_article,wrong);run(bad_article,private,site,7)
    if list(private.iterdir()) or list(site.iterdir()):raise ValueError('invalid article input created output')
    bad_sponsor=out/'bad-sponsor.edn';wrong=dict(data);wrong['sponsor-body-sha']='0'*64;manifest(bad_sponsor,wrong);run(bad_sponsor,private,site,7)
    if list(private.iterdir()) or list(site.iterdir()):raise ValueError('invalid sponsor input created output')
    bad_starter=out/'bad-starter.edn';wrong=dict(data);wrong['starter-body-sha']='0'*64;manifest(bad_starter,wrong);run(bad_starter,private,site,7)
    if list(private.iterdir()) or list(site.iterdir()):raise ValueError('invalid starter input created output')
    good=out/'manifest.edn';manifest(good,data);run(good,private,site,42)
    wanted={('' if tag=='en' else tag+'/')+route+'/index.html' for tag in TAGS for route in ('blog','legal','sponsor')};wanted.update(str(Path(g)/n) for g,n,_ in mappings);wanted.update({'_headers','_redirects','robots.txt','spec/org-identity/index.html'});wanted.update(('' if tag=='en' else tag+'/')+'blog/'+slug+'/index.html' for tag in TAGS for slug in ('infra-cost-measured-path','csf2-attack-graphs'))
    wanted.update({'start/index.html','ta/index.html','uk/index.html'})
    actual={str(p.relative_to(site)) for p in site.rglob('*') if p.is_file()}
    if actual!=wanted:raise ValueError(('unexpected site files',actual))
    for tag in TAGS:
        if sha(site/('' if tag=='en' else tag)/'blog/index.html')!=expected[tag]:raise ValueError(('blog mismatch',tag))
        if sha(site/('' if tag=='en' else tag)/'legal/index.html')!=legal_expected[tag]:raise ValueError(('legal mismatch',tag))
        if sha(site/('' if tag=='en' else tag)/'sponsor/index.html')!=sponsor_expected[tag]:raise ValueError(('sponsor mismatch',tag))
    for tag in ('en','ta','uk'):
        if sha(site/('start' if tag=='en' else tag)/'index.html')!=starter_expected[tag]:raise ValueError(('starter mismatch',tag))
    for group,name,src in mappings:
        if sha(src)!=sha(site/group/name):raise ValueError(('asset mismatch',group,name))
    for tag in TAGS:
        for route,slug in (('infra','infra-cost-measured-path'),('security','csf2-attack-graphs')):
            path=site/('' if tag=='en' else tag)/'blog'/slug/'index.html'
            if sha(path)!=article_expected[(route,tag)] or b'freebuff' in path.read_bytes().lower():raise ValueError(('article mismatch',route,tag))
    identity=site/'spec/org-identity/index.html'
    if sha(identity)!='1eddf303567a0ea780ab1938cf06598c19df9e08b4d2565e04925f06eaf83dcf' or b'freebuff' in identity.read_bytes().lower():raise ValueError('identity mismatch')
    digest=hashlib.sha256(hosting_source.read_bytes()).digest();cid='b'+base64.b32encode(b'\x01\x55\x12\x20'+digest).decode().lower().rstrip('=')
    hosting_expected={'_headers':hosting_headers.read_bytes()+('\n/ipfs/'+cid+'\n  Content-Type: text/plain; charset=utf-8\n  Content-Disposition: inline; filename=hello.kotoba\n').encode(),
                      '_redirects':b'/zh /zh-Hans/ 301\n/zh/* /zh-Hans/:splat 301\n',
                      'robots.txt':b'User-agent: *\nAllow: /\n\nSitemap: https://kotoba-lang.org/sitemap.xml\n'}
    for name,value in hosting_expected.items():
        if (site/name).read_bytes()!=value or b'freebuf' in value.lower():raise ValueError(('hosting metadata mismatch',name))
    before={str(p):sha(p) for p in site.rglob('*') if p.is_file()};run(good,private,site,7)
    if any(sha(Path(p))!=h for p,h in before.items()):raise ValueError('existing output modified')
    if any(sha(Path(p))!=h for p,h in pins.items()):raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_SITE_SECTIONS_CONTROLLER_MACOS','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'locales':list(TAGS),'badDigestRefused':True,'existingStagePreserved':True,'nativeOrchestration':True,'privateImmutableStageRequired':True,'sections':['blog','legal','articles','identity','sponsor','starters'],'articleCount':35,'staticAssetCount':len(mappings),'hostingMetadata':list(hosting_expected),'completeSiteGenerated':False,'linuxQualified':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'])

if __name__=='__main__':main()
