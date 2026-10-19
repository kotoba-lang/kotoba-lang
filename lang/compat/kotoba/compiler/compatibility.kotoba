(ns kotoba.compiler.compatibility)

(def schema :kotoba.compatibility/v1)
(def compiler-version "kotoba-compiler/1")
(def language-version :kotoba.language/safe-v1)
(def tender-role :kototama/component-tender-v1)

(defn descriptor [{:keys [hir-format kir-format target target-profile value-abi]}]
  {:format schema
   :compiler compiler-version
   :language language-version
   :hir hir-format
   :kir kir-format
   :target target
   :runtime (:runtime target-profile)
   :value-abi value-abi
   :tender-role tender-role
   :tender-contract (case (:execution target-profile)
                      :javascript :kotoba.restricted-esm/v1
                      :wasm :kotoba.capability-host/v1
                      :native :kotoba.supervisor/v1
                      :process :kotoba.aiueos-process/v1
                      :kernel :kotoba.aiueos-kernel/v1
                      :firmware :kotoba.aiueos-firmware/v1
                      :cljs :kotoba.cljs-host/v1
                      :kotoba.unsupported/v1)})
