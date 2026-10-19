(ns kekkai.kotoba
  "Wire a `DatomicStore` to a kotoba-server pod (e.g. kotobase.net) over the
  ai.gftd.apps.kotobase.datomic.* XRPC namespace. The store is unchanged — it
  only ever calls the `:db-api` map; `langchain.kotoba-db/kotoba-api` implements
  that map against the remote pod, so this is purely a constructor.

  I/O is injected (langchain's host-caps contract): an http-fn is provided here
  via JDK java.net.http (no dependency), and the JSON pair is passed by the
  caller (e.g. clojure.data.json) so this namespace stays dependency-free.

  The kotobase.net datomic endpoints are auth-gated — pass :token (Bearer JWT)
  or :cacao + :did. A live run therefore needs a credential; the store contract
  itself is already proven backend-agnostic (test/kekkai/store_contract_test)."
  (:require [clojure.string :as str]
            [langchain.kotoba-db :as kdb]
            [kekkai.cacao :as cacao]
            [kekkai.store :as store])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]
           [java.time Instant]
           [java.util UUID]))

(defn jvm-http-fn
  "host-caps :http-fn backed by the JDK HTTP client (no dependency)."
  [{:keys [url method headers body]}]
  (let [b (HttpRequest/newBuilder (URI/create url))]
    (doseq [[k v] headers] (.header b k v))
    (let [req  (-> b (.method (str/upper-case (name (or method :post)))
                             (if body
                               (HttpRequest$BodyPublishers/ofString body)
                               (HttpRequest$BodyPublishers/noBody)))
                   (.build))
          resp (.send (HttpClient/newHttpClient) req (HttpResponse$BodyHandlers/ofString))]
      {:status (.statusCode resp) :body (.body resp)})))

(defn kotoba-store
  "A `store/DatomicStore` backed by a kotoba-server pod (e.g. kotobase.net).
   Auth options (pick one):
     :token            Bearer JWT (handed)
     :cacao + :did     a ready CACAO b64 + signer DID
     :identity         a kekkai.cacao identity ({:private-key :public-key :did})
                       → the actor SELF-MINTS a CACAO for :grant (default a
                       read grant on :graph). This is the charter path:
                       no handed token, no coordination-server auth-key — the
                       actor issues its own capability from its own node key.
   opts:
     :url   pod base URL    :graph target named graph
     :json-write :json-read injected JSON fns (e.g. data.json)
     :grant {:cap :cap/read|:cap/transact :scope graph} (default read on :graph)
     :http-fn optional override (defaults to jvm-http-fn)"
  [{:keys [url graph json-write json-read token cacao did identity grant http-fn]}]
  (let [;; per-actor: the graph defaults to the actor's OWN key-derived IPNS
        ;; name (the actor owns it → self-mint is authorized by construction).
        graph (or graph (:graph identity))
        ;; self-mint a CACAO from the actor's own key when an identity is given
        [cacao did]
        (if identity
          (let [now (str (Instant/now))
                g   (or grant {:cap :cap/read :scope graph})]
            [(cacao/mint identity g {:aud url :nonce (str (UUID/randomUUID))
                                     :issued-at now
                                     :expiry (str (.plusSeconds (Instant/now) 3600))})
             (:did identity)])
          [cacao did])
        host-caps {:http-fn (or http-fn jvm-http-fn)
                   :json-write json-write :json-read json-read}
        api  (kdb/kotoba-api host-caps)
        conn (kdb/kotoba-conn url graph (cond-> {}
                                          token (assoc :token token)
                                          cacao (assoc :cacao cacao :did did)))]
    (store/->DatomicStore api conn)))
