(ns owl.reason
  "Tractable, decidable-by-simple-graph-algorithm structural inference over
  owl.model ontologies.

  THIS IS NOT A DESCRIPTION-LOGIC REASONER. There is no SROIQ tableau, no
  existential/cardinality/disjunction reasoning, no consistency checking
  beyond owl.validate's structural checks, and no open-world entailment in
  general -- see README. It implements exactly three closure operations,
  each a plain graph-reachability computation over ASSERTED facts:

  (a) SubClassOf transitive closure (sec 9.1.1) -- atomic Class -> atomic
      Class edges only; a SubClassOf axiom whose :owl/sub or :owl/super is a
      complex class expression contributes no edge to this graph.
  (b) TransitiveObjectProperty closure (sec 9.2.13) over asserted
      ObjectPropertyAssertion facts (sec 9.6.4) -- an independent
      per-property reachability computation, NOT general property-chain
      reasoning (sec 9.2.1 SubObjectPropertyOf chains are not expanded).
  (c) SymmetricObjectProperty (sec 9.2.11) / InverseObjectProperties
      (sec 9.2.4) fact materialization -- adds the obviously-implied
      mirrored facts.

  For the SAME semantics evaluated as a least fixpoint against a stored
  graph rather than an in-memory ontology value, see `owl.rules`: it emits
  RDFS/OWL 2 RL as Datalog rules for a query engine to run, which is what
  you want when the ontology is too large to hold and when entailments have
  to compose (the inverse of a transitive property does not close in one
  pass, and cannot here -- see the paragraph below).

  `materialize` composes (c) then (b) ONCE (not iterated to a fixpoint) --
  if expanding inverse/symmetric facts would in turn enable a new
  transitive-closure step across a DIFFERENT transitive property that
  itself needs another round of (c), this namespace does not chase that;
  it is a single deterministic pass, not general model-building."
  (:require [owl.model :as m]))

