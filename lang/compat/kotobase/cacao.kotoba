(ns kotobase.cacao
  "Self-issued CACAO minting for the kotobase.net tenant Datom write/read plane.

  Byte-compatible with the edge (net-kotobase worker/src/app.cljc:
  Cacao / cacaoSiweMessage / verifyWorkerB2Cacao) and the pod (kotoba_auth::Cacao),
  ported from @gftd/kotobase-datomic (net-kotobase/sdk/kotobase-datomic/src/index.cljc).

  The signature is over a SIWE (CAIP-122) message; the CACAO envelope is
  DAG-CBOR then base64. `mint-cacao` is shape-agnostic — the capability set
  and the `kotoba://graph/<...>` scope are the caller's choice, and WHICH
  shape a verifier accepts differs by endpoint lineage (kotobase.client picks
  per its :auth-profile — see that ns):

  * apex (kotobase.net clj-edge / cf-wasm, live-probed 2026-07-09): requires
    the `kotobase:pin` capability and a graph scope equal to the ISSUER DID —
    pass :capability \"kotobase:pin\", the op caps as :extra-capabilities,
    :graph = the issuer did:key.
  * legacy pod/tenant-worker: operation capability (a transact CACAO must
    grant BOTH `datom:transact` and `tx:create`; a read CACAO grants
    `datom:read`) and a `kotoba://graph/<graph-cid>` scope matching the
    caller's own `kotobase/db/<did>/<db-name>` (the edge re-binds the graph
    from db_name so the scope must match)."
  (:require ["@noble/curves/ed25519.js" :refer [ed25519]]
            ["@ipld/dag-cbor" :as dag-cbor]
            [kotoba.lang.text :as str]
            [kotobase.cid :as cid]))

;; ── encodings ──────────────────────────────────────────────────────────────

(defn bytes->base64 [^js b]
  (if (exists? js/Buffer)
    (.toString (.from js/Buffer b) "base64")
    (js/btoa (apply str (map js/String.fromCharCode (array-seq b))))))

(defn base64->bytes [^string s]
  (if (exists? js/Buffer)
    (js/Uint8Array. (.from js/Buffer s "base64"))
    (let [bin (js/atob s)
          n (.-length bin)
          out (js/Uint8Array. n)]
      (dotimes [i n] (aset out i (.charCodeAt bin i)))
      out)))

;; base64url no pad — the pod's decode_sig_bytes uses URL_SAFE_NO_PAD for the
;; CACAO signature (then a hex fallback). Padded base64 trips the hex fallback.
(defn bytes->base64url [^js b]
  (-> (bytes->base64 b)
      (str/replace "+" "-")
      (str/replace "/" "_")
      (str/replace #"=+$" "")))

(defn base64url->bytes [^string s]
  (let [pad (case (mod (count s) 4) 2 "==" 3 "=" "")]
    (base64->bytes (-> s (str/replace "-" "+") (str/replace "_" "/") (str pad)))))

(defn- utc-seconds [^js date]
  (str/replace (.toISOString date) #"\.\d{3}Z$" "Z"))

;; ── SIWE message (byte-identical to worker/src/app.cljc cacaoSiweMessage) ──────

(defn cacao-siwe-message
  "The signed SIWE/CAIP-122 message for a CACAO payload map `p`."
  [{:keys [domain iss aud version nonce iat exp statement resources]}]
  (let [parts (str/split iss #":")
        address (or (last parts) iss)
        chain-id (if (str/starts-with? iss "did:key:")
                   "1"
                   (let [n (count parts)] (if (>= n 2) (nth parts (- n 2)) "1")))
        lines (cond-> [(str (or domain "") " wants you to sign in with your Ethereum account:")
                       address
                       ""]
                statement (conj statement "")
                :always (conj (str "URI: " aud)
                              (str "Version: " version)
                              (str "Chain ID: " chain-id)
                              (str "Nonce: " nonce)
                              (str "Issued At: " iat))
                exp (conj (str "Expiration Time: " exp))
                (seq resources) (as-> ls (apply conj ls "Resources:"
                                                (map #(str "- " %) resources))))]
    (str/join "\n" lines)))

;; ── minting ──────────────────────────────────────────────────────────────────

(defn- random-nonce []
  (-> (bytes->base64 (.getRandomValues js/crypto (js/Uint8Array. 12)))
      (str/replace #"[^a-zA-Z0-9]" "")
      (as-> s (subs s 0 (min 16 (count s))))))

(defn mint-cacao
  "Mint a base64(DAG-CBOR) CACAO. Returns {:cacao-b64 :did :graph}.

  opts: :secret-key (32-byte Uint8Array seed), :aud (operator DID),
        :capability (e.g. \"datom:transact\" / \"datom:read\"),
        :extra-capabilities (e.g. [\"tx:create\"]), :graph (graph CID),
        :ttl-sec (default 300), :now-ms / :nonce (deterministic test overrides),
        :sig-encoding — :base64url (default; the pod's decode_sig_bytes needs
        URL_SAFE_NO_PAD, see ns docstring) or :base64 (plain, no URL-safe
        substitution/padding-strip — kotobase-storage-d1's own
        verifyAuthenticationCacao decodes the signature with plain atob(),
        which does NOT understand base64url's `-`/`_` alphabet; a
        base64url-encoded signature silently fails ed25519 verification
        there with a generic 401 Unauthenticated, no specific error surfaced
        to the caller — use :base64 for that verifier),
        :tenant — when given, `kotoba://tenant/<tenant>` is added to the
        SIGNED resource list. `kotobase-server`'s
        `kotobase.server.security.authority/verify-grant` builds exactly that
        string and, under `:require-tenant-binding? true`, refuses a grant
        that does not carry it; its resource allowlist admits only the graph
        resource, the tenant resource and `kotoba://can/*`. Absent, the bytes
        are unchanged — this is additive, and a verifier that does not require
        the binding is unaffected either way.
        :statement — the SIWE statement field, signed. nil (the default) is
        the previous behaviour."
  [{:keys [secret-key aud capability extra-capabilities graph tenant statement
           ttl-sec now-ms nonce sig-encoding]
    :or {ttl-sec 300 extra-capabilities [] sig-encoding :base64url}}]
  (let [pub (.getPublicKey ed25519 secret-key)
        did (cid/did-key-from-ed25519-pub pub)
        now (if (some? now-ms) (js/Date. now-ms) (js/Date.))
        nonce (or nonce (random-nonce))
        iat (utc-seconds now)
        exp (utc-seconds (js/Date. (+ (.getTime now) (* ttl-sec 1000))))
        resources (cond-> (conj (mapv #(str "kotoba://can/" %)
                                      (cons capability extra-capabilities))
                                (str "kotoba://graph/" graph))
                    tenant (conj (str "kotoba://tenant/" tenant)))
        p {:domain "kotobase.net" :iss did :aud aud :version "1"
           :nonce nonce :iat iat :exp exp :statement statement :resources resources}
        msg (cacao-siwe-message p)
        sig (.sign ed25519 (cid/text->bytes msg) secret-key)
        sig-str (if (= :base64 sig-encoding) (bytes->base64 sig) (bytes->base64url sig))
        cacao #js {:h #js {:t "caip122"}
                   :p (clj->js p)
                   :s #js {:t "EdDSA" :s sig-str}}]
    {:cacao-b64 (bytes->base64 (.encode dag-cbor cacao))
     :did did
     :graph graph}))
