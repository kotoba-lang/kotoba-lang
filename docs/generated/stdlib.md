# Generated Kotoba standard-library reference

> Generated from [`lang/conformance/stdlib/manifest.edn`](../../lang/conformance/stdlib/manifest.edn). Do not edit by hand.

The bounded public module list is `frozen`. Adding a public name requires a manifest/version change and conformance evidence.

## `core`

Source: [`lang/stdlib/core.kotoba`](../../lang/stdlib/core.kotoba).

Records: `Err`, `None`, `Ok`, `Some`.

Public names: `comp2`, `concat`, `err`, `err?`, `every?`, `find`, `group-by`, `merge`, `ok`, `ok?`, `option-none`, `option-none?`, `option-some`, `option-some?`, `option-value`, `partial1`, `range`, `range-step`, `reverse`, `reverse-into`, `select-keys`, `some`, `stdlib-binary-closure-anchor`, `unwrap-err`, `unwrap-ok`, `update`, `zipmap`.

## Language built-ins

String operations: `string-byte-length`, `string-code-point-at`, `string-concat`, `string-contains?`, `string-fold-case`, `string-from-i64`, `string-join`, `string-length`, `string-substring`, `string-upper`, `string=?`.

Option sugar: `if-some`, `match-option`, `when-some`.

These built-ins are not ambiently prelude-loaded; admission is controlled by the cited language authorities.
