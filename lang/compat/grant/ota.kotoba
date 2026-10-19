(ns grant.ota
  "The end-to-end update: what `grant.publisher` admits, what `grant.update`
  advances, and the receipt that says what actually happened.

  Contract: `resources/aiueos/ota_contract.edn`. Pure decisions; fetching bytes,
  hashing them and writing partitions are provider mechanism.

  ## The hole this namespace closes

  `grant.update/admit-step` takes `:publisher-admitted?` as a bare boolean, and
  checks it once, at the transition into `:fetched`. Nothing binds that boolean
  to a *release*. A caller holding a verdict about release 41 can advance an
  update carrying release 42's bytes and every check still passes, because the
  two namespaces never compare notes about which release they are each talking
  about.

  So the unit of composition here is not a boolean, it is an **admission**: a
  publisher verdict stamped with the identity of the release it was about, and
  the time it was taken. Every subsequent step re-checks that stamp against the
  release in hand, and refuses on `:admission-for-another-release` rather than
  proceeding on a verdict that was true of something else.

  ## An admission is not permanent

  Freshness is checked when the release is admitted. Staging can take a long
  time — a blue-green guest has to come up, a probe has to answer — and a freeze
  attack is exactly an attacker who lets time pass. So `advance` re-checks the
  admission's age against `:max-admission-age-ms` and expires it, rather than
  treating \"it was fresh when we started\" as \"it is fresh now\".

  ## Identity is supplied, not computed

  `:manifest-id` is a digest the caller computed. This namespace never hashes:
  `grant.signing/sha256-hex` is JVM-only and a second canonicaliser would be a
  signature-confusion hazard (`grant.key-lifecycle/document-bytes` says so in
  its own docstring). What this namespace guarantees is that **the same id flows
  through every step** — it cannot tell you the id is the right one, only that
  nothing swapped it midway."
  (:require [grant.publisher :as publisher]
            [grant.update :as update]))

(def deny-reasons
  "Reasons `grant.ota` itself produces. Reasons from `grant.publisher` and
  `grant.update` pass through unchanged — a caller reading a verdict should not
  have to guess which layer refused, and re-labelling would lose that."
  #{:no-admission :admission-for-another-release :admission-expired
    :manifest-id-missing :artifact-digest-mismatch :artifact-missing
    :release-not-admitted})

(def default-policy
  "`:max-admission-age-ms` is deliberately shorter than the publisher's own
  freshness TTL: the publisher answers \"is this release current\", this answers
  \"is our decision about it still warm\". A staging window longer than this
  re-admits rather than proceeding on a cold verdict."
  {:max-admission-age-ms (* 1000 60 30)})

(defn- deny [reason extra]
  (merge {:aiueos/decision :deny :aiueos.ota/reason reason} extra))

;; ── artifacts ──────────────────────────────────────────────────────────────

(defn artifact-index
  "Manifest artifacts -> `{kind digest}`. Pure; the digests are the caller's."
  [artifacts]
  (into {} (map (juxt :kind :sha256)) artifacts))

