(ns kotoba.runtime
  "Small CLJ-owned Kotoba execution core.

  This is the first Rust-free executable slice: it reads Kotoba-family source
  with an explicit reader target, checks a strict pure subset, emits deterministic
  EDN IR, and can run a zero-arity `main` function."
  (:require [clojure.java.io :as io]
            ;; aliased `cstr`, not `str` -- this file already uses the bare
            ;; `clojure.core/str` function extensively; `:as str` would
            ;; silently shadow every one of those call sites.
            [clojure.string :as cstr]
            [clojure.walk :as walk]
            [kotoba.guest-grammar :as guest-grammar]
            [kotoba.core.contracts :as core-contracts]
            [kotoba.lang.capability-values :as capability-values]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as reader-types]))

(def builtin-fns
  {'+ +
   '- -
   '* *
   '/ /
   'quot quot
   'mod mod
   'rem rem
   'inc inc
   'dec dec
   'min min
   'max max
   'i64 long
   'i64+ +
   'i64- -
   'i64* *
   'host-i64-roundtrip identity
   'call-indirect (fn [_idx arg] arg)
   '= =
   '< <
   '> >
   '<= <=
   '>= >=
   'zero? zero?
   'pos? pos?
   'neg? neg?
   'not not
   'bit-and bit-and
   'bit-or bit-or
   'bit-xor bit-xor
   'bit-shift-left bit-shift-left
   'bit-shift-right bit-shift-right
   'unsigned-bit-shift-right unsigned-bit-shift-right
   'i64and bit-and
   'i64or bit-or
   'i64xor bit-xor
   'i64shl bit-shift-left
   'i64shr bit-shift-right
   'i64ushr unsigned-bit-shift-right
   'alloc (constantly 0)
   'alloc-checked (constantly 0)
   'str-ptr (constantly 0)
   'bytes-ptr (constantly 0)
   'str-len (fn [s] (count (.getBytes (str s) "UTF-8")))
   'bytes-len count
   'memory-pages (constantly 1)
   'memory-grow (fn [_pages] 1)
   'mem-byte-at (fn [_ptr _idx] 0)
   'mem-i32-at (fn [_ptr _offset] 0)
   'byte-store! (fn [_ptr _idx value] value)
   'i32-store! (fn [_ptr _offset value] value)
   'result-ok? (fn [value] (not (neg? value)))
   'result-err? neg?
   'result-write! (fn [record-ptr _value] record-ptr)
   'result-status (fn [_record-ptr] 0)
   'result-value (fn [_record-ptr] 0)
   'byte-at (fn [value idx]
              (let [bytes (if (string? value)
                            (mapv #(bit-and % 0xff) (.getBytes value "UTF-8"))
                            (vec value))]
                (nth bytes idx)))
   'str str
   'count count
   'keyword keyword
   'name name})

(def capability-contract
  (core-contracts/capability-contract))

(def special-forms
  (:special-forms capability-contract))

(defn read-forms
  "Read every form from source using a concrete CLJC reader target.

  Kotoba source is untrusted input (the whole point of the safe-subset
  checker downstream), so the reader's eval-reader (`#=(...)`) and
  record/type-literal construction syntax must stay disabled here —
  `clojure.tools.reader/*read-eval*` defaults to true, which would let a
  crafted `.kotoba` file execute arbitrary JVM code the instant it's read,
  before the checker ever runs."
  [source reader-target]
  (let [rdr (reader-types/string-push-back-reader source)
        opts {:read-cond :allow
              :features #{reader-target}
              :eof ::eof}]
    (binding [reader/*read-eval* false]
      (loop [forms []]
        (let [form (reader/read opts rdr)]
          (if (= ::eof form)
            forms
            (recur (conj forms form))))))))

(defn read-file
  "Read every form from the file at path using a concrete CLJC reader target."
  [path reader-target]
  (read-forms (slurp (io/file path)) reader-target))

(defn list-head
  "The operator symbol of a list form (e.g. `+` in `(+ 1 2)`), or nil when
  form isn't a seq."
  [form]
  (when (seq? form)
    (first form)))

(defn walk-forms
  "Depth-first walk: call f on form itself, then on every nested form/map
  key+value/collection element."
  [f form]
  (f form)
  (cond
    (seq? form) (doseq [x form] (walk-forms f x))
    (map? form) (doseq [[k v] form]
                  (walk-forms f k)
                  (walk-forms f v))
    (coll? form) (doseq [x form] (walk-forms f x))))

(defn capability-name
  "Human-readable name for a capability value (delegates to the shared
  contract)."
  [value]
  (core-contracts/capability-name value))

(defn capability-id
  "Numeric capability id for a capability value under this launcher's
  capability-contract, or nil if unknown."
  [value]
  (core-contracts/capability-id capability-contract value))

(def cap-passing-imports
  "S4b capability-passing extension of the host-import surface, owned by this
  launcher slice (the core capability contract stays authoritative for the
  base ops). `cap-acquire` intersects policy ∩ grants ∩ requested ONCE at the
  host boundary and returns an opaque i64 capability handle; the single
  demonstration use shape `host-i64-roundtrip-with` threads that handle as a
  first-class argument through compiled wasm and is resolved back to the
  stored concrete capability at host-call time."
  {'cap-acquire {:module "kotoba"
                 :field "cap_acquire"
                 :params [:i32 :i32 :i32]
                 :result :i64}
   'host-i64-roundtrip-with {:module "kotoba"
                             :field "host_i64_roundtrip_with"
                             :capability "ledger/append"
                             :params [:i64 :i64]
                             :result :i64}})

(def host-imports
  (merge (core-contracts/host-imports capability-contract)
         cap-passing-imports))

(def host-import-order
  (into (core-contracts/host-import-order capability-contract)
        ['cap-acquire 'host-i64-roundtrip-with]))

(def op->kind
  "Host-import op (capability contract symbol) -> capability kind understood
  by kotoba.lang.capability-values/effect-for-kind."
  {'notify-show :host/notify
   'clipboard-read :host/clipboard-read
   'clipboard-write :host/clipboard-write
   'clipboard-write-str :host/clipboard-write
   'http-fetch :host/http
   'keychain-read :host/keychain-read
   'keychain-write :host/keychain-write
   'fs-read :host/fs-read
   'fs-write :host/fs-write
   'host-i64-roundtrip :host/ledger-append
   'kgraph-assert! :host/graph-assert
   'kgraph-retract! :host/graph-retract
   'kgraph-get-objects :host/graph-get-objects
   'kgraph-query :host/graph-query
   ;; aiueos default kernel capabilities (aiueos.policy/default-kernel-caps,
   ;; ADR-2607022700) -- topic/subscribe backs three ops (poll/take/count),
   ;; matching the retired Rust surface.rs registry.
   'log-write :host/log-write
   'clock-monotonic :host/clock-monotonic
   'random-bytes :host/random-bytes
   'topic-publish :host/topic-publish
   'topic-poll :host/topic-subscribe
   'topic-take :host/topic-subscribe
   'topic-count :host/topic-subscribe
   'pci-config :host/pci-config
   'dma-map :host/dma-map
   'irq-subscribe :host/irq-subscribe
   'mmio-map :host/mmio-map
   ;; kotoba-lang/kototama's actor:host ABI (kototama.contract/
   ;; kototama.tender, ADR-2607062330/2607062400), mirrored into kotoba's
   ;; own real host-provider surface (kotoba.wasm-exec/real-op-effects).
   ;; None of these need a cap-passing <op>-with variant (no entry below in
   ;; with-op->op/op->with-op) -- a plain guarded call is sufficient, same
   ;; as kgraph-assert!/clipboard-write -- but the entry HERE is still
   ;; required: kotoba.lang.capability-host/guard-call needs a capability
   ;; kind to build a request from for ANY guarded call, cap-passing or
   ;; not. (A first version of this registration omitted these entries on
   ;; the mistaken assumption that only cap-passing needed op->kind --
   ;; corrected once kotoba.wasm-exec's real, non-stub HostFunctions
   ;; actually tried to guard-call them and were denied :malformed-
   ;; requested every time, kotoba-lang/kotoba-lang#12.)
   'gen-keypair :host/identity-keypair
   'sign :host/identity-sign
   'verify :host/identity-verify
   'sha256-hex :host/hash-sha256
   'http-post :host/http-post
   'log-read :host/log-read
   ;; kami-* game-engine ECS surface (kotoba-core-contracts "kami/engine",
   ;; one shared capability id 233 -> one kind for the family, mirroring
   ;; topic-* mapping three ops to :host/topic-subscribe). The matching
   ;; effect-for-kind entry landed in kotoba-lang at the same time, so
   ;; guard-call can't reproduce the aiueos :unsupported-kind gap above.
   'kami-tick-n :host/kami-engine
   'kami-spawn :host/kami-engine
   'kami-despawn :host/kami-engine
   'kami-set-position! :host/kami-engine
   'kami-set-velocity! :host/kami-engine
   'kami-get-x :host/kami-engine
   'kami-get-y :host/kami-engine
   'kami-set-position3! :host/kami-engine
   'kami-set-velocity3! :host/kami-engine
   'kami-get-z :host/kami-engine
   'kami-count-tagged :host/kami-engine
   'kami-nearest-tagged :host/kami-engine
   'kami-move-tagged-toward! :host/kami-engine
   'kami-despawn-within! :host/kami-engine
   'kami-axis :host/kami-engine
   'kami-rand :host/kami-engine
   ;; ADR-2607140600 Phase 3a device-capability bridge (iPhone sensing for
   ;; the indoor floorplan-lab): 4 read-only, no-network-egress ops backed
   ;; by kotoba.sensing-host's deterministic stub (no real CoreMotion/
   ;; CoreBluetooth/AVAudioEngine access -- that native shim is explicitly
   ;; out of this ADR's scope). audio-play/audio-record share the ONE
   ;; audio/io capability -> :host/audio-io, same convention as topic-poll/
   ;; topic-take/topic-count sharing :host/topic-subscribe above. The
   ;; matching effect-for-kind entries landed in kotoba-lang at the same
   ;; time (kotoba-lang@9c3f1b7), so guard-call can't reproduce the aiueos
   ;; :unsupported-kind gap documented above.
   'motion-read :host/motion-read
   'audio-play :host/audio-io
   'audio-record :host/audio-io
   'ble-scan :host/ble-scan
   'wifi-info :host/wifi-info})

(def with-op->op
  "Capability-passing use variant (`<op>-with`, leading argument a capability
  handle from `cap-acquire`) -> base host-import op."
  (into {} (map (fn [[op _]] [(symbol (str op "-with")) op])) op->kind))

(def op->with-op
  "Base host-import op -> its capability-passing `<op>-with` use variant."
  (into {} (map (fn [[with-op op]] [op with-op])) with-op->op))

(def cap-passing-ops
  "Host-import ops that require a real capability-affine implementation on
  EVERY execution path -- `cap-acquire` itself, plus every `<op>-with`
  capability-passing use variant that's actually reachable as a compiled
  host-import call. `with-op->op`/`op->with-op` mechanically derive an
  `<op>-with` symbol for EVERY op registered in `op->kind` (most of which,
  e.g. `kami-tick-n-with`, were never wired into `host-imports`/
  `cap-passing-imports` and so can never appear in `required-host-imports`'
  output for a real compiled program); intersecting `op->with-op`'s values
  against `host-imports` narrows this down to the ops a guest can genuinely
  require -- currently just `host-i64-roundtrip-with`, alongside
  `cap-acquire`.

  A host that links a program requiring one of these ops must either back it
  with a real handle-aware implementation or refuse to run the program --
  never silently substitute an inert always-0 stub in its place (see
  kotoba.wasm-exec's `real-op-ids`, which intentionally excludes both
  `cap-acquire` and `host-i64-roundtrip-with`, and kotoba.launcher's
  `wasm-run-result*`, which uses this set to turn that gap into a loud
  refusal instead of a silent wrong-value stub)."
  (into #{'cap-acquire}
        (filter host-imports)
        (vals op->with-op)))

(def kind->capability
  "Capability kind -> contract capability name, derived from op->kind and the
  host-import contract."
  (into {}
        (keep (fn [[op kind]]
                (when-let [cap (get-in host-imports [op :capability])]
                  [kind cap])))
        op->kind))

(defn policy-capabilities
  "Set of capability names granted by policy (delegates to the shared
  contract)."
  [policy]
  (core-contracts/policy-capabilities policy))

(defn required-capabilities
  "Capability names referenced anywhere in forms, via `has-capability?`,
  `cap-acquire`, an `<op>-with` capability-passing use, or a direct
  capability-bearing host-import call."
  [forms]
  (let [caps (atom [])]
    (doseq [form forms]
      (walk-forms
       (fn [node]
         (when (seq? node)
           (let [op (first node)]
             (cond
               (= 'has-capability? op)
               (swap! caps conj (second node))

               (= 'cap-acquire op)
               (when-let [cap (get kind->capability (second node))]
                 (swap! caps conj cap))

               (contains? with-op->op op)
               (swap! caps conj (get-in host-imports
                                        [(get with-op->op op) :capability]))

               (get-in host-imports [op :capability])
               (swap! caps conj (get-in host-imports [op :capability]))))))
       form))
    (vec @caps)))

(defn required-host-imports
  "Host-import ops (symbols in host-imports) used anywhere in forms, in
  canonical host-import-order."
  [forms]
  (let [imports (atom #{})]
    (doseq [form forms]
      (walk-forms
       (fn [node]
         (when (and (seq? node) (contains? host-imports (first node)))
           (swap! imports conj (first node))))
       form))
    (vec (filter @imports host-import-order))))

(def ^:private network-resource-ops
  "Ops whose first str-ptr argument is a URL subject to capability-resources."
  #{'http-fetch 'http-post})

(defn- str-ptr-literal
  "When EXPR is `(str-ptr \"literal\")`, return the string; else nil."
  [expr]
  (when (and (seq? expr) (= 'str-ptr (first expr)) (string? (second expr)))
    (second expr)))

(defn- policy-resource-set
  "Set of resource strings for CAP-NAME under POLICY, or nil when unconstrained."
  [policy cap-name]
  (let [m (:kotoba.policy/capability-resources policy)]
    (when m
      (let [raw (or (get m cap-name)
                    (get m (keyword cap-name))
                    (some (fn [[k v]]
                            (when (= (capability-name k) cap-name) v))
                          m))]
        (cond
          (nil? raw) nil
          (= :any raw) #{:any}
          (set? raw) raw
          (string? raw) #{raw}
          :else nil)))))

(defn- resource-literal-allowed?
  "Static check: literal URL RESOURCE is covered by GRANTED (set or :any)."
  [granted resource]
  (cond
    (nil? granted) true
    (or (= :any granted) (contains? granted :any)) true
    (not (string? resource)) true
    :else
    (boolean (some (fn [g]
                     (and (string? g)
                          (or (= g resource)
                              (.startsWith ^String resource g))))
                   granted))))

(defn source-problems
  "Return safety/type problems for the current executable subset.

  P0 (ADR-2607180900): always includes guest-grammar/strict-problems
  (catalog forbidden heads + optional strict unknown-form rejection)."
  ([safe-facts forms] (source-problems safe-facts forms nil))
  ([safe-facts forms policy]
  (let [denied (set (remove #{"ns"} (:non-executable-forms safe-facts)))
        effect-ops (set (:effect-ops safe-facts))
        allowed-caps (policy-capabilities policy)
        grammar-problems (guest-grammar/strict-problems forms policy)
        problems (atom (vec grammar-problems))]
    (doseq [form forms]
      (walk-forms
       (fn [node]
         (when-let [head (some-> node list-head str)]
           (cond
             (denied head)
             (when-not (some #(and (= :denied-form (:kotoba.runtime/problem %))
                                   (= head (:kotoba.runtime/form %)))
                             @problems)
               (swap! problems conj
                      (guest-grammar/with-hint
                        {:kotoba.runtime/problem :denied-form
                         :kotoba.runtime/form head}
                        head)))

             (= "cap-acquire" head)
             (let [kind (second node)
                   cap-name (get kind->capability kind)]
               (cond
                 (nil? cap-name)
                 (swap! problems conj {:kotoba.runtime/problem :unknown-capability-kind
                                       :kotoba.runtime/kind (pr-str kind)})

                 (not (contains? allowed-caps cap-name))
                 (swap! problems conj {:kotoba.runtime/problem :capability-not-granted
                                       :kotoba.runtime/capability cap-name})))

             (contains? with-op->op (first node))
             (let [cap-name (get-in host-imports
                                    [(get with-op->op (first node)) :capability])]
               (when-not (contains? allowed-caps cap-name)
                 (swap! problems conj {:kotoba.runtime/problem :capability-not-granted
                                       :kotoba.runtime/capability cap-name})))

             (contains? host-imports (first node))
             (let [op (first node)
                   cap (or (get-in host-imports [op :capability])
                           (second node))
                   cap-name (capability-name cap)]
               (cond
                 (nil? (capability-id cap))
                 (swap! problems conj {:kotoba.runtime/problem :unknown-capability
                                       :kotoba.runtime/capability cap-name})

                 (not (contains? allowed-caps cap-name))
                 (swap! problems conj {:kotoba.runtime/problem :capability-not-granted
                                       :kotoba.runtime/capability cap-name})

                 (and (contains? network-resource-ops op)
                      (contains? allowed-caps cap-name))
                 (when-let [url (str-ptr-literal (second node))]
                   (let [granted (policy-resource-set policy cap-name)]
                     (when (and granted
                                (not (resource-literal-allowed? granted url)))
                       (swap! problems conj
                              {:kotoba.runtime/problem :resource-not-allowed
                               :kotoba.runtime/capability cap-name
                               :kotoba.runtime/resource url}))))))

             (effect-ops head)
             (swap! problems conj {:kotoba.runtime/problem :host-effect-requires-capability
                                   :kotoba.runtime/form head}))))
       form))
    @problems)))

(declare eval-form)

(defn truthy?
  "Kotoba truthiness: only nil and false are falsy, everything else
  (including 0) is truthy."
  [value]
  (not (or (false? value) (nil? value))))

(defn bind-params
  "Zip a fn's params to call args into an env map. Throws ex-info on arity
  mismatch."
  [params args]
  (when-not (= (count params) (count args))
    (throw (ex-info "arity mismatch" {:params params :args args})))
  (zipmap params args))

(defn eval-body
  "Evaluate forms in order under env/fns for side effects, returning the
  value of the last form (or nil for an empty body)."
  [forms env fns]
  (reduce (fn [_ form] (eval-form form env fns)) nil forms))

(defn call-fn
  "Call f with args: a Kotoba fn value (`{:kind :kotoba.runtime/fn ...}`) is
  interpreted, an ifn (builtin) is applied directly, anything else throws
  ex-info \"not callable\"."
  [f args fns]
  (cond
    (and (map? f) (= :kotoba.runtime/fn (:kind f)))
    (eval-body (:body f) (bind-params (:params f) args) fns)

    (ifn? f)
    (apply f args)

    :else
    (throw (ex-info "not callable" {:callee f :args args}))))

(defn eval-form
  "Core tree-walking evaluator for one form under env (local bindings) and
  fns (user-defined functions). Handles literals, symbol lookup
  (env -> fns -> builtin-fns), and the special forms ns/quote/do/let/if
  /when/and/or/def/defn; anything else is treated as a function call.
  when/and/or use `truthy?` (nil/false falsy), mirroring `if` -- note the
  known backend divergence: the WASM compiler's `if`/`when`/`and`/`or`
  operate on i32 where 0 is falsy, while here integer 0 is truthy."
  [form env fns]
  (cond
    (symbol? form)
    (cond
      (contains? env form) (get env form)
      (contains? fns form) (get fns form)
      (contains? builtin-fns form) (get builtin-fns form)
      :else (throw (ex-info "unknown symbol" {:symbol form})))

    (or (number? form) (string? form) (keyword? form) (boolean? form) (nil? form))
    form

    (vector? form)
    (mapv #(eval-form % env fns) form)

    (map? form)
    (into {} (map (fn [[k v]] [(eval-form k env fns) (eval-form v env fns)])) form)

    (seq? form)
    (let [[op & args] form]
      (case op
        ns nil
        quote (first args)
        do (eval-body args env fns)
        let (let [[bindings & body] args
                  env' (reduce (fn [acc [k v]]
                                 (assoc acc k (eval-form v acc fns)))
                               env
                               (partition 2 bindings))]
              (eval-body body env' fns))
        if (let [[test then else] args]
             (if (truthy? (eval-form test env fns))
               (eval-form then env fns)
               (eval-form else env fns)))
        when (let [[test & body] args]
               (when (truthy? (eval-form test env fns))
                 (eval-body body env fns)))
        and (loop [remaining args
                   value true]
              (if (seq remaining)
                (let [v (eval-form (first remaining) env fns)]
                  (if (truthy? v)
                    (recur (next remaining) v)
                    v))
                value))
        or (loop [remaining args]
             (if (seq remaining)
               (let [v (eval-form (first remaining) env fns)]
                 (if (truthy? v)
                   v
                   (recur (next remaining))))
               nil))
        def (let [[_name value] args]
              (eval-form value env fns))
        defn nil
        (call-fn (eval-form op env fns)
                 (mapv #(eval-form % env fns) args)
                 fns)))

    :else
    (throw (ex-info "unsupported literal" {:form form}))))

(defn function-def
  "Parse a top-level `(defn name [params] body...)` form into
  `[name {:kind :kotoba.runtime/fn ...}]`, or nil if form isn't a defn."
  [form]
  (when (and (seq? form) (= 'defn (first form)))
    (let [[_ name params & body] form]
      [name {:kind :kotoba.runtime/fn
             :name name
             :params (vec params)
             :body (vec body)}])))

(defn lower-language-forms
  "Lower Kotoba-only surface forms into the compiler core. This pass is
  shared by IR and Wasm so the two paths cannot silently diverge.
  `(defsystem move [dt] ...)` has the stable ABI export `move-tick`.

  ADR-2607180900 (L2): multi-body `when` → `if`+`do`; bare string literals
  on string-head host ops → str-ptr/str-len."
  [forms]
  (let [constants (into {}
                        (keep (fn [form]
                                (when (and (seq? form) (= 'def (first form))
                                           (= 3 (count form)))
                                  [(second form) (nth form 2)])))
                        forms)
        string-head-ops (guest-grammar/string-head-host-ops)
        string-args (fn [op s & args]
                      (list* op (list 'str-ptr s) (list 'str-len s) args))
        lower-cond (fn lower-cond [clauses]
                     (if (empty? clauses)
                       0
                       (let [[test value & more] clauses]
                         (if (= :else test)
                           value
                           (list 'if test value (lower-cond more))))))
        lower-string-head
        (fn [op args]
          (if (and (contains? string-head-ops op)
                   (seq args)
                   (string? (first args)))
            (list* op
                   (list 'str-ptr (first args))
                   (list 'str-len (first args))
                   (rest args))
            (list* op args)))
        lower-node
        (fn [node]
          (if-not (seq? node)
            node
            (let [[op & args] node]
              (case op
                defsystem (let [[name params & body] args]
                            (list* 'defn (symbol (str name "-tick")) params body))
                cond (lower-cond args)
                not= (list 'not (list* '= args))
                when (let [[test & body] args]
                       (cond
                         (empty? body) (list 'if test 0 0)
                         (= 1 (count body)) (list 'if test (first body) 0)
                         :else (list 'if test (cons 'do body) 0)))
                nearest-tagged (apply string-args 'kami-nearest-tagged args)
                spawn-entity (apply string-args 'kami-spawn args)
                count-tagged (apply string-args 'kami-count-tagged args)
                axis (apply string-args 'kami-axis args)
                play-sound (apply string-args 'audio-play args)
                despawn-entity (list* 'kami-despawn args)
                set-position! (case (count args)
                                3 (list* 'kami-set-position! args)
                                4 (list* 'kami-set-position3! args)
                                node)
                set-velocity! (case (count args)
                                3 (list* 'kami-set-velocity! args)
                                4 (list* 'kami-set-velocity3! args)
                                node)
                get-x (list* 'kami-get-x args)
                get-y (list* 'kami-get-y args)
                get-z (list* 'kami-get-z args)
                tick-n (list 'kami-tick-n)
                rand-int (list* 'kami-rand args)
                ;; The current Kami host has a safe bulk operation instead of
                ;; exposing an unbounded guest loop over host-owned entities.
                doseq-entities
                (let [[[entity tag] & body] args
                      call (first body)]
                  (if (and (= 1 (count body)) (seq? call)
                           (= 'move-toward! (first call))
                           (= entity (second call)))
                    (let [[_ _ target speed] call]
                      (string-args 'kami-move-tagged-toward! tag
                                   (list 'kami-get-x target)
                                   (list 'kami-get-y target)
                                   speed))
                    node))
                ;; Default: string-head host ops accept bare string literals.
                (lower-string-head op args)))))]
    (->> forms
         (remove #(and (seq? %) (= 'def (first %))))
         (mapv (fn [form]
                 (->> form
                      (walk/postwalk-replace constants)
                      (walk/postwalk lower-node)))))))

(defn compile-forms
  "Compile checked forms to deterministic EDN IR."
  [source-plan forms]
  (let [forms (lower-language-forms forms)
        fns (into {} (keep function-def forms))
        expressions (vec (remove #(and (seq? %) (#{'ns 'defn} (first %))) forms))]
    {:schema "kotoba.runtime.edn-ir.v0"
     :kotoba.runtime/source-plan source-plan
     :kotoba.runtime/exports (vec (sort (map (comp str key) fns)))
     :kotoba.runtime/forms (pr-str forms)
     :kotoba.runtime/expression-count (count expressions)
     :kotoba.runtime/fn-count (count fns)}))

(defn- acquired-kinds
  "Capability kinds acquired (`cap-acquire`) or used through a capability
  handle (`<op>-with`) anywhere inside FORM."
  [form]
  (let [kinds (atom [])]
    (walk-forms
     (fn [node]
       (when (seq? node)
         (let [op (first node)]
           (cond
             (= 'cap-acquire op)
             (swap! kinds conj (second node))

             (contains? with-op->op op)
             (swap! kinds conj (get op->kind (get with-op->op op)))))))
     form)
    (vec (distinct @kinds))))

;; ---------------------------------------------------------------------------
;; Typed capability parameters (S4b)

(defn cap-param-kind
  "Capability kind declared on a parameter symbol through the canonical
  `^{:cap <kind-kw>}` metadata form (e.g.
  `(defn use-ledger [^{:cap :host/ledger-append} c ^:i64 code] ...)`),
  or nil for an untyped parameter. This is the ONE metadata form: kind
  keywords are themselves namespaced (`:host/ledger-append`), so a
  `^:cap/<kind>` reader shorthand cannot spell them and is not accepted."
  [sym]
  (:cap (meta sym)))

(declare function-defs symbol-key)

(defn- binding-symbol-keys
  "All local symbol keys introduced by a symbol or destructuring binding.
  Used only to shadow capability facts; collecting an extra symbol from a
  destructuring default is conservative (it can forget a capability fact,
  never invent one)."
  [binding]
  (->> (tree-seq coll? seq binding)
       (filter symbol?)
       (remove #{'&})
       (map symbol-key)
       set))

(defn- shadow-cap-bindings [cap-env binding]
  (reduce dissoc cap-env (binding-symbol-keys binding)))

(defn- capability-destructuring-problem [fn-name binding]
  {:kotoba.runtime/problem :capability-destructuring-forbidden
   :kotoba.runtime/fn (str fn-name)
   :kotoba.runtime/binding (pr-str binding)})

(defn- cap-expr-kind
  "Static capability kind of EXPR under CAP-ENV (local symbol -> kind), or
  nil when EXPR is not statically known to carry a capability. Recognized
  cap-typed expressions in this slice: the direct result of
  `(cap-acquire <kind> ...)`, a `^{:cap <kind>}` typed parameter, and a
  let-bound alias of either."
  [cap-env expr]
  (cond
    (and (seq? expr) (= 'cap-acquire (first expr))) (second expr)
    (symbol? expr) (get cap-env (symbol-key expr))
    :else nil))

(defn- cap-arg-problem
  "Problem for one call-site argument position that requires a capability of
  EXPECTED kind, or nil when ARG statically satisfies it."
  [fn-name op cap-env expected arg]
  (let [actual (cap-expr-kind cap-env arg)]
    (cond
      (nil? actual)
      {:kotoba.runtime/problem :cap-arg-not-capability
       :kotoba.runtime/fn (str fn-name)
       :kotoba.runtime/op (str op)
       :kotoba.runtime/arg (pr-str arg)}

      (not= expected actual)
      {:kotoba.runtime/problem :cap-kind-mismatch
       :kotoba.runtime/fn (str fn-name)
       :kotoba.runtime/op (str op)
       :kotoba.runtime/expected expected
       :kotoba.runtime/actual actual})))

(defn- cap-use-problems
  "Static typed-capability problems inside EXPR (one expression of FN-NAME's
  body). CAP-ENV maps local symbols to capability kinds; FN-PARAM-KINDS maps
  user fn name -> vector of its declared param cap kinds (nil entries for
  untyped params), so the checks hold across the direct call graph."
  [fn-name cap-env fn-param-kinds expr]
  (letfn [(check-all [cap-env exprs]
            (vec (mapcat #(check cap-env %) exprs)))
          (check [cap-env expr]
            (if-not (seq? expr)
              []
              (let [[op & args] expr]
                (case op
                  (quote ns defn) []
                  def (check-all cap-env (rest args))
                  (do if) (check-all cap-env args)
                  let (let [[bindings & body] args]
                        (loop [pairs (partition 2 bindings)
                               cap-env cap-env
                               problems []]
                          (if-let [[binding value] (first pairs)]
                            (let [kind (cap-expr-kind cap-env value)
                                  cap-env' (if (symbol? binding)
                                             (if kind
                                               (assoc cap-env (symbol-key binding) kind)
                                               (dissoc cap-env (symbol-key binding)))
                                             (shadow-cap-bindings cap-env binding))
                                  problems' (cond-> (into problems (check cap-env value))
                                              (and kind (not (symbol? binding)))
                                              (conj (capability-destructuring-problem
                                                     fn-name binding)))]
                              (recur (next pairs) cap-env' problems'))
                            (into problems (check-all cap-env body)))))
                  ;; call site
                  (let [problems (check-all cap-env args)]
                    (cond
                      ;; `<op>-with` first argument must be cap-typed and of
                      ;; the op's kind.
                      (contains? with-op->op op)
                      (if-let [problem (cap-arg-problem
                                        fn-name op cap-env
                                        (get op->kind (get with-op->op op))
                                        (first args))]
                        (conj problems problem)
                        problems)

                      ;; direct call to a user fn: cap-typed params require
                      ;; cap-typed arguments of the same kind.
                      (contains? fn-param-kinds op)
                      (into problems
                            (remove nil?)
                            (map (fn [expected arg]
                                   (when expected
                                     (cap-arg-problem fn-name op cap-env
                                                      expected arg)))
                                 (get fn-param-kinds op)
                                 args))

                      :else problems))))))]
    (check cap-env expr)))

(defn cap-typed-problems
  "Static S4b typed-capability-parameter checks, run at check/emit time next
  to the effect gate:

  - a `^{:cap <kind>}` param kind must be a known capability kind
    (:unknown-capability-kind — kotoba.lang.capability-values/effect-for-kind
    is the vocabulary);
  - the first argument of every `<op>-with` use must be cap-typed — the
    direct result of `(cap-acquire ...)`, a cap-typed param, or a let-bound
    alias — never an untyped/forgeable integer (:cap-arg-not-capability);
  - the cap-typed value's kind must match the op's kind, and a cap-typed
    argument passed to a user fn must match the callee's declared param kind
    (:cap-kind-mismatch), across the direct call graph."
  [forms]
  (let [defs (function-defs forms)
        fn-param-kinds (into {}
                             (map (fn [[fname f]]
                                    [fname (mapv cap-param-kind (:params f))]))
                             defs)
        param-problems
        (vec (mapcat
              (fn [[fname f]]
                (keep (fn [param]
                        (when-let [kind (cap-param-kind param)]
                          (if-not (symbol? param)
                            (capability-destructuring-problem fname param)
                            (when-not (contains? capability-values/effect-for-kind kind)
                              {:kotoba.runtime/problem :unknown-capability-kind
                               :kotoba.runtime/fn (str fname)
                               :kotoba.runtime/kind (pr-str kind)}))))
                      (:params f)))
              defs))
        body-problems
        (vec (mapcat
              (fn [form]
                (if-let [[fname f] (function-def form)]
                  (let [cap-env (into {}
                                      (keep (fn [param]
                                              (when-let [kind (and (symbol? param)
                                                                  (cap-param-kind param))]
                                                [(symbol-key param) kind])))
                                      (:params f))]
                    (mapcat #(cap-use-problems fname cap-env fn-param-kinds %)
                            (:body f)))
                  (cap-use-problems 'top-level {} fn-param-kinds form)))
              forms))]
    (into param-problems body-problems)))

;; ---------------------------------------------------------------------------
;; Capability value affinity (narrow S2 -- ADR-safe-capability-language.md
;; "borrow checker (S2, deterministic drop, no implicit clone)").
;;
;; This is NOT a general Rust-style ownership/borrow/lifetime system over
;; every value in the language. T1 Memory Safety is already achieved without
;; one (raw memory ops denied, byte-at/byte-append! bounds-checked, the bump
;; allocator never frees so use-after-free/double-free are structurally
;; absent, concurrency primitives are denied so data races are structurally
;; absent). The ONLY thing scoped here is capability-typed values: a
;; `^{:cap <kind>}` param, the direct result of `(cap-acquire ...)`, or a
;; let-bound alias of either. "Deterministic drop" means an unused
;; capability-typed binding is fine (no linear must-use requirement); "no
;; implicit clone" means the SAME binding may be consumed at most once along
;; any single execution path -- reusing it (two `<op>-with` calls, two
;; callee arguments, or one of each) would let one capability grant silently
;; back two independent live uses, which is exactly the kind of duplication
;; capability-passing (S4b) must not allow.

(defn- cap-consuming-arg-index?
  "True when position IDX of a call to OP is a capability-consuming argument
  position: the sole (index 0) leading handle argument of an `<op>-with`
  use, or an argument aligned with one of the callee's `^{:cap <kind>}`
  typed params (FN-PARAM-KINDS: user fn name -> vector of declared param cap
  kinds, nil entries for untyped params -- the same map cap-typed-problems
  builds)."
  [fn-param-kinds op idx]
  (cond
    (contains? with-op->op op) (zero? idx)
    (contains? fn-param-kinds op) (some? (nth (get fn-param-kinds op) idx nil))
    :else false))

(defn- cap-expr-info
  "[info' counter'] where info' is `{:kind :origin}` describing EXPR's
  capability identity under CAP-ENV (local symbol -> `{:kind :origin}`), or
  nil when EXPR is not statically known to carry a capability -- the same
  two recognized shapes as `cap-expr-kind` (a direct `(cap-acquire ...)`
  result, or a symbol reference), just carrying an ORIGIN id alongside the
  kind. A direct `(cap-acquire ...)` result is assigned a FRESH origin
  (COUNTER, then bumped) because each textual occurrence produces a
  distinct value; a symbol reference reuses whatever `{:kind :origin}` its
  binding already carries, so a `let`-bound alias shares its origin with
  whatever it aliases rather than fabricating a new identity. This is what
  lets the affine check in cap-affine-step catch `(let [alias c] ...)`
  followed by using `alias` and `c` once each as a double-spend of the SAME
  origin, not two independent bindings."
  [cap-env counter expr]
  (cond
    (and (seq? expr) (= 'cap-acquire (first expr)))
    [{:kind (second expr) :origin counter} (inc counter)]

    (symbol? expr)
    [(get cap-env (symbol-key expr)) counter]

    :else
    [nil counter]))

(defn- affine-use
  "[problems' used'] after checking one already-evaluated argument ARG at a
  capability-consuming position of a call to OP, under CAP-ENV (local
  symbol -> `{:kind :origin}`) and USED (origin id -> the op that first
  consumed it, for every capability VALUE already consumed on every path
  reaching this call). Tracking is by ORIGIN, not by local binding name, so
  a `let`-bound alias and whatever it aliases share the same origin and a
  reuse through either name is caught. Only a bare symbol reference to a
  tracked binding can ever be reused -- an inline `(cap-acquire ...)`
  expression produces a fresh, never-referenceable-again value every time
  it is textually evaluated, so it is never itself a reuse. Reusing an
  already-consumed origin is the `:cap-value-reused` violation."
  [fn-name cap-env used op arg]
  (if-let [{:keys [kind origin]} (and (symbol? arg) (get cap-env (symbol-key arg)))]
    (if-let [first-use (get used origin)]
      [[{:kotoba.runtime/problem :cap-value-reused
         :kotoba.runtime/fn (str fn-name)
         :kotoba.runtime/op (str op)
         :kotoba.runtime/binding (str arg)
         :kotoba.runtime/kind kind
         :kotoba.runtime/first-use first-use}]
       used]
      [[] (assoc used origin (str op))])
    [[] used]))

(defn- cap-affine-step
  "[problems' used' counter'] after walking EXPR (one form inside FN-NAME's
  body) under CAP-ENV (local symbol -> `{:kind :origin}`, grows through
  `let`), FN-PARAM-KINDS (user fn name -> declared param cap kinds), USED
  (origin id -> the op that consumed it, for every capability VALUE already
  consumed on every path reaching EXPR), and COUNTER (the next fresh origin
  id to assign to a `(cap-acquire ...)` occurrence). Sequencing:

  - `do`/function-body/`def` values thread USED and COUNTER left-to-right
    (each form sees everything the previous ones consumed, and every
    `cap-acquire` occurrence gets its own never-repeated origin);
  - `let` evaluates each binding's value under the PRE-binding env/used/
    counter (so a value referencing a name this same let is about to
    shadow still sees the outer binding), assigns the new binding's
    `{:kind :origin}` via cap-expr-info (fresh origin for a direct
    `cap-acquire`, shared origin for an alias), and threads USED/COUNTER
    through the bindings and then the body in order. USED is keyed by
    origin, not by local name, so unlike CAP-ENV it never needs scope-exit
    filtering: an origin created inside a let remains a globally unique
    identifier for that one capability value for the rest of the function,
    so two sibling `(let [c ...] ...)` blocks reusing the name `c` for two
    DIFFERENT `cap-acquire` calls get two different origins automatically
    and can never be confused, regardless of lexical scope;
  - `if` evaluates its test under the incoming USED/COUNTER, then evaluates
    BOTH branches independently from the same post-test USED (only one
    branch ever runs at runtime) and continues with the UNION of what each
    branch consumed -- a binding consumed in EITHER branch must be treated
    as possibly-already-consumed by whatever follows, since a downstream
    reuse on the branch actually taken would otherwise go undetected. The
    else branch continues numbering origins from wherever the then branch
    left COUNTER (an arbitrary but harmless ordering artifact -- origins
    across the two branches never collide, and downstream code continues
    from the higher of the two);
  - a capability-typed value reaching an `<op>-with` leading argument or a
    callee's `^{:cap <kind>}` parameter position is a consuming use
    (affine-use), checked AFTER recursing into the argument itself (so a
    reuse nested inside a complex argument expression is still caught)."
  [fn-name cap-env fn-param-kinds used counter expr]
  (if-not (seq? expr)
    [[] used counter]
    (let [[op & args] expr
          step (fn [used counter e] (cap-affine-step fn-name cap-env fn-param-kinds used counter e))
          step-seq (fn [used counter exprs]
                     (reduce (fn [[problems used counter] e]
                               (let [[p' used' counter'] (step used counter e)]
                                 [(into problems p') used' counter']))
                             [[] used counter] exprs))]
      (case op
        (quote ns defn) [[] used counter]
        def (step-seq used counter (rest args))
        do (step-seq used counter args)

        let
        (let [[bindings & body] args]
          (loop [pairs (partition 2 bindings)
                 cap-env cap-env
                 used used
                 counter counter
                 problems []]
            (if-let [[binding value] (first pairs)]
              (let [[p' used' counter'] (cap-affine-step fn-name cap-env fn-param-kinds used counter value)
                    [info counter''] (cap-expr-info cap-env counter' value)
                    cap-env' (if (symbol? binding)
                               (if info
                                 (assoc cap-env (symbol-key binding) info)
                                 (dissoc cap-env (symbol-key binding)))
                               (shadow-cap-bindings cap-env binding))]
                (recur (next pairs) cap-env' used' counter'' (into problems p')))
              (let [[p' used' counter']
                    (reduce (fn [[problems used counter] e]
                              (let [[p'' used'' counter'']
                                    (cap-affine-step fn-name cap-env fn-param-kinds used counter e)]
                                [(into problems p'') used'' counter'']))
                            [[] used counter] body)]
                [(into problems p') used' counter']))))

        if
        (let [[test then else] args
              [tp tused tcounter] (step used counter test)
              [thp thused thcounter] (cap-affine-step fn-name cap-env fn-param-kinds tused tcounter then)
              [ep eused ecounter] (cap-affine-step fn-name cap-env fn-param-kinds tused thcounter else)]
          [(-> tp (into thp) (into ep)) (merge thused eused) ecounter])

        ;; call site: thread USED/COUNTER left-to-right through args
        ;; (recursing into each for its own internal problems/uses first),
        ;; then flag any capability-consuming position that reuses an
        ;; already-used origin.
        (loop [remaining (map-indexed vector args)
               used used
               counter counter
               problems []]
          (if-let [[idx arg] (first remaining)]
            (let [[p' used' counter'] (step used counter arg)
                  [p'' used''] (if (cap-consuming-arg-index? fn-param-kinds op idx)
                                 (affine-use fn-name cap-env used' op arg)
                                 [[] used'])]
              (recur (next remaining) used'' counter' (-> problems (into p') (into p''))))
            [problems used counter]))))))

(defn cap-affine-problems
  "Static capability-value affinity checks (narrow S2): every capability
  VALUE -- the result of a `^{:cap <kind>}` param binding or a
  `(cap-acquire ...)` call, tracked by origin so every `let`-bound alias of
  it shares the same identity -- may be consumed (the leading argument of
  an `<op>-with` use, or an argument aligned with a callee's
  `^{:cap <kind>}` param) AT MOST ONCE along any single execution path
  through a function body (`:cap-value-reused` otherwise). Being left
  unused is fine -- deterministic drop, no linear must-use requirement.

  This is checked purely per function body: passing a capability into a
  callee's cap-typed param is itself the caller's one consuming use of its
  own binding; what the callee does with the value it receives is the
  callee's own, separately checked, affine property. Each top-level `defn`
  (and each bare top-level form) is walked from a fresh, empty USED set and
  a fresh origin counter starting at 0, with cap-env seeded from
  `^{:cap <kind>}` params for `defn` (each param gets its own origin)."
  [forms]
  (let [defs (function-defs forms)
        fn-param-kinds (into {}
                             (map (fn [[fname f]]
                                    [fname (mapv cap-param-kind (:params f))]))
                             defs)]
    (vec
     (mapcat
      (fn [form]
        (let [[problems _used _counter]
              (if-let [[fname f] (function-def form)]
                (let [[cap-env counter]
                              (reduce (fn [[cap-env counter] param]
                                (if-let [kind (and (symbol? param)
                                                   (cap-param-kind param))]
                                  [(assoc cap-env (symbol-key param) {:kind kind :origin counter})
                                   (inc counter)]
                                  [cap-env counter]))
                              [{} 0]
                              (:params f))]
                  (cap-affine-step fname cap-env fn-param-kinds {} counter (cons 'do (:body f))))
                (cap-affine-step 'top-level {} fn-param-kinds {} 0 form))]
          problems))
      forms))))

(defn- direct-callees
  "User fn names (from FN-NAMES) called anywhere inside BODY."
  [fn-names body]
  (let [found (atom #{})]
    (doseq [form body]
      (walk-forms
       (fn [node]
         (when (and (seq? node) (contains? fn-names (first node)))
           (swap! found conj (first node))))
       form))
    @found))

(defn fn-required-cap-kinds
  "Map of user fn name -> set of capability kinds the fn requires: kinds its
  body acquires (`cap-acquire`) or uses through a handle (`<op>-with`), kinds
  of its `^{:cap <kind>}` typed params, plus — interprocedurally, closing a
  fixpoint over direct calls — everything its callees require. A caller
  passing its own cap param through to a callee therefore inherits the
  requirement."
  [forms]
  (let [defs (function-defs forms)
        names (set (map first defs))
        own (into {}
                  (map (fn [[fname f]]
                         [fname (into (set (acquired-kinds (:body f)))
                                      (keep cap-param-kind)
                                      (:params f))]))
                  defs)
        callees (into {}
                      (map (fn [[fname f]]
                             [fname (direct-callees names (:body f))]))
                      defs)]
    (loop [required own]
      (let [advanced (into {}
                           (map (fn [[fname kinds]]
                                  [fname (into kinds
                                               (mapcat #(get required % #{}))
                                               (get callees fname))]))
                           required)]
        (if (= advanced required)
          required
          (recur advanced))))))

(defn cap-effect-problems
  "S4b effect/capability consistency: when a `defn` declares an :effects row
  (metadata on the function name), every capability kind it requires — body
  `cap-acquire`/`<op>-with` kinds, `^{:cap <kind>}` typed param kinds, and,
  through the fn-required-cap-kinds fixpoint over direct calls, everything
  its callees require — must be covered by the row
  (kotoba.lang.capability-values/effects-consistent?). Under-declaration is
  rejected; functions without a declared row are not checked by this slice."
  [forms]
  (let [required (fn-required-cap-kinds forms)]
    (vec
     (for [form forms
           :when (and (seq? form) (= 'defn (first form)))
           :let [[_ fn-name] form
                 row (:effects (meta fn-name))]
           :when (some? row)
           :let [kinds (get required fn-name)
                 checked (capability-values/effects-consistent?
                          row (map (fn [kind] {:cap/kind kind}) kinds))]
           :when (not (:ok? checked))]
       {:kotoba.runtime/problem :cap-effect-under-declared
        :kotoba.runtime/fn (str fn-name)
        :kotoba.runtime/missing (:missing checked)}))))

(defn- annotated-signature? [form]
  (and (seq? form)
       (= 'defn (first form))
       (some? (:signature (meta (second form))))))

(defn- type-system-validator []
  (try
    (requiring-resolve 'kotoba.lang.type-system/validate-forms)
    (catch java.io.FileNotFoundException _ nil)))

(defn type-contract-problems
  "Fail closed for annotated signatures when the language validator is absent."
  [forms]
  (when (some annotated-signature? forms)
    (if-let [validate-forms (type-system-validator)]
      (let [{:keys [problems missing-effects]} (validate-forms forms)]
        (vec
         (concat
          (map (fn [problem]
                 {:kotoba.runtime/problem :type-contract-invalid
                  :kotoba.runtime/detail problem})
               problems)
          (map (fn [effect]
                 {:kotoba.runtime/problem :type-contract-missing-effect
                  :kotoba.runtime/effect effect})
               missing-effects))))
      [{:kotoba.runtime/problem :type-contract-unavailable
        :kotoba.runtime/required "kotoba.lang.type-system/validate-forms"}])))

(defn- type-system-fn [sym]
  (try
    (requiring-resolve sym)
    (catch java.io.FileNotFoundException _ nil)
    (catch Exception _ nil)))

(def source-import-arities
  "Guest-facing (pre-ABI) arities for host imports. The live host-imports
  :params vector is the WASM ABI after string/ptr lowering and keyword
  encoding — not the surface call shape authors write. L4 checks surface
  arity from this map; ops absent here are not arity-checked (ABI-only)."
  {'clipboard-read 2
   'clipboard-write 2
   'log-write 2
   'log-read 2
   'clock-monotonic 0
   'cap-acquire 2
   'sha256-hex 2
   'http-post 4
   'random-bytes 2
   'gen-keypair 0})

(defn- source-import-catalog
  "Catalog shaped for type-system/validate-host-import-calls using surface arities."
  []
  (into {}
        (keep (fn [[op n]]
                (when-let [entry (get host-imports op)]
                  [op (assoc entry :params (vec (repeat n :value)))]))
              source-import-arities)))

(defn import-arity-problems
  "L4: check host-import surface arity against source-import-arities.
  Opt-in via policy :kotoba.policy/check-import-arity or :typed-hir so existing
  demos (ABI-shaped catalogs) are not false-positive rejected. Always
  available for direct calls with explicit true policy."
  ([forms] (import-arity-problems forms nil))
  ([forms policy]
   (let [enabled? (boolean (or (:kotoba.policy/check-import-arity policy)
                               (:kotoba.policy/typed-hir policy)
                               (:check-import-arity policy)
                               (:typed-hir policy)))]
     (if-not enabled?
       []
       (if-let [validate (type-system-fn 'kotoba.lang.type-system/validate-host-import-calls)]
         (let [{:keys [problems]} (validate (source-import-catalog) forms)]
           (mapv (fn [problem]
                   {:kotoba.runtime/problem :import-arity-invalid
                    :kotoba.runtime/detail problem})
                 problems))
         [])))))

(defn require-signature-problems
  "L4: when policy requests typed-HIR admission, public defn must carry
  :signature metadata."
  [forms policy]
  (let [require? (boolean (or (:kotoba.policy/require-signatures policy)
                              (:kotoba.policy/typed-hir policy)
                              (:require-signatures policy)
                              (:typed-hir policy)))]
    (if-let [check (type-system-fn 'kotoba.lang.type-system/require-signatures-problems)]
      (mapv (fn [problem]
              {:kotoba.runtime/problem :signature-required
               :kotoba.runtime/detail problem})
            (check forms require?))
      [])))

(declare wasm-binary)

(defn check
  "Run all static checks (safety/type problems, typed-capability problems,
  capability-value affinity, effect/capability consistency) over forms and,
  if none fire, compile the EDN IR. Returns `{:kotoba.runtime/ok?
  :kotoba.runtime/problems :kotoba.runtime/ir}`."
  ([safe-facts source-plan forms] (check safe-facts source-plan forms nil))
  ([safe-facts source-plan forms policy]
  (let [surface-forms forms
        forms (lower-language-forms forms)
        static-problems (vec (concat (source-problems safe-facts forms policy)
                                     (cap-typed-problems forms)
                                     (cap-affine-problems forms)
                                     (cap-effect-problems forms)
                                     (type-contract-problems forms)
                                     (import-arity-problems forms policy)
                                     (require-signature-problems forms policy)))
        ;; A defsystem source is an executable game module, not merely data.
        ;; Its safety check must prove that the selected Kotoba backend can
        ;; compile it; accepting an unknown operation here would be fail-open.
        backend (when (and (empty? static-problems)
                           (some #(and (seq? %) (= 'defsystem (first %))) surface-forms))
                  (wasm-binary forms policy))
        backend-problems (when (and backend (not (:kotoba.wasm/ok? backend)))
                           (mapv (fn [problem]
                                   {:kotoba.runtime/problem :backend-unsupported
                                    :kotoba.runtime/backend :wasm
                                    :kotoba.runtime/cause problem})
                                 (:kotoba.wasm/problems backend)))
        problems (into static-problems backend-problems)
        ir (when (empty? problems)
             (compile-forms source-plan forms))]
    {:kotoba.runtime/ok? (empty? problems)
     :kotoba.runtime/problems problems
     :kotoba.runtime/ir ir})))

(defn- guarded-host-fns
  "Interpreter bindings routing every capability-bearing host-import op used
  by FORMS through HOST-CALL (fn [op args] result)."
  [forms host-call]
  (into {}
        (keep (fn [op]
                (when (and (get-in host-imports [op :capability])
                           (not (contains? with-op->op op)))
                  [op (fn [& args] (host-call op (vec args)))])))
        (required-host-imports forms)))

(defn run
  "Run FORMS through the CLJ interpreter slice.

  The optional OPTS map enables the capability-guarded host path (issue #263):
  {:policy           <policy EDN, used for the static capability check>
   :host-call        <fn [op args] -> result; every capability-bearing
                      host-import op is dispatched through it>
   :capability-query <fn [cap] -> boolean, bound as has-capability?>
   :host-fns         <map of extra interpreter bindings (symbol -> fn), used
                      by the S4b capability-passing surface: cap-acquire and
                      the <op>-with use variants>}

  Without OPTS behavior is unchanged: host-import ops are statically rejected
  as :capability-not-granted. With a guard installed, a denied host call
  (ex-info carrying :kotoba.host/denied) fails the run closed with a
  :host-call-denied problem; the provider is never invoked."
  ([safe-facts source-plan forms] (run safe-facts source-plan forms nil))
  ([safe-facts source-plan forms {:keys [policy host-call capability-query host-fns]}]
   (let [{:kotoba.runtime/keys [ok? problems ir]} (check safe-facts source-plan forms policy)
         fns (into {} (keep function-def forms))
         env-fns (cond-> fns
                   host-call (merge (guarded-host-fns forms host-call))
                   host-fns (merge host-fns)
                   (and host-call capability-query)
                   (assoc 'has-capability? capability-query))]
     (if-not ok?
       {:kotoba.runtime/ok? false
        :kotoba.runtime/problems problems
        :kotoba.runtime/ir ir}
       (try
         (let [expressions (vec (remove #(and (seq? %) (#{'ns 'defn} (first %))) forms))
               value (cond
                       (contains? fns 'main)
                       (call-fn (get fns 'main) [] env-fns)

                       (seq expressions)
                       (eval-body expressions {} env-fns)

                       :else
                       nil)]
           {:kotoba.runtime/ok? true
            :kotoba.runtime/value value
            :kotoba.runtime/ir ir})
         (catch clojure.lang.ExceptionInfo e
           (if (and host-call (:kotoba.host/denied (ex-data e)))
             {:kotoba.runtime/ok? false
              :kotoba.runtime/problems [{:kotoba.runtime/problem :host-call-denied
                                         :kotoba.runtime/call (:kotoba.host/call (ex-data e))
                                         :kotoba.runtime/denied (:kotoba.host/denied (ex-data e))}]
              :kotoba.runtime/ir ir}
             (throw e)))
         ;; The interpreter (eval-form/eval-body/call-fn) is a plain
         ;; tree-walker with no tail-call optimization and no step/fuel
         ;; budget of its own (unlike the WASM path's `kotoba.wasm-exec/
         ;; fuel-listener`, a real per-instruction cap): an unboundedly
         ;; self-recursive `defn` (e.g. src/demo_loop_forever.kotoba's
         ;; `spin`) grows the JVM call stack one frame per recursive call
         ;; until the JVM throws `StackOverflowError` -- a `java.lang.Error`,
         ;; NOT an `Exception`, so it is invisible to the
         ;; `clojure.lang.ExceptionInfo` catch above and would otherwise
         ;; propagate uncaught, crashing this `run` call (and, via
         ;; kotoba.launcher's `-main`, the whole `kotoba run` process) with a
         ;; raw Java stack trace instead of the clean
         ;; `{:kotoba.runtime/ok? false ...}` shape every other failure mode
         ;; here produces. Caught narrowly (StackOverflowError, not a
         ;; blanket Throwable) so unrelated JVM-level failures (e.g.
         ;; OutOfMemoryError, a genuine AssertionError bug) still surface
         ;; instead of being silently reclassified as a clean interpreter
         ;; result. This is deliberately just "catch and report cleanly" --
         ;; NOT a step-count/instruction-budget fuel mechanism matching the
         ;; WASM path's `fuel-listener`; that would be a larger feature and
         ;; is out of scope here."
         (catch StackOverflowError _e
           {:kotoba.runtime/ok? false
            :kotoba.runtime/problems [{:kotoba.runtime/problem :stack-overflow}]
            :kotoba.runtime/ir ir}))))))

(defn wasm-artifact
  "Emit deterministic bytes for the current IR contract.

  This is intentionally labeled as EDN IR, not a WebAssembly binary. A later
  emitter can replace the payload while keeping the checked source contract.
  "
  [ir]
  (.getBytes (pr-str ir) "UTF-8"))

(defn uleb
  "Unsigned LEB128 encoding."
  [n]
  (loop [value (long n)
         out []]
    (let [byte (bit-and value 0x7f)
          next-value (unsigned-bit-shift-right value 7)]
      (if (zero? next-value)
        (conj out byte)
        (recur next-value (conj out (bit-or byte 0x80)))))))

(defn sleb32
  "Signed LEB128 encoding for i32 constants."
  [n]
  (loop [value (long n)
         out []]
    (let [byte (bit-and value 0x7f)
          shifted (bit-shift-right value 7)
          sign-set? (pos? (bit-and byte 0x40))
          done? (or (and (zero? shifted) (not sign-set?))
                    (and (= -1 shifted) sign-set?))]
      (if done?
        (conj out byte)
        (recur shifted (conj out (bit-or byte 0x80)))))))

(defn f32-le-bytes
  "4 little-endian bytes for `n`'s IEEE-754 single-precision bit pattern
  (WASM binary format §5.4.3 -- `f32.const`'s immediate is NOT LEB128, it's
  4 raw bytes)."
  [n]
  (let [bits (Float/floatToIntBits (float n))]
    [(bit-and bits 0xff)
     (bit-and (bit-shift-right bits 8) 0xff)
     (bit-and (bit-shift-right bits 16) 0xff)
     (bit-and (bit-shift-right bits 24) 0xff)]))

(defn bcat
  "Concatenate any number of byte sequences into one flat vector."
  [& parts]
  (vec (mapcat identity parts)))

(defn utf8-bytes
  "UTF-8 encode s into a vector of unsigned (0-255) byte values."
  [s]
  (mapv #(bit-and % 0xff) (.getBytes (str s) "UTF-8")))

(defn literal-bytes
  "Byte vector for a string or all-integer vector literal, or nil for
  anything else (used to recognize memory-backed literals)."
  [value]
  (cond
    (string? value) (utf8-bytes value)
    (and (vector? value)
         (seq value)
         (every? integer? value)) (mapv #(bit-and % 0xff) value)
    :else nil))

(defn collect-memory-literals
  "Distinct string/byte-vector literals referenced anywhere in forms, in
  first-seen order (candidates for static memory layout)."
  [forms]
  (let [literals (atom [])]
    (doseq [form forms]
      (walk-forms
       (fn [node]
         (when (or (string? node)
                   (vector? node))
           (when (literal-bytes node)
             (swap! literals conj node))))
       form))
    (vec (distinct @literals))))

(defn memory-layout
  "Assign each distinct memory literal in forms a static offset (starting at
  1024) and length: `{literal {:offset :bytes :length}}`."
  [forms]
  (loop [literals (collect-memory-literals forms)
         offset 1024
         layout {}]
    (if-let [literal (first literals)]
      (let [bs (literal-bytes literal)]
        (recur (next literals)
               (+ offset (count bs))
               (assoc layout literal {:offset offset
                                      :bytes bs
                                      :length (count bs)})))
      layout)))

(defn align-to
  "Round n up to the next multiple of alignment (n unchanged if already
  aligned)."
  [n alignment]
  (let [rem (mod n alignment)]
    (if (zero? rem)
      n
      (+ n (- alignment rem)))))

(defn heap-base
  "First 16-byte-aligned address after every static memory-layout entry (at
  least 2048, leaving room below the layout for other reserved use)."
  [layout]
  (align-to
   (reduce max 2048 (map (fn [[_ entry]]
                           (+ (:offset entry) (:length entry)))
                         layout))
   16))

(def wasm-cap-kind-ids
  "Capability kind -> deterministic i32 id for the `cap_acquire` wasm import.
  Only kinds whose contract capability maps to exactly one kind are exposed
  (the id reuses the contract capability id, keeping the boundary 1:1);
  many-kind capabilities (clipboard, keychain, fs) stay interpreter-only in
  this slice."
  (let [kinds-by-cap (reduce (fn [acc [kind cap]]
                               (update acc cap (fnil conj #{}) kind))
                             {}
                             kind->capability)]
    (into {}
          (keep (fn [[kind cap]]
                  (when (= 1 (count (get kinds-by-cap cap)))
                    [kind (capability-id cap)])))
          kind->capability)))

(defn vec-bytes
  "Encode a WASM binary `vec(item)`: a uleb128 item count followed by the
  items' bytes concatenated."
  [items]
  (bcat (uleb (count items)) (mapcat identity items)))

(defn section
  "Encode one WASM binary section: a 1-byte section id followed by a
  uleb128-length-prefixed payload."
  [id payload]
  (bcat [id] (uleb (count payload)) payload))

(declare compile-wasm-fold compile-wasm-fold-type compiled-result-type local-index
         local-type merge-local-types local-decls function-param-types
         function-result-type symbol-key wasm-valtypes)

(defn desugar-and
  "Rewrite a Kotoba `(and a b c ...)` form into nested `let`/`if` so
  compile-wasm-expr's EXISTING if/let lowering (0x21 local.set + 0x20
  local.get, the [0x04 0x7f ... 0x05 ... 0x0b] if/else/end block) encodes the
  short-circuit — no new WASM instruction pattern is introduced. Each
  argument is compiled and evaluated at most once: `(and a b)` becomes
  `(let [t a] (if t b t))`, i.e. bind a's value once, branch on it, and reuse
  the bound value (not a re-evaluation of `a`) as the falsy result. Recurses
  for 3+ args: `(and a b c)` => `(let [t a] (if t (and b c) t))`. `(and)` is
  vacuously truthy (`1`, kotoba's i32 true); `(and a)` is just `a`."
  [args]
  (cond
    (empty? args) 1
    (empty? (rest args)) (first args)
    :else (let [tmp (gensym "and-tmp__")]
            (list 'let [tmp (first args)]
                  (list 'if tmp (desugar-and (rest args)) tmp)))))

(defn desugar-or
  "Mirror of desugar-and for `(or a b c ...)`: `(or a b)` becomes
  `(let [t a] (if t t b))` — bind a's value once, return it if truthy,
  otherwise fall through to b. `(or)` is vacuously falsy (`0`); `(or a)` is
  just `a`."
  [args]
  (cond
    (empty? args) 0
    (empty? (rest args)) (first args)
    :else (let [tmp (gensym "or-tmp__")]
            (list 'let [tmp (first args)]
                  (list 'if tmp tmp (desugar-or (rest args)))))))

;; ADR-2607150000: keyword/map literal support, sharing the FNV-1a interning
;; approach kotoba-lang/compiler uses for the same feature, but 32-bit here
;; to match this repo's default :i32 numeric domain (compiler/'s is i64).
(def max-get-unroll-depth
  "Fixed unroll depth for the `get` special form's bounded pair-list scan
  (see its case-dispatch docstring in compile-wasm-expr) -- a map literal
  or assoc-chain deeper than this is not an admission error here (unlike
  kotoba-lang/compiler's fuel-bounded recursive version); a `get` miss past
  this depth just returns the default early. 32 comfortably covers the
  small option-map-shaped literals this feature targets."
  32)

(defn- fnv1a-i32
  "Deterministic 32-bit FNV-1a hash of S's UTF-8 bytes, used to intern
  keyword literals as distinct i32 constants (ADR-2607150000) -- mirrors
  kotoba-lang/compiler's 64-bit version, narrowed to i32 to match this
  repo's default numeric domain. Not clojure.core/hash, for the same
  reproducibility reason compiler/'s version documents. Collision
  probability for one module's realistically small keyword vocabulary is
  low but non-zero -- a known, documented limitation, not eliminated."
  [^String s]
  (let [bs (.getBytes s "UTF-8")
        offset-basis (unchecked-int 0x811c9dc5)
        prime (unchecked-int 0x01000193)]
    (reduce (fn [h b] (unchecked-multiply-int (unchecked-int (bit-xor h (bit-and (int b) 0xff))) prime))
            offset-basis bs)))

(defn- keyword->i32 [kw] (fnv1a-i32 (str kw)))

(defn desugar-map
  "`{:k1 v1 :k2 v2}` -> `(pair (pair k1 v1) (pair (pair k2 v2) 0))`, reusing
  the pair/pair-first/pair-second primitives above entirely (ADR-2607150000).
  Entries sorted by `(pr-str k)` for deterministic codegen regardless of
  Clojure's own map-literal iteration order (unspecified for >8 entries)."
  [form]
  (let [entries (sort-by (fn [[k _]] (pr-str k)) (seq form))]
    (reduce (fn [tail [k v]] (list 'pair (list 'pair k v) tail)) 0 (reverse entries))))

(defn compile-wasm-expr
  "Compile one Kotoba expression to a WASM instruction sequence. Returns
  `{:bytes :local-count :local-types :result-type}` on success, or
  `{:problem {:kotoba.wasm/problem ...}}` on failure (unknown local,
  unsupported form/op, arity/type mismatch, etc.) — problems short-circuit
  up through composite expressions. `locals` maps local symbol -> index/type
  entry; `fns` carries the compile-time context (function indexes, memory
  layout, declared result types)."
  ([form locals] (compile-wasm-expr form locals {}))
  ([form locals fns]
  (cond
    (integer? form)
    {:bytes (bcat [0x41] (sleb32 form))
     :result-type :i32}

    ;; ADR-2607150000: keyword literals intern to a deterministic i32
    ;; constant; map literals desugar to a pair-chain (desugar-map above)
    ;; and recompile through this same function.
    (keyword? form)
    {:bytes (bcat [0x41] (sleb32 (keyword->i32 form)))
     :result-type :i32}

    (map? form)
    (compile-wasm-expr (desugar-map form) locals fns)

    (symbol? form)
    (if-let [entry (get locals (symbol-key form))]
      {:bytes (bcat [0x20] (uleb (local-index entry)))
       :result-type (local-type entry)}
      {:problem {:kotoba.wasm/problem :unknown-local
                 :kotoba.wasm/symbol (str form)}})

    (seq? form)
    (let [[op & args] form]
      (case op
        do (loop [remaining args
                  out []
                  local-types []]
             (if-let [expr (first remaining)]
               (let [compiled (compile-wasm-expr expr locals fns)]
                 (if (:problem compiled)
                   compiled
                   (if (next remaining)
                     (recur (next remaining)
                            (bcat out (:bytes compiled) [0x1a])
                            (into local-types (merge-local-types compiled)))
                     {:bytes (bcat out (:bytes compiled))
                      :local-count (+ (count local-types)
                                      (count (merge-local-types compiled)))
                      :local-types (into local-types (merge-local-types compiled))
                      :result-type (compiled-result-type compiled)})))
               {:bytes out
                :local-count (count local-types)
                :local-types local-types}))

        let (let [[bindings & body] args
                  pairs (partition 2 bindings)]
              (loop [pairs pairs
                     locals locals
                     next-local (count locals)
                     local-types []
                     out []]
                (if-let [[name value] (first pairs)]
                  (let [compiled (compile-wasm-expr value locals fns)]
                    (if (:problem compiled)
                      compiled
                      (recur (next pairs)
                             (assoc locals (symbol-key name)
                                    {:idx next-local
                                     :type (compiled-result-type compiled)})
                             (inc next-local)
                             (conj (into local-types (merge-local-types compiled))
                                   (compiled-result-type compiled))
                             (bcat out (:bytes compiled) [0x21] (uleb next-local)))))
                  (let [compiled (compile-wasm-expr (cons 'do body) locals fns)]
                    (if (:problem compiled)
                      compiled
                      {:bytes (bcat out (:bytes compiled))
                       :local-count (+ (count local-types)
                                       (count (merge-local-types compiled)))
                       :local-types (into local-types (merge-local-types compiled))
                       :result-type (compiled-result-type compiled)})))))

        if (let [[test then else] args
                 test-compiled (compile-wasm-expr test locals fns)
                 then-compiled (compile-wasm-expr then locals fns)
                 else-compiled (compile-wasm-expr else locals fns)]
             (cond
               (:problem test-compiled) test-compiled
               (:problem then-compiled) then-compiled
               (:problem else-compiled) else-compiled
               :else {:bytes (bcat (:bytes test-compiled)
                                   [0x04 (wasm-valtypes (compiled-result-type then-compiled))]
                                   (:bytes then-compiled)
                                   [0x05]
                                   (:bytes else-compiled)
                                   [0x0b])
                      :local-count (max (:local-count test-compiled 0)
                                        (:local-count then-compiled 0)
                                        (:local-count else-compiled 0))
                      :local-types (merge-local-types test-compiled then-compiled else-compiled)
                      :result-type (compiled-result-type then-compiled)}))

        and (compile-wasm-expr (desugar-and args) locals fns)

        or (compile-wasm-expr (desugar-or args) locals fns)

        when (let [[test & body] args]
               (compile-wasm-expr (list 'if test (cons 'do body) 0) locals fns))

        ;; ADR-2607150000: pair/pair-first/pair-second, keyword/map literals,
        ;; and get/assoc, ported (in spirit) from kotoba-lang/compiler's
        ;; version of the same feature -- but implemented here on top of
        ;; this repo's OWN existing primitives (alloc/i32-store!/mem-i32-at)
        ;; rather than a host import, since kotoba/ (unlike compiler/)
        ;; already exposes raw linear memory to guest code directly. A pair
        ;; is 8 bytes: left i32 @ offset 0, right i32 @ offset 4.
        pair
        (if (not= 2 (count args))
          {:problem {:kotoba.wasm/problem :arity :kotoba.wasm/op "pair"
                     :kotoba.wasm/expected 2 :kotoba.wasm/actual (count args)}}
          (let [[l r] args
                ptr (gensym "pair-ptr__")]
            (compile-wasm-expr
             (list 'let [ptr (list 'alloc 8)]
                   (list 'i32-store! ptr 0 l)
                   (list 'i32-store! ptr 4 r)
                   ptr)
             locals fns)))

        pair-first
        (if (not= 1 (count args))
          {:problem {:kotoba.wasm/problem :arity :kotoba.wasm/op "pair-first"
                     :kotoba.wasm/expected 1 :kotoba.wasm/actual (count args)}}
          (compile-wasm-expr (list 'mem-i32-at (first args) 0) locals fns))

        pair-second
        (if (not= 1 (count args))
          {:problem {:kotoba.wasm/problem :arity :kotoba.wasm/op "pair-second"
                     :kotoba.wasm/expected 1 :kotoba.wasm/actual (count args)}}
          (compile-wasm-expr (list 'mem-i32-at (first args) 4) locals fns))

        ;; get is a BOUNDED unroll (not a synthesized recursive helper like
        ;; compiler/'s __kotoba_map_get) -- this repo's function-table has
        ;; no single injection point analogous to compiler/'s `analyze`
        ;; (function-defs is consumed from 4 separate call sites), so
        ;; unrolling to a fixed depth avoids threading a new function
        ;; through all of them. m/k/default are each bound ONCE via `let`
        ;; so the unrolled body only re-references cheap local.gets, not
        ;; re-evaluated subexpressions. A map deeper than
        ;; max-get-unroll-depth silently returns default past that depth --
        ;; documented limitation, not a trap (unlike compiler/'s fuel-bound
        ;; recursive version).
        get
        (if (not (<= 2 (count args) 3))
          {:problem {:kotoba.wasm/problem :arity :kotoba.wasm/op "get"
                     :kotoba.wasm/expected "2 or 3" :kotoba.wasm/actual (count args)}}
          (let [[m k default-form] args
                default (if (some? default-form) default-form 0)
                m-sym (gensym "get-m__") k-sym (gensym "get-k__") d-sym (gensym "get-d__")
                unroll (fn unroll [cur depth]
                         (if (zero? depth)
                           d-sym
                           (list 'if (list '= cur 0)
                                 d-sym
                                 (list 'if (list '= (list 'pair-first (list 'pair-first cur)) k-sym)
                                       (list 'pair-second (list 'pair-first cur))
                                       (unroll (list 'pair-second cur) (dec depth))))))]
            (compile-wasm-expr
             (list 'let [m-sym m k-sym k d-sym default]
                   (unroll m-sym max-get-unroll-depth))
             locals fns)))

        ;; assoc is a pure O(1) desugar (prepend + shadow, variadic k/v
        ;; pairs) -- same as compiler/'s version, no unroll/recursion needed.
        assoc
        (if (not (and (>= (count args) 3) (odd? (count args))))
          {:problem {:kotoba.wasm/problem :arity :kotoba.wasm/op "assoc"
                     :kotoba.wasm/expected "an odd count >= 3 (map, then key/value pairs)"
                     :kotoba.wasm/actual (count args)}}
          (let [[m & kvs] args]
            (compile-wasm-expr
             (reduce (fn [acc-map [k v]] (list 'pair (list 'pair k v) acc-map))
                     m (partition 2 kvs))
             locals fns)))

        + (compile-wasm-fold 0x6a args locals fns)
        - (compile-wasm-fold 0x6b args locals fns)
        * (compile-wasm-fold 0x6c args locals fns)
        quot (compile-wasm-fold 0x6d args locals fns)
        / (compile-wasm-fold 0x6d args locals fns)
        rem (compile-wasm-fold 0x6f args locals fns)
        mod (compile-wasm-fold 0x6f args locals fns)
        = (compile-wasm-fold 0x46 args locals fns)
        < (compile-wasm-fold 0x48 args locals fns)
        > (compile-wasm-fold 0x4a args locals fns)
        <= (compile-wasm-fold 0x4c args locals fns)
        >= (compile-wasm-fold 0x4e args locals fns)
        bit-and (compile-wasm-fold 0x71 args locals fns)
        bit-or (compile-wasm-fold 0x72 args locals fns)
        bit-xor (compile-wasm-fold 0x73 args locals fns)
        bit-shift-left (compile-wasm-fold 0x74 args locals fns)
        bit-shift-right (compile-wasm-fold 0x75 args locals fns)
        unsigned-bit-shift-right (compile-wasm-fold 0x76 args locals fns)
        i64 (let [value (first args)]
              (cond
                (not= 1 (count args))
                {:problem {:kotoba.wasm/problem :arity
                           :kotoba.wasm/op "i64"
                           :kotoba.wasm/expected 1
                           :kotoba.wasm/actual (count args)}}

                (integer? value)
                {:bytes (bcat [0x42] (sleb32 value))
                 :result-type :i64}

                :else
                {:problem {:kotoba.wasm/problem :unsupported-i64-literal
                           :kotoba.wasm/form (pr-str value)}}))
        i64+ (compile-wasm-fold-type 0x7c args locals fns :i64)
        i64- (compile-wasm-fold-type 0x7d args locals fns :i64)
        i64* (compile-wasm-fold-type 0x7e args locals fns :i64)
        i64and (compile-wasm-fold-type 0x83 args locals fns :i64)
        i64or (compile-wasm-fold-type 0x84 args locals fns :i64)
        i64xor (compile-wasm-fold-type 0x85 args locals fns :i64)
        i64shl (compile-wasm-fold-type 0x86 args locals fns :i64)
        i64shr (compile-wasm-fold-type 0x87 args locals fns :i64)
        i64ushr (compile-wasm-fold-type 0x88 args locals fns :i64)
        f32 (let [value (first args)]
              (cond
                (not= 1 (count args))
                {:problem {:kotoba.wasm/problem :arity
                           :kotoba.wasm/op "f32"
                           :kotoba.wasm/expected 1
                           :kotoba.wasm/actual (count args)}}

                (number? value)
                {:bytes (bcat [0x43] (f32-le-bytes value))
                 :result-type :f32}

                :else
                {:problem {:kotoba.wasm/problem :unsupported-f32-literal
                           :kotoba.wasm/form (pr-str value)}}))
        f32+ (compile-wasm-fold-type 0x92 args locals fns :f32)
        f32- (compile-wasm-fold-type 0x93 args locals fns :f32)
        f32* (compile-wasm-fold-type 0x94 args locals fns :f32)
        f32div (compile-wasm-fold-type 0x95 args locals fns :f32)
        f32sqrt (if (not= 1 (count args))
                  {:problem {:kotoba.wasm/problem :arity
                             :kotoba.wasm/op "f32sqrt"
                             :kotoba.wasm/expected 1
                             :kotoba.wasm/actual (count args)}}
                  (let [compiled (compile-wasm-expr (first args) locals fns)]
                    (if (:problem compiled)
                      compiled
                      (assoc compiled
                             :bytes (bcat (:bytes compiled) [0x91])
                             :result-type :f32))))
        f32neg (if (not= 1 (count args))
                 {:problem {:kotoba.wasm/problem :arity
                            :kotoba.wasm/op "f32neg"
                            :kotoba.wasm/expected 1
                            :kotoba.wasm/actual (count args)}}
                 (let [compiled (compile-wasm-expr (first args) locals fns)]
                   (if (:problem compiled)
                     compiled
                     (assoc compiled
                            :bytes (bcat (:bytes compiled) [0x8c])
                            :result-type :f32))))
        ;; f32 comparisons take :f32 args but produce an :i32 boolean --
        ;; compile-wasm-fold-type assumes homogeneous arg/result typing
        ;; (right for f32+ etc., wrong here), so fold untyped and stamp the
        ;; result-type after, matching plain `=`/`</`>`'s existing looser
        ;; (unchecked-arg-type) convention above.
        f32= (let [c (compile-wasm-fold 0x5b args locals fns)] (cond-> c (not (:problem c)) (assoc :result-type :i32)))
        f32< (let [c (compile-wasm-fold 0x5d args locals fns)] (cond-> c (not (:problem c)) (assoc :result-type :i32)))
        f32> (let [c (compile-wasm-fold 0x5e args locals fns)] (cond-> c (not (:problem c)) (assoc :result-type :i32)))
        f32<= (let [c (compile-wasm-fold 0x5f args locals fns)] (cond-> c (not (:problem c)) (assoc :result-type :i32)))
        f32>= (let [c (compile-wasm-fold 0x60 args locals fns)] (cond-> c (not (:problem c)) (assoc :result-type :i32)))
        call-indirect (let [[idx arg] args
                            idx-compiled (compile-wasm-expr idx locals fns)
                            arg-compiled (compile-wasm-expr arg locals fns)]
                        (cond
                          (not= 2 (count args))
                          {:problem {:kotoba.wasm/problem :arity
                                     :kotoba.wasm/op "call-indirect"
                                     :kotoba.wasm/expected 2
                                     :kotoba.wasm/actual (count args)}}

                          (:problem idx-compiled)
                          idx-compiled

                          (:problem arg-compiled)
                          arg-compiled

                          (not= :i32 (compiled-result-type idx-compiled))
                          {:problem {:kotoba.wasm/problem :type-mismatch
                                     :kotoba.wasm/op "call-indirect"
                                     :kotoba.wasm/expected :i32}}

                          (not= :i32 (compiled-result-type arg-compiled))
                          {:problem {:kotoba.wasm/problem :type-mismatch
                                     :kotoba.wasm/op "call-indirect"
                                     :kotoba.wasm/expected :i32}}

                          :else
                          {:bytes (bcat (:bytes arg-compiled)
                                        (:bytes idx-compiled)
                                        [0x11]
                                        (uleb (:indirect-type-index fns 0))
                                        [0x00])
                           :local-count (+ (count (merge-local-types idx-compiled))
                                           (count (merge-local-types arg-compiled)))
                           :local-types (merge-local-types idx-compiled arg-compiled)
                           :result-type :i32}))
        alloc (let [size (first args)
                    size-compiled (compile-wasm-expr size locals fns)]
                (cond
                  (not= 1 (count args))
                  {:problem {:kotoba.wasm/problem :arity
                             :kotoba.wasm/op "alloc"
                             :kotoba.wasm/expected 1
                             :kotoba.wasm/actual (count args)}}

                  (:problem size-compiled)
                  size-compiled

                  :else
                  {:bytes (bcat [0x23 0x00 0x23 0x00]
                                (:bytes size-compiled)
                                [0x6a 0x24 0x00])
                   :local-count (:local-count size-compiled 0)}))
        alloc-checked (let [size (first args)
                            size-for-check (compile-wasm-expr size locals fns)
                            allocation (compile-wasm-expr (list 'alloc size) locals fns)]
                        (cond
                          (not= 1 (count args))
                          {:problem {:kotoba.wasm/problem :arity
                                     :kotoba.wasm/op "alloc-checked"
                                     :kotoba.wasm/expected 1
                                     :kotoba.wasm/actual (count args)}}

                          (:problem size-for-check)
                          size-for-check

                          (:problem allocation)
                          allocation

                          :else
                          {:bytes (bcat [0x23 0x00]
                                        (:bytes size-for-check)
                                        [0x6a 0x3f 0x00 0x41]
                                        (sleb32 65536)
                                        [0x6c 0x4c 0x04 0x7f]
                                        (:bytes allocation)
                                        [0x05 0x41]
                                        (sleb32 -1)
                                        [0x0b])
                           :local-count (max (:local-count size-for-check 0)
                                             (:local-count allocation 0))}))
        str-len (let [value (first args)]
                  (if-let [bytes (and (string? value) (literal-bytes value))]
                    {:bytes (bcat [0x41] (sleb32 (count bytes)))}
                    {:problem {:kotoba.wasm/problem :unsupported-string-op
                               :kotoba.wasm/op "str-len"}}))
        str-ptr (let [value (first args)
                      entry (get (:memory fns) value)]
                  (if (and (string? value) entry)
                    {:bytes (bcat [0x41] (sleb32 (:offset entry)))}
                    {:problem {:kotoba.wasm/problem :unsupported-string-op
                               :kotoba.wasm/op "str-ptr"}}))
        bytes-ptr (let [value (first args)
                        entry (get (:memory fns) value)]
                    (if (and (vector? value) entry)
                      {:bytes (bcat [0x41] (sleb32 (:offset entry)))}
                      {:problem {:kotoba.wasm/problem :unsupported-bytes-op
                                 :kotoba.wasm/op "bytes-ptr"}}))
        bytes-len (let [value (first args)]
                    (if-let [bytes (and (vector? value) (literal-bytes value))]
                      {:bytes (bcat [0x41] (sleb32 (count bytes)))}
                      {:problem {:kotoba.wasm/problem :unsupported-bytes-op
                                 :kotoba.wasm/op "bytes-len"}}))
        memory-pages (if (empty? args)
                       {:bytes [0x3f 0x00]}
                       {:problem {:kotoba.wasm/problem :arity
                                  :kotoba.wasm/op "memory-pages"
                                  :kotoba.wasm/expected 0
                                  :kotoba.wasm/actual (count args)}})
        memory-grow (let [pages (first args)
                          pages-compiled (compile-wasm-expr pages locals fns)]
                      (cond
                        (not= 1 (count args))
                        {:problem {:kotoba.wasm/problem :arity
                                   :kotoba.wasm/op "memory-grow"
                                   :kotoba.wasm/expected 1
                                   :kotoba.wasm/actual (count args)}}

                        (:problem pages-compiled)
                        pages-compiled

                        :else
                        {:bytes (bcat (:bytes pages-compiled) [0x40 0x00])
                         :local-count (:local-count pages-compiled 0)}))
        mem-byte-at (let [[ptr idx] args
                          addr-compiled (compile-wasm-expr (list '+ ptr idx) locals fns)]
                      (if (:problem addr-compiled)
                        addr-compiled
                        {:bytes (bcat (:bytes addr-compiled) [0x2d 0x00 0x00])
                         :local-count (:local-count addr-compiled 0)}))
        mem-i32-at (let [[ptr offset] args
                         addr-compiled (compile-wasm-expr (list '+ ptr offset) locals fns)]
                     (if (:problem addr-compiled)
                       addr-compiled
                       {:bytes (bcat (:bytes addr-compiled) [0x28 0x02 0x00])
                        :local-count (:local-count addr-compiled 0)}))
        byte-store! (let [[ptr idx value] args
                          addr-compiled (compile-wasm-expr (list '+ ptr idx) locals fns)
                          value-compiled (compile-wasm-expr value locals fns)]
                      (cond
                        (:problem addr-compiled) addr-compiled
                        (:problem value-compiled) value-compiled
                        :else {:bytes (bcat (:bytes addr-compiled)
                                            (:bytes value-compiled)
                                            [0x3a 0x00 0x00]
                                            (:bytes value-compiled))
                               :local-count (max (:local-count addr-compiled 0)
                                                 (:local-count value-compiled 0))}))
        i32-store! (let [[ptr offset value] args
                         addr-compiled (compile-wasm-expr (list '+ ptr offset) locals fns)
                         value-compiled (compile-wasm-expr value locals fns)]
                     (cond
                       (:problem addr-compiled) addr-compiled
                       (:problem value-compiled) value-compiled
                       :else {:bytes (bcat (:bytes addr-compiled)
                                           (:bytes value-compiled)
                                           [0x36 0x02 0x00]
                                           (:bytes value-compiled))
                              :local-count (max (:local-count addr-compiled 0)
                                                (:local-count value-compiled 0))}))
        byte-at (let [[value idx] args
                      bytes (literal-bytes value)]
                  (cond
                    (nil? bytes)
                    {:problem {:kotoba.wasm/problem :unsupported-bytes-op
                               :kotoba.wasm/op "byte-at"}}

                    (not (integer? idx))
                    {:problem {:kotoba.wasm/problem :unsupported-bytes-index
                               :kotoba.wasm/op "byte-at"}}

                    (or (neg? idx) (<= (count bytes) idx))
                    {:problem {:kotoba.wasm/problem :bytes-index-out-of-bounds
                               :kotoba.wasm/op "byte-at"
                               :kotoba.wasm/index idx
                               :kotoba.wasm/length (count bytes)}}

                    :else
                    {:bytes (bcat [0x41] (sleb32 (nth bytes idx)))}))
        cap-acquire (let [[kind resource] args
                          kind-id (get wasm-cap-kind-ids kind)
                          entry (get (:memory fns) resource)]
                      (cond
                        (not= 2 (count args))
                        {:problem {:kotoba.wasm/problem :arity
                                   :kotoba.wasm/op "cap-acquire"
                                   :kotoba.wasm/expected 2
                                   :kotoba.wasm/actual (count args)}}

                        (nil? kind-id)
                        {:problem {:kotoba.wasm/problem :unsupported-capability-kind
                                   :kotoba.wasm/op "cap-acquire"
                                   :kotoba.wasm/kind (pr-str kind)}}

                        (not (and (string? resource) entry))
                        {:problem {:kotoba.wasm/problem :unsupported-cap-resource
                                   :kotoba.wasm/op "cap-acquire"}}

                        :else
                        {:bytes (bcat [0x41] (sleb32 kind-id)
                                      [0x41] (sleb32 (:offset entry))
                                      [0x41] (sleb32 (:length entry))
                                      [0x10] (uleb (get fns 'cap-acquire)))
                         :result-type :i64}))
        has-capability? (let [cap (first args)]
                          (if-let [id (capability-id cap)]
                            {:bytes (bcat [0x41] (sleb32 id)
                                          [0x10] (uleb (get fns 'has-capability? 0)))}
                            {:problem {:kotoba.wasm/problem :unknown-capability
                                       :kotoba.wasm/capability (capability-name cap)}}))
        zero? (let [compiled (compile-wasm-expr (first args) locals fns)]
                (if (:problem compiled)
                  compiled
                  {:bytes (bcat (:bytes compiled) [0x45])
                   :local-count (:local-count compiled 0)}))
        not (let [compiled (compile-wasm-expr (first args) locals fns)]
              (if (:problem compiled)
                compiled
                {:bytes (bcat (:bytes compiled) [0x45])
                 :local-count (:local-count compiled 0)}))
        pos? (compile-wasm-expr (list '> (first args) 0) locals fns)
        neg? (compile-wasm-expr (list '< (first args) 0) locals fns)
        result-ok? (compile-wasm-expr (list '>= (first args) 0) locals fns)
        result-err? (compile-wasm-expr (list '< (first args) 0) locals fns)
        result-write! (let [[record-ptr value] args
                            record-for-status (compile-wasm-expr record-ptr locals fns)
                            record-for-value (compile-wasm-expr (list '+ record-ptr 4) locals fns)
                            record-for-return (compile-wasm-expr record-ptr locals fns)
                            status (compile-wasm-expr (list 'if (list 'result-err? value) 1 0) locals fns)
                            raw-value (compile-wasm-expr value locals fns)]
                        (cond
                          (:problem record-for-status) record-for-status
                          (:problem record-for-value) record-for-value
                          (:problem record-for-return) record-for-return
                          (:problem status) status
                          (:problem raw-value) raw-value
                          :else {:bytes (bcat (:bytes record-for-status)
                                              (:bytes status)
                                              [0x36 0x02 0x00]
                                              (:bytes record-for-value)
                                              (:bytes raw-value)
                                              [0x36 0x02 0x00]
                                              (:bytes record-for-return))
                                 :local-count (reduce max 0 (map #(:local-count % 0)
                                                                  [record-for-status
                                                                   record-for-value
                                                                   record-for-return
                                                                   status
                                                                   raw-value]))}))
        result-status (compile-wasm-expr (list 'mem-i32-at (first args) 0) locals fns)
        result-value (compile-wasm-expr (list 'mem-i32-at (first args) 4) locals fns)
        inc (compile-wasm-expr `(+ ~(first args) 1) locals fns)
        dec (compile-wasm-expr `(- ~(first args) 1) locals fns)
        (if-let [import (get host-imports op)]
          (let [arity (count (:params import))]
            (if (not= arity (count args))
              {:problem {:kotoba.wasm/problem :arity
                         :kotoba.wasm/op (str op)
                         :kotoba.wasm/expected arity
                         :kotoba.wasm/actual (count args)}}
              (loop [remaining args
                     out []
                     local-types []]
                (if-let [arg (first remaining)]
                  (let [compiled (compile-wasm-expr arg locals fns)]
                    (if (:problem compiled)
                      compiled
                      (recur (next remaining)
                             (bcat out (:bytes compiled))
                             (into local-types (merge-local-types compiled)))))
                  {:bytes (bcat out [0x10] (uleb (get fns op)))
                   :local-count (count local-types)
                   :local-types local-types
                   :result-type (:result import)}))))
          (if-let [fn-index (get fns op)]
            (loop [remaining args
                   out []
                   local-types []]
              (if-let [arg (first remaining)]
                (let [compiled (compile-wasm-expr arg locals fns)]
                  (if (:problem compiled)
                    compiled
                    (recur (next remaining)
                           (bcat out (:bytes compiled))
                           (into local-types (merge-local-types compiled)))))
                {:bytes (bcat out [0x10] (uleb fn-index))
                 :local-count (count local-types)
                 :local-types local-types
                 :result-type (get-in fns [:fn-result-types op] :i32)}))
            {:problem (guest-grammar/with-hint
                        {:kotoba.wasm/problem :unsupported-op
                         :kotoba.wasm/op (str op)}
                        op)}))))

    :else
    {:problem {:kotoba.wasm/problem :unsupported-form
               :kotoba.wasm/form (pr-str form)}})))

(defn compile-wasm-fold
  "Compile a variadic numeric/comparison op (e.g. `+`) by folding opcode
  left-to-right over 2+ compiled args (a single arg just compiles through
  unchanged). Requires at least one arg."
  ([opcode args locals] (compile-wasm-fold opcode args locals {}))
  ([opcode args locals fns]
  (cond
    (empty? args)
    {:problem {:kotoba.wasm/problem :arity
               :kotoba.wasm/message "numeric wasm op requires at least one argument"}}

    (= 1 (count args))
    (compile-wasm-expr (first args) locals fns)

    :else
    (loop [remaining (rest args)
           compiled (compile-wasm-expr (first args) locals fns)
           local-types (merge-local-types compiled)]
      (if (:problem compiled)
        compiled
        (if-let [arg (first remaining)]
          (let [next-compiled (compile-wasm-expr arg locals fns)]
            (if (:problem next-compiled)
              next-compiled
              (recur (next remaining)
                     {:bytes (bcat (:bytes compiled) (:bytes next-compiled) [opcode])}
                     (into local-types (merge-local-types next-compiled)))))
          (assoc compiled
                 :local-count (count local-types)
                 :local-types local-types)))))))

(defn compile-wasm-fold-type
  "Like compile-wasm-fold, but for typed ops (e.g. `i64+`): asserts every
  arg's compiled result-type matches result-type (a :type-mismatch problem
  otherwise) and tags the fold's own result-type accordingly."
  [opcode args locals fns result-type]
  (let [compiled (compile-wasm-fold opcode args locals fns)]
    (cond
      (:problem compiled) compiled
      (not-every? #(= result-type (or (:result-type %) :i32))
                  (map #(compile-wasm-expr % locals fns) args))
      {:problem {:kotoba.wasm/problem :type-mismatch
                 :kotoba.wasm/expected result-type}}
      :else (assoc compiled :result-type result-type))))

(defn main-function
  "Parsed def-map for the `main` function in forms, or nil if forms declares
  none."
  [forms]
  (get (into {} (keep function-def forms)) 'main))

(defn function-defs
  "All top-level `defn` forms parsed to `[name def-map]` pairs, in source
  order."
  [forms]
  (vec (keep function-def forms)))

(defn uses-call-indirect?
  "True if `call-indirect` appears anywhere in forms (triggers emitting a
  WASM table/element section)."
  [forms]
  (let [found? (atom false)]
    (doseq [form forms]
      (walk-forms
       (fn [node]
         (when (and (seq? node) (= 'call-indirect (first node)))
           (reset! found? true)))
       form))
    @found?))

(def wasm-valtypes
  {:i32 0x7f
   :i64 0x7e
   :f32 0x7d})

(defn symbol-key
  "Normalize sym to an unqualified symbol (drop any namespace) for use as a
  locals/params map key."
  [sym]
  (symbol (name sym)))

(defn annotated-wasm-type
  "Wasm value type for a param symbol: `^:i64` params and `^{:cap <kind>}`
  typed capability params (whose runtime representation is the opaque i64
  handle) lower to :i64; `^:f32` params lower to :f32; everything else is
  :i32."
  [sym]
  (cond
    (or (:i64 (meta sym)) (some? (cap-param-kind sym))) :i64
    (:f32 (meta sym)) :f32
    :else :i32))

(defn function-result-type
  "Declared WASM result type for a `[name def-map]` function entry: :i64 if
  the fn name has `^:i64` metadata, :f32 if `^:f32`, else nil (void)."
  [[name _f]]
  (cond
    (:i64 (meta name)) :i64
    (:f32 (meta name)) :f32
    :else nil))

(defn function-param-types
  "WASM value types for a `[name def-map]` function entry's params, via
  annotated-wasm-type."
  [[_name f]]
  (mapv annotated-wasm-type (:params f)))

(defn compiled-result-type
  "Result-type of a compile-wasm-expr result, defaulting to :i32 when
  unset."
  [compiled]
  (or (:result-type compiled) :i32))

(defn local-index
  "Numeric locals-table index of entry: entries are either a bare index or a
  `{:idx :type}` map."
  [entry]
  (if (map? entry) (:idx entry) entry))

(defn local-type
  "WASM value type of a locals-table entry, defaulting to :i32 for bare
  (untyped) entries."
  [entry]
  (if (map? entry) (:type entry) :i32))

(defn local-decls
  "Encode the WASM local-declarations vector for a function body: types
  grouped by consecutive run, each run as `(count type)`."
  [types]
  (if (seq types)
    (let [groups (partition-by identity types)]
      (bcat (uleb (count groups))
            (mapcat (fn [group]
                      (bcat (uleb (count group))
                            [(wasm-valtypes (first group))]))
                    groups)))
    [0x00]))

(defn merge-local-types
  "Combine the :local-types (or a :i32-filled placeholder sized by
  :local-count) of any number of compiled sub-expressions into one flat
  vector, for tracking a function's scratch-local types across composed
  compile-wasm-expr calls."
  [& compiled-values]
  (vec (mapcat #(or (:local-types %) (repeat (:local-count % 0) :i32))
               compiled-values)))

(defn wasm-fn-type
  "Encode one WASM function type entry: form byte 0x60, param types, and
  either a 1-result-type or a 0-count result vec."
  [{:keys [params result]}]
  (bcat [0x60]
        (uleb (count params))
        (map wasm-valtypes params)
        (if result
          [0x01 (wasm-valtypes result)]
          [0x00])))

(def wasm-magic-version
  [0x00 0x61 0x73 0x6d 0x01 0x00 0x00 0x00])

(defn import-entry
  "Encode one WASM import entry: length-prefixed module name, length-prefixed
  field name, function-import kind byte (0x00), and the type index."
  [module field type-index]
  (let [module-bytes (utf8-bytes module)
        field-bytes (utf8-bytes field)]
    (bcat (uleb (count module-bytes))
          module-bytes
          (uleb (count field-bytes))
          field-bytes
          [0x00]
          (uleb type-index))))

(defn export-entry
  "Encode one WASM export entry: length-prefixed name, export kind byte
  (e.g. 0x00 func, 0x02 memory), and the exported item's index."
  [name kind index]
  (let [name-bytes (utf8-bytes name)]
    (bcat (uleb (count name-bytes))
          name-bytes
          [kind]
          (uleb index))))

(defn global-entry
  "Encode one WASM mutable i32 global with the given constant initial
  value."
  [initial-value]
  (bcat [0x7f 0x01 0x41]
        (sleb32 initial-value)
        [0x0b]))

(defn table-entry
  "Encode one WASM funcref table entry (for `call_indirect`) with the given
  size."
  [size]
  [0x70 0x00 size])

(defn element-segment
  "Encode a WASM element segment that populates table index 0 (starting at
  offset 0) with function-indexes, for `call_indirect` targets."
  [function-indexes]
  (bcat [0x00 0x41 0x00 0x0b]
        (uleb (count function-indexes))
        (mapcat uleb function-indexes)))

(defn data-segment
  "Encode a WASM active data segment: memory index 0, a constant i32
  offset, and the byte payload."
  [offset bs]
  (bcat [0x00 0x41]
        (sleb32 offset)
        [0x0b]
        (uleb (count bs))
        bs))

(defn wasm-binary
  "Compile checked functions to WebAssembly. Every function is exported;
  command programs require zero-arity `main`, while game modules may expose
  `init` and/or `*-tick` systems instead."
  ([forms] (wasm-binary forms nil))
  ([forms _policy]
  (let [forms (lower-language-forms forms)
        defs (function-defs forms)
        indirect? (uses-call-indirect? forms)
        imports (required-host-imports forms)
        import-count (count imports)
        import-indexes (into {} (map-indexed (fn [idx op] [op idx]) imports))
        layout (memory-layout forms)
        heap-start (heap-base layout)
        fn-indexes (merge import-indexes
                          (into {} (map-indexed (fn [idx [name _]]
                                                   [name (+ import-count idx)])
                                                 defs)))
        declared-fn-result-types (into {} (map (fn [[name :as def]]
                                                 [name (or (function-result-type def) :i32)])
                                               defs))
        compile-context (assoc fn-indexes
                               :memory layout
                               :fn-result-types declared-fn-result-types
                               :indirect-type-index 0)
        main (get (into {} defs) 'main)
        module-entry? (some (fn [[name _]]
                              (or (= name 'init)
                                  (cstr/ends-with? (str name) "-tick")))
                            defs)]
    (cond
      (and (nil? main) (not module-entry?))
      {:kotoba.wasm/ok? false
       :kotoba.wasm/problems [{:kotoba.wasm/problem :missing-main}]}

      (and main (seq (:params main)))
      {:kotoba.wasm/ok? false
       :kotoba.wasm/problems [{:kotoba.wasm/problem :main-arity
                               :kotoba.wasm/expected 0
                               :kotoba.wasm/actual (count (:params main))}]}

      :else
      (let [compiled-fns
            (mapv (fn [[name f]]
                    (let [locals (into {} (map-indexed (fn [idx param]
                                                         [(symbol-key param)
                                                          {:idx idx
                                                           :type (annotated-wasm-type param)}])
                                                       (:params f)))
                          compiled (compile-wasm-expr (cons 'do (:body f)) locals compile-context)]
                      (assoc compiled
                             :name name
                             :param-count (count (:params f)))))
                  defs)]
        (if-let [problem (some :problem compiled-fns)]
          {:kotoba.wasm/ok? false
           :kotoba.wasm/problems [problem]}
          (let [import-metadata (mapv host-imports imports)
                import-signatures (mapv (fn [import]
                                           {:params (:params import)
                                            :result (:result import)})
                                         import-metadata)
                fn-signatures (mapv (fn [compiled]
                                       {:params (function-param-types
                                                (some #(when (= (:name compiled) (first %)) %) defs))
                                        :result (compiled-result-type compiled)})
                                     compiled-fns)
                indirect-signatures (when indirect?
                                      [{:params [:i32] :result :i32}])
                signatures (vec (distinct (concat indirect-signatures
                                                  import-signatures
                                                  fn-signatures)))
                type-index-by-signature (into {} (map-indexed (fn [idx signature]
                                                                [signature idx])
                                                              signatures))
                compiled-fns (mapv (fn [compiled signature]
                                     (assoc compiled :type-index
                                            (get type-index-by-signature signature)))
                                   compiled-fns
                                   fn-signatures)
                type-section (section 1 (vec-bytes (mapv wasm-fn-type signatures)))
                import-section (when (seq imports)
                                 (section 2
                                          (vec-bytes
                                           (mapv (fn [import]
                                                   (import-entry (:module import)
                                                                 (:field import)
                                                                 (get type-index-by-signature
                                                                      {:params (:params import)
                                                                       :result (:result import)})))
                                                 import-metadata))))
                function-section (section 3 (vec-bytes (mapv (comp uleb :type-index) compiled-fns)))
                indirect-target-indexes (when indirect?
                                          (mapv #(get fn-indexes (first %))
                                                (remove #(= 'main (first %)) defs)))
                table-section (when indirect?
                                (section 4 (vec-bytes [(table-entry (count indirect-target-indexes))])))
                memory-section (section 5 (vec-bytes [[0x00 0x01]]))
                global-section (section 6 (vec-bytes [(global-entry heap-start)]))
                export-names (mapv (comp str first) defs)
                export-section (section 7 (vec-bytes (conj (mapv (fn [[name _]]
                                                                    (export-entry (str name) 0x00
                                                                                  (get fn-indexes name)))
                                                                  defs)
                                                             (export-entry "memory" 0x02 0))))
                bodies (mapv (fn [compiled]
                               (let [decls (local-decls (merge-local-types compiled))
                                     body (bcat decls (:bytes compiled) [0x0b])]
                                 (bcat (uleb (count body)) body)))
                             compiled-fns)
                code-section (section 10 (vec-bytes bodies))
                element-section (when indirect?
                                  (section 9 (vec-bytes [(element-segment indirect-target-indexes)])))
                data-section (when (seq layout)
                               (section 11
                                        (vec-bytes
                                         (mapv (fn [[_ entry]]
                                                 (data-segment (:offset entry) (:bytes entry)))
                                               layout))))
                module-bytes (bcat wasm-magic-version
                                   type-section
                                   import-section
                                   function-section
                                   table-section
                                   memory-section
                                   global-section
                                   export-section
                                   element-section
                                   code-section
                                   data-section)]
            {:kotoba.wasm/ok? true
             :kotoba.wasm/binary (byte-array (map unchecked-byte module-bytes))
             :kotoba.wasm/byte-count (count module-bytes)
             :kotoba.wasm/export (when main "main")
             :kotoba.wasm/exports export-names
             :kotoba.wasm/result-type (when main (compiled-result-type (some #(when (= 'main (:name %)) %) compiled-fns)))
             :kotoba.wasm/function-count (count compiled-fns)
             :kotoba.wasm/import-count import-count
             :kotoba.wasm/imports import-metadata
             :kotoba.wasm/memory? true
             :kotoba.wasm/memory-min-pages 1
             :kotoba.wasm/heap-base heap-start
             :kotoba.wasm/data-segment-count (count layout)
             :kotoba.wasm/local-count (reduce max 0 (map #(:local-count % 0) compiled-fns))})))))))

;; ---------------------------------------------------------------------------
;; ADR-2607151500: ClojureScript backend -- a SECOND, genuinely separate
;; execution target for `.kotoba`, alongside compile-wasm-expr/wasm-binary
;; above. Covers ONLY the narrow-slice governor-style op set (int/keyword/
;; map literals, let/if/do/and/or/when, pair/pair-first/pair-second, get/
;; assoc, +/-/*/quot//rem/mod/comparisons, not/zero?/pos?/neg?/inc/dec,
;; named function calls) -- explicitly NOT i64/f32 typed ops, bitwise ops,
;; string ops, memory ops (alloc/i32-store!/mem-i32-at/byte-at/etc as raw
;; user-facing ops), or capability ops (cap-acquire/has-capability?/
;; call-indirect). kotoba-lang/compiler's own cljs backend (ADR-2607151500,
;; src/kotoba/compiler/backend/cljs.clj) landed its map/keyword/get/assoc/
;; loop-recur subset first and added cap-call support as a later, separate
;; addendum; the same incremental, honestly-scoped discipline applies here.
;;
;; Unlike compile-wasm-expr, this needs NO `locals`/`fns` context threaded
;; through recursive calls -- WASM locals need numeric INDICES (hence
;; compile-wasm-expr's local-index bookkeeping), but cljs's own `let`/`defn`
;; bind SYMBOLIC names directly, so lowering is a simple form -> form
;; rewrite (mirroring kotoba-lang/compiler's backend/cljs.clj `lower-expr`,
;; which has the same shape for the same reason).
;;
;; Two semantics kotoba's WASM output gets for free from the `if`/
;; comparison WASM OPCODES THEMSELVES (0-is-false via the `if` instruction;
;; i32.eq/i32.lt_s/etc. natively push 0 or 1) that plain cljs forms do NOT
;; reproduce automatically -- both handled explicitly here, same as
;; compiler/'s cljs backend:
;;   - every `if` wraps its test: `(if (zero? test') else' then')`
;;   - every comparison wraps its result: `(if (op ...) 1 0)`
;;
;; `pair`/`pair-first`/`pair-second`: this repo's WASM path encodes a pair
;; as 8 raw bytes in linear memory (alloc + i32-store!/mem-i32-at) because
;; that's the only heap kotoba/'s WASM target has. A cljs runtime already
;; has real persistent data structures, so here a pair is just a plain
;; 2-element vector + `nth` -- simpler, and `get`'s bounded unroll (reused
;; verbatim below, since it's pure form construction that recurses through
;; whichever `compile-*-expr` processes its result) inherits this for free.
;;
;; No memory-based host ABI: `kotoba wasm emit` requires a 0-arity `main`
;; because a WASM module can only export a fixed-arity function and real
;; inputs must be marshaled through linear memory -- neither restriction
;; applies to a cljs target, so `defn`s here keep their own declared
;; params and callers pass real arguments directly. This means EXISTING
;; `.kotoba` sources written for the memory-ABI convention (e.g. the
;; `mem-i32-at`-reading cloud-itonami governor ports) do NOT compile
;; unchanged to this target -- a real, documented v1 scope limit, not
;; silently patched over.

(declare compile-cljs-expr)

(def ^:private cljs-comparison-ops '#{= < > <= >=})
(def ^:private cljs-dividing-ops '#{quot / rem mod})
(def ^:private cljs-arith-ops (into cljs-dividing-ops '#{+ - *}))

(defn- cljs-fold-binary
  "Left-folds MAKE-BINARY (a fn [compiled-acc compiled-arg] -> compiled-form)
  over ARGS' compiled forms -- mirrors `compile-wasm-fold`'s own algorithm
  exactly: `(op a b c)` -> `((a op b) op c)`, a single arg passes through
  unchanged, at least one arg is required. This matters beyond `+`/`*`
  (associative, fold order is cosmetic): WASM's comparison opcodes ALSO go
  through this SAME fold, so `(< 3 1 2)` compiles to `((3<1) < 2)` =
  `(0 < 2)` = true -- NOT Clojure's native `<`'s true n-ary \"monotonic\"
  chained-comparison semantics (which would say false, 3 is not < 1).
  Replicating this exact fold, quirky as it is for 3+-arg comparisons, is
  what makes this backend agree with kotoba/'s own WASM target rather than
  silently adopting cljs's more intuitive but DIFFERENT native semantics."
  [make-binary args]
  (when (empty? args)
    (throw (ex-info "numeric op requires at least one argument" {:kotoba.cljs/problem :arity})))
  (if (= 1 (count args))
    (compile-cljs-expr (first args))
    (reduce (fn [acc-form arg] (make-binary acc-form (compile-cljs-expr arg)))
            (compile-cljs-expr (first args))
            (rest args))))

(defn- cljs-checked-divide
  "One fold step for quot//rem/mod: divisor is let-bound once (evaluated
  exactly once, matching a WASM stack machine's own single-evaluation
  property) and checked for zero before dividing -- WASM's i32.div_s/
  i32.rem_s instructions themselves TRAP on a zero divisor; plain cljs
  `quot`/`rem`/`mod`/`/` on JS numbers do NOT (silently produce Infinity/
  NaN/an exception only on some paths), so this guard is what makes this
  backend agree with kotoba/'s WASM target's trapping behavior instead of
  silently diverging into IEEE-754 semantics -- the same fail-closed
  reasoning kotoba-lang/compiler's cljs backend's own `kotoba$quot` guard
  documents."
  [op-name acc-form divisor-form]
  (let [d-sym (gensym "div__")]
    (list 'let [d-sym divisor-form]
          (list 'if (list 'zero? d-sym)
                (list 'throw (list 'ex-info "division-by-zero"
                                   {:kotoba.cljs/trap :division-by-zero}))
                (list op-name acc-form d-sym)))))
(def ^:private cljs-unsupported-ops
  "Explicitly rejected in this v1 scope -- i64/f32 typed ops, bitwise ops,
  string ops, raw memory ops, and capability ops all need a cljs-side
  design this pass does not attempt (see this section's own preface
  comment). Failing loud and clear here beats silently falling through to
  a named-function-call emission for an op that was never a user-defined
  function."
  '#{i64 i64+ i64- i64* i64and i64or i64xor i64shl i64shr i64ushr
     f32 f32+ f32- f32* f32div f32sqrt
     bit-and bit-or bit-xor bit-shift-left bit-shift-right unsigned-bit-shift-right
     alloc i32-store! mem-i32-at mem-byte-at byte-store! byte-at
     str-len str-ptr bytes-ptr bytes-len memory-pages memory-grow
     cap-acquire has-capability? call-indirect
     result-ok? result-err? result-write! result-status result-value})

(defn- cljs-reject! [op form]
  (throw (ex-info (str "op not supported by the cljs backend (ADR-2607151500 v1 scope): " op)
                  {:kotoba.cljs/problem :unsupported-op :kotoba.cljs/op op :kotoba.cljs/form (pr-str form)})))

(defn compile-cljs-expr
  "Lowers one `.kotoba` expression to a plain ClojureScript s-expression --
  the cljs-target sibling of `compile-wasm-expr` above, covering only the
  narrow-slice governor-style subset (see this section's own preface
  comment for the exact scope and the semantics it must bridge)."
  [form]
  (cond
    (integer? form) form
    (keyword? form) (keyword->i32 form)
    (map? form) (compile-cljs-expr (desugar-map form))
    (symbol? form) form
    (seq? form)
    (let [[op & args] form]
      (case op
        do (cons 'do (map compile-cljs-expr args))

        let (let [[bindings & body] args]
              (list* 'let (vec (mapcat (fn [[name value]] [name (compile-cljs-expr value)])
                                        (partition 2 bindings)))
                     (map compile-cljs-expr body)))

        if (let [[test then else] args]
             (list 'if (list 'zero? (compile-cljs-expr test))
                   (compile-cljs-expr else) (compile-cljs-expr then)))

        and (compile-cljs-expr (desugar-and args))
        or (compile-cljs-expr (desugar-or args))
        when (let [[test & body] args]
               (compile-cljs-expr (list 'if test (cons 'do body) 0)))

        pair (let [[l r] args] (list 'vector (compile-cljs-expr l) (compile-cljs-expr r)))
        pair-first (list 'nth (compile-cljs-expr (first args)) 0)
        pair-second (list 'nth (compile-cljs-expr (first args)) 1)

        get
        (if (not (<= 2 (count args) 3))
          (cljs-reject! 'get form)
          (let [[m k default-form] args
                default (if (some? default-form) default-form 0)
                m-sym (gensym "get-m__") k-sym (gensym "get-k__") d-sym (gensym "get-d__")
                unroll (fn unroll [cur depth]
                         (if (zero? depth)
                           d-sym
                           (list 'if (list '= cur 0)
                                 d-sym
                                 (list 'if (list '= (list 'pair-first (list 'pair-first cur)) k-sym)
                                       (list 'pair-second (list 'pair-first cur))
                                       (unroll (list 'pair-second cur) (dec depth))))))]
            (compile-cljs-expr
             (list 'let [m-sym m k-sym k d-sym default]
                   (unroll m-sym max-get-unroll-depth)))))

        assoc
        (if (not (and (>= (count args) 3) (odd? (count args))))
          (cljs-reject! 'assoc form)
          (let [[m & kvs] args]
            (compile-cljs-expr
             (reduce (fn [acc-map [k v]] (list 'pair (list 'pair k v) acc-map))
                     m (partition 2 kvs)))))

        not (list 'if (list '= (compile-cljs-expr (first args)) 0) 1 0)
        zero? (list 'if (list '= (compile-cljs-expr (first args)) 0) 1 0)
        pos? (compile-cljs-expr (list '> (first args) 0))
        neg? (compile-cljs-expr (list '< (first args) 0))
        inc (compile-cljs-expr (list '+ (first args) 1))
        dec (compile-cljs-expr (list '- (first args) 1))

        (cond
          (contains? cljs-unsupported-ops op) (cljs-reject! op form)

          (contains? cljs-comparison-ops op)
          (cljs-fold-binary (fn [acc-form arg-form] (list 'if (list op acc-form arg-form) 1 0))
                            args)

          (contains? cljs-dividing-ops op)
          ;; `/` and `quot` are the SAME op here (integer division, matching
          ;; compile-wasm-fold's own opcode aliasing); `rem`/`mod` likewise.
          (let [op-name (case op (/ quot) 'quot (mod rem) 'rem)]
            (cljs-fold-binary (fn [acc-form arg-form] (cljs-checked-divide op-name acc-form arg-form))
                              args))

          (contains? cljs-arith-ops op) ; the remaining ops: + - *
          (cljs-fold-binary (fn [acc-form arg-form] (list op acc-form arg-form)) args)

          :else (apply list op (map compile-cljs-expr args)))))
    :else (cljs-reject! :literal form)))

(defn cljs-source
  "Compiles top-level `(defn name [params] body...)` forms to a plain
  ClojureScript source string -- one `(ns ...)` form, a `(declare ...)` of
  every function name (forward references between user-defined functions
  are ordinary and expected here; unlike compile-wasm-expr's function-index
  table this needs no bytes-level equivalent, just textual ordering safety
  -- see kotoba-lang/compiler's own cljs backend, which found this exact
  gap live via `nbb` for its `loop`-desugared helpers), then one `defn` per
  function, callable directly by any cljs host that requires the emitted
  namespace -- no memory-based ABI, callers pass real arguments (see this
  section's own preface comment for why that's fine here, unlike the WASM
  target)."
  ([forms] (cljs-source forms 'kotoba.compiled.generated))
  ([forms ns-name]
   (let [defs (function-defs forms)]
     (when (empty? defs)
       (throw (ex-info "at least one defn is required" {:kotoba.cljs/problem :no-functions})))
     (let [fn-forms (mapv (fn [[name {:keys [params body]}]]
                            (list 'defn name (vec params)
                                  (list* 'do (map compile-cljs-expr body))))
                          defs)
           fn-names (mapv first defs)
           forms* (concat [(list 'ns ns-name)]
                          [(list* 'declare fn-names)]
                          fn-forms)]
       (cstr/join "\n\n" (map pr-str forms*))))))
