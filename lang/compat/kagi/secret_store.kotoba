(ns kagi.secret-store
  "SecretStore providers for kagi key custody.

  Secret values are returned only to runtime code that needs them. Callers must
  not log returned values."
  (:require [clojure.edn :as edn]
            [clojure.java.shell :as sh]
            [clojure.string :as str]))

(defprotocol SecretStore
  (put-secret! [store ref secret opts])
  (get-secret [store ref opts])
  (delete-secret! [store ref opts])
  (exists? [store ref])
  (metadata [store ref]))

(defn parse-ref [s]
  (let [s* (str/trim (str s))]
    (when-not (str/blank? s*)
      (if-let [[_ scheme body] (re-matches #"^([A-Za-z][A-Za-z0-9+.-]*)://(.+)$" s*)]
        {:scheme (str/lower-case scheme) :body body :raw s*}
        {:scheme "keychain" :body s* :raw (str "keychain://" s*)}))))

(defn redact-ref [s]
  (when-let [{:keys [scheme body]} (parse-ref s)]
    (let [tail (last (remove str/blank? (str/split body #"/")))]
      (str scheme "://.../" (or tail "secret")))))

(defn- service-account [ref]
  (let [{:keys [scheme body raw]} (parse-ref ref)]
    (when-not (= "keychain" scheme)
      (throw (ex-info "AppleKeychainStore only supports keychain:// refs"
                      {:ref (redact-ref raw) :scheme scheme})))
    (let [[service account] (str/split body #"/" 2)]
      {:service service :account (or account "default")})))

(defn- sh-ok [{:keys [exit out err] :as result} context]
  (if (zero? exit)
    (str/trim-newline (or out ""))
    (throw (ex-info (str "secret store command failed: " context)
                    {:context context
                     :exit exit
                     :err (some-> err str/trim)
                     :result (dissoc result :out)}))))

(defrecord AppleKeychainStore [sh-fn]
  SecretStore
  (put-secret! [_ ref secret _opts]
    (let [{:keys [service account]} (service-account ref)]
      (sh-ok (sh-fn "security" "add-generic-password"
                    "-U" "-s" service "-a" account "-w" (str secret))
             (str "apple-keychain put " service "/" account))
      {:ok? true :ref (redact-ref ref) :provider :apple-keychain}))
  (get-secret [_ ref _opts]
    (let [{:keys [service account]} (service-account ref)]
      (sh-ok (sh-fn "security" "find-generic-password"
                    "-s" service "-a" account "-w")
             (str "apple-keychain get " service "/" account))))
  (delete-secret! [_ ref _opts]
    (let [{:keys [service account]} (service-account ref)]
      (sh-ok (sh-fn "security" "delete-generic-password"
                    "-s" service "-a" account)
             (str "apple-keychain delete " service "/" account))
      {:ok? true :ref (redact-ref ref) :provider :apple-keychain}))
  (exists? [store ref]
    (try
      (boolean (seq (get-secret store ref {})))
      (catch Exception _ false)))
  (metadata [_ ref]
    (let [{:keys [service account]} (service-account ref)]
      {:ref (redact-ref ref)
       :provider :apple-keychain
       :service service
       :account account
       :custody :os-keychain
       :secret-readable? false})))

(defn apple-keychain-store
  ([] (apple-keychain-store sh/sh))
  ([sh-fn] (->AppleKeychainStore sh-fn)))

(defrecord EnvStore []
  SecretStore
  (put-secret! [_ _ _ _]
    (throw (ex-info "env store is read-only" {:provider :env})))
  (get-secret [_ ref _]
    (let [{:keys [scheme body raw]} (parse-ref ref)]
      (when-not (= "env" scheme)
        (throw (ex-info "EnvStore only supports env:// refs"
                        {:ref (redact-ref raw) :scheme scheme})))
      (or (not-empty (System/getenv body))
          (throw (ex-info "missing env secret" {:env body})))))
  (delete-secret! [_ _ _]
    (throw (ex-info "env store is read-only" {:provider :env})))
  (exists? [store ref] (try (boolean (seq (get-secret store ref {}))) (catch Exception _ false)))
  (metadata [_ ref] {:ref (redact-ref ref) :provider :env :custody :process-env}))

(defn env-store [] (->EnvStore))

(defrecord MemSecretStore [a]
  SecretStore
  (put-secret! [_ ref secret _] (swap! a assoc (:raw (parse-ref ref)) (str secret)) {:ok? true})
  (get-secret [_ ref _] (or (get @a (:raw (parse-ref ref)))
                            (throw (ex-info "missing mem secret" {:ref (redact-ref ref)}))))
  (delete-secret! [_ ref _] (swap! a dissoc (:raw (parse-ref ref))) {:ok? true})
  (exists? [_ ref] (contains? @a (:raw (parse-ref ref))))
  (metadata [_ ref] {:ref (redact-ref ref) :provider :mem :custody :test-only}))

(defn mem-secret-store
  ([] (mem-secret-store {}))
  ([seed] (->MemSecretStore (atom seed))))

(defn store-for-ref [ref]
  (case (:scheme (parse-ref ref))
    "keychain" (apple-keychain-store)
    "env" (env-store)
    (throw (ex-info "unsupported secret ref scheme" {:ref (redact-ref ref)}))))

(defn put-edn! [store ref value]
  (put-secret! store ref (pr-str value) {:content-type "application/edn"}))

(defn get-edn [store ref]
  (edn/read-string (get-secret store ref {:content-type "application/edn"})))
