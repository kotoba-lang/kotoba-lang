(ns kotoba.compiler.backend.cljs
  "ADR-2607151500: lowers a KIR module to plain ClojureScript source TEXT --
  a new, genuinely separate execution target alongside the existing wasm32/
  x86_64/aarch64 backends. Unlike those, this backend does not assemble
  bytes; KIR's `:functions` bodies are already a small closed-form Lisp
  (let/if/cap-call/pair family/arithmetic/comparison/named calls, see
  ir.clj's `eval-expr`), so lowering is a 1:1 data->data rewrite from KIR's
  s-expression vocabulary to an equivalent, textually-emitted cljs
  s-expression vocabulary -- no byte-level encoding, no memory layout, no
  ABI concerns (a cljs runtime already has real heap-allocated persistent
  data structures, so `pair`/`pair-first`/`pair-second` become plain
  2-element vectors + `nth`, not a hand-rolled linear-memory heap).

  Two semantics KIR normatively defines that plain cljs forms do NOT
  reproduce for free, both handled explicitly here (silently accepting the
  cljs default would be a silent divergence from every other backend, not
  an equivalent target):
  - KIR's `if` treats exactly 0 as false, anything else (including
    negative numbers) as true -- cljs's `if` treats only nil/false as
    falsy, and 0 is truthy. Every emitted `if` wraps its test as
    `(if (zero? test) else then)`, matching ir.clj's own eval-expr exactly.
  - KIR's comparison ops (`= < > <= >=`) return the INTEGER 1 or 0 (so the
    result composes with `if`'s 0-is-false convention above), not a
    boolean -- every comparison is wrapped `(if (cljs-op ...) 1 0)`.

  One more real (documented, not silently smoothed over) gap against the
  wasm/native backends, an honest scope limit -- now narrowed from
  \"silently wrong\" to \"loudly fails\":
  - `ir.clj`'s `execute` docstring is explicit: \"Add/subtract/multiply
    wrap modulo 2^64\" -- true two's-complement i64 wraparound. Exact
    wraparound parity would need EVERY value (params, literals,
    intermediate results) represented as a JS BigInt end to end -- a much
    larger, invasive rewrite of this backend's whole numeric
    representation, not attempted here (no known safe-kotoba-subset
    program needs it). Instead, every `+`/`-`/`*` result is checked
    against JS's own safe-integer bound (`kotoba$check-safe-int`, 2^53-1)
    and THROWS `:arithmetic-overflow` rather than silently continuing with
    a value cljs's plain `number` (an IEEE-754 double) can no longer
    represent exactly -- the same fail-closed posture this backend already
    takes for fuel/division/capability, applied here too: an unreachable-
    today divergence stays unreachable by trapping loudly, not by
    producing a silently wrong result. The safe-integer bound is written
    as a portable numeric literal (not `js/Number.isSafeInteger`)
    specifically so this check evaluates identically whether the emitted
    source runs under real cljs or, as this backend's own committed test
    suite does, under plain JVM Clojure.
  `cap-call` (capability invocation): the emitted module exports
  `set-cap-dispatch!` (a fn [cap-id value] -> i64, installed via
  `kotoba$cap-dispatch`, a `defonce` atom) as its cljs-side equivalent of
  WASM's `kotoba:cap.call` host import -- the host calls
  `set-cap-dispatch!` before calling `main`, exactly the same \"host wires
  the boundary, host calls main\" shape as the wasm/native backends'
  memory-writing convention. No dispatcher installed means EVERY cap-call
  throws `:capability-denied` -- fail-closed, matching
  `kotoba-lang/kotoba`'s own `has-capability-fn` (\"no POLICY grants
  NOTHING\") rather than defaulting to permissive.

  `typed-cap-call` uses a separate exact provider registry installed through
  `set-typed-providers!`. Generated modules seal their capability contracts
  and schema table, validate structured function inputs and outputs, validate
  requests before provider dispatch and results after it, and deny every
  typed capability unless its numeric ID is explicitly allowed and installed.
  The codec implements the bounded record/variant/option/set/map/tuple profile
  needed by the application capability kits, including exact UTF-8 limits.

  Fuel: WASM's `charge!` is a MODULE-GLOBAL mutable counter that starts at
  512 and is never replenished for the life of one Instance (ir.clj: one
  charge per function ENTRY, not per expression -- confirmed by
  backend/wasm.clj's `global-section`, a `(global (mut i64) (i64.const
  512))` set once at instantiation). This backend reproduces the same
  global-depletion behavior with a namespace-level `defonce` atom, so a
  loaded cljs module is, like a WASM Instance, permanently exhausted after
  512 total function calls across its whole lifetime -- not 512 per
  top-level call."
  (:require [clojure.string :as str]
            [kotoba.compiler.reference-runtime :as reference-runtime]))

(declare lower-expr)

(def ^:private comparison-ops '#{= < > <= >=})
(def ^:private wraparound-arith-ops '#{+ - *})

(def ^:private i64-predicate-source
  "(defn- kotoba$i64-value? [value]
  #?(:clj (and (integer? value)
                (<= -9223372036854775808 value 9223372036854775807))
     :cljs (or (and (number? value) (js/Number.isSafeInteger value))
               (and (some? value)
                    (try (= (.-constructor value) js/BigInt)
                         (catch :default _ false))
                    (<= (js/BigInt \"-9223372036854775808\") value
                        (js/BigInt \"9223372036854775807\"))))))")

(def ^:private vector-index-source
  "(defn- kotoba$host-index [index]
  #?(:clj (when (integer? index) (long index))
     :cljs (let [host-index (if (number? index) index (js/Number index))]
             (when (js/Number.isSafeInteger host-index) host-index))))")

(defn- lower-expr [form]
  (cond
    (integer? form) form
    (or (string? form) (keyword? form) (boolean? form)) form
    (symbol? form) form
    :else
    (let [[op & args] form]
      (case op
        let (let [[bindings body] args]
              (list 'let (vec (mapcat (fn [[name value]] [name (lower-expr value)])
                                       (partition 2 bindings)))
                    (lower-expr body)))

        if (let [[test then else] args]
             (list 'if (list 'zero? (lower-expr test)) (lower-expr else) (lower-expr then)))

        ;; `do`: Clojure's own `do` sequences the lowered subexpressions.
        do (list* 'do (map lower-expr args))

        pair (let [[l r] args] (list 'vector (lower-expr l) (lower-expr r)))
        pair-first (list 'nth (lower-expr (first args)) 0)
        pair-second (list 'nth (lower-expr (first args)) 1)

        vector-new
        (list 'kotoba$typed-value! (list 'quote :vector-i64)
              (apply list 'vector (map lower-expr args)))

        vector-count
        (list 'kotoba$vector-count (lower-expr (first args)))

        vector-get
        (list 'kotoba$vector-get
              (lower-expr (first args))
              (lower-expr (second args))
              (list 'fn [] (lower-expr (nth args 2))))

        vector-at
        (list 'kotoba$vector-at
              (lower-expr (first args))
              (lower-expr (second args)))

        vector-drop
        (list 'kotoba$vector-drop
              (lower-expr (first args))
              (lower-expr (second args)))

        vector-take
        (list 'kotoba$vector-take
              (lower-expr (first args))
              (lower-expr (second args)))

        vector-assoc
        (list 'kotoba$vector-assoc
              (lower-expr (first args))
              (lower-expr (second args))
              (lower-expr (nth args 2)))

        vector-conj
        (list 'kotoba$vector-conj
              (lower-expr (first args))
              (lower-expr (second args)))

        hetero-vector-new
        (let [[type & items] args]
          (list 'kotoba$typed-value! (list 'quote type)
                (apply list 'vector (list 'quote type) (map lower-expr items))))

        hetero-vector-count
        (let [[type value] args]
          (list 'dec
                (list 'count
                      (list 'kotoba$typed-value! (list 'quote type)
                            (lower-expr value)))))

        hetero-vector-at
        (let [[type value index] args]
          (list 'nth
                (list 'kotoba$typed-value! (list 'quote type)
                      (lower-expr value))
                (inc index)))

        hetero-vector-assoc
        (let [[type value index item] args]
          (list 'kotoba$typed-value! (list 'quote type)
                (list 'assoc
                      (list 'kotoba$typed-value! (list 'quote type)
                            (lower-expr value))
                      (inc index)
                      (lower-expr item))))

        hetero-vector-equal
        (let [[type left right] args]
          (list 'if
                (list '= (list 'kotoba$typed-value! (list 'quote type)
                               (lower-expr left))
                      (list 'kotoba$typed-value! (list 'quote type)
                            (lower-expr right)))
                1 0))

        quot (list 'kotoba$quot (lower-expr (first args)) (lower-expr (second args)))

        ;; cap-id is always a literal integer in [0,255] here -- validated
        ;; by frontend.clj's validate-expr, never itself an expression (see
        ;; ir.clj's eval-expr, which never runs `cap-id` through eval-expr
        ;; either) -- so it passes through unchanged, only `value` recurses.
        cap-call (let [[cap-id value] args]
                   (list 'kotoba$cap-call cap-id (lower-expr value)))

        typed-cap-call
        (let [[cap-id request-type result-type request] args]
          (list 'kotoba$typed-cap-call cap-id
                (list 'quote request-type) (list 'quote result-type)
                (lower-expr request)))

        string-byte-length
        (list 'kotoba$utf8-byte-count (lower-expr (first args)))

        string=?
        (list 'if (list '= (lower-expr (first args)) (lower-expr (second args))) 1 0)

        string-concat
        (list 'kotoba$typed-value! (list 'quote :string)
              (list 'str (lower-expr (first args)) (lower-expr (second args))))

        string-substring
        (list 'kotoba$utf8-substring
              (lower-expr (first args))
              (lower-expr (second args))
              (lower-expr (nth args 2)))

        symbol
        (list 'kotoba$typed-value! (list 'quote :symbol)
              (list 'symbol (lower-expr (first args))))

        (cond
          (contains? comparison-ops op)
          (list 'if (apply list op (map lower-expr args)) 1 0)

          (contains? wraparound-arith-ops op)
          (list 'kotoba$check-safe-int (apply list op (map lower-expr args)))

          :else (apply list op (map lower-expr args)))))))

(defn- lower-function [{:keys [name params param-types result body]} exported?]
  (let [closure-dispatcher?
        (boolean (re-matches #"__kotoba_invoke(?:_[^$]+)?\$arity[0-4]"
                             (clojure.core/name name)))
        ;; A closure is an i64 handle in Wasm/KIR but a private two-word pair
        ;; in the host CLJS representation. Dispatcher entry is internal and
        ;; validates the handle by closed lambda id, so do not apply the public
        ;; scalar boundary validator to that first synthetic parameter.
        parameter-checks
        (->> (map vector params param-types (range))
             (keep (fn [[parameter type index]]
                     (when-not (and closure-dispatcher? (zero? index))
                       (list 'kotoba$typed-value! (list 'quote type) parameter))))
             vec)
        lowered-result (list 'kotoba$typed-value! (list 'quote result)
                             (lower-expr body))]
    (list (if exported? 'defn 'defn-) name (vec params)
          (list* 'do '(kotoba$charge!) (concat parameter-checks [lowered-result])))))

(def ^:private prelude-tail-forms
  "Everything in the prelude that does not depend on the declared budget."
  '[(defn- kotoba$charge! []
      (let [remaining (swap! kotoba$fuel dec)]
        (when (neg? remaining)
          (throw (ex-info "fuel-exhausted" {:kotoba.cljs/trap :fuel-exhausted})))))
    ;; 2^53 - 1, JS's own safe-integer bound -- written as a portable
    ;; numeric literal (not js/Number.isSafeInteger) so this evaluates
    ;; identically under real cljs or plain JVM Clojure (see this ns's
    ;; own docstring).
    (def ^:private kotoba$max-safe-integer 9007199254740991)
    (defn- kotoba$check-safe-int [n]
      (if (<= (- kotoba$max-safe-integer) n kotoba$max-safe-integer)
        n
        (throw (ex-info "arithmetic-overflow"
                        {:kotoba.cljs/trap :arithmetic-overflow :kotoba.cljs/value n}))))
    (defn- kotoba$quot [a b]
      (when (zero? b)
        (throw (ex-info "division-by-zero" {:kotoba.cljs/trap :division-by-zero})))
      (quot a b))
    (defonce kotoba$cap-dispatch (atom nil))
    (defn set-cap-dispatch!
      "Installs F (a fn [cap-id value] -> i64) as this module's capability
      host, this backend's equivalent of WASM's kotoba:cap.call host
      import. Call before `main` if the module uses cap-call. No
      dispatcher installed means every cap-call is denied (fail-closed)."
      [f]
      (reset! kotoba$cap-dispatch f))
    (defn- kotoba$cap-call [cap-id value]
      (if-let [f @kotoba$cap-dispatch]
        (long (f cap-id value))
        (throw (ex-info "capability-denied"
                        {:kotoba.cljs/trap :capability-denied
                         :kotoba.cljs/capability cap-id}))))
    (defonce kotoba$typed-host (atom {:allow #{} :providers {}}))
    (defn- kotoba$typed-fail! [message data]
      (throw (ex-info message (assoc data :kotoba.cljs/trap :typed-capability))))
    (defn- kotoba$utf8-byte-count [text]
      (loop [index 0 total 0]
        (if (= index (count text))
          total
          (let [unit (subs text index (inc index))]
            (cond
              (<= (compare unit "\u007f") 0)
              (recur (inc index) (inc total))
              (<= (compare unit "\u07ff") 0)
              (recur (inc index) (+ total 2))
              (and (<= (compare "\ud800" unit) 0)
                   (<= (compare unit "\udbff") 0))
              (if (< (inc index) (count text))
                (let [next-unit (subs text (inc index) (+ index 2))]
                  (if (and (<= (compare "\udc00" next-unit) 0)
                           (<= (compare next-unit "\udfff") 0))
                    (recur (+ index 2) (+ total 4))
                    (kotoba$typed-fail! "invalid-typed-value"
                                        {:reason :unpaired-surrogate})))
                (kotoba$typed-fail! "invalid-typed-value"
                                    {:reason :unpaired-surrogate}))
              (and (<= (compare "\udc00" unit) 0)
                   (<= (compare unit "\udfff") 0))
              (kotoba$typed-fail! "invalid-typed-value"
                                  {:reason :unpaired-surrogate})
              :else (recur (inc index) (+ total 3)))))))
    (defn- kotoba$utf8-substring [text start end]
      (let [length (kotoba$utf8-byte-count text)]
        (when-not (and (integer? start) (integer? end) (<= 0 start end length))
          (kotoba$typed-fail! "invalid-typed-operation"
                              {:reason :substring-bounds}))
        (loop [index 0 byte-index 0 boundaries {0 0}]
          (if (= index (count text))
            (let [from (get boundaries start) to (get boundaries end)]
              (when-not (and (some? from) (some? to))
                (kotoba$typed-fail! "invalid-typed-operation"
                                    {:reason :substring-code-point-boundary}))
              (subs text from to))
            (let [unit (subs text index (inc index))
                  [units bytes]
                  (cond
                    (<= (compare unit "\u007f") 0) [1 1]
                    (<= (compare unit "\u07ff") 0) [1 2]
                    (and (<= (compare "\ud800" unit) 0)
                         (<= (compare unit "\udbff") 0)) [2 4]
                    :else [1 3])]
              (recur (+ index units) (+ byte-index bytes)
                     (assoc boundaries (+ byte-index bytes) (+ index units))))))))
    (defn- kotoba$typed-value! [type value]
      (let [nodes (atom 0)]
        (letfn [(walk [descriptor item depth]
                  (swap! nodes inc)
                  (when (or (> depth 8) (> @nodes 64))
                    (kotoba$typed-fail! "invalid-typed-value" {:reason :value-bounds}))
                  (cond
                    (= descriptor :i64)
                    (when-not (kotoba$i64-value? item)
                      (kotoba$typed-fail! "invalid-typed-value" {:expected :i64}))
                    (or (= descriptor :f32) (= descriptor :f64))
                    (when-not (number? item)
                      (kotoba$typed-fail! "invalid-typed-value" {:expected descriptor}))
                    (= descriptor :string)
                    (when-not (and (string? item)
                                   (<= (kotoba$utf8-byte-count item) 65536))
                      (kotoba$typed-fail! "invalid-typed-value" {:expected :string}))
                    (= descriptor :keyword)
                    (when-not (and (keyword? item)
                                   (<= (kotoba$utf8-byte-count (str item)) 512))
                      (kotoba$typed-fail! "invalid-typed-value" {:expected :keyword}))
                    (= descriptor :symbol)
                    (when-not (and (symbol? item)
                                   (<= (kotoba$utf8-byte-count (str item)) 512))
                      (kotoba$typed-fail! "invalid-typed-value" {:expected :symbol}))
                    (= descriptor :bool)
                    (when-not (or (true? item) (false? item))
                      (kotoba$typed-fail! "invalid-typed-value" {:expected :bool}))
                    (= descriptor :vector-i64)
                    (when-not (and (vector? item)
                                   (<= (count item) 16384)
                                   (every? kotoba$i64-value? item))
                      (kotoba$typed-fail! "invalid-typed-value"
                                          {:expected :vector-i64}))
                    (and (vector? descriptor) (= :ref (first descriptor)))
                    (if-let [resolved (get kotoba$schemas (second descriptor))]
                      (walk resolved item (inc depth))
                      (kotoba$typed-fail! "invalid-typed-value" {:reason :unknown-schema}))
                    (and (vector? descriptor) (= :option (first descriptor)))
                    (let [[actual present? payload & extra] item]
                      (when-not (and (vector? item) (empty? extra)
                                     (= descriptor actual) (boolean? present?)
                                     (= (count item) (if present? 3 2)))
                        (kotoba$typed-fail! "invalid-typed-value" {:expected descriptor}))
                      (when present? (walk (second descriptor) payload (inc depth))))
                    (and (vector? descriptor) (= :set (first descriptor)))
                    (let [[actual items & extra] item]
                      (when-not (and (vector? item) (empty? extra) (= descriptor actual)
                                     (vector? items) (<= (count items) 32)
                                     (= (count items) (count (distinct items))))
                        (kotoba$typed-fail! "invalid-typed-value" {:expected descriptor}))
                      (doseq [member items]
                        (walk (second descriptor) member (inc depth))))
                    (and (vector? descriptor) (= :map (first descriptor)))
                    (let [[actual entries & extra] item]
                      (when-not (and (vector? item) (empty? extra) (= descriptor actual)
                                     (vector? entries) (<= (count entries) 31)
                                     (every? #(and (vector? %) (= 2 (count %))) entries)
                                     (= (count entries)
                                        (count (distinct (map first entries)))))
                        (kotoba$typed-fail! "invalid-typed-value" {:expected descriptor}))
                      (doseq [[key map-value] entries]
                        (walk (second descriptor) key (inc depth))
                        (walk (nth descriptor 2) map-value (inc depth))))
                    (and (vector? descriptor) (= :vector (first descriptor)))
                    (let [[actual & items] item
                          item-types (second descriptor)]
                      (when-not (and (vector? item) (= descriptor actual)
                                     (= (count items) (count item-types)))
                        (kotoba$typed-fail! "invalid-typed-value" {:expected descriptor}))
                      (doseq [[item-type member] (map vector item-types items)]
                        (walk item-type member (inc depth))))
                    (and (vector? descriptor) (= :record (first descriptor)))
                    (let [[actual & fields] item
                          field-types (map second (nth descriptor 2))]
                      (when-not (and (vector? item) (= descriptor actual)
                                     (= (count fields) (count field-types)))
                        (kotoba$typed-fail! "invalid-typed-value" {:expected descriptor}))
                      (doseq [[field-type field] (map vector field-types fields)]
                        (walk field-type field (inc depth))))
                    (and (vector? descriptor) (= :variant (first descriptor)))
                    (let [[actual tag payload & extra] item
                          payload-type (some (fn [[case-tag case-type]]
                                               (when (= case-tag tag) case-type))
                                             (nth descriptor 2))]
                      (when-not (and (vector? item) (empty? extra)
                                     (= descriptor actual) payload-type)
                        (kotoba$typed-fail! "invalid-typed-value" {:expected descriptor}))
                      (walk payload-type payload (inc depth)))
                    :else
                    (kotoba$typed-fail! "invalid-typed-value"
                                        {:reason :unknown-type :type descriptor})))]
          (walk type value 0)
          value)))
    (defn- kotoba$vector-count [items]
      (count (kotoba$typed-value! :vector-i64 items)))
    (defn- kotoba$vector-get [items index fallback]
      (let [items (kotoba$typed-value! :vector-i64 items)
            index (kotoba$host-index index)]
        (if (and (some? index) (<= 0 index) (< index (count items)))
          (nth items index)
          (fallback))))
    (defn- kotoba$vector-at [items index]
      (let [items (kotoba$typed-value! :vector-i64 items)
            index (kotoba$host-index index)]
        (when-not (and (some? index) (<= 0 index) (< index (count items)))
          (kotoba$typed-fail! "vector-index-out-of-range" {:index index}))
        (nth items index)))
    (defn- kotoba$vector-drop [items drop-count]
      (let [items (kotoba$typed-value! :vector-i64 items)
            drop-count (kotoba$host-index drop-count)]
        (when-not (and (some? drop-count)
                       (<= 0 drop-count (count items)))
          (kotoba$typed-fail! "vector-drop-out-of-range" {:count drop-count}))
        (subvec items drop-count)))
    (defn- kotoba$vector-take [items take-count]
      ;; The mirror of vector-drop: keeps the FIRST take-count items, same
      ;; range contract (0..count) and the same :vector-take-out-of-range
      ;; trap -- pop desugars to (vector-take v (- (vector-count v) 1)), so
      ;; an empty vector's -1 lands out of range here without a special case.
      (let [items (kotoba$typed-value! :vector-i64 items)
            take-count (kotoba$host-index take-count)]
        (when-not (and (some? take-count)
                       (<= 0 take-count (count items)))
          (kotoba$typed-fail! "vector-take-out-of-range" {:count take-count}))
        (subvec items 0 take-count)))
    (defn- kotoba$vector-assoc [items index item]
      (let [items (kotoba$typed-value! :vector-i64 items)
            index (kotoba$host-index index)]
        (when-not (and (some? index) (<= 0 index) (< index (count items)))
          (kotoba$typed-fail! "vector-index-out-of-range" {:index index}))
        (kotoba$typed-value! :i64 item)
        (assoc items index item)))
    (defn- kotoba$vector-conj [items item]
      (let [items (kotoba$typed-value! :vector-i64 items)]
        (when (>= (count items) 16384)
          (kotoba$typed-fail! "vector-too-large" {:limit 16384}))
        (kotoba$typed-value! :i64 item)
        (conj items item)))
    (defn set-typed-providers! [options]
      (when-not (and (map? options) (= #{:allow :providers} (set (keys options))))
        (kotoba$typed-fail! "typed provider options are not exact" {}))
      (let [{:keys [allow providers]} options]
        (when-not (and (set? allow)
                       (every? #(and (integer? %) (<= 0 % 255)) allow)
                       (map? providers) (<= (count providers) 256))
          (kotoba$typed-fail! "typed provider registry is invalid" {}))
        (doseq [[id provider] providers]
          (when-not (and (contains? allow id)
                         (= #{:request-type :result-type :invoke}
                            (set (keys provider)))
                         (ifn? (:invoke provider)))
            (kotoba$typed-fail! "typed provider is not exactly admitted"
                                {:capability id})))
        (doseq [[id contract] kotoba$typed-contracts]
          (when-let [provider (get providers id)]
            (when-not (= contract (select-keys provider [:request-type :result-type]))
              (kotoba$typed-fail! "typed provider contract mismatch"
                                  {:capability id}))))
        (reset! kotoba$typed-host options)))
    (defn- kotoba$typed-cap-call [id request-type result-type request]
      (kotoba$typed-value! request-type request)
      (let [{:keys [allow providers]} @kotoba$typed-host]
        (when-not (contains? allow id)
          (kotoba$typed-fail! "typed capability denied" {:capability id}))
        (let [provider (or (get providers id)
                           (kotoba$typed-fail! "typed capability provider is not installed"
                                               {:capability id}))]
          (when-not (= {:request-type request-type :result-type result-type}
                       (select-keys provider [:request-type :result-type]))
            (kotoba$typed-fail! "typed provider contract mismatch" {:capability id}))
          (kotoba$typed-value! result-type ((:invoke provider) request)))))])

(def default-fuel
  "The historical budget, kept as the default so an unparameterised call emits
  exactly what it did before."
  512)

(defn- prelude-forms
  "The runtime prelude for a DECLARED fuel budget.

  The budget used to be hardcoded at 512 here while the rest of the toolchain
  already carried one — `--fuel`, policy `:budgets {:fuel n}`, and
  `core/default-component-budgets` — so a caller could declare a budget, watch
  it be accepted, and receive an artifact that ignored it:
  `kotoba -M compile --target js --fuel 100000` emitted `fuel=512` (measured
  2026-08-11). The wasm32 branch has honoured the same value since T7.4; only
  this backend dropped it on the floor.

  Threading it does not widen what a module MAY DO. Authority is the policy's
  job — a module that requests no capabilities still cannot observe anything at
  any budget — and memory stays bounded by the separate `:nodes`/`:bytes` limbs
  of `:resource-model [:fuel :depth :nodes :bytes :provider-timeout]`. Fuel
  bounds exactly one thing: how long a call may run before the host gets
  control back.

  What the old constant DID bound, in practice, was program size. A charge is
  taken per function ENTRY and `loop`/`recur` desugars to a self-calling helper
  (ADR 0173 is explicit that zero-charge recur is not claimed), so 512 was a
  ceiling of ~510 iterations for the life of an instance — measured: a plain
  countdown returns at n=510 and traps at n=600. Any guest reading input longer
  than that could not finish, whatever the host was willing to spend."
  [fuel]
  (into [(list 'defonce 'kotoba$fuel (list 'atom fuel))] prelude-tail-forms))

(def default-ns-name
  "KIR carries no `ns` name (sema/analyze validates and discards the
  source `(ns ...)` form, see its own comment) -- every emitted module uses
  this fixed namespace unless the caller renames it in the returned source
  text themselves (an ns-name option is a natural follow-up, not needed for
  emit to be genuinely runnable via `nbb`/a cljs bundler today)."
  'kotoba.compiled.generated)

(defn emit
  "Lowers KIR to a plain ClojureScript source string: one `(ns ...)` form,
  the fuel/quot prelude, a `(declare ...)` of every KIR function name, then
  one `defn` per KIR function (in KIR's own order) -- callable directly by
  any cljs host (nbb, a browser bundle, shadow-cljs) that requires the
  emitted namespace and invokes `main` itself, the same \"host writes
  inputs, host calls main\" shape every other backend in this compiler
  already uses.

  The `declare` line is not decorative: unlike WASM/native, which resolve
  every call through a function-index table built BEFORE any body is
  assembled (so textual order is irrelevant), plain `defn` forms in a cljs
  (or JVM Clojure) file are compiled in file order with no forward
  hoisting -- a function whose body calls a KIR sibling defined LATER in
  the same source fails to resolve at compile/analysis time. This is real,
  not hypothetical: a `loop`/`recur`-using source's own synthesized helper
  is placed AFTER the defn that calls it in KIR's own function order (the
  originating defn is listed first, its loop-helpers appended after --
  see ADR-2607150000), so `main () -> __kotoba_loop_1` is exactly this
  shape. Confirmed live via real `nbb` execution before this fix: `Unable
  to resolve symbol: __kotoba_loop_1`, identically to plain JVM `eval`."
  ([kir] (emit kir default-fuel))
  ([kir fuel]
  (let [fn-names (mapv :name (:functions kir))
        exported-names (set (or (:exports kir) fn-names))
        fn-forms (mapv #(lower-function % (contains? exported-names (:name %)))
                       (:functions kir))
        schemas (or (:schemas kir) {})
        contracts (reference-runtime/capability-contracts kir)
        forms (concat [(list 'def 'kotoba$schemas (list 'quote schemas))
                       (list 'def 'kotoba$typed-contracts (list 'quote contracts))]
                      (prelude-forms fuel)
                       [(list* 'declare fn-names)] fn-forms)]
    (str/join "\n\n"
              (concat [(pr-str (list 'ns default-ns-name))
                       i64-predicate-source vector-index-source]
                      (map pr-str forms))))))
