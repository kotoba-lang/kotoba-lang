(ns kagi.identity
  "vault actor の自己主権 identity(JVM)。`itonami/cacao.clj` を継承:
  Ed25519 did:key と鍵由来 IPNS 名(`k51…`)がそのまま actor の graph = vault namespace。
  CACAO は自己発行(owner hand-off も共有 token も不要)。**kotoba authority は不変**で、
  ここに hybrid 署名用の ML-DSA-65 公開鍵を加法的に併記する(graph に publish)。

  `.kagi/identity.edn` は公開metadataとSecretStore/native-handle参照のみを保持する。
  production署名鍵は非exportable OS/HSM handleを必須とする。"
  (:require [clojure.edn :as edn]
            [ed25519.core :as ed25519]
            [ipns.core :as ipns]
            [kagi.crypto :as crypto]
            [kagi.native-key :as native-key]
            [kagi.key-registry :as key-registry]
            [kagi.secret-store :as secret-store])
  (:import [java.security KeyPairGenerator KeyFactory Key MessageDigest]
           [java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec]
           [java.time Instant]
           [java.time.temporal ChronoUnit]
           [java.util Base64 UUID]))

(defn- enc ^bytes [^Key k] (.getEncoded k))
(defn- b64 ^String [^bytes b] (.encodeToString (Base64/getEncoder) b))
(defn- unb64 ^bytes [^String s] (.decode (Base64/getDecoder) s))
(defn- b64-map [m] (into {} (map (fn [[k v]] [k (b64 v)])) m))
(defn- unb64-map [m] (into {} (map (fn [[k v]] [k (unb64 v)])) m))

(defn encode-bundle [m] (b64-map m))
(defn decode-bundle [m] (unb64-map m))

(defn- raw-pub ^bytes [pub]
  (let [enc (.getEncoded pub)]
    (java.util.Arrays/copyOfRange enc (- (alength enc) 32) (alength enc))))

;; did:key + IPNS-name derivation delegated to kotoba-lang/ed25519 and
;; kotoba-lang/ipns (ADR-2607050100) — this used to be a private
;; BigInteger-based reimplementation.

(defn- did-key [pub]
  (ed25519/did-key-from-pub (raw-pub pub)))

(defn- ipns-name
  "actor の graph = 鍵由来 libp2p-key IPNS 名(`k51…`)。鍵を持つことが graph の authority。"
  [pub]
  (ipns/pubkey->name (raw-pub pub)))

(defn generate-identity
  "fresh hybrid identity。Ed25519 が **authority**(did:key/IPNS graph、kotoba 不変)で、
   ML-DSA-65 を vault commit/台帳の **hybrid 共同署名** として加法的に併発行する
   (`:mldsa-public-b64` は graph に publish)。provider を渡すと **hybrid KEM 受信鍵**
   (X25519+ML-KEM-768)も生成し、他メンバーがこの identity へ secret を共有できる。"
  ([] (generate-identity nil))
  ([provider]
   (let [kp  (.generateKeyPair (KeyPairGenerator/getInstance "Ed25519"))
         pub (.getPublic kp)
         ml  (.generateKeyPair (KeyPairGenerator/getInstance "ML-DSA-65"))
         kem (when provider (crypto/kem-keypair provider))
         now (Instant/now)
         key-base {:created-at (str now) :not-before (str now)
                   :originator-not-after (str (.plus now 365 ChronoUnit/DAYS))
                   :custody-ref "pending://identity-generation"}]
     (cond-> {:private-key (.getPrivate kp) :public-key pub
              :authority-id (str "urn:kagi:authority:" (UUID/randomUUID))
              :did (did-key pub) :graph (ipns-name pub)
              :private-b64 (b64 (enc (.getPrivate kp)))
              :public-b64  (b64 (enc pub))
              :mldsa-private-b64 (b64 (enc (.getPrivate ml)))
              :mldsa-public-b64  (b64 (enc (.getPublic ml)))
              :signing-key (key-registry/transition
                            (key-registry/key-record
                             (merge key-base {:id (str "sign:" (b64 (raw-pub pub)))
                                              :purpose :identity-signing
                                              :suite :authority-v1 :epoch 0}))
                            :active (str now))}
       kem (assoc :kem-public (b64-map (:public kem))
                  :kem-secret (b64-map (:secret kem))
                  :kem-key (key-registry/transition
                            (key-registry/key-record
                             (merge key-base {:id (str "kem:" (b64 (crypto/sha256 (:pq (:public kem)))))
                                              :purpose :recipient-kem
                                              :suite :kem-v1 :epoch 0
                                              :originator-not-after
                                              (str (.plus now 90 ChronoUnit/DAYS))}))
                            :active (str now)))))))

