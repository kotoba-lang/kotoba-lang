(ns aiueos.key-lifecycle
  "Root-signed, monotonic signer lifecycle epochs.

  Each accepted bundle advances exactly one epoch and binds the previous
  bundle digest, preventing replay, rollback and forked updates. Delegated
  signer scopes must be a subset of an active delegation-capable parent.
  The materialized policy maps are directly consumable by aiueos.signing."
  (:require [aiueos.signing :as signing])
  (:import [java.nio.charset StandardCharsets]
           [java.security KeyFactory KeyPair KeyPairGenerator PrivateKey
            PublicKey Signature]
           [java.security.spec X509EncodedKeySpec]
           [java.util Base64]))

(def bundle-version 1)

(defn generate-key-pair []
  (.generateKeyPair (KeyPairGenerator/getInstance "Ed25519")))

(defn raw-public-hex [^KeyPair key-pair]
  (let [encoded (.getEncoded (.getPublic key-pair))]
    (signing/hex-encode (drop (- (alength encoded) 32) encoded))))

(defn public-key-base64 [^KeyPair key-pair]
  (.encodeToString (Base64/getEncoder) (.getEncoded (.getPublic key-pair))))

(defn- canonical [value]
  (cond
    (map? value)
    (into (sorted-map-by #(compare (pr-str %1) (pr-str %2)))
          (map (fn [[key item]] [key (canonical item)]))
          value)
    (set? value) (mapv canonical (sort-by pr-str value))
    (vector? value) (mapv canonical value)
    (sequential? value) (mapv canonical value)
    :else value))

(defn document-bytes
  "Deterministic UTF-8 bytes a signature over DOCUMENT covers: the canonical
  form with SIGNATURE-KEY removed.

  Generalized from the lifecycle bundle's own `:signature` so that other signed
  documents — release attestations use `:sbom/signature` and
  `:provenance/signature` — sign the *same* canonical form. A second
  canonicalizer would be a signature-confusion hazard, not a convenience."
  [document signature-key]
  (.getBytes (pr-str (canonical (dissoc document signature-key)))
             StandardCharsets/UTF_8))

(defn document-digest
  "Hex SHA-256 of `document-bytes`. Unprefixed; callers that need a
  `sha256:<hex>` reference add the prefix."
  [document signature-key]
  (signing/hex-encode
   (.digest (java.security.MessageDigest/getInstance "SHA-256")
            (document-bytes document signature-key))))

(defn sign-document
  "DOCUMENT with a base64 Ed25519 signature over `document-bytes` at
  SIGNATURE-KEY."
  [document signature-key ^PrivateKey private-key]
  (let [signer (Signature/getInstance "Ed25519")]
    (.initSign signer private-key)
    (.update signer (document-bytes document signature-key))
    (assoc document signature-key
           (.encodeToString (Base64/getEncoder) (.sign signer)))))

(defn bundle-digest [bundle]
  (document-digest bundle :signature))

(defn sign-bundle [bundle ^PrivateKey private-key]
  (sign-document bundle :signature private-key))

(defn- decode-public-key [encoded]
  (.generatePublic
   (KeyFactory/getInstance "Ed25519")
   (X509EncodedKeySpec. (.decode (Base64/getDecoder) ^String encoded))))

(defn document-signature-valid?
  "Whether DOCUMENT's signature at SIGNATURE-KEY verifies over
  `document-bytes` under PUBLIC-KEY (a `PublicKey` or its base64 X.509 form).
  Fail-closed: any decode or verification error is `false`."
  [document signature-key public-key]
  (try
    (let [verifier (Signature/getInstance "Ed25519")]
      (.initVerify verifier
                   (if (instance? PublicKey public-key)
                     public-key
                     (decode-public-key public-key)))
      (.update verifier (document-bytes document signature-key))
      (.verify verifier
               (.decode (Base64/getDecoder) ^String (get document signature-key))))
    (catch Exception _ false)))

(defn- signature-valid? [bundle root-public-key]
  (document-signature-valid? bundle :signature root-public-key))

(defn initial-node-state []
  {:epoch 0 :bundle-digest nil :signers {} :component-signers {}})

(defn- active-at? [entry now-ms]
  (and (= :active (:status entry))
       (string? (:public-key entry))
       (= 64 (count (:public-key entry)))
       (or (nil? (:not-before-ms entry))
           (and (nat-int? (:not-before-ms entry))
                (<= (:not-before-ms entry) now-ms)))
       (or (nil? (:expires-at-ms entry))
           (and (nat-int? (:expires-at-ms entry))
                (< now-ms (:expires-at-ms entry))))))

(defn- scope-subset? [child parent]
  (or (contains? parent :*)
      (every? parent child)))

(defn- delegation-valid?
  [keys root-id signer-id now-ms visiting]
  (let [entry (get keys signer-id)]
    (cond
      (contains? visiting signer-id) false
      (= signer-id root-id) (active-at? entry now-ms)
      :else
      (let [parent-id (:delegated-by entry)
            parent (get keys parent-id)]
        (and (active-at? entry now-ms)
             (keyword? parent-id)
             (true? (:may-delegate? parent))
             (scope-subset? (:components entry #{})
                            (:components parent #{}))
             (or (nil? (:expires-at-ms parent))
                 (and (:expires-at-ms entry)
                      (<= (:expires-at-ms entry)
                          (:expires-at-ms parent))))
             (delegation-valid? keys root-id parent-id now-ms
                                (conj visiting signer-id)))))))

(defn- lifecycle-errors [bundle now-ms]
  (let [registry (:keys bundle)
        root-id (:root-id bundle)
        root-entry (get registry root-id)
        active-ids (filter #(= :active (:status (get registry %)))
                           (keys registry))]
    (into []
          (concat
           (when-not (and (map? registry) (keyword? root-id) root-entry)
             [{:kind :invalid-root-entry}])
           (when-not (and (nat-int? (:issued-at-ms bundle))
                          (nat-int? (:expires-at-ms bundle))
                          (<= (:issued-at-ms bundle) now-ms)
                          (< now-ms (:expires-at-ms bundle)))
             [{:kind :bundle-outside-validity}])
           (keep (fn [signer-id]
                   (when-not
                    (delegation-valid? registry root-id signer-id now-ms #{})
                     {:kind :invalid-delegation :signer signer-id}))
                 active-ids)))))

(defn apply-bundle
  "Verify and atomically materialize the next lifecycle epoch.

  Returns {:ok? true :state ...} or {:ok? false :reason ... :state old}.
  Nodes must consume every epoch in order; this makes convergence observable
  and prevents accepting an update on an unknown branch."
  [state root-public-key bundle now-ms]
  (let [expected-epoch (inc (:epoch state))
        errors (lifecycle-errors bundle now-ms)]
    (cond
      (not= bundle-version (:version bundle))
      {:ok? false :reason :unsupported-version :state state}
      (not= expected-epoch (:epoch bundle))
      {:ok? false
       :reason (if (<= (:epoch bundle) (:epoch state)) :rollback :epoch-gap)
       :state state}
      (not= (:bundle-digest state) (:previous-digest bundle))
      {:ok? false :reason :previous-digest-mismatch :state state}
      (not (signature-valid? bundle root-public-key))
      {:ok? false :reason :bad-root-signature :state state}
      (seq errors)
      {:ok? false :reason :invalid-lifecycle :errors errors :state state}
      :else
      (let [registry (:keys bundle)
            active
            (into {}
                  (keep (fn [[signer-id entry]]
                          (when (delegation-valid?
                                 registry (:root-id bundle) signer-id now-ms #{})
                            [signer-id
                             (select-keys entry
                                          [:public-key :status
                                           :not-before-ms :expires-at-ms])])))
                  registry)
            component-signers
            (reduce-kv
             (fn [result signer-id entry]
               (if (contains? active signer-id)
                 (reduce (fn [r component]
                           (if (= :* component)
                             r
                             (update r component (fnil conj #{}) signer-id)))
                         result
                         (:components entry #{}))
                 result))
             {}
             registry)
            state' {:epoch (:epoch bundle)
                    :bundle-digest (bundle-digest bundle)
                    :signers active
                    :component-signers component-signers}]
        {:ok? true :state state'}))))

(defn apply-to-policy
  "Replace signer authority in POLICY with an accepted node state."
  [policy node-state now-ms]
  (assoc policy
         :aiueos.policy/signers (:signers node-state)
         :aiueos.policy/component-signers (:component-signers node-state)
         :aiueos.policy/key-epoch (:epoch node-state)
         :aiueos.policy/key-epoch-digest (:bundle-digest node-state)
         :aiueos.policy/now-ms now-ms))
