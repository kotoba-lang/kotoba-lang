(ns kotoba.lang.value-runtime-native-contract-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]))

(def authority-path "lang/value-runtime-native.edn")
(def resource-path "resources/kotoba/lang/value-runtime-native.edn")

(defn- read-contract [path]
  (edn/read-string (slurp path)))

(deftest native-value-runtime-contract-is-vendored-byte-for-byte
  (is (= (slurp authority-path) (slurp resource-path))))

(deftest identity-location-and-authority-stay-distinct
  (let [contract (read-contract authority-path)
        identity (:identity contract)]
    (is (= :v1 (:kotoba.value-runtime-native/format contract)))
    (is (= :positive-i64 (get-in identity [:runtime-handle :representation])))
    (is (= [:value-cid :runtime-handle]
           (get-in identity [:capability :not-derived-from])))
    (is (= {:value/intern 15 :value/hydrate 14}
           (into {}
                 (keep (fn [[op descriptor]]
                         (when (map? (:authority descriptor))
                           [op (get-in descriptor [:authority :compiler-wire-id])]))
                       (:operations contract)))))))

(deftest production-native-is-c-free-and-fails-closed-until-qualified
  (let [contract (read-contract authority-path)
        native (:production-native contract)
        receipt (:foreign-code-receipt native)]
    (is (= :aiueos-c-free-bare-metal-v1 (:execution-surface native)))
    (is (= :typed-capability-syscall (:transport native)))
    (is (= :forbidden (:hosted-context-callbacks native)))
    (is (= :forbidden (:c-loader-qualification native)))
    (is (= {:page-bytes 4096
            :permissions [:read :write :no-execute]
            :physical-slot-capacity 63
            :logical-handle-maximum 4096
            :synchronization :kotoba-kernel-compare-exchange-u32
            :native-export "kotoba_aiueos_value_handle_arena"}
           (:handle-arena native)))
    (is (= {:request-bytes 96
            :capability-table-bytes 4096
            :trusted-profile [:arena-length :request-length
                              :capability-table-length :current-domain]
            :native-export "kotoba_aiueos_value_runtime_dispatch"
            :provider-routes {:value/intern 15 :value/hydrate 14}}
           (:typed-dispatch native)))
    (is (= {:raw-envelope-bytes 104
            :user-page-bytes 4096
            :request-offset-maximum 3992
            :copy-range [0 56]
            :expected-digest-range [24 56]
            :capability-handle-range [56 64]
            :raw-zero-range [64 104]
            :normalized-zero-range [56 96]
            :native-export "kotoba_aiueos_value_runtime_entry"}
           (:normalized-entry native)))
    (is (every? empty? (vals receipt)))
    (is (= :deny-until-qualified (get-in contract [:admission :native])))
    (testing "the contract does not claim an absent CPL3 provider"
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-handle-arena-object])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-c-free-normalized-dispatch])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-c-free-entry-normalization])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-c-free-kernel-image-link])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-c-free-syscall-return-admission])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-c-free-gdt-tss-kernel-stack])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-c-free-scheduler-domain-publication])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-c-free-capability-table-mutation])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-c-free-provider-generation-policy])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-live-entry-assembly])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-sealed-provider-transport])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-cas-digest-primitive])))
      (is (= :implemented
             (get-in contract [:implementation-status
                               :aiueos-request-bound-cas-digest])))
      (is (= :pending
             (get-in contract [:implementation-status :aiueos-cpl3-provider]))))))
