(ns kotoba.lang.package-registry
  "First-party package registry kernel (ADR-2607180900 follow-up /
  package-rules.md m5).

  A registry maps package name+version to a signed, CID-pinned registry
  record. Safe Kotoba never executes name+version alone — resolution must
  produce a full lock entry (repo-rid, tree-cid, manifest-cid, signers,
  capabilities) before package-admission.

  This namespace is I/O-free: callers supply an already-parsed registry EDN
  map. Network/HTTP registry backends can project into the same shape."
  (:require [clojure.string :as str]
            [kotoba.lang.package-contract :as package-contract]))

(def registry-version 1)

(def record-required
  [:registry/name
   :registry/version
   :registry/repo-rid
   :registry/commit
   :registry/tree-cid
   :registry/manifest-cid
   :registry/signers
   :registry/capabilities])

(def record-optional
  [:registry/ref
   :registry/kind
   :registry/component-cid
   :registry/record-cid
   :registry/provides
   :registry/consumes
   :registry/build])

(defn- non-empty-string?
  [x]
  (and (string? x) (not (str/blank? x))))

(defn registry-key
  "Canonical name@version key."
  [name version]
  (str name "@" version))

(defn- index-records
  "Build {\"name@version\" record} from a registry's :records vector."
  [records]
  (into {}
        (keep (fn [r]
                (when (and (map? r)
                           (non-empty-string? (:registry/name r))
                           (non-empty-string? (:registry/version r)))
                  [(registry-key (:registry/name r) (:registry/version r)) r])))
        records))

(defn normalize
  "Normalize a registry EDN map. Accepts either
  {:kotoba.registry/version 1 :records [...]}
  or a bare vector of records (wrapped as version 1)."
  [registry]
  (cond
    (vector? registry)
    {:kotoba.registry/version registry-version
     :records (vec registry)
     :index (index-records registry)}

    (map? registry)
    (let [records (or (:records registry)
                      (:kotoba.registry/records registry)
                      [])
          version (or (:kotoba.registry/version registry)
                      (:version registry)
                      registry-version)]
      {:kotoba.registry/version version
       :records (vec records)
       :index (index-records records)})

    :else
    {:kotoba.registry/version 0 :records [] :index {}}))