(defn kem-public
  "他メンバーがこの identity へ encapsulate するための hybrid KEM 公開 bundle {:x :pq}。"
  [id]
  (some-> (:kem-public id) unb64-map))

(defn kem-secret
  "accept-share で envelope を開くための hybrid KEM 秘密 bundle。"
  [id]
  (or (:decapsulation-handle id)
      (some-> (:kem-secret id) unb64-map)))

(defn sign-secret
  "Return an opaque native signer when provisioned, otherwise the encoded
  compatibility bundle used by migrated software identities."
  [{:keys [signing-handle private-b64 mldsa-private-b64]}]
  (or signing-handle
      (when (and private-b64 mldsa-private-b64)
        {:ed (unb64 private-b64) :mldsa (unb64 mldsa-private-b64)})
      (throw (ex-info "identity has no signing capability" {}))))

(defn sign-public
  "`verify*` / graph publish 用の公開 bundle {:ed <x509> :mldsa <x509>}。"
  [{:keys [public-b64 mldsa-public-b64]}]
  {:ed (unb64 public-b64) :mldsa (unb64 mldsa-public-b64)})

(defn member-record
  "graph に publish するメンバー entity(depth-1 self-mint)。公開鍵束のみ(秘密は出さない)。"
  [id role]
  (cond-> #:member{:did (:did id) :role role :sign-pub (b64-map (sign-public id))}
    (:signing-key id) (assoc :member/sign-key (:signing-key id))
    (:kem-public id) (assoc :member/kem-pub (kem-public id))
    (:kem-key id) (assoc :member/kem-key (:kem-key id))))

(defn load-identity [{:keys [private-b64 public-b64] :as m}]
  (let [kf (KeyFactory/getInstance "Ed25519")
        pub  (.generatePublic kf (X509EncodedKeySpec. (.decode (Base64/getDecoder) public-b64)))]
    (cond-> (merge m {:public-key pub :did (did-key pub) :graph (ipns-name pub)})
      private-b64 (assoc :private-key
                         (.generatePrivate kf (PKCS8EncodedKeySpec.
                                                (.decode (Base64/getDecoder) private-b64)))))))

(def secret-fields #{:private-b64 :mldsa-private-b64 :kem-secret})
(def public-fields #{:authority-id :public-b64 :mldsa-public-b64 :kem-public :signing-key :kem-key
                     :native-signing-handle
                     :native-kem-handle
                     :retired-signing-epochs})

(defn- ensure-key-metadata [m]
  (let [now (Instant/now)
        custody (or (:secret-ref m) "plaintext-development://identity")
        base {:created-at (str now) :not-before (str now) :custody-ref custody}
        signing (or (:signing-key m)
                    (key-registry/transition
                     (key-registry/key-record
                      (merge base {:id (str "sign:" (subs (:public-b64 m) 0 (min 24 (count (:public-b64 m)))))
                                   :purpose :identity-signing :suite :authority-v1 :epoch 0
                                   :originator-not-after (str (.plus now 365 ChronoUnit/DAYS))}))
                     :active (str now)))
        kem (when (:kem-public m)
              (or (:kem-key m)
                  (key-registry/transition
                   (key-registry/key-record
                    (merge base {:id (str "kem:" (subs (get-in m [:kem-public :pq])
                                                        0 (min 24 (count (get-in m [:kem-public :pq])))))
                                 :purpose :recipient-kem :suite :kem-v1 :epoch 0
                                 :originator-not-after (str (.plus now 90 ChronoUnit/DAYS))}))
                   :active (str now))))]
    (cond-> (assoc m :signing-key signing
                     :authority-id (or (:authority-id m)
                                       (str "urn:kagi:authority:legacy:"
                                            (subs (:public-b64 m) 0
                                                  (min 32 (count (:public-b64 m))))))
                     :security/key-metadata-version 1)
      kem (assoc :kem-key kem))))

