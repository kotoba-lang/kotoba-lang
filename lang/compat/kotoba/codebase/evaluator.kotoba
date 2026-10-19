(ns kotoba.codebase.evaluator
  "Hash-native evaluation of stored semantic definitions.

  A definition is evaluated from its canonical block, and every dependency is
  hydrated from the store BY CID. No source file, no namespace, and no name is
  consulted: a definition that is reachable by hash is runnable, and renaming
  or deleting every binding that points at it changes nothing about how it
  runs.

  This is the difference between content-addressed identity and a
  content-addressed CODEBASE. The earlier local runner kept an `executable
  source witness` per definition CID and re-compiled that text before running
  it, which made the source -- not the definition graph -- the thing that had
  to be transferred, stored, and trusted, and meant a definition whose
  dependencies were all present still could not run without its file.

  Evaluation here is pure by construction: the capability intrinsics are
  rejected rather than dispatched, so a stored definition cannot acquire
  authority merely by being reachable."
  (:require [clojure.string :as str]
            [ipld.value :as value]
            [kotoba.codebase.ir :as ir]
            [kotoba.codebase.semantic-code :as semantic]
            [kotoba.codebase.store :as store]))

(def default-fuel
  "Evaluation steps admitted for one `evaluate` call. Bounded because a stored
  definition graph is untrusted input: it may be recursive, and hydration is
  driven by data in the blocks themselves."
  1000000)

(def max-hydration-depth 64)

(def default-max-call-depth
  "Nested applications admitted for one `evaluate` call.

  Fuel alone does not make evaluation safe: a runaway recursion exhausts the
  HOST stack long before a generous fuel budget runs out, and a
  `StackOverflowError` is an Error, not a fail-closed result -- it can leave
  the caller's own machinery half-unwound. Depth is therefore bounded
  independently, well below any reachable JVM stack limit."
  256)

(defn- fail! [problem data]
  (throw (ex-info (name problem) (assoc data :problem problem))))

(defn- burn! [fuel]
  (when (neg? (vswap! fuel dec))
    (fail! :codebase/fuel-exhausted {:limit default-fuel}))
  nil)

;; ---------------------------------------------------------------------------
;; Intrinsics
;;
;; The intrinsic vocabulary is the language profile's, not a namespace's, so it
;; is resolved by stable id rather than by looking a symbol up anywhere.

(defn- effect-intrinsic [id]
  (fn [& _]
    (fail! :codebase/effect-not-permitted
           {:intrinsic id
            :hint "pure evaluation cannot grant capabilities; run through a host that admits effects"})))

(def ^:private intrinsic-fns
  (let [pure {"+" + "-" - "*" * "/" / "=" = "not=" not=
              "<" < "<=" <= ">" > ">=" >=
              "inc" inc "dec" dec "zero?" zero? "pos?" pos? "neg?" neg?
              "str" str "keyword" keyword "name" name "namespace" namespace
              "count" count "empty?" empty? "first" first "rest" rest "next" next
              "nth" nth "get" get "assoc" assoc "dissoc" dissoc "conj" conj
              "cons" cons "into" into "vector" vector "hash-map" hash-map
              "set" set "contains?" contains? "keys" keys "vals" vals
              "true?" true? "false?" false? "nil?" nil? "some?" some?
              "identity" identity "constantly" constantly
              "apply" apply "map" (comp doall map) "reduce" reduce
              "filter" (comp doall filter)}]
    (merge pure
           {"has-capability?" (effect-intrinsic "has-capability?")
            "cap-acquire" (effect-intrinsic "cap-acquire")})))

(def ^:private intrinsic-prefix "kotoba.intrinsic/v1/")

(defn- intrinsic [id]
  (when-not (and (string? id) (str/starts-with? id intrinsic-prefix))
    (fail! :codebase/unknown-intrinsic {:id id}))
  (let [sym (subs id (count intrinsic-prefix))]
    (or (get intrinsic-fns sym)
        (fail! :codebase/unknown-intrinsic {:id id}))))

