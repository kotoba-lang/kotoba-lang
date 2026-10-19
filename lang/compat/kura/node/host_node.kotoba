(ns kura.node.host-node
  "Concrete `IHttp` and `ICrypto` for a Node.js host.

  `kura.node.s3` does zero I/O and zero crypto so that request shaping stays a
  pure function testable without credentials. This is the other half: the
  place where the abstraction is cashed in for `node:crypto` and `fetch`.

  Deliberately `.cljs` and deliberately small. Everything host-specific in the
  storage node lives in this one file, which is what lets the rest of
  `kura-node` be portable `.cljc` and run identically under a JVM harness, a
  Worker, or here.

  **Buffers, not strings, on the crypto boundary.** SigV4's key-derivation
  ladder feeds each HMAC's *raw output* into the next one; hex-encoding
  between steps produces a signature that is wrong in a way no test vector
  catches until a real service returns 403. `-hmac-sha256` returns a Buffer
  and only `-hex` ever stringifies."
  (:require ["node:crypto" :as crypto]
            [kura.node.s3 :as s3]))

(defn- ->buf [x]
  (cond
    (string? x) (js/Buffer.from x "utf8")
    (instance? js/Uint8Array x) (js/Buffer.from x)
    :else x))

(defrecord NodeCrypto []
  s3/ICrypto
  (-sha256-hex [_ b]
    (-> (crypto/createHash "sha256") (.update (->buf b)) (.digest "hex")))
  (-hmac-sha256 [_ k m]
    ;; Returns raw bytes. Hex-encoding here would break the SigV4 ladder,
    ;; whose every step consumes the previous step's raw digest.
    (-> (crypto/createHmac "sha256" (->buf k)) (.update (->buf m)) (.digest)))
  (-hex [_ b] (.toString (->buf b) "hex"))
  (-utf8 [_ s] (js/Buffer.from s "utf8")))

(defn node-crypto [] (->NodeCrypto))

(defrecord NodeHttp [opts]
  s3/IHttp
  (-request [_ {:keys [method url headers body]}]
    ;; Synchronous-looking callers are not supported: this returns a promise
    ;; and `kura.node.s3` is written against a synchronous protocol, so a Node
    ;; deployment drives it through the async runner below rather than calling
    ;; the store's methods directly. Saying so here rather than papering over
    ;; it with a blocking shim — there is no way to block on a promise in
    ;; Node, and a shim that pretends otherwise deadlocks.
    (-> (js/fetch url
                  (clj->js (cond-> {:method (-> method name .toUpperCase)
                                    :headers headers
                                    :redirect "manual"}
                             (and body (pos? (.-length body)))
                             (assoc :body body))))
        (.then (fn [resp]
                 (-> (.arrayBuffer resp)
                     (.then (fn [ab]
                              {:status (.-status resp)
                               ;; Object.fromEntries rather than iterating the
                               ;; Headers iterator: `es6-iterator-seq` is a
                               ;; ClojureScript-compiler builtin that nbb does
                               ;; not carry, and this file has to run on both.
                               :headers (into {}
                                              (map (fn [[k v]] [(.toLowerCase k) v]))
                                              (js->clj (js/Object.fromEntries
                                                        (.entries (.-headers resp)))))
                               :body (js/Uint8Array. ab)}))))))))

(defn node-http
  ([] (node-http {}))
  ([opts] (->NodeHttp opts)))

;; --- signing, exercised directly -------------------------------------------

(defn signed-request
  "Sign and send one S3 request, returning a promise of the response.

  The escape hatch for a caller that wants the adapter's signing without its
  synchronous store protocol — which is how the live conformance script drives
  a real bucket, and how anyone can check a signature against a service
  without standing up the whole node."
  [{:keys [http crypto key-id secret region service endpoint host bucket key
           method query body headers now-iso]
    :or {region "us-east-1" service "s3" method :get}}]
  (let [body (or body (js/Uint8Array. 0))
        path (s3/object-path bucket key)
        q (s3/canonical-query query)
        signed (s3/sign {:crypto crypto :key-id key-id :secret secret
                         :region region :service service :host host
                         :method method :path path :query q :body body
                         :now-iso (or now-iso (.toISOString (js/Date.)))})]
    (s3/-request http {:method method
                       :url (str endpoint path (when (seq q) (str "?" q)))
                       :headers (merge signed headers)
                       :body body})))

(defn now-iso [] (.toISOString (js/Date.)))
