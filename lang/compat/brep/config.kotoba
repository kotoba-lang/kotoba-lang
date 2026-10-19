(ns brep.config
  "EDN-authority config for tessellation LOD constants and epsilon
  thresholds used by `brep.kernel` and `brep.tessellate` (previously
  hardcoded magic numbers scattered through the Rust `tessellate` /
  `brep` modules, kept as bare literals in the initial CLJC restoration).

  Authority: `resources/brep/tessellation.edn`. This namespace holds a
  portable (cljs/SCI-safe, no classpath-resource I/O) literal mirror of
  that resource; `brep.config-loader` (JVM-only `.clj`, used from tests)
  verifies the two stay byte-for-byte in sync — see
  `brep-test/edn-matches-resource`.

  No network, no I/O.")

(def config
  {:cylinder {:segments 24
              :default-height 1.0}
   :sphere {:u-segments 16
            :v-segments 12}
   :epsilon {:point-merge 1e-12
             :knot-denominator 1e-14}})

(def cylinder-segments (get-in config [:cylinder :segments]))
(def cylinder-default-height (get-in config [:cylinder :default-height]))
(def sphere-u-segments (get-in config [:sphere :u-segments]))
(def sphere-v-segments (get-in config [:sphere :v-segments]))
(def epsilon-point-merge (get-in config [:epsilon :point-merge]))
(def epsilon-knot-denominator (get-in config [:epsilon :knot-denominator]))
