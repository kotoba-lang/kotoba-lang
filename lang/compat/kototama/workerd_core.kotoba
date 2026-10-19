(ns kototama.workerd-core
  "Core-Wasm adapter plan for workerd-style WebAssembly hosts.

   workerd's WebAssembly API instantiates Core modules, not arbitrary standard
   Components.  This adapter therefore shares the same explicit grants and
   named-provider invariant, while refusing Component binaries instead of
   silently degrading their authority model.  The returned plan is consumed by
   the JavaScript host integration; it deliberately contains no ambient WASI."
  (:require [kototama.component-provider :as provider]))

(defn- manifest-import [capability ability]
  (let [module (namespace capability)
        import-name (name capability)]
    (when-not (and (keyword? capability) module (seq import-name))
      (throw (ex-info "workerd capability must name an exact WIT import"
                      {:phase :workerd-core :capability capability})))
    {:module module
     :name import-name
     :capability (subs (str capability) 1)
     :ability ability}))

(defn prepare-core-module!
  "Return the exact JavaScript import plan for a Core-Wasm artifact.

   Providers are retained as opaque handles for the embedding application. A
   workerd binding must turn only these named entries into its WebAssembly
   imports object and invoke `provider/invoke!` for every guest call."
  [{:keys [artifact] :as request}]
  (when-not (= :wasm/v1 (:format artifact))
    (throw (ex-info "workerd accepts Core-Wasm artifacts only"
                    {:phase :workerd-core :format (:format artifact)})))
  (when-not (bytes? (:bytes artifact))
    (throw (ex-info "workerd Core-Wasm artifact requires bytes"
                    {:phase :workerd-core})))
  (let [prepared (provider/prepare! (assoc request :runtime :workerd-core
                                                   :component? false))
        abilities (:component-imports artifact)
        capabilities (sort-by str (keys abilities))]
    {:runtime :workerd-core
     :ambient-wasi false
     :module-bytes (:bytes artifact)
     :imports (:providers prepared)
     :abilities abilities
     :manifest {:format "kototama.workerd-core/v1"
                :imports (mapv #(manifest-import % (get abilities %))
                               capabilities)
                :grants (mapv #(subs (str %) 1) capabilities)}
     :invoke (fn [import payload] (provider/invoke! prepared import payload))}))