;; --- generic BFS reachability over an adjacency map {node -> #{node}} ---

(defn- bfs-closure [graph start]
  (loop [frontier (get graph start #{}) seen #{}]
    (if (empty? frontier)
      seen
      (let [new-seen (into seen frontier)
            next-frontier (->> frontier (mapcat #(get graph % #{})) (remove new-seen) set)]
        (recur next-frontier new-seen)))))

(defn- reverse-graph [g]
  (reduce (fn [rg [node adjacent]]
            (reduce (fn [rg2 a] (update rg2 a (fnil conj #{}) node)) rg adjacent))
          {} g))

;; --- (a) SubClassOf closure (sec 9.1.1) ---

(defn subclass-edges
  "{sub-class-iri -> #{direct-super-class-iri}} from atomic Class->atomic
  Class SubClassOf axioms only (a SubClassOf between complex class
  expressions contributes nothing here -- there is no general subsumption
  check without a reasoner)."
  [o]
  (reduce (fn [g ax]
            (let [sub (:owl/sub ax) super (:owl/super ax)]
              (if (and (m/class? sub) (m/class? super))
                (update g (:owl/iri sub) (fnil conj #{}) (:owl/iri super))
                g)))
          {}
          (m/axioms-of-type o :sub-class-of)))

(defn superclasses-of
  "All class IRIs reachable from `class-iri` via one or more SubClassOf
  edges (direct or transitive)."
  [o class-iri]
  (bfs-closure (subclass-edges o) class-iri))

(defn subclasses-of
  "All class IRIs that reach `class-iri` via one or more SubClassOf edges
  (the inverse direction of superclasses-of)."
  [o class-iri]
  (bfs-closure (reverse-graph (subclass-edges o)) class-iri))

(defn subclass-of?
  "Is `sub-iri` a subclass of `super-iri`, directly or transitively, via
  atomic SubClassOf edges?"
  [o sub-iri super-iri]
  (contains? (superclasses-of o sub-iri) super-iri))

;; --- object property facts + characteristic axioms ---

(defn object-property-facts
  "[property-iri subject-iri object-iri] triples from asserted
  ObjectPropertyAssertion axioms (sec 9.6.4). Only plain ObjectProperty /
  NamedIndividual entities are considered -- an assertion using an
  ObjectInverseOf property expression (sec 6.1.1) as :owl/property is not
  included in this fact set (out of scope for v1 fact extraction)."
  [o]
  (into []
        (keep (fn [ax]
                (let [p (:owl/property ax) s (:owl/subject ax) obj (:owl/object ax)]
                  (when (and (m/object-property? p) (m/named-individual? s) (m/named-individual? obj))
                    [(:owl/iri p) (:owl/iri s) (:owl/iri obj)]))))
        (m/axioms-of-type o :object-property-assertion)))

(defn transitive-property-iris [o]
  (into #{} (map (comp :owl/iri :owl/property)) (m/axioms-of-type o :transitive-object-property)))

(defn symmetric-property-iris [o]
  (into #{} (map (comp :owl/iri :owl/property)) (m/axioms-of-type o :symmetric-object-property)))

(defn inverse-property-pairs
  "Set of #{iri1 iri2} unordered pairs from InverseObjectProperties axioms
  (sec 9.2.4)."
  [o]
  (into #{}
        (map (fn [ax] #{(:owl/iri (:owl/first ax)) (:owl/iri (:owl/second ax))}))
        (m/axioms-of-type o :inverse-object-properties)))

;; --- (c) symmetric / inverse fact materialization ---

(defn materialize-symmetric-inverse
  "Given `facts` ([property subject object] triples), add:
  - the mirrored [property object subject] fact for every fact whose
    property is a SymmetricObjectProperty (sec 9.2.11);
  - the cross-property [other-property object subject] fact for every fact
    whose property has an asserted inverse (sec 9.2.4, either direction of
    the pair)."
  [o facts]
  (let [sym (symmetric-property-iris o)
        inv-pairs (inverse-property-pairs o)
        inverses-of (fn [p] (keep #(when (contains? % p) (first (disj % p))) inv-pairs))]
    (into (set facts)
          (mapcat (fn [[p s obj]]
                    (cond-> []
                      (contains? sym p) (conj [p obj s])
                      true (into (map (fn [q] [q obj s]) (inverses-of p))))))
          facts)))

;; --- (b) transitive closure ---

(defn transitive-closure-facts
  "Extend `facts` with the transitive closure (sec 9.2.13) of every
  TransitiveObjectProperty: an independent per-property reachability
  computation, NOT combined with SubObjectPropertyOf property chains
  (sec 9.2.1)."
  [o facts]
  (let [trans (transitive-property-iris o)]
    (into (set facts)
          (mapcat
           (fn [p]
             (let [edges (reduce (fn [g [pp s obj]] (if (= pp p) (update g s (fnil conj #{}) obj) g))
                                  {} facts)]
               (mapcat (fn [s] (map (fn [obj] [p s obj]) (bfs-closure edges s))) (keys edges)))))
          trans)))

(defn materialize
  "Asserted ObjectPropertyAssertion facts (sec 9.6.4), expanded by (c)
  symmetric/inverse materialization then (b) transitive closure, in that
  fixed order (a single deterministic pass -- see ns docstring for what
  this deliberately does not chase to a fixpoint). Returns a set of
  [property-iri subject-iri object-iri] triples."
  [o]
  (->> (object-property-facts o)
       (materialize-symmetric-inverse o)
       (transitive-closure-facts o)))

(defn entails-object-property-fact?
  "Is [p-iri s-iri o-iri] in the materialized fact closure of `o`?"
  [o p-iri s-iri o-iri]
  (contains? (materialize o) [p-iri s-iri o-iri]))

;; --- combined: individual types via ClassAssertion + SubClassOf closure ---

(defn asserted-types
  "Atomic Class IRIs individual `ind-iri` is directly asserted a member of
  via ClassAssertion (sec 9.6.3). A ClassAssertion whose :owl/class is a
  complex class expression is not an atomic type and is not included."
  [o ind-iri]
  (into #{}
        (keep (fn [ax]
                (when (and (= ind-iri (:owl/iri (:owl/individual ax))) (m/class? (:owl/class ax)))
                  (:owl/iri (:owl/class ax)))))
        (m/axioms-of-type o :class-assertion)))

(defn inferred-types
  "`asserted-types` plus their transitive superclasses via (a) the
  SubClassOf closure -- e.g. if A subClassOf B subClassOf C and individual x
  is asserted a ClassAssertion of A, then C is in (inferred-types o x) even
  though there is no direct ClassAssertion of x as C. This is NOT general
  class-expression subsumption -- only atomic Class->Class SubClassOf edges
  are followed (see ns docstring / README)."
  [o ind-iri]
  (let [asserted (asserted-types o ind-iri)]
    (into asserted (mapcat #(superclasses-of o %)) asserted)))
