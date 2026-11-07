#!/usr/bin/env python3
"""Observe the unchanged native page/catalog predicates on a Linux candidate."""
import argparse
import json
from pathlib import Path
from build_support import ROOT, TAGS, sha, native_run, write_bytes
from check_pages import ROUTES
from linux_build import LinuxBuild


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for key in ('build', 'inputs', 'libraries', 'grammar'):
        p.add_argument('--'+key, type=Path, required=True)
    a = p.parse_args()
    build = a.build.resolve(strict=True)
    candidate = build/'candidate'
    receipt = json.loads((build/'receipt.json').read_text())
    before = {str(p.relative_to(candidate)):sha(p) for p in candidate.rglob('*') if p.is_file()}
    if before != receipt['files']:
        raise ValueError('candidate drift before native qualification')
    b = LinuxBuild(build/'linux-gates', a.inputs, a.libraries, a.grammar)
    def recipe(entry, modules):
        return dict(modules=[*modules,*(['input'] if entry!='locale_pages_test' else [])], entries=['src/site/native/'+entry+'.kotoba'], aliases=[])
    b.recipes = {
      'page-unit':recipe('locale_pages_test',['locale_pages']),
      'page-negative':recipe('locale_pages_test',['locale_pages']),
      'page-file':recipe('locale_pages_file',['locale_pages']),
      'catalog-file':recipe('locale_catalog_file',['locale_catalog','locale_placeholders'])}
    programs = {}
    def compile(name):
        folder = b.compile(name)
        programs[name] = folder
        return folder
    def run(name, *argv, expected=42):
        folder = programs[name]
        result = native_run(folder/'entry.bin', folder, argv, expected=expected, timeout=180)
        if result:
            raise ValueError('unexpected native gate stdout')
    compile('page-unit')
    run('page-unit')
    source = (ROOT/'site/native/locale_pages.kotoba').read_text()
    needle = '(same-code? english html)'
    if source.count(needle) != 1:
        raise ValueError('page mutation anchor changed')
    b.source_overrides = {'src/site/native/locale_pages.kotoba':source.replace(needle,'true').encode()}
    compile('page-negative')
    run('page-negative',expected=7)
    b.source_overrides = {}
    pages = compile('page-file')
    observations = []
    for tag in TAGS:
        for route in ROUTES:
            path = ('' if tag=='en' else tag)+route+'index.html'
            path = path.lstrip('/')
            source = candidate/path
            if sha(source) != receipt['files'][path]:
                raise ValueError('candidate page drift')
            write_bytes(pages/'site/dist'/path, source.read_bytes())
    for tag in TAGS:
        for route in ROUTES:
            run('page-file', tag, route)
            observations.append(dict(tag=tag,route=route,result=42))
    run('page-file','../ja','/',expected=7)
    run('page-file','ja','/../',expected=7)
    target = pages/'site/dist/ja/index.html'
    original = target.read_bytes()
    if b'id="architecture"' not in original:
        raise ValueError('missing landmark mutation anchor')
    target.write_bytes(original.replace(b'id="architecture"',b'id="removed-architecture"'))
    run('page-file','ja','/',expected=7)
    target.write_bytes(original)
    catalogs = compile('catalog-file')
    for tag in TAGS[1:]:
        run('catalog-file',tag)
    run('catalog-file','../ja',expected=7)
    target = catalogs/'site/i18n/ja.json'
    original = target.read_bytes()
    target.write_bytes(b'{}\n')
    run('catalog-file','ja',expected=7)
    english = catalogs/'site/i18n/en.json'
    original_english = english.read_bytes()
    small = b'{"'+b'a'*64+b'":"translated"}\n'
    english.write_bytes(small)
    target.write_bytes(small)
    run('catalog-file','ja',expected=7)
    english.write_bytes(original_english)
    target.write_bytes(original)
    b.finish()
    after = {str(p.relative_to(candidate)):sha(p) for p in candidate.rglob('*') if p.is_file()}
    if before != after:
        raise ValueError('native gates changed candidate')
    result = dict(status='PASS_NATIVE_SITE_VALIDATION',profile=b.profile,
        buildReceiptSha256=sha(build/'receipt.json'),pageChecks=len(observations),catalogChecks=16,
        unitAndMutationControls=True,invalidPathsRefused=True,missingLandmarkRefused=True,
        missingKeysRefused=True,smallSourceRefused=True,candidateUnchanged=True,
        programs={n:json.loads((f/'build.json').read_text()) for n,f in programs.items()},
        compilerSha256=sha(b.compiler),runnerSha256=sha(b.packager),published=False)
    (build/'native-validation.json').write_text(json.dumps(result,indent=2)+'\n')
    print(result['status'],result['pageChecks'],result['catalogChecks'],flush=True)


if __name__=='__main__':
    main()
