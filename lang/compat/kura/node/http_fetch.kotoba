(ns kura.node.http-fetch
  "`IHttp` over the global `fetch` — Node 18+, a Cloudflare Worker, a browser.

  Split out of `kura.node.host-node` when the Worker build failed on that
  namespace's `node:crypto` import: the transport was never Node-specific,
  only the crypto next to it was. Nothing here touches a platform builtin.

  `redirect: manual` is not a preference. A redirect that the client follows
  arrives at the new URL carrying a signature computed over the old one, so it
  fails authentication in a way that reads like a credential problem. Better
  to see the 3xx."
  (:require [kura.node.s3 :as s3]))

(defn- explain
  "Re-throw a fetch failure with its cause attached to the message.

  `fetch failed` is undici's outer message and says nothing; the reason lives in
  `.cause` and nothing was reading it. A whole ingest was mis-diagnosed twice —
  flaky source, then flaky network — because every layer above only ever saw
  those two words. A handler that discards `.cause` on a wrapped error discards
  the diagnosis."
  [method url e]
  (let [c (.-cause ^js e)
        detail (when c (str " (" (or (.-code ^js c) (.-name ^js c)) ": "
                            (.-message ^js c) ")"))]
    (throw (doto (js/Error. (str (-> method name .toUpperCase) " " url " — "
                                 (.-message ^js e) detail))
             (aset "cause" (or c e))))))

(def retriable-causes
  "Transport failures worth trying again on a fresh connection.

  `UND_ERR_SOCKET: other side closed` is the one that mattered here: Backblaze
  B2 closes a keep-alive socket after a while, undici hands the dead one back
  out of its pool, and the next PUT dies on it. It is not a server rejection —
  the request never arrived — so retrying is correct and gets a new socket.

  **Only transport failures.** An HTTP status is a decision the server made and
  repeating the request will get the same decision, so 4xx and 5xx are returned
  to the caller, not retried here."
  #{"UND_ERR_SOCKET" "ECONNRESET" "ECONNREFUSED" "EPIPE" "ETIMEDOUT"
    "UND_ERR_HEADERS_TIMEOUT" "UND_ERR_BODY_TIMEOUT" "UND_ERR_CONNECT_TIMEOUT"})

(def ^:private max-attempts
  "Three, and the reason to keep it small is that this only helps a dead socket.
  If a fresh connection also fails, the problem is not the connection."
  3)

(defn- retriable? [e]
  (let [c (.-cause ^js e)]
    (boolean (and c (contains? retriable-causes (.-code ^js c))))))

(defn- delay> [ms] (js/Promise. (fn [res] (js/setTimeout res ms))))

(defn- fetch-once>
  "One attempt. Separate from the protocol method so retry can wrap it without
  widening `s3/IHttp` — every fake in every test implements that protocol, and a
  transport detail is no reason to make them all implement one more thing."
  [{:keys [method url headers body]}]
  (-> (js/fetch url
                (clj->js (cond-> {:method (-> method name .toUpperCase)
                                  :headers (or headers {})
                                  :redirect "manual"}
                           (and body (pos? (.-length ^js body)))
                           (assoc :body body))))
      (.then (fn [resp]
               (-> (.arrayBuffer ^js resp)
                   (.then (fn [ab]
                            {:status (.-status ^js resp)
                             ;; Object.fromEntries rather than the Headers
                             ;; iterator: `es6-iterator-seq` is a
                             ;; ClojureScript-compiler builtin that nbb does
                             ;; not carry, and this runs on both.
                             :headers (into {}
                                            (map (fn [[k v]] [(.toLowerCase k) v]))
                                            (js->clj (js/Object.fromEntries
                                                      (.entries (.-headers ^js resp)))))
                             :body (js/Uint8Array. ab)})))))
      (.catch (fn [e] (explain method url e)))))

(defrecord FetchHttp []
  s3/IHttp
  (-request [_ req]
    ;; Retry lives at the transport, because a closed socket is a transport
    ;; fact. An earlier attempt wrapped retry around the whole object write,
    ;; which re-encoded and re-sent every shard to work around one dead
    ;; connection — and failed four times doing it, because the object was never
    ;; the problem.
    (letfn [(go [n]
              (-> (fetch-once> req)
                  (.catch (fn [e]
                            (if (and (< n max-attempts) (retriable? e))
                              (-> (delay> (* 150 n)) (.then (fn [_] (go (inc n)))))
                              (throw e))))))]
      (go 1))))

(defn fetch-http [] (->FetchHttp))

(defn now-iso
  "Current instant as ISO-8601. Passed to the signer explicitly, because
  `sigv4` never reads a clock and inheriting that keeps a signing test a pure
  function of its inputs."
  []
  (.toISOString (js/Date.)))
