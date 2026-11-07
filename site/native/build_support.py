"""Byte staging and process observation for the native site generators.

All parsing of site authorities, rendering, translation and composition occurs
in Kotoba. This module copies declared source/data bytes and launches pinned
native tools; it never invokes the retained CLJK path.
"""
from pathlib import Path
import hashlib,json,os,platform,re,shutil,subprocess,time
ROOT=Path(__file__).resolve().parents[2]
TAGS=('en','ar','arz','bn','de','es','fr','hi','id','ja','ko','mr','pcm','pt','ru','ur','zh-Hans')
COMPILER_SHA='2b4ac12a77f156ccc2790eb0cf5453e57b9cac8669a2dce9779095579f7994a2'
PACKAGER_SHA='e95284408f091c8979ef74ece57116a4df2250e61e42074da5dc95ca30264142'
ALIASES={'html_core.kotoba':'kotoba.html/kotoba/html_core.kotoba','html_hiccup.kotoba':'kotoba.html/kotoba/html_hiccup.kotoba','components.kotoba':'kotoba.jpGoDigitalDesignSystem/kotoba/components.kotoba','button.kotoba':'kotoba.jpGoDigitalDesignSystem/kotoba/button.kotoba','table.kotoba':'kotoba.jpGoDigitalDesignSystem/kotoba/table.kotoba','theme_toggle.kotoba':'kotoba.jpGoDigitalDesignSystem/kotoba/theme_toggle.kotoba','language_selector.kotoba':'kotoba.jpGoDigitalDesignSystem/kotoba/language_selector.kotoba','css_sheet.kotoba':'kotoba.css/kotoba/css_sheet.kotoba','json_scalar.kotoba':'kotoba.json/kotoba/json_scalar.kotoba','json/core.kotoba':'kotoba.json/src/json/core.kotoba','multiformats/sha256.kotoba':'kotoba.ioMultiformats/src/multiformats/sha256.kotoba','multiformats/cid.kotoba':'kotoba.ioMultiformats/src/multiformats/cid.kotoba'}
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def write_bytes(path,data):
 path=Path(path);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
def program_image(folder):
 folder=Path(folder)
 return folder/('entry.bin' if (folder/'entry.bin').exists() else 'entry.macho')
def native_run(image,folder,args=(),expected=42,timeout=900):
 """The child receives only the private data root and declared native inputs."""
 if Path(image).suffix=='.bin':
  return linux_run(image,folder,args,expected,timeout)
 image,folder=Path(image),Path(folder);fd=os.open(folder,os.O_RDONLY|os.O_DIRECTORY);saved=None
 if fd!=3:
  try:saved=os.dup(3)
  except OSError:pass
  os.dup2(fd,3)
 try:
  start=time.monotonic();r=subprocess.run([str(image),*map(str,args)],cwd=folder,env={'PATH':'/nonexistent','LANG':'C'},pass_fds=(3,),capture_output=True,timeout=timeout)
 finally:
  if fd!=3:
   if saved is None:os.close(3)
   else:os.dup2(saved,3);os.close(saved)
  os.close(fd)
 call={'image':str(image),'argv':list(map(str,args)),'imageSha256':sha(image),'exit':r.returncode,'seconds':time.monotonic()-start,'stdoutSha256':hashlib.sha256(r.stdout).hexdigest(),'stderrSha256':hashlib.sha256(r.stderr).hexdigest(),'PATH':'/nonexistent'}
 with (folder/'calls.jsonl').open('a') as f:f.write(json.dumps(call)+'\n')
 if r.returncode!=expected or r.stderr:
  (folder/'failed.stdout').write_bytes(r.stdout);(folder/'failed.stderr').write_bytes(r.stderr)
  raise RuntimeError((image.name,args,r.returncode,r.stderr[-1000:]))
 return r.stdout

