(ns owl.model
  "OWL 2 Web Ontology Language (W3C Recommendation, 2nd ed. 2012-12-11,
  Structural Specification and Functional-Style Syntax --
  https://www.w3.org/TR/owl2-syntax/) as EDN. Zero third-party deps --
  portable .cljc (JVM, ClojureScript, SCI). Models the STRUCTURAL object
  model of that spec (sec 3-10): entities, class/property expressions,
  axioms, and the ontology that collects them -- NOT the RDF-mapping (a
  separate, much messier W3C document; see kotoba-lang/org-w3-rdf for the
  plain-triple view) and NOT the concrete `SubClassOf(:Cat :Animal)` textual
  grammar (see owl.functional for a parsed-tree stand-in, and the README
  Follow-ups for why we don't tokenize the real text).

  IRIs are always plain strings -- no prefix/CURIE shorthand (`:Cat` for
  `http://example.org/onto#Cat`) is resolved here; that belongs to the
  textual-syntax `Prefix(...)` directive (sec 3.2 Ontology Documents),
  out of scope (see README).

  Entities (sec 5.1-5.6.1) are `{:owl/entity-type <kind> :owl/iri iri}`:
    {:owl/entity-type :class :owl/iri \"http://ex.org#Cat\"}
  kinds: :class (5.1) :datatype (5.2) :object-property (5.3)
  :data-property (5.4) :annotation-property (5.5) :named-individual (5.6.1).
  Anonymous individuals (sec 5.6.2) are out of scope (v2, see README).

  A literal (sec 5.7) is `{:owl/lexical-form s :owl/datatype iri}` or, for a
  language-tagged literal, `{:owl/lexical-form s :owl/lang tag}`. The
  lexical form is ALWAYS a plain string, never parsed into a Clojure number
  -- this namespace never does a string->number round trip, sidestepping
  the classic `(= 50 50.0)` -> false gotcha entirely.

  A property expression is either an entity map (an ObjectProperty or
  DataProperty) or, for object properties only,
  `{:owl/property-expr :object-inverse-of :owl/inverse ope}` (sec 6.1.1
  ObjectInverseOf). Data property expressions (sec 6.2) are always just
  DataProperty entities -- OWL 2 has no DataInverseOf.

  A data range (sec 7) here is, for v1, always just a Datatype entity --
  DataIntersectionOf/DataUnionOf/DataComplementOf/DataOneOf (7.1-7.4) and
  DatatypeRestriction facets (7.5) are out of scope (see README).

  A class expression (sec 8) is either an atomic Class entity map or a
  complex `{:owl/class-expr <kind> ...}` map: :object-intersection-of
  (8.1.1) :object-union-of (8.1.2) :object-complement-of (8.1.3)
  :object-one-of (8.1.4) :object-some-values-from (8.2.1)
  :object-all-values-from (8.2.2) :object-has-value (8.2.3)
  :object-has-self (8.2.4) :object-min/max/exact-cardinality
  (8.3.1-8.3.3), and the Data analogs :data-some/all-values-from
  (8.4.1-8.4.2) :data-has-value (8.4.3) :data-min/max/exact-cardinality
  (8.5.1-8.5.3).

  An axiom (sec 9) is `{:owl/axiom <kind> ...}` -- see the per-kind builder
  below for exact shape and section citation. `:owl/annotations` (a vector
  of annotation maps, sec 10.1) may be assoc'd onto any axiom map via
  `annotate`; nothing here requires it.

  An ontology (sec 3.1 Ontology IRI/Version IRI, sec 3.4 Imports) is:
    {:owl/iri iri-or-nil :owl/version-iri iri-or-nil
     :owl/imports [iri ...] :owl/axioms [axiom ...]}"
  (:refer-clojure :exclude [class class?]))

;; --- entities (sec 5.1-5.6.1) ---

(defn- entity [entity-type iri] {:owl/entity-type entity-type :owl/iri iri})

(defn class [iri] (entity :class iri))                              ;; sec 5.1
(defn datatype [iri] (entity :datatype iri))                        ;; sec 5.2
(defn object-property [iri] (entity :object-property iri))          ;; sec 5.3
(defn data-property [iri] (entity :data-property iri))              ;; sec 5.4
(defn annotation-property [iri] (entity :annotation-property iri))  ;; sec 5.5
(defn named-individual [iri] (entity :named-individual iri))        ;; sec 5.6.1

(defn entity? [x] (and (map? x) (contains? x :owl/entity-type)))
(defn entity-type [x] (:owl/entity-type x))
(defn iri [x] (:owl/iri x))

(defn- entity-of? [entity-type* x] (and (entity? x) (= entity-type* (:owl/entity-type x))))
(defn class? [x] (entity-of? :class x))
(defn datatype? [x] (entity-of? :datatype x))
(defn object-property? [x] (entity-of? :object-property x))
(defn data-property? [x] (entity-of? :data-property x))
(defn annotation-property? [x] (entity-of? :annotation-property x))
(defn named-individual? [x] (entity-of? :named-individual x))

;; --- literals (sec 5.7) ---

(def xsd-string "http://www.w3.org/2001/XMLSchema#string")

(defn literal
  "A typed literal (sec 5.7 `lexicalForm '^^' Datatype`). `lexical-form` is
  always a string -- never parsed to a number here (see ns docstring)."
  ([lexical-form] (literal lexical-form xsd-string))
  ([lexical-form datatype-iri] {:owl/lexical-form lexical-form :owl/datatype datatype-iri}))

(defn lang-literal
  "A language-tagged literal (sec 5.7 alternative grammar `lexicalForm@langTag`)."
  [lexical-form lang]
  {:owl/lexical-form lexical-form :owl/lang lang})

(defn literal? [x] (and (map? x) (contains? x :owl/lexical-form)))
(defn lang-literal? [x] (and (literal? x) (contains? x :owl/lang)))

;; --- property expressions (sec 6.1-6.2) ---

(defn object-inverse-of
  "ObjectInverseOf(OPE) (sec 6.1.1) -- the inverse of an object property
  expression. Data property expressions (sec 6.2) have no inverse form."
  [ope]
  {:owl/property-expr :object-inverse-of :owl/inverse ope})

(defn object-property-expr? [x]
  (or (object-property? x) (and (map? x) (= :object-inverse-of (:owl/property-expr x)))))

(defn data-property-expr? [x] (data-property? x))

;; --- class expressions (sec 8) ---

(defn- class-expr [kind m] (assoc m :owl/class-expr kind))

(defn object-intersection-of [ces] (class-expr :object-intersection-of {:owl/operands (vec ces)}))  ;; 8.1.1
(defn object-union-of        [ces] (class-expr :object-union-of        {:owl/operands (vec ces)}))  ;; 8.1.2
(defn object-complement-of   [ce]  (class-expr :object-complement-of   {:owl/operand ce}))           ;; 8.1.3
(defn object-one-of         [inds] (class-expr :object-one-of          {:owl/individuals (vec inds)})) ;; 8.1.4

(defn object-some-values-from [ope ce] (class-expr :object-some-values-from {:owl/property ope :owl/filler ce})) ;; 8.2.1
(defn object-all-values-from  [ope ce] (class-expr :object-all-values-from  {:owl/property ope :owl/filler ce})) ;; 8.2.2
(defn object-has-value  [ope ind] (class-expr :object-has-value {:owl/property ope :owl/individual ind}))        ;; 8.2.3
(defn object-has-self   [ope]     (class-expr :object-has-self  {:owl/property ope}))                            ;; 8.2.4

(defn object-min-cardinality  ;; 8.3.1 (unqualified: 2-arity; qualified: 3-arity with filler CE)
  ([n ope] (object-min-cardinality n ope nil))
  ([n ope ce] (class-expr :object-min-cardinality (cond-> {:owl/cardinality n :owl/property ope} ce (assoc :owl/filler ce)))))
(defn object-max-cardinality  ;; 8.3.2
  ([n ope] (object-max-cardinality n ope nil))
  ([n ope ce] (class-expr :object-max-cardinality (cond-> {:owl/cardinality n :owl/property ope} ce (assoc :owl/filler ce)))))
(defn object-exact-cardinality  ;; 8.3.3
  ([n ope] (object-exact-cardinality n ope nil))
  ([n ope ce] (class-expr :object-exact-cardinality (cond-> {:owl/cardinality n :owl/property ope} ce (assoc :owl/filler ce)))))

(defn data-some-values-from [dpe dr] (class-expr :data-some-values-from {:owl/property dpe :owl/filler dr})) ;; 8.4.1
(defn data-all-values-from  [dpe dr] (class-expr :data-all-values-from  {:owl/property dpe :owl/filler dr})) ;; 8.4.2
(defn data-has-value [dpe lit] (class-expr :data-has-value {:owl/property dpe :owl/value lit}))              ;; 8.4.3

(defn data-min-cardinality  ;; 8.5.1
  ([n dpe] (data-min-cardinality n dpe nil))
  ([n dpe dr] (class-expr :data-min-cardinality (cond-> {:owl/cardinality n :owl/property dpe} dr (assoc :owl/filler dr)))))
(defn data-max-cardinality  ;; 8.5.2
  ([n dpe] (data-max-cardinality n dpe nil))
  ([n dpe dr] (class-expr :data-max-cardinality (cond-> {:owl/cardinality n :owl/property dpe} dr (assoc :owl/filler dr)))))
(defn data-exact-cardinality  ;; 8.5.3
  ([n dpe] (data-exact-cardinality n dpe nil))
  ([n dpe dr] (class-expr :data-exact-cardinality (cond-> {:owl/cardinality n :owl/property dpe} dr (assoc :owl/filler dr)))))

(defn class-expr? [x] (or (class? x) (and (map? x) (contains? x :owl/class-expr))))
(defn class-expr-kind [x] (if (class? x) :class (:owl/class-expr x)))

(def cardinality-kinds
  #{:object-min-cardinality :object-max-cardinality :object-exact-cardinality
    :data-min-cardinality :data-max-cardinality :data-exact-cardinality})

;; --- class axioms (sec 9.1) ---

(defn- axiom [kind m] (assoc m :owl/axiom kind))

(defn sub-class-of [sub super] (axiom :sub-class-of {:owl/sub sub :owl/super super}))          ;; 9.1.1
(defn equivalent-classes [ces] (axiom :equivalent-classes {:owl/classes (vec ces)}))            ;; 9.1.2
(defn disjoint-classes   [ces] (axiom :disjoint-classes   {:owl/classes (vec ces)}))            ;; 9.1.3
(defn disjoint-union [cls ces] (axiom :disjoint-union {:owl/class cls :owl/disjoint-classes (vec ces)})) ;; 9.1.4

;; --- object property axioms (sec 9.2) ---

(defn sub-object-property-of
  "SubObjectPropertyOf(sub super) (sec 9.2.1). `sub` is normally an object
  property expression, but may also be a vector `[ope1 ope2 ...]`
  representing a property chain -- ObjectPropertyChain(ope1 ope2 ...) --
  in which case this asserts the chain implies `super`."
  [sub super]
  (axiom :sub-object-property-of {:owl/sub sub :owl/super super}))

(defn equivalent-object-properties [opes] (axiom :equivalent-object-properties {:owl/properties (vec opes)})) ;; 9.2.2
(defn disjoint-object-properties   [opes] (axiom :disjoint-object-properties   {:owl/properties (vec opes)})) ;; 9.2.3
(defn inverse-object-properties [ope1 ope2] (axiom :inverse-object-properties {:owl/first ope1 :owl/second ope2})) ;; 9.2.4
(defn object-property-domain [ope ce] (axiom :object-property-domain {:owl/property ope :owl/domain ce})) ;; 9.2.5
(defn object-property-range  [ope ce] (axiom :object-property-range  {:owl/property ope :owl/range ce}))  ;; 9.2.6
(defn functional-object-property [ope] (axiom :functional-object-property {:owl/property ope}))            ;; 9.2.7
(defn inverse-functional-object-property [ope] (axiom :inverse-functional-object-property {:owl/property ope})) ;; 9.2.8
(defn reflexive-object-property   [ope] (axiom :reflexive-object-property   {:owl/property ope}))          ;; 9.2.9
(defn irreflexive-object-property [ope] (axiom :irreflexive-object-property {:owl/property ope}))          ;; 9.2.10
(defn symmetric-object-property   [ope] (axiom :symmetric-object-property   {:owl/property ope}))          ;; 9.2.11
(defn asymmetric-object-property  [ope] (axiom :asymmetric-object-property  {:owl/property ope}))          ;; 9.2.12
(defn transitive-object-property  [ope] (axiom :transitive-object-property  {:owl/property ope}))          ;; 9.2.13

;; --- data property axioms (sec 9.3) ---

(defn sub-data-property-of [sub super] (axiom :sub-data-property-of {:owl/sub sub :owl/super super})) ;; 9.3.1
(defn equivalent-data-properties [dpes] (axiom :equivalent-data-properties {:owl/properties (vec dpes)})) ;; 9.3.2
(defn disjoint-data-properties   [dpes] (axiom :disjoint-data-properties   {:owl/properties (vec dpes)})) ;; 9.3.3
(defn data-property-domain [dpe ce] (axiom :data-property-domain {:owl/property dpe :owl/domain ce})) ;; 9.3.4
(defn data-property-range  [dpe dr] (axiom :data-property-range  {:owl/property dpe :owl/range dr}))  ;; 9.3.5
(defn functional-data-property [dpe] (axiom :functional-data-property {:owl/property dpe}))            ;; 9.3.6

;; --- assertions (sec 9.6) ---

(defn same-individual        [inds] (axiom :same-individual        {:owl/individuals (vec inds)})) ;; 9.6.1
(defn different-individuals  [inds] (axiom :different-individuals  {:owl/individuals (vec inds)})) ;; 9.6.2
(defn class-assertion [ce ind] (axiom :class-assertion {:owl/class ce :owl/individual ind}))        ;; 9.6.3
(defn object-property-assertion [ope subj obj]
  (axiom :object-property-assertion {:owl/property ope :owl/subject subj :owl/object obj}))         ;; 9.6.4
(defn negative-object-property-assertion [ope subj obj]
  (axiom :negative-object-property-assertion {:owl/property ope :owl/subject subj :owl/object obj})) ;; 9.6.5
(defn data-property-assertion [dpe subj lit]
  (axiom :data-property-assertion {:owl/property dpe :owl/subject subj :owl/value lit}))            ;; 9.6.6
(defn negative-data-property-assertion [dpe subj lit]
  (axiom :negative-data-property-assertion {:owl/property dpe :owl/subject subj :owl/value lit}))   ;; 9.6.7

;; --- entity declarations (sec 5.8) ---

(defn declaration [entity-map] (axiom :declaration {:owl/entity entity-map})) ;; sec 5.8

;; --- annotations (sec 10) -- modeled structurally, kept simple ---

(defn annotation [ann-property-iri value] {:owl/property ann-property-iri :owl/value value}) ;; sec 10.1
(defn annotate [ax anns] (assoc ax :owl/annotations (vec anns)))                              ;; sec 10.1
(defn annotation-assertion [ann-property-iri subject value]
  (axiom :annotation-assertion {:owl/property ann-property-iri :owl/subject subject :owl/value value})) ;; sec 10.2.1

;; --- ontology (sec 3.1 Ontology IRI/Version IRI, sec 3.4 Imports) ---

(defn ontology
  ([] (ontology nil nil))
  ([iri-str] (ontology iri-str nil))
  ([iri-str opts] (merge {:owl/iri iri-str :owl/version-iri nil :owl/imports [] :owl/axioms []} opts)))

(defn add-axiom  [o ax]  (update o :owl/axioms conj ax))
(defn add-axioms [o axs] (update o :owl/axioms into axs))
(defn add-import [o import-iri] (update o :owl/imports conj import-iri))
(defn set-version-iri [o v] (assoc o :owl/version-iri v))

;; --- structural queries ---

(defn axioms [o] (:owl/axioms o []))
(defn axioms-of-type [o kind] (filterv #(= kind (:owl/axiom %)) (axioms o)))
(defn subclass-axioms [o] (axioms-of-type o :sub-class-of))

(defn declarations [o] (axioms-of-type o :declaration))
(defn entities
  "The set of entity maps introduced by a Declaration axiom (sec 5.8) in `o`."
  [o]
  (into #{} (map :owl/entity) (declarations o)))
(defn declared-entities
  "Set of [entity-type iri] pairs introduced by a Declaration axiom -- the
  matching key used to detect dangling references (see owl.validate),
  precise enough to respect punning (sec 5.9: the same IRI legitimately
  declared as two different entity types)."
  [o]
  (into #{} (map (fn [e] [(:owl/entity-type e) (:owl/iri e)])) (entities o)))

(defn referenced-entities
  "Walk `x` (an axiom, class/property expression, ontology, or arbitrary
  nested collection of these) and collect every entity map (sec 5) it
  references, however deeply nested."
  [x]
  (cond
    (entity? x) #{x}
    (map? x) (reduce into #{} (map referenced-entities (vals x)))
    (sequential? x) (reduce into #{} (map referenced-entities x))
    :else #{}))

(defn class-expr-nodes
  "All :owl/class-expr complex class-expression nodes reachable from `x`
  (typically an axiom), including nested ones inside :owl/operands /
  :owl/operand / :owl/filler. Useful for structural checks that need to
  look inside a class expression tree (e.g. cardinality shape, see
  owl.validate)."
  [x]
  (cond
    (and (map? x) (contains? x :owl/class-expr))
    (into [x] (mapcat class-expr-nodes (vals (dissoc x :owl/class-expr))))
    (map? x) (mapcat class-expr-nodes (vals x))
    (sequential? x) (mapcat class-expr-nodes x)
    :else nil))
