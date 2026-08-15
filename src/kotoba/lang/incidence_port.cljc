(ns kotoba.lang.incidence-port
  "Capability-guarded publication boundary for incidence emissions.

  Facets produce inert addressed incidences. This namespace is the narrow
  effect seam that may publish them: an injected append function is invoked
  only after the requested ledger scope, verified grants, local policy, and
  declared effect row agree. No transport is selected here."
  (:require [kotoba.lang.capability-host :as host]
            [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.incidence :as incidence]))

(def append-kind :host/ledger-append)
(def append-effect :host/ledger-append)
(def append-call :kotoba.dataspace/append)

(defn- emissions-error
  [emissions]
  (cond
    (not (vector? emissions))
    {:problem :dataspace/emissions-not-a-vector}

    :else
    (let [verified (mapv incidence/verify-addressed emissions)
          invalid-index (first (keep-indexed (fn [i result]
                                               (when-not (:ok? result) i))
                                             verified))
          cids (mapv :cid verified)]
      (cond
        (some? invalid-index)
        {:problem :dataspace/emission-invalid
         :index invalid-index
         :verification (nth verified invalid-index)}

        (not= (count cids) (count (set cids)))
        {:problem :dataspace/emission-duplicate}

        :else nil))))

(defn- request-error
  [dataspace requested now record! append!]
  (cond
    (not (capabilities/non-empty-string? dataspace))
    {:problem :dataspace/resource-invalid}

    (not (fn? append!))
    {:problem :dataspace/provider-invalid}

    (not (capabilities/date-string? now))
    {:problem :dataspace/receipt-date-invalid}

    (and (some? record!) (not (fn? record!)))
    {:problem :dataspace/recorder-invalid}

    (not (capabilities/capability? requested))
    {:problem :dataspace/capability-invalid}

    (not= append-kind (:cap/kind requested))
    {:problem :dataspace/capability-kind
     :expected append-kind
     :actual (:cap/kind requested)}

    (not= dataspace (:cap/resource requested))
    {:problem :dataspace/capability-resource
     :expected dataspace
     :actual (:cap/resource requested)}))

(defn publish-emissions!
  "Publish verified facet EMISSIONS through an injected append provider.

  OPTS contains exact dataspace and capability inputs plus an injected
  append function accepting a map with :dataspace, :entry, and :capability.

  Every entry is hash-verified before the first effect. Each append then passes
  through guard-ability-call, so the provider sees only the concrete
  post-intersection capability. Processing stops on the first denial. Provider
  exceptions are receipted by the host guard and rethrown; already appended
  content remains append-only."
  [{:keys [dataspace emissions requested effect-row cacao-grants local-policy
           now record! append!]}]
  (if-let [error (or (request-error dataspace requested now record! append!)
                     (emissions-error emissions))]
    {:ok? false :reason (:problem error) :error error}
    (loop [remaining emissions
           results []
           receipts []]
      (if-let [entry (first remaining)]
        (let [outcome
              (host/guard-ability-call
               {:call append-call
                :requested requested
                :effect-row effect-row
                :cacao-grants cacao-grants
                :local-policy local-policy
                :now now
                :record! record!
                :handler
                (fn [concrete]
                  (when-not (= dataspace (:cap/resource concrete))
                    (throw (ex-info "dataspace capability scope changed"
                                    {:problem :dataspace/scope-mismatch
                                     :expected dataspace
                                     :actual (:cap/resource concrete)})))
                  (append! {:dataspace dataspace
                            :entry entry
                            :capability concrete}))})]
          (if (:kotoba.host/ok? outcome)
            (recur (next remaining)
                   (conj results (:kotoba.host/result outcome))
                   (conj receipts (:kotoba.host/receipt outcome)))
            {:ok? false
             :reason (:kotoba.host/denied outcome)
             :failed-cid (:incidence/cid entry)
             :results results
             :receipts (conj receipts (:kotoba.host/receipt outcome))}))
        {:ok? true
         :results results
         :receipts receipts}))))
