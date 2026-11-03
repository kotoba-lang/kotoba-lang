import argparse,hashlib,json,os,platform,re,shutil,subprocess,time
from pathlib import Path
ap=argparse.ArgumentParser(description="Qualify experimental Linux native legal-document assembly in a private stage")
for name in ('stage','head','body','compiler','runner','reference','output'):
 ap.add_argument('--'+name,type=Path,required=True)
for name in ('head-sha256','body-sha256','compiler-sha256','runner-sha256','reference-sha256'):
 ap.add_argument('--'+name,required=True)
a=ap.parse_args()
if (platform.system(),platform.machine())!=('Linux','x86_64'):raise ValueError('requires Linux x86_64')
stage,head,body,compiler,runner,reference=(p.resolve(strict=True) for p in (a.stage,a.head,a.body,a.compiler,a.runner,a.reference))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def ref(p,digest):
 if sha(p)!=digest:raise ValueError(('receipt drift',p))
 rows=json.loads(p.read_text())['observations'];out={r['tag']:r['sha256'] for r in rows}
 if len(rows)!=17 or len(out)!=17:raise ValueError('missing tags')
 return out
if sha(compiler)!=a.compiler_sha256 or sha(runner)!=a.runner_sha256:raise ValueError('binary drift')
hashes=ref(head/'receipt.json',a.head_sha256)
bashes=ref(body/'receipt.json',a.body_sha256)
dashes=ref(reference,a.reference_sha256)
if hashes.keys()!=bashes.keys() or hashes.keys()!=dashes.keys():raise ValueError('locale mismatch')
out=a.output.resolve()
if not out.is_relative_to(stage) or out==stage:raise ValueError('output must be a new private directory inside stage')
out.mkdir(parents=True,exist_ok=False);src=stage/'src';entry=src/'site/native/legal_document_file_linux_file.kotoba'
files=[p for p in sorted(src.rglob('*.kotoba')) if p.name not in ('legal_document_file.kotoba','legal_document_page.kotoba')]
pins={str(p):sha(p) for p in files};lock=out/'project.ksl';lock.write_text('KSL1\nentry '+str(entry)+'\nroot '+str(src)+'\n'+''.join('file '+sha(p)+' '+str(p)+'\n' for p in files))
env={'PATH':'/nonexistent','LANG':'C','KEXE_CAP_RESOURCES_35':str(stage),'KEXE_STACK_BYTES':'8388608'};image=out/'document.kx64'
r=subprocess.run([str(compiler),'compile',str(entry),'--target','x86_64-linux','--output',str(image),'--format','kx64-v1','--source-path',str(src),'--source-lock',str(lock),'--source-lock-sha256',sha(lock)],env=env,cwd=stage,capture_output=True,timeout=900)
if r.returncode or r.stderr:raise RuntimeError(('compile',r.returncode,r.stderr[-500:]))
header,payload=image.read_bytes().split(b'\n\n',1);m=re.fullmatch(rb'KX64V1 x86_64-linux sysv-ctx-v11 ([0-9]+) 0',header)
if not m or int(m[1])!=len(payload):raise ValueError('invalid KX64')
binary=out/'document.bin';binary.write_bytes(payload)
def run(tag,dest,d,result):
 t=time.monotonic();r=subprocess.run([str(runner),str(binary),'0','0','x86_64','35,37,38,39','--',tag,str(dest),str(d)],env=dict(env,KEXE_ALLOW='35,37,38,39',KEXE_STRUCTURED_REPORT='1',KEXE_FUEL='off',KEXE_STRING_POOL='268435456'),cwd=stage,capture_output=True,timeout=120)
 if r.returncode or r.stderr or not re.search(rb':status :ok\s+:result '+str(result).encode()+rb'\b',r.stdout):raise RuntimeError((tag,r.returncode,r.stdout[-500:],r.stderr[-500:]))
 return time.monotonic()-t
def manifest(d,tag):(d/'document-manifest.edn').write_text('{:tag "'+tag+'" :head "'+sha(d/'head.html')+'" :body "'+sha(d/'body.html')+'"}')
rows=[]
for tag in hashes:
 d=out/tag;d.mkdir();h=head/(tag+'.html');b=body/(tag+'.html')
 if sha(h)!=hashes[tag] or sha(b)!=bashes[tag]:raise ValueError(('input hash',tag))
 shutil.copyfile(h,d/'head.html');shutil.copyfile(b,d/'body.html');manifest(d,tag);target=d/'page.html';seconds=run(tag,target,d,42)
 if sha(target)!=dashes[tag] or b'freebuff' in target.read_bytes().lower():raise ValueError(('document mismatch',tag))
 rows.append({'tag':tag,'sha256':sha(target),'bytes':target.stat().st_size,'seconds':seconds});print(tag,'pass',flush=True)
d=out/'ja';target=d/'occupied.html';target.write_text('preserve');run('ja',target,d,7)
if target.read_text()!='preserve':raise ValueError('existing file modified')
for name in ('head.html','body.html'):
 p=d/name;old=p.read_bytes()
 try:
  p.write_bytes(old+b' ');target=d/('mutated-'+name);run('ja',target,d,7)
  if target.exists():raise ValueError('mutation emitted file')
 finally:p.write_bytes(old)
manifest(d,'en');target=d/'wrong-tag.html';run('ja',target,d,7)
if target.exists():raise ValueError('wrong tag wrote file')
if any(sha(Path(p))!=digest for p,digest in pins.items()):raise ValueError('source drift')
(out/'receipt.json').write_text(json.dumps({'status':'PASS_EXPERIMENTAL_LINUX_LEGAL_DOCUMENTS','observations':rows,'inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'headReceiptSha256':sha(head/'receipt.json'),'bodyReceiptSha256':sha(body/'receipt.json'),'referenceReceiptSha256':sha(reference),'experimentalCompiler':True,'linuxSpawnQualified':False,'fullSiteGenerated':False,'publicationQualified':False},indent=2)+'\n')
print('PASS_EXPERIMENTAL_LINUX_LEGAL_DOCUMENTS')
