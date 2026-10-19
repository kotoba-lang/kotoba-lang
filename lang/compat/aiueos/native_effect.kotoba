(ns aiueos.native-effect
  "Fail-closed qualification for production native effects.

  Hosted VM, Linux, JVM/FFM and the retained C reference kernel are useful
  oracles, but none is the production execution surface accepted here."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def contract-resource "aiueos/native-effect-v1.edn")
(def production-surface :aiueos-c-free-bare-metal-v1)
(def foreign-code-keys
  #{:c-sources :foreign-objects :imports :dynamic-dependencies})
(def contract-keys
  #{:aiueos.native-effect/format :execution-surface :provider-manifest
    :capability :execution-status :gaps :evidence})
(def capability-keys #{:name :wire-id :request :result})
(def provider-manifest-keys #{:format :sha256 :capability-count})

(defn- fail! [message data]
  (throw (ex-info message (assoc data :phase :aiueos-native-effect))))

(defn read-contract []
  (edn/read-string (slurp (io/resource contract-resource))))

(defn verify-contract!
  "Admits only the compiler-owned C-free production surface. Pending claims
  name concrete gaps and carry no evidence; qualified claims must be backed by
  the exact four-part empty foreign-code receipt plus runtime semantics."
  [contract]
  (when-not (= contract-keys (set (keys contract)))
    (fail! "native effect contract keys are not exact" {}))
  (when-not (= :v1 (:aiueos.native-effect/format contract))
    (fail! "native effect contract format is unsupported" {}))
  (when-not (= production-surface (:execution-surface contract))
    (fail! "native effect execution surface is not C-free aiueos"
           {:actual (:execution-surface contract)}))
  (when-not (= provider-manifest-keys
               (set (keys (:provider-manifest contract))))
    (fail! "provider manifest binding is not exact" {}))
  (when-not (= capability-keys (set (keys (:capability contract))))
    (fail! "native effect capability contract is not exact" {}))
  (case (:execution-status contract)
    :pending
    (when-not (and (seq (:gaps contract)) (empty? (:evidence contract)))
      (fail! "pending native effect must name gaps without evidence" {}))

    :qualified
    (when-not (and (empty? (:gaps contract))
                   (= #{:runtime-boundary :semantic-vectors
                        :foreign-code-receipt}
                      (set (keys (:evidence contract)))))
      (fail! "qualified native effect evidence is incomplete" {}))

    (fail! "native effect execution status is invalid" {}))
  contract)

(defn verify-receipt!
  "Validates the executable receipt used by a qualified native effect claim."
  [receipt]
  (when-not (= production-surface (:execution-surface receipt))
    (fail! "native effect receipt names a non-production surface" {}))
  (when-not (= :clock/now (:capability receipt))
    (fail! "native effect receipt names the wrong capability" {}))
  (when-not (= foreign-code-keys
               (set (filter foreign-code-keys (keys receipt))))
    (fail! "native effect receipt omits a foreign-code dimension" {}))
  (doseq [key foreign-code-keys]
    (when-not (= [] (get receipt key))
      (fail! "native effect receipt contains foreign code"
             {:key key :value (get receipt key)})))
  receipt)