def linux_run(image,folder,args=(),expected=42,timeout=900):
 image,folder=Path(image),Path(folder)
 record=json.loads((image.parent/'build.json').read_text());runner=Path(record['runner']['path'])
 if sha(image)!=record['binarySha256'] or sha(runner)!=record['runner']['sha256']:raise ValueError('Linux native program/runner drift')
 env={'PATH':'/nonexistent','LANG':'C','KEXE_ALLOW':'35,37,38,39','KEXE_CAP_RESOURCES_35':str(folder),'KEXE_STACK_BYTES':'8388608','KEXE_FUEL':'off','KEXE_STRING_POOL':'268435456','KEXE_STRUCTURED_REPORT':'1'}
 argv=[str(runner),str(image),'0','0','x86_64','35,37,38,39','--',*map(str,args)]
 start=time.monotonic();r=subprocess.run(argv,cwd=folder,env=env,capture_output=True,timeout=timeout)
 call={'image':str(image),'argv':argv,'imageSha256':sha(image),'runnerSha256':sha(runner),'exit':r.returncode,'seconds':time.monotonic()-start,'stdoutSha256':hashlib.sha256(r.stdout).hexdigest(),'stderrSha256':hashlib.sha256(r.stderr).hexdigest(),'PATH':'/nonexistent','scope':str(folder)}
 with (folder/'calls.jsonl').open('a') as f:f.write(json.dumps(call)+'\n')
 tail=re.search(rb'\{:status :ok :result '+str(expected).encode()+rb' :fuel [^\n]*\}\n?$',r.stdout)
 if r.returncode or r.stderr or not tail:
  (folder/'failed.stdout').write_bytes(r.stdout);(folder/'failed.stderr').write_bytes(r.stderr)
  raise RuntimeError((image.name,args,r.returncode,r.stdout[-200:],r.stderr[-1000:]))
 return r.stdout[:tail.start()]
