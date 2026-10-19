(ns filecoin.rpc
  "Lotus JSON-RPC: the request bodies, and the shapes that come back.

  Every Lotus node — and every hosted endpoint that fronts one — speaks
  JSON-RPC 2.0 at `/rpc/v1` with methods in the `Filecoin.` namespace. This
  namespace builds those requests as data and parses the replies; the
  transport is `filecoin.protocols/IHttp`, supplied by the host.

  Two conversions live here and nowhere else, because they are JSON's
  problem rather than the chain's:

  - **Message JSON is not message CBOR.** The RPC form is an object with
    capitalised keys, amounts as decimal *strings* (JSON numbers cannot hold
    attoFIL) and `Params` as base64. The CBOR form is a 10-element array.
    Neither is derivable from the other by renaming, so both are written out.
  - **`Params` is base64**, standard alphabet with padding, because that is
    how Go's `encoding/json` marshals a `[]byte`.

  Read-only calls need no key. `mpool-push` does — but the signing happens
  outside this namespace (`filecoin.protocols/ISigner`), so nothing here
  ever holds one."
  (:require [kotoba.lang.text :as str]
            [filecoin.address :as addr]
            [filecoin.message :as msg]
            [json.core :as json]))

(def ^:const default-path "/rpc/v1")

(def endpoints
  "Public gateways, for the read-only calls. Neither accepts `MpoolPush`
  without an API token."
  {:mainnet "https://api.node.glif.io/rpc/v1"
   :calibration "https://api.calibration.node.glif.io/rpc/v1"})

;; ── base64 (RFC 4648, standard alphabet, padded) ─────────────────────────────
;; Small enough to carry, and there is no base64 anywhere in this workspace
;; to borrow — `multiformats.core` has base32 and base58 only.

