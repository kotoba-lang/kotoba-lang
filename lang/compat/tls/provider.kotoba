(ns tls.provider
  "The cryptographic seam for the TLS 1.3 client.

  Everything in this repository that needs a hash, a key agreement, an AEAD or a
  signature check reaches it through a *provider map* -- a plain map of plain
  functions. The handshake, the key schedule and the record layer never name a
  JDK class, so the JVM provider that makes this usable today can be swapped for
  aiueos's native `.kotoba` objects without touching protocol code.

  ## The contract

      {:hash      {:sha256 (fn [bytes] ...32) :sha384 (fn [bytes] ...48)}
       :hmac      (fn [hash-kw key data] ...)
       :x25519    {:keypair (fn [] {:private ... :public ...})
                   :dh      (fn [priv peer-pub] ...32)}
       :aead      {:aes-128-gcm       {:seal (fn [key nonce aad pt] ct)
                                       :open (fn [key nonce aad ct] [:ok pt] | [:error reason])}
                   :chacha20-poly1305 {:seal ... :open ...}}
       :signature {:verify (fn [scheme-kw spki-der message signature]
                             [:ok true] | [:error reason])}
       :random    (fn [n] ...n)}

  ## Errors are values

  No operation in this seam throws. Failure is `[:error reason]` where `reason`
  is a keyword drawn from `known-reasons`. Two consequences are load-bearing:

  1. **There is no `[:ok false]`.** A signature that does not verify is
     `[:error :signature/bad-signature]`, not a truthy-shaped success carrying a
     false payload. A caller that destructures `[:ok _]` therefore cannot
     mistake a rejection for an acceptance -- the shape itself refuses.

  2. **`:open` never returns plaintext it could not authenticate.** On any
     authentication failure it returns `[:error :aead/bad-tag]` and nothing
     else. This is the single most important refusal in the protocol.

  ### Why every post-validation AEAD failure collapses to one reason

  `:seal`/`:open` validate key and nonce lengths *before* touching the cipher,
  and report those as their own reasons -- they describe the caller's own
  parameters. Once execution passes that gate it is operating on
  attacker-controlled bytes, and every failure from there collapses to the
  single constant `:aead/bad-tag`: truncated input, a flipped ciphertext byte, a
  flipped AAD byte and a wholly forged frame are indistinguishable in the return
  value. Distinguishing them is the padding-oracle class of defect, so the
  distinction is not made.

  ## `:x25519 :dh` and the shape of a failure

  `:dh` returns a 32-byte array on success -- exactly the shape above. It cannot
  throw, so a failure is returned as `[:error reason]`; a 32-element byte array
  and a 2-element vector are trivially distinguishable by the caller. The
  failure that matters is `:x25519/small-order-point`: RFC 8446 s7.4.2 requires
  aborting when the shared secret would be all zeroes, and a provider is
  expected to enforce that rather than hand back a degenerate secret.

  ## Validating a provider

  `validate` reports what a provider map is *missing*, by key path, so a
  provider that lacks a suite fails at the wiring boundary rather than at the
  first record. Construction sites must gate on it:

      (let [[tag p] (tls.provider/validate (tls.provider.jvm/provider))]
        (case tag :ok (use p) :error (refuse p)))"
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; The contract, as data
;; ---------------------------------------------------------------------------

(def contract
  "Every leaf a usable provider must supply, as a key path plus what it is for.

  This table is what makes `validate` unable to accept an empty map: the check
  is driven by these entries, so a provider missing everything is reported as
  missing everything. If this table were ever empty, `validate` refuses outright
  with `:contract-empty` rather than reporting a vacuous success -- a check that
  could not run must not return the value of a check that ran and found nothing
  wrong."
  [{:path [:hash :sha256]                    :doc "(fn [bytes]) -> 32 bytes"}
   {:path [:hash :sha384]                    :doc "(fn [bytes]) -> 48 bytes"}
   {:path [:hmac]                            :doc "(fn [hash-kw key data]) -> mac bytes"}
   {:path [:x25519 :keypair]                 :doc "(fn []) -> {:private b :public b}"}
   {:path [:x25519 :dh]                      :doc "(fn [priv peer-pub]) -> 32 bytes | [:error r]"}
   {:path [:aead :aes-128-gcm :seal]         :doc "(fn [key nonce aad pt]) -> ct"}
   {:path [:aead :aes-128-gcm :open]         :doc "(fn [key nonce aad ct]) -> [:ok pt] | [:error r]"}
   {:path [:aead :chacha20-poly1305 :seal]   :doc "(fn [key nonce aad pt]) -> ct"}
   {:path [:aead :chacha20-poly1305 :open]   :doc "(fn [key nonce aad ct]) -> [:ok pt] | [:error r]"}
   {:path [:signature :verify]               :doc "(fn [scheme spki msg sig]) -> [:ok true] | [:error r]"}
   {:path [:random]                          :doc "(fn [n]) -> n bytes"}])

(def aead-params
  "Key, nonce and tag lengths per AEAD, in bytes (RFC 8446 s5.2, RFC 8439).

  These are checked before the cipher runs. The JDK does *not* enforce all of
  them -- it accepts an 11-byte GCM nonce quite happily -- so these checks are
  load-bearing rather than decorative."
  {:aes-128-gcm       {:key-len 16 :nonce-len 12 :tag-len 16}
   :chacha20-poly1305 {:key-len 32 :nonce-len 12 :tag-len 16}})

(def hash-params
  "Output and block length per hash, in bytes."
  {:sha256 {:len 32 :block 64}
   :sha384 {:len 48 :block 128}})

(def signature-schemes
  "TLS 1.3 SignatureScheme code points this seam accepts (RFC 8446 s4.2.3)."
  #{:ecdsa-secp256r1-sha256
    :rsa-pss-rsae-sha256
    :rsa-pss-rsae-sha384
    :ed25519})