class Build:
 def __init__(self,output,compiler,packager,libraries,grammar):
  if (platform.system(),platform.machine())!=('Darwin','arm64'):raise ValueError('this native build profile requires macOS ARM64')
  self.output=Path(output).resolve();self.output.mkdir(parents=True,exist_ok=False)
  self.compiler=Path(compiler).resolve(strict=True);self.packager=Path(packager).resolve(strict=True)
  self.libs=Path(libraries).resolve(strict=True);self.grammar=Path(grammar).resolve(strict=True)
  for p,h in [(self.compiler,COMPILER_SHA),(self.packager,PACKAGER_SHA)]:
   if sha(p)!=h:raise ValueError('unqualified native tool '+str(p))
   subprocess.run(['/usr/bin/codesign','--verify','--strict',str(p)],check=True,capture_output=True,timeout=30)
  self.recipes=json.loads((ROOT/'site/native/build-components.json').read_text())
  pins=json.loads((ROOT/'site/native/build-library-pins.json').read_text())
  for name,path in ALIASES.items():
   if pins[name]['path']!=path or sha(self.libs/path)!=pins[name]['sha256']:raise ValueError('native library pin drift '+name)
  self.inputs={};self.data={};self.components={};self.prepare_data()
 def add(self,name,path,expected=None):
  p=Path(path).resolve(strict=True)
  if expected and sha(p)!=expected:raise ValueError('input pin mismatch '+str(p))
  self.data[name]=p;self.inputs[str(p)]=sha(p)
 def prepare_data(self):
  for base in ['site','lang']:
   for p in (ROOT/base).glob('*.edn'):self.add(p.name,p)
  for p in (ROOT/'site/i18n').glob('*.json'):self.add('site/i18n/'+p.name,p)
  for src,dest in [('site/generate.cljk','generator.cljk'),('site/generate.cljk','source.txt'),('site/src/kotoba/site/hero.cljk','hero.cljk'),('site/src/kotoba/site/chart.cljk','chart.cljk'),('site/assets/play/double-21.kotoba','play.kotoba'),('site/assets/play/double-21.wasm','play.wasm'),('site/assets/play/double-21.wasm.provenance.edn','provenance.edn'),('site/benchmarks/embench-c-parity-20261015.json','embench.json'),('bench/public-runtime-comparison/latest.json','runtime-benchmark.json'),('bench/public-build-scaling/latest.json','scaling-benchmark.json'),('bench/public-domain-comparison/latest.json','domain-benchmark.json'),('bench/public-end-to-end-comparison/latest.json','loop-benchmark.json'),('bench/public-end-to-end-comparison/latest.json','end-to-end-benchmark.json'),('bench/public-compile-comparison/latest.json','compile-benchmark.json'),('bench/public-compile-comparison/latest.json','benchmark.json'),('site/_headers','headers.txt')]:self.add(dest,ROOT/src)
  self.add('release.edn',ROOT/'lang/docs-release.edn')
  self.add('search-index.edn',ROOT/'docs/search-index.edn')
  self.add('locales.cljk',ROOT/'site/src/kotoba/site/locales.cljk')
  self.add('embench-parity.json',ROOT/'site/benchmarks/embench-c-parity-20261015.json')
  for p in (ROOT/'site').glob('*article.edn'):self.add('site/'+p.name,p)
  spec=json.loads((ROOT/'site/native/css-source-dependencies.json').read_text());dds=self.libs/'kotoba.jpGoDigitalDesignSystem'
  for name,path in [('dds.css','resources/jp_go_dds/dds.css'),('language.css','resources/jp_go_dds/components/language-selector.css'),('box.css','resources/jp_go_dds/components/menu-list-box.css'),('menu.css','resources/jp_go_dds/components/menu-list.css'),('behavior.cljk','src/jp_go_dds/behavior.cljk'),('core.cljk','src/jp_go_dds/core.cljk'),('tokens.cljk','src/jp_go_dds/tokens.cljk')]:self.add(name,dds/path,spec['ddsBase']['files'][path])
  self.add('theme-source.cljk',dds/'src/jp_go_dds/theme_toggle.cljk',spec['themeSource']['sha256'])
  self.add('theme.json',self.grammar/spec['syntaxTheme']['path'],spec['syntaxTheme']['sha256'])
  highlight=json.loads((ROOT/'site/native/highlight-source-dependencies.json').read_text())['grammar']
  self.add('grammar.edn',self.grammar/highlight['catalogPath'],highlight['catalogSha256']);self.add('highlight.cljk',self.grammar/highlight['tokenizerPath'],highlight['tokenizerSha256'])
 def compile(self,name):
  recipe=self.recipes[name];folder=self.output/name;folder.mkdir()
  sources={}
  for n in recipe['modules']:sources['src/site/native/'+n+'.kotoba']=ROOT/'site/native'/(n+'.kotoba')
  for n in recipe['entries']:sources[n]=ROOT/'test/site/native'/Path(n).name
  for n in recipe['aliases']:sources['src/'+n]=self.libs/ALIASES[n]
  for target,source in sources.items():
   write_bytes(folder/target,source.read_bytes());self.inputs[str(source)]=sha(source)
  for target,source in self.data.items():write_bytes(folder/target,source.read_bytes())
  entry=recipe.get('entry') or recipe['entries'][0];lock=folder/'project.ksl'
  lock.write_text('KSL1\nentry /'+entry+'\nroot /src\n'+''.join('file '+sha(folder/n)+' /'+n+'\n' for n in sorted(sources)))
  native_run(self.compiler,folder,['compile','/'+entry,'--target','aarch64-macos','--output','entry.kseed','--source-path','/src','--source-lock','project.ksl','--source-lock-sha256',sha(lock)],expected=0)
  info=native_run(self.compiler,folder,['extract-native','entry.kseed','--symbol','main','--output','entry.raw'],expected=0)
  match=re.search(rb':offset (\d+), :length (\d+), :arity 0}',info)
  if not match or int(match[2])!=(folder/'entry.raw').stat().st_size:raise ValueError('native entry mismatch')
  native_run(self.packager,folder,['entry-unsigned.macho','entry.raw','fs2-cli-stdio-general',match[1].decode()],expected=0)
  image=folder/'entry.macho';shutil.copyfile(folder/'entry-unsigned.macho',image);image.chmod(0o700);subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
  record={'sourceLockSha256':sha(lock),'binarySha256':sha(image),'sources':{n:sha(folder/n) for n in sources},'data':{n:sha(p) for n,p in self.data.items()}}
  (folder/'build.json').write_text(json.dumps(record,indent=2)+'\n');self.components[name]=folder
  print('compiled',name,flush=True);return folder
 def run(self,name,args=(),output=None):
  folder=self.components.get(name) or self.compile(name)
  data=native_run(folder/'entry.macho',folder,args)
  if output:write_bytes(output,data)
  return data
 def finish(self):
  if any(sha(p)!=h for p,h in self.inputs.items()):raise ValueError('build input changed')
  (self.output/'inputs.json').write_text(json.dumps(self.inputs,indent=2)+'\n')
