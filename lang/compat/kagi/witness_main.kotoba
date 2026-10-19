(ns kagi.witness-main
  "Standalone public-checkpoint witness process. It owns no vault or private
  keys and accepts only checkpoints verified against its allowlist."
  (:require [kagi.crypto :as crypto]
            [kagi.persist :as persist]
            [kagi.witness-http :as http]
            [kagi.witness-service :as service])
  (:gen-class)
  (:import [java.util.concurrent CountDownLatch]))

(def loopback-hosts #{"127.0.0.1" "localhost" "::1"})

(defn validate-config!
  [{:witness/keys [state-path public-keys bind port] :as config}]
  (when-not (and (seq state-path) (map? public-keys) (seq public-keys)
                 (contains? loopback-hosts (or bind "127.0.0.1"))
                 (integer? (or port 8789)) (<= 0 (or port 8789) 65535))
    (throw (ex-info "invalid witness config; bind must be loopback behind TLS proxy"
                    {:state-path? (boolean (seq state-path))
                     :public-key-count (count public-keys)
                     :bind bind :port port})))
  config)

(defn start-from-config! [config]
  (let [{:witness/keys [state-path public-keys bind port]}
        (validate-config! config)
        provider (crypto/jvm-provider)
        witness-service (service/file-service state-path provider public-keys)
        server (http/start! witness-service (or bind "127.0.0.1") (or port 8789))]
    {:server server :service witness-service :endpoint (http/endpoint server)}))

(defn -main [& [config-path]]
  (when-not (seq config-path)
    (throw (ex-info "usage: clojure -M:witness path/to/witness-config.edn" {})))
  (let [config (or (persist/load* config-path)
                   (throw (ex-info "witness config not found" {:path config-path})))
        {:keys [server endpoint]} (start-from-config! config)
        stop (CountDownLatch. 1)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn [] (.close server) (.countDown stop))))
    (println (pr-str {:witness/status :ready :endpoint endpoint :private-keys? false}))
    (.await stop)))
