#!/usr/bin/env python3
"""Upload an observed native candidate. This never builds or invokes CLJK.

Wrangler is only the static asset transport. Rendering and packaging of native
site programs have already completed before this separate control-plane step.
"""
import argparse,hashlib,json,os,subprocess
from pathlib import Path
from build_support import ROOT,sha

def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--wrangler',type=Path,required=True);p.add_argument('--dry-run',action='store_true');a=p.parse_args()
 build=a.build.resolve(strict=True);candidate=build/'candidate';receipt=json.loads((build/'receipt.json').read_text());observation=json.loads((build/'observation.json').read_text());negative=json.loads((build/'negative-controls.json').read_text())
 files={str(p.relative_to(candidate)):sha(p) for p in candidate.rglob('*') if p.is_file()}
 if receipt.get('status')!='BUILT_NATIVE_SITE_CANDIDATE' or receipt.get('cljkInvoked') is not False or files!=receipt['files']:raise ValueError('native candidate drift')
 if observation.get('status')!='PASS_NATIVE_SITE_OBSERVATION' or observation.get('buildReceiptSha256')!=sha(build/'receipt.json') or observation.get('files')!=files:raise ValueError('candidate lacks successful observation')
 if negative.get('status')!='PASS_NATIVE_RELEASE_REFUSALS' or negative.get('candidateUnchanged') is not True or negative.get('buildReceiptSha256')!=sha(build/'receipt.json'):raise ValueError('candidate lacks refusal observations')
 if sha(build/'inputs.json')!=receipt['inputsSha256']:raise ValueError('input manifest drift')
 for name,record in observation['nativePrograms'].items():
  if sha(build/name/'entry.macho')!=record['binarySha256'] or sha(build/name/'project.ksl')!=record['sourceLockSha256'] or sha(build/name/'build.json')!=record['buildManifestSha256']:raise ValueError('native program changed '+name)
 qualification=json.loads((build/'native-validation.json').read_text())
 if qualification.get('status')!='PASS_NATIVE_SITE_VALIDATION' or qualification.get('buildReceiptSha256')!=sha(build/'receipt.json'):raise ValueError('candidate lacks native locale/page qualification')
 for gate in qualification['gates']:
  folder=Path(gate['directory'])
  if sha(folder/'receipt.json')!=gate['receiptSha256'] or sha(folder/gate['program'])!=gate['programSha256'] or sha(folder/gate['sourceLock'])!=gate['sourceLockSha256']:raise ValueError('native gate drift')
 for name,pin in qualification['sourceHashes'].items():
  if sha(ROOT/name)!=pin:raise ValueError('native gate source changed '+name)
 inputs=json.loads((build/'inputs.json').read_text())
 for path,pin in inputs.items():
  if sha(path)!=pin:raise ValueError('input changed '+path)
 origin=git('remote','get-url','origin').decode().strip()
 if origin not in ['git@github.com:kotoba-lang/kotoba-lang.git','https://github.com/kotoba-lang/kotoba-lang.git']:raise ValueError('wrong repository')
 for name,pin in qualification['sourceHashes'].items():inputs[str(ROOT/name)]=pin
 # A dry run may inspect the review branch. Publication requires all staged
 # repository inputs to agree with freshly fetched main, including squash merges.
 if not a.dry_run:
  git('fetch','origin','main');main=git('rev-parse','origin/main').decode().strip()
  if git('show','origin/main:site/wrangler.jsonc')!=(ROOT/'site/wrangler.jsonc').read_bytes():raise ValueError('hosting config differs from main')
  for path,pin in inputs.items():
   file=Path(path)
   if file.is_relative_to(ROOT):
    data=git('show','origin/main:'+str(file.relative_to(ROOT)))
    if hashlib.sha256(data).hexdigest()!=pin:raise ValueError('candidate differs from main '+str(file.relative_to(ROOT)))
 else:main=git('rev-parse','HEAD').decode().strip()
 wrangler=a.wrangler.resolve(strict=True)
 argv=[str(wrangler),'deploy','--config',str(ROOT/'site/wrangler.jsonc'),'--assets',str(candidate),'--no-bundle','--message','KOTOBA-160 JUN-851 native site '+main[:12]]
 if a.dry_run:argv+=['--dry-run','--outdir',str(build/'upload-dry-run')]
 result=subprocess.run(argv,cwd=ROOT,env={**os.environ,'WRANGLER_SEND_METRICS':'false'},capture_output=True,timeout=900)
 (build/('upload-dry-run.log' if a.dry_run else 'upload.log')).write_bytes(result.stdout+result.stderr)
 print(result.stdout.decode(),end='');print(result.stderr.decode(),end='')
 if result.returncode:raise SystemExit(result.returncode)
 record={'mode':'dry-run' if a.dry_run else 'uploaded','main':main,'buildReceiptSha256':sha(build/'receipt.json'),'observationSha256':sha(build/'observation.json'),'refusalsSha256':sha(build/'negative-controls.json'),'nativeValidationSha256':sha(build/'native-validation.json'),'staticOnly':True,'liveReadbackComplete':False}
 (build/('upload-dry-run.json' if a.dry_run else 'upload.json')).write_text(json.dumps(record,indent=2)+'\n')
if __name__=='__main__':main()
