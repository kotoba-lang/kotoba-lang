(ns bonsai.remote-helper
  "Lifecycle and safety boundary for git-remote-kotoba transports."
  (:require [babashka.process :as process]
            [clojure.java.io :as io]
            [kotoba.lang.text :as str])
  (:import (java.security MessageDigest)))

(def services #{"git-upload-pack" "git-receive-pack"})

(defn- hex [bytes] (apply str (map #(format "%02x" (bit-and (int %) 0xff)) bytes)))
(defn cache-key [remote]
  (hex (.digest (MessageDigest/getInstance "SHA-256") (.getBytes ^String remote "UTF-8"))))

(defn local-path? [url]
  (or (str/starts-with? url "/") (str/starts-with? url "kotoba::")))

(defn local-path [url]
  (cond
    (str/starts-with? url "kotoba::") (subs url (count "kotoba::"))
    (str/starts-with? url "/") url
    :else nil))

(defn network-remote? [url]
  (or (str/starts-with? url "kotoba://")
      (str/starts-with? url "https://")
      (str/starts-with? url "http://")))

(defn materialized-path
  "Resolve URL to a bare repository path. Network remotes are isolated below
   an explicit absolute cache root and keyed by the full remote SHA-256."
  [url cache-root]
  (if-let [path (local-path url)]
    path
    (do
      (when-not (network-remote? url)
        (throw (ex-info "unsupported kotoba remote URL" {:reason :unsupported-url})))
      (when-not (and cache-root (.isAbsolute (io/file cache-root)))
        (throw (ex-info "KOTOBA_GIT_CACHE must be an absolute path for network remotes"
                        {:reason :invalid-cache})))
      (.getPath (io/file cache-root (str (cache-key url) ".git"))))))

(defn run-adapter!
  "Run the configured adapter as argv, never through a shell. Operation is
   `fetch` or `push`; remote and path are separate arguments so credentials
   never need to be interpolated into a command string."
  [adapter operation remote path]
  (when-not (and adapter (not (str/blank? adapter)))
    (throw (ex-info "KOTOBA_GIT_ADAPTER is required for network remotes"
                    {:reason :missing-adapter})))
  (let [result @(process/process [adapter operation remote path]
                                 {:in :inherit :out :string :err :inherit})]
    (when-not (str/blank? (:out result))
      (binding [*out* *err*] (print (:out result)) (flush)))
    (when-not (zero? (:exit result))
      (throw (ex-info "kotoba Git adapter failed"
                      {:reason :adapter-failed :operation operation
                       :exit (:exit result)})))))

(defn prepare!
  [url {:keys [cache-root adapter]}]
  (let [path (materialized-path url cache-root)]
    (when (network-remote? url)
      (.mkdirs (io/file cache-root))
      (run-adapter! adapter "fetch" url path))
    path))

(defn connect!
  "Run Git service over the materialized repo. Successful receive-pack is
   followed by adapter `push`, which is where a kotobase adapter verifies the
   resulting object closure and submits signed nekko.ref-event updates."
  [service url opts]
  (when-not (contains? services service)
    (throw (ex-info "unsupported Git service" {:reason :unsupported-service})))
  (let [path (or (:prepared-path opts) (prepare! url opts))
        result @(process/process [service path]
                                 {:in :inherit :out :inherit :err :inherit})]
    (when (and (zero? (:exit result)) (= service "git-receive-pack")
               (network-remote? url))
      (run-adapter! (:adapter opts) "push" url path))
    (:exit result)))
