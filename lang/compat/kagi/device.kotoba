(ns kagi.device
  "Device enrollment — adding a second machine WITHOUT sending it the master
  passphrase.

  ## The gap this closes

  `kagi push`/`pull` already move the vault between machines, but the vault
  arrives as ciphertext and a new machine has no way to open it. Until now the
  only answers were the master passphrase or a Shamir share set, so the one
  factor that is supposed to stay offline had to be typed into every new
  device. 1Password solves the same problem with its Secret Key; this solves it
  with a key exchange, so nothing long-lived is copied at all.

  ## The exchange

      B: kagi device request --label mac-b     -> request.edn + FINGERPRINT
      A: kagi device grant request.edn --fingerprint <what B printed>
                                               -> grant.edn
      B: kagi device accept grant.edn          -> B can unlock, on its own

  A encapsulates the VMK to B's hybrid public key (X25519 + ML-KEM-768, via
  `crypto/share-dek` — the same primitive item sharing uses, applied to the
  VMK). B decapsulates with a private key that never leaves B, then
  IMMEDIATELY creates its own OS-keychain wrap and drops the grant. From then
  on B unlocks like A does and the grant is inert.

  ## Why the fingerprint is a required argument and not a printed nicety

  The only thing standing between this and a man-in-the-middle is that A
  encapsulates to B's REAL public key rather than to an attacker's. A operator
  who pipes `request.edn` straight from an untrusted channel into `grant` has
  no way to notice a substitution. So `grant` REFUSES without `--fingerprint`,
  and the value must match the request's — which means a human read it off B's
  screen and typed it on A. That step cannot be automated away, and making it
  a required argument is the only way to keep it from being skipped.

  ## What this is NOT

  `revoke` removes a device's wrap. It does NOT un-know a VMK that device
  already held: anything with the VMK in memory or on disk keeps it. Real
  revocation is VMK rotation plus a re-wrap for every remaining device, which
  kagi does not implement yet (`rotate` rotates an item DEK, not the VMK).
  `revoke` is therefore an access-list change, and the docstring on
  `revoke-device` says so where an operator will actually read it."
  (:require [clojure.string :as str]
            [kagi.crypto :as crypto]
            [kagi.secret-store :as secret-store]
            [kagi.unlock :as unlock])
  (:import [java.time Instant]
           [java.time.temporal ChronoUnit]
           [java.util Base64 UUID]))

(def ^:const default-ttl-sec 900)

(def ^:private b64-enc (Base64/getEncoder))
(def ^:private b64-dec (Base64/getDecoder))

(defn- b64 [^bytes b] (.encodeToString b64-enc b))
(defn- unb64 ^bytes [^String s] (.decode b64-dec s))

(defn- now-iso []
  (str (.truncatedTo (Instant/now) ChronoUnit/SECONDS)))

(defn- b64-map
  "Encode a `{:x bytes :pq bytes}` hybrid key/ciphertext for transport."
  [m]
  (into {} (map (fn [[k v]] [k (if (bytes? v) (b64 v) v)]) m)))

(defn- unb64-map [m]
  (into {} (map (fn [[k v]] [k (if (string? v) (unb64 v) v)]) m)))

;; ───────────────────────────── fingerprint ─────────────────────────────

