(ns aiueos.enroll
  "Device identity and QR pairing — the decision half of \"power it on, scan the
  label, and it is yours\".

  Contract: `resources/aiueos/enroll_contract.edn`. Every function here is a
  pure decision over data a caller has already gathered; nothing in this
  namespace generates a key, reads a device, or talks to a network. The
  mechanism side (entropy, storage, transport) lives in providers, per the
  ADR-2607241100 split.

  ## The shape of the problem

  A label QR is *static and photographable*. Anything it carries is public the
  moment the box leaves the factory, so the QR may not carry a secret that by
  itself grants control. What makes a claim safe is not the token's secrecy but
  **the window in which the token is answerable**:

  - the token is only answerable while the device is in `:factory` state;
  - the first successful claim moves the device to `:claimed`, and from then on
    the same token is refused (`:already-claimed`);
  - returning to `:factory` requires physical evidence (the reset the operator
    performs on the machine), which a photograph cannot supply.

  So a stolen photo is worth something only against a box that has never been
  claimed and is reachable — and worth nothing the instant the owner claims it.
  That is the same commissioning model consumer device standards converged on,
  and it is chosen here for the same reason.

  ## Two trust grades, never conflated

  `:attested` — the factory provisioned a per-device factory key and issued a
  certificate over (factory public key, hardware serial, model). The device's
  *operational* key is generated on first boot and self-registered under the
  factory key, so the private half never exists outside the device and the
  device's provenance is still checkable.

  `:tofu` — no factory attestation is available; the first claim is trusted on
  first use. This is a real degradation and is reported as such in the verdict
  rather than being smoothed over. A caller that requires provenance rejects a
  `:tofu` grant; a caller that does not may accept it knowingly."
  (:require [clojure.string :as str]))

;; ── device lifecycle ───────────────────────────────────────────────────────