(defn record-problems
  "Structural problems for one registry record."
  [record]
  (cond
    (not (map? record))
    [{:problem :registry/record-not-a-map :value record}]

    :else
    (cond-> []
      (some #(nil? (get record %)) record-required)
      (into (keep (fn [k]
                    (when-not (contains? record k)
                      {:problem :registry/missing-field :field k}))
                  record-required))

      (and (contains? record :registry/repo-rid)
           (not (package-contract/cid? (:registry/repo-rid record))))
      (conj {:problem :registry/cid-invalid :field :registry/repo-rid
             :value (:registry/repo-rid record)})

      (and (contains? record :registry/tree-cid)
           (not (package-contract/cid? (:registry/tree-cid record))))
      (conj {:problem :registry/cid-invalid :field :registry/tree-cid
             :value (:registry/tree-cid record)})

      (and (contains? record :registry/manifest-cid)
           (not (package-contract/cid? (:registry/manifest-cid record))))
      (conj {:problem :registry/cid-invalid :field :registry/manifest-cid
             :value (:registry/manifest-cid record)})

      (and (contains? record :registry/component-cid)
           (some? (:registry/component-cid record))
           (not (package-contract/cid? (:registry/component-cid record))))
      (conj {:problem :registry/cid-invalid :field :registry/component-cid
             :value (:registry/component-cid record)})

      (and (contains? record :registry/signers)
           (not (and (vector? (:registry/signers record))
                     (seq (:registry/signers record))
                     (every? non-empty-string? (:registry/signers record)))))
      (conj {:problem :registry/signers-invalid
             :value (:registry/signers record)})

      (and (contains? record :registry/capabilities)
           (not (vector? (:registry/capabilities record))))
      (conj {:problem :registry/capabilities-invalid
             :value (:registry/capabilities record)})

      (and (contains? record :registry/kind)
           (not (contains? package-contract/allowed-package-kinds
                           (:registry/kind record))))
      (conj {:problem :registry/unknown-kind
             :value (:registry/kind record)}))))

(defn validate
  "Validate registry structure. Returns {:ok? bool :problems [...]}."
  [registry]
  (let [norm (normalize registry)
        problems
        (cond-> []
          (not= registry-version (:kotoba.registry/version norm))
          (conj {:problem :registry/version-unsupported
                 :value (:kotoba.registry/version norm)})

          (not (vector? (:records norm)))
          (conj {:problem :registry/records-required})

          true
          (into (mapcat record-problems (:records norm)))

          (not= (count (:records norm)) (count (:index norm)))
          (conj {:problem :registry/duplicate-or-incomplete-keys}))]
    {:ok? (empty? problems)
     :problems problems
     :registry norm}))

(defn lookup
  "Find a registry record by name and version. Returns the record or nil."
  [registry name version]
  (let [norm (normalize registry)]
    (get (:index norm) (registry-key name version))))

(defn resolve-record
  "Resolve name+version. Returns
  {:ok? true :record ...} or {:ok? false :problems [...]}."
  [registry name version]
  (let [v (validate registry)]
    (if-not (:ok? v)
      {:ok? false :problems (:problems v)}
      (if-let [record (lookup (:registry v) name version)]
        (let [rp (record-problems record)]
          (if (seq rp)
            {:ok? false :problems rp}
            {:ok? true :record record}))
        {:ok? false
         :problems [{:problem :registry/not-found
                     :name name
                     :version version
                     :key (registry-key name version)}]}))))

(defn record->lock-dep
  "Project a registry record into a package-contract lock dependency entry.
  Optional CAPABILITY-GRANT (vector) narrows :dep/capabilities; defaults to
  the record's declared capabilities (still subject to admission trust)."
  ([record] (record->lock-dep record nil))
  ([record capability-grant]
   (let [caps (vec (or capability-grant (:registry/capabilities record) []))]
     (cond-> {:dep/name (:registry/name record)
              :dep/version (:registry/version record)
              :dep/repo-rid (:registry/repo-rid record)
              :dep/commit (:registry/commit record)
              :dep/tree-cid (:registry/tree-cid record)
              :dep/manifest-cid (:registry/manifest-cid record)
              :dep/signers (vec (:registry/signers record))
              :dep/capabilities caps}
       (:registry/ref record) (assoc :dep/ref (:registry/ref record))
       (:registry/kind record) (assoc :dep/kind (:registry/kind record))
       (:registry/component-cid record)
       (assoc :dep/component-cid (:registry/component-cid record)
              :dep/build (merge {:deterministic true}
                                (:registry/build record)
                                {:component-cid (:registry/component-cid record)}))
       (:registry/record-cid record)
       (assoc :dep/registry-record-cid (:registry/record-cid record))
       (:registry/provides record) (assoc :dep/provides (:registry/provides record))
       (:registry/consumes record) (assoc :dep/consumes (:registry/consumes record))))))

(defn lock-from-requests
  "Resolve a vector of version-only requests into a lockfile.

  Each request is {:name \"kotoba-lang/json\" :version \"0.1.0\"
                   :capabilities [] optional}.

  Returns {:ok? true :lock {...} :deps [...]}
  or {:ok? false :problems [...]} — never a partial lock on failure
  (fail-closed: one missing package rejects the whole resolve)."
  [registry requests]
  (let [v (validate registry)]
    (if-not (:ok? v)
      {:ok? false :problems (:problems v)}
      (let [results
            (mapv (fn [req]
                    (let [name (or (:name req) (:dep/name req))
                          version (or (:version req) (:dep/version req))
                          caps (or (:capabilities req) (:dep/capabilities req))
                          resolved (resolve-record (:registry v) name version)]
                      (if (:ok? resolved)
                        {:ok? true
                         :dep (record->lock-dep (:record resolved) caps)}
                        {:ok? false
                         :problems (:problems resolved)
                         :request req})))
                  (or requests []))]
        (if (every? :ok? results)
          (let [deps (mapv :dep results)]
            {:ok? true
             :lock {:kotoba.lock/version 1 :deps deps}
             :deps deps})
          {:ok? false
           :problems (vec (mapcat :problems (remove :ok? results)))})))))

(defn version-only-request?
  "True when DEP looks like an unsafe name+version-only reference
  (missing CID pins) — the shape the registry exists to resolve."
  [dep]
  (and (map? dep)
       (non-empty-string? (or (:dep/name dep) (:name dep)))
       (non-empty-string? (or (:dep/version dep) (:version dep)))
       (not (package-contract/cid? (or (:dep/repo-rid dep) (:repo-rid dep))))
       (not (package-contract/cid? (or (:dep/manifest-cid dep)
                                       (:manifest-cid dep))))))
