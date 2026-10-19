(ns kotoba.cartpole-math
  "Facade re-exporting `kami.cartpole-math` (SSoT, ADR-2607102200 addendum 9).

  ## Why this file exists again (ADR-2608133600, com-junkawasaki/root)

  Deleted on 2026-07-20 by `7c6cb307` (\"Migrate cartpole physics fully to
  Kotoba\"), which put `src/kotoba/cartpole_math.kotoba` at this path. `.kotoba` is
  not loadable by any Clojure runtime, so that removed the namespace rather than
  moving it. `src/kotoba/cartpole_math.kotoba` stays the SEMANTIC AUTHORITY for the
  physics and is not reverted; this facade and `kami.cartpole-math` behind it are
  the LOAD PATH, held to the authority by `test/cartpole_math_parity_test.clj`.

  The two surfaces are shaped differently and the parity test says how they line
  up: the guest exports four SCALAR component functions (`step-x`, `step-x-dot`,
  `step-theta`, `step-theta-dot`) because a bounded typed ABI carries `:f64`, while
  this namespace's `step` returns the assembled `[x x-dot theta theta-dot]` vector.
  `output-bytes` / `output-hash` have no guest counterpart at all — see
  `kami.cartpole-math`.

  Removal condition: when consumers have a load path that does not need a `.cljc`
  (for the native route, ADR-2607279200 W4)."
  (:require [kami.cartpole-math :as impl]))

(def clamp           impl/clamp)
(def step            impl/step)
(def canonical-input impl/canonical-input)
(def canonical-step  impl/canonical-step)
(def output-bytes    impl/output-bytes)
(def output-hash     impl/output-hash)
