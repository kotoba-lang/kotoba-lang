(ns kotoba.lang.package-registry
  "Compatibility registry kernel. Resolution is pure and always emits a
  complete CID-pinned lock entry; name@version is never executable input."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [kotoba.lang.package-contract :as contract]))

(def registry-version 1)
(def required [:registry/name :registry/version :registry/repo-rid
               :registry/commit :registry/tree-cid :registry/manifest-cid
               :registry/signers :registry/capabilities])

(defn- text? [x] (and (string? x) (not (str/blank? x))))
(defn registry-key [name version] [name version])

(defn version-only-request?
  "True for the deliberately non-admissible name+version request shape.
  Resolution must turn this into a CID-pinned lock dependency before it can
  cross the package admission boundary."
  [request]
  (and (map? request)
       (= #{:dep/name :dep/version} (set (keys request)))
       (text? (:dep/name request)) (text? (:dep/version request))))

(defn normalize [registry]
  (let [records (cond (vector? registry) registry
                      (map? registry) (or (:records registry) (:kotoba.registry/records registry) [])
                      :else [])
        version (if (map? registry) (or (:kotoba.registry/version registry) (:version registry) registry-version)
                    registry-version)]
    {:kotoba.registry/version version :records (vec records)
     :index (into {} (keep (fn [r] (when (and (map? r) (text? (:registry/name r))
                                             (text? (:registry/version r)))
                                    [(registry-key (:registry/name r) (:registry/version r)) r]))) records)}))

(defn record-problems [record]
  (cond
    (not (map? record)) [{:problem :registry/record-not-a-map}]
    :else
    (cond-> []
      (some #(not (contains? record %)) required)
      (into (keep #(when-not (contains? record %) {:problem :registry/missing-field :field %}) required))
      (and (contains? record :registry/repo-rid) (not (contract/cid? (:registry/repo-rid record))))
      (conj {:problem :registry/cid-invalid :field :registry/repo-rid})
      (and (contains? record :registry/tree-cid) (not (contract/cid? (:registry/tree-cid record))))
      (conj {:problem :registry/cid-invalid :field :registry/tree-cid})
      (and (contains? record :registry/manifest-cid) (not (contract/cid? (:registry/manifest-cid record))))
      (conj {:problem :registry/cid-invalid :field :registry/manifest-cid})
      (not (and (vector? (:registry/signers record)) (seq (:registry/signers record))
                (every? text? (:registry/signers record))))
      (conj {:problem :registry/signers-invalid})
      (not (vector? (:registry/capabilities record)))
      (conj {:problem :registry/capabilities-invalid})
      ;; CI2: a record MAY publish the definition identities it exports. It is
      ;; optional because effectful code stays component-addressed until its
      ;; capability interface is in the typed KIR, but when it is present it
      ;; must be a vector of real CIDv1s -- an unparseable string here would
      ;; otherwise become an unenforceable lock entry downstream.
      (and (contains? record :registry/definition-cids)
           (not (and (vector? (:registry/definition-cids record))
                     (seq (:registry/definition-cids record))
                     (every? contract/cid? (:registry/definition-cids record))
                     (apply distinct? (:registry/definition-cids record)))))
      (conj {:problem :registry/definition-cids-invalid}))))

(defn validate [registry]
  (let [r (normalize registry)
        problems (vec (concat
                       (when-not (= registry-version (:kotoba.registry/version r))
                         [{:problem :registry/version-unsupported}])
                       (mapcat record-problems (:records r))
                       (when-not (= (count (:records r)) (count (:index r)))
                         [{:problem :registry/duplicate-or-incomplete-keys}])))]
    {:ok? (empty? problems) :problems problems :registry r}))

(defn lookup [registry name version]
  (get-in (normalize registry) [:index (registry-key name version)]))

(defn resolve-record [registry name version]
  (let [v (validate registry)]
    (if-not (:ok? v) {:ok? false :problems (:problems v)}
      (if-let [record (lookup (:registry v) name version)]
        (let [problems (cond-> (record-problems record)
                         (or (not= name (:registry/name record))
                             (not= version (:registry/version record)))
                         (conj {:problem :registry/coordinate-mismatch
                                :requested {:name name :version version}
                                :resolved {:name (:registry/name record)
                                           :version (:registry/version record)}}))]
          (if (seq problems) {:ok? false :problems problems} {:ok? true :record record}))
        {:ok? false :problems [{:problem :registry/not-found :name name :version version}]}))))

(defn record->lock-dep [record capability-grant]
  (let [caps (vec (or capability-grant []))]
    (cond-> {:dep/name (:registry/name record) :dep/version (:registry/version record)
             :dep/repo-rid (:registry/repo-rid record) :dep/commit (:registry/commit record)
             :dep/tree-cid (:registry/tree-cid record) :dep/manifest-cid (:registry/manifest-cid record)
             :dep/signers (vec (:registry/signers record)) :dep/capabilities caps}
      (:registry/kind record) (assoc :dep/kind (:registry/kind record))
      (:registry/component-cid record) (assoc :dep/component-cid (:registry/component-cid record))
      ;; CI2: the alias resolves to the definition identities, so linking by
      ;; name can never silently substitute different code. Sorted because
      ;; dependency order is not semantic and the lock should be stable.
      (:registry/definition-cids record)
      (assoc :dep/definition-cids (vec (sort (:registry/definition-cids record)))))))

(defn lock-from-requests [registry requests]
  (let [results (mapv (fn [request]
                        (let [resolved (resolve-record registry (or (:name request) (:dep/name request))
                                                       (or (:version request) (:dep/version request)))
                              requested-caps (or (:capabilities request)
                                                 (:dep/capabilities request)
                                                 [])]
                          (cond
                            (not (:ok? resolved)) resolved
                            (not (vector? requested-caps))
                            {:ok? false :problems [{:problem :registry/capability-grant-invalid}]}
                            (not (set/subset? (set requested-caps)
                                              (set (:registry/capabilities (:record resolved)))))
                            {:ok? false
                             :problems [{:problem :registry/capability-grant-not-subset
                                         :requested requested-caps
                                         :declared (:registry/capabilities (:record resolved))}]}
                            :else
                            (assoc resolved :dep (record->lock-dep (:record resolved)
                                                                  requested-caps)))))
                      requests)]
    (if (every? :ok? results)
      {:ok? true :lock {:kotoba.lock/version 1 :deps (mapv :dep results)} :deps (mapv :dep results)}
      {:ok? false :problems (vec (mapcat :problems (remove :ok? results)))})))

(defn check-case
  "Runs one `lang/code-identity-conformance` `:alias` case (CI2): a name@version
  request must resolve, through the signed registry record, to exactly the
  definition identities the manifest expects. This is the property that makes
  `:dep/name` an alias rather than a substitution point."
  [tc data]
  (let [outcome (lock-from-requests (:registry data) [(:request data)])]
    (if (= :accept (:kind tc))
      (let [dep (first (:deps outcome))]
        {:ok? (and (boolean (:ok? outcome))
                   (= (:expected-definition-cids tc) (:dep/definition-cids dep)))
         :actual (select-keys dep [:dep/name :dep/definition-cids])})
      {:ok? (and (not (:ok? outcome))
                 (contains? (set (map :problem (:problems outcome)))
                            (:expected-problem tc)))
       :actual (:problems outcome)})))
