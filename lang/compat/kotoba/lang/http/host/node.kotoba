(ns kotoba.lang.http.host.node
  "Node host adapter for OUTBOUND HTTP over `node:http` / `node:https`.

  The runtime this workspace prefers. CLAUDE.md orders them
  `kotoba wasm > clojurewasm > ClojureScript > nbb`, with the JVM below that,
  and until this existed the only client hosts here were `host/jvm`
  (java.net.http) and `host/babashka` (a third-party client). Outbound HTTP on
  the nbb path had to reach for one of them, or shell out to `curl` -- which
  is a process spawn, a strictly WIDER authority than the network read it was
  standing in for.

  ## Why not `fetch`

  It is in Node and in nbb, and it is one line. It also gives a different
  answer. Measured 2026-09-09 against a server sending `X-Multi` twice:

      fetch  Headers.get(\"x-multi\")   -> \"first, second\"
      node   res.headers[\"x-multi\"]   -> \"first, second\"
      node   res.rawHeaders            -> [\"X-Multi\" \"first\"] [\"X-Multi\" \"second\"]

  `host/jvm` answers `\"first\"`. A joined string is a THIRD answer -- neither
  the first value nor the list -- and ACME reads `[\"replay-nonce\"]` and
  `[\"location\"]` positionally. Two hosts under one contract that disagree
  about a header is the kind of difference that shows up as a failed protocol
  handshake and not as a failed test, so this reads `rawHeaders` and folds them
  through the same `kotoba.lang.http/fold-headers` the JVM host uses.

  ## Why `send` returns a promise, and there is no `IHttp` here

  `IHttp/send` says \"Perform `req`, return a response map.\" A promise is not a
  response map, and putting both under one name would be a contract that means
  two things depending on who is holding it. So this host exposes
  `http-transport` and does not reify the protocol.

  That is a property of the runtime rather than a gap here: **Node has no
  in-process synchronous HTTP.** The only synchronous route is spawning a
  process, and `process/spawn` is a wider authority than `http/fetch` -- the
  thing a capability-confined design exists to avoid granting by accident. An
  async `IHttp` is a decision for this repo to take deliberately, with one
  protocol that says so, and not something a host should imply by returning a
  different type from the one the docstring promises."
  (:require [kotoba.lang.http :as http]
            ["node:http" :as node-http]
            ["node:https" :as node-https]
            ["node:url" :refer [URL]]))

(def ^:private methods-with-body
  "POST, PUT and PATCH carry a body; GET, DELETE and HEAD do not. Listed so an
   unknown method cannot fall into either branch by accident."
  #{:post :put :patch})

(defn- method->name [method]
  (let [k (keyword (if (string? method) (.toLowerCase method) (name method)))]
    (case k
      :get "GET" :post "POST" :put "PUT" :patch "PATCH"
      :delete "DELETE" :head "HEAD" :options "OPTIONS"
      (throw (ex-info "kotoba.lang.http.host.node: unsupported HTTP method"
                      {:method method})))))

(def ^:private max-redirects
  "`host/jvm` builds its client with `Redirect/NORMAL`, so it follows. Node does
   not, and a host that silently stopped following would hand a 302 to a caller
   that had never had to think about one. Bounded, because a server can point at
   itself forever."
  5)

(defn- raw-header-pairs
  "`rawHeaders` is a flat alternating list. Pair it up, preserving each
   occurrence -- that is the whole reason this host reads it."
  [raw]
  (partition 2 (array-seq raw)))

(defn- redirect-target
  "The URL to follow to, or nil. Returns `::downgrade` for HTTPS -> HTTP, which
   `Redirect/NORMAL` refuses and so does this."
  [status headers from-url]
  (when (and (#{301 302 303 307 308} status) (get headers "location"))
    (let [to (URL. (get headers "location") from-url)]
      (if (and (= "https:" (.-protocol (URL. from-url)))
               (= "http:" (.-protocol to)))
        ::downgrade
        (.toString to)))))

(defn- follow-method
  "303 becomes GET, and 301/302 do too for anything that is not GET/HEAD --
   which is what every client on the public internet actually does. 307 and 308
   preserve the method, which is the reason they exist."
  [status method]
  (cond
    (= 303 status) :get
    (and (#{301 302} status) (not (#{:get :head} (keyword (name method))))) :get
    :else method))

(defn- request-once
  "One request, no redirect handling. Resolves {:status :headers :body}."
  [{:keys [url method headers body timeout-ms]}]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [u (URL. url)
             secure? (= "https:" (.-protocol u))
             impl (if secure? node-https node-http)
             opts #js {:method (method->name (or method :get))
                       :headers (clj->js (into {} (map (fn [[k v]]
                                                         [(if (keyword? k) (name k) (str k))
                                                          (str v)]))
                                               headers))}
             req (.request impl u opts
                           (fn [res]
                             (let [chunks (atom "")]
                               (.setEncoding res "utf8")
                               (.on res "data" #(swap! chunks str %))
                               (.on res "end"
                                    #(resolve {:status (.-statusCode res)
                                               :headers (http/fold-headers
                                                         (raw-header-pairs (.-rawHeaders res)))
                                               :body @chunks})))))]
         (.setTimeout req timeout-ms
                      (fn [] (.destroy req (ex-info "kotoba.lang.http.host.node: request timed out"
                                                    {:url url :timeout-ms timeout-ms}))))
         (.on req "error" reject)
         (when (contains? methods-with-body (keyword (name (or method :get))))
           (.write req (str (or body ""))))
         (.end req))
       (catch :default e (reject e))))))

(defn http-transport
  "Return a function of a request map, performing it over `node:http`.

   req  {:url string
         :method :get|:post|:put|:patch|:delete|:head|:options
         :headers {string-or-keyword value}   optional
         :body string}                        optional
   ->   Promise of {:status int :headers {lowercase-name value} :body string}

   The response shape is `host/jvm`'s, deliberately and to the letter. What
   differs is the promise, and only because the runtime leaves no choice."
  ([] (http-transport {}))
  ([{:keys [timeout-seconds]}]
   (fn send [{:keys [url method] :as req}]
     (let [secs (or (:timeout-seconds req) timeout-seconds 120)
           ;; Throw synchronously for an unsupported method, exactly as
           ;; host/jvm does: the caller finds out before anything is sent,
           ;; rather than through a rejected promise it might not be awaiting.
           _ (method->name (or method :get))]
       (letfn [(attempt [url method hops]
                 (-> (request-once (assoc req :url url :method method
                                          :timeout-ms (* 1000 secs)))
                     (.then (fn [{:keys [status headers] :as resp}]
                              (let [target (redirect-target status headers url)]
                                (cond
                                  (nil? target) resp
                                  (= ::downgrade target) resp
                                  (>= hops max-redirects)
                                  ;; Rejected, not thrown. Measured 2026-09-09:
                                  ;; a `throw` inside `.then` reaches an nbb
                                  ;; caller wrapped in SCI's own error, so
                                  ;; `ex-data` returns {:type :sci/error :line
                                  ;; ...} and the caller has to know to look in
                                  ;; `ex-cause` for the data this host meant to
                                  ;; hand it. Rejecting keeps the ex-info
                                  ;; intact, so the same catch works on either
                                  ;; runtime.
                                  (js/Promise.reject
                                   (ex-info "kotoba.lang.http.host.node: too many redirects"
                                            {:url url :max-redirects max-redirects}))
                                  :else (attempt target (follow-method status method) (inc hops))))))))]
         (attempt url (or method :get) 0))))))
