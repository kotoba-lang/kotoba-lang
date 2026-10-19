(ns kotoba.ontology.connector
  "Registry of external-data connectors, and the pure step that tags a
  normalized fact with ontology provenance before it lands in a
  cloud-itonami-* activity/artifact graph.

  Fetch/parse stays where kotoba-lang/goyoukiki already put it: a per-source
  JVM-only adapter (goyoukiki.jp.kkj, goyoukiki.jp.geps) that does the actual
  HTTP call and maps the raw response to the domain's own pure-data model.
  This namespace does not fetch anything — it only records which connectors
  exist, what object type they produce, and how to stamp their output with
  :ontology/* provenance so it can be told apart from every other source."
  (:require [kotoba.ontology :as ontology]))

(def registry
  {:kotoba.registry/id :kotoba/ontology-connectors
   :kotoba.registry/version 1
   :connectors
   [{:id :jp.kkj
     :name "kkj.go.jp 官公需情報ポータル 検索API"
     :object-type :tender
     :repo "https://github.com/kotoba-lang/goyoukiki"
     :status :implemented}
    {:id :jp.geps
     :name "GEPS 落札実績オープンデータ"
     :object-type :tender
     :repo "https://github.com/kotoba-lang/goyoukiki"
     :status :implemented}]})

(defn connectors
  ([] (connectors registry))
  ([reg] (:connectors reg)))

(defn by-id
  ([] (by-id registry))
  ([reg] (into {} (map (juxt :id identity) (connectors reg)))))

(defn get-connector
  ([id] (get-connector registry id))
  ([reg id] (get (by-id reg) id)))

(defn tag
  "Attach ontology provenance to an already-normalized fact map. Pure — the
  live fetch stays in the connector's own adapter; this only marks where a
  fact came from and what object type it claims to satisfy.

  Fails closed on an unregistered `connector-id`: throws rather than
  silently stamping `:ontology/type nil` — a tag that names a source with
  no known object-type is not provenance, it is a fabricated-looking label
  that would make `kotoba.ontology.connector/tagged-conforms?` on it
  ambiguous instead of false. Every caller in this fleet only ever tags
  with an id it registered itself, so this can never fire from correct
  code — it exists to catch a typo'd/unregistered id loudly instead of
  producing a quietly-wrong fact."
  [connector-id fact & {:keys [reg fetched-at confidence]
                        :or {reg registry}}]
  (let [{:keys [object-type] :as connector} (get-connector reg connector-id)]
    (when-not connector
      (throw (ex-info (str "unregistered connector: " connector-id)
                      {:connector-id connector-id :known-ids (mapv :id (connectors reg))})))
    (cond-> (assoc fact :ontology/type object-type :ontology/source connector-id)
      fetched-at (assoc :ontology/fetched-at fetched-at)
      confidence (assoc :ontology/confidence confidence))))

(defn tagged-conforms?
  "True when `fact` both conforms to its connector's declared object type
  and carries :ontology/* provenance from a registered connector."
  ([connector-id fact] (tagged-conforms? registry ontology/registry connector-id fact))
  ([conn-reg onto-reg connector-id fact]
   (let [{:keys [object-type]} (get-connector conn-reg connector-id)]
     (boolean
      (and object-type
           (= object-type (:ontology/type fact))
           (= connector-id (:ontology/source fact))
           (ontology/conforms? onto-reg object-type (dissoc fact :ontology/type :ontology/source
                                                             :ontology/fetched-at :ontology/confidence)))))))
