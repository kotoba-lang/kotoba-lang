(ns kotoba.lang.type-system-portable-test
  "The genuinely portable slice of kotoba.lang.type-system-test: every
  assertion here operates on literal signature/form data and touches no file.
  Only `type-system-conformance-fixtures-match-contract`, which reads
  `lang/type-conformance/manifest.edn` and its per-case fixture files, stays
  in kotoba.lang.type-system-test (.clj).

  `defn-signature-contract-agrees-with-parameter-metadata` and
  `defn-signature-rejects-capability-metadata-disagreement` use the bare
  Clojure reader (`read-string`, not `clojure.edn/read-string`) to parse a
  `defn` form carrying `^{...}` metadata and a quoted symbol -- this is the
  one place this port needed a real ClojureScript reader, not just a
  reader-conditional, and `cljs.reader/read-string` reads the same forms."
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer [deftest is testing] :include-macros true])
            #?(:cljs [cljs.reader :refer [read-string]])
            [kotoba.lang.type-system :as types]))

(deftest public-signature-requires-capability-effects
  (let [signature {:params [[:cap :host/fs-read "/app/config"] :string]
                   :returns [:result :string :keyword]
                   :effects #{:host/fs-read :error}}]
    (is (:ok? (types/validate-signature signature)))
    (is (= #{:host/fs-read}
           (:missing-effects
            (types/validate-signature (assoc signature :effects #{:error})))))))

(deftest public-signature-cannot-return-a-region-reference
  (let [result (types/validate-signature
                {:params [[:region 'r]]
                 :returns [:region-ref 'r :i32]
                 :effects #{}})]
    (is (false? (:ok? result)))
    (is (some #(= :region/escape (:problem %)) (:problems result)))))

(deftest defn-signature-contract-agrees-with-parameter-metadata
  (let [form (read-string
              "(defn ^{:signature {:params [[:cap :host/fs-read \"/app\"] :string] :returns [:result :string :keyword] :effects #{:host/fs-read :error}}} read-config [^{:cap :host/fs-read} fs path] nil)")]
    (is (:ok? (types/validate-defn form)))
    (is (:ok? (types/validate-forms [form])))
    (is (= {:op :typed-defn
            :name "read-config"
            :params [[:cap :host/fs-read "/app"] :string]
            :returns [:result :string :keyword]
            :effects #{:host/fs-read :error}
            :schema :kotoba.typed-hir/v1}
           (types/typed-hir-entry form)))
    (is (= #{:host/fs-read}
           (:missing-effects
            (types/validate-defn
             (read-string
              "(defn ^{:signature {:params [[:cap :host/fs-read \"/app\"]] :returns :string :effects #{}}} read-config [^{:cap :host/fs-read} fs] nil)")))))))

(deftest defn-signature-rejects-capability-metadata-disagreement
  (let [result (types/validate-defn
                (read-string
                 "(defn ^{:signature {:params [[:cap :host/fs-read \"/app\"]] :returns :string :effects #{:host/fs-read}}} read-config [^{:cap :host/fs-write} fs] nil)"))]
    (is (false? (:ok? result)))
    (is (= :signature/capability-parameter-mismatch
           (:problem (first (:problems result)))))))

(deftest malformed-type-contracts-fail-closed
  (testing "unknown type tags and malformed capability scopes do not become :value"
    (is (false? (types/type? [:future :i32])))
    (is (false? (types/type? [:cap :host/fs-read 42])))
    (is (false? (types/type? [:cap :graph-read "/public"
                              [:cap :host/fs-write :any]])))
    (is (false? (types/type? [:region-ref 'r])))))

(deftest structured-children-are-effect-contained-and-cannot-share-caps-yet
  (is (:ok? (types/validate-scope {:effects #{:host/log-write}
                                   :children [{:effects #{:host/log-write}
                                               :captures [:string]}]})))
  (let [result (types/validate-scope
                {:effects #{:host/log-write}
                 :children [{:effects #{:host/http}
                             :captures [[:cap :host/fs-read "/app"]]}]})]
    (is (false? (:ok? result)))
    (is (= #{:spawn/effect-escapes :spawn/capability-move-unimplemented}
           (set (map :problem (:problems result)))))))

(deftest l4-option-and-no-nil-return
  (is (true? (types/type? [:option :string])))
  (is (false? (types/type? [:option])))
  (let [nil-ret (types/validate-signature
                 {:params [:i64] :returns :nil :effects #{}})]
    (is (false? (:ok? nil-ret)))
    (is (some #(= :type/no-nil-return (:problem %)) (:problems nil-ret))))
  (is (:ok? (types/validate-signature
             {:params [[:option :string]]
              :returns [:result :string :keyword]
              :effects #{}}))))

(deftest l4-host-import-arity-at-compile
  (let [catalog {'log-write {:params [:i32 :i32] :result :i32}
                 'clock-monotonic {:params [] :result :i64}}
        bad (types/validate-host-import-calls
             catalog '[(defn main [] (log-write 1))])
        good (types/validate-host-import-calls
              catalog '[(defn main [] (log-write 1 2) (clock-monotonic))])]
    (is (false? (:ok? bad)))
    (is (= :import/arity-mismatch (:problem (first (:problems bad)))))
    (is (:ok? good))))

(deftest l4-require-signatures
  (let [forms (list (read-string "(defn main [] 0)")
                    (read-string
                     "(defn ^{:signature {:params [] :returns :i64 :effects #{}}} ready [] 1)"))]
    (is (empty? (types/require-signatures-problems forms false)))
    (let [ps (types/require-signatures-problems forms true)]
      (is (= 1 (count ps)))
      (is (= :signature/required (:problem (first ps))))
      (is (= "main" (:function (first ps)))))))

(deftest l4-typed-hir-module
  (let [form (read-string
              "(defn ^{:signature {:params [] :returns :i64 :effects #{}}} main [] 0)")
        mod (types/typed-hir-module [form])]
    (is (:ok? mod))
    (is (= 1 (count (:entries mod))))
    (is (= :typed-defn (:op (first (:entries mod)))))))