(defn verify-artifacts
  "Do the bytes the provider fetched match what the manifest names?

  `observed` — `{kind digest}` for what was actually fetched and hashed.

  An artifact named by the manifest and absent from `observed` is a
  **mismatch**, not a skip. A partially-fetched update that verified the half it
  managed to fetch is the case where applying the understood half is worse than
  applying nothing — the same rule `grant.update/unknown-kinds` states for
  kinds, applied to bytes."
  [artifacts observed]
  (let [want (artifact-index artifacts)
        missing (remove #(contains? observed (key %)) want)
        wrong (filter (fn [[k d]] (and (contains? observed k)
                                       (not= d (get observed k))))
                      want)]
    {:aiueos.ota/ok? (and (empty? missing) (empty? wrong))
     :aiueos.ota/missing (mapv key missing)
     :aiueos.ota/mismatched (mapv key wrong)}))

;; ── admission ──────────────────────────────────────────────────────────────

(defn admit
  "Ask `grant.publisher` about a release, and stamp the answer with the
  release's identity and the time it was taken.

  Returns `{:aiueos.ota/admission {...}}` on a grant and the publisher's own
  denial otherwise — unchanged, so the reason a release was refused is the
  publisher's reason and not a paraphrase."
  ([manifest state] (admit manifest state publisher/default-policy))
  ([manifest state policy]
   (cond
     (nil? (:manifest-id manifest)) (deny :manifest-id-missing {})
     :else
     (let [v (publisher/admit-release manifest state policy)]
       (if (publisher/admitted? v)
         {:aiueos/decision :grant
          :aiueos.ota/admission {:manifest-id (:manifest-id manifest)
                                 :sequence (:sequence manifest)
                                 :taken-ms (:now-ms state)
                                 :verdict v}}
         v)))))

(defn admission-usable?
  "Is `admission` still about this release, and still warm?

  Split out from `advance` because a caller may want to know before it starts a
  step that will take minutes. Returns nil when usable, or a denial."
  ([admission manifest-id now-ms] (admission-usable? admission manifest-id now-ms default-policy))
  ([admission manifest-id now-ms policy]
   (let [policy (merge default-policy policy)
         age (when (and now-ms (:taken-ms admission)) (- now-ms (:taken-ms admission)))]
     (cond
       (nil? admission) (deny :no-admission {})
       (nil? manifest-id) (deny :manifest-id-missing {})
       (not= manifest-id (:manifest-id admission))
       (deny :admission-for-another-release
             {:aiueos.ota/admitted-id (:manifest-id admission)
              :aiueos.ota/in-hand-id manifest-id})
       (and age (> age (:max-admission-age-ms policy)))
       (deny :admission-expired {:aiueos.ota/age-ms age
                                 :aiueos.ota/maximum-ms (:max-admission-age-ms policy)})
       :else nil))))

;; ── advancing ──────────────────────────────────────────────────────────────

(defn advance
  "One step of the update, with the admission re-checked at every step.

  `ota` — `{:manifest-id :admission :step :class :kinds :observed-digests
            :artifacts :now-ms}` plus everything `grant.update/admit-step`
  reads (`:previous-preserved?`, `:health`, `:machine-busy?`, …).

  The order is the order in which a wrong answer is cheapest: is this admission
  even about this release, are the bytes what was promised, and only then may
  the step machine speak."
  ([ota] (advance ota default-policy))
  ([ota policy]
   (or (admission-usable? (:admission ota) (:manifest-id ota) (:now-ms ota) policy)
       (let [checked (when (seq (:artifacts ota))
                       (verify-artifacts (:artifacts ota) (or (:observed-digests ota) {})))]
         (cond
           (and checked (seq (:aiueos.ota/missing checked)))
           (deny :artifact-missing {:aiueos.ota/missing (:aiueos.ota/missing checked)})

           (and checked (seq (:aiueos.ota/mismatched checked)))
           (deny :artifact-digest-mismatch
                 {:aiueos.ota/mismatched (:aiueos.ota/mismatched checked)})

           :else
           ;; the boolean update/ admit-step wants is now DERIVED from an
           ;; admission that has just been shown to be about this release,
           ;; rather than passed in by a caller who may be holding an older one.
           (update/admit-step (assoc ota :publisher-admitted? true) policy))))))

;; ── receipt ────────────────────────────────────────────────────────────────

(def outcomes #{:committed :rolled-back :refused})

(defn receipt
  "What happened, as a record that outlives the process that did it.

  Carries the identity of the release, the digests on both sides of the swap,
  and the recovery digest that must not have moved. A receipt that cannot be
  checked against the manifest it claims to be about is a log line, not a
  receipt."
  [{:keys [manifest-id sequence class outcome previous-digest updated-digest
           recovery-digest artifacts admission committed-ms]}]
  {:aiueos.ota/manifest-id manifest-id
   :aiueos.ota/sequence sequence
   :aiueos.ota/class class
   :aiueos.ota/outcome outcome
   :aiueos.ota/previous-digest previous-digest
   :aiueos.ota/updated-digest updated-digest
   :aiueos.ota/recovery-digest recovery-digest
   :aiueos.ota/artifacts (artifact-index artifacts)
   :aiueos.ota/admitted-id (:manifest-id admission)
   :aiueos.ota/committed-ms committed-ms})

(def receipt-faults
  #{:manifest-id-mismatch :admission-mismatch :unknown-outcome :recovery-moved
    :swap-did-not-happen :artifacts-not-covered})

(defn verify-receipt
  "Check a receipt against the manifest it claims to describe and the recovery
  digest observed now.

  Returns `{:aiueos.ota/valid? true}` or a map naming every fault found — all of
  them, not the first, because a receipt is read after the fact and a second
  round-trip to discover the next problem is a round-trip to a machine that may
  no longer exist."
  [r manifest observed-recovery-digest]
  (let [faults
        (cond-> []
          (not= (:aiueos.ota/manifest-id r) (:manifest-id manifest))
          (conj :manifest-id-mismatch)

          (and (:aiueos.ota/admitted-id r)
               (not= (:aiueos.ota/admitted-id r) (:aiueos.ota/manifest-id r)))
          (conj :admission-mismatch)

          (not (contains? outcomes (:aiueos.ota/outcome r)))
          (conj :unknown-outcome)

          (and observed-recovery-digest (:aiueos.ota/recovery-digest r)
               (not= observed-recovery-digest (:aiueos.ota/recovery-digest r)))
          (conj :recovery-moved)

          (and (= :committed (:aiueos.ota/outcome r))
               (= (:aiueos.ota/previous-digest r) (:aiueos.ota/updated-digest r)))
          (conj :swap-did-not-happen)

          (not= (set (keys (:aiueos.ota/artifacts r)))
                (set (keys (artifact-index (:artifacts manifest)))))
          (conj :artifacts-not-covered))]
    (if (seq faults)
      {:aiueos.ota/valid? false :aiueos.ota/faults (vec faults)}
      {:aiueos.ota/valid? true})))

(defn granted? [verdict] (= :grant (:aiueos/decision verdict)))