(defn fingerprint
  "A short, human-readable digest of a device's PUBLIC key.

  Six groups of four base32-ish characters, because the operator has to read
  this aloud or retype it and a 64-char hex string invites 'looks about
  right'. Derived from the full public key, so substituting the key changes
  it."
  [public-key]
  (let [material (crypto/sha256 (.getBytes (pr-str (b64-map public-key)) "UTF-8"))
        alphabet "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  ; no I/O/0/1
        chars (map #(nth alphabet (mod (bit-and (int %) 0xff) 32)) (take 24 material))]
    (->> chars (partition 4) (map #(apply str %)) (str/join "-"))))

;; ───────────────────────────── B: request ─────────────────────────────

(defn device-secret-ref
  "Where a device keeps its own enrollment private key."
  [device-id]
  (str "keychain://kagi-device/" device-id))

(defn make-request!
  "On the NEW device. Generates a hybrid keypair, stores the private half in
  this device's own secret store, and returns the public request to carry to
  the enrolled device.

  The private key never appears in the request and never leaves this machine."
  [p {:keys [label store]}]
  (let [{:keys [public secret]} (crypto/kem-keypair p)
        device-id (str (UUID/randomUUID))
        ref (device-secret-ref device-id)
        st (or store (secret-store/store-for-ref ref))]
    (secret-store/put-secret! st ref (pr-str (b64-map secret))
                              {:content-type "application/edn"
                               :purpose "kagi-device-enrollment"})
    {:request {:device/id device-id
               :device/label (or label device-id)
               :device/public (b64-map public)
               :device/created-at (now-iso)}
     :fingerprint (fingerprint public)}))

;; ───────────────────────────── A: grant ─────────────────────────────

(defn grant-errors
  "Everything that makes a grant refuse, as data.

  `:fingerprint-missing` and `:fingerprint-mismatch` are separate codes on
  purpose: the first is an operator who skipped the check, the second is an
  operator who did it and it FAILED, which is a possible attack and should
  read differently in a ledger."
  [{:device/keys [id public]} confirmed-fingerprint]
  (cond-> []
    (str/blank? (str id))
    (conj {:rule :malformed-request :detail "device/id が無い"})

    (empty? public)
    (conj {:rule :malformed-request :detail "device/public が無い"})

    (str/blank? (str confirmed-fingerprint))
    (conj {:rule :fingerprint-missing
           :detail "新しい端末が表示した fingerprint を --fingerprint で渡すこと。
                    これを省くと、攻撃者の公開鍵に VMK を封入しても気付けない"})

    (and (seq public) (not (str/blank? (str confirmed-fingerprint)))
         (not= (str/upper-case (str confirmed-fingerprint))
               (fingerprint (unb64-map public))))
    (conj {:rule :fingerprint-mismatch
           :detail "request の公開鍵と読み上げられた fingerprint が一致しない。
                    途中で差し替えられた可能性がある — 中止して経路を確認すること"})))

(defn make-grant
  "On the ENROLLED device. Encapsulates the VMK to the requesting device's
  public key.

  Pure: takes the VMK the caller already unlocked and returns the envelope.
  Persisting nothing here is deliberate — the grant is a message, not state.

  Throws when `grant-errors` finds anything. A grant is the single most
  powerful object this system produces (it carries the whole vault), so it
  fails loudly rather than returning a partial result a caller might use."
  [p ^bytes vmk request confirmed-fingerprint & [{:keys [ttl-sec vault-id]}]]
  (when-let [errs (seq (grant-errors request confirmed-fingerprint))]
    (throw (ex-info "device grant refused" {:device/errors errs})))
  (let [envelope (crypto/share-dek p (unb64-map (:device/public request)) vmk)
        issued (.truncatedTo (Instant/now) ChronoUnit/SECONDS)]
    {:grant/device-id (:device/id request)
     :grant/label (:device/label request)
     :grant/kem-ct (b64-map (:kem-ct envelope))
     :grant/nonce (b64 (:nonce envelope))
     :grant/wrapped (b64 (:wrapped envelope))
     :grant/fingerprint (fingerprint (unb64-map (:device/public request)))
     :grant/vault-id vault-id
     :grant/issued-at (str issued)
     ;; Short by default. A grant sitting in a chat log for a week is a
     ;; standing offer of the whole vault to anyone who also has the device
     ;; key -- and the device key is on a machine that is, by definition, not
     ;; yet trusted enough to have been enrolled.
     :grant/expires-at (str (.plusSeconds issued (or ttl-sec default-ttl-sec)))}))

;; ───────────────────────────── B: accept ─────────────────────────────

(defn accept-errors [grant now-iso-str]
  (cond-> []
    (str/blank? (str (:grant/device-id grant)))
    (conj {:rule :malformed-grant :detail "grant/device-id が無い"})

    (and (:grant/expires-at grant)
         (pos? (compare (str now-iso-str) (str (:grant/expires-at grant)))))
    (conj {:rule :grant-expired
           :detail (str "この grant は " (:grant/expires-at grant) " に失効している")})))

(defn accept-grant!
  "On the NEW device. Recovers the VMK from the grant using this device's own
  private key, then adds an OS-keychain wrap so the grant is never needed
  again.

  Returns `{:vmk :wrap :device-id}`. The caller writes the wrap into the vault
  metadata and drops the grant file — `accept-grant!` deliberately does not
  touch the vault, so the persistence decision (and its ledger entry) stays
  with the caller."
  [p grant {:keys [store now vault-store keychain-ref]}]
  (when-let [errs (seq (accept-errors grant (or now (now-iso))))]
    (throw (ex-info "device grant refused" {:device/errors errs})))
  (let [device-id (:grant/device-id grant)
        ref (device-secret-ref device-id)
        st (or store (secret-store/store-for-ref ref))
        sk (unb64-map (read-string (secret-store/get-secret
                                    st ref {:purpose "kagi-device-enrollment"})))
        vmk (crypto/accept-share p sk {:kem-ct (unb64-map (:grant/kem-ct grant))
                                       :nonce (unb64 (:grant/nonce grant))
                                       :wrapped (unb64 (:grant/wrapped grant))})
        wrap-ref (or keychain-ref (str "keychain://kagi/vmk-unlock-" device-id))]
    {:vmk vmk
     :device-id device-id
     :wrap (unlock/os-keychain-wrap p vmk
                                    (or vault-store (secret-store/store-for-ref wrap-ref))
                                    wrap-ref)}))

;; ───────────────────────────── registry ─────────────────────────────

(defn register
  "Record an enrolled device in the vault metadata.

  The registry is what `ls` and `revoke` operate on; the WRAP is what actually
  unlocks. They are kept as separate fields deliberately — a registry entry
  with no wrap is a device that was recorded and never completed, and that
  should be visible rather than silently equivalent to an enrolled one."
  [meta {:keys [device-id label fingerprint wrap-ref at]}]
  (update meta :device/registry (fnil conj [])
          {:device/id device-id
           :device/label label
           :device/fingerprint fingerprint
           :device/wrap-ref wrap-ref
           :device/enrolled-at (or at (now-iso))}))

(defn devices [meta]
  (vec (:device/registry meta)))

(defn revoke-device
  "Remove a device's unlock wrap and mark its registry entry revoked.

  READ THIS BEFORE RELYING ON IT: this is an ACCESS-LIST change, not
  revocation of knowledge. A device that has already unlocked the vault holds
  the VMK, and removing its wrap does not reach into that machine. Anything it
  copied stays readable to it.

  Real revocation is VMK rotation followed by a re-wrap for every remaining
  device, which kagi does not implement yet. Until it does, treat a lost
  device as a vault compromise and rotate the SECRETS, not the wrap."
  [meta device-id]
  (let [wrap-refs (into #{} (comp (filter #(= device-id (:device/id %)))
                                  (map :device/wrap-ref))
                        (:device/registry meta))]
    (-> meta
        (update :unlock/wraps (fn [ws] (vec (remove #(contains? wrap-refs (:ref %)) ws))))
        (update :device/registry
                (fn [ds] (mapv (fn [d] (cond-> d
                                         (= device-id (:device/id d))
                                         (assoc :device/revoked-at (now-iso))))
                               ds))))))

(defn status
  "What `kagi device ls` shows. No key material, ever."
  [meta]
  {:devices (mapv (fn [d] (select-keys d [:device/id :device/label :device/fingerprint
                                          :device/enrolled-at :device/revoked-at]))
                  (devices meta))
   :unlock (unlock/status meta)})
