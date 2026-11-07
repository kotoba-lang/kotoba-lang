# Oracle staging shim: run `.cljk` sources as `.cljc` (JUN-579)

Owner decision 2026-11-07: `.cljk` stays as a reference oracle source and is treated as `.cljc` for oracle runs.
JVM Clojure, Babashka, Basilisp and the other `.cljc` readers do not read the `.cljk` extension, so
`scripts/oracle-stage-cljc.py` stages a copy of a tree with the `.cljk` files renamed.

```bash
python3 -I scripts/oracle-stage-cljc.py SRC DEST
```

It is a byte stager. It copies every file unchanged, resolves no namespace, compiles nothing, runs nothing and
renders no verdict. It is a local one-off tool: not CI, not a gate, not a build or runtime step, and the native
route never depends on it. `DEST` must be new or empty and outside `SRC`. `DEST/ORACLE-STAGE.edn` records the date,
the source, its git commit when `SRC` is a checkout, and a sha256 for every renamed file.

Target extension, per file, in this order:

1. a name ending `.clj.cljk` or `.cljs.cljk` loses `.cljk` (the platform extension is already in the name);
2. the value recorded for the path in the nearest `cljk-origin.edn` (the extension the file had before the
   2026-09-11 rename), so a file that was `.cljs` becomes `.cljs` again and the JVM ignores it;
3. `.cljc`.

## Limits

- Only the staged tree is renamed. A dependency fetched by `:git/sha` lands in `~/.gitlibs` as `.cljk` and is not
  staged, so a JVM or Babashka run works only for code whose dependencies are `:local/root` inside the staged tree
  (the monorepo `lib/` layout satisfies this).
- Reader conditionals are untouched. `#?(:kotoba …)` is ignored by the other readers, and `#?@` is not read by
  Kotoba, so shared source avoids it.
- Basilisp reads `.cljc` with the `:lpy` feature key; it was not run here.
- A passing run is reference evidence for that runtime only. It does not qualify the native route.

## First use (2026-11-07)

Tool versions: Clojure CLI 1.12.5.1654 (JDK 21.0.1), Babashka 1.12.218, Python 3.15.0, macOS arm64.

- Input: `lib/` of `kotoba-lang/kotoba` at `91ed14c090c90ceff833a868f1685c8840503f76`, taken with `git archive`
  (so the manifest has no commit). Result: 33291 files copied, 11294 renamed, 0 symlinks skipped, 0 name clashes.
- `kotoba.pathfind`, namespace `pathfind-test`, staged `.cljk` run as `.cljc`:
  - JVM: `clojure -M -e "(require 'pathfind-test) (clojure.test/run-tests 'pathfind-test)"` in the staged library
    directory: 2 tests, 4 assertions, 0 failures, 0 errors.
  - Babashka: `bb -cp "src:test:../kotoba.test/src:../kotoba.spec/src" -e "(require 'pathfind-test) (clojure.test/run-tests 'pathfind-test)"`:
    2 tests, 4 assertions, 0 failures, 0 errors.
- This is the first run of a `.cljk` library on a runtime other than the nbb engine. It covers one small library
  and says nothing about the rest.
