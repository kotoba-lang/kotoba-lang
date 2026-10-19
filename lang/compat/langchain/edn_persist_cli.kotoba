(ns langchain.edn-persist-cli
  "JSON/stdin bridge for non-Clojure repository-driven runtimes. The bridge
  owns EDN parsing and the shared file lock; Python/Rust/etc. never parse and
  rewrite an unknown EDN projection themselves."
  (:require [langchain.edn-persist :as persist]
            [langchain.json :as json])
  (:gen-class))

(defn execute
  "Execute one bridge operation and return a JSON-safe value."
  [operation state-file stream input]
  (let [host (persist/host state-file)]
    (case operation
      "append" ((:append host) stream (json/decode input))
      "latest" (last ((:read host) stream 0))
      "read" ((:read host) stream 0)
      (throw (ex-info "unknown EDN persistence bridge operation"
                      {:type :langchain.edn-persist/unknown-operation})))))

(defn -main [& [operation state-file stream]]
  (try
    (when-not (and operation state-file stream)
      (throw (ex-info "EDN persistence bridge requires operation, file, stream"
                      {:type :langchain.edn-persist/bridge-arguments})))
    (println (json/encode (execute operation state-file stream (slurp *in*))))
    (catch Exception _
      ;; Never echo input, paths, or exception data across the process boundary.
      (binding [*out* *err*] (println "EDN persistence bridge failed"))
      (System/exit 1))))
