(ns shinkansen.serve
  "The node:http transport for `shinkansen.host` — and the dev loop
  (maturity axis F, which had a contract and no server).

  Bytes in → `host/handle` → bytes out. Two things live here that the pure
  host cannot own:

    the reload stream   GET /__shinkansen/reload is a text/event-stream;
                        every open client gets `reload` when the ctx is
                        swapped. `host/reload-script` (injected only when
                        :dev? is set) listens and calls location.reload().
    the watch           `fs.watch` over :watch dirs, debounced; on change
                        `rebuild-fn` runs and must answer {:ok true :ctx …}
                        or {:ok false :problems …}. A failed rebuild keeps
                        the LAST GOOD ctx serving and routes every GET to an
                        error document naming the problems (dev.cljc:
                        error-as-content, never a silent 500). The stream
                        fires either way, so the browser shows the error.

  Every save is a new snapshot — a new head to look at — which is why
  live-reload is the whole HMR story here (SPEC §2.5): there is no module
  to hot-swap, only a new document CID.

  Run programmatically: (start {:ctx … :port 0 :watch [\"src\"] :rebuild-fn f})
  → a promise of {:server :port :stop :swap!}."
  (:require [clojure.string :as str]
            [shinkansen.host :as host]
            ["node:http" :as http]
            ["node:fs" :as fs]))

(defn- read-body [^js req]
  (js/Promise.
   (fn [resolve _]
     (let [chunks (atom [])]
       (.on req "data" (fn [c] (swap! chunks conj c)))
       (.on req "end" (fn [] (resolve (.toString (js/Buffer.concat (clj->js @chunks)) "utf8"))))
       (.on req "error" (fn [_] (resolve "")))))))

(defn- parse-json [s]
  (when-not (str/blank? s)
    (try (js->clj (js/JSON.parse s) :keywordize-keys true)
         (catch :default _ ::invalid))))

(defn- headers->clj [^js req]
  (js->clj (.-headers req)))

(defn- write-response [^js res {:keys [status headers body]}]
  (.writeHead res status (clj->js headers))
  (.end res (str body)))

(defn- error-ctx
  "The ctx a failed rebuild serves: the last good tree, every leaf replaced
  by the error document, so the browser shows WHY."
  [ctx problems]
  (let [html (:html (host/error-document {:path "(rebuild)" :reason :rebuild-failed
                                          :detail (pr-str problems)}))]
    (assoc ctx :documents (into {} (for [[k _] (:documents ctx)] [k {:html html :mode :ssg :status 500}])))))

(defn start
  "Start serving. Returns a promise of
     {:server <http.Server> :port n :stop (fn []) :swap! (fn [ctx])}
  `:swap!` replaces the served ctx and fires the reload stream — the same
  thing the watch does, callable by a test or a build script."
  [{:keys [ctx port host watch rebuild-fn debounce-ms]
    :or {port 0 host "127.0.0.1" debounce-ms 80}}]
  (let [current (atom ctx)
        clients (atom #{})
        broadcast! (fn [msg]
                     (doseq [^js res @clients]
                       (try (.write res (str "data: " msg "\n\n")) (catch :default _ nil))))
        swap-ctx! (fn [new-ctx] (reset! current new-ctx) (broadcast! "reload") new-ctx)
        rebuild! (fn []
                   (when rebuild-fn
                     (let [r (try (rebuild-fn @current)
                                  (catch :default e {:ok false :problems [{:reason :rebuild-threw
                                                                           :error (str (ex-message e))}]}))]
                       (if (:ok r)
                         (swap-ctx! (assoc (:ctx r) :dev? true))
                         (swap-ctx! (error-ctx @current (:problems r)))))))
        timer (atom nil)
        on-change (fn [_ _]
                    (when-let [t @timer] (js/clearTimeout t))
                    (reset! timer (js/setTimeout rebuild! debounce-ms)))
        watchers (atom [])
        server (http/createServer
                (fn [^js req ^js res]
                  (let [url (.-url req)]
                    (if (= url "/__shinkansen/reload")
                      (do (.writeHead res 200 #js {"content-type" "text/event-stream"
                                                   "cache-control" "no-cache"
                                                   "connection" "keep-alive"})
                          (.write res ": open\n\n")
                          (swap! clients conj res)
                          (.on req "close" (fn [] (swap! clients disj res))))
                      (-> (read-body req)
                          (.then (fn [raw]
                                   (let [body (parse-json raw)]
                                     (if (= ::invalid body)
                                       (write-response res {:status 400
                                                            :headers {"content-type" "application/json"}
                                                            :body (host/->json {:ok false :reason :body-not-json})})
                                       (write-response res (host/handle {:method (.-method req)
                                                                         :url url
                                                                         :headers (headers->clj req)
                                                                         :body body}
                                                                        @current))))))
                          (.catch (fn [e]
                                    (write-response res {:status 500
                                                         :headers {"content-type" "application/json"}
                                                         :body (host/->json {:ok false :reason :handler-threw
                                                                             :error (str (ex-message e))})}))))))))]
    (js/Promise.
     (fn [resolve reject]
       (.on server "error" reject)
       (.listen server port host
                (fn []
                  (doseq [dir (or watch [])]
                    (try (swap! watchers conj (fs/watch dir #js {:recursive true} on-change))
                         (catch :default e (js/console.error (str "watch refused for " dir ": " (ex-message e))))))
                  (resolve {:server server
                            :port (.-port (.address server))
                            :swap! swap-ctx!
                            :rebuild! rebuild!
                            :stop (fn []
                                    (doseq [^js w @watchers] (.close w))
                                    (doseq [^js c @clients] (try (.end c) (catch :default _ nil)))
                                    (.close server))})))))))
