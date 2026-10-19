(ns aiueos.publisher
  "Publisher trust and revocation — the decision half of \"this release may
  become the running system\".

  Contract: `resources/aiueos/publisher_contract.edn`. Pure decisions; fetching,
  signature verification and status-list retrieval are provider mechanism. This
  namespace consumes *verified* counts and flags, never raw signatures, for the
  same reason `aiueos.enroll` consumes a verified possession proof.

  ## Why this namespace has to exist before OTA does

  ADR-2608012050 declined binary-cache substitution with a condition attached:
  it may be taken up once *publisher trust and revocation are modelled to the
  same standard as component admission*. An OTA path is exactly that
  substitution, so the condition is now load-bearing. Shipping \"signed updates\"
  without the rest fixes a distribution channel that has no way to say a key is
  no longer trusted — which is the failure that matters, because the signature
  keeps verifying after the key is stolen.

  ## The four questions, and the attack each one answers

  A release is admitted only when all four hold. They are separate because they
  fail separately, and a design that collapses them loses an attack:

  1. **threshold** — at least `k` of the publisher keys named by the current
     root signed this release. Answers: one stolen key.
  2. **revocation** — none of the signing keys is revoked. Answers: a key stolen
     *before* it was known to be stolen, whose old signatures are still valid.
  3. **monotonic sequence** — the release's sequence exceeds the installed one.
     Answers: rollback to a version whose bug is now public.
  4. **freshness** — a short-lived timestamp statement covers this release.
     Answers: freeze — an attacker who simply stops delivering updates, which no
     signature check can detect because nothing invalid was ever presented.

  Roles are named as in TUF, deliberately: the threat model is the same one, and
  inventing new names for it would hide that this is a solved problem being
  reused rather than a new scheme being improvised.

  ## Where the pieces live

  This namespace decides. The revocation bitmap is a
  `org-w3-vc-bitstring-status-list` document; the release manifest and its
  artifacts are content-addressed blocks; ordering across publishers is inga's
  job. None of those are reimplemented here.

  ## Freshness on a machine that may have no clock

  `state` may carry a `:clock` from `aiueos.clock/resolve-time`. When it does,
  freshness is decided there, and its third answer is honoured: a machine that
  can neither accept nor reject **denies the release** with
  `:freshness-undecidable`. It does not fall through to admitting one, and it
  does not report `:timestamp-expired` as though a measurement had been taken.
  Without a `:clock`, `:now-ms` is used directly — which is the hosted profile,
  where a wall clock exists."
  (:require [clojure.set :as set]
            [aiueos.clock :as clock]))

(def roles
  "TUF roles. `:root` names the keys and thresholds for every other role;
  `:targets` signs releases; `:snapshot` binds a consistent set; `:timestamp` is
  short-lived and answers freshness."
  #{:root :targets :snapshot :timestamp})

(def deny-reasons
  #{:below-threshold :key-revoked :key-not-in-root :sequence-not-monotonic
    :timestamp-expired :timestamp-missing :digest-mismatch :unknown-role
    :root-expired :no-signatures :threshold-unsatisfiable :freshness-undecidable})

(defn- deny [reason extra]
  (merge {:aiueos/decision :deny :aiueos.publisher/reason reason} extra))

(defn- admit [extra]
  (merge {:aiueos/decision :grant} extra))

(def default-policy
  "`:freshness-ttl-ms` is short on purpose: it is the whole of the anti-freeze
  answer, and a long TTL is indistinguishable from not having one. `:offline-
  grace-ms` is what a machine on a flaky link is allowed to keep running on
  after the timestamp expires — it does *not* extend admission of new releases,
  only tolerance of the existing one."
  {:freshness-ttl-ms (* 1000 60 60 24)
   :offline-grace-ms (* 1000 60 60 24 7)
   :min-threshold 2})

(defn revoked?
  "Is `index` revoked in a decoded bitstring status list? `bits` is the decoded
  bitmap as a vector/seq of 0/1 — decoding the base64url+gzip envelope is the
  status-list library's job, not this one.

  An index outside the bitmap is **revoked**, not absent. A status list that has
  not yet grown to cover a key cannot be read as vouching for it."
  [bits index]
  (let [v (vec bits)]
    (if (or (nil? index) (neg? index) (>= index (count v)))
      true
      (= 1 (nth v index)))))

