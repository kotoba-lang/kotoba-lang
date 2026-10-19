(ns kototama.browser
  "Browser (ClojureScript) entry — proves a kototama UNSPSC actor runs its ReAct
  loop in a browser tab. Same .cljc actor code as JVM/SCI; the taxonomy EDN is
  fetched and injected via set-table! (the cljs data path)."
  (:require [cljs.reader :as reader]
            [langgraph.graph :as g]
            [langchain.model :as lcm]
            [langchain.message :as msg]
            [langchain.db :as db]
            [langchain.kotoba-db :as kdb]
            [kotoba.lang.text :as str]
            [kototama.unspsc.taxonomy :as tax]
            [kototama.unspsc.capability :as cap]
            [kototama.unspsc.react :as react]))

(defn log! [s]
  (when-let [el (.getElementById js/document "out")]
    (set! (.-textContent el) (str (.-textContent el) s "\n")))
  (js/console.log s))

(defn run-demo [taxon]
  (log! (str "\n▶ actor " (:code taxon) " — " (:title taxon)))
  (log! (str "  DID " (:did taxon)))
  ;; deterministic mock model drives the ReAct loop (no network in the demo)
  (let [model  (lcm/mock-model
                [(msg/ai "" {:tool-calls [{:id "t1" :name "validate_line"
                                           :input {:line {:quantity 1 :unit "head"}}}]})
                 (msg/ai "Verdict: NOT-OK — missing animal_health_certificate.")])
        actor  (react/react-actor taxon model)
        result (g/invoke actor {:messages [(msg/user "validate: quantity 1 head")]} {})
        messages (:messages result)
        tool-ran (boolean (some #(= :tool (:role %)) messages))
        turns    (count (filter #(= :assistant (:role %)) messages))]
    (log! (str "  ReAct loop: " (count messages) " messages, "
               turns " assistant turns, tool executed=" tool-ran))
    (log! (str "  final: " (msg/text (msg/last-message messages))))
    (let [v (cap/run taxon {:quantity 1 :unit "head"})]
      (log! (str "  capability verdict ok?=" (:ok v)
                 " missing=" (vec (:missing v)))))))

(def ^:private schema
  {:unspsc/code    {:db/unique :db.unique/identity}
   :unspsc/title   {} :unspsc/segment {} :unspsc/did {} :unspsc/verdict {}})

(defn datomic-query-demo
  "kotoba browser datomic query — build a Datom store IN THE BROWSER, transact every
  actor's state as datoms, then run client-side datalog (q/pull). No server."
  []
  (log! "\n── kotoba browser datomic query (client-side datalog, no server) ──")
  (let [conn (db/create-conn schema)]
    (doseq [code (tax/codes)]
      (let [t (tax/taxon code)
            v (cap/run t {:quantity 1 :unit "ea"})]
        (db/transact! conn [{:unspsc/code (:code t) :unspsc/title (:title t)
                             :unspsc/segment (:segment t) :unspsc/did (:did t)
                             :unspsc/verdict (if (:ok v) "ok" "not-ok")}])))
    (let [dbv (db/db conn)
          total (count (db/q '[:find [?c ...] :where [?e :unspsc/code ?c]] dbv))
          seg10 (db/q '[:find [?c ...] :where [?e :unspsc/segment "10"] [?e :unspsc/code ?c]] dbv)
          ;; a JOIN query: titles of not-ok actors
          notok (db/q '[:find [?title ...]
                        :where [?e :unspsc/verdict "not-ok"] [?e :unspsc/title ?title]] dbv)
          one (db/pull dbv '[*] [:unspsc/code "10101500"])]
      (log! (str "  transacted " total " actors into the in-browser Datom store"))
      (log! (str "  q [:find ?c :where [?e :unspsc/segment \"10\"]] → " (vec (sort seg10))))
      (log! (str "  q join (verdict=not-ok titles) → " (vec (sort notok))))
      (log! (str "  pull [:unspsc/code \"10101500\"] → "
                 (select-keys one [:unspsc/title :unspsc/did :unspsc/verdict]))))))

;; ── browser → LIVE kotoba node datomic query (via same-origin /xrpc proxy) ──

(def ^:private live-graph-cid
  "p00 fleet graph (500 actors) persisted on the live kotoba node."
  "bafyreiduja7hxnsey63g3pausypbakdbnkfii3imazoj2ragroy4vgs4fi")

(defn- sync-xhr
  "Synchronous XHR — satisfies langchain.kotoba-db's sync http-fn contract in
  the browser. Same-origin (/xrpc/* → dev-proxy → live node)."
  [{:keys [url method headers body]}]
  (let [xhr (js/XMLHttpRequest.)]
    (.open xhr (str/upper (name method)) url false)
    (doseq [[k v] headers] (.setRequestHeader xhr k v))
    (.send xhr (or body ""))
    {:status (.-status xhr) :body (.-responseText xhr)}))

(def ^:private host-caps
  {:http-fn sync-xhr
   :json-write (fn [m] (js/JSON.stringify (clj->js m)))
   :json-read  (fn [s] (js->clj (js/JSON.parse s) :keywordize-keys true))})

(defn live-query-demo
  "Query the LIVE kotoba Datom graph (the 18,342-actor fleet) FROM THE BROWSER
  over the kotoba XRPC datomic.q surface — same `q` datalog API as client-side."
  []
  (log! "\n── browser → LIVE kotoba node datomic query (XRPC datomic.q) ──")
  (try
    (let [conn (kdb/kotoba-conn "" live-graph-cid)      ; "" = same-origin /xrpc (proxy)
          {:keys [q]} (kdb/kotoba-api host-caps)
          codes (q '[:find [?c ...] :where [?e :unspsc/code ?c]] conn)
          one   (q '[:find [?t ?d] :where [?e :unspsc/code "10101500"]
                     [?e :unspsc/title ?t] [?e :unspsc/did ?d]] conn)]
      (log! (str "  live graph " (subs live-graph-cid 0 16) "… → " (count codes)
                 " actors persisted on the node (queried from the browser)"))
      (log! (str "  q actor 10101500 (LIVE) → " (vec one))))
    (catch :default e
      (log! (str "  live query unavailable (start dev-proxy.py): " e)))))

(defn init []
  (log! "kototama actor — running in the BROWSER (ClojureScript)")
  (-> (js/fetch "/taxonomy-sample.edn")
      (.then (fn [r] (.text r)))
      (.then (fn [text]
               (tax/set-table! (reader/read-string text))
               (log! (str "taxonomy loaded: " (tax/count-codes) " codes "
                          (vec (sort (tax/codes)))))
               (run-demo (tax/taxon "10101500"))   ; bespoke-derived (livestock)
               (run-demo (tax/taxon "11101503"))   ; Haiku-enriched (Barite)
               (datomic-query-demo)                ; client-side kotoba datomic query
               (live-query-demo)                   ; browser → LIVE node datomic query
               (log! "\n✓ DONE — ReAct loop + client + LIVE datomic query in the browser")))
      (.catch (fn [e] (log! (str "ERROR: " e))))))