(defn split-identity [id]
  {:public (select-keys id public-fields)
   :secret (select-keys id secret-fields)})

(defn secret-backed-identity? [m]
  (and (:secret-ref m)
       (not (:private-b64 m))))

(defn load-secret-backed-identity
  ([m secret-store] (load-secret-backed-identity m secret-store nil))
  ([m secret-store native-signing]
   (let [secret (secret-store/get-edn secret-store (:secret-ref m))]
     (if native-signing
       (do
         (when (some #(contains? secret %) [:private-b64 :mldsa-private-b64])
           (throw (ex-info "native identity still contains exportable signing keys"
                           {:remediation "run kagi identity-native-migrate after verifying token aliases"})))
         (when (and (:kem-handle native-signing) (contains? secret :kem-secret))
           (throw (ex-info "native identity still contains exportable KEM keys"
                           {:remediation "run kagi identity-native-migrate after verifying token aliases"})))
         (let [id (load-identity (merge m secret))
               signer (native-key/bound-signer (:keystore native-signing)
                                                (:handle native-signing)
                                                (sign-public id))
               recipient (when-let [h (:kem-handle native-signing)]
                           (native-key/bound-kem-recipient (:keystore native-signing) h
                                                          (kem-public id)))]
           (cond-> (assoc id :signing-handle signer)
             recipient (assoc :decapsulation-handle recipient))))
       (load-identity (merge m secret))))))

(defn migrate-native-signing!
  "After token public-key binding and a live hybrid signing self-test succeed,
  remove exportable Ed25519/ML-DSA encodings from the existing SecretStore.
  Any failure before the durable overwrite leaves the old secret untouched."
  [path public-id secret-store native-signing]
  (let [ref (:secret-ref public-id)]
    (when-not (seq ref)
      (throw (ex-info "identity must first be migrated to a SecretStore" {})))
    (let [expected (sign-public public-id)
          signer (native-key/bound-signer (:keystore native-signing)
                                           (:handle native-signing) expected)
          challenge (crypto/sha256 (.getBytes (str "kagi/native-migration/" (UUID/randomUUID))
                                               "UTF-8"))
          signature (crypto/sign-with (crypto/jvm-provider) signer challenge)
          _ (when-not (crypto/verify* (crypto/jvm-provider) expected challenge signature)
              (throw (ex-info "native signing possession self-test failed" {})))
          recipient (when-let [h (:kem-handle native-signing)]
                      (native-key/bound-kem-recipient (:keystore native-signing) h
                                                     (kem-public public-id)))
          _ (when recipient
              (let [{:keys [ciphertext shared]} (crypto/kem-encap (crypto/jvm-provider)
                                                                  (kem-public public-id))
                    recovered (crypto/kem-decap-with (crypto/jvm-provider) recipient ciphertext)]
                (try
                  (when-not (MessageDigest/isEqual ^bytes shared ^bytes recovered)
                    (throw (ex-info "native KEM possession self-test failed" {})))
                  (finally
                    (java.util.Arrays/fill ^bytes shared (byte 0))
                    (java.util.Arrays/fill ^bytes recovered (byte 0))))))
          old-secret (secret-store/get-edn secret-store ref)
          sanitized (cond-> (dissoc old-secret :private-b64 :mldsa-private-b64)
                      recipient (dissoc :kem-secret))
          metadata (native-key/metadata (:handle native-signing))
          kem-metadata (when-let [h (:kem-handle native-signing)]
                         (native-key/kem-metadata h))
          public* (-> public-id
                      (select-keys public-fields)
                      (assoc :secret-ref ref :secret-provider :secret-store
                             :native-signing-handle metadata)
                      (cond-> kem-metadata (assoc :native-kem-handle kem-metadata))
                      (assoc-in [:signing-key :key/custody-ref]
                                (str "native://" (:keystore-id metadata)))
                      (cond-> kem-metadata
                        (assoc-in [:kem-key :key/custody-ref]
                                  (str "native://" (:keystore-id kem-metadata)))))]
      (when-not (every? #(contains? old-secret %) [:private-b64 :mldsa-private-b64])
        (throw (ex-info "exportable signing keys are already absent" {})))
      (secret-store/put-edn! secret-store ref sanitized)
      (let [stored (secret-store/get-edn secret-store ref)]
        (when (some #(contains? stored %)
                    (cond-> [:private-b64 :mldsa-private-b64] recipient (conj :kem-secret)))
          (throw (ex-info "SecretStore did not remove exportable native key material" {}))))
      (spit path (pr-str public*))
      {:migrated? true :secret-ref ref :native-signing-handle metadata})))

(defn migrate-identity-secret!
  "Move secret key material from an identity map into SecretStore.

  Returns the public identity map that should be written to identity.edn."
  [path id secret-store secret-ref]
  (let [{:keys [public secret]} (split-identity id)
        base-public (-> public
                        ensure-key-metadata
                        (assoc :secret-ref secret-ref :secret-provider :secret-store)
                        (assoc-in [:signing-key :key/custody-ref] secret-ref))
        public* (cond-> base-public
                  (:kem-key base-public) (assoc-in [:kem-key :key/custody-ref] secret-ref))]
    (secret-store/put-edn! secret-store secret-ref secret)
    (spit path (pr-str public*))
    public*))

(defn load-or-create-identity!
  "per-actor 鍵を `path`(.kagi/identity.edn) から読込、無ければ生成→永続(b64 のみ保存)。
   provider を渡すと hybrid KEM 受信鍵も生成・永続する。"
  ([path] (load-or-create-identity! path nil))
  ([path provider] (load-or-create-identity! path provider nil))
  ([path provider {:keys [secret-store secret-ref allow-plaintext? allow-existing-plaintext?
                          native-signing]}]
   (let [f (java.io.File. ^String path)]
     (if (.exists f)
       (let [m (edn/read-string (slurp f))
             m* (ensure-key-metadata m)]
         (if (secret-backed-identity? m*)
           (do
             (when (not= m m*) (spit f (pr-str m*)))
             (load-secret-backed-identity
              m*
              (or secret-store (secret-store/store-for-ref (:secret-ref m*)))
              native-signing))
           (if (or allow-plaintext? allow-existing-plaintext?)
             (load-identity m*)
             (throw (ex-info "plaintext identity requires migration"
                             {:path path
                              :remediation "run kagi identity-migrate before other commands"})))))
       (let [_ (when native-signing
                 (throw (ex-info "native identity public keys must be provisioned before init" {})))
             id (generate-identity provider)
             parent (.getParentFile (.getAbsoluteFile f))]
         (when parent (.mkdirs parent))
         (if (and secret-store secret-ref)
           (migrate-identity-secret! path id secret-store secret-ref)
           (if allow-plaintext?
             (spit f (pr-str (assoc (select-keys id [:authority-id :private-b64 :public-b64
                                                     :mldsa-private-b64 :mldsa-public-b64
                                                     :kem-public :kem-secret])
                                    :security/plaintext-development-only true)))
             (throw (ex-info "refusing to persist plaintext identity secret material"
                             {:path path
                              :remediation "configure a SecretStore or explicitly set :allow-plaintext? for disposable development only"}))))
         id)))))