(defn signing-keys-ok
  "Split `signatures` into keys the current root names and keys it does not, and
  count the ones that are both named and unrevoked.

  `signatures` — `[{:key-id :verified? :status-index} …]` where `:verified?` is
  the provider's answer for that one signature.
  `root`       — `{:keys #{key-id} :threshold n}`
  `bits`       — decoded revocation bitmap."
  [signatures root bits]
  (let [named (set (:keys root))
        verified (filter :verified? signatures)
        in-root (filter #(contains? named (:key-id %)) verified)
        {revoked true live false} (group-by #(revoked? bits (:status-index %)) in-root)]
    {:aiueos.publisher/verified (count verified)
     :aiueos.publisher/in-root (count in-root)
     :aiueos.publisher/revoked (mapv :key-id revoked)
     :aiueos.publisher/live (mapv :key-id live)
     :aiueos.publisher/outside-root (mapv :key-id
                                          (remove #(contains? named (:key-id %)) verified))}))

(defn admit-release
  "Decide whether a release manifest may be applied.

  `release` — `{:sequence :signatures :artifact-digests-match? :timestamp-ms}`
  `state`   — `{:installed-sequence :now-ms :root :revocation-bits :root-expires-ms}`
  `policy`  — see `default-policy`.

  The order of the checks is the order in which a wrong answer is cheapest to
  give: structural problems first, then trust, then freshness."
  ([release state] (admit-release release state default-policy))
  ([release state policy]
   (let [policy (merge default-policy policy)
         {:keys [installed-sequence now-ms root revocation-bits root-expires-ms]} state
         {:keys [sequence signatures artifact-digests-match? timestamp-ms]} release
         threshold (max (or (:threshold root) 0) (:min-threshold policy))
         tally (signing-keys-ok (or signatures []) root revocation-bits)
         live (count (:aiueos.publisher/live tally))]
     (cond
       (empty? (or signatures [])) (deny :no-signatures {})

       (and root-expires-ms now-ms (> now-ms root-expires-ms))
       (deny :root-expired {:aiueos.publisher/root-expires-ms root-expires-ms})

       ;; a root that names fewer keys than its own threshold can never admit
       ;; anything; say so rather than reporting :below-threshold forever.
       (< (count (:keys root)) threshold)
       (deny :threshold-unsatisfiable
             {:aiueos.publisher/named (count (:keys root))
              :aiueos.publisher/threshold threshold})

       (not (true? artifact-digests-match?)) (deny :digest-mismatch {})

       ;; signatures verified, but none of the signers is a key this root names.
       (and (zero? (:aiueos.publisher/in-root tally))
            (seq (:aiueos.publisher/outside-root tally)))
       (deny :key-not-in-root
             {:aiueos.publisher/outside-root (:aiueos.publisher/outside-root tally)})

       (and (zero? live) (seq (:aiueos.publisher/revoked tally)))
       (deny :key-revoked {:aiueos.publisher/revoked (:aiueos.publisher/revoked tally)})

       (< live threshold)
       (deny :below-threshold (merge tally {:aiueos.publisher/threshold threshold}))

       (and (some? installed-sequence) (some? sequence)
            (<= sequence installed-sequence))
       (deny :sequence-not-monotonic
             {:aiueos.publisher/sequence sequence
              :aiueos.publisher/installed installed-sequence})

       (nil? timestamp-ms) (deny :timestamp-missing {})

       :else
       (let [ttl (:freshness-ttl-ms policy)
             f (if-let [c (:clock state)]
                 (clock/decide-freshness c timestamp-ms ttl)
                 (when now-ms
                   (if (> (- now-ms timestamp-ms) ttl)
                     {:aiueos.clock/freshness :stale :aiueos.clock/reason :ttl-exceeded
                      :aiueos.clock/age-ms (- now-ms timestamp-ms)}
                     {:aiueos.clock/freshness :fresh})))]
         (case (:aiueos.clock/freshness f)
           :stale (deny :timestamp-expired
                        {:aiueos.publisher/age-ms (:aiueos.clock/age-ms f)
                         :aiueos.publisher/ttl-ms ttl
                         :aiueos.publisher/freshness-reason (:aiueos.clock/reason f)})
           :undecidable (deny :freshness-undecidable
                              {:aiueos.publisher/freshness-reason (:aiueos.clock/reason f)})
           (admit (merge tally {:aiueos.publisher/sequence sequence
                                :aiueos.publisher/threshold threshold}))))
))))

(defn keep-running?
  "May a machine that cannot reach the publisher keep running what it already
  has? Yes, within `:offline-grace-ms` — refusing to run because an update
  server is unreachable would turn a network outage into a fleet outage.

  This never admits anything new; it only says whether the existing system stays
  up. The two are different questions and are answered by different functions on
  purpose."
  ([state] (keep-running? state default-policy))
  ([{:keys [now-ms timestamp-ms]} policy]
   (let [policy (merge default-policy policy)]
     (boolean (or (nil? timestamp-ms) (nil? now-ms)
                  (<= (- now-ms timestamp-ms)
                      (+ (:freshness-ttl-ms policy) (:offline-grace-ms policy))))))))

(defn admit-root-rotation
  "Decide whether a new root may replace the current one. Requires a threshold
  of signatures from **both** roots: the old one, so the rotation is authorised,
  and the new one, so a root nobody can sign for cannot be installed.

  Losing either half has a name. Without the old root's signatures, anyone who
  can deliver bytes replaces the trust anchor. Without the new root's, a typo in
  the key list bricks the fleet's ability to ever update again."
  [{:keys [old-root-signatures new-root-signatures old-root new-root revocation-bits]}]
  (let [old-tally (signing-keys-ok (or old-root-signatures []) old-root revocation-bits)
        new-tally (signing-keys-ok (or new-root-signatures []) new-root revocation-bits)
        old-live (count (:aiueos.publisher/live old-tally))
        new-live (count (:aiueos.publisher/live new-tally))]
    (cond
      (< old-live (or (:threshold old-root) 1))
      (deny :below-threshold {:aiueos.publisher/role :root-old
                              :aiueos.publisher/live old-live
                              :aiueos.publisher/threshold (:threshold old-root)})
      (< new-live (or (:threshold new-root) 1))
      (deny :below-threshold {:aiueos.publisher/role :root-new
                              :aiueos.publisher/live new-live
                              :aiueos.publisher/threshold (:threshold new-root)})
      :else (admit {:aiueos.publisher/role :root
                    :aiueos.publisher/old-live old-live
                    :aiueos.publisher/new-live new-live}))))

(defn admitted? [verdict] (= :grant (:aiueos/decision verdict)))
