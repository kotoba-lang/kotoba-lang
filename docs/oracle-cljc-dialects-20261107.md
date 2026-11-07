# Oracle candidates: dialects that read `.cljc` (recorded 2026-11-07)

Owner decision 2026-11-07 (JUN-579): every language that supports `.cljc` is an
allowed local reference oracle, under the existing oracle rules (one-off, dated
evidence, never in CI, gates, builds, packaging or runtime paths, never a
fallback or a required comparison route).

## Status of this table

This table was pasted by the owner into the session on 2026-11-07. It is a
third-party summary (it cites the Clojure reader-conditionals guide, the
Basilisp docs, the jank book and clj-easy/clojure-dialects-docs). **Nothing in
it was verified by running the tools.** Treat each row as a lead, and confirm
the tool version, its reader feature key and its `.cljc` handling when the
oracle is first used, then record that evidence with the date.

| Dialect | Runtime | Reader feature | `.cljc` (as pasted) |
|---|---|---|---|
| Clojure | JVM | `:clj` | yes |
| ClojureScript | JavaScript | `:cljs` | yes |
| ClojureCLR | .NET | `:cljr` | yes |
| Basilisp | Python | `:lpy` | yes |
| Babashka | native / SCI | `:bb` / `:clj` | yes |
| Squint | JavaScript | `:cljs` family | yes |
| Cherry | JavaScript | `:cljs` | yes |
| ClojureDart | Dart / Flutter | `:cljd` | yes |
| jank | C++ / LLVM | `:jank` | yes |
| Clojerl | Erlang VM | `:clje` | yes |
| Lokke | Guile Scheme | `:cljl` | reader support only |
| Phel | PHP | `:phel` | implementation exists, needs verification |

## Cautions recorded with it

- Reading `.cljc` is not API compatibility. `clojure.core` coverage differs by
  dialect (Basilisp has unimplemented functions), so shared code is limited to
  what each dialect implements.
- The same feature branch does not always behave the same: Babashka also reads
  `:clj`, Squint and Cherry use `:cljs`.
- Macros run in different environments with different APIs, so the same source
  still needs a separate test per dialect.
- Kotoba's reader does not read `#?@`, so source meant for Kotoba and an oracle
  should avoid it (see `AGENTS.md`).
- The suggested starting set was Clojure, ClojureScript and Basilisp, with jank
  and ClojureDart added later to find incompatibilities step by step.

## Relation to the CDCI rule

Babashka, GraalVM-based tools and any JVM tool stay out of CI and gates as
before. Being on this list permits a developer to run them once by hand to
obtain a reference value; it does not permit a workflow to start them.
