(ns owl.functional
  "Bidirectional conversion between owl.model EDN and a parsed EDN tree that
  mirrors the OWL 2 Functional-Style Syntax (W3C, sec 3-10 of
  https://www.w3.org/TR/owl2-syntax/) 1:1, e.g. the axiom written in the
  spec's concrete text as

    SubClassOf( :Cat :Animal )

  is represented here as the tree

    [:SubClassOf [:Class \"http://ex.org#Cat\"] [:Class \"http://ex.org#Animal\"]]

  i.e. a vector whose first element is the spec's functional-syntax
  operator name (as a keyword, exactly as capitalized in the spec: `:Class`,
  `:SubClassOf`, `:ObjectSomeValuesFrom`, ...) and whose remaining elements
  are either IRI strings, literal trees, or nested operator trees.

  THIS NAMESPACE DOES NOT PARSE THE CONCRETE TEXTUAL SYNTAX -- no tokenizer,
  no handling of `Prefix(...)` declarations or prefixed names (`:Cat`
  sugar for a full IRI). It only defines the tree SHAPE that mirrors that
  grammar structurally, and converts between that tree and owl.model's EDN
  maps (parse-* tree->model, emit-* model->tree). A real text parser is a
  real, separate piece of scope -- see README Follow-ups (mirrors how
  xmile.xml in org-oasis-open-xmile converts between an already-parsed XML
  element tree and its domain EDN without owning XML text parsing).

  Round-trip guarantee (well-formed input):
    (= ax     (parse-axiom (emit-axiom ax)))
    (= o      (parse-ontology (emit-ontology o)))"
  (:require [owl.model :as m]))

;; --- entities (sec 5.1-5.6.1) ---

(def ^:private entity-type->tag
  {:class :Class :datatype :Datatype :object-property :ObjectProperty
   :data-property :DataProperty :annotation-property :AnnotationProperty
   :named-individual :NamedIndividual})

(def ^:private tag->entity-builder
  {:Class m/class :Datatype m/datatype :ObjectProperty m/object-property
   :DataProperty m/data-property :AnnotationProperty m/annotation-property
   :NamedIndividual m/named-individual})

(defn emit-entity [e] [(entity-type->tag (m/entity-type e)) (m/iri e)])
(defn parse-entity [[tag iri-str]] ((tag->entity-builder tag) iri-str))

;; --- literals (sec 5.7) ---

(defn emit-literal
  "[:Literal lexical-form datatype-iri] for a typed literal, or
  [:Literal lexical-form [:lang tag]] for a language-tagged one."
  [lit]
  (if (m/lang-literal? lit)
    [:Literal (:owl/lexical-form lit) [:lang (:owl/lang lit)]]
    [:Literal (:owl/lexical-form lit) (:owl/datatype lit m/xsd-string)]))

(defn parse-literal [[_ lex tag-or-iri]]
  (if (and (vector? tag-or-iri) (= :lang (first tag-or-iri)))
    (m/lang-literal lex (second tag-or-iri))
    (m/literal lex tag-or-iri)))

;; --- property expressions (sec 6.1-6.2) ---

(defn emit-property-expr [pe]
  (if (= :object-inverse-of (:owl/property-expr pe))
    [:ObjectInverseOf (emit-property-expr (:owl/inverse pe))]
    (emit-entity pe)))

(defn parse-property-expr [tree]
  (if (= :ObjectInverseOf (first tree))
    (m/object-inverse-of (parse-property-expr (second tree)))
    (parse-entity tree)))

;; --- data ranges (sec 7) -- v1: named Datatype only, see owl.model ---

(defn emit-data-range [dr] (emit-entity dr))
(defn parse-data-range [tree] (parse-entity tree))

;; --- class expressions (sec 8) ---

(def ^:private class-expr-kind->tag
  {:object-intersection-of :ObjectIntersectionOf
   :object-union-of :ObjectUnionOf
   :object-complement-of :ObjectComplementOf
   :object-one-of :ObjectOneOf
   :object-some-values-from :ObjectSomeValuesFrom
   :object-all-values-from :ObjectAllValuesFrom
   :object-has-value :ObjectHasValue
   :object-has-self :ObjectHasSelf
   :object-min-cardinality :ObjectMinCardinality
   :object-max-cardinality :ObjectMaxCardinality
   :object-exact-cardinality :ObjectExactCardinality
   :data-some-values-from :DataSomeValuesFrom
   :data-all-values-from :DataAllValuesFrom
   :data-has-value :DataHasValue
   :data-min-cardinality :DataMinCardinality
   :data-max-cardinality :DataMaxCardinality
   :data-exact-cardinality :DataExactCardinality})

(def ^:private tag->class-expr-kind
  (into {} (map (fn [[k v]] [v k])) class-expr-kind->tag))

(defn emit-class-expr [ce]
  (if (m/class? ce)
    (emit-entity ce)
    (let [kind (:owl/class-expr ce)
          tag (class-expr-kind->tag kind)]
      (case kind
        (:object-intersection-of :object-union-of)
        (into [tag] (map emit-class-expr) (:owl/operands ce))

        :object-complement-of
        [tag (emit-class-expr (:owl/operand ce))]

        :object-one-of
        (into [tag] (map emit-entity) (:owl/individuals ce))

        (:object-some-values-from :object-all-values-from)
        [tag (emit-property-expr (:owl/property ce)) (emit-class-expr (:owl/filler ce))]

        :object-has-value
        [tag (emit-property-expr (:owl/property ce)) (emit-entity (:owl/individual ce))]

        :object-has-self
        [tag (emit-property-expr (:owl/property ce))]

        (:object-min-cardinality :object-max-cardinality :object-exact-cardinality)
        (cond-> [tag (:owl/cardinality ce) (emit-property-expr (:owl/property ce))]
          (:owl/filler ce) (conj (emit-class-expr (:owl/filler ce))))

        (:data-some-values-from :data-all-values-from)
        [tag (emit-property-expr (:owl/property ce)) (emit-data-range (:owl/filler ce))]

        :data-has-value
        [tag (emit-property-expr (:owl/property ce)) (emit-literal (:owl/value ce))]

        (:data-min-cardinality :data-max-cardinality :data-exact-cardinality)
        (cond-> [tag (:owl/cardinality ce) (emit-property-expr (:owl/property ce))]
          (:owl/filler ce) (conj (emit-data-range (:owl/filler ce))))))))

(declare parse-class-expr)

(defn- parse-object-cardinality [builder tree]
  (let [n (nth tree 1) ope (parse-property-expr (nth tree 2))]
    (if (= 4 (count tree))
      (builder n ope (parse-class-expr (nth tree 3)))
      (builder n ope))))

(defn- parse-data-cardinality [builder tree]
  (let [n (nth tree 1) dpe (parse-property-expr (nth tree 2))]
    (if (= 4 (count tree))
      (builder n dpe (parse-data-range (nth tree 3)))
      (builder n dpe))))

(defn parse-class-expr [tree]
  (let [tag (first tree)]
    (if (= :Class tag)
      (parse-entity tree)
      (case (tag->class-expr-kind tag)
        :object-intersection-of (m/object-intersection-of (mapv parse-class-expr (rest tree)))
        :object-union-of (m/object-union-of (mapv parse-class-expr (rest tree)))
        :object-complement-of (m/object-complement-of (parse-class-expr (nth tree 1)))
        :object-one-of (m/object-one-of (mapv parse-entity (rest tree)))
        :object-some-values-from (m/object-some-values-from (parse-property-expr (nth tree 1)) (parse-class-expr (nth tree 2)))
        :object-all-values-from (m/object-all-values-from (parse-property-expr (nth tree 1)) (parse-class-expr (nth tree 2)))
        :object-has-value (m/object-has-value (parse-property-expr (nth tree 1)) (parse-entity (nth tree 2)))
        :object-has-self (m/object-has-self (parse-property-expr (nth tree 1)))
        :object-min-cardinality (parse-object-cardinality m/object-min-cardinality tree)
        :object-max-cardinality (parse-object-cardinality m/object-max-cardinality tree)
        :object-exact-cardinality (parse-object-cardinality m/object-exact-cardinality tree)
        :data-some-values-from (m/data-some-values-from (parse-property-expr (nth tree 1)) (parse-data-range (nth tree 2)))
        :data-all-values-from (m/data-all-values-from (parse-property-expr (nth tree 1)) (parse-data-range (nth tree 2)))
        :data-has-value (m/data-has-value (parse-property-expr (nth tree 1)) (parse-literal (nth tree 2)))
        :data-min-cardinality (parse-data-cardinality m/data-min-cardinality tree)
        :data-max-cardinality (parse-data-cardinality m/data-max-cardinality tree)
        :data-exact-cardinality (parse-data-cardinality m/data-exact-cardinality tree)))))

;; --- axioms (sec 9, sec 5.8 Declaration, sec 10.2.1 AnnotationAssertion) ---

(defn- emit-n-ary [tag emit-item items] (into [tag] (map emit-item) items))

(defn- emit-sub-object-property-of [ax]
  (let [sub (:owl/sub ax)]
    [:SubObjectPropertyOf
     (if (sequential? sub)
       (into [:ObjectPropertyChain] (map emit-property-expr) sub)
       (emit-property-expr sub))
     (emit-property-expr (:owl/super ax))]))

(defn- parse-sub-object-property-of [tree]
  (let [sub-tree (nth tree 1) super-tree (nth tree 2)]
    (m/sub-object-property-of
     (if (= :ObjectPropertyChain (first sub-tree))
       (mapv parse-property-expr (rest sub-tree))
       (parse-property-expr sub-tree))
     (parse-property-expr super-tree))))

(defn emit-axiom [ax]
  (case (:owl/axiom ax)
    :sub-class-of [:SubClassOf (emit-class-expr (:owl/sub ax)) (emit-class-expr (:owl/super ax))]
    :equivalent-classes (emit-n-ary :EquivalentClasses emit-class-expr (:owl/classes ax))
    :disjoint-classes (emit-n-ary :DisjointClasses emit-class-expr (:owl/classes ax))
    :disjoint-union (into [:DisjointUnion (emit-class-expr (:owl/class ax))]
                           (map emit-class-expr) (:owl/disjoint-classes ax))

    :sub-object-property-of (emit-sub-object-property-of ax)
    :equivalent-object-properties (emit-n-ary :EquivalentObjectProperties emit-property-expr (:owl/properties ax))
    :disjoint-object-properties (emit-n-ary :DisjointObjectProperties emit-property-expr (:owl/properties ax))
    :inverse-object-properties [:InverseObjectProperties (emit-property-expr (:owl/first ax)) (emit-property-expr (:owl/second ax))]
    :object-property-domain [:ObjectPropertyDomain (emit-property-expr (:owl/property ax)) (emit-class-expr (:owl/domain ax))]
    :object-property-range [:ObjectPropertyRange (emit-property-expr (:owl/property ax)) (emit-class-expr (:owl/range ax))]
    :functional-object-property [:FunctionalObjectProperty (emit-property-expr (:owl/property ax))]
    :inverse-functional-object-property [:InverseFunctionalObjectProperty (emit-property-expr (:owl/property ax))]
    :reflexive-object-property [:ReflexiveObjectProperty (emit-property-expr (:owl/property ax))]
    :irreflexive-object-property [:IrreflexiveObjectProperty (emit-property-expr (:owl/property ax))]
    :symmetric-object-property [:SymmetricObjectProperty (emit-property-expr (:owl/property ax))]
    :asymmetric-object-property [:AsymmetricObjectProperty (emit-property-expr (:owl/property ax))]
    :transitive-object-property [:TransitiveObjectProperty (emit-property-expr (:owl/property ax))]

    :sub-data-property-of [:SubDataPropertyOf (emit-property-expr (:owl/sub ax)) (emit-property-expr (:owl/super ax))]
    :equivalent-data-properties (emit-n-ary :EquivalentDataProperties emit-property-expr (:owl/properties ax))
    :disjoint-data-properties (emit-n-ary :DisjointDataProperties emit-property-expr (:owl/properties ax))
    :data-property-domain [:DataPropertyDomain (emit-property-expr (:owl/property ax)) (emit-class-expr (:owl/domain ax))]
    :data-property-range [:DataPropertyRange (emit-property-expr (:owl/property ax)) (emit-data-range (:owl/range ax))]
    :functional-data-property [:FunctionalDataProperty (emit-property-expr (:owl/property ax))]

    :class-assertion [:ClassAssertion (emit-class-expr (:owl/class ax)) (emit-entity (:owl/individual ax))]
    :object-property-assertion [:ObjectPropertyAssertion (emit-property-expr (:owl/property ax))
                                 (emit-entity (:owl/subject ax)) (emit-entity (:owl/object ax))]
    :negative-object-property-assertion [:NegativeObjectPropertyAssertion (emit-property-expr (:owl/property ax))
                                          (emit-entity (:owl/subject ax)) (emit-entity (:owl/object ax))]
    :data-property-assertion [:DataPropertyAssertion (emit-property-expr (:owl/property ax))
                               (emit-entity (:owl/subject ax)) (emit-literal (:owl/value ax))]
    :negative-data-property-assertion [:NegativeDataPropertyAssertion (emit-property-expr (:owl/property ax))
                                        (emit-entity (:owl/subject ax)) (emit-literal (:owl/value ax))]
    :same-individual (emit-n-ary :SameIndividual emit-entity (:owl/individuals ax))
    :different-individuals (emit-n-ary :DifferentIndividuals emit-entity (:owl/individuals ax))

    :declaration [:Declaration (emit-entity (:owl/entity ax))]
    :annotation-assertion [:AnnotationAssertion (:owl/property ax) (:owl/subject ax)
                            (if (m/literal? (:owl/value ax)) (emit-literal (:owl/value ax)) (:owl/value ax))]))

(defn parse-axiom [tree]
  (case (first tree)
    :SubClassOf (m/sub-class-of (parse-class-expr (nth tree 1)) (parse-class-expr (nth tree 2)))
    :EquivalentClasses (m/equivalent-classes (mapv parse-class-expr (rest tree)))
    :DisjointClasses (m/disjoint-classes (mapv parse-class-expr (rest tree)))
    :DisjointUnion (m/disjoint-union (parse-class-expr (nth tree 1)) (mapv parse-class-expr (nthrest tree 2)))

    :SubObjectPropertyOf (parse-sub-object-property-of tree)
    :EquivalentObjectProperties (m/equivalent-object-properties (mapv parse-property-expr (rest tree)))
    :DisjointObjectProperties (m/disjoint-object-properties (mapv parse-property-expr (rest tree)))
    :InverseObjectProperties (m/inverse-object-properties (parse-property-expr (nth tree 1)) (parse-property-expr (nth tree 2)))
    :ObjectPropertyDomain (m/object-property-domain (parse-property-expr (nth tree 1)) (parse-class-expr (nth tree 2)))
    :ObjectPropertyRange (m/object-property-range (parse-property-expr (nth tree 1)) (parse-class-expr (nth tree 2)))
    :FunctionalObjectProperty (m/functional-object-property (parse-property-expr (nth tree 1)))
    :InverseFunctionalObjectProperty (m/inverse-functional-object-property (parse-property-expr (nth tree 1)))
    :ReflexiveObjectProperty (m/reflexive-object-property (parse-property-expr (nth tree 1)))
    :IrreflexiveObjectProperty (m/irreflexive-object-property (parse-property-expr (nth tree 1)))
    :SymmetricObjectProperty (m/symmetric-object-property (parse-property-expr (nth tree 1)))
    :AsymmetricObjectProperty (m/asymmetric-object-property (parse-property-expr (nth tree 1)))
    :TransitiveObjectProperty (m/transitive-object-property (parse-property-expr (nth tree 1)))

    :SubDataPropertyOf (m/sub-data-property-of (parse-property-expr (nth tree 1)) (parse-property-expr (nth tree 2)))
    :EquivalentDataProperties (m/equivalent-data-properties (mapv parse-property-expr (rest tree)))
    :DisjointDataProperties (m/disjoint-data-properties (mapv parse-property-expr (rest tree)))
    :DataPropertyDomain (m/data-property-domain (parse-property-expr (nth tree 1)) (parse-class-expr (nth tree 2)))
    :DataPropertyRange (m/data-property-range (parse-property-expr (nth tree 1)) (parse-data-range (nth tree 2)))
    :FunctionalDataProperty (m/functional-data-property (parse-property-expr (nth tree 1)))

    :ClassAssertion (m/class-assertion (parse-class-expr (nth tree 1)) (parse-entity (nth tree 2)))
    :ObjectPropertyAssertion (m/object-property-assertion (parse-property-expr (nth tree 1)) (parse-entity (nth tree 2)) (parse-entity (nth tree 3)))
    :NegativeObjectPropertyAssertion (m/negative-object-property-assertion (parse-property-expr (nth tree 1)) (parse-entity (nth tree 2)) (parse-entity (nth tree 3)))
    :DataPropertyAssertion (m/data-property-assertion (parse-property-expr (nth tree 1)) (parse-entity (nth tree 2)) (parse-literal (nth tree 3)))
    :NegativeDataPropertyAssertion (m/negative-data-property-assertion (parse-property-expr (nth tree 1)) (parse-entity (nth tree 2)) (parse-literal (nth tree 3)))
    :SameIndividual (m/same-individual (mapv parse-entity (rest tree)))
    :DifferentIndividuals (m/different-individuals (mapv parse-entity (rest tree)))

    :Declaration (m/declaration (parse-entity (nth tree 1)))
    :AnnotationAssertion (m/annotation-assertion (nth tree 1) (nth tree 2)
                                                  (let [v (nth tree 3)]
                                                    (if (and (vector? v) (= :Literal (first v)))
                                                      (parse-literal v)
                                                      v)))))

;; --- ontology (sec 3.1, sec 3.4) ---

(defn emit-ontology [o]
  [:Ontology (:owl/iri o) (:owl/version-iri o) (vec (:owl/imports o []))
   (mapv emit-axiom (:owl/axioms o []))])

(defn parse-ontology [[_ iri-str version-iri imports axiom-trees]]
  (-> (m/ontology iri-str)
      (m/set-version-iri version-iri)
      (assoc :owl/imports (vec imports))
      (assoc :owl/axioms (mapv parse-axiom axiom-trees))))
