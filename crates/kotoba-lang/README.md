# kotoba-lang

`kotoba-lang` is the canonical Kotoba source profile and compatibility contract.
It is not a compiler or runtime.

It owns the stable surface that multiple tools must agree on:

- accepted source extensions: `.kotoba`, `.clj`, `.cljc`, `.cljs`
- reader targets: `kotoba`, `clj`, `cljs`
- `.cljc` reader conditional branch order
- namespace source resolution extension priority
- a machine-readable profile manifest at
  `resources/kotoba/lang/profile.edn`
- conformance fixtures at `resources/kotoba/lang/conformance/`

The user-facing compiler surface is `kotoba -e` and `kotoba wasm ...`.
Those commands compile this profile as a Kotoba/EDN-subset to WebAssembly.
`kotoba-clj` remains the implementation crate and compatibility binary.

New Kotoba-only source should use `.kotoba`; portable `.cljc` is for shared
Clojure-family code, with Kotoba-specific behavior behind `#?(:kotoba ...)`
reader conditionals. Profile gates treat public `kotoba` output as canonical
and verify that `.kotoba` namespace sources win over `.clj` compatibility
sources for the `kotoba` reader target.
