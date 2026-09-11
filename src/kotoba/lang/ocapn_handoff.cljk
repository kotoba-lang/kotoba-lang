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

(defprotocol ^:private FutureGiftValue
  (-future-gift-state [future]))

(deftype ^:private FutureGift [state]
  FutureGiftValue
  (-future-gift-state [_] state))

(defprotocol ^:private AdmittedGiftValue
  (-gift-target [gift])
  (-gift-audit [gift]))

(deftype ^:private AdmittedGift [target audit]
  AdmittedGiftValue
  (-gift-target [_] target)
  (-gift-audit [_] audit))

(defn gift-store
  "Create one session-scoped gift store.

  Consumed gift identifiers remain as replay tombstones for the lifetime of
  the store.  A session owner should discard the entire store when its CapTP
  sessions close rather than trying to serialize or reuse it."
  []
  (GiftStore. (atom {:gifts {}
                     :pending {}
                     :used-gift-ids #{}
                     :used-handoff-counts #{}})))
(defn gift-store? [x] (satisfies? GiftStoreValue x))
(defn future-gift? [x] (satisfies? FutureGiftValue x))
(defn admitted-gift? [x] (satisfies? AdmittedGiftValue x))
(defn gift-target [gift] (when (admitted-gift? gift) (-gift-target gift)))
(defn gift-description [gift] (when (admitted-gift? gift) (-gift-audit gift)))

(defn- byte-array-value? [x]
  #?(:clj (= (class x) (Class/forName "[B"))
     :cljs (instance? js/Uint8Array x)))

(defn- octets [x] (mapv #(bit-and 0xff %) x))
(defn- byte-count [x]
  #?(:clj (alength ^bytes x)
     :cljs (.-length x)))
(defn- fixed-bytes? [x size]
  (and (byte-array-value? x) (= size (byte-count x))))
(defn- gift-id? [x]
  (and (byte-array-value? x) (<= 1 (byte-count x) 64)))

(defn- public-key-value? [x]
  ;; Raw bytes and records are retained for injected/private verifier profiles.
  ;; The vector branch is the OCapN Ed25519 component emitted by Endo 1.1.1:
  ;; ['public-key ['ecc ['curve 'Ed25519] ['flags 'eddsa] ['q <32 bytes>]]]
  (or (byte-array-value? x)
      (captp/syrup-record? x)
      (and (vector? x)
           (= 2 (count x))
           (= 'public-key (first x))
           (vector? (second x))
           (= 4 (count (second x)))
           (let [[scheme curve flags q] (second x)]
             (and (= 'ecc scheme)
                  (= ['curve 'Ed25519] curve)
                  (= ['flags 'eddsa] flags)
                  (vector? q) (= 2 (count q))
                  (= 'q (first q))
                  (fixed-bytes? (second q) 32))))))

(defn- signature-value? [x]
  ;; Legacy injected verifiers may use an opaque bytestring.  The vector branch
  ;; is the interoperable OCapN Ed25519 signature component.
  (or (byte-array-value? x)
      (and (vector? x)
           (= 2 (count x))
           (= 'sig-val (first x))
           (vector? (second x))
           (= 3 (count (second x)))
           (let [[scheme r s] (second x)]
             (and (= 'eddsa scheme)
                  (vector? r) (= 2 (count r))
                  (vector? s) (= 2 (count s))
                  (= 'r (first r)) (fixed-bytes? (second r) 32)
                  (= 's (first s)) (fixed-bytes? (second s) 32))))))

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
    (when-not (signature-value? signature)
      (throw (ex-info "handoff signer returned no signature"
                      {:problem :handoff/signature-invalid})))
    (captp/syrup-record 'desc:sig-envelope [record signature])))

(defn handoff-give!
  "Create the Gifter's signed, inert handoff certificate."
  [sign! {:keys [receiver-key exporter-location session-id gifter-side gift-id]}]
  (when-not (and (public-key-value? receiver-key)
                 (captp/syrup-record? exporter-location)
                 (fixed-bytes? session-id 32)
                 (fixed-bytes? gifter-side 32)
                 (gift-id? gift-id))
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

(defn- resolve-future!
  [future target audit]
  (let [listeners (atom nil)]
    (swap! (-future-gift-state future)
           (fn [state]
             (if (= :pending (:status state))
               (do (reset! listeners (:listeners state))
                   {:status :fulfilled
                    :gift (AdmittedGift. target audit)})
               state)))
    (doseq [listener @listeners]
      ;; Delivery is already committed. A consumer callback cannot roll it
      ;; back or make a valid deposit appear to fail.
      (try
        (listener (:gift @(-future-gift-state future)))
        (catch #?(:clj Throwable :cljs :default) _ nil)))))

(defn future-gift-settlement
  "Return inert status, or the opaque admitted gift after future deposit."
  [future]
  (when (future-gift? future)
    (let [{:keys [status gift]} @(-future-gift-state future)]
      (cond-> {:handoff/status status}
        gift (assoc :handoff/gift gift)))))

(defn listen-future-gift!
  "Register a lexical callback. It runs once, now or after deposit."
  [future listener]
  (when-not (and (future-gift? future) (fn? listener))
    (throw (ex-info "future gift and listener required"
                    {:problem :handoff/future-listener-invalid})))
  (let [ready (atom nil)]
    (swap! (-future-gift-state future)
           (fn [state]
             (case (:status state)
               :pending (update state :listeners conj listener)
               :fulfilled (do (reset! ready (:gift state)) state)
               state)))
    (when @ready
      (try
        (listener @ready)
        (catch #?(:clj Throwable :cljs :default) _ nil)))
    future))

(defn deposit-gift!
  "Verify and deposit one gift under the authenticated Gifter-Exporter session."
  [store verify! gifter-public-key
   {:keys [session-id gifter-side]} signed-give target]
  (when-not (gift-store? store)
    (throw (ex-info "gift store required" {:problem :handoff/store-required})))
  (let [[give signature] (envelope-parts signed-give 'desc:handoff-give 5)
        [receiver-key exporter-location give-session give-side gift-id]
        (:syrup/fields give)]
    (when-not (and (fixed-bytes? session-id 32)
                   (fixed-bytes? gifter-side 32)
                   (public-key-value? receiver-key)
                   (captp/syrup-record? exporter-location)
                   (fixed-bytes? give-session 32)
                   (fixed-bytes? give-side 32)
                   (gift-id? gift-id)
                   (signature-value? signature))
      (throw (ex-info "handoff-give fields are invalid"
                      {:problem :handoff/give-fields-invalid})))
    (when-not (and (= (octets session-id) (octets give-session))
                   (= (octets gifter-side) (octets give-side)))
      (throw (ex-info "gift is not bound to this CapTP session"
                      {:problem :handoff/gifter-session-mismatch})))
    (verified! verify! gifter-public-key give signature
               :handoff/give-signature-invalid)
    (let [key [(octets give-session) (octets give-side) (octets gift-id)]
          outcome (atom nil)]
      (swap! (-gift-state store)
             (fn [state]
               (cond
                 (contains? (:used-gift-ids state) key)
                 (do (reset! outcome :replay) state)

                 (contains? (:pending state) key)
                 (let [{:keys [future audit]} (get-in state [:pending key])]
                   (reset! outcome {:future future :audit audit})
                   (-> state
                       (update :pending dissoc key)
                       (update :used-gift-ids conj key)))

                 :else
                 (do (reset! outcome :stored)
                     (-> state
                         (assoc-in [:gifts key] target)
                         (update :used-gift-ids conj key))))))
      (when (= :replay @outcome)
        (throw (ex-info "gift identifier was already deposited"
                        {:problem :handoff/gift-replay})))
      (when (map? @outcome)
        (resolve-future! (:future @outcome) target (:audit @outcome)))
      {:handoff/deposited? true
       :handoff/delivered-to-waiter? (map? @outcome)
       :handoff/gift-id (octets gift-id)})))

(defn- verified-withdrawal
  [verify! resolve-gifter-key!
   {:keys [receiving-session receiving-side]} signed-receive]
  (let [[receive receive-signature]
        (envelope-parts signed-receive 'desc:handoff-receive 4)
        [receive-session receive-side handoff-count signed-give]
        (:syrup/fields receive)
        [give give-signature]
        (envelope-parts signed-give 'desc:handoff-give 5)
        [receiver-key _ give-session give-side gift-id] (:syrup/fields give)]
    (when-not (and (fixed-bytes? receiving-session 32)
                   (fixed-bytes? receiving-side 32)
                   (fixed-bytes? receive-session 32)
                   (fixed-bytes? receive-side 32)
                   (int? handoff-count) (not (neg? handoff-count))
                   (public-key-value? receiver-key)
                   (fixed-bytes? give-session 32)
                   (fixed-bytes? give-side 32)
                   (gift-id? gift-id)
                   (signature-value? receive-signature)
                   (signature-value? give-signature)
                   (= (octets receiving-session) (octets receive-session))
                   (= (octets receiving-side) (octets receive-side)))
      (throw (ex-info "handoff is not bound to the receiving session"
                      {:problem :handoff/receiver-session-mismatch})))
    (verified! verify! receiver-key receive receive-signature
               :handoff/receive-signature-invalid)
    (let [gifter-key (resolve-gifter-key! give-session give-side)]
      (verified! verify! gifter-key give give-signature
                 :handoff/give-signature-invalid))
    {:gift-key [(octets give-session) (octets give-side) (octets gift-id)]
     :replay-key [(octets receive-session) handoff-count]
     :audit {:handoff/session (octets receive-session)
             :handoff/count handoff-count
             :handoff/gift-id (octets gift-id)}}))

(defn withdraw-gift!
  "Verify both certificates, reject replay, and return opaque gifted authority.

  RESOLVE-GIFTER-KEY! receives the Gifter-Exporter session ID and public side;
  it must return that authenticated session's public key."
  [store verify! resolve-gifter-key!
   {:keys [receiving-session receiving-side]} signed-receive]
  (when-not (and (gift-store? store) (fn? resolve-gifter-key!))
    (throw (ex-info "handoff verifier capabilities required"
                    {:problem :handoff/verifier-required})))
  (let [{:keys [gift-key replay-key audit]}
        (verified-withdrawal verify! resolve-gifter-key!
                             {:receiving-session receiving-session
                              :receiving-side receiving-side}
                             signed-receive)
        outcome (atom nil)]
    (swap! (-gift-state store)
           (fn [state]
             (cond
               (contains? (:used-handoff-counts state) replay-key)
               (do (reset! outcome {:kind :replay}) state)

               (contains? (:pending state) gift-key)
               (do (reset! outcome {:kind :reserved}) state)

               (not (contains? (:gifts state) gift-key))
               (do (reset! outcome {:kind :unavailable}) state)

               :else
               (let [target (get-in state [:gifts gift-key])]
                 (reset! outcome {:kind :claimed :target target})
                 (-> state
                     (update :used-handoff-counts conj replay-key)
                     (update :gifts dissoc gift-key))))))
    (case (:kind @outcome)
      :replay (throw (ex-info "handoff count was already used"
                              {:problem :handoff/count-replay}))
      :reserved (throw (ex-info "gift is reserved by a pending withdrawal"
                                {:problem :handoff/gift-reserved}))
      :unavailable (throw (ex-info "gift has not been deposited"
                                   {:problem :handoff/gift-unavailable}))
      :claimed (AdmittedGift. (:target @outcome) audit))))

(defn withdraw-gift-deferred!
  "Verify and reserve a withdrawal even when deposit has not arrived yet.

  The returned opaque future is fulfilled exactly once by a later deposit.
  A second waiter, reused handoff count, or replayed deposit fails closed."
  [store verify! resolve-gifter-key!
   {:keys [receiving-session receiving-side]} signed-receive]
  (when-not (and (gift-store? store) (fn? resolve-gifter-key!))
    (throw (ex-info "handoff verifier capabilities required"
                    {:problem :handoff/verifier-required})))
  (let [{:keys [gift-key replay-key audit]}
        (verified-withdrawal verify! resolve-gifter-key!
                             {:receiving-session receiving-session
                              :receiving-side receiving-side}
                             signed-receive)
        future (FutureGift. (atom {:status :pending :listeners []}))
        outcome (atom nil)]
    (swap! (-gift-state store)
           (fn [state]
             (cond
               (contains? (:used-handoff-counts state) replay-key)
               (do (reset! outcome {:kind :replay}) state)

               (contains? (:pending state) gift-key)
               (do (reset! outcome {:kind :reserved}) state)

               (contains? (:gifts state) gift-key)
               (let [target (get-in state [:gifts gift-key])]
                 (reset! outcome {:kind :claimed :target target})
                 (-> state
                     (update :used-handoff-counts conj replay-key)
                     (update :gifts dissoc gift-key)))

               :else
               (do (reset! outcome {:kind :waiting})
                   (-> state
                       (update :used-handoff-counts conj replay-key)
                       (assoc-in [:pending gift-key]
                                 {:future future :audit audit}))))))
    (case (:kind @outcome)
      :replay (throw (ex-info "handoff count was already used"
                              {:problem :handoff/count-replay}))
      :reserved (throw (ex-info "gift already has a pending withdrawal"
                                {:problem :handoff/gift-reserved}))
      :waiting future
      :claimed (do (resolve-future! future (:target @outcome) audit) future))))