;; ---------------------------------------------------------------------------
;; Expression evaluation

(defn- literal [form]
  (let [decoded (value/form->value form)]
    (if (value/float64? decoded) (value/float64-value decoded) decoded)))

(declare eval-node)

(defn- eval-body [body context]
  (last (mapv #(eval-node % context) body)))

(defn- local-value
  "De Bruijn lookup. Index 0 is the innermost binder, so the stack is read from
  its end -- the same orientation `semantic-code` assigns them."
  [locals index]
  (let [position (- (count locals) 1 index)]
    (when (or (neg? position) (>= position (count locals)))
      (fail! :codebase/unbound-local {:index index :depth (count locals)}))
    (nth locals position)))

(defn- eval-node [node {:keys [locals fuel] :as context}]
  (burn! fuel)
  (when-not (map? node)
    (fail! :codebase/malformed-ir {:node node}))
  (case (get node "op")
    "literal" (literal (get node "value"))
    "local" (local-value locals (get node "index"))
    "intrinsic" (intrinsic (get node "id"))

    "reference"
    ((:resolve-reference context) (ir/link->cid (get node "cid")))

    "recursive-reference"
    (let [members (some-> (:group context) deref)]
      (when-not members
        (fail! :codebase/recursive-reference-outside-group {}))
      (let [index (get node "index")]
        (when-not (and (integer? index) (< -1 index (count members)))
          (fail! :codebase/recursive-reference-out-of-range {:index index}))
        @(nth members index)))

    "vector" (mapv #(eval-node % context) (get node "items"))
    "set" (into #{} (map #(eval-node % context)) (get node "items"))
    "map" (into {} (map (fn [[k v]] [(eval-node k context) (eval-node v context)]))
                (get node "entries"))

    "if" (let [[test then else] (get node "args")]
           (if (eval-node test context)
             (eval-node then context)
             (when else (eval-node else context))))

    "do" (eval-body (get node "body") context)

    "and" (reduce (fn [_ arg] (let [v (eval-node arg context)] (if v v (reduced v))))
                  true (get node "args"))
    "or" (reduce (fn [_ arg] (let [v (eval-node arg context)] (if v (reduced v) v)))
                 nil (get node "args"))

    "when" (when (eval-node (get node "test") context)
             (eval-body (get node "body") context))

    "let"
    (let [context (reduce (fn [ctx binding]
                            (update ctx :locals conj
                                    (eval-node (get binding "value") ctx)))
                          context
                          (get node "bindings"))]
      (eval-body (get node "body") context))

    "fn"
    (let [params (get node "params")
          arity (count params)
          body (get node "body")
          captured (:locals context)
          counter (:call-depth context)
          limit (:max-call-depth context default-max-call-depth)]
      (fn [& args]
        (when-not (= arity (count args))
          (fail! :codebase/arity-mismatch {:expected arity :actual (count args)}))
        ;; Counted at CALL time, not at closure-creation time: a recursive
        ;; group evaluates its `fn` node once and then re-enters that single
        ;; closure, so a depth captured when it was built never grows.
        (let [depth (vswap! counter inc)]
          (try
            (when (> depth limit)
              (fail! :codebase/call-depth-exceeded {:limit limit}))
            (eval-body body (assoc context :locals (into captured args)))
            (finally (vswap! counter dec))))))

    "call"
    (let [callee (eval-node (get node "callee") context)
          args (mapv #(eval-node % context) (get node "args"))]
      (when-not (ifn? callee)
        (fail! :codebase/not-callable {:value callee}))
      (apply callee args))

    (fail! :codebase/unsupported-ir-op {:op (get node "op")})))

;; ---------------------------------------------------------------------------
;; Definition hydration

(defn- resolver
  "Resolve a definition CID to its value, hydrating from the store on demand.

  Results are memoized per evaluation so a diamond in the dependency graph is
  evaluated once, and depth is bounded so a deep chain fails closed instead of
  overflowing the host stack."
  [root fuel max-call-depth]
  (let [cache (volatile! {})
        call-depth (volatile! 0)]
    (letfn [(resolve-cid [cid depth]
              (when (> depth max-hydration-depth)
                (fail! :codebase/hydration-too-deep {:limit max-hydration-depth :cid cid}))
              (if-let [entry (find @cache cid)]
                (val entry)
                (let [value (evaluate-block cid (store/get-block root cid) depth)]
                  (vswap! cache assoc cid value)
                  value)))

            (context-for [depth]
              {:locals []
               :fuel fuel
               :call-depth call-depth
               :max-call-depth max-call-depth
               :resolve-reference #(resolve-cid % (inc depth))})

            (evaluate-block [cid block depth]
              (condp = (ir/block-kind block)
                semantic/schema
                (eval-node (get block "ir") (context-for depth))

                "kotoba.recursive-member.v1"
                (let [group-cid (ir/link->cid (get block "group"))
                      group (store/get-block root group-cid)
                      index (get block "index")
                      members (get group "members")]
                  (when-not (= "kotoba.recursive-group.v1" (ir/block-kind group))
                    (fail! :codebase/not-recursive-group {:cid group-cid}))
                  (when-not (and (integer? index) (< -1 index (count members)))
                    (fail! :codebase/recursive-member-out-of-range
                           {:cid cid :index index}))
                  (let [frame (volatile! nil)
                        context (assoc (context-for depth) :group frame)]
                    ;; Members are delayed, not eager: a self- or mutually
                    ;; recursive group refers to its own members while it is
                    ;; still being built, and only a delay lets that reference
                    ;; exist before the value does.
                    (vreset! frame
                             (mapv (fn [member]
                                     (delay (eval-node (get member "ir") context)))
                                   members))
                    @(nth @frame index)))

                (fail! :codebase/not-evaluable-block
                       {:cid cid :schema (ir/block-kind block)})))]
      resolve-cid)))

(defn evaluate
  "Evaluate the definition stored at CID.

  Only the store is consulted: dependencies are hydrated by CID, and a missing
  dependency is a hard failure rather than a fallback to a name or a path."
  ([root cid] (evaluate root cid {}))
  ([root cid {:keys [fuel max-call-depth]
              :or {fuel default-fuel max-call-depth default-max-call-depth}}]
   (let [fuel (volatile! fuel)]
     {:cid cid
      :value ((resolver root fuel max-call-depth) cid 0)
      :fuel-remaining @fuel
      ;; The cell, not just a snapshot: evaluating a `defn` yields a CLOSURE,
      ;; and the work of actually calling it happens after this returns. A
      ;; caller that applies the closure and then reports the snapshot would
      ;; report the fuel left before any of that work was done.
      :fuel-cell fuel})))

(defn definition-type
  "The semantic type block a definition or recursive member commits to."
  [root cid]
  (let [block (store/get-block root cid)]
    (store/get-block root (ir/link->cid (get block "type")))))

(defn invoke
  "Evaluate CID and, when it denotes a function, apply it to ARGS.

  Whether to apply is read from the definition's TYPE block, not guessed from
  the argument count: a zero-arity function and a plain value are both reached
  with no arguments, and only the type distinguishes them."
  ([root cid args] (invoke root cid args {}))
  ([root cid args opts]
   (let [{:keys [value fuel-cell]} (evaluate root cid opts)
         type (definition-type root cid)
         function? (= "function" (get type "kind"))]
     (cond
       (not function?)
       (do (when (seq args)
             (fail! :codebase/not-callable {:cid cid :hint "definition is a value"}))
           {:cid cid :value value :fuel-remaining @fuel-cell})

       :else
       (do (when-not (ifn? value)
             (fail! :codebase/not-callable {:cid cid}))
           (let [result (apply value args)]
             {:cid cid :value result :fuel-remaining @fuel-cell}))))))

(defn count-of-references
  "Definition CIDs BLOCK depends on, from its IR rather than its declared list.

  Used by callers that need to verify the declared `dependencies` field agrees
  with what the expression actually references."
  [block]
  (ir/expression-references (or (get block "ir") (get block "members"))))
