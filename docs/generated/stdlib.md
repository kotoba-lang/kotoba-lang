# Generated Kotoba standard-library reference

> Generated from [`lang/conformance/stdlib/manifest.edn`](../../lang/conformance/stdlib/manifest.edn). Do not edit by hand.

The bounded public module list is `frozen`. Adding a public name requires a manifest/version change and conformance evidence.

## `core`

Source: [`lang/stdlib/core.kotoba`](../../lang/stdlib/core.kotoba).

Records: .

Public names: `comp2`, `concat`, `distinct`, `drop-while`, `every?`, `first-match`, `frequencies`, `get-in2`, `interpose`, `juxt2`, `keep`, `mapv`, `merge`, `partial1`, `partition`, `range`, `range-step`, `remove`, `reverse`, `reverse-into`, `sort`, `sort-by`, `stdlib-binary-closure-anchor`, `take-while`, `zipmap`.

## Language built-ins

String operations: `string-byte-length`, `string-code-point-at`, `string-concat`, `string-contains?`, `string-fold-case`, `string-from-i64`, `string-join`, `string-length`, `string-substring`, `string-upper`, `string=?`.

Option sugar: `if-some`, `match-option`, `when-some`.

These built-ins are not ambiently prelude-loaded; admission is controlled by the cited language authorities.
