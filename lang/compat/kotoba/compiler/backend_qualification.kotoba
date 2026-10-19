(ns kotoba.compiler.backend-qualification
  "CI gate binding backend qualification claims to the provider manifest."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [kotoba.artifact.core :as artifact]))

(def manifest-resource "kotoba/lang/provider-conformance-v1.edn")
(def qualification-resource "kotoba/lang/backend-provider-qualification-v2.edn")
(def catalog-resource "kotoba/lang/capability-catalog.edn")
(def backend-keys #{:wasmtime :native :cljs})
(def qualification-keys
  #{:manifest-gate :execution-surface :execution-status :gaps :evidence})
(def execution-surfaces
  {:wasmtime :wasmtime-component-v1
   :native :aiueos-c-free-bare-metal-v1
   :cljs :nbb-process-v1})
(def qualified-evidence-keys
  {:wasmtime #{:runtime-boundary :semantic-vectors}
   :native #{:runtime-boundary :semantic-vectors :foreign-code-receipt}
   :cljs #{:runtime-boundary :semantic-vectors}})

(defn- fail! [message data]
  (throw (ex-info message (assoc data :phase :backend-provider-qualification))))

(defn- read-resource [path]
  (when-let [resource (io/resource path)]
    (edn/read-string (slurp resource))))

(defn verify-data!
  "Verifies one backend claim against supplied decoded resources. A pending
  backend must name concrete gaps and carry no qualification evidence. A
  qualified backend must close all gaps and supply backend-specific evidence.
  Native qualification is restricted to the aiueos C-free bare-metal surface
  and additionally requires a foreign-code receipt."
  [manifest registry qualification backend]
  (when-not (= :v1 (:kotoba.provider-conformance/format manifest))
    (fail! "provider conformance manifest format is unsupported" {}))
  (when-not (= :v2 (:kotoba.backend-provider-qualification/format qualification))
    (fail! "backend qualification format is unsupported" {}))
  (when-not (= backend-keys (set (keys (:backends qualification))))
    (fail! "backend qualification inventory is not exact" {}))
  (let [manifest-hash (artifact/sha256 manifest)
        manifest-claim (:provider-manifest qualification)
        capabilities (->> (:kits manifest) (mapcat :capabilities) vec)
        claim (get-in qualification [:backends backend])]
    (when-not (and (= :kotoba.provider-conformance/v1 (:format manifest-claim))
                   (= manifest-hash (:sha256 manifest-claim))
                   (= (count capabilities) (:capability-count manifest-claim)))
      (fail! "backend gate is not bound to this provider manifest"
             {:backend backend :manifest-sha256 manifest-hash}))
    (doseq [{:keys [name id]} capabilities]
      (when-not (= id (get registry name))
        (fail! "provider manifest disagrees with capability registry"
               {:backend backend :name name :id id :registered (get registry name)})))
    (when-not (and claim (= qualification-keys (set (keys claim)))
                   (= :required (:manifest-gate claim)))
      (fail! "backend manifest gate claim is not exact" {:backend backend}))
    (when-not (= (get execution-surfaces backend) (:execution-surface claim))
      (fail! "backend execution surface is not the compiler-owned surface"
             {:backend backend
              :expected (get execution-surfaces backend)
              :actual (:execution-surface claim)}))
    (case (:execution-status claim)
      :pending
      (when-not (and (seq (:gaps claim)) (empty? (:evidence claim)))
        (fail! "pending backend must name gaps and must not claim evidence"
               {:backend backend}))

      :qualified
      (when-not (and (empty? (:gaps claim))
                     (= (get qualified-evidence-keys backend)
                        (set (keys (:evidence claim))))
                     (every? string? (vals (:evidence claim))))
        (fail! "qualified backend lacks closed semantic evidence"
               {:backend backend}))

      (fail! "backend execution status is invalid" {:backend backend}))
    {:format :kotoba.backend-provider-qualification/receipt-v2
     :backend backend
     :execution-surface (:execution-surface claim)
     :provider-manifest-sha256 manifest-hash
     :capability-count (count capabilities)
     :manifest-gate :passed
     :execution-status (:execution-status claim)
     :gaps (:gaps claim)}))

(defn verify! [backend]
  (let [catalog (read-resource catalog-resource)
        registry (into {}
                       (map (fn [[name entry]]
                              [name (:compiler-wire-id entry)]))
                       (:capabilities catalog))]
    (verify-data! (read-resource manifest-resource)
                registry
                (read-resource qualification-resource)
                backend)))

(defn -main [& [command backend-name]]
  (when-not (= "verify" command)
    (fail! "usage: verify <wasmtime|native|cljs>" {}))
  (let [backend (keyword backend-name)
        receipt (verify! backend)]
    (println (pr-str receipt))))
