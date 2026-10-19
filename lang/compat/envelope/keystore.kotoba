;; envelope.keystore — where a hybrid recipient's secret keys live, and what
;; makes one refuse to hand them over.
;;
;; `envelope.passkey` already answers this for the classical half: the
;; identity is an ordinary random X25519 key, and a passkey's PRF output
;; wraps it rather than becoming it, so the identity outlives any one
;; credential. A hybrid recipient has two secrets, not one, and a wrapped key
;; delivered to it opens only if BOTH are present — so a store that holds one
;; of them is a store that cannot open anything.
;;
;; This is that store, and it is deliberately a store of RECORDS rather than
;; of keys: what it keeps is public material plus two wraps, which is safe to
;; put on a server that must not be able to read anything. The unlock secret
;; — a passkey PRF output, a device-keychain item, an operator-held key — is
;; supplied per call and is never held here.
;;
;;   (-> (keystore/generate-identity)
;;       (.then #(keystore/seal-record % prf-output salt))
;;       (.then store-it-somewhere))
;;
;;   ;; later, as ayatori's :keys! port
;;   (keystore/unlocker records prf-output)
;;
;; ## The fingerprint is derived, not assigned
;;
;; A record's identity is its keys: `fingerprint` hashes the two public keys,
;; and both wraps are sealed with that fingerprint in their AAD. An attacker
;; who relabels a record, or swaps its public material, changes the AAD and
;; the unwrap fails — the binding between "who this is" and "what opens it"
;; is cryptographic rather than a field somebody remembered to compare.
(ns envelope.keystore
  (:require [envelope.kem :as kem]
            [envelope.seal :as seal]
            [kotoba.signal.x25519 :as x25519]))

(def record-version 1)

(def fingerprint-domain "kotoba/envelope/v1 hybrid-recipient")

(def ^:private aad-prefix "kotoba/envelope/v1 keystore ")

;; ML-KEM-768's decapsulation key embeds its own encapsulation key: FIPS 203
;; stores dk as (dk_PKE || ek || H(ek) || z), so ek is the 1184 bytes at 1152.
(def ^:private ml-kem-pub-offset 1152)

(defn- reject! [reason data]
  (throw (ex-info "envelope keystore refused"
                  (merge {:envelope.keystore/reason reason} data))))

(defn- concat-bytes [& arrays]
  (let [out (js/Uint8Array. (reduce + 0 (map #(.-length ^js %) arrays)))]
    (reduce (fn [off ^js a] (.set out a off) (+ off (.-length a))) 0 arrays)
    out))

(defn- utf8 [s] (.encode (js/TextEncoder.) s))

(defn- bytes= [^js a ^js b]
  (and (some? a) (some? b)
       (= (.-length a) (.-length b))
       (every? #(= (aget a %) (aget b %)) (range (.-length a)))))

(defn- as-bytes [x] (if (string? x) (seal/unb64url x) x))

(defn fingerprint
  "-> Promise<string>. The name of a hybrid recipient, computed from its two
  public keys rather than chosen for it.

  A grant's `:recipient-key` should be this. The protocol requires the
  issuer to bind a recipient's encryption-key fingerprint independently of
  anything the requester says; a fingerprint that is derived is one the
  issuer and the recipient compute the same way from the same bytes, and an
  assigned one is a second thing that has to be kept true."
  [{:keys [pub pq-pub]}]
  (let [pub (as-bytes pub) pq-pub (as-bytes pq-pub)]
    (when-not (and (= 32 (.-length ^js pub)) (= kem/public-bytes (.-length ^js pq-pub)))
      (reject! :not-a-hybrid-public-key
               {:x25519 (some-> ^js pub .-length) :ml-kem (some-> ^js pq-pub .-length)}))
    (-> (js/crypto.subtle.digest "SHA-256" (concat-bytes (utf8 fingerprint-domain) pub pq-pub))
        (.then (fn [digest] (str "kv1:" (seal/b64url (js/Uint8Array. digest))))))))

(defn generate-identity
  "-> Promise<{:recipient-key :pub :priv :pq-pub :pq-priv}>. Fresh hybrid
  identity. The secrets are in the returned map and nowhere else yet."
  []
  (let [x (x25519/generate-keypair)
        pq (kem/generate-keypair)
        identity {:pub (:pub x) :priv (:priv x)
                  :pq-pub (:pub pq) :pq-priv (:priv pq)}]
    (-> (fingerprint identity)
        (.then (fn [fp] (assoc identity :recipient-key fp))))))

(defn- aad [fp half] (str aad-prefix fp " " half))

(defn seal-record
  "-> Promise<record>. The storable form: public material, a status, and two
  wraps. No secret is in it.

  Each half is wrapped separately under its own AAD, so the two cannot be
  exchanged for one another, and both AADs carry the fingerprint, so the
  record cannot be relabelled or have its public material edited without
  making both wraps unopenable."
  [{:keys [recipient-key pub priv pq-pub pq-priv] :as identity} ^js unlock-secret & [^js salt]]
  (-> (js/Promise.resolve (or recipient-key (fingerprint identity)))
      (.then (fn [fp]
               (-> (js/Promise.all
                    #js [(seal/wrap-under-key priv unlock-secret salt (aad fp "x25519"))
                         (seal/wrap-under-key pq-priv unlock-secret salt (aad fp "ml-kem-768"))])
                    (.then (fn [[x-wrap pq-wrap]]
                             {:record/version record-version
                              :recipient-key fp
                              :key/status :active
                              :pub (seal/b64url (as-bytes pub))
                              :pq-pub (seal/b64url (as-bytes pq-pub))
                              :wrapped/x25519 x-wrap
                              :wrapped/ml-kem pq-wrap})))))))

(defn open-record
  "-> Promise<{:recipient-key :pub :priv :pq-priv}>, or a rejection.

  Refuses, by name and before touching the wraps:

    :unsupported-record   a version this code does not know. Guessing at an
                          unknown record shape is how a store opens something
                          it does not understand.
    :key-not-active       `:key/status` is anything other than `:active` --
                          including a status this code has never heard of.
                          `kotoba.security.key-status` owns that vocabulary;
                          what matters here is that everything outside the
                          one permitted value is refused rather than only the
                          values known when this was written.
    :fingerprint-mismatch the stored name is not the one the stored public
                          keys hash to.

  And then cryptographically: the wraps are sealed under the derived
  fingerprint, so an edited record cannot be opened even if it passes the
  checks above, and a wrong unlock secret fails the AEAD.

  One consistency check survives the unwrap: ML-KEM-768's decapsulation key
  embeds its own encapsulation key, so a recovered secret that does not match
  the record's `:pq-pub` is refused. There is no equivalent for the X25519
  half -- deriving a public key from a private one needs a scalar
  multiplication this repo does not expose -- so that half rests on the AAD
  binding alone, which is why the AAD carries the fingerprint of both."
  [{:keys [record/version recipient-key key/status pub pq-pub] :as record} ^js unlock-secret]
  (-> (js/Promise.resolve nil)
      (.then
       (fn [_]
         (when-not (= record-version version)
           (reject! :unsupported-record {:record/version version}))
         (when-not (= :active status)
           (reject! :key-not-active {:key/status status :recipient-key recipient-key}))
         (fingerprint {:pub pub :pq-pub pq-pub})))
      (.then
       (fn [derived]
         (when-not (= derived recipient-key)
           (reject! :fingerprint-mismatch {:stored recipient-key :derived derived}))
         (js/Promise.all
          #js [(seal/unwrap-under-key (:wrapped/x25519 record) unlock-secret
                                      (aad derived "x25519"))
               (seal/unwrap-under-key (:wrapped/ml-kem record) unlock-secret
                                      (aad derived "ml-kem-768"))])))
      (.then
       (fn [[priv pq-priv]]
         (let [embedded (.slice ^js pq-priv ml-kem-pub-offset
                                (+ ml-kem-pub-offset kem/public-bytes))]
           (when-not (bytes= embedded (as-bytes pq-pub))
             (reject! :ml-kem-key-pair-mismatch {:recipient-key recipient-key})))
         {:recipient-key recipient-key
          :pub (as-bytes pub)
          :priv priv
          :pq-priv pq-priv}))))

(defn unlocker
  "Build the `:keys!` port `ayatori.disclosure-open/recipient-opener` takes.

  RECORDS is a map of fingerprint -> record, or a function of one. An absent
  fingerprint resolves to nil, which the opener reports as
  `:no-key-for-recipient`; a record that is present and refused REJECTS, so
  \"there is no such recipient\" and \"that recipient's key is revoked\" do
  not arrive as the same answer.

  The unlock secret is closed over here rather than stored anywhere: this
  function is what a device holds for as long as it is unlocked, and holding
  it is the whole of what being unlocked means."
  [records ^js unlock-secret]
  (let [lookup (if (fn? records) records #(get records %))]
    (fn [fingerprint-string]
      (if-let [record (lookup fingerprint-string)]
        (open-record record unlock-secret)
        (js/Promise.resolve nil)))))
