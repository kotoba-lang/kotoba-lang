(ns kotobase.guarded
  "The read path that cannot be taken without authority.

  `kotobase.authorized-query` was the right gate and had exactly one caller:
  its own test. A gate nothing goes through is a gate, an unguarded read path,
  and a document saying reads are authorised — and the document is the part
  people believe.

  This namespace is the composition that was missing. It is deliberately *not*
  a change to `kotobase.core`: breaking `q`/`query`/`pull` would break every
  consumer in the workspace at once, and a migration nobody can complete is
  how a safe path ends up bypassed for real work. So the guarded path is added,
  the unguarded one keeps working, and which is which is a fact a test can
  check rather than a claim in prose (see `kotobase.guarded-surface-test`).

  Four things are required together, because each alone is defeated:

  - a **policy** that admits the query at one immutable basis. Admission, not
    redaction: a Datalog query with an unconstrained join leaks through
    existence and aggregation whatever you do to the rows afterwards.
  - a **classified schema**, so the policy's projection can be reasoned about
    at all. Without it `:mail/body` and `:mail/thread` are the same shape.
  - the **grant** the caller holds, as classes and erasure scopes.
  - a **receipt sink** that says it persisted. `kotobase.admission` refuses to
    run an effect until its audit is durable; a read that leaves nothing
    behind is the same hole on the other side."
  (:require [kotobase.authorized-query :as gate]
            [kotobase.classification :as classification]))

(defn- reject! [reason data]
  (throw (ex-info "guarded read rejected" (assoc data :kotobase.guarded/reason reason))))

(defn admit
  "Admit a read, or throw. Returns the compiled query plus what it may reach.

  `authorize!` is the policy compiler `kotobase.authorized-query/compile!`
  expects. `schema` and `grant` decide whether the projection that policy
  allowed is one this caller may actually be served: a policy can allow an
  attribute the caller holds no class for, and the narrower of the two wins."
  [{:keys [authorize! schema grant query]}]
  (when-not (map? schema) (reject! :missing-schema {}))
  (when-not (classification/classified? schema)
    ;; an attribute nobody classified is one nobody authorised, and a schema
    ;; that is partly classified is the dangerous kind: the unclassified half
    ;; reads as ordinary
    (reject! :schema-not-fully-classified
             {:errors (filterv #(= :unclassified-attribute (:error %))
                               (classification/schema-errors schema))}))
  (when-not (map? grant) (reject! :missing-grant {}))
  (let [compiled (gate/compile! authorize! query)
        projection (get-in compiled [:decision :projection])
        errors (classification/projection-errors schema projection grant)]
    (when (seq errors)
      ;; the policy said yes and the grant does not carry it. Refused rather
      ;; than served narrowed: a caller that asked for something it may not
      ;; have should learn that, not receive a quietly smaller answer
      (reject! :projection-outside-grant {:errors errors}))
    {:compiled compiled
     :projection projection
     :inline (classification/inline-attributes schema)}))

(defn read!
  "Admit, evaluate, and record. There is no arity that skips any of the three.

  `evaluate!` is handed the compiled query and the policy decision; it never
  sees a connection. `receipt!` must return `{:receipt/durable? true
  :receipt/cid …}` or the rows are withheld."
  [{:keys [evaluate! receipt!] :as request}]
  (let [{:keys [compiled inline projection]} (admit request)
        result (gate/execute! evaluate! receipt! compiled)]
    (assoc result
           :projection projection
           ;; which of the served attributes were values and which were
           ;; addresses. A caller that treats an address as a value has read a
           ;; hash and reported it as content
           :inline (into (sorted-set) (filter inline projection))
           :by-reference (into (sorted-set) (remove inline projection)))))

(def unguarded-read-fns
  "The read surface of `kotobase.core`, which is unguarded by design.

  Named here so that `it is unguarded` is a fact with a location rather than
  something a reader has to infer. A caller reaching for one of these is
  choosing to read without a policy, a grant, or a receipt, and that choice
  should be visible in review."
  '#{datoms q query pull head})
