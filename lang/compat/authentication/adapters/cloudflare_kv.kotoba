(ns authentication.adapters.cloudflare-kv
  "Cloudflare KV IEphemeralStore adapter. KV alone cannot provide atomic
  compare-and-delete; pass a Durable Object `coordinator` for production
  put-once/consume semantics."
  (:require [authentication.identity-ports :as ports]))

(defn- encode [value version]
  (js/JSON.stringify #js {:version version :value (clj->js value)}))

(defn- decode [raw]
  (when raw
    (let [parsed (js/JSON.parse raw)]
      {:version (aget parsed "version")
       :value (js->clj (aget parsed "value") :keywordize-keys true)})))

(defn- coordinated [coordinator operation payload fallback]
  (if coordinator
    (.run coordinator operation (clj->js payload))
    (fallback)))

(defrecord CloudflareKvStore [kv prefix coordinator random-id]
  ports/IEphemeralStore
  (-put-once! [_ key value ttl]
    (let [full-key (str prefix key)
          version (random-id)]
      (coordinated coordinator "put-once"
                   {:key full-key :value value :version version :ttl ttl}
                   (fn []
                     (-> (.get kv full-key)
                         (.then (fn [existing]
                                  (if existing false
                                    (-> (.put kv full-key (encode value version)
                                              #js {:expirationTtl ttl})
                                        (.then (fn [_] version)))))))))))
  (-get-value [_ key]
    (-> (.get kv (str prefix key)) (.then decode)))
  (-consume! [_ key expected-version]
    (let [full-key (str prefix key)]
      (coordinated coordinator "consume"
                   {:key full-key :expected-version expected-version}
                   (fn []
                     (-> (.get kv full-key)
                         (.then (fn [raw]
                                  (let [record (decode raw)]
                                    (if (not= expected-version (:version record)) false
                                      (-> (.delete kv full-key)
                                          (.then (fn [_] (:value record)))))))))))))
  (-delete! [_ key] (.delete kv (str prefix key))))

(defn store
  [{:keys [kv prefix coordinator random-id]
    :or {prefix "identity:" random-id #(str (js/crypto.randomUUID))}}]
  (->CloudflareKvStore kv prefix coordinator random-id))
