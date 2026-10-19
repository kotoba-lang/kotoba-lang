(ns kotoba.compiler.nbb.x86-64-cli
  "x86-64-only nbb native compiler entrypoint."
  (:require [kotoba.compiler.nbb.cli :as native-cli]
            [kotoba.compiler.nbb.cli-support :as support]
            [kotoba.compiler.nbb.compile-cache :as compile-cache]
            [kotoba.compiler.nbb.native-package :as native-package]
            [kotoba.kir.target :as target-profile]
            [kotoba.native.x86-64 :as x86-64]))

(defn- run! [args context]
  (native-cli/run! args :x86_64-kotoba-v1 x86-64/emit-program
                   native-package/package context))

(if (= "worker" (first *command-line-args*))
  (let [target-name (support/option *command-line-args* "--target")
        target (get native-cli/targets target-name)
        _ (when-not (= :x86_64-kotoba-v1 (some-> target target-profile/backend))
            (support/usage-error!
             (str "error: x86-64 worker does not cover target " target-name)))
        context (assoc (compile-cache/create-context) :target target)]
    (support/serve! #(run! % context) target))
  (support/execute! #(run! % nil)))
