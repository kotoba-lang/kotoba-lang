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
from check_home_opening_chapters import chapters
from check_home_closing_chapter import chapters as closing_chapter
from check_home_try_prefix import chapter_rules, nav as code_nav
from check_home_start import nav as start_nav
from concurrent.futures import ThreadPoolExecutor
from reference_translation import reference_title

ROOT = Path(__file__).resolve().parents[2]
PINS = ROOT / 'site/native/css-source-dependencies.json'
TAGS = 'en ar arz bn de es fr hi id ja ko mr pcm pt ru ur zh-Hans'.split()
FILES = [('resources/jp_go_dds/dds.css','dds.css'), ('resources/jp_go_dds/components/language-selector.css','language.css'), ('resources/jp_go_dds/components/menu-list-box.css','box.css'), ('resources/jp_go_dds/components/menu-list.css','menu.css'), ('src/jp_go_dds/behavior.cljk','behavior.cljk'), ('src/jp_go_dds/core.cljk','core.cljk'), ('src/jp_go_dds/tokens.cljk','tokens.cljk')]

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('compiler', 'packager', 'sha256-source', 'dds-root', 'css-source', 'json-source', 'theme-json', 'html-core', 'html-hiccup', 'dds-button', 'dds-theme', 'dds-selector', 'dds-components', 'dds-table', 'grammar-source', 'tokenizer-source', 'output'):
        ap.add_argument('--' + name, type=Path, required=True)
    ap.add_argument('--tag', choices=TAGS, help='qualify only this locale; default checks all locales')
    a = ap.parse_args()
    selected_tags = [a.tag] if a.tag else TAGS
    if (platform.system(), platform.machine()) != ('Darwin', 'arm64'): raise ValueError('requires native macOS ARM64')
    compiler, packager, sha_source, dds, css_source, json_source, theme_json, html_core, html_hiccup, button, theme, selector, components, table, grammar, tokenizer = [p.resolve(strict=True) for p in (a.compiler, a.packager, a.sha256_source, a.dds_root, a.css_source, a.json_source, a.theme_json, a.html_core, a.html_hiccup, a.dds_button, a.dds_theme, a.dds_selector, a.dds_components, a.dds_table, a.grammar_source, a.tokenizer_source)]
    grammar_spec = json.loads((ROOT / 'site/native/highlight-source-dependencies.json').read_text())['grammar']
    if sha(grammar) != grammar_spec['catalogSha256'] or sha(tokenizer) != grammar_spec['tokenizerSha256']: raise ValueError('highlight source drift')
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
    references = [(tag, route, ROOT / 'site/dist' / ('' if tag == 'en' else tag) / path / 'index.html') for tag in selected_tags for route, path in (('infra', 'blog/infra-cost-measured-path'), ('security', 'blog/csf2-attack-graphs'))]
    tracked = [ROOT / 'site/native/reference_translation.py', *[p for p, _, _ in extras], ROOT / 'site/native/html-dependencies.json', ROOT / 'site/native/header-dependencies.json', ROOT / 'site/native/dds-dependencies.json', *[ROOT / 'site/i18n' / (t + '.json') for t in TAGS], *[p for _, _, p in references], ROOT / 'site/infra-cost-article.edn', ROOT / 'site/security-article.edn', ROOT / 'site/org-identity-article.edn', compiler, packager, sha_source, json_source, theme_json, dds / 'src/jp_go_dds/theme_toggle.cljk', ROOT / 'site/generate.cljk', ROOT / 'site/native/metadata-sources.json', *[p for p, _ in sources], PINS, ROOT / 'site/native/dependencies.json',
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
    modules += [(ROOT / 'site/native' / (n + '.kotoba'), 'src/site/native/' + n + '.kotoba') for n in ['home_try_prefix_page', 'home_try_prefix', 'home_start', 'home_code_play', 'home_highlight', 'home_play', 'home_closing_page', 'home_closing_chapter', 'home_opening_page', 'home_opening_chapters', 'home_contents', 'home_section', 'home_deliberate', 'site_command', 'sitemap_page', 'sitemap', 'article_page', 'article_head', 'header_scripts', 'page_metadata', 'article_body', 'article_section', 'footer', 'header', 'header_inline', 'header_menu', 'header_theme', 'header_selector', 'locale_mark', 'locale_tree', 'locale_rebase', 'locale_links', 'locale_filter', 'locale_message', 'locale_catalog', 'locale_placeholders']]
    authorities = [ROOT / 'lang' / name for name in ('stack-architecture.edn', 'typed-eval.edn', 'product-defaults.edn', 'surface-status.edn', 'docs-release.edn', 'wasm-component-platform.edn', 'elaboration-pipeline.edn')] + [ROOT / 'site/sponsorship.edn']
    tracked += [grammar, tokenizer, ROOT / 'site/native/highlight-source-dependencies.json', ROOT / 'site/src/kotoba/site/locales.cljk', ROOT / 'site/assets/play/double-21.kotoba', ROOT / 'site/assets/play/double-21.wasm', ROOT / 'site/assets/play/double-21.wasm.provenance.edn', ROOT / 'site/native/check_home_try_prefix.py', ROOT / 'site/native/check_home_start.py', ROOT / 'site/native/check_home_code_play.py']
    tracked += [p for p, _ in modules] + authorities + [ROOT / 'site/native/check_home_opening_chapters.py', ROOT / 'site/native/check_home_closing_chapter.py']
    pages = sorted((ROOT / 'site/dist').rglob('*.html'))
    tracked += pages + [ROOT / 'site/dist/sitemap.xml']
    before = {str(p): sha(p) for p in tracked}
    out = a.output.resolve(); out.mkdir(parents=True, exist_ok=False)
    for src, name in modules:
        dst = out / name; dst.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(src, dst)
    for src, name in sources: shutil.copyfile(src, out / name)
    shutil.copyfile(dds / 'src/jp_go_dds/theme_toggle.cljk', out / 'theme-source.cljk')
    shutil.copyfile(ROOT / 'site/generate.cljk', out / 'generator.cljk')
    for authority in authorities: shutil.copyfile(authority, out / authority.name)
    shutil.copyfile(theme_json, out / 'theme.json')
    for src, name in ((grammar, 'grammar.edn'), (tokenizer, 'highlight.cljk'), (ROOT / 'site/src/kotoba/site/locales.cljk', 'locales.cljk'), (ROOT / 'site/assets/play/double-21.kotoba', 'play.kotoba'), (ROOT / 'site/assets/play/double-21.wasm', 'play.wasm'), (ROOT / 'site/assets/play/double-21.wasm.provenance.edn', 'provenance.edn')): shutil.copyfile(src, out / name)
    (out / 'site/i18n').mkdir(parents=True, exist_ok=True)
    for tag in TAGS: shutil.copyfile(ROOT / 'site/i18n' / (tag + '.json'), out / 'site/i18n' / (tag + '.json'))
    for name in ('infra-cost-article.edn', 'security-article.edn', 'org-identity-article.edn'): shutil.copyfile(ROOT / 'site' / name, out / 'site' / name)
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
        opening = run(binary, 'opening', 'en', expected_exit=42)
        if chapters(opening.decode(), True) != chapters((ROOT / 'site/dist/index.html').read_text(), False):
            raise ValueError('shared command opening chapters differ')
        run(binary, 'opening', '../ja', expected_exit=7)
        closing = run(binary, 'closing', 'en', expected_exit=42)
        if closing_chapter(closing.decode(), True) != closing_chapter((ROOT / 'site/dist/index.html').read_text(), False):
            raise ValueError('shared command closing chapter differs')
        run(binary, 'closing', '../ja', expected_exit=7)
        def check_try_prefix(tag):
            result = run(binary, 'try-prefix', tag, expected_exit=42).decode()
            reference = (ROOT / 'site/dist' / ('' if tag == 'en' else tag) / 'index.html').read_text()
            cat = json.loads((ROOT / 'site/i18n' / (tag + '.json')).read_text())
            if chapter_rules(result) != chapter_rules(reference)[3:4] or any(start_nav(result, True, name) != start_nav(reference, False, name) for name in ('start', 'docs')) or code_nav(result, True, 'code', cat) != code_nav(reference, False, 'code', cat):
                raise ValueError(('shared command try prefix differs', tag))
            print('try-prefix', tag, 'pass', flush=True)
            return tag
        with ThreadPoolExecutor(max_workers=3) as pool:
            try_locales = list(pool.map(check_try_prefix, TAGS))
        run(binary, 'try-prefix', '../ja', expected_exit=7)
        demo = out / 'play.wasm'; original_demo = demo.read_bytes()
        try:
            demo.write_bytes(original_demo + b' ')
            run(binary, 'try-prefix', 'en', expected_exit=7)
        finally:
            demo.write_bytes(original_demo)
        identity = run(binary, 'identity', 'en', expected_exit=42)
        identity_text = identity.decode()
        for required in ('<title>Organization identity and verifiable membership on Kotoba Cloud</title>',
                         'href="https://kotoba-lang.org/spec/org-identity/" rel="canonical"',
                         'One source of truth per layer', 'Membership: W3C Verifiable Credentials with status',
                         'Current API surface', 'ADR-2609141633', 'Specification · Authentication &amp; organization identity'):
            if required not in identity_text: raise ValueError(('identity source content missing', required))
        if 'freebuff' in identity_text.lower() or re.search(r'<link\b[^>]*\bhreflang=', identity_text) or '/ja/spec/org-identity/' in identity_text:
            raise ValueError('identity page exposes telemetry or ungenerated translations')
        run(binary, 'identity', 'ja', expected_exit=7)
        # Stage existing HTML to test the shared sitemap command through the
        # same native entry. These are test inputs, not native-regenerated pages.
        manifest = []
        for i, source in enumerate(pages):
            target = out / 'sitemap-pages' / f'{i:04}.html'
            target.parent.mkdir(parents=True, exist_ok=True)
            if source == ROOT / 'site/dist/spec/org-identity/index.html': target.write_bytes(identity)
            else: shutil.copyfile(source, target)
            manifest.append('/sitemap-pages/' + target.name)
        (out / 'manifest.edn').write_text(json.dumps(manifest))
        old_urls = re.findall(r'<loc>([^<]+)</loc>', (ROOT / 'site/dist/sitemap.xml').read_text())
        if old_urls.count('https://kotoba-lang.org') != 1: raise ValueError('unexpected legacy blank-page canonical')
        urls = sorted(set(('https://kotoba-lang.org/spec/org-identity/' if x == 'https://kotoba-lang.org' else x) for x in old_urls))
        expected_sitemap = ('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
                            ''.join('<url><loc>' + x + '</loc></url>\n' for x in urls) + '</urlset>\n').encode()
        if run(binary, 'sitemap', expected_exit=42) != expected_sitemap: raise ValueError('corrected command sitemap differs')
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
    receipt = {'status':('PASS_NATIVE_ARTICLE_PAGE_LOCALE' if a.tag else 'PASS_NATIVE_MULTILINGUAL_ARTICLE_PAGE'), 'testedLocales':selected_tags, 'inputs':before, 'binarySha256':sha(binary),
               'sourceLockSha256':sha(lock), 'observations': results, 'invalidRouteRefused':True, 'invalidTagRefused':True, 'freebuffAbsent':True, 'modifiedInputRefused':True,
               'darkRulesGenerated':True, 'completeStylesheetGenerated':True, 'completeArticlePageGenerated':True, 'sharedTryPrefixCommandLocales':try_locales, 'sharedTryPrefixMutatedDemoRefused':True, 'sharedClosingCommandChecked':True, 'sharedOpeningCommandChecked':True, 'englishIdentitySourceChecked':True, 'identityMissingLocaleRefused':True, 'correctedIdentitySitemapChecked':True, 'sharedSitemapCommandChecked':True, 'existingSitemapPages':len(pages), 'siteGenerated':False, 'publicationQualified':False}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n'); print(receipt['status'])
if __name__ == '__main__': main()
