#!/usr/bin/env python3
"""Compare native full native article CSS from pinned sources."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
from check_source_concat import Style, REFS
ROOT = Path(__file__).resolve().parents[2]
PINS = ROOT / 'site/native/css-source-dependencies.json'
FILES = [('resources/jp_go_dds/dds.css','dds.css'), ('resources/jp_go_dds/components/language-selector.css','language.css'), ('resources/jp_go_dds/components/menu-list-box.css','box.css'), ('resources/jp_go_dds/components/menu-list.css','menu.css'), ('src/jp_go_dds/behavior.cljk','behavior.cljk'), ('src/jp_go_dds/core.cljk','core.cljk'), ('src/jp_go_dds/tokens.cljk','tokens.cljk')]

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'dds-root', 'css-source', 'json-source', 'theme-json', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'): raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, dds, css_source, json_source, theme_json = [p.resolve(strict=True) for p in (a.compiler, a.packager, a.sha256_source, a.dds_root, a.css_source, a.json_source, a.theme_json)]
    spec = json.loads(PINS.read_text()); deps = json.loads((ROOT / 'site/native/dependencies.json').read_text())
    sources = [(dds / path, name) for path, name in FILES]
    if (spec['schema'] != 'kotoba.site.native-css-source-dependencies.v1' or
        any(sha(src) != spec['ddsBase']['files'][path] for (path, _), (src, _) in zip(FILES, sources)) or
        deps['dependencies'][0]['sha256'] != sha(sha_source) or spec['cssSheet']['sha256'] != sha(css_source) or spec['jsonScalar']['sha256'] != sha(json_source) or spec['syntaxTheme']['sha256'] != sha(theme_json) or spec['themeSource']['sha256'] != sha(dds / 'src/jp_go_dds/theme_toggle.cljk') or json.loads((ROOT / 'site/native/metadata-sources.json').read_text())['files']['site/generate.cljk'] != sha(ROOT / 'site/generate.cljk')):
        raise ValueError('source pin mismatch')
    for binary in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(binary)], check=True, capture_output=True, timeout=30)
    references = []
    for p in REFS:
        parser = Style(); parser.feed(p.read_text()); parser.close(); references.append(''.join(parser.data))
    tracked = [compiler, packager, sha_source, json_source, theme_json, dds / 'src/jp_go_dds/theme_toggle.cljk', ROOT / 'site/generate.cljk', ROOT / 'site/native/metadata-sources.json', *[p for p, _ in sources], PINS, ROOT / 'site/native/dependencies.json',
               ROOT / 'site/native/check_source_concat.py', *REFS, Path(__file__).resolve()]
    modules = [(ROOT / 'site/native/dds_base_css.kotoba', 'src/site/native/dds_base_css.kotoba'),
               (ROOT / 'site/native/dds_ext_css.kotoba', 'src/site/native/dds_ext_css.kotoba'),
               (ROOT / 'site/native/dds_ext_data.kotoba', 'src/site/native/dds_ext_data.kotoba'),
               (ROOT / 'site/native/dds_palette.kotoba', 'src/site/native/dds_palette.kotoba'),
               (ROOT / 'site/native/syntax_theme.kotoba', 'src/site/native/syntax_theme.kotoba'),
               (json_source, 'src/json_scalar.kotoba'),
               (ROOT / 'site/native/dds_skin_css.kotoba', 'src/site/native/dds_skin_css.kotoba'),
               (ROOT / 'site/native/source_concat.kotoba', 'src/site/native/source_concat.kotoba'),
               (css_source, 'src/css_sheet.kotoba'),
               (ROOT / 'site/native/dds_bridge_css.kotoba', 'src/site/native/dds_bridge_css.kotoba'),
                              (ROOT / 'test/site/native/article_style_file.kotoba', 'src/site/native/article_style_file.kotoba'),
               (sha_source, 'src/multiformats/sha256.kotoba')]
    tracked += [p for p, _ in modules]
    before = {str(p): sha(p) for p in tracked}
    out = a.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    for src, name in modules:
        dst = out / name; dst.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(src, dst)
    for src, name in sources: shutil.copyfile(src, out / name)
    shutil.copyfile(dds / 'src/jp_go_dds/theme_toggle.cljk', out / 'theme-source.cljk')
    shutil.copyfile(ROOT / 'site/generate.cljk', out / 'generator.cljk')
    shutil.copyfile(theme_json, out / 'theme.json')
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/article_style_file.kotoba\nroot /src\n' +
                    ''.join('file ' + sha(out / name) + ' /' + name + '\n' for _, name in sorted(modules, key=lambda x: x[1])))
    fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY); saved = None
    try:
        if fd != 3:
            try: saved = os.dup(3)
            except OSError: pass
            os.dup2(fd, 3)
        def run(image, *args, expected_exit=0):
            p = subprocess.run([str(image), *args], cwd=out, env={'PATH': '/nonexistent', 'LANG': 'C'},
                               pass_fds=(3,), capture_output=True, timeout=300)
            if p.returncode != expected_exit or p.stderr: raise RuntimeError(('native command', args, p.returncode, p.stderr[-500:]))
            return p.stdout
        run(compiler, 'compile', '/src/site/native/article_style_file.kotoba', '--target', 'aarch64-macos',
            '--output', 'base.kseed', '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
        info = run(compiler, 'extract-native', 'base.kseed', '--symbol', 'main', '--output', 'base.raw')
        m = re.search(rb':offset (\d+), :length (\d+), :arity 0}', info)
        if not m or int(m[2]) != (out / 'base.raw').stat().st_size: raise ValueError('bad entry metadata')
        run(packager, 'base-unsigned.macho', 'base.raw', 'fs2-cli-stdio-general', m[1].decode())
        binary = out / 'base.macho'; shutil.copyfile(out / 'base-unsigned.macho', binary); binary.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(binary)], check=True, capture_output=True, timeout=30)
        results = []
        for route, reference in zip(('infra', 'security'), references):
            (out / 'route').write_text(route)
            result = run(binary, expected_exit=42)
            if result != reference.encode(): raise ValueError('complete article CSS differs: ' + route)
            results.append(result)
        result = b''.join(results)
        original = (out / 'tokens.cljk').read_bytes(); (out / 'tokens.cljk').write_bytes(original + b' ')
        run(binary, expected_exit=7)
    finally:
        if fd != 3:
            if saved is None: os.close(3)
            else: os.dup2(saved, 3); os.close(saved)
        os.close(fd)
    if any(sha(p) != before[str(p)] for p in tracked): raise ValueError('source changed during gate')
    receipt = {'status':'PASS_NATIVE_COMPLETE_ARTICLE_CSS', 'inputs':before, 'binarySha256':sha(binary),
               'sourceLockSha256':sha(lock), 'outputSha256':hashlib.sha256(result).hexdigest(), 'modifiedInputRefused':True,
               'darkRulesGenerated':False, 'completeStylesheetGenerated':True, 'siteGenerated':False, 'publicationQualified':False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n'); print(receipt['status'])
if __name__ == '__main__': main()
