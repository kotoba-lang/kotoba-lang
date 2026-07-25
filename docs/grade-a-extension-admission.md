# Grade A `.kotoba` extension admission

The workspace extension inventory is closed over every discovered `.kotoba`
path. Each entry contains a SHA-256 content digest and exactly one admission
state:

- `:canonical-verified` or `:canonical-fixture-verified`; or
- a typed exception whose `:canonical-admission` is unconditionally `:deny`.

Typed exceptions bind the artifact kind, repository owner, replacement
extension, and migration track. Schema DSL and archived-language collisions
cannot be interpreted as canonical source. Newly discovered or changed
candidate source is quarantined until its verification digest is recorded.
An exception documents remediation; it never grants execution.

Current evidence covers 1,082 paths: 224 verified canonical files and 858
typed denials, including all 792 known extension collisions.

```sh
bb scripts/generate-q9-kotoba-extension-audit.bb
clojure -M:extension-audit
clojure -M:test -n kotoba.lang.extension-audit-test
```

The verifier rejects missing files, digest drift, duplicate paths, incomplete
exceptions, owner-scope mismatch, accidental canonical admission, and
exceptions attached to verified canonical source.
