(ns kotoba.compiler.logic-manifest
  "Content-bound compiler facts for runtime authorization.

  This namespace does not mint authority and does not store a Biscuit. It
  projects checked KIR into canonical IPLD-ready data. A host verifies the
  resulting block/attestation, then injects its facts into the authorizer as
  the trusted `amu:` provenance."
  (:require [kotoba.abi.contract :as abi]
            [kotoba.artifact.core :as artifact]
            [kotoba.sema :as sema]))

(def manifest-format :kotoba.logic-manifest/v1)
(def compiler-facts-format :kotoba.compiler-facts/v1)
(def max-links 1024)
(def max-resource-bounds 32)

(def required-input-keys
  #{:definition-cid :artifact-cid :compiler-contract :semantics-cid :world-cid
    :dependency-cids :intent-schema-cids :resource-bounds})

(def ^:private manifest-fields
  #{:format :definition-cid :artifact-cid :compiler-contract :semantics-cid
    :world-cid :dependency-cids :intent-schema-cids :resource-bounds
    :kir-sha256 :effects :wire-effects :function-effects :manifest-sha256})

(def ^:private compiler-facts-fields
  #{:format :manifest-cid :manifest-sha256 :facts})

(defn- fail! [message data]
  (throw (ex-info message (assoc data :phase :logic-manifest))))

(defn- exact-map? [value fields]
  (and (map? value) (= fields (set (keys value)))))

(defn- cid-vector? [value]
  (and (vector? value)
       (<= (count value) max-links)
       (= (count value) (count (distinct value)))
       (every? abi/cid? value)))

(defn- resource-bounds? [value]
  (and (map? value)
       (<= (count value) max-resource-bounds)
       (every? (fn [[k v]]
                 (and (qualified-keyword? k) (integer? v) (<= 0 v)))
               value)))

(defn- semantic-effect [effect]
  (if (and (vector? effect)
           (= 2 (count effect))
           (= :cap/call (first effect))
           (integer? (second effect)))
    (or (get sema/capability-id->name (second effect))
        (fail! "KIR names an unknown capability wire id" {:effect effect}))
    effect))

(defn- semantic-effects [effects]
  (into #{} (map semantic-effect) effects))

(defn- function-effects [kir]
  (->> (:functions kir)
       (map (fn [{:keys [name effects]}]
              {:function (str name)
               :effects (semantic-effects (set effects))
               :wire-effects (set effects)}))
       (sort-by :function)
       vec))

(defn- hash-body [manifest]
  (artifact/sha256 (dissoc manifest :manifest-sha256)))

(defn valid?
  "Validate the manifest's closed shape and self-hash. This validates content,
  not storage availability or a compiler signature."
  [manifest]
  (and (exact-map? manifest manifest-fields)
       (= manifest-format (:format manifest))
       (every? abi/cid? ((juxt :definition-cid :artifact-cid :compiler-contract
                               :semantics-cid :world-cid) manifest))
       (cid-vector? (:dependency-cids manifest))
       (cid-vector? (:intent-schema-cids manifest))
       (resource-bounds? (:resource-bounds manifest))
       (string? (:kir-sha256 manifest))
       (= 64 (count (:kir-sha256 manifest)))
       (set? (:effects manifest))
       (set? (:wire-effects manifest))
       (vector? (:function-effects manifest))
       (string? (:manifest-sha256 manifest))
       (= (:manifest-sha256 manifest) (hash-body manifest))))

(defn build!
  "Build `kotoba.logic-manifest/v1` from checked COMPILED KIR and immutable
  identities. INPUT is exact. In particular it cannot provide either effect
  row; both the semantic row and target wire row are recomputed from KIR."
  [compiled input]
  (when-not (and (map? input) (= required-input-keys (set (keys input))))
    (fail! "logic manifest input is not exact" {:reason :invalid-input}))
  (when-not (and (map? compiled) (map? (:kir compiled)))
    (fail! "logic manifest requires checked KIR" {:reason :missing-kir}))
  (when-not (and (every? abi/cid? ((juxt :definition-cid :artifact-cid
                                         :compiler-contract :semantics-cid
                                         :world-cid) input))
                 (cid-vector? (:dependency-cids input))
                 (cid-vector? (:intent-schema-cids input))
                 (resource-bounds? (:resource-bounds input)))
    (fail! "logic manifest identities or bounds are invalid"
           {:reason :invalid-identity-or-bounds}))
  (let [kir (:kir compiled)
        wire-effects (set (:effects kir))
        body (-> input
                 ;; Link order is not semantic. Canonicalize it before hashing
                 ;; so two graph walkers cannot give one definition two
                 ;; manifest identities.
                 (update :dependency-cids #(vec (sort %)))
                 (update :intent-schema-cids #(vec (sort %)))
                 (assoc :format manifest-format
                        :kir-sha256 (artifact/sha256 kir)
                        :effects (semantic-effects wire-effects)
                        :wire-effects wire-effects
                        :function-effects (function-effects kir)))
        manifest (assoc body :manifest-sha256 (artifact/sha256 body))]
    (when-not (valid? manifest)
      (fail! "logic manifest rejected" {:reason :invalid-manifest}))
    manifest))

(defn authorizer-facts
  "Project a valid manifest to bounded n-ary tuples. Predicates are strings so
  the IPLD representation is data rather than executable Clojure symbols."
  [manifest]
  (when-not (valid? manifest)
    (fail! "cannot project invalid logic manifest" {:reason :invalid-manifest}))
  (let [definition (:definition-cid manifest)]
    (vec
     (concat
      [["amu:definition" definition]
       ["amu:artifact" definition (:artifact-cid manifest)]
       ["amu:compiler" definition (:compiler-contract manifest)]
       ["amu:semantics" definition (:semantics-cid manifest)]
       ["amu:world" definition (:world-cid manifest)]]
      (map (fn [dependency] ["amu:depends-on" definition dependency])
           (:dependency-cids manifest))
      (map (fn [effect] ["amu:requires" definition effect])
           (sort-by pr-str (:effects manifest)))
      (map (fn [schema] ["amu:emits" definition schema])
           (:intent-schema-cids manifest))))))

(defn authorizer-evidence!
  "Bind projected compiler facts to the CID assigned to the persisted manifest.
  `abi/cid?` checks structure only; the host must verify the block bytes and
  compiler attestation before treating this envelope as trusted."
  [manifest-cid manifest]
  (when-not (abi/cid? manifest-cid)
    (fail! "logic manifest CID is invalid" {:reason :invalid-manifest-cid}))
  (let [evidence {:format compiler-facts-format
                  :manifest-cid manifest-cid
                  :manifest-sha256 (:manifest-sha256 manifest)
                  :facts (authorizer-facts manifest)}]
    (when-not (= compiler-facts-fields (set (keys evidence)))
      (fail! "compiler facts envelope rejected" {:reason :invalid-envelope}))
    evidence))