(def known-reasons
  "Every `reason` keyword this seam is allowed to return.

  Tests assert that observed refusals are drawn from this set, so a provider
  cannot invent an undocumented reason or leak an exception message as one."
  #{;; provider validation
    :contract-empty :provider-not-a-map :provider-incomplete
    ;; hash / hmac
    :hash/bad-input :hmac/unknown-hash :hmac/bad-input
    ;; x25519
    :x25519/bad-private-key-length :x25519/bad-peer-key-length
    :x25519/small-order-point :x25519/agreement-failed :x25519/bad-input
    ;; aead
    :aead/unknown-suite :aead/bad-key-length :aead/bad-nonce-length
    :aead/bad-input :aead/bad-tag
    ;; signature
    :signature/unknown-scheme :signature/bad-public-key :signature/bad-signature
    :signature/bad-input
    ;; random
    :random/bad-length})

;; ---------------------------------------------------------------------------
;; Validation
;; ---------------------------------------------------------------------------

(defn- leaf
  "Fetch a contract leaf without throwing on a non-associative intermediate."
  [provider path]
  (reduce (fn [acc k] (when (some? acc) (get acc k))) provider path))

(defn missing
  "Key paths the provider does not supply at all."
  [provider]
  (into [] (comp (map :path) (remove #(some? (leaf provider %)))) contract))

(defn not-callable
  "Key paths the provider supplies as something that cannot be called."
  [provider]
  (into [] (comp (map :path)
                 (filter #(let [v (leaf provider %)] (and (some? v) (not (ifn? v))))))
        contract))

(defn validate
  "Return `[:ok provider]` when `provider` satisfies `contract`, else
  `[:error info]` naming exactly which key paths are absent or uncallable.

  Refuses to report success it did not establish:
  * an empty `contract` yields `:contract-empty` rather than a vacuous `:ok`;
  * a non-map (including `nil`) yields `:provider-not-a-map`;
  * `{}` yields `:provider-incomplete` listing all of `contract`."
  [provider]
  (cond
    (empty? contract)
    [:error {:reason :contract-empty
             :detail "tls.provider/contract is empty; validation cannot establish anything"}]

    (not (map? provider))
    [:error {:reason :provider-not-a-map :found (type provider)}]

    :else
    (let [m (missing provider)
          n (not-callable provider)]
      (if (and (empty? m) (empty? n))
        [:ok provider]
        [:error {:reason        :provider-incomplete
                 :missing       m
                 :not-callable  n
                 :checked       (count contract)}]))))

(defn valid?
  "True when `provider` satisfies the contract."
  [provider]
  (= :ok (first (validate provider))))

(defn explain
  "Human-readable rendering of a `validate` result, for wiring-time diagnostics."
  [result]
  (let [[tag info] result]
    (if (= :ok tag)
      (str "provider ok (" (count contract) " contract leaves present)")
      (case (:reason info)
        :contract-empty      "provider contract is empty -- validation is inoperative"
        :provider-not-a-map  (str "provider is not a map: " (pr-str (:found info)))
        :provider-incomplete (str "provider incomplete against " (:checked info) " leaves"
                                  (when (seq (:missing info))
                                    (str "; missing: "
                                         (str/join ", " (map #(str/join "." (map name %))
                                                             (:missing info)))))
                                  (when (seq (:not-callable info))
                                    (str "; not callable: "
                                         (str/join ", " (map #(str/join "." (map name %))
                                                             (:not-callable info))))))
        (str "provider invalid: " (pr-str info))))))
