(ns kotoba.lang.conformance-matrix-test
  "T1.2: required-backends matrix structural tests."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.conformance-matrix :as cm]))

(deftest manifest-version-2-and-matrix-valid
  (let [m (cm/load-manifest)
        v (cm/validate-matrix m)]
    (is (= 2 (:kotoba.lang.conformance/version m)))
    (is (= "T1.2" (:kotoba.lang.conformance/wbs m)))
    (is (true? (:ok? v)) (str "problems: " (pr-str (:problems v))))
    (is (>= (:case-count v) 30))
    (is (>= (:pure-product-count v) 15)
        "pure-product cases drive T1.3 dual-backend gate")))

(deftest pure-product-requires-kir-and-wasm
  (let [m (cm/load-manifest)
        pp (cm/pure-product-cases m)]
    (is (seq pp))
    (doseq [c pp]
      (is (= cm/pure-product-required
             (cm/required-backends-for m c))
          (str (:id c))))))

(deftest case-classes-cover-all-cases
  (let [m (cm/load-manifest)
        classes (set (keys (cm/case-classes m)))]
    (doseq [c (cm/cases m)]
      (is (contains? classes (:class c)) (str (:id c) " " (:class c))))))

(deftest negative-cases-use-compiler-admit
  (let [m (cm/load-manifest)
        neg (cm/cases-for-class m :negative-admit)]
    (is (seq neg))
    (doseq [c neg]
      (is (= #{:compiler-admit} (:required-backends c)) (str (:id c))))))

;; --- surface-status <-> manifest agreement --------------------------------

(deftest recorded-claims-and-required-backends-agree
  (let [v (cm/validate-claims (cm/load-manifest) (cm/load-surface-status))]
    (is (true? (:ok? v)) (str "problems: " (pr-str (:problems v))))
    (is (pos? (:linked-case-count v))
        "no surface-status entry links a conformance case, so this gate is inert")))

(deftest a-measured-compiler-rejection-must-carry-the-orphan-note
  (let [m (cm/load-manifest)
        ss (cm/load-surface-status)
        contradictory (assoc-in ss [:other-gaps :nested-destructuring :measurement]
                                {:result :rejected})
        v (cm/validate-claims m contradictory)]
    (is (false? (:ok? v)))
    (is (= :measured-rejection-without-orphan-note
           (-> v :problems first :type)))))

(deftest an-orphan-note-without-a-measurement-is-stale
  (let [m (cm/load-manifest)
        ss (cm/load-surface-status)
        stale (assoc-in ss [:other-gaps :nested-destructuring :orphaned-conformance]
                        {:case :nested-let-destructuring :executed-by :none})
        v (cm/validate-claims m stale)]
    (is (false? (:ok? v)))
    (is (some #(= :orphan-note-without-measured-rejection (:type %)) (:problems v))
        "a note claiming the compiler rejects the surface outlives the
         measurement it was based on unless something checks")))

(deftest a-case-declared-in-another-manifest-must-say-so
  (let [m (cm/load-manifest)
        ss (cm/load-surface-status)
        stripped (update-in ss [:other-gaps :record-schema-values]
                            dissoc :conformance-manifest)
        v (cm/validate-claims m stripped)]
    (is (false? (:ok? v)))
    (is (= :conformance-case-unknown (-> v :problems first :type))
        ":record-kit lives in the compiler's pilot manifest; without
         :conformance-manifest the link is indistinguishable from a dangling one")))


;; --- required backends <-> the runners that drive them ---------------------

(deftest every-recorded-case-accounts-for-each-required-backend
  (let [v (cm/validate-execution (cm/load-manifest))]
    (println (format "SCANNED\t%d\tcases recording where their backends are driven"
                     (:recorded v)))
    (is (true? (:ok? v)) (str "problems: " (pr-str (:problems v))))
    (is (>= (:recorded v) cm/min-cases-with-execution-record))))

(defn- with-case
  "Replace one case by id, so a mutation names what it changed."
  [manifest id f]
  (update manifest :cases
          (fn [cs] (mapv #(if (= id (:id %)) (f %) %) cs))))

(def ^:private recorded-case :bounded-set-literal-and-operations)

(deftest dropping-a-required-backend-from-the-declaration-goes-red
  ;; The load-bearing direction. If removing `:kir` from the case that this
  ;; repository actually runs left everything green, the agreement between the
  ;; declaration and the runner would be an accident.
  (let [m (with-case (cm/load-manifest) recorded-case
            #(assoc % :required-backends #{:wasm32-kotoba-v1}))
        v (cm/validate-execution m)]
    (is (false? (:ok? v)))
    (is (some #(and (= :backend-recorded-but-not-required (:type %))
                    (= :kir (:backend %)))
              (:problems v))
        "the runner is still named; the requirement is gone")))

(deftest requiring-a-backend-with-neither-runner-nor-reason-goes-red
  (let [m (with-case (cm/load-manifest) recorded-case
            #(dissoc % :unexecuted-backends))
        v (cm/validate-execution m)]
    (is (false? (:ok? v)))
    (is (some #(and (= :required-backend-not-accounted-for (:type %))
                    (= :wasm32-kotoba-v1 (:backend %)))
              (:problems v)))))

(deftest a-deferral-that-outlived-what-it-excused-goes-red
  (let [m (with-case (cm/load-manifest) recorded-case
            #(assoc-in % [:executed-by :wasm32-kotoba-v1] "somewhere"))
        v (cm/validate-execution m)]
    (is (false? (:ok? v)))
    (is (some #(= :backend-both-executed-and-deferred (:type %)) (:problems v))
        "a backend cannot be both driven and excused for not being driven")))

(deftest a-deferral-without-a-date-or-a-closing-condition-goes-red
  (let [m (with-case (cm/load-manifest) recorded-case
            #(assoc-in % [:unexecuted-backends :wasm32-kotoba-v1]
                       {:reason "later"}))
        v (cm/validate-execution m)]
    (is (false? (:ok? v)))
    (is (= [:as-of :closes-when]
           (-> (filter #(= :deferral-missing-keys (:type %)) (:problems v))
               first :missing)))))

(deftest deleting-the-records-does-not-pass-by-having-nothing-to-check
  (let [m (update (cm/load-manifest) :cases
                  (fn [cs] (mapv #(dissoc % :executed-by :unexecuted-backends) cs)))
        v (cm/validate-execution m)]
    (is (false? (:ok? v)))
    (is (= 0 (:recorded v)))
    (is (some #(= :execution-record-floor (:type %)) (:problems v)))))

(deftest backends-catalog-has-t13-pair
  (let [m (cm/load-manifest)
        b (cm/backends m)]
    (is (contains? b :kir))
    (is (contains? b :wasm32-kotoba-v1))
    (is (= :required-for-pure-product (get-in b [:kir :status])))
    (is (= :required-for-pure-product (get-in b [:wasm32-kotoba-v1 :status])))))
