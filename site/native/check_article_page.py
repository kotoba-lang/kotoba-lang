#!/usr/bin/env python3
"""Compare native complete native multilingual article pages from pinned sources."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
from check_source_concat import REFS
from check_footers import Tree, normalize
from concurrent.futures import ThreadPoolExecutor
from reference_translation import reference_title

ROOT = Path(__file__).resolve().parents[2]
PINS = ROOT / 'site/native/css-source-dependencies.json'
TAGS = 'en ar arz bn de es fr hi id ja ko mr pcm pt ru ur zh-Hans'.split()
FILES = [('resources/jp_go_dds/dds.css','dds.css'), ('resources/jp_go_dds/components/language-selector.css','language.css'), ('resources/jp_go_dds/components/menu-list-box.css','box.css'), ('resources/jp_go_dds/components/menu-list.css','menu.css'), ('src/jp_go_dds/behavior.cljk','behavior.cljk'), ('src/jp_go_dds/core.cljk','core.cljk'), ('src/jp_go_dds/tokens.cljk','tokens.cljk')]

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'dds-root', 'css-source', 'json-source', 'theme-json', 'html-core', 'html-hiccup', 'dds-button', 'dds-theme', 'dds-selector', 'dds-components', 'dds-table', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    a = ap.parse_args()
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'): raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, dds, css_source, json_source, theme_json, html_core, html_hiccup, button, theme, selector, components, table = [p.resolve(strict=True) for p in (a.compiler, a.packager, a.sha256_source, a.dds_root, a.css_source, a.json_source, a.theme_json, a.html_core, a.html_hiccup, a.dds_button, a.dds_theme, a.dds_selector, a.dds_components, a.dds_table)]
    spec = json.loads(PINS.read_text()); deps = json.loads((ROOT / 'site/native/dependencies.json').read_text())
    sources = [(dds / path, name) for path, name in FILES]
    if (spec['schema'] != 'kotoba.site.native-css-source-dependencies.v1' or
        any(sha(src) != spec['ddsBase']['files'][path] for (path, _), (src, _) in zip(FILES, sources)) or
        deps['dependencies'][0]['sha256'] != sha(sha_source) or spec['cssSheet']['sha256'] != sha(css_source) or spec['jsonScalar']['sha256'] != sha(json_source) or spec['syntaxTheme']['sha256'] != sha(theme_json) or spec['themeSource']['sha256'] != sha(dds / 'src/jp_go_dds/theme_toggle.cljk') or json.loads((ROOT / 'site/native/metadata-sources.json').read_text())['files']['site/generate.cljk'] != sha(ROOT / 'site/generate.cljk')):
        raise ValueError('source pin mismatch')
    html_spec = json.loads((ROOT / 'site/native/html-dependencies.json').read_text())
    header_spec = json.loads((ROOT / 'site/native/header-dependencies.json').read_text())
    dds_spec = json.loads((ROOT / 'site/native/dds-dependencies.json').read_text())
    extras = [(html_core, 'src/html_core.kotoba', html_spec['dependencies'][0]['sha256']),
              (html_hiccup, 'src/html_hiccup.kotoba', html_spec['dependencies'][1]['sha256']),
              (button, 'src/button.kotoba', header_spec['sha256']), (theme, 'src/theme_toggle.kotoba', header_spec['theme']['sha256']),
              (selector, 'src/language_selector.kotoba', header_spec['selector']['sha256']),
              (components, 'src/components.kotoba', dds_spec['dependencies'][0]['sha256']),
              (table, 'src/table.kotoba', dds_spec['dependencies'][1]['sha256'])]
    if any(sha(p) != digest for p, _, digest in extras): raise ValueError('HTML source drift')
    for binary in (compiler, packager):
        subprocess.run(['/usr/bin/codesign', '--verify', '--strict', str(binary)], check=True, capture_output=True, timeout=30)
    references = [(tag, route, ROOT / 'site/dist' / ('' if tag == 'en' else tag) / path / 'index.html') for tag in TAGS for route, path in (('infra', 'blog/infra-cost-measured-path'), ('security', 'blog/csf2-attack-graphs'))]
    tracked = [ROOT / 'site/native/reference_translation.py', *[p for p, _, _ in extras], ROOT / 'site/native/html-dependencies.json', ROOT / 'site/native/header-dependencies.json', ROOT / 'site/native/dds-dependencies.json', *[ROOT / 'site/i18n' / (t + '.json') for t in TAGS], *[p for _, _, p in references], ROOT / 'site/infra-cost-article.edn', ROOT / 'site/security-article.edn', compiler, packager, sha_source, json_source, theme_json, dds / 'src/jp_go_dds/theme_toggle.cljk', ROOT / 'site/generate.cljk', ROOT / 'site/native/metadata-sources.json', *[p for p, _ in sources], PINS, ROOT / 'site/native/dependencies.json',
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
                              (ROOT / 'test/site/native/article_page_file.kotoba', 'src/site/native/article_page_file.kotoba'),
               (sha_source, 'src/multiformats/sha256.kotoba')]
    modules += [(p, path) for p, path, _ in extras]
    modules += [(ROOT / 'site/native' / (n + '.kotoba'), 'src/site/native/' + n + '.kotoba') for n in ['article_head', 'header_scripts', 'page_metadata', 'article_body', 'article_section', 'footer', 'header', 'header_inline', 'header_menu', 'header_theme', 'header_selector', 'locale_mark', 'locale_tree', 'locale_rebase', 'locale_links', 'locale_filter', 'locale_message', 'locale_catalog', 'locale_placeholders']]
    tracked += [p for p, _ in modules]
    before = {str(p): sha(p) for p in tracked}
    out = a.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    for src, name in modules:
        dst = out / name; dst.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(src, dst)
    for src, name in sources: shutil.copyfile(src, out / name)
    shutil.copyfile(dds / 'src/jp_go_dds/theme_toggle.cljk', out / 'theme-source.cljk')
    shutil.copyfile(ROOT / 'site/generate.cljk', out / 'generator.cljk')
    shutil.copyfile(theme_json, out / 'theme.json')
    (out / 'site/i18n').mkdir(parents=True, exist_ok=True)
    for tag in TAGS: shutil.copyfile(ROOT / 'site/i18n' / (tag + '.json'), out / 'site/i18n' / (tag + '.json'))
    for name in ('infra-cost-article.edn', 'security-article.edn'): shutil.copyfile(ROOT / 'site' / name, out / 'site' / name)
    lock = out / 'project.ksl'
    lock.write_text('KSL1\nentry /src/site/native/article_page_file.kotoba\nroot /src\n' +
                    ''.join('file ' + sha(out / name) + ' /' + name + '\n' for _, name in sorted(modules, key=lambda x: x[1])))
    fd = os.open(out, os.O_RDONLY | os.O_DIRECTORY); saved = None
    try:
        if fd != 3:
            try: saved = os.dup(3)
            except OSError: pass
            os.dup2(fd, 3)
        def run(image, *args, expected_exit=0):
            p = subprocess.run([str(image), *args], cwd=out, env={'PATH': '/nonexistent', 'LANG': 'C'},
                               pass_fds=(3,), capture_output=True, timeout=900)
            if p.returncode != expected_exit or p.stderr: raise RuntimeError(('native command', args, p.returncode, p.stderr[-500:]))
            return p.stdout
        run(compiler, 'compile', '/src/site/native/article_page_file.kotoba', '--target', 'aarch64-macos',
            '--output', 'base.kseed', '--source-path', '/src', '--source-lock', 'project.ksl', '--source-lock-sha256', sha(lock))
        info = run(compiler, 'extract-native', 'base.kseed', '--symbol', 'main', '--output', 'base.raw')
        m = re.search(rb':offset (\d+), :length (\d+), :arity 0}', info)
        if not m or int(m[2]) != (out / 'base.raw').stat().st_size: raise ValueError('bad entry metadata')
        run(packager, 'base-unsigned.macho', 'base.raw', 'fs2-cli-stdio-general', m[1].decode())
        binary = out / 'base.macho'; shutil.copyfile(out / 'base-unsigned.macho', binary); binary.chmod(0o700)
        subprocess.run(['/usr/bin/codesign', '--force', '--sign', '-', str(binary)], check=True, capture_output=True, timeout=30)
        def remove_old_freebuff(node):
            if not isinstance(node, list): return
            node[2][:] = [item for item in node[2] if not (isinstance(item, list) and item[0] == 'script' and any(isinstance(x, str) and 'window.freebuff' in x for x in item[2]))]
            for item in node[2]: remove_old_freebuff(item)
        def check(case):
            tag, route, reference = case
            result = run(binary, route, tag, expected_exit=42)
            if b'freebuff' in result.lower() or b'bfcid' in result.lower(): raise ValueError('telemetry in generated page')
            actual = Tree(); actual.feed(result.decode()); actual.close()
            expected = Tree(); expected.feed(reference_title(reference.read_text(), tag).replace('site/generate.cljk', 'site/native/article_body.kotoba')); expected.close()
            remove_old_freebuff(expected.root)
            if len(actual.stack) != 1 or normalize(actual.root) != normalize(expected.root): raise ValueError(('complete article DOM differs', route, tag))
            print(route, tag, 'page pass', flush=True)
            return {'route': route, 'tag': tag, 'sha256': hashlib.sha256(result).hexdigest()}
        with ThreadPoolExecutor(max_workers=3) as pool:
            results = list(pool.map(check, references))
        run(binary, 'unknown', 'en', expected_exit=7)
        run(binary, 'infra', '../ja', expected_exit=7)
        original = (out / 'tokens.cljk').read_bytes(); (out / 'tokens.cljk').write_bytes(original + b' ')
        run(binary, 'infra', 'en', expected_exit=7)
    finally:
        if fd != 3:
            if saved is None: os.close(3)
            else: os.dup2(saved, 3); os.close(saved)
        os.close(fd)
    if any(sha(p) != before[str(p)] for p in tracked): raise ValueError('source changed during gate')
    receipt = {'status':'PASS_NATIVE_MULTILINGUAL_ARTICLE_PAGE', 'inputs':before, 'binarySha256':sha(binary),
               'sourceLockSha256':sha(lock), 'observations': results, 'invalidRouteRefused':True, 'invalidTagRefused':True, 'freebuffAbsent':True, 'modifiedInputRefused':True,
               'darkRulesGenerated':True, 'completeStylesheetGenerated':True, 'completeArticlePageGenerated':True, 'siteGenerated':False, 'publicationQualified':False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n'); print(receipt['status'])
if __name__ == '__main__': main()
