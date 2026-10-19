(ns kotobase.code-graph
  "C2/C3 storage projection for content-addressed Kotoba code.

  This namespace intentionally depends only on IStore. Cryptographic block
  verification is host-injected as `(verify cid block)`, keeping kotobase-clj
  portable while making verification mandatory on admission."
  (:require [clojure.string :as str]
            [kotoba.abi.contract :as abi]
            [kotoba.security.effect :as effect]
            [kotobase.store :as store]))

(def definitions "code.definitions")
(def types "code.types")
(def artifacts "code.artifacts")
(def analysis-cache "code.analysis-cache")
(def namespace-commits "code.namespace-commits")
(def execution-receipts "code.execution-receipts")
(def execution-identities "code.execution-identities")
(def query-receipts "code.query-receipts")
(def identity-migrations "code.identity-migrations")
(def retention-pins "code.retention-pins")
(def datom-stream "code.datoms")
(def retention-stream "code.retention-events")

(defn- require-value [pred value problem data]
  (when-not (pred value)
    (throw (ex-info (name problem) (assoc data :problem problem))))
  value)

(defn definition-record
  "Portable storage record from a semantic compiler result. BLOCK remains the
  authoritative hashed value; the other fields are its verified query
  projection and must be regenerated/rechecked by the admitting host."
  [{:keys [cid block dependency-cids effects kind type-cid source-cid visibility
           sealed-block-cid] :as input}]
  {:code.definition/cid (or cid (:code.definition/cid input))
   :code.definition/block (or block (:code.definition/block input))
   :code.definition/kind (or kind (:code.definition/kind input) :term)
   :code.definition/dependencies
   (vec (sort (or dependency-cids (:code.definition/dependencies input) [])))
   :code.definition/effects
   (vec (sort (or effects (:code.definition/effects input) [])))
   :code.definition/type-cid (or type-cid (:code.definition/type-cid input))
   :code.definition/source-cid (or source-cid (:code.definition/source-cid input))
   :code.definition/visibility (or visibility (:code.definition/visibility input) :private)
   :code.definition/sealed-block-cid
   (or sealed-block-cid (:code.definition/sealed-block-cid input))})

(defn definition-datoms [record]
  (let [cid (:code.definition/cid record)]
    (vec
     (concat
      [[:db/add cid :code.definition/kind (:code.definition/kind record)]
       [:db/add cid :code.definition/visibility (:code.definition/visibility record)]]
      (map (fn [dep] [:db/add cid :code.definition/depends-on dep])
           (:code.definition/dependencies record))
      (map (fn [effect] [:db/add cid :code.definition/effect effect])
           (:code.definition/effects record))
      (when-let [type-cid (:code.definition/type-cid record)]
        [[:db/add cid :code.definition/type-cid type-cid]])
      (when-let [source-cid (:code.definition/source-cid record)]
        [[:db/add cid :code.definition/source-cid source-cid]])))))