(def ^:private b64-alphabet
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")
(def ^:private b64-idx
  (into {} (map-indexed (fn [i c] [c i]) b64-alphabet)))

(defn- ->ints [data]
  (cond (nil? data) []
        (vector? data) data
        :else (mapv #(bit-and (int %) 0xff) (seq data))))

(defn base64
  "Bytes → a padded standard-alphabet base64 string."
  [data]
  (let [bs (->ints data)]
    (apply str
           (mapcat (fn [chunk]
                     (let [n (count chunk)
                           [a b c] (concat chunk [0 0])
                           v (+ (* a 65536) (* b 256) c)
                           chars [(nth b64-alphabet (quot v 262144))
                                  (nth b64-alphabet (mod (quot v 4096) 64))
                                  (nth b64-alphabet (mod (quot v 64) 64))
                                  (nth b64-alphabet (mod v 64))]]
                       (case n
                         1 [(nth chars 0) (nth chars 1) \= \=]
                         2 [(nth chars 0) (nth chars 1) (nth chars 2) \=]
                         chars)))
                   (partition-all 3 bs)))))

(defn base64-decode
  "A base64 string → a vector of ints."
  [s]
  (let [clean (str/replace s "=" "")]
    (loop [cs (seq clean) buf 0 bits 0 out []]
      (if (empty? cs)
        out
        (let [idx (or (b64-idx (first cs))
                      (throw (ex-info "rpc: invalid base64 character"
                                      {:char (first cs)})))
              buf (+ (* buf 64) idx)
              bits (+ bits 6)]
          (if (>= bits 8)
            (let [keep (bit-shift-left 1 (- bits 8))]  ; bits < 14, so this
              (recur (rest cs)                          ; stays well inside 32
                     (mod buf keep)
                     (- bits 8)
                     (conj out (quot buf keep))))
            (recur (rest cs) buf bits out)))))))

;; ── message ⇄ JSON ───────────────────────────────────────────────────────────

(defn message->json
  "A message map → the object Lotus expects.

  Normalises unconditionally, for the same reason `filecoin.message/encode`
  does: deciding by the presence of `:to` gets it wrong for exactly the raw
  map a caller writes by hand, and then hands a *string* to `addr/to-string`.
  Lotus answers that with `unknown address protocol` from inside its
  unmarshaller, which reads as a malformed request rather than as an
  un-normalised one. Found by making a real call."
  [m]
  (let [m (msg/message m)]
    {"Version" (:version m)
     "To" (addr/to-string (:to m))
     "From" (addr/to-string (:from m))
     "Nonce" (:nonce m)
     "Value" (:value m)
     "GasLimit" (:gas-limit m)
     "GasFeeCap" (:gas-fee-cap m)
     "GasPremium" (:gas-premium m)
     "Method" (:method m)
     "Params" (if (seq (:params m)) (base64 (:params m)) nil)}))

(defn json->message
  "The inverse. Amounts stay decimal strings."
  [o]
  {:version (get o "Version" 0)
   :to (addr/from-string (get o "To"))
   :from (addr/from-string (get o "From"))
   :nonce (get o "Nonce" 0)
   :value (str (get o "Value" "0"))
   :gas-limit (get o "GasLimit" 0)
   :gas-fee-cap (str (get o "GasFeeCap" "0"))
   :gas-premium (str (get o "GasPremium" "0"))
   :method (get o "Method" 0)
   :params (if-let [p (get o "Params")] (base64-decode p) [])})

(defn signature->json [sig]
  {"Type" (:type sig) "Data" (base64 (:data sig))})

(defn signed-message->json [sm]
  {"Message" (message->json (:message sm))
   "Signature" (signature->json (:signature sm))})

;; ── requests ─────────────────────────────────────────────────────────────────

(defn request
  "A JSON-RPC 2.0 request body as data. `id` defaults to 1: this library
  issues one call per request, so a monotonic counter would be ceremony."
  ([method params] (request method params 1))
  ([method params id]
   {"jsonrpc" "2.0" "method" method "params" (vec params) "id" id}))

(defn chain-head [] (request "Filecoin.ChainHead" []))
(defn state-network-name [] (request "Filecoin.StateNetworkName" []))
(defn chain-get-message [cid] (request "Filecoin.ChainGetMessage" [{"/" cid}]))
(defn chain-get-tipset [tsk] (request "Filecoin.ChainGetTipSet" [tsk]))

(defn wallet-balance [address]
  (request "Filecoin.WalletBalance" [(if (string? address) address (addr/to-string address))]))

(defn mpool-get-nonce [address]
  (request "Filecoin.MpoolGetNonce" [(if (string? address) address (addr/to-string address))]))

(defn state-get-actor
  "The actor's state head, nonce and balance at `tipset` (nil = chain head)."
  ([address] (state-get-actor address nil))
  ([address tipset]
   (request "Filecoin.StateGetActor"
            [(if (string? address) address (addr/to-string address)) tipset])))

(defn state-lookup-id
  "The `f0` form of a robust address."
  ([address] (state-lookup-id address nil))
  ([address tipset]
   (request "Filecoin.StateLookupID"
            [(if (string? address) address (addr/to-string address)) tipset])))

(defn state-account-key
  "The public-key address (`f1`/`f3`) behind an `f0`."
  ([address] (state-account-key address nil))
  ([address tipset]
   (request "Filecoin.StateAccountKey"
            [(if (string? address) address (addr/to-string address)) tipset])))

(defn gas-estimate-message-gas
  "Fill in gas-limit, fee-cap and premium. `max-fee` is a decimal attoFIL
  string, or nil for the node's default."
  ([message] (gas-estimate-message-gas message nil nil))
  ([message max-fee tipset]
   (request "Filecoin.GasEstimateMessageGas"
            [(message->json message)
             (when max-fee {"MaxFee" max-fee})
             tipset])))

(defn mpool-push
  "Submit a signed message. Returns its CID."
  [signed]
  (request "Filecoin.MpoolPush" [(signed-message->json signed)]))

(defn state-wait-msg
  "Block until a message appears on chain with `confidence` tipsets after it."
  ([cid] (state-wait-msg cid 1))
  ([cid confidence] (request "Filecoin.StateWaitMsg" [{"/" cid} confidence nil true])))

;; ── calling ──────────────────────────────────────────────────────────────────

(defn http-request
  "The `IHttp` request map for a JSON-RPC body."
  ([url body] (http-request url body nil))
  ([url body auth-token]
   {:method :post
    :url url
    :headers (cond-> {"content-type" "application/json"}
               auth-token (assoc "authorization" (str "Bearer " auth-token)))
    :body (json/encode body)}))

(defn parse-response
  "A JSON-RPC response body → its `result`. Throws on an `error` member,
  because a JSON-RPC error arrives with HTTP 200 and a caller that only
  checks the status code will read `nil` as an answer."
  [body]
  (let [o (if (string? body) (json/decode body) body)]
    (when-let [err (get o "error")]
      (throw (ex-info (str "filecoin rpc: " (get err "message"))
                      {:code (get err "code") :error err})))
    (get o "result")))
