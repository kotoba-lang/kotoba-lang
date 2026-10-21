# Portable public library import contract

Use the language repository's immutable revision with `:deps/root
"lib/library-import"` to depend on `kotoba.lang.library-import` on a source
script host. This profile points to the authoritative source under `lib/library-import/src/` and
declares its complete portable dependency closure (`kotoba.lang.text`). It
does not pull the full language repository's JVM/compiler test/runtime graph.
The full language CLI uses the same namespace and source bytes.

The profile also distributes `kotoba.lang.naming` and the normative Kotoba ADL
resource `kotoba/spec/naming.kotoba`. See [Kotoba Spec: naming](../../docs/lang/naming.md).
Library IDs delegate to the shared validator; source authoring conventions do
not narrow the compatibility reader or rewrite existing aliases.