(def states
  "The lifecycle a device moves through. `:released` is a terminal state for the
  current owner binding; only physical reset evidence returns it to `:factory`."
  #{:factory :claimed :released})

(def transitions
  "state -> allowed next states. A transition that is not here is denied; there
  is no default arm."
  {:factory  #{:claimed}
   :claimed  #{:released}
   :released #{:factory}})

(defn transition-allowed?
  [from to]
  (boolean (get-in transitions [from to])))

;; ── QR payload ─────────────────────────────────────────────────────────────
;;
;; Compact, single-line, no percent-encoding surprises. Field order is fixed so
;; the encoding is canonical: the same device always produces the same string,
;; which is what lets a label be reprinted without becoming a second identity.

(def qr-scheme "aiueos")
(def qr-version 1)
(def qr-field-sep ";")
(def qr-kv-sep "=")

(def qr-required-fields
  "Fields a payload must carry. `:did` identifies, `:token` is answerable only
  in `:factory` state, `:endpoint` says where the claim is posted. `:model` is
  present so a console can show something meaningful before it has ever reached
  the device."
  [:did :model :endpoint :token])

(defn- kv [k v] (str (name k) qr-kv-sep v))

(defn qr-payload
  "Render the canonical label payload. Returns a string, or nil if any required
  field is missing or contains a separator (which would make the encoding
  ambiguous — refused rather than escaped, because a label is printed once)."
  [{:keys [did model endpoint token] :as fields}]
  (let [vals* (map #(get fields %) qr-required-fields)]
    (when (and (every? (fn [v] (and (string? v) (seq v))) vals*)
               (not-any? (fn [v] (or (str/includes? v qr-field-sep)
                                     (str/includes? v qr-kv-sep))) vals*))
      (str qr-scheme ":" qr-version qr-field-sep
           (str/join qr-field-sep
                     [(kv :did did) (kv :model model)
                      (kv :endpoint endpoint) (kv :token token)])))))

(defn parse-qr
  "Parse a label payload. Returns a map of fields, or
  `{:aiueos.enroll/error <reason>}` — never a partially-filled map, so a caller
  cannot proceed on half a payload."
  [s]
  (cond
    (not (string? s)) {:aiueos.enroll/error :not-a-string}
    (not (str/starts-with? s (str qr-scheme ":"))) {:aiueos.enroll/error :wrong-scheme}
    :else
    (let [[head & rest*] (str/split s (re-pattern qr-field-sep))
          version (str/replace head (str qr-scheme ":") "")
          pairs (into {} (for [p rest*
                               :let [i (str/index-of p qr-kv-sep)]
                               :when i]
                           [(keyword (subs p 0 i)) (subs p (inc i))]))]
      (cond
        (not= version (str qr-version)) {:aiueos.enroll/error :unsupported-version}
        (not-every? #(seq (str (get pairs % ""))) qr-required-fields)
        {:aiueos.enroll/error :missing-field}
        :else pairs))))

;; ── claim admission ────────────────────────────────────────────────────────

(defn- deny [reason extra]
  (merge {:aiueos/decision :deny :aiueos.enroll/reason reason} extra))

(defn- grant [trust extra]
  (merge {:aiueos/decision :grant :aiueos.enroll/trust trust} extra))

(def deny-reasons
  "Every reason a claim can be refused. A verdict carrying a reason outside this
  set is a bug in the caller, not a new policy."
  #{:not-in-factory-state :token-mismatch :window-expired :no-proof-of-possession
    :device-did-mismatch :attestation-required :attestation-invalid
    :already-claimed :owner-missing})

(def default-policy
  "Deny-by-default in the two places that matter: attestation is *not* required
  by default (a fleet that cannot yet attest must still be able to enrol, and
  the verdict says `:tofu` so the caller knows), and the claim window is finite.
  An infinite window is the one setting that turns a photographed label into a
  permanent key, so there is no value here that means \"no limit\"."
  {:claim-window-ms (* 1000 60 60 24 30)
   :require-attestation? false})

(defn claim
  "Decide whether `claim` may bind `device` to an owner.

  `device` — `{:did :state :token :attested? :attestation-valid? :first-seen-ms}`
  `req`    — `{:did :token :owner :now-ms :possession-proof-valid?}`
  `policy` — `{:claim-window-ms :require-attestation?}`

  `:possession-proof-valid?` is the caller's verified answer to \"did the device
  sign this exact challenge with the operational key behind `:did`\". This
  namespace does not verify signatures; it refuses to decide without the answer,
  which is why a missing proof is `:no-proof-of-possession` rather than false."
  [device req policy]
  (let [{:keys [claim-window-ms require-attestation?]} policy
        window (or claim-window-ms (:claim-window-ms default-policy))
        elapsed (when (and (:now-ms req) (:first-seen-ms device))
                  (- (:now-ms req) (:first-seen-ms device)))]
    (cond
      (nil? (:owner req)) (deny :owner-missing {})
      (not= (:did device) (:did req)) (deny :device-did-mismatch {})
      (= :claimed (:state device)) (deny :already-claimed {})
      (not= :factory (:state device)) (deny :not-in-factory-state
                                            {:aiueos.enroll/state (:state device)})
      (not= (:token device) (:token req)) (deny :token-mismatch {})
      (not (true? (:possession-proof-valid? req))) (deny :no-proof-of-possession {})
      (and elapsed (> elapsed window)) (deny :window-expired
                                             {:aiueos.enroll/elapsed-ms elapsed
                                              :aiueos.enroll/window-ms window})
      (and require-attestation? (not (:attested? device)))
      (deny :attestation-required {})
      (and (:attested? device) (not (true? (:attestation-valid? device))))
      (deny :attestation-invalid {})
      :else (grant (if (:attested? device) :attested :tofu)
                   {:aiueos.enroll/did (:did device)
                    :aiueos.enroll/owner (:owner req)
                    :aiueos.enroll/next-state :claimed}))))

(defn rebind
  "Decide whether an already-claimed device may be bound to a new owner.

  A photograph of the label is not evidence here — the token is not consulted at
  all. Exactly one of two things must hold: the current owner signed the
  transfer, or the operator performed the physical reset on the machine."
  [device req]
  (cond
    (not= (:did device) (:did req)) (deny :device-did-mismatch {})
    (not= :claimed (:state device)) (deny :not-in-factory-state
                                          {:aiueos.enroll/state (:state device)})
    (true? (:current-owner-signature-valid? req))
    (grant (if (:attested? device) :attested :tofu)
           {:aiueos.enroll/did (:did device)
            :aiueos.enroll/owner (:owner req)
            :aiueos.enroll/next-state :released
            :aiueos.enroll/via :owner-signature})
    (true? (:physical-reset-evidence? req))
    (grant (if (:attested? device) :attested :tofu)
           {:aiueos.enroll/did (:did device)
            :aiueos.enroll/owner (:owner req)
            :aiueos.enroll/next-state :released
            :aiueos.enroll/via :physical-reset})
    :else (deny :no-proof-of-possession {})))

(defn granted? [verdict] (= :grant (:aiueos/decision verdict)))
