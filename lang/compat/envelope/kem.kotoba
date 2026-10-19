(ns envelope.kem
  "The hybrid key encapsulation behind `:x25519+ml-kem-768`.

  X25519 alone means a wrap harvested today opens the day a
  cryptographically-relevant quantum computer exists — and the wrap holds
  the content key, so that is the whole object. Combining X25519 with
  ML-KEM-768 (FIPS 203) means an attacker has to break **both**: the derived
  key is HKDF over the concatenation, so either shared secret being secret
  keeps it secret. That is why this combines rather than replaces —
  a hybrid is never weaker than the classical construction it extends, and
  ML-KEM is young enough that this matters.

  ## Where the primitives come from, and why here

  `@noble/post-quantum`'s `ml_kem768`, bound thinly. X25519 and HKDF still
  come from `kotoba-lang/org-signal` — this namespace does not touch them.

  Two other ML-KEM bindings exist in this workspace, and neither is a home
  for this one:

  - `kotoba-lang/kagi`'s `kagi.crypto.noble` is an actor's crypto provider.
    A library depending on an actor inverts the dependency direction.
  - `kotoba-lang/pqh`'s `pq_noble.cljs` is the `IPq` host that repo's README
    calls future work. It has been written but sits **uncommitted** in the
    shared checkout since 2026-07-20; building on someone's unlanded working
    tree is not a dependency, it is a coincidence.

  `kotoba-lang/org-signal` would be the principled home — Signal specifies
  PQXDH over ML-KEM — but it implements X3DH only, and growing it a
  post-quantum half is its own piece of work, not a side effect of this one.

  The `envelope` README's rule is that a **second X25519 implementation**
  would be a second one to get wrong. This is not an implementation; it is a
  call into an audited one. Three thin bindings to the same audited
  primitive is a consolidation opportunity, not a correctness hazard — but
  it is an opportunity, and it is recorded here so it is not forgotten.

  ## Sizes (FIPS 203, ML-KEM-768)

  public key 1184 B · ciphertext 1088 B · shared secret 32 B. Every wrap to
  a hybrid recipient therefore carries 1088 bytes more than a classical one.
  That is the price, and it is per recipient, not per object."
  (:require ["@noble/post-quantum/ml-kem.js" :refer [ml_kem768]]
            [kotoba.signal.hkdf :as hkdf]
            [kotoba.signal.x25519 :as x25519]))

(def public-bytes 1184)
(def ciphertext-bytes 1088)
(def shared-bytes 32)
(def wrap-key-bytes 32)

(def ^:private combiner-info "kotoba/envelope/v1 hybrid-kem")

(defn generate-keypair
  "-> {:priv Uint8Array :pub Uint8Array} for the ML-KEM-768 half.
  The X25519 half is `kotoba.signal.x25519/generate-keypair`; a hybrid
  recipient publishes both."
  []
  (let [kp (.keygen ml_kem768)]
    {:priv (.-secretKey kp) :pub (.-publicKey kp)}))

(defn- concat-bytes [& arrays]
  (let [total (reduce + 0 (map #(.-length ^js %) arrays))
        out (js/Uint8Array. total)]
    (reduce (fn [off ^js a] (.set out a off) (+ off (.-length a))) 0 arrays)
    out))

(defn combiner-input
  "The bytes the hybrid combiner feeds HKDF: `{:ikm x25519-shared ||
  mlkem-shared, :info combiner-info || eph-pub || recipient-pub || pq-ct}`,
  with no salt.

  Binding the transcript is what stops a wrap being replayed against a
  different recipient or with a substituted encapsulation: change any of
  them and the derived key changes, so the AEAD fails to open instead of
  opening as something plausible.

  Public, and separate from the HKDF call, so that `envelope.seal` (Web
  Crypto HKDF, a Promise) and `envelope.seal-sync` (a synchronous HKDF) derive
  the wrap key from ONE definition of the transcript. Two copies of this
  concatenation would be two places for the order of four fields to drift,
  and the first sign of that would be an AEAD failure -- which is what a
  forged wrap looks like too."
  [x-shared pq-shared eph-pub recipient-pub pq-ct]
  {:ikm (concat-bytes x-shared pq-shared)
   :info (concat-bytes (.encode (js/TextEncoder.) combiner-info)
                       eph-pub recipient-pub pq-ct)})

(defn- combine [x-shared pq-shared eph-pub recipient-pub pq-ct]
  (let [{:keys [ikm info]} (combiner-input x-shared pq-shared eph-pub recipient-pub pq-ct)]
    (hkdf/hkdf nil ikm info wrap-key-bytes)))

(defn encapsulate-shared
  "Sender side, up to but not including HKDF -- every step here is
  synchronous (X25519 and ML-KEM both are; only Web Crypto's HKDF is not).
  -> {:x-shared :pq-shared :ephemeral-pub :pq-ct}. Fresh ephemeral X25519 and
  a fresh ML-KEM encapsulation per call."
  [{:keys [pub pq-pub]}]
  (let [{eph-priv :priv eph-pub :pub} (x25519/generate-keypair)
        enc (.encapsulate ml_kem768 pq-pub)]
    {:x-shared (x25519/dh eph-priv pub)
     :pq-shared (.-sharedSecret enc)
     :ephemeral-pub eph-pub
     :pq-ct (.-cipherText enc)}))

(defn decapsulate-shared
  "Recipient side, up to but not including HKDF. -> {:x-shared :pq-shared}."
  [{:keys [priv pq-priv]} {:keys [ephemeral-pub pq-ct]}]
  {:x-shared (x25519/dh priv ephemeral-pub)
   :pq-shared (.decapsulate ml_kem768 pq-ct pq-priv)})

(defn encapsulate
  "Sender side. `recipient` is `{:pub x25519-pub :pq-pub ml-kem-pub}`.

  -> Promise<{:wrap-key :ephemeral-pub :pq-ct}>. Fresh ephemeral X25519 and a
  fresh ML-KEM encapsulation per call, so two wraps of the same key to the
  same recipient share no material."
  [{:keys [pub] :as recipient}]
  (let [{:keys [x-shared pq-shared ephemeral-pub pq-ct]} (encapsulate-shared recipient)]
    (-> (combine x-shared pq-shared ephemeral-pub pub pq-ct)
        (.then (fn [wk] {:wrap-key wk :ephemeral-pub ephemeral-pub :pq-ct pq-ct})))))

(defn decapsulate
  "Recipient side. -> Promise<wrap-key>. Rejects if either half is wrong;
  there is no partial success, which is the point of the combiner."
  [keys {:keys [ephemeral-pub pq-ct recipient-pub] :as wrap}]
  (let [{:keys [x-shared pq-shared]} (decapsulate-shared keys wrap)]
    (combine x-shared pq-shared ephemeral-pub recipient-pub pq-ct)))
