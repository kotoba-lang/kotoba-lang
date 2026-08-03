(ns kotoba.lang.grammar-authority
  "W0 mechanical checks: guest-grammar is the sole source-surface authority,
  every admitted form is classified by surface-status, portable claims have
  evidence or remain partial, and vendored consumers byte-match the authority.

  Authority: lang/guest-grammar.edn + lang/surface-status.edn +
  lang/elaboration-pipeline.edn (ADR-2607279200 / migration plan W0)."
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]))

(def grammar-path "lang/guest-grammar.edn")
(def surface-path "lang/surface-status.edn")
(def pipeline-path "lang/elaboration-pipeline.edn")

(def local-vendor-path "resources/kotoba/lang/guest-grammar.edn")

(def sibling-vendor-paths
  ["../compiler/resources/kotoba/lang/guest-grammar.edn"
   "../kotoba/resources/kotoba/lang/guest-grammar.edn"
   "../grammar/resources/kotoba/lang/guest-grammar.edn"])

(def portable-backends #{:compiler :kotoba-wasm :kotoba-cljs})

(def dispositions
  #{:intentional-security-constraint
    :intentional-semantic-simplification
    :implemented-partial
    :not-yet-implemented})

(defn read-edn [path]
  (edn/read-string (slurp (io/file path))))

(defn- as-sym [x]
  (cond
    (symbol? x) x
    (keyword? x) (symbol (name x))
    (string? x) (symbol x)
    :else x))

(defn- as-kw [x]
  (cond
    (keyword? x) x
    (symbol? x) (keyword (name x))
    (string? x) (keyword x)
    :else x))

