(ns kagi.cacao
  "自己発行 CACAO(SIWE/EIP-4361 を Ed25519 did:key で署名、CBOR wire、base64)。
  `ai-gftd-itonami/src/itonami/cacao.clj` の **mint** 側を移植し、kagi では **verify** 側
  (CBOR decode + did:key→公開鍵復元 + Ed25519 検証)も実装する。actor は自分の鍵で graph の
  owner なので depth-1 self-mint が構造的に authorized(owner hand-off も共有 token も不要)。

  SIWE/wire ビルダは `kotoba.cacao` の byte-exact 純関数の写し(同期して保つ)。crypto は
  JDK Ed25519 + 最小 CBOR(definite-length)。"
  (:require [cacao.core :as shared]
            [clojure.string :as str]
            [ed25519.core :as ed25519])
  (:import [java.security Signature MessageDigest]
           [java.io ByteArrayOutputStream]
           [java.lang StringBuilder]
           [java.util Base64]
           [java.time Instant]))

;; ───────── canonical graph CID (mirror of kotobase.cid, via tsumugu port) ─────────
;; The kotobase.net edge does NOT address a tenant by its raw IPNS name — it
;; recomputes the graph handle as CIDv1/dag-cbor/sha2-256 of
;; "kotobase/db/<did>/<db-name>" and pins THAT into every write/read. Scope
;; kagi-sync CACAOs to this canonical graph, like the live tsumugu / app-aozora.

(def ^:private b32 "abcdefghijklmnopqrstuvwxyz234567")

(defn- sha256 ^bytes [^bytes data]
  (.digest (MessageDigest/getInstance "SHA-256") data))

(defn- base32-lower-no-pad
  "CIDv1 base32-lower, no padding. Ported from kotobase.cid (via tsumugu.cacao)."
  [^bytes data]
  (let [sb (StringBuilder.)
        {:keys [bits value]}
        (reduce
         (fn [{:keys [bits value]} b]
           (let [b (bit-and (int b) 0xff)
                 value (bit-or (bit-shift-left value 8) b)
                 bits (+ bits 8)]
             (loop [bits bits value value]
               (if (>= bits 5)
                 (do (.append sb (.charAt b32 (bit-and (unsigned-bit-shift-right value (- bits 5)) 31)))
                     (recur (- bits 5) value))
                 {:bits bits :value value}))))
         {:bits 0 :value 0}
         data)]
    (when (pos? bits)
      (.append sb (.charAt b32 (bit-and (bit-shift-left value (- 5 bits)) 31))))
    (.toString sb)))

(defn graph-cid-from-name
  "SHA-256(name) behind a CIDv1/dag-cbor/sha2-256 header (0x01 0x71 0x12 0x20),
  base32-lower 'b'. Ported from kotobase.cid/graph-cid-from-name."
  [^String name]
  (let [hash (sha256 (.getBytes name "UTF-8"))
        cid  (byte-array (concat [(unchecked-byte 0x01) (unchecked-byte 0x71)
                                  (unchecked-byte 0x12) (unchecked-byte 0x20)]
                                 (seq hash)))]
    (str "b" (base32-lower-no-pad cid))))

(defn canonical-graph
  "The deterministic graph CID the kotobase.net edge recomputes from did +
  db-name on every write/read. Scope kagi-sync CACAOs to THIS, not the IPNS name."
  [did db-name]
  (graph-cid-from-name (str "kotobase/db/" did "/" db-name)))

;; ───────── pure SIWE builders (mirror of kotoba.cacao / itonami) ─────────

(def ^:private cap->op {:cap/read "datom:read" :cap/transact "datom:transact" :cap/admin "tx:create"})

(defn grant->resources [{:keys [cap scope]}]
  ;; Every kotobase.net CACAO must carry the mandatory kotobase:pin capability
  ;; alongside the request op and graph scope (net-kotobase edge_cacao).
  ["kotoba://op/kotobase:pin"
   (str "kotoba://op/" (cap->op cap))
   (str "kotoba://graph/" scope)])

(defn grant->payload [grant {:keys [iss aud nonce issued-at expiry domain version statement]
                             :or {domain "com.junkawasaki.kagi" version "1"}}]
  {:iss iss :aud aud :issued-at issued-at :expiry expiry :nonce nonce
   :domain domain :statement statement :version version
   :resources (grant->resources grant)})

