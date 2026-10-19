(ns kotoba.ontology
  "Minimal object-type + provenance schema.

  A cloud-itonami-* blueprint ingests facts from many external sources
  (m365, PLM/ERP, open-data portals, ...). Without a shared vocabulary each
  blueprint invents its own ad hoc keyword shape for the same real-world
  thing (an organization, a filing, a tender). This registry gives object
  types a stable :id and a required-attribute contract, so a fact can be
  checked and tagged with provenance before it lands in the
  activity/artifact graph.

  Seeded with :tender, the coarse common denominator of
  kotoba-lang/goyoukiki's already-implemented `goyoukiki.model/opportunity`
  (jp.kkj + jp.geps connectors, both real, both tested against captured
  responses). New object types are added as real connectors need them, not
  speculatively ahead of one.")

(def registry
  {:kotoba.registry/id :kotoba/ontology
   :kotoba.registry/version 1
   :object-types
   [{:id :tender
     :name "Procurement Tender / Opportunity"
     :attributes {:status :keyword}
     :key [:status]}]})

(defn object-types
  ([] (object-types registry))
  ([reg] (:object-types reg)))

(defn by-id
  ([] (by-id registry))
  ([reg] (into {} (map (juxt :id identity) (object-types reg)))))

(defn get-type
  ([id] (get-type registry id))
  ([reg id] (get (by-id reg) id)))

(defn conforms?
  "Structural check only: does `fact` have every attribute key declared for
  object-type `id`? Does not check attribute value types — object types
  declare a shape contract, not a validator."
  ([id fact] (conforms? registry id fact))
  ([reg id fact]
   (when-let [type (get-type reg id)]
     (every? #(contains? fact %) (keys (:attributes type))))))
