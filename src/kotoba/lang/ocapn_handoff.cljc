(ns kotoba.lang.ocapn-handoff
  "Fail-closed CapTP third-party handoff certificates and single-use gift store.

  Signing and verification are injected live capabilities. Certificate records
  remain inert Syrup data; only an opaque admitted gift carries authority."
  (:require [kotoba.lang.captp-runtime :as captp]))

(defprotocol ^:private GiftStoreValue
  (-gift-state [store]))

(deftype ^:private GiftStore [state]
  GiftStoreValue
  (-gift-state [_] state))

(defprotocol ^:private AdmittedGiftValue
  (-gift-target [gift])
  (-gift-audit [gift]))

(deftype ^:private AdmittedGift [target audit]
  AdmittedGiftValue
  (-gift-target [_] target)
  (-gift-audit [_] audit))

(defn gift-store [] (GiftStore. (atom {:gifts {} :used-handoff-counts #{}})))
(defn gift-store? [x] (satisfies? GiftStoreValue x))
(defn admitted-gift? [x] (satisfies? AdmittedGiftValue x))
(defn gift-target [gift] (when (admitted-gift? gift) (-gift-target gift)))
(defn gift-description [gift] (when (admitted-gift? gift) (-gift-audit gift)))

(defn- byte-array-value? [x]
  #?(:clj (= (class x) (Class/forName "[B"))
     :cljs (instance? js/Uint8Array x)))

(defn- octets [x] (mapv #(bit-and 0xff %) x))
(defn- fixed-bytes? [x size] (and (byte-array-value? x) (= size (count x))))

(defn- envelope-parts [envelope expected-label field-count]
  (when-not (and (captp/syrup-record? envelope)
                 (= 'desc:sig-envelope (:syrup/record envelope))
                 (= 2 (count (:syrup/fields envelope))))
    (throw (ex-info "signed CapTP envelope required"
                    {:problem :handoff/envelope-invalid})))
  (let [[signed signature] (:syrup/fields envelope)]
    (when-not (and (captp/syrup-record? signed)
                   (= expected-label (:syrup/record signed))
                   (= field-count (count (:syrup/fields signed))))
      (throw (ex-info "CapTP handoff record is invalid"
                      {:problem :handoff/record-invalid})))
    [signed signature]))

(defn- sign-envelope! [sign! record]
  (when-not (fn? sign!)
    (throw (ex-info "handoff signer capability required"
                    {:problem :handoff/signer-required})))
  (let [signature (sign! (captp/syrup-encode record))]
    (when-not (byte-array-value? signature)
      (throw (ex-info "handoff signer returned no signature"
                      {:problem :handoff/signature-invalid})))
    (captp/syrup-record 'desc:sig-envelope [record signature])))

(defn handoff-give!
  "Create the Gifter's signed, inert handoff certificate."
  [sign! {:keys [receiver-key exporter-location session-id gifter-side gift-id]}]
  (when-not (and (or (byte-array-value? receiver-key) (captp/syrup-record? receiver-key))
                 (captp/syrup-record? exporter-location)
                 (fixed-bytes? session-id 32)
                 (fixed-bytes? gifter-side 32)
                 (fixed-bytes? gift-id 32))
    (throw (ex-info "handoff-give fields are invalid"
                    {:problem :handoff/give-fields-invalid})))
  (sign-envelope!
   sign!
   (captp/syrup-record 'desc:handoff-give
                       [receiver-key exporter-location session-id
                        gifter-side gift-id])))

(defn handoff-receive!
  "Bind the Receiver's current Exporter session to a signed give."
  [sign! {:keys [receiving-session receiving-side handoff-count signed-give]}]
  (envelope-parts signed-give 'desc:handoff-give 5)
  (when-not (and (fixed-bytes? receiving-session 32)
                 (fixed-bytes? receiving-side 32)
                 (int? handoff-count) (not (neg? handoff-count)))
    (throw (ex-info "handoff-receive fields are invalid"
                    {:problem :handoff/receive-fields-invalid})))
  (sign-envelope!
   sign!
   (captp/syrup-record 'desc:handoff-receive
                       [receiving-session receiving-side handoff-count
                        signed-give])))

(defn- verified! [verify! public-key record signature problem]
  (when-not (and (fn? verify!)
                 (true? (verify! public-key
                                 (captp/syrup-encode record)
                                 signature)))
    (throw (ex-info "handoff signature verification failed"
                    {:problem problem}))))

(defn deposit-gift!
  "Verify and deposit one gift under the authenticated Gifter-Exporter session."
  [store verify! gifter-public-key
   {:keys [session-id gifter-side]} signed-give target]
  (when-not (gift-store? store)
    (throw (ex-info "gift store required" {:problem :handoff/store-required})))
  (let [[give signature] (envelope-parts signed-give 'desc:handoff-give 5)
        [_ _ give-session give-side gift-id] (:syrup/fields give)]
    (when-not (and (= (octets session-id) (octets give-session))
                   (= (octets gifter-side) (octets give-side)))
      (throw (ex-info "gift is not bound to this CapTP session"
                      {:problem :handoff/gifter-session-mismatch})))
    (verified! verify! gifter-public-key give signature
               :handoff/give-signature-invalid)
    (let [key [(octets give-session) (octets give-side) (octets gift-id)]
          added? (atom false)]
      (swap! (-gift-state store)
             (fn [state]
               (if (contains? (:gifts state) key)
                 state
                 (do (reset! added? true)
                     (assoc-in state [:gifts key] target)))))
      (when-not @added?
        (throw (ex-info "gift identifier was already deposited"
                        {:problem :handoff/gift-replay})))
      {:handoff/deposited? true :handoff/gift-id (octets gift-id)})))

(defn withdraw-gift!
  "Verify both certificates, reject replay, and return opaque gifted authority.

  RESOLVE-GIFTER-KEY! receives the Gifter-Exporter session ID and public side;
  it must return that authenticated session's public key."
  [store verify! resolve-gifter-key!
   {:keys [receiving-session receiving-side]} signed-receive]
  (when-not (and (gift-store? store) (fn? resolve-gifter-key!))
    (throw (ex-info "handoff verifier capabilities required"
                    {:problem :handoff/verifier-required})))
  (let [[receive receive-signature]
        (envelope-parts signed-receive 'desc:handoff-receive 4)
        [receive-session receive-side handoff-count signed-give]
        (:syrup/fields receive)
        [give give-signature]
        (envelope-parts signed-give 'desc:handoff-give 5)
        [receiver-key _ give-session give-side gift-id] (:syrup/fields give)]
    (when-not (and (= (octets receiving-session) (octets receive-session))
                   (= (octets receiving-side) (octets receive-side)))
      (throw (ex-info "handoff is not bound to the receiving session"
                      {:problem :handoff/receiver-session-mismatch})))
    (verified! verify! receiver-key receive receive-signature
               :handoff/receive-signature-invalid)
    (let [gifter-key (resolve-gifter-key! give-session give-side)]
      (verified! verify! gifter-key give give-signature
                 :handoff/give-signature-invalid))
    (let [gift-key [(octets give-session) (octets give-side) (octets gift-id)]
          replay-key [(octets receive-session) handoff-count]
          target (get-in @(-gift-state store) [:gifts gift-key])]
      (when-not target
        (throw (ex-info "gift has not been deposited"
                        {:problem :handoff/gift-unavailable})))
      (let [claimed? (atom false)]
        (swap! (-gift-state store)
               (fn [state]
                 (if (contains? (:used-handoff-counts state) replay-key)
                   state
                   (do (reset! claimed? true)
                       (-> state
                           (update :used-handoff-counts conj replay-key)
                           (update :gifts dissoc gift-key))))))
        (when-not @claimed?
          (throw (ex-info "handoff count was already used"
                          {:problem :handoff/count-replay})))
        (AdmittedGift.
         target
         {:handoff/session (octets receive-session)
          :handoff/count handoff-count
          :handoff/gift-id (octets gift-id)})))))
