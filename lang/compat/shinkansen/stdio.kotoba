(ns shinkansen.stdio
  "MCP stdio server loop: reads JSON-RPC requests one per line from stdin,
  answers via shinkansen.mcp, prints one JSON response per line to stdout.

  The lake handlers are injected at startup — by default they hit the
  LIVE yataverse lake API (https://yataverse.com). Everything here is
  Node-side IO; the tool logic itself stays in shinkansen.mcp (pure).

  Run:  nbb -m shinkansen.stdio"
  (:require [clojure.string :as str]
            [shinkansen.mcp :as mcp]))

(def base-url (or (.-SHINKANSEN_LAKE_URL js/process.env)
                 "https://yataverse.com"))

(defn- http-get-json
  "One GET returning parsed JSON, or {:ok false :error …} on failure.
  Never throws: a tool answer must always be shapeable."
  [url]
  (js/Promise.
   (fn [resolve _]
     (try
       (-> (js/fetch url)
           (.then (fn [resp]
                    (if (.-ok resp)
                      (-> (.json resp)
                          (.then (fn [j] (resolve {:ok true :status (.-status resp)
                                                   :body (js->clj j :keywordize-keys true)}))))
                      (resolve {:ok false :status (.-status resp)
                                :error (str "HTTP " (.-status resp))}))))
           (.catch (fn [e] (resolve {:ok false :error (.-message e)}))))
       (catch :default e
         (resolve {:ok false :error (.-message e)}))))))

(defn- http-get-text
  "One GET returning text (for /ipfs/{cid} block fetches)."
  [url]
  (js/Promise.
   (fn [resolve _]
     (-> (js/fetch url)
         (.then (fn [resp]
                  (-> (.text resp)
                      (.then (fn [t] (resolve {:ok (.-ok resp) :status (.-status resp)
                                               :text t}))))))
         (.catch (fn [e] (resolve {:ok false :error (.-message e)})))))))

(defn make-handlers
  "Live lake handlers bound to a base URL. Shapes match shinkansen.mcp/handle-call."
  [base]
  {:lake-list (fn [{:keys [limit cursor]}]
                 (let [q (cond-> (str "/api/v1/lake/blocks?limit=" (or limit 50))
                           cursor (str "&cursor=" (js/encodeURIComponent (str cursor))))]
                   (http-get-json (str (str/replace base #"/*$" "") q))))
   :lake-head (fn [_] (http-get-json (str (str/replace base #"/*$" "") "/api/v1/lake/head")))
   :lake-fetch (fn [{:keys [cid format]}]
                 (if (= format "text")
                   (http-get-text (str "https://" cid ".ipfs.yataverse.com/"))
                   (js/Promise.resolve {:ok true :cid cid
                                         :gateway (str "https://" cid ".ipfs.yataverse.com/")})))
   :lake-dispatch (fn [envelope]
                    ;; No app is attached in R0 — the dispatch tool is declared
                    ;; and honestly answers not-implemented (ADR: declared but
                    ;; unimplemented must be visible, not silent). An attached
                    ;; app answers (invoke/dispatch decl envelope {…:authorize-fn})
                    ;; and nothing else: without an authorizer bound, invoke
                    ;; refuses :no-authorizer — it never appends because the
                    ;; call arrived.
                    (js/Promise.resolve {:ok false :error "no app attached yet (R0)"
                                         :envelope (dissoc envelope :grant)}))})

(defn- write-json [obj]
  (println (js/JSON.stringify (clj->js obj))))

(defn serve
  "Blocking stdio loop. Answers line by line; never exits on a bad line."
  []
  (let [readline (js/require "node:readline")
        rl (.createInterface readline #js {:input (.-stdin js/process)
                                           :crlf false :terminal false})
        ctx {:base-url base-url :handlers (make-handlers base-url)}]
    (.on rl "line"
         (fn [line]
           (let [s (str/trim line)]
             (when-not (str/blank? s)
               (try
                 (let [req (js->clj (js/JSON.parse s) :keywordize-keys true)
                       resp (mcp/handle-request req ctx)]
                   (if (instance? js/Promise resp)
                     (.then resp write-json)
                     (write-json resp)))
                 (catch :default e
                   (write-json {:jsonrpc "2.0" :id nil
                                :error {:code -32700 :message (str "parse error: " (.-message e))}})))))))
    (println (str "shinkansen stdio ready; lake=" base-url))
    rl))

(defn -main [& _args]
  (serve)
  nil)
