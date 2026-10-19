(ns datom.source.plane
  "Query-class → serving plane.

  Mechanical form of 'do not put the world in one ref and Datalog it'
  (ADR-2608170200). Each class names the contract that may answer it and
  the strategies that are forbidden. Admission is data: a request is a
  map, a refusal is a map. Silence is not a pass.

  Temporary: the catalog is a Clojure map. `admit` is a total function
  over that table and is a candidate for `kotoba/pure`; collections in
  the catalog stay here until native maps qualify.")

(def catalog
  "Closed table. Adding a class is an ADR, not a silent key."
  {:equality {:id :equality
              :contract :IPatternSource
              :cost :log-plus-result
              :allows #{:pattern-scan :cursor-prefix}
              :forbids #{:hydrate-then-scan :graphsync-selector :ipld-schema-walk}}
   :range {:id :range
           :contract :IRangeSource
           :cost :log-plus-result
           :allows #{:ordered-range :columnar-minmax}
           :forbids #{:hmac-blind-range :hydrate-then-scan :explore-range-on-map}}
   :search {:id :search
            :contract :IPostings
            :cost :df-plus-result
            :allows #{:postings-intersect}
            :forbids #{:full-doc-scan :hydrate-then-scan :datalog-over-tokens
                       :graphsync-selector}}
   :olap {:id :olap
          :contract :columnar-projection
          :cost :granules-touched
          :allows #{:columnar-scan}
          :forbids #{:aggregate-on-avet :hydrate-then-scan}
          :rebuildable? true}
   :graph {:id :graph
           :contract :IAdjacency
           :cost :sum-of-degrees
           :allows #{:adjacency-hop}
           :forbids #{:multi-hop-via-full-scan :hydrate-then-scan}}
   :feed {:id :feed
          :contract :materialized-view
          :cost :lookup
          :allows #{:view-lookup :incremental-view}
          :forbids #{:hydrate-then-scan :per-request-attr-scan}
          :rebuildable? true}})

(defn plane
  "The catalog entry for `class`, or nil if unknown. Nil is not admission."
  [class]
  (get catalog class))

(defn admit
  "Classify a request against the catalog.

  Returns `{:ok true :plane ... :strategy ...}` or
  `{:ok false :reason ...}` plus the facts that made the decision.
  Unknown class, missing strategy, empty catalog — all are refusals.
  None of them return `{:ok true}`."
  [class {:keys [strategy] :as _request}]
  (let [entry (plane class)]
    (cond
      (empty? catalog)
      {:ok false :reason :empty-catalog :class class}

      (nil? class)
      {:ok false :reason :unknown-class :class class}

      (nil? entry)
      {:ok false :reason :unknown-class :class class
       :known (vec (sort (keys catalog)))}

      (nil? strategy)
      {:ok false :reason :missing-strategy :class class
       :allows (:allows entry) :forbids (:forbids entry)}

      (contains? (:forbids entry) strategy)
      {:ok false :reason :forbidden-strategy :class class
       :strategy strategy :forbids (:forbids entry)}

      (not (contains? (:allows entry) strategy))
      {:ok false :reason :not-admitted :class class
       :strategy strategy :allows (:allows entry)}

      :else
      {:ok true :plane entry :strategy strategy})))