(defn- iss-address [iss] (last (str/split iss #":")))
(defn- iss-chain-id [iss]
  (if (str/starts-with? iss "did:key:") "1"
      (let [segs (str/split iss #":")]
        (if (>= (count segs) 2) (nth segs (- (count segs) 2)) "1"))))

(defn siwe-message [{:keys [iss aud issued-at expiry nonce domain statement version resources]}]
  (->> (concat
        [(str domain " wants you to sign in with your Ethereum account:") (iss-address iss) ""]
        (when statement [statement ""])
        [(str "URI: " aud) (str "Version: " version) (str "Chain ID: " (iss-chain-id iss))
         (str "Nonce: " nonce) (str "Issued At: " issued-at)]
        (when expiry [(str "Expiration Time: " expiry)])
        (when (seq resources) (cons "Resources:" (map #(str "- " %) resources))))
       (str/join "\n")))

(defn ->wire [payload sig-b64]
  {"h" {"t" "eip4361"}
   "p" (cond-> {"iss" (:iss payload) "aud" (:aud payload) "iat" (:issued-at payload)
                "nonce" (:nonce payload) "domain" (:domain payload)
                "version" (:version payload) "resources" (:resources payload)}
         (:expiry payload)    (assoc "exp" (:expiry payload))
         (:statement payload) (assoc "statement" (:statement payload)))
   "s" {"t" "EdDSA" "s" (or sig-b64 "")}})

;; ───────── minimal CBOR encode (definite-length) ─────────

(defn- cbor-head [^ByteArrayOutputStream o major n]
  (cond (< n 24)    (.write o (int (+ (bit-shift-left major 5) n)))
        (< n 256)   (do (.write o (int (+ (bit-shift-left major 5) 24))) (.write o (int n)))
        (< n 65536) (do (.write o (int (+ (bit-shift-left major 5) 25)))
                        (.write o (int (bit-and (unsigned-bit-shift-right n 8) 0xff)))
                        (.write o (int (bit-and n 0xff))))
        :else (throw (ex-info "cbor len too big" {:n n}))))

;; dag-cbor canonical map-key order: by UTF-8 byte length ascending, then
;; bytewise. The kotobase.net edge decodes with strict @ipld/dag-cbor, which
;; REJECTS non-canonical maps — so key order here is load-bearing for the live
;; edge even though it never affects the SIWE signature (that's over the message
;; string, not the CBOR envelope).
(defn- dag-cbor-key-order [ks]
  (sort-by (fn [k] (let [b (.getBytes ^String (name k) "UTF-8")]
                     [(alength b) (vec b)]))
           ks))

(defn- cbor-val [^ByteArrayOutputStream o v]
  (cond
    (string? v)     (let [b (.getBytes ^String v "UTF-8")] (cbor-head o 3 (alength b)) (.write o b 0 (alength b)))
    (map? v)        (do (cbor-head o 5 (count v))
                        (doseq [k (dag-cbor-key-order (keys v))]
                          (cbor-val o (name k)) (cbor-val o (get v k))))
    (sequential? v) (do (cbor-head o 4 (count v)) (doseq [x v] (cbor-val o x)))
    :else           (cbor-val o (str v))))

(defn- cbor-bytes ^bytes [v]
  (let [o (ByteArrayOutputStream.)] (cbor-val o v) (.toByteArray o)))

;; ───────── minimal CBOR decode (text/array/map; verify 用) ─────────

(defn- ub [^bytes b i] (bit-and (int (aget b i)) 0xff))

(defn- cbor-read
  "[value next-pos] を返す。text(3)/array(4)/map(5)、追加情報 0-23/24/25 のみ対応。"
  [^bytes b pos]
  (let [ib (ub b pos) major (bit-shift-right ib 5) info (bit-and ib 0x1f)
        [n p1] (cond (< info 24) [info (inc pos)]
                     (= info 24) [(ub b (inc pos)) (+ pos 2)]
                     (= info 25) [(bit-or (bit-shift-left (ub b (inc pos)) 8) (ub b (+ pos 2))) (+ pos 3)]
                     :else (throw (ex-info "cbor: unsupported head" {:info info})))]
    (case (int major)
      3 [(String. (java.util.Arrays/copyOfRange b p1 (+ p1 n)) "UTF-8") (+ p1 n)]
      4 (loop [i 0 p p1 acc []]
          (if (< i n) (let [[v np] (cbor-read b p)] (recur (inc i) np (conj acc v))) [acc p]))
      5 (loop [i 0 p p1 acc {}]
          (if (< i n)
            (let [[k p2] (cbor-read b p) [v p3] (cbor-read b p2)] (recur (inc i) p3 (assoc acc k v)))
            [acc p]))
      (throw (ex-info "cbor: unsupported major" {:major major})))))

;; ───────── did:key → public key ─────────

(defn did-key->public
  "did:key:z… (Ed25519) → java PublicKey. Delegates raw-key extraction to the
  canonical kotoba-lang/ed25519 implementation (base58btc decode + multicodec
  0xED01 verification + exact-length check), replacing this repo's own former
  copy-pasted decoder that never checked the multicodec byte-type."
  [iss]
  (ed25519/public-from-raw (ed25519/did-key->pubkey iss)))

;; ───────── Ed25519 ─────────

(defn- ed-sign ^bytes [priv ^bytes msg]
  (let [s (doto (Signature/getInstance "Ed25519") (.initSign priv))] (.update s msg) (.sign s)))

(defn- ed-verify? [pub ^bytes msg ^bytes sig]
  (let [v (doto (Signature/getInstance "Ed25519") (.initVerify pub))] (.update v msg) (.verify v sig)))

(defn- seed-bytes
  "The raw 32-byte Ed25519 seed, from either representation callers hold.

  This repo's identities carry a JCA `PrivateKey` (from `KeyPairGenerator`),
  whose PKCS#8 encoding ends with the seed; the shared CACAO library derives
  the issuer DID from the seed itself, so it needs the raw bytes. Anything
  already 32 bytes is passed through."
  ^bytes [priv]
  (cond
    (bytes? priv) (if (= 32 (alength ^bytes priv))
                    priv
                    (byte-array (take-last 32 (seq priv))))
    :else (byte-array (take-last 32 (seq (.getEncoded ^java.security.PrivateKey priv))))))

;; ───────── mint / verify ─────────

(defn mint
  "actor が自分の Ed25519 鍵で base64 cacao を自己発行する。
   identity: {:private-key :did}、grant: {:cap :scope}、opts: {:aud :nonce :issued-at :expiry}。"
  [{:keys [private-key did]} grant {:keys [aud nonce issued-at expiry]}]
  (let [payload (grant->payload grant {:iss did :aud aud :nonce nonce
                                       :issued-at issued-at :expiry expiry})
        msg     (siwe-message payload)
        sig     (ed-sign private-key (.getBytes ^String msg "UTF-8"))
        sig-b64 (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) sig)]
    (.encodeToString (Base64/getEncoder) (cbor-bytes (->wire payload sig-b64)))))

;; ── kotobase.net apex CACAO ─────────────────────────────────────────────────
;; The rules live in `cacao.core` (org-chainagnostic-cacao), NOT here. The
;; comment that used to sit at this spot described the pre-cutover pod and
;; asserted the apex wanted "NO h wire field" and a single resource; both were
;; false of the apex and kept `push`/`pull` returning 401 for months. Re-adding
;; a local description of the apex's rules is how that happens again — read
;; `cacao.core/mint-kotobase-apex`, which is tested against them.

(def kotobase-pin-resource
  "Re-exported for callers in this repo; the value is the shared library's."
  shared/kotobase-pin-capability)

(def kotobase-operator-did shared/kotobase-apex-aud)

(defn- ->kotobase-wire
  "The wire envelope the LIVE apex accepts.

  `h` is `{\"t\" \"caip122\"}`. An earlier comment here asserted the apex wanted
  NO `h` field at all; that was true of the pre-cutover pod and is why `kagi
  push` 401'd after the 2026-07-08 apex cutover. The reference is
  `kotoba-lang/kotobase-client`, which authenticates against this apex on
  every request today — when this file and that one disagree, that one is
  right, because it is the one being exercised."
  [payload sig-b64]
  {"h" {"t" "caip122"}
   "p" (cond-> {"iss" (:iss payload) "aud" (:aud payload) "iat" (:issued-at payload)
                "nonce" (:nonce payload) "domain" (:domain payload)
                "version" (:version payload) "resources" (:resources payload)}
         (:expiry payload) (assoc "exp" (:expiry payload)))
   "s" {"t" "EdDSA" "s" (or sig-b64 "")}})

(defn graph-scope
  "The `kotoba://graph/<issuer-did>` scope the apex requires.

  Delegates to the shared library. Kept as a name here because callers and
  tests in this repo refer to it."
  [did]
  (last (shared/kotobase-apex-resources did)))

(defn mint-kotobase
  "Mint a CACAO the live kotobase.net apex accepts.

  This used to be ~15 lines of local payload/wire/CBOR assembly, and all of
  it was wrong in three different ways at once — no graph scope, no `h`
  header, and an `iat` carrying nanoseconds that the apex's
  `parse-utc-seconds` cannot parse. Each produced the same opaque 401, so
  `push`/`pull` were dead and looked like a network problem
  (ADR-2607275000).

  It is now a call into `cacao.core/mint-kotobase-apex`, which owns those
  rules for every consumer and THROWS on a violation rather than handing back
  a token the apex will silently refuse. ADR-2607268000 is the standing rule
  that made this the fix rather than another patch to the copy.

  `id` is `{:private-key :did}`; `:private-key` may be the raw 32-byte seed or
  a JCA PrivateKey, since callers in this repo hold both."
  [{:keys [private-key did]} {:keys [aud nonce issued-at expiry op-caps]
                              :or {op-caps ["datom:read" "datom:transact" "tx:create"]}}]
  (let [seed (seed-bytes private-key)
        {:keys [cacao-b64 iss]} (shared/mint-kotobase-apex
                                 {:seed seed :aud aud :nonce nonce
                                  :iat issued-at :exp expiry :op-caps op-caps})]
    (when (and did (not= did iss))
      ;; The issuer is DERIVED from the seed by the shared library, never
      ;; passed in. If the caller's own :did disagrees, something is holding
      ;; the wrong key and a silent mismatch would surface as an unrelated
      ;; authorization failure much later.
      (throw (ex-info "cacao: identity :did does not match the seed's derived did:key"
                      {:cacao/rule :issuer-binding :expected did :derived iss})))
    cacao-b64))

(defn decode-wire
  "Decode a minted CACAO into its three wire parts, WITHOUT verifying.

  For inspection and for tests that assert the shape the live apex accepts.
  Verification is `verify`; this deliberately does not do it, so a test can
  assert what a REJECTED token looked like."
  [cacao-b64]
  (let [wire (first (cbor-read (.decode (Base64/getDecoder) ^String cacao-b64) 0))
        p    (get wire "p")]
    {:header-type (get-in wire ["h" "t"])
     :signature   (get-in wire ["s" "s"])
     :payload     {:iss (get p "iss") :aud (get p "aud") :issued-at (get p "iat")
                   :expiry (get p "exp") :nonce (get p "nonce")
                   :domain (get p "domain") :version (get p "version")
                   :statement (get p "statement") :resources (get p "resources")}}))

(defn verify
  "自己発行 cacao を検証 → {:ok? :iss :aud :resources :expired? :replay?}。SIWE を再構成し
   iss(did:key)から公開鍵を復元して Ed25519 署名を検証する。opts:
     :aud         — 照合する audience(省略可)
     :now         — 現在時刻 ISO 文字列(省略時は実時刻)。expiry を過ぎていれば reject
     :nonce-seen? — (fn [nonce] bool) 既出 nonce なら true → リプレイとして reject"
  ([cacao-b64] (verify cacao-b64 nil))
  ([cacao-b64 {:keys [aud now nonce-seen?]}]
   (try
     (let [wire (first (cbor-read (.decode (Base64/getDecoder) ^String cacao-b64) 0))
           p    (get wire "p")
           sigb (.decode (Base64/getUrlDecoder) ^String (get-in wire ["s" "s"]))
           payload {:iss (get p "iss") :aud (get p "aud") :issued-at (get p "iat")
                    :expiry (get p "exp") :nonce (get p "nonce") :domain (get p "domain")
                    :statement (get p "statement") :version (get p "version")
                    :resources (get p "resources")}
           msg  (siwe-message payload)
           pub  (did-key->public (:iss payload))
           sig-ok? (ed-verify? pub (.getBytes ^String msg "UTF-8") sigb)
           aud-ok? (or (nil? aud) (= aud (:aud payload)))
           exp-ok? (if-let [exp (:expiry payload)]
                     (let [now* (if now (Instant/parse now) (Instant/now))]
                       (not (.isAfter now* (Instant/parse exp))))
                     true)
           replay? (boolean (and nonce-seen? (nonce-seen? (:nonce payload))))]
       {:ok? (boolean (and sig-ok? aud-ok? exp-ok? (not replay?)))
        :iss (:iss payload) :aud (:aud payload) :resources (:resources payload)
        :expired? (not exp-ok?) :replay? replay?})
     (catch Exception e {:ok? false :error (.getMessage e)}))))
