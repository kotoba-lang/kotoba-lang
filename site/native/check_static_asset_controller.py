#!/usr/bin/env python3
"""Qualify native staging of checked-in static site assets on macOS arm64."""
import argparse
import hashlib
import json
import os
import platform
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'asset-stager-dir', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'):
        raise ValueError('requires macOS arm64')
    compiler, packager, source, stager_dir = (p.resolve(strict=True) for p in (a.compiler, a.packager, a.sha256_source, a.asset_stager_dir))
    deps = ROOT / 'site/native/dependencies.json'
    stager = stager_dir / 'stage.macho'
    if sha(source) != json.loads(deps.read_text())['dependencies'][0]['sha256']:
        raise ValueError('SHA dependency drift')
    if sha(stager) != json.loads((stager_dir / 'receipt.json').read_text())['binarySha256']:
        raise ValueError('stager drift')
    for p in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(p)], check=True, capture_output=True, timeout=30)
    assets = ROOT / 'site/assets'
    mappings = [('', 'kotoba-wordmark.png', assets/'kotoba-wordmark.png'),
                ('', 'favicon.ico', assets/'kotoba-favicon.ico'),
                ('', 'kotoba-favicon.png', assets/'kotoba-favicon.png'),
                ('', 'kotoba-og-card.png', assets/'kotoba-og-card.png'),
                ('', 'agent-quickstart.md', assets/'agent-quickstart.md'),
                ('', 'llms.txt', assets/'llms.txt'), ('', 'llms-full.txt', assets/'llms-full.txt'),
                ('.well-known', 'security.txt', assets/'security.txt'),
                ('anatomy', 'light.html', assets/'anatomy/kotoba-murakumo-light.html')]
    mappings += [('play', p.name, p) for p in sorted((assets/'play').iterdir()) if p.is_file()]
    mappings += [('ipfs', p.name, p) for p in sorted((assets/'ipfs').iterdir()) if p.is_file()]
    mappings += [('ipfs', 'bafkreiaeohkv2zuo2x4yq745euqm5wijrub5xpkxbbw5gre6tdp3sxzonm', assets/'play/double-21.kotoba')]
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
    if len({(group, name) for group, name, _ in mappings}) != len(mappings):
        raise ValueError('duplicate destination')
    modules = [(ROOT/'site/native/static_asset_controller.kotoba', 'src/site/native/static_asset_controller.kotoba'),
               (ROOT/'test/site/native/static_asset_controller_file.kotoba', 'src/site/native/static_asset_controller_file.kotoba'),
               (source, 'src/multiformats/sha256.kotoba')]
    tracked = [compiler, packager, source, stager, stager_dir/'receipt.json', deps, Path(__file__).resolve(), *[p for p, _ in modules], *[p for _, _, p in mappings]]
    pins = {str(p): sha(p) for p in tracked}
    out = a.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    for p, name in modules:
        target = out/name; target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(p, target)
    lock = out/'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/static_asset_controller_file.kotoba\nroot /src\n' + ''.join('file '+sha(out/n)+' /'+n+'\n' for _, n in sorted(modules, key=lambda x:x[1])))
    def build(image, *args):
        fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY)
        if fd != 3: os.dup2(fd, 3)
        try:
            r = subprocess.run([str(image), *map(str,args)], cwd=out, env={'PATH':'/nonexistent','LANG':'C'}, pass_fds=(3,), capture_output=True, timeout=300)
        finally:
            if fd != 3: os.close(fd)
        if r.returncode or r.stderr: raise RuntimeError((image.name, args, r.returncode, r.stderr[-500:]))
        return r.stdout
    build(compiler, 'compile', '/src/site/native/static_asset_controller_file.kotoba', '--target', 'aarch64-macos', '--output', 'assets.kseed', '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
    info = build(compiler, 'extract-native', 'assets.kseed', '--symbol', 'main', '--output', 'assets.raw')
    match = re.search(rb':offset (\d+), :length (\d+), :arity 0}', info)
    if not match or int(match[2]) != (out/'assets.raw').stat().st_size: raise ValueError('invalid entry')
    build(packager, 'assets-unsigned.macho', 'assets.raw', 'amu-cli-host', match[1].decode())
    image = out/'assets.macho'; shutil.copyfile(out/'assets-unsigned.macho', image); image.chmod(0o700)
    subprocess.run(['/usr/bin/codesign','--force','--sign','-',str(image)],check=True,capture_output=True,timeout=30)
    rows = [{'group': group, 'name': name, 'source': str(src.resolve()), 'sha256': sha(src)} for group, name, src in mappings]
    def manifest(name, entries, stager_hash=None):
        def esc(s): return json.dumps(s,ensure_ascii=False)
        body = ' '.join('{'+' '.join(':'+k+' '+esc(v) for k,v in row.items())+'}' for row in entries)
        p = out/(name+'.edn'); p.write_text('{:stager '+esc(str(stager))+' :stager-sha256 '+esc(stager_hash or sha(stager))+' :assets ['+body+']}'); return p
    def run(name, entries, expected, stager_hash=None):
        private=out/(name+'-private'); site=out/(name+'-site'); private.mkdir(); site.mkdir()
        r=subprocess.run([str(image),str(manifest(name,entries,stager_hash)),str(private),str(site)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},capture_output=True,timeout=900)
        if r.returncode!=expected or r.stdout or r.stderr: raise RuntimeError((name,r.returncode,r.stdout[:100],r.stderr[-500:]))
        return private,site
    for name, entries, wrong in [('wrong-source', [dict(rows[0],sha256='0'*64)], None), ('duplicate', [rows[0],rows[0]], None),
                                 ('bad-group',[dict(rows[0],group='../escape')],None), ('bad-name',[dict(rows[0],name='../escape')],None),
                                 ('wrong-stager',[rows[0]],'0'*64)]:
        private,site=run(name,entries,7,wrong)
        if list(private.iterdir()) or list(site.iterdir()): raise ValueError(('refusal left output',name))
    private,site=run('good',rows,42)
    expected={str(Path(group)/name):sha(src) for group,name,src in mappings}
    actual={str(p.relative_to(site)):sha(p) for p in site.rglob('*') if p.is_file()}
    if actual!=expected: raise ValueError('output set or digest mismatch')
    r=subprocess.run([str(image),str(out/'good.edn'),str(private),str(site)],cwd=out,env={'PATH':'/nonexistent','LANG':'C'},capture_output=True,timeout=900)
    if r.returncode!=7 or r.stdout or r.stderr or {str(p.relative_to(site)):sha(p) for p in site.rglob('*') if p.is_file()}!=expected:
        raise ValueError('existing output not preserved')
    if any(sha(Path(p))!=h for p,h in pins.items()): raise ValueError('input drift')
    receipt={'status':'PASS_NATIVE_STATIC_ASSET_CONTROLLER_MACOS','inputs':pins,'binarySha256':sha(image),'sourceLockSha256':sha(lock),'assets':expected,'assetCount':len(expected),'badDigestAndPathRefusals':True,'duplicateRefused':True,'existingStagePreserved':True,'privateImmutableStageRequired':True,'completeAssetManifest':False,'completeSiteGenerated':False,'publicationQualified':False}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(receipt['status'],len(expected))

if __name__=='__main__': main()