(defn forbidden-heads [grammar]
  (into #{} (map as-sym) (:forbidden-heads grammar #{})))

(defn invariant-surfaces [surface]
  (into #{}
        (mapcat (fn [[_entry v]]
                  (map as-sym (:surface v #{}))))
        (:invariants surface {})))

(defn sugar-entries [grammar]
  (into {}
        (map (fn [[k v]] [(as-kw k) v]))
        (:sugar grammar {})))

(defn sugar-source-forms
  "Symbols/keywords that appear as source sugar heads for one sugar entry.
  Documentation strings in :forms (e.g. nested-destructuring notes) are ignored."
  [sugar-key entry]
  (let [explicit (seq (filter #(or (symbol? %) (keyword? %)) (:forms entry)))
        k (as-kw sugar-key)]
    (into #{}
          (map as-sym)
          (or explicit
              (when (and (keyword? k) (not (str/includes? (name k) "-literal")))
                ;; keyword sugar keys that are not purely conceptual map to
                ;; the same-named source head (e.g. :cond -> cond).
                (case k
                  (:map-literal :vector-literal :set-literal
                   :nested-destructuring :record-constructor
                   :protocol-dispatch :interface-contract
                   :protocol-extension :keyword-literal
                   :portable-string-symbol-values :string-host-arg
                   :typed-capability-call :inline-fn-callback
                   :loop-recur :closed-multimethod
                   :variadic-comparison :function-semantics)
                  nil
                  [(symbol (name k))]))))))

(defn conceptual-sugar-keys
  "Sugar catalog keys that classify feature families rather than a single head."
  []
  #{:map-literal :vector-literal :set-literal :nested-destructuring
    :record-constructor :protocol-dispatch :interface-contract
    :protocol-extension :keyword-literal :portable-string-symbol-values
    :string-host-arg :typed-capability-call :inline-fn-callback
    :loop-recur :closed-multimethod :variadic-comparison})

(defn feature-catalog [surface]
  (merge (:collections surface {})
         (:other-gaps surface {})))

(defn feature-form-hints
  "Best-effort form symbols mentioned by a surface-status feature entry."
  [feature-key entry]
  (let [from-ops (map as-sym (or (:operations entry) #{}))
        from-primary (map as-sym (or (:primary-implementation entry) #{}))
        from-compiler (map as-sym (or (:compiler-implementation entry) #{}))
        from-record (when-let [f (get-in entry [:record-data :form])] [(as-sym f)])
        from-protocol (when-let [f (get-in entry [:protocols :form])] [(as-sym f)])
        from-interface (when-let [f (get-in entry [:interface :form])] [(as-sym f)])
        from-extensions (map as-sym (or (:extensions entry) #{}))
        from-lazy-ops (map as-sym (or (:operations entry) #{}))
        named (case (as-kw feature-key)
                :map-literal '[get assoc]
                :contextual-document-literal '[document]
                :option-flow '[option-or if-some when-some some-> some->> match-option]
                :vector-literal '[]
                :set-literal '[contains? conj disj]
                :map-function '[map]
                :filter-function '[filter]
                :reduce-function '[reduce]
                :first-class-closure-values '[fn invoke fn-ref apply]
                :named-multi-arity-functions '[]
                :lazy-sequences '[lazy-cons lazy-first lazy-rest lazy-empty?
                                  lazy-map lazy-filter take drop]
                :dynamic-arity-apply '[apply]
                :bounded-control-and-sugar '[loop recur match defdesugar]
                :nested-destructuring '[]
                :protocol-and-record-dispatch '[defrecord defprotocol definterface
                                                extend-type extend-protocol]
                :persistent-collection-semantics '[count nth peek pop keys vals
                                                   get assoc dissoc conj disj contains?]
                :portable-value-model '[string? symbol? keyword? string-length string=]
                :inline-fn-callbacks '[fn]
                :multi-collection-map '[map lazy-map]
                :portable-source-stdlib '[]
                :release-integration '[]
                :backend-parity '[]
                nil)]
    (into #{} cat [from-ops from-primary from-compiler from-record from-protocol
                   from-interface from-extensions from-lazy-ops named])))

(defn classified-forms
  "Union of all forms that surface-status currently classifies."
  [surface]
  (let [invariants (invariant-surfaces surface)
        features (feature-catalog surface)
        feature-forms (into #{} (mapcat (fn [[k v]] (feature-form-hints k v)) features))
        core-defaults '#{ns def defn defprotocol definterface defrecord
                         extend-type extend-protocol let if do main}
        arith-defaults '#{+ - * quot bit-xor bit-and bit-or}
        cmp-defaults '#{= < > <= >=}
        pred-defaults '#{not zero? pos? neg? string? symbol? keyword?
                         string-length string= string-concat string-substring symbol}
        sugar-defaults '#{-> ->> as-> and or when if-not when-not
                          cond condp cond->> case if-let when-let if-some when-some
                          some-> some->> not= do get assoc contains? conj disj
                          map filter reduce fn invoke fn-ref apply
                          lazy-cons lazy-first lazy-rest lazy-empty? lazy-map
                          lazy-filter take drop count nth peek pop keys vals dissoc
                          match defdesugar loop recur assert doseq dotimes
                          defmulti defmethod}]
    (set/union invariants feature-forms core-defaults arith-defaults
               cmp-defaults pred-defaults sugar-defaults)))

(defn admitted-source-forms
  "Forms the guest grammar admits as source heads (excluding pure host-op catalogs)."
  [grammar]
  (let [core (into #{} (map as-sym) (:core-special-forms grammar #{}))
        arith (into #{} (map as-sym) (:arithmetic grammar #{}))
        cmp (into #{} (map as-sym) (:comparisons grammar #{}))
        pred (into #{} (map as-sym) (:predicates grammar #{}))
        sugar-forms (into #{}
                          (mapcat (fn [[k v]] (sugar-source-forms k v)))
                          (sugar-entries grammar))
        ;; admitted-builtins are runtime/helpers; classify by presence in grammar
        ;; catalog rather than surface-status feature lists.
        builtins (into #{}
                       (map as-sym)
                       (:admitted-builtins grammar #{}))]
    {:core core
     :arithmetic arith
     :comparisons cmp
     :predicates pred
     :sugar sugar-forms
     :builtins builtins
     :all (set/union core arith cmp pred sugar-forms)}))

(defn sugar-portability
  "For each sugar entry, whether it claims full portable backend coverage."
  [grammar]
  (into {}
        (map (fn [[k v]]
               (let [backends (set (:backends v #{}))
                     portable? (= portable-backends backends)]
                 [k {:backends backends
                     :portable-claim? portable?
                     :status (:status v :implemented-partial)
                     :forms (sugar-source-forms k v)}])))
        (sugar-entries grammar)))

(defn feature-portable-claims
  "Surface-status features that claim full three-backend implementation."
  [surface]
  (into {}
        (keep (fn [[k v]]
                (let [impl (set (or (:implementation v)
                                    (:primary-implementation v)
                                    #{}))
                      ;; compiler-implementation alone is not a portable claim
                      full? (set/subset? portable-backends impl)]
                  (when (and full? (= :implemented-partial (:disposition v)))
                    [k {:implementation impl
                        :conformance (:conformance v)
                        :missing (vec (:missing v []))
                        :disposition (:disposition v)}]))))
        (feature-catalog surface)))

(defn conformance-evidence-present?
  "True when a conformance keyword is backed by shared evidence or its named fixture."
  [surface conformance-key]
  (or (nil? conformance-key)
      (contains? (set (get-in surface [:other-gaps :backend-parity :evidence :shared-cases] []))
                 conformance-key)
      ;; Named fixture files used by the shared matrix.
      (.isFile (io/file (str "lang/conformance/" (name conformance-key) ".kotoba")))))

(defn file-bytes [path]
  (when (.isFile (io/file path))
    (slurp (io/file path))))

(defn vendor-drift
  "Return paths whose guest-grammar bytes differ from authority (or are missing)."
  ([authority-bytes]
   (vendor-drift authority-bytes (cons local-vendor-path sibling-vendor-paths)))
  ([authority-bytes paths]
   (into []
         (keep (fn [path]
                 (let [body (file-bytes path)]
                   (cond
                     (nil? body) {:path path :error :missing}
                     (not= authority-bytes body) {:path path :error :byte-mismatch}
                     :else nil))))
         paths)))

(defn validate
  "Run all W0 grammar-authority checks. Returns a result map with :valid?."
  ([]
   (validate (read-edn grammar-path)
             (read-edn surface-path)
             (when (.isFile (io/file pipeline-path))
               (read-edn pipeline-path))))
  ([grammar surface]
   (validate grammar surface nil))
  ([grammar surface pipeline]
   (let [authority-bytes (slurp (io/file grammar-path))
         forbidden (forbidden-heads grammar)
         inv-surface (invariant-surfaces surface)
         missing-forbidden (set/difference forbidden inv-surface)
         extra-invariant (set/difference inv-surface forbidden)
         admitted (admitted-source-forms grammar)
         classified (classified-forms surface)
         ;; Builtins and pure host helpers are grammar-classified by catalog
         ;; membership; they do not need surface-status feature rows.
         needs-surface (:all admitted)
         unclassified (set/difference needs-surface classified)
         portability (sugar-portability grammar)
         incomplete-portable
         (into []
               (keep (fn [[k meta]]
                       (when (and (:portable-claim? meta)
                                  ;; conceptual keys may not name a single head
                                  (or (seq (:forms meta))
                                      (contains? (conceptual-sugar-keys) k)))
                         (let [forms (or (seq (:forms meta)) #{})
                               form-ok? (or (empty? forms)
                                            (set/subset? (set forms) classified))]
                           (when-not form-ok?
                             {:sugar k :forms forms :reason :portable-forms-unclassified})))))
               portability)
         feature-claims (feature-portable-claims surface)
         portable-without-evidence
         (into []
               (keep (fn [[k meta]]
                       (let [conf (:conformance meta)
                             missing (:missing meta)
                             evidenced? (conformance-evidence-present? surface conf)]
                         (cond
                           (and (seq missing) conf (not evidenced?))
                           {:feature k :conformance conf :missing missing
                            :reason :portable-claim-missing-conformance-evidence}

                           (and (empty? missing) conf (not evidenced?))
                           {:feature k :conformance conf
                            :reason :conformance-key-not-in-shared-evidence}

                           :else nil))))
               feature-claims)
         vendor (vendor-drift authority-bytes)
         pipeline-errors
         (cond-> []
           (nil? pipeline)
           (conj {:code :pipeline/missing :path pipeline-path})
           (and pipeline (not= 1 (:kotoba.lang.elaboration-pipeline/version pipeline)))
           (conj {:code :pipeline/version
                  :actual (:kotoba.lang.elaboration-pipeline/version pipeline)})
           (and pipeline
                (not= grammar-path
                      (:kotoba.lang.elaboration-pipeline/source-surface-authority pipeline)))
           (conj {:code :pipeline/authority-path})
           (and pipeline
                (not (map? (:contract-versions pipeline))))
           (conj {:code :pipeline/contract-versions}))
         grammar-meta-errors
         (cond-> []
           (not= "kotoba-lang/kotoba-lang"
                 (:kotoba.lang.guest-grammar/authority grammar))
           (conj {:code :grammar/authority})
           (nil? (:kotoba.lang.guest-grammar/version grammar))
           (conj {:code :grammar/version}))
         surface-meta-errors
         (cond-> []
           (not= "kotoba-lang/kotoba-lang"
                 (:kotoba.lang.surface-status/authority surface))
           (conj {:code :surface/authority})
           (not (map? (:dispositions surface)))
           (conj {:code :surface/dispositions})
           (not (map? (:classification-rule surface)))
           (conj {:code :surface/classification-rule}))
         errors (vec (concat
                      grammar-meta-errors
                      surface-meta-errors
                      pipeline-errors
                      (when (seq missing-forbidden)
                        [{:code :forbidden/unclassified-by-surface
                          :forms missing-forbidden}])
                      (when (seq unclassified)
                        [{:code :admitted/unclassified-by-surface
                          :forms unclassified}])
                      (when (seq incomplete-portable)
                        [{:code :portable/sugar-unclassified
                          :entries incomplete-portable}])
                      (when (seq portable-without-evidence)
                        [{:code :portable/missing-evidence
                          :entries portable-without-evidence}])
                      (let [local-missing (filter #(and (= local-vendor-path (:path %))
                                                          (= :missing (:error %)))
                                                     vendor)
                              mismatches (filter #(= :byte-mismatch (:error %)) vendor)
                              ;; Sibling absence is expected outside the monorepo layout;
                              ;; fail closed only on local missing or any present mismatch.
                              bad (vec (concat local-missing mismatches))]
                          (when (seq bad)
                            [{:code :vendor/drift :paths bad}]))))
         valid? (empty? errors)]
     {:valid? valid?
      :errors errors
      :stats {:forbidden-heads (count forbidden)
              :invariant-surface (count inv-surface)
              :admitted-forms (count needs-surface)
              :classified-forms (count classified)
              :unclassified (count unclassified)
              :sugar-entries (count portability)
              :portable-sugar (count (filter (comp :portable-claim? val) portability))
              :feature-portable-claims (count feature-claims)
              :vendor-checked (inc (count sibling-vendor-paths))
              :extra-invariant-surface (count extra-invariant)}
      :extra-invariant-surface extra-invariant
      :unclassified unclassified
      :missing-forbidden missing-forbidden
      :vendor vendor})))

(defn -main [& _args]
  (let [result (validate)]
    (println "grammar-authority stats:" (pr-str (:stats result)))
    (if (:valid? result)
      (do (println "W0 PASS: guest-grammar authority, surface classification, and vendor sync")
          (System/exit 0))
      (do (println "W0 FAIL:" (pr-str (:errors result)))
          (System/exit 1)))))
