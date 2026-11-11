"""Linux source/byte staging; all composition remains native Kotoba."""
import json
import platform
import shutil
import threading
from pathlib import Path
from build_support import Build, ROOT, ALIASES, sha, write_bytes, native_run
from observe_linux import Observation, static_elf


class LinuxBuild(Build):
    profile = 'Linux-x86_64'

    def __init__(self, output, inputs, libraries, grammar):
        if (platform.system(), platform.machine()) != ('Linux', 'x86_64'):
            raise ValueError('native Linux x86-64 required')
        self.output = Path(output).resolve()
        self.output.mkdir(parents=True, exist_ok=False)
        inputs = Path(inputs).resolve(strict=True)
        self.compiler = inputs/'amu.elf'
        spec = json.loads((ROOT/'site/native/linux_toolchain.json').read_text())
        if static_elf(self.compiler) != spec['profiles']['current-source']['compilerSha256']:
            raise ValueError('Linux full build requires the explicit current-source profile')
        source = inputs/'runner.kotoba'
        if sha(source) != spec['runnerSourceSha256']:
            raise ValueError('native runner source drift')
        self.observer = Observation(self.output, self.compiler, True)
        runtime = self.output/'runtime'
        runtime.mkdir()
        shutil.copyfile(source, runtime/'runner.kotoba')
        self.packager = runtime/'runner.elf'
        row, _, err = self.observer.invoke('runner-build', self.compiler, runtime,
            ['compile', runtime/'runner.kotoba', '--target', 'x86_64-linux', '--output', self.packager, '--format', 'kx64-runner'])
        if row['exit'] or err or not self.packager.exists():
            raise ValueError('native runner build failed')
        static_elf(self.packager)
        self.packager.chmod(0o700)
        self.libs, self.grammar = Path(libraries).resolve(strict=True), Path(grammar).resolve(strict=True)
        pins = json.loads((ROOT/'site/native/build-library-pins.json').read_text())
        for n, path in ALIASES.items():
            if pins[n]['path'] != path or sha(self.libs/path) != pins[n]['sha256']:
                raise ValueError('native library drift '+n)
        self.recipes = json.loads((ROOT/'site/native/build-components.json').read_text())
        self.inputs = {str(source):sha(source), str(self.compiler):sha(self.compiler),
                       str(ROOT/'site/native/input_linux.kotoba'):sha(ROOT/'site/native/input_linux.kotoba')}
        self.data, self.components = {}, {}
        self.source_overrides = {}
        self._lock = threading.Lock()
        self.prepare_data()

    def compile(self, name):
        recipe = self.recipes[name]
        sources, originals = {}, {}
        for n in recipe['modules']:
            original = ROOT/'site/native'/('input_linux.kotoba' if n == 'input' else n+'.kotoba')
            originals['src/site/native/'+n+'.kotoba'] = original
        for n in recipe['entries']:
            originals[n] = ROOT/'test/site/native'/Path(n).name
        for n in recipe['aliases']:
            originals['src/'+n] = self.libs/ALIASES[n]
        for n, original in originals.items():
            sources[n] = self.source_overrides.get(n, original.read_bytes())
            with self._lock:
                self.inputs[str(original)] = sha(original)
        row, image, folder = self.observer.compile(name, sources, recipe.get('entry') or recipe['entries'][0])
        if not row['compiled']:
            raise ValueError('native producer compile failed '+name+': '+row['stderrTail'])
        shutil.copyfile(folder/'source.ksl', folder/'project.ksl')
        for n, path in self.data.items():
            write_bytes(folder/n, path.read_bytes())
        record = dict(profile=self.profile, image='entry.bin', binarySha256=sha(image),
                      sourceLockSha256=sha(folder/'project.ksl'), sources={n:sha(folder/n) for n in sources},
                      data={n:sha(p) for n,p in self.data.items()},
                      runner=dict(path=str(self.packager), sha256=sha(self.packager)))
        (folder/'build.json').write_text(json.dumps(record, indent=2)+'\n')
        with self._lock:
            self.components[name] = folder
        print('compiled Linux', name, flush=True)
        return folder

    def run(self, name, args=(), output=None):
        folder = self.components.get(name) or self.compile(name)
        data = native_run(folder/'entry.bin', folder, args)
        if output:
            write_bytes(output, data)
        return data
