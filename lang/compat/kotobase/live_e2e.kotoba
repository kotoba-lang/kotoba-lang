(ns kotobase.live-e2e
  "Opt-in real kotobase.net CACAO write/read smoke test.

  Uses an ephemeral Ed25519 identity and unique database name. The secret never
  leaves process memory; only the self-issued CACAO is sent to the service."
  (:require ["@noble/curves/ed25519.js" :refer [ed25519]]
            [kotobase.client :as client]))

(defn fail! [error]
  (js/console.error "kotobase.net live E2E failed:" error)
  (set! (.-exitCode js/process) 1))

(defn marker-visible?
  [^js result marker]
  (some #(re-find (re-pattern marker) (str %))
        (js->clj (or (.-rows_edn result) (.-rows result)))))

(defn await-marker
  [c db-name query marker remaining]
  (-> (client/q c db-name query)
      (.then
       (fn [result]
         (cond
           (marker-visible? result marker) result
           (pos? remaining)
           (-> (js/Promise. (fn [resolve] (js/setTimeout resolve 500)))
               (.then (fn [_]
                        (await-marker c db-name query marker (dec remaining)))))
           :else
           (throw (js/Error. (str "marker absent after consistency wait: "
                                  (js/JSON.stringify result)))))))))

(defn store-post
  [c method body capabilities]
  (let [cacao (client/request-cacao c capabilities (:did c))
        headers #js {"content-type" "application/json"
                     "authorization" (str "CACAO " cacao)
                     "x-kotoba-did" (:did c)}]
    (-> (js/fetch (str (:endpoint c) "/xrpc/net.kotobase.store." method)
                  #js {:method "POST" :headers headers
                       :body (js/JSON.stringify (clj->js (assoc body :cacao_b64 cacao)))})
        (.then
         (fn [^js response]
           (-> (.text response)
               (.then
                (fn [text]
                  (let [parsed (if (seq text) (js/JSON.parse text) #js {})]
                    (when (or (not (.-ok response)) (false? (.-ok parsed)))
                      (throw (js/Error.
                              (str method " " (.-status response) ": " text))))
                    parsed)))))))))

(defn main []
  (if-not (= "1" (.. js/process -env -KOTOBASE_LIVE_E2E))
    (js/console.log "SKIP: set KOTOBASE_LIVE_E2E=1 for real kotobase.net E2E")
    (let [secret (.randomPrivateKey (.-utils ed25519))
          db-name (str "semantic-code-e2e-" (.now js/Date))
          marker (str "marker-" (.now js/Date))
          c (client/make-client
             {:endpoint "https://kotobase.net"
              :operator-did "did:web:kotobase.net"
              :secret-key secret})
          tx (pr-str [{:db/id (str "semantic-code-e2e/" marker)
                       :semantic/marker marker}])
          query "{:find [?v] :where [[?e :semantic/marker ?v]]}"]
      (-> (client/transact c db-name tx {:retry? true})
          (.then (fn [^js response]
                   (js/console.log "transact graph" (.-graph response))
                   (await-marker c db-name query marker 10)))
          (.then
           (fn [_]
             (js/console.log "PASS: real kotobase.net CACAO transact/query" db-name)
             (store-post c "put"
                         {:coll db-name :key "semantic-code" :val {:marker marker}}
                         ["datom:transact" "tx:create"])))
          (.then
           (fn [^js response]
             (when-not (and (string? (.-cid response))
                            (re-find #"^bafy" (.-cid response)))
               (throw (js/Error. (str "store.put did not return a DAG-CBOR CID: "
                                      (js/JSON.stringify response)))))
             (client/store-get c db-name "semantic-code")))
          (.then
           (fn [value]
               (when-not (= marker (:marker value))
                 (throw (js/Error. (str "store.get mismatch: " value))))
               (js/console.log "PASS: real IPLD store put/get" db-name)
               (js/Promise.all
                (clj->js
                 (mapv #(client/store-append c db-name {:index %}) (range 50))))))
          (.then
           (fn [events]
             (let [seqs (->> (array-seq events) (map #(get % :seq)) sort vec)]
               (when-not (= (vec (range 1 51)) seqs)
                 (throw (js/Error. (str "concurrent append sequence mismatch: " seqs))))
               (client/store-read c db-name 0))))
          (.then
           (fn [events]
             (when-not (= (vec (range 1 51)) (mapv :seq events))
               (throw (js/Error. (str "read sequence mismatch: " events))))
             (js/console.log "PASS: real strict concurrent append/read" db-name)))
          (.catch fail!)))))
