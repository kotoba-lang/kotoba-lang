(ns kotoba.compiler.cljs-i64
  "Shared i64-as-JS-`bigint` helpers used ONLY by the `:cljs` branches of
  `kotoba.compiler.frontend`/`ir`/`backend.wasm`. Exists because plain cljs
  numbers are IEEE-754 doubles (silently lose precision above 2^53) and cljs
  bitwise ops (`bit-and`/`bit-shift-right`/etc.) coerce to JS int32 (silently
  truncate above 2^31) -- neither matches this compiler's normative i64
  wraparound semantics (`kotoba.compiler.ir/execute`'s own docstring: '...
  wrap modulo 2^64'), which the compile-time oracle in `ir/lower` depends on
  to constant-fold a pure `main` byte-for-byte identically to the JVM
  (`Long`-based) path. JS `bigint` has exact, native, unbounded-precision
  integer arithmetic plus a built-in two's-complement wraparound primitive
  (`BigInt.asIntN`), so every actual `.kotoba` VALUE (literals, arithmetic
  results, pair handles, function args/returns) is represented as a `bigint`
  on this side -- never a plain cljs number. Interpreter-internal bookkeeping
  that is never itself a `.kotoba` value (fuel counters, heap array indices)
  stays a plain number; see call sites for the exact boundary.

  `zero?`/`neg?`/`pos?`/`integer?` do NOT reliably recognize `bigint` in this
  cljs runtime (confirmed live: `(zero? (js/BigInt 0))` => false) -- use
  `k-zero?`/`k-neg?`/`k-pos?`/`bigint-value?` below instead of the cljs.core
  versions on any value that came from this namespace.")

(def min-i64 (js/BigInt "-9223372036854775808"))
(def max-i64 (js/BigInt "9223372036854775807"))
(def zero (js/BigInt 0))
(def one (js/BigInt 1))

(defn bigint-value? [x]
  (boolean (and (some? x) (try (= (.-constructor x) js/BigInt) (catch :default _ false)))))

(defn k-zero? [x] (= x zero))
(defn k-neg? [x] (< x zero))
(defn k-pos? [x] (> x zero))

(defn in-i64-range? [x] (and (<= min-i64 x) (<= x max-i64)))

(defn wrap-i64
  "Two's-complement wraparound to the signed 64-bit range -- the cljs
  equivalent of the JVM path's `unchecked-add`/`unchecked-subtract`/
  `unchecked-multiply` on `long`, both being 'modulo 2^64' by construction."
  [x]
  (js/BigInt.asIntN 64 x))

(defn ashr
  "Sign-preserving (arithmetic) right shift of bigint N by SHIFT bits (a
  plain, small, non-negative number constant) -- `sleb128`'s own encoding
  loop needs this for the i64.const backend (`kotoba.compiler.backend.wasm`).
  cljs's `bit-shift-right` throws on bigint input directly ('Cannot mix
  BigInt and other types', confirmed live -- it coerces through a plain
  int32 path internally), and JS bigint has no unsigned right-shift operator
  at all (`>>>` is specifically undefined for bigint), so this is
  implemented via floor-division rather than a native shift: JS `/` on
  bigint truncates toward zero (confirmed live), which only equals
  arithmetic shift-right for non-negative dividends or exact multiples of
  2^SHIFT -- the one-line adjustment below (subtract 1 from the truncated
  quotient when the remainder is nonzero and N is negative) converts
  truncating division into floor division, which IS equivalent to
  arithmetic right shift for a positive power-of-two divisor."
  [n shift]
  (let [d (js/BigInt (bit-shift-left 1 shift))
        q (/ n d)
        r (- n (* q d))]
    (if (and (not= r zero) (k-neg? n)) (- q one) q)))

(defn ->bigint
  "Coerces a `.kotoba` integer VALUE to `bigint`, whether it already is one
  (a literal from `kotoba.compiler.kotoba-reader`) or is still a plain cljs
  number (a `0`/`1`/etc. synthesized directly by `kotoba.compiler.frontend`'s
  desugaring, e.g. `desugar-and`'s vacuous `1`, `when`'s trailing `0`, `get`'s
  default `0` -- these are written as ordinary Clojure literals in the
  compiler's OWN source, not read from `.kotoba` source, so they never pass
  through the reader's bigint-producing path). Every other i64 arithmetic/
  comparison helper in this namespace assumes its inputs already went
  through this once; `kotoba.compiler.ir`'s `eval-expr` calls it at the one
  point a literal enters the value stream, so the invariant holds
  everywhere downstream."
  [x]
  (if (bigint-value? x) x (js/BigInt x)))
