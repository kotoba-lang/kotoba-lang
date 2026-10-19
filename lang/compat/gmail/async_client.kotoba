(ns gmail.async-client
  "Async (Promise-returning) request core for the cljs/nbb READ surface --
  the twin of gmail.client/request!, deliberately kept SEPARATE from it.

  WHY A SEPARATE CORE (see the ADR addendum for the full rationale):
  gmail.client/request! -- and every existing gmail.* namespace, plus
  com-cloudflare and com-wise, which all share the convention -- uses a
  SYNCHRONOUS :http-fn: `{:url :method :headers :body} -> {:status :body}`,
  a plain return value. Under nbb/cljs the natural transport is js/fetch,
  which is inherently async (it returns a Promise). Rather than break that
  synchronous contract -- a change that would ripple out to
  com-cloudflare/com-wise -- this is an ADDITIONAL, cljs-only, READ-ONLY
  surface that is async-native from day one: its :http-fn returns a
  js/Promise of `{:status :body}` (the SAME field shape as the sync
  contract, just wrapped in a Promise), and request! returns a js/Promise of
  the parsed JSON body. Living in its own namespace(s) means there is never
  ambiguity about which functions are sync (existing, JVM) vs async (these,
  cljs).

  Every function still takes an injectable :http-fn -- this library's one
  non-negotiable convention -- so the async surface is as stub-testable as
  the sync one (a stub returns `(js/Promise.resolve {:status .. :body ..})`).

  Reuses gmail.client/api-base (a plain, portable, non-reader-conditional
  `def`) rather than redefining the base URL."
  (:require [gmail.client :as client]
            [clojure.string :as str]))

(defn- auth-headers [token]
  {"Authorization" (str "Bearer " token)
   "Content-Type" "application/json"})

(defn- encode-query-value
  "Percent-encode a query-param VALUE, the cljs analog of
  gmail.client/encode-query-value: a space becomes %20 (NOT `+`), and `:`/`/`
  stay readable (Gmail's `from:`/`after:2024/01/01` syntax relies on them),
  while every other delimiter (`&`/`=`/`+`/`#`/non-ASCII) is escaped so it
  can't be misread as query-string syntax. Built on js/encodeURIComponent
  (which already maps space -> %20 and escapes `&`/`=`/`+`/`#`), then
  un-escaping the two chars it over-encodes (`:` and `/`) back to literal."
  [v]
  (-> (js/encodeURIComponent (str v))
      (str/replace "%3A" ":")
      (str/replace "%2F" "/")))

(defn fetch-http-fn
  "Default cljs transport: js/fetch wrapped to resolve to `{:status :body}`
  -- the SAME field shape the sync :http-fn returns, only inside a Promise.
  :body is the raw response TEXT; JSON parsing happens in request! (parallel
  to how the sync client parses in request!, not in the transport)."
  ([] (fetch-http-fn {}))
  ([_opts]
   (fn [{:keys [url method headers body]}]
     (-> (js/fetch url
                   (clj->js (cond-> {:method (str/upper-case (name method))
                                     :headers headers}
                              body (assoc :body body))))
         (.then (fn [resp]
                  (-> (.text resp)
                      (.then (fn [text] {:status (.-status resp) :body text})))))))))

(defn request!
  "Async counterpart to gmail.client/request!. Takes the same kind of opts
  map (:method :body :http-fn :token :query) but returns a js/Promise of the
  parsed JSON body (or nil for an empty body), REJECTING on a non-2xx with an
  error carrying the same `{:status :path :body}` data the sync client
  throws. This is a read-only surface, so callers pass :get.

  Token: unlike the sync client there is NO env fallback here (there is no
  portable GMAIL_ACCESS_TOKEN read across both nbb and the browser), so
  :token is required; its absence REJECTS the returned Promise rather than
  throwing synchronously, so a caller's single .catch handles auth and
  transport failures alike and request! always returns a Promise."
  ([path] (request! path {}))
  ([path {:keys [method body http-fn token query]
          :or {method :get}}]
   (let [http-fn (or http-fn (fetch-http-fn))]
     (if-not token
       (js/Promise.reject (ex-info "GMAIL_ACCESS_TOKEN token is required"
                                   {:path path}))
       (let [query-string (when (seq query)
                            (str "?" (str/join "&" (map (fn [[k v]] (str (name k) "=" (encode-query-value v))) query))))
             req (cond-> {:url (str client/api-base path query-string)
                          :method method
                          :headers (auth-headers token)}
                   body (assoc :body (js/JSON.stringify (clj->js body))))]
         (-> (http-fn req)
             (.then (fn [resp]
                      ;; parallel to the sync request!'s body: reject on
                      ;; non-2xx, else parse a non-empty body, else nil.
                      ;; Note: we RETURN `js/Promise.reject` rather than
                      ;; `(throw ...)` here on purpose -- under nbb/SCI a throw
                      ;; inside an interpreted `.then` gets wrapped in an
                      ;; sci/error that hides the original ex-data, so a caller
                      ;; couldn't read {:status :path :body}; returning a
                      ;; rejected promise carries the raw ex-info through intact
                      ;; (and behaves identically under a compiled cljs build).
                      (if-not (< (:status resp) 300)
                        (js/Promise.reject
                         (ex-info "Gmail API request failed"
                                  {:status (:status resp) :path path :body (:body resp)}))
                        (when (seq (:body resp))
                          (js->clj (js/JSON.parse (:body resp)) :keywordize-keys true)))))))))))
