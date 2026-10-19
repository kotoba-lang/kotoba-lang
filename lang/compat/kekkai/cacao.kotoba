(ns kekkai.cacao
  "Agent-side CACAO issuance (JVM). The kekkai actor mints its OWN
  server-verifiable CACAO to authenticate to a kotoba-server pod (kotobase.net)
  — no human-handed token. This is the JVM realization of kotoba's
  `mint-cacao!` / `sign-cacao->b64` (kotoba-auth / kotoba-wasm); the SIWE/wire
  builders below are a faithful copy of the proven byte-exact pure functions in
  `kotoba.cacao` (keep in sync), and the crypto is JDK Ed25519 + a minimal CBOR
  encoder.

  The parallel to Tailscale is exact: a Tailscale node IS its WireGuard/node
  key — the key, not a username, is what authorizes the machine in the tailnet.
  Here the actor's Ed25519 key IS its graph: the key-derived IPNS name
  (`ipns-name`, the 'k51…' name) per kotoba/write.cljs (AUTHORITY is the Ed25519
  signature over a key-derived IPNS name, NOT a server). The actor owns its
  graph by holding the key, so a depth-1 self-minted CACAO (iss = the actor's
  own DID = graph owner) is authorized by construction — no owner hand-off, no
  shared token, no coordination-server-issued auth-key.
  Use `load-or-create-identity!` to bootstrap/persist the actor's node key."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [ed25519.core :as ed25519]
            [ipns.core :as ipns])
  (:import [java.security KeyPairGenerator Signature KeyFactory]
           [java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec]
           [java.io ByteArrayOutputStream]
           [java.util Base64]))

;; ───────── pure CACAO builders (mirror of kotoba.cacao) ─────────

(def ^:private cap->op {:cap/read "datom:read" :cap/transact "datom:transact" :cap/admin "tx:create"})

(defn grant->resources [{:keys [cap scope]}]
  [(str "kotoba://op/" (cap->op cap)) (str "kotoba://graph/" scope)])

(defn grant->payload [grant {:keys [iss aud nonce issued-at expiry domain version statement]
                             :or {domain "gftd.office" version "1"}}]
  {:iss iss :aud aud :issued-at issued-at :expiry expiry :nonce nonce
   :domain domain :statement statement :version version
   :resources (grant->resources grant)})

