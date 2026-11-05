#!/usr/bin/env python3
"""Observe real native refusals, including rejection of partially emitted HTML."""
import argparse,json
from pathlib import Path
from build_support import native_run,sha,write_bytes
from build_site import Job

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);a=p.parse_args();build=a.build.resolve(strict=True)
 candidate=build/'candidate';before={str(p.relative_to(candidate)):sha(p) for p in candidate.rglob('*') if p.is_file()}
 j=Job(build,'negative-controls');folder=j.folder;obs=[]
 def refuse(name,args,filename=None,replacement=None,partial=False):
  target=folder/filename if filename else None;old=target.read_bytes() if target else None
  try:
   if target:target.write_bytes(replacement if replacement is not None else old+b' ')
   data=native_run(build/name/'entry.macho',folder,args,expected=7)
   if bool(data)!=partial:raise ValueError(('unexpected refusal output',name,len(data),partial))
   obs.append({'producer':name,'argv':args,'modified':filename,'exit':7,'partialStdoutBytes':len(data),'candidateChanged':False})
  finally:
   if target:target.write_bytes(old)
 refuse('home_command',['hero','en'],'generator.cljk')
 refuse('home_command',['hero','../ja'])
 refuse('aiueos_body',['en'],'aiueos-release.edn')
 refuse('aiueos_body',['fr'])
 refuse('publication_json',[],'library-publication.edn')
 # The native article head is emitted before an invalid section is refused.
 # Job.run must reject that stdout before any publishable fragment is staged.
 bad=b'{:title "Invalid section control" :description "Native partial output refusal" :sections ["invalid"]}'
 path=folder/'site/security-article.edn';old=path.read_bytes();failed=False
 try:
  path.write_bytes(bad)
  try:j.run('article_page','security','en')
  except RuntimeError:failed=True
  if not failed or not (folder/'failed.stdout').read_bytes().startswith(b'<!DOCTYPE html>'):raise ValueError('streaming refusal was not exercised')
  if (build/'fragments/negative-controls/article_page-security-en.html').exists():raise ValueError('failed fragment was retained as success')
  obs.append({'producer':'article_page','argv':['security','en'],'modified':'site/security-article.edn','partialStdoutBytes':(folder/'failed.stdout').stat().st_size,'failedFragmentPublished':False})
 finally:path.write_bytes(old)
 after={str(p.relative_to(candidate)):sha(p) for p in candidate.rglob('*') if p.is_file()}
 if before!=after:raise ValueError('negative controls changed candidate')
 result={'status':'PASS_NATIVE_RELEASE_REFUSALS','observations':obs,'candidateUnchanged':True,'buildReceiptSha256':sha(build/'receipt.json')}
 (build/'negative-controls.json').write_text(json.dumps(result,indent=2)+'\n');print(result['status'])
if __name__=='__main__':main()