(defn put-type!
  "Verify and store a semantic type block before definitions may reference it."
  [s verify {:keys [cid block] :as record}]
  (require-value string? cid :code/type-cid-required {})
  (require-value some? block :code/type-block-required {:cid cid})
  (require-value true? (boolean (verify cid block)) :code/type-cid-mismatch {:cid cid})
  (if-let [existing (store/-get s types cid)]
    (do (require-value #(= existing %) record :code/type-cid-record-conflict {:cid cid})
        existing)
    (do (store/-put s types cid record)
        (store/-append s datom-stream {:datom [:db/add cid :code.type/kind
                                                (get block "kind" "unknown")]})
        record)))

(defn get-type [s cid]
  (store/-get s types cid))

(defn put-definition!
  "Verify and idempotently store one definition plus its Datom projection.
  VERIFY must return truthy only when CID recomputes from BLOCK. Every declared
  dependency must already exist, preventing dangling admitted closures."
  [s verify record]
  (let [record (definition-record record)
        cid (require-value string? (:code.definition/cid record)
                           :code/invalid-cid {})
        block (:code.definition/block record)]
    (require-value some? block :code/block-required {:cid cid})
    (require-value true? (boolean (verify cid block))
                   :code/cid-mismatch {:cid cid})
    (when-let [type-cid (:code.definition/type-cid record)]
      (require-value some? (get-type s type-cid)
                     :code/missing-type {:cid cid :type-cid type-cid}))
    (doseq [dep (:code.definition/dependencies record)]
      (require-value some? (store/-get s definitions dep)
                     :code/missing-dependency {:cid cid :dependency dep}))
    (if-let [existing (store/-get s definitions cid)]
      (do
        (require-value #(= existing %) record :code/cid-record-conflict {:cid cid})
        existing)
      (do
        (store/-put s definitions cid record)
        (doseq [datom (definition-datoms record)]
          (store/-append s datom-stream {:datom datom}))
        record))))

(defn get-definition [s cid]
  (store/-get s definitions cid))

(defn definition-view
  "Return a definition only when disclosure is public or AUTHORIZE approves.
  Unauthorized callers receive a non-executable sealed reference without the
  semantic block, source CID, dependencies, effects, or type projection."
  [s cid authorize]
  (let [record (get-definition s cid)]
    (require-value some? record :code/missing-dependency {:cid cid})
    (if (or (= :public (:code.definition/visibility record))
            (boolean (authorize record)))
      record
      (select-keys record [:code.definition/cid :code.definition/kind
                           :code.definition/visibility
                           :code.definition/sealed-block-cid]))))

(defn all-definitions [s]
  (mapv #(get-definition s %) (store/-list s definitions)))

(defn direct-dependents [s target-cid]
  (->> (all-definitions s)
       (filter #(some #{target-cid} (:code.definition/dependencies %)))
       (mapv :code.definition/cid)
       sort vec))

(defn dependency-closure
  "Verified reachable definition CIDs, including ROOT. Missing records fail
  closed rather than returning a partial closure."
  [s root]
  (loop [todo [root] seen #{}]
    (if-let [cid (peek todo)]
      (if (contains? seen cid)
        (recur (pop todo) seen)
        (let [record (get-definition s cid)]
          (require-value some? record :code/missing-dependency {:cid cid})
          (recur (into (pop todo) (:code.definition/dependencies record))
                 (conj seen cid))))
      seen)))

(defn transitive-effects [s root]
  (->> (dependency-closure s root)
       (mapcat #(:code.definition/effects (get-definition s %)))
       set))

(defn definitions-requiring-effect [s effect]
  (->> (store/-list s definitions)
       (filter #(contains? (transitive-effects s %) effect))
       sort vec))

(defn put-artifact!
  "Verify and store a derivation-addressed artifact. VERIFY receives the
  complete record and must recompute artifact-cid from its bytes/envelope."
  [s verify {:keys [artifact-cid code-root-cid compiler-contract-cid] :as record}]
  (doseq [[value problem] [[artifact-cid :code/artifact-cid-required]
                           [code-root-cid :code/code-root-required]
                           [compiler-contract-cid :code/compiler-contract-required]]]
    (require-value string? value problem {}))
  (require-value fn? verify :code/artifact-verifier-required {})
  (require-value true? (boolean (verify record)) :code/artifact-cid-mismatch
                 {:artifact-cid artifact-cid})
  (dependency-closure s code-root-cid)
  (store/-put s artifacts artifact-cid record))

(defn find-artifact [s code-root-cid compiler-contract-cid]
  (some (fn [cid]
          (let [record (store/-get s artifacts cid)]
            (when (and (= code-root-cid (:code-root-cid record))
                       (= compiler-contract-cid (:compiler-contract-cid record)))
              record)))
        (store/-list s artifacts)))

(defn cache-put!
  "Admit a content-addressed analysis/test result. Cache identity is valid only
  with explicit code, analyzer, environment, and input identities; ambient
  results cannot be promoted into the shared cache."
  [s verify {:keys [cid block code-root-cid analyzer-contract-cid
                    environment-cid input-cids] :as record}]
  (doseq [[value problem]
          [[cid :cache/cid-required]
           [code-root-cid :cache/code-root-required]
           [analyzer-contract-cid :cache/analyzer-contract-required]
           [environment-cid :cache/environment-required]]]
    (require-value string? value problem {}))
  (require-value vector? input-cids :cache/inputs-required {:cid cid})
  (require-value #(every? string? %) input-cids :cache/inputs-invalid {:cid cid})
  (dependency-closure s code-root-cid)
  (require-value true? (boolean (verify cid block)) :cache/cid-mismatch {:cid cid})
  (store/-put s analysis-cache cid record))

(defn cache-get [s cache-cid]
  (store/-get s analysis-cache cache-cid))

(defn put-namespace-commit!
  "Verify and store an immutable name->definition-CID mapping. Parent commits
  and bound definitions must exist. VERIFY owns CID recomputation."
  [s verify {:keys [cid block parents bindings] :as commit}]
  (require-value string? cid :namespace/cid-required {})
  (require-value true? (boolean (verify cid block)) :namespace/cid-mismatch {:cid cid})
  (require-value map? bindings :namespace/bindings-required {:cid cid})
  (doseq [parent parents]
    (require-value some? (store/-get s namespace-commits parent)
                   :namespace/missing-parent {:cid cid :parent parent}))
  (doseq [[name definition-cid] bindings]
    (require-value string? name :namespace/name-invalid {:cid cid :name name})
    (require-value some? (get-definition s definition-cid)
                   :namespace/missing-definition
                   {:cid cid :name name :definition-cid definition-cid}))
  (if-let [existing (store/-get s namespace-commits cid)]
    (do (require-value #(= existing %) commit :namespace/cid-record-conflict {:cid cid})
        existing)
    (do
      (store/-put s namespace-commits cid commit)
      (doseq [[name definition-cid] bindings]
        (store/-append s datom-stream
                       {:datom [:db/add cid :code.namespace/binding
                                {:name name :definition-cid definition-cid}]}))
      commit)))

(defn namespace-commit [s cid]
  (store/-get s namespace-commits cid))

(defn resolve-name [s namespace-cid name]
  (get-in (namespace-commit s namespace-cid) [:bindings name]))

(defn resolve-qualified-name
  "Resolve `name` or `name#cid-prefix` against one immutable namespace. A hash
  qualifier never falls back to a same-named but different definition."
  [s namespace-cid qualified]
  (let [[name prefix] (str/split qualified #"#" 2)
        cid (resolve-name s namespace-cid name)]
    (require-value string? cid :namespace/name-not-found
                   {:namespace-cid namespace-cid :name name})
    (when prefix
      (require-value #(and (seq prefix) (str/starts-with? % prefix)) cid
                     :namespace/hash-qualifier-mismatch
                     {:namespace-cid namespace-cid :name name
                      :expected-prefix prefix :actual cid}))
    cid))

(defn merge-bindings
  "Pure three-way namespace merge. Independently changed names merge; divergent
  edits are returned as explicit conflicts and are never chosen implicitly."
  [base left right]
  (let [names (set (concat (keys base) (keys left) (keys right)))]
    (reduce
     (fn [{:keys [bindings conflicts] :as out} name]
       (let [b (get base name ::absent)
             l (get left name ::absent)
             r (get right name ::absent)
             chosen (cond (= l r) l (= l b) r (= r b) l :else ::conflict)]
         (cond
           (= chosen ::conflict)
           (assoc out :conflicts
                  (assoc conflicts name {:base b :left l :right r}))

           (= chosen ::absent) out
           :else (assoc out :bindings (assoc bindings name chosen)))))
     {:bindings {} :conflicts {}} names)))

(defn put-identity-migration!
  "Store an explicitly authorized attestation relating identities from two
  versioned semantic domains. It does not claim the CIDs are interchangeable;
  consumers decide whether to trust AUTHORITY-CID and the injected AUTHORIZE
  policy."
  [s verify authorize {:keys [cid block from-cid to-cid from-contract-cid
                              to-contract-cid authority-cid] :as migration}]
  (doseq [[value problem]
          [[cid :migration/cid-required]
           [from-cid :migration/from-required]
           [to-cid :migration/to-required]
           [from-contract-cid :migration/from-contract-required]
           [to-contract-cid :migration/to-contract-required]
           [authority-cid :migration/authority-required]]]
    (require-value string? value problem {}))
  (require-value some? (get-definition s from-cid) :migration/from-missing
                 {:cid from-cid})
  (require-value some? (get-definition s to-cid) :migration/to-missing
                 {:cid to-cid})
  (require-value true? (boolean (verify cid block)) :migration/cid-mismatch
                 {:cid cid})
  (require-value true? (boolean (authorize migration))
                 :migration/authority-denied {:authority-cid authority-cid})
  (if-let [existing (store/-get s identity-migrations cid)]
    (do (require-value #(= existing %) migration
                       :migration/cid-record-conflict {:cid cid})
        existing)
    (do (store/-put s identity-migrations cid migration)
        (store/-append s datom-stream
                       {:datom [:db/add from-cid :code.identity/migrates-to
                                {:to-cid to-cid :attestation-cid cid}]})
        migration)))

(defn migrations-from [s definition-cid]
  (->> (store/-list s identity-migrations)
       (map #(store/-get s identity-migrations %))
       (filter #(= definition-cid (:from-cid %)))
       (sort-by :cid) vec))

(defn history
  "All namespace commits reachable through parent links from HEAD."
  [s head]
  (loop [todo [head] seen #{}]
    (if-let [cid (peek todo)]
      (if (contains? seen cid)
        (recur (pop todo) seen)
        (let [commit (namespace-commit s cid)]
          (require-value some? commit :namespace/missing-parent {:cid cid})
          (recur (into (pop todo) (:parents commit)) (conj seen cid))))
      seen)))

(defn put-execution-receipt!
  "C4: verify and persist one execution provenance record. REQUIRED-EFFECTS is
  recomputed from the admitted code graph; every effect must be present in the
  concrete post-intersection GRANTED-EFFECTS. The receipt CID authenticates the
  complete block but never grants authority by itself."
  [s verify {:keys [cid block code-root-cid artifact-cid compiler-contract-cid
                    input-root-cids output-root-cids package-lock-cid policy-cid
                    grant-cids host-receipt-cids granted-effects outcome]
             :as receipt}]
  (doseq [[value problem]
          [[cid :execution/cid-required]
           [code-root-cid :execution/code-root-required]
           [artifact-cid :execution/artifact-required]
           [compiler-contract-cid :execution/compiler-contract-required]
           [package-lock-cid :execution/package-lock-required]
           [policy-cid :execution/policy-required]]]
    (require-value string? value problem {}))
  (require-value true? (boolean (verify cid block)) :execution/cid-mismatch {:cid cid})
  (let [required (transitive-effects s code-root-cid)
        granted (set granted-effects)
        missing (set (remove granted required))
        artifact (store/-get s artifacts artifact-cid)]
    (require-value some? artifact :execution/artifact-missing {:artifact-cid artifact-cid})
    (require-value #(= code-root-cid (:code-root-cid %)) artifact
                   :execution/artifact-code-mismatch {:code-root-cid code-root-cid})
    (require-value empty? missing :execution/capability-missing
                   {:required required :granted granted :missing missing})
    (doseq [[values problem]
            [[input-root-cids :execution/input-roots-invalid]
             [output-root-cids :execution/output-roots-invalid]
             [grant-cids :execution/grants-invalid]
             [host-receipt-cids :execution/host-receipts-invalid]]]
      (require-value vector? values problem {}))
    (require-value keyword? outcome :execution/outcome-required {})
    (let [record (assoc receipt
                        :required-effects required
                        :granted-effects granted)]
      (if-let [existing (store/-get s execution-receipts cid)]
        (do (require-value #(= existing %) record
                           :execution/cid-record-conflict {:cid cid})
            existing)
        (do
          (store/-put s execution-receipts cid record)
          (doseq [datom
                  [[:db/add cid :execution/code-root-cid code-root-cid]
                   [:db/add cid :execution/artifact-cid artifact-cid]
                   [:db/add cid :execution/policy-cid policy-cid]
                   [:db/add cid :execution/outcome outcome]]]
            (store/-append s datom-stream {:datom datom}))
          record)))))

(defn execution-receipt [s cid]
  (store/-get s execution-receipts cid))

(defn put-execution-identity!
  "Verify and persist one portable execution identity before its observed
  result is exposed. The host supplies VERIFY because this portable store does
  not choose a hash implementation. The ABI descriptor is closed and contains
  no bearer capability handle; CID verification authenticates its canonical
  block at the storage boundary."
  [s verify {:keys [cid block identity]}]
  (require-value string? cid :execution-identity/cid-required {})
  (require-value some? block :execution-identity/block-required {:cid cid})
  (require-value true? (boolean (verify cid block))
                 :execution-identity/cid-mismatch {:cid cid})
  (require-value abi/valid-execution-identity? identity
                 :execution-identity/invalid-descriptor {:cid cid})
  (let [record {:cid cid :block block :identity identity}]
    (if-let [existing (store/-get s execution-identities cid)]
      (do (require-value #(= existing %) record
                         :execution-identity/cid-record-conflict {:cid cid})
          existing)
      (do
        (store/-put s execution-identities cid record)
        (doseq [[attribute value]
                [[:execution-identity/plan-cid (:plan-cid identity)]
                 [:execution-identity/code-closure-cid (:code-closure-cid identity)]
                 [:execution-identity/artifact-cid (:artifact-cid identity)]
                 [:execution-identity/component-cid (:component-cid identity)]
                 [:execution-identity/wit-world-cid (:wit-world-cid identity)]
                 [:execution-identity/policy-cid (:policy-cid identity)]
                 [:execution-identity/policy-decision-cid (:policy-decision-cid identity)]
                 [:execution-identity/db-basis (:db-basis identity)]
                 [:execution-identity/runtime-identity (:runtime-identity identity)]
                 [:execution-identity/outcome-cid (:outcome-cid identity)]]
              :when (some? value)]
          (store/-append s datom-stream {:datom [:db/add cid attribute value]}))
        (doseq [[attribute values]
                [[:execution-identity/grant-cid (:grant-cids identity)]
                 [:execution-identity/approval-cid (:approval-cids identity)]
                 [:execution-identity/host-receipt-cid (:host-receipt-cids identity)]]
                value values]
          (store/-append s datom-stream {:datom [:db/add cid attribute value]}))
        record))))

(defn execution-identity [s cid]
  (store/-get s execution-identities cid))

(def query-receipt-keys
  #{:cid :block :execution-identity-cid :query-cid :result-cid
    :basis :policy-cid :tenant :purpose :resource-cids})

(defn put-query-receipt!
  "Persist a content-addressed receipt for an authorized data read.

  The receipt block intentionally does not contain EXECUTION-IDENTITY-CID:
  an execution identity already lists this receipt CID, so including the
  reverse link in the hashed block would create a CID cycle.  Kotobase stores
  that reverse association as an immutable datom after verifying both sides.
  Raw query results are addressed by RESULT-CID and never projected here."
  [s verify {:keys [cid block execution-identity-cid query-cid result-cid
                    basis policy-cid tenant purpose resource-cids] :as receipt}]
  (require-value #(= query-receipt-keys (set (keys %))) receipt
                 :query-receipt/invalid-record {})
  (doseq [[value problem] [[cid :query-receipt/cid-required]
                           [execution-identity-cid :query-receipt/execution-identity-required]
                           [query-cid :query-receipt/query-cid-required]
                           [result-cid :query-receipt/result-cid-required]
                           [basis :query-receipt/basis-required]
                           [policy-cid :query-receipt/policy-required]
                           [tenant :query-receipt/tenant-required]]]
    (require-value #(and (string? %) (seq %)) value problem {}))
  (require-value keyword? purpose :query-receipt/purpose-required {})
  (require-value #(and (vector? %) (seq %) (every? string? %)) resource-cids
                 :query-receipt/resources-invalid {})
  (require-value true? (boolean (verify cid block)) :query-receipt/cid-mismatch {:cid cid})
  (let [identity-record (execution-identity s execution-identity-cid)
        identity (:identity identity-record)]
    (require-value some? identity-record :query-receipt/execution-identity-missing
                   {:execution-identity-cid execution-identity-cid})
    (require-value (fn [_] (= basis (:db-basis identity))) identity :query-receipt/basis-mismatch
                   {:basis basis :identity-basis (:db-basis identity)})
    (require-value (fn [_] (= policy-cid (:policy-cid identity))) identity :query-receipt/policy-mismatch
                   {:policy-cid policy-cid :identity-policy-cid (:policy-cid identity)})
    (require-value (fn [_] (contains? (set (:host-receipt-cids identity)) cid))
                   identity
                   :query-receipt/not-bound-by-identity
                   {:execution-identity-cid execution-identity-cid :cid cid})
    (if-let [existing (store/-get s query-receipts cid)]
      (do (require-value #(= existing %) receipt :query-receipt/cid-record-conflict {:cid cid})
          existing)
      (do
        (store/-put s query-receipts cid receipt)
        (doseq [[attribute value]
                [[:query-receipt/execution-identity execution-identity-cid]
                 [:query-receipt/query-cid query-cid]
                 [:query-receipt/result-cid result-cid]
                 [:query-receipt/basis basis]
                 [:query-receipt/policy-cid policy-cid]
                 [:query-receipt/tenant tenant]
                 [:query-receipt/purpose purpose]]]
          (store/-append s datom-stream {:datom [:db/add cid attribute value]}))
        (doseq [resource-cid resource-cids]
          (store/-append s datom-stream
                         {:datom [:db/add cid :query-receipt/resource-cid resource-cid]}))
        receipt))))

(defn query-receipt [s cid]
  (store/-get s query-receipts cid))

(defn export-closure
  "C5: portable block bundle for ROOT, dependency-first. The receiver may ask
  for only the CIDs it lacks and admit each block through put-definition!."
  [s root]
  (let [seen (atom #{}) out (atom [])]
    (letfn [(visit [cid]
              (when-not (contains? @seen cid)
                (let [record (get-definition s cid)]
                  (require-value some? record :code/missing-dependency {:cid cid})
                  (doseq [dep (:code.definition/dependencies record)] (visit dep))
                  (swap! seen conj cid)
                  (swap! out conj record))))]
      (visit root)
      @out)))

(defn missing-cids [s cids]
  (->> cids (remove #(get-definition s %)) sort vec))

(defn import-closure!
  "Admit a dependency-first bundle. VERIFY is mandatory and every dependency
  edge is checked by put-definition!."
  [s verify records]
  (mapv #(put-definition! s verify %) records))

(defn export-code-graph
  "Definition closure plus the unique semantic type blocks it references."
  [s root]
  (let [definitions (export-closure s root)
        type-cids (set (keep :code.definition/type-cid definitions))]
    {:types (mapv (fn [cid]
                    (or (get-type s cid)
                        (throw (ex-info "missing semantic type"
                                        {:problem :code/missing-type :type-cid cid}))))
                  (sort type-cids))
     :definitions definitions}))

(defn import-code-graph!
  "Admit type blocks first, then the dependency-first definition closure."
  [s verify {:keys [types definitions]}]
  (doseq [type types] (put-type! s verify type))
  (import-closure! s verify definitions))

(defn sync-code-root!
  "Synchronize only missing semantic blocks from SOURCE to TARGET.

  Both endpoints are ordinary IStore values, so they may independently be
  LocalStore or XRPC-backed KotobaseStore instances. AUTHORIZE receives each
  source definition record before disclosure. When COMPILER-CONTRACT-CID is
  supplied, a matching artifact is transferred only through VERIFY-ARTIFACT."
  [source target verify
   {:keys [code-root-cid authorize compiler-contract-cid verify-artifact]
    :or {authorize (constantly false)}}]
  (require-value string? code-root-cid :code/code-root-required {})
  (let [{:keys [types definitions]} (export-code-graph source code-root-cid)
        _ (doseq [record definitions]
            (require-value true? (boolean (authorize record))
                           :code/disclosure-denied
                           {:cid (:code.definition/cid record)
                            :visibility (:code.definition/visibility record)}))
        missing-defs (set (missing-cids target
                                        (map :code.definition/cid definitions)))
        definitions (filterv #(contains? missing-defs
                                         (:code.definition/cid %))
                             definitions)
        needed-types (set (keep :code.definition/type-cid definitions))
        types (filterv #(and (contains? needed-types (:cid %))
                             (nil? (get-type target (:cid %))))
                       types)]
    (import-code-graph! target verify {:types types :definitions definitions})
    (let [artifact (when compiler-contract-cid
                     (find-artifact source code-root-cid compiler-contract-cid))
          artifact-transferred?
          (boolean
           (when (and artifact
                      (nil? (find-artifact target code-root-cid
                                           compiler-contract-cid)))
             (require-value fn? verify-artifact
                            :code/artifact-verifier-required {})
             (put-artifact! target verify-artifact artifact)))]
      {:code-root-cid code-root-cid
       :definition-cids (mapv :code.definition/cid definitions)
       :type-cids (mapv :cid types)
       :artifact-cid (:artifact-cid artifact)
       :artifact-transferred? artifact-transferred?})))

(defn execute-code-root!
  "C5 host-neutral execution coordinator.

  COMPILE receives the admitted dependency-first records plus compiler CID and
  returns an artifact record. RUN receives the artifact and INPUT. AUTHORIZE
  receives required effects and must return the concrete granted effect set or
  throw. Existing artifacts are reused by derivation key. This function does
  not persist a receipt because the host must first add its policy/grant/input/
  output links and content-address that final evidence block."
  [s {:keys [code-root-cid compiler-contract-cid input compile run authorize
             verify-artifact]}]
  (let [closure (export-code-graph s code-root-cid)
        required (transitive-effects s code-root-cid)
        granted (set (authorize required))]
    (require-value empty? (set (remove granted required))
                   :execution/capability-missing
                   {:required required :granted granted})
    (let [artifact (or (find-artifact s code-root-cid compiler-contract-cid)
                       (let [built (compile closure compiler-contract-cid)]
                         (put-artifact! s verify-artifact built)
                         built))
          result (run artifact input)]
      {:code-root-cid code-root-cid
       :compiler-contract-cid compiler-contract-cid
       :artifact artifact
       :required-effects required
       :granted-effects granted
       :result result})))

(defn pin-root!
  "Pin an immutable root for deployment, release, audit retention, legal hold,
  or research evidence. KIND is descriptive policy data, not authority."
  [s {:keys [id kind root-cid] :as pin}]
  (require-value string? id :retention/id-required {})
  (require-value keyword? kind :retention/kind-required {:id id})
  (require-value string? root-cid :retention/root-required {:id id})
  (let [record (assoc pin :active? true)]
    (store/-put s retention-pins id record)
    (store/-append s retention-stream {:op :pin :id id :root-cid root-cid
                                       :kind kind})
    record))

(defn revoke-pin!
  "Auditably deactivate a pin. Physical deletion remains outside IStore."
  [s id reason]
  (effect/guard!
   {:evaluate
    (fn [{:keys [store pin-id revocation-reason]}]
      (let [pin (store/-get store retention-pins pin-id)]
        (require-value some? pin :retention/pin-not-found {:id pin-id})
        (require-value string? revocation-reason
                       :retention/reason-required {:id pin-id})
        {:retention-revoke/allowed? true :retention-revoke/pin pin}))
    :request {:store s :pin-id id :revocation-reason reason}
    :approved? :retention-revoke/allowed?
    :action :retention-pin/revoke
    :resource id
    :digest reason
    :effect
    (fn [decision]
      (let [record (assoc (:retention-revoke/pin decision)
                          :active? false :revocation-reason reason)]
        (store/-put s retention-pins id record)
        (store/-append s retention-stream {:op :revoke :id id :reason reason})
        record))}))

(defn retention-roots [s]
  (set (keep (fn [id]
               (let [pin (store/-get s retention-pins id)]
                 (when (:active? pin) (:root-cid pin))))
             (store/-list s retention-pins))))

(defn- namespace-definition-roots [s]
  (set
   (mapcat (fn [cid]
             (vals (:bindings (store/-get s namespace-commits cid))))
           (store/-list s namespace-commits))))

(defn retained-definition-cids
  "Definitions reachable from namespace history, artifacts, execution receipts,
  or explicit retention pins. Non-definition pin roots are ignored here and
  remain the responsibility of their owning block collection."
  [s]
  (let [roots (set (concat
                    (namespace-definition-roots s)
                    (keep (fn [cid]
                            (:code-root-cid (store/-get s artifacts cid)))
                          (store/-list s artifacts))
                    (keep (fn [cid]
                            (:code-root-cid (store/-get s execution-receipts cid)))
                          (store/-list s execution-receipts))
                    (filter #(get-definition s %) (retention-roots s))))]
    (reduce (fn [out root] (into out (dependency-closure s root))) #{} roots)))

(defn gc-plan
  "Return an auditable deletion plan; IStore deliberately has no destructive
  delete operation. Applying the plan belongs to a storage adapter with its own
  retention authorization and sealed-block accounting."
  [s]
  (let [all (set (store/-list s definitions))
        retained (retained-definition-cids s)]
    {:retained retained
     :candidates (set (remove retained all))
     :pins (vec (sort (store/-list s retention-pins)))}))
