#!/usr/bin/env python3
"""Run/bind the existing native locale and page gates to one built candidate."""
import argparse,json,subprocess,sys
from pathlib import Path
from build_support import ROOT,TAGS,COMPILER_SHA,PACKAGER_SHA,sha
from check_pages import ROUTES

def bind(build,pages,catalogs):
 base=json.loads((build/'receipt.json').read_text());p=json.loads((pages/'receipt.json').read_text());c=json.loads((catalogs/'receipt.json').read_text())
 if p['status']!='PASS_NATIVE_EXISTING_PAGE_CHECKS' or c['status']!='PASS_NATIVE_FILE_CATALOG_SUBSET':raise ValueError('native gate did not pass')
 for r in (p,c):
  if (r['compilerSha256'],r['packagerSha256'])!=(COMPILER_SHA,PACKAGER_SHA):raise ValueError('unqualified gate tools')
 for flag in ('unitAndMutationControls','invalidPathsRefused','missingLandmarkRefused'):
  if p.get(flag) is not True:raise ValueError('missing page control '+flag)
 for flag in ('invalidPathRefused','missingKeysRefused','smallSourceRefused'):
  if c.get(flag) is not True:raise ValueError('missing catalog control '+flag)
 if {(x['tag'],x['route']) for x in p['pages']}!={(t,r) for t in TAGS for r in ROUTES} or len(p['pages'])!=119:raise ValueError('native page coverage')
 for x in p['pages']:
  name=('' if x['tag']=='en' else x['tag'])+x['route']+'index.html';name=name.lstrip('/')
  if x['result']!=42 or x['sha256']!=base['files'][name]:raise ValueError('native page/candidate mismatch '+name)
 if {x['tag'] for x in c['catalogs']}!=set(TAGS)-{'en'} or len(c['catalogs'])!=16:raise ValueError('native catalog coverage')
 for x in c['catalogs']:
  if x['result']!=42 or x['sha256']!=sha(ROOT/'site/i18n'/(x['tag']+'.json')):raise ValueError('native catalog/source mismatch')
 if c['sourceSha256']!=sha(ROOT/'site/i18n/en.json'):raise ValueError('native English message source mismatch')
 records=[]
 for folder,receipt,image,lock in [(pages,p,'page-file.macho','page-file.ksl'),(catalogs,c,'catalog.macho','project.ksl')]:
  if sha(folder/image)!=receipt['programSha256'] or sha(folder/lock)!=receipt['sourceLockSha256']:raise ValueError('gate program/lock drift')
  records.append({'directory':str(folder),'receiptSha256':sha(folder/'receipt.json'),'program':image,'programSha256':sha(folder/image),'sourceLock':lock,'sourceLockSha256':sha(folder/lock)})
 source_names=['site/native/locale_pages.kotoba','test/site/native/locale_pages_file.kotoba','test/site/native/locale_pages_test.kotoba','site/native/locale_catalog.kotoba','site/native/locale_placeholders.kotoba','test/site/native/locale_catalog_file.kotoba','site/native/check_pages.py','site/native/check_catalog_files.py','site/native/qualify_site.py']
 sources={n:sha(ROOT/n) for n in source_names}
 sources['site/native/input.kotoba']=sha(ROOT/'site/native/input.kotoba')
 for n in source_names[:6]:
  if sources[n] not in ((pages/'page-file.ksl').read_text()+(pages/'page-unit.ksl').read_text()+(catalogs/'project.ksl').read_text()):raise ValueError('source not in native gate locks '+n)
 result={'status':'PASS_NATIVE_SITE_VALIDATION','buildReceiptSha256':sha(build/'receipt.json'),'pageChecks':119,'catalogChecks':16,'gates':records,'sourceHashes':sources,'published':False}
 (build/'native-validation.json').write_text(json.dumps(result,indent=2)+'\n');return result

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['build','compiler','packager']:p.add_argument('--'+n,type=Path,required=True)
 p.add_argument('--catalogs-dir',type=Path);a=p.parse_args();build=a.build.resolve(strict=True)
 if (sha(a.compiler),sha(a.packager))!=(COMPILER_SHA,PACKAGER_SHA):raise ValueError('unqualified native tools')
 def gate(script,output,extra=()):
  argv=[sys.executable,str(ROOT/'site/native'/script),'--compiler',str(a.compiler.resolve()),'--packager',str(a.packager.resolve()),'--output',str(output),*extra]
  with (build/(script+'.log')).open('wb') as log:r=subprocess.run(argv,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=1800)
  if r.returncode:raise ValueError('native gate failed '+script)
 pages=build/'native-page-check';gate('check_pages.py',pages,['--dist',str(build/'candidate')])
 catalogs=a.catalogs_dir.resolve(strict=True) if a.catalogs_dir else build/'native-catalog-check'
 if not a.catalogs_dir:gate('check_catalog_files.py',catalogs)
 result=bind(build,pages,catalogs);print(result['status'],result['pageChecks'],result['catalogChecks'])
if __name__=='__main__':main()
