(ns vrm.expression
  "ExpressionManager — resolve VRM expression weights into the concrete
  per-frame changes the renderer applies: morph-target weights,
  material-colour overrides, UV transforms, and the blink/lookAt/mouth
  override state. Restored from `kami-vrm/src/expression.rs`
  (kotoba-lang/kami-engine, deleted PR #82) as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root).

  `kami-vrm` already *parses* expressions (morph/material/UV binds +
  override flags, see `vrm.parse`); this is the runtime applier (the
  `@pixiv/three-vrm` `VRMExpressionManager` analogue). Given a set of
  expression weights (`{name weight}`), it accumulates the binds and applies
  VRM 1.0 override semantics so e.g. a `happy` expression that `:block`s
  blink suppresses the blink track this frame.")

(def ^:private blink-presets #{:blink :blink-left :blink-right})
(def ^:private lookat-presets #{:look-up :look-down :look-left :look-right})
(def ^:private mouth-presets #{:aa :ih :ou :ee :oh})

(defn- is-blink? [p] (contains? blink-presets p))
(defn- is-lookat? [p] (contains? lookat-presets p))
(defn- is-mouth? [p] (contains? mouth-presets p))

(defn new-manager
  "Build an ExpressionManager (`{:expressions [...]}`) from parsed
  expressions (`vrm.vrm-types/vrm-expression` maps)."
  [expressions]
  {:expressions (vec expressions)})

(defn- find-expr [mgr name] (some #(when (= (:name %) name) %) (:expressions mgr)))

(defn- apply-override [factor ov w]
  (cond
    (nil? ov) factor
    (= ov :block) (if (> w 0.0) 0.0 factor)
    (= ov :blend) (* factor (- 1.0 (max 0.0 (min 1.0 w))))
    :else factor))

(defn- override-factors
  "`[blink-factor lookat-factor mouth-factor]` from active override binds."
  [mgr weights]
  (reduce
   (fn [[bf lf mf] [name w]]
     (if (or (<= w 0.0) (not (find-expr mgr name)))
       [bf lf mf]
       (let [e (find-expr mgr name)]
         [(apply-override bf (:override-blink e) w)
          (apply-override lf (:override-look-at e) w)
          (apply-override mf (:override-mouth e) w)])))
   [1.0 1.0 1.0]
   weights))

(defn- accum-morph [acc w b]
  (update acc [(:mesh-index b) (:morph-index b)] (fnil + 0.0) (* w (:weight b))))

(defn- accum-color [acc w b]
  (update acc [(:material-index b) (:property b)]
          (fn [entry]
            (let [[sum tw] (or entry [[0.0 0.0 0.0 0.0] 0.0])]
              [(mapv + sum (mapv #(* % w) (:target-value b))) (+ tw w)]))))

(defn- accum-uv [acc w b]
  (update acc (:material-index b)
          (fn [entry]
            (let [[off scl tw] (or entry [[0.0 0.0] [0.0 0.0] 0.0])]
              [(mapv + off (mapv #(* % w) (:offset b)))
               (mapv + scl (mapv #(* % w) (:scale b)))
               (+ tw w)]))))

(defn- effective-weight [e raw blink-factor lookat-factor mouth-factor]
  (let [w (max 0.0 (min 1.0 raw))
        w (cond (is-blink? (:preset e)) (* w blink-factor)
                (is-lookat? (:preset e)) (* w lookat-factor)
                (is-mouth? (:preset e)) (* w mouth-factor)
                :else w)]
    (if (:is-binary e) (if (>= w 0.5) 1.0 0.0) w)))

(defn resolve-expression
  "Resolve `weights` (`{expression-name weight-in-[0,1]}`) into the
  per-frame changes: `{:morphs {[mesh-idx morph-idx] weight}
  :material-colors {[mat-idx property] {:target [r g b a] :weight w}}
  :uv-transforms {mat-idx {:offset [.. ] :scale [..] :weight w}} :blink-factor
  :lookat-factor :mouth-factor}`."
  [mgr weights]
  (let [[blink-factor lookat-factor mouth-factor] (override-factors mgr weights)
        acc0 {:morphs {} :color-acc {} :uv-acc {}}
        {:keys [morphs color-acc uv-acc]}
        (reduce
         (fn [acc [name raw]]
           (if-let [e (find-expr mgr name)]
             (let [w (effective-weight e raw blink-factor lookat-factor mouth-factor)]
               (if (<= w 0.0)
                 acc
                 (-> acc
                     (update :morphs (fn [m] (reduce #(accum-morph %1 w %2) m (:morph-target-binds e))))
                     (update :color-acc (fn [m] (reduce #(accum-color %1 w %2) m (:material-color-binds e))))
                     (update :uv-acc (fn [m] (reduce #(accum-uv %1 w %2) m (:texture-transform-binds e)))))))
             acc))
         acc0
         weights)
        material-colors (into {}
                               (keep (fn [[k [sum tw]]]
                                       (when (> tw 0.0)
                                         [k {:target (mapv #(/ % tw) sum) :weight (min 1.0 tw)}])))
                               color-acc)
        uv-transforms (into {}
                             (keep (fn [[k [off scl tw]]]
                                     (when (> tw 0.0)
                                       [k {:offset (mapv #(/ % tw) off) :scale (mapv #(/ % tw) scl) :weight (min 1.0 tw)}])))
                             uv-acc)]
    {:morphs morphs
     :material-colors material-colors
     :uv-transforms uv-transforms
     :blink-factor blink-factor
     :lookat-factor lookat-factor
     :mouth-factor mouth-factor}))
