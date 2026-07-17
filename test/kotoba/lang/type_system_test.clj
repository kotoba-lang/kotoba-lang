(ns kotoba.lang.type-system-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.type-system :as types]))

(def conformance-manifest "lang/type-conformance/manifest.edn")

(defn read-edn [path]
  (edn/read-string (slurp (io/file path))))

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
            :effects #{:host/fs-read :error}}
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

(deftest type-system-conformance-fixtures-match-contract
  (let [manifest (read-edn conformance-manifest)]
    (is (= 1 (:kotoba.lang.type.conformance/version manifest)))
    (doseq [tc (:cases manifest)
            :let [result (types/validate-case
                          tc
                          (read-edn (str "lang/type-conformance/" (:file tc))))]]
      (case (:kind tc)
        :accept (is (:ok? result) (:id tc))
        :expect-error (do
                        (is (false? (:ok? result)) (:id tc))
                        (is (some #(= (:problem tc) (:problem %)) (:problems result))
                            (:id tc)))))))