(defn- iss-address [iss] (last (str/split iss #":")))
(defn- iss-chain-id [iss]
  (if (str/starts-with? iss "did:key:") "1"
      (let [segs (str/split iss #":")] (if (>= (count segs) 2) (nth segs (- (count segs) 2)) "1"))))

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

;; ───────── minimal CBOR (definite-length; serde-deserializable) ─────────

(defn- cbor-head [^ByteArrayOutputStream o major n]
  (cond (< n 24)    (.write o (int (+ (bit-shift-left major 5) n)))
        (< n 256)   (do (.write o (int (+ (bit-shift-left major 5) 24))) (.write o (int n)))
        (< n 65536) (do (.write o (int (+ (bit-shift-left major 5) 25)))
                        (.write o (int (bit-and (unsigned-bit-shift-right n 8) 0xff)))
                        (.write o (int (bit-and n 0xff))))
        :else (throw (ex-info "cbor len too big" {:n n}))))

(defn- cbor-val [^ByteArrayOutputStream o v]
  (cond
    (string? v)     (let [b (.getBytes ^String v "UTF-8")] (cbor-head o 3 (alength b)) (.write o b 0 (alength b)))
    (map? v)        (do (cbor-head o 5 (count v)) (doseq [[k vv] v] (cbor-val o (name k)) (cbor-val o vv)))
    (sequential? v) (do (cbor-head o 4 (count v)) (doseq [x v] (cbor-val o x)))
    :else           (cbor-val o (str v))))

(defn- cbor-bytes ^bytes [v]
  (let [o (ByteArrayOutputStream.)] (cbor-val o v) (.toByteArray o)))

;; ───────── Ed25519 + did:key (delegated to kotoba-lang/ed25519, ADR-2607050100) ─────────

(defn- raw-pub
  "Raw 32-byte Ed25519 public key (last 32 bytes of the X.509 SPKI encoding)."
  ^bytes [pub]
  (let [enc (.getEncoded pub)] (java.util.Arrays/copyOfRange enc (- (alength enc) 32) (alength enc))))

(defn- did-key [pub]
  (ed25519/did-key-from-pub (raw-pub pub)))

;; ───────── key-derived IPNS graph name (per-actor graph = node key) ─────────
;; Each actor's graph IS its own key: the graph name is the libp2p-key IPNS
;; name derived from the actor's Ed25519 pubkey (kotoba/write.cljs: "AUTHORITY
;; is the Ed25519 signature over a key-derived IPNS name, NOT a server"). So the
;; actor owns its graph by holding the key — self-mint is authorized by
;; construction, no owner hand-off. (Tailscale analog: a node's identity in the
;; tailnet IS its node key, not a server-issued account.) Derivation lives in
;; kotoba-lang/ipns (ADR-2607050100); this used to be a private BigInteger
;; reimplementation.

(defn ipns-name
  "The actor's own graph: the base36 CIDv1 (libp2p-key, identity multihash)
  IPNS name of its Ed25519 pubkey — i.e. the 'k51…' name. DID-derived, so the
  actor IS the authority over this graph."
  [pub]
  (ipns/pubkey->name (raw-pub pub)))

(defn generate-identity
  "A fresh Ed25519 identity {:private-key :public-key :did :graph}. For owner/
  test bootstrap — a provisioned actor persists and reloads its node key."
  []
  (let [kp (.generateKeyPair (KeyPairGenerator/getInstance "Ed25519"))
        pub (.getPublic kp)]
    {:private-key (.getPrivate kp) :public-key pub :did (did-key pub) :graph (ipns-name pub)
     :private-b64 (.encodeToString (Base64/getEncoder) (.getEncoded (.getPrivate kp)))
     :public-b64  (.encodeToString (Base64/getEncoder) (.getEncoded pub))}))

(defn load-identity
  "Reload a persisted identity from base64 PKCS8 private + X.509 public."
  [{:keys [private-b64 public-b64]}]
  (let [kf (KeyFactory/getInstance "Ed25519")
        priv (.generatePrivate kf (PKCS8EncodedKeySpec. (.decode (Base64/getDecoder) private-b64)))
        pub  (.generatePublic kf (X509EncodedKeySpec. (.decode (Base64/getDecoder) public-b64)))]
    {:private-key priv :public-key pub :did (did-key pub) :graph (ipns-name pub)
     :private-b64 private-b64 :public-b64 public-b64}))

(defn load-or-create-identity!
  "Per-actor node key: load the actor's persisted Ed25519 identity at `path`, or
  generate + persist one on first run (only the b64 key material is stored).
  Returns {:private-key :public-key :did :graph …}. This is the 'each actor
  issues its own key' bootstrap — the actor's graph is the key-derived IPNS."
  [path]
  (let [f (java.io.File. ^String path)]
    (if (.exists f)
      (load-identity (edn/read-string (slurp f)))
      (let [id (generate-identity)
            parent (.getParentFile (.getAbsoluteFile f))]
        (when parent (.mkdirs parent))
        (spit f (pr-str (select-keys id [:private-b64 :public-b64])))
        id))))

(defn- ed-sign ^bytes [priv ^bytes msg]
  (let [s (doto (Signature/getInstance "Ed25519") (.initSign priv))] (.update s msg) (.sign s)))

(defn verify? [pub ^bytes msg ^bytes sig]
  (let [v (doto (Signature/getInstance "Ed25519") (.initVerify pub))] (.update v msg) (.verify v sig)))

;; ───────── mint ─────────

(defn mint
  "Mint a base64 cacao_b64 the agent signs itself.
   identity: {:private-key :public-key :did}
   grant:    {:cap :cap/read|:cap/transact|:cap/admin :scope <graph>}
   opts:     {:aud <server did/uri> :nonce :issued-at :expiry}"
  [{:keys [private-key did]} grant {:keys [aud nonce issued-at expiry]}]
  (let [payload (grant->payload grant {:iss did :aud aud :nonce nonce
                                       :issued-at issued-at :expiry expiry})
        msg     (siwe-message payload)
        sig     (ed-sign private-key (.getBytes ^String msg "UTF-8"))
        sig-b64 (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) sig)
        wire    (->wire payload sig-b64)]
    (.encodeToString (Base64/getEncoder) (cbor-bytes wire))))
