(ns grant.anchors
  "How the pin set gets to a device, and how it changes.

  Contract: `resources/aiueos/anchors_contract.edn`. Pure decisions; fetching
  and signature verification are provider mechanism, as in `grant.publisher`.

  ## The hole this namespace closes

  ADR-0044 made an https peer trustworthy only if its key is in
  `:aiueos.cloud/trust-anchors`. It recorded no pin for anything real, which
  left the machine strictly worse off than before: **a pinning client with no
  pin distribution is a client that cannot connect.** This is the distribution.

  ## It is a release, so it is admitted like one

  An anchor set is a signed, sequenced document that changes what the machine
  will trust — the same shape as a release, with the same four attacks against
  it (one stolen key, a key stolen before it was known to be, rollback to a set
  whose key is now public, and freeze). `grant.publisher` already answers all
  four, so this namespace **composes** it rather than growing a second, weaker
  copy. Publisher reasons pass through unchanged, for the reason `grant.ota`
  gives: a caller reading a verdict should not have to guess which layer
  refused.

  ## What is true of anchor sets and of nothing else

  **An empty set is not a permissive set, it is a brick.** A machine whose pin
  set is empty reaches nothing, so a perfectly signed empty set is refused —
  the one denial that protects the fleet from its own publisher.

  **Replacing every anchor at once is a one-way door.** If the new keys are
  wrong, the machine cannot reach anything to be told so, including the
  correction. So an ordinary rotation must **overlap**: keep at least one key
  that works today, ship the new one alongside it, switch the server, retire
  the old one. Three admitted sets, none of which can strand a device.

  A compromise cannot overlap — dropping the stolen key is the entire point.
  That path exists, is called `:break-glass?`, needs **every** root key rather
  than the threshold, and the verdict says `:one-way? true` so the operator is
  told what they are doing. Break-glass also drops the previous set
  *immediately* rather than keeping it through the overlap window, because a
  window in which the stolen key still works is the thing being escaped.

  ## Trust on first use is not available

  A device with no anchors cannot be handed one over a connection it has no way
  to judge. The first set ships **in the image**, covered by the release
  signature that already exists, and is marked `:bootstrap?`. Anything else
  arriving at a device with no current set is refused with `:no-current-set`.

  ## A machine that cannot fetch keeps what it has

  `keep-using?` mirrors `grant.publisher/keep-running?` and for the same
  reason: expiring the pin set because an anchor server is unreachable would
  turn a network outage into a fleet outage. Freshness gates **admission of a
  new set**, never continued use of the current one."
  (:require [grant.publisher :as publisher]
            [kotoba.lang.coll :as set]))

;; ── two sequence spaces, two keys ──────────────────────────────────────────
;;
;; Anchor sets and releases are both sequenced, and both are checked by
;; `grant.publisher`, which reads `:installed-sequence`. If a caller keeps one
;; state map for both -- which is the obvious thing to do, since the root keys
;; and revocation bitmap really are shared -- then the higher of the two
;; numbers silently blocks the other stream, and a rollback arrives looking
;; like an upgrade. So anchor state carries its own key and this namespace
;; hands publisher the right one.

(def deny-reasons
  "Reasons this namespace produces. Reasons from `grant.publisher` pass
  through unchanged."
  #{:set-id-missing :anchor-set-empty :no-current-set
    :disjoint-without-break-glass :break-glass-below-threshold
    :no-anchors-artifact :anchors-for-another-release :anchors-digest-mismatch
    :document-malformed :document-version-unknown :document-pin-malformed})

(def default-policy
  "`:overlap-window-ms` is how long the previous set stays usable after an
  ordinary rotation — long enough for a fleet to converge, short enough that a
  retired key is actually retired. `:break-glass-threshold` defaults to *every*
  key the root names, because break-glass is the one operation that can strand
  a device."
  {:overlap-window-ms (* 1000 60 60 24 7)
   :break-glass-threshold nil})

(defn- deny [reason extra]
  (merge {:aiueos/decision :deny :aiueos.anchors/reason reason} extra))

(defn- admit [extra]
  (merge {:aiueos/decision :grant} extra))

(defn admitted? [verdict] (= :grant (:aiueos/decision verdict)))

(defn usable-anchors
  "The pins this machine will accept right now — what belongs in
  `:aiueos.cloud/trust-anchors`.

  During an ordinary rotation's overlap window that is the union of the current
  and previous sets, so the switchover is not a flag day. After the window it is
  the current set alone: a previous set that never expires is a key that was
  never retired."
  [{:keys [current-anchors previous-anchors accept-previous-until-ms now-ms]}]
  (let [current (set current-anchors)
        previous (set previous-anchors)]
    (if (and (seq previous) accept-previous-until-ms now-ms
             (<= now-ms accept-previous-until-ms))
      (set/set-union current previous)
      current)))

(defn keep-using?
  "May a machine that cannot reach the anchor publisher keep using the set it
  has? Yes, whenever it has one. This never admits anything new."
  [state]
  (boolean (seq (:current-anchors state))))

(defn admit-set
  "Decide whether PROPOSED may become this machine's pin set.

  `proposed` — `{:set-id :sequence :anchors #{spki-hex} :signatures
                 :timestamp-ms :digest-matches? :break-glass? :bootstrap?}`
  `state`    — `grant.publisher`'s state plus `:current-anchors` and
               `:installed-anchor-sequence` (see above: not
               `:installed-sequence`, which belongs to releases).

  Structural problems first, then publisher trust, then the two questions only
  an anchor set raises."
  ([proposed state] (admit-set proposed state default-policy))
  ([proposed state policy]
   (let [policy (merge default-policy policy)
         state (assoc state :installed-sequence (:installed-anchor-sequence state))
         anchors (set (:anchors proposed))
         current (set (:current-anchors state))
         root-keys (count (:keys (:root state)))
         glass-threshold (or (:break-glass-threshold policy) root-keys)]
     (cond
       (nil? (:set-id proposed))
       (deny :set-id-missing {})

       (empty? anchors)
       (deny :anchor-set-empty {:aiueos.anchors/set-id (:set-id proposed)})

       :else
       (let [verdict (publisher/admit-release
                      {:sequence (:sequence proposed)
                       :signatures (:signatures proposed)
                       :artifact-digests-match? (:digest-matches? proposed)
                       :timestamp-ms (:timestamp-ms proposed)}
                      state
                      policy)]
         (if-not (publisher/admitted? verdict)
           verdict
           (let [live (count (:aiueos.publisher/live verdict))
                 overlap (set/set-intersection anchors current)]
             (cond
               (and (empty? current) (not (true? (:bootstrap? proposed))))
               (deny :no-current-set {:aiueos.anchors/set-id (:set-id proposed)})

               (and (seq current) (empty? overlap) (not (true? (:break-glass? proposed))))
               (deny :disjoint-without-break-glass
                     {:aiueos.anchors/set-id (:set-id proposed)
                      :aiueos.anchors/current (vec (sort current))
                      :aiueos.anchors/proposed (vec (sort anchors))})

               (and (seq current) (empty? overlap) (< live glass-threshold))
               (deny :break-glass-below-threshold
                     {:aiueos.anchors/set-id (:set-id proposed)
                      :aiueos.anchors/live live
                      :aiueos.anchors/required glass-threshold})

               :else
               (admit {:aiueos.anchors/set-id (:set-id proposed)
                       :aiueos.anchors/sequence (:sequence proposed)
                       :aiueos.anchors/anchors anchors
                       :aiueos.anchors/overlap (vec (sort overlap))
                       :aiueos.anchors/one-way? (boolean (and (seq current) (empty? overlap)))
                       :aiueos.anchors/bootstrap? (true? (:bootstrap? proposed))
                       :aiueos.publisher/live live})))))))))

(defn apply-set
  "The state after VERDICT admits a set. Pure: persisting it is the provider's.

  An ordinary rotation keeps the previous set usable for the overlap window. A
  break-glass rotation drops it at once — a window in which the stolen key
  still works is the thing being escaped."
  ([state verdict] (apply-set state verdict default-policy))
  ([state verdict policy]
   (if-not (admitted? verdict)
     state
     (let [policy (merge default-policy policy)
           one-way? (true? (:aiueos.anchors/one-way? verdict))
           now (:now-ms state)]
       (merge state
              {:current-anchors (:aiueos.anchors/anchors verdict)
               :installed-anchor-sequence (:aiueos.anchors/sequence verdict)
               :previous-anchors (if one-way? #{} (set (:current-anchors state)))
               :accept-previous-until-ms (when (and (not one-way?) now
                                                    (seq (:current-anchors state)))
                                           (+ now (:overlap-window-ms policy)))})))))

;; ── the first set arrives in the image ─────────────────────────────────────
;;
;; A device with no anchors cannot fetch one: the connection that would deliver
;; it is the connection the anchors exist to judge. The only channel it already
;; trusts is the signed artifact it boots, so that is where the first set comes
;; from — as a release artifact of kind `:anchors`, covered by the release
;; signature that exists anyway.
;;
;; That makes every release a potential trust change, which is the reason the
;; rules above are not bypassed here. `from-release` produces a *proposal*; it
;; still goes through `admit-set`, so a release that replaces every anchor is
;; refused exactly as a standalone set would be. **The update channel does not
;; get a shortcut around the one-way door**, and the release being perfectly
;; signed is not an argument, because a signed release that strands the fleet
;; is still a stranded fleet.

(def anchors-artifact-kind :anchors)

(defn carries-anchors?
  "Whether RELEASE names an anchor-set artifact at all. Most releases do not,
  and that is not an error."
  [release]
  (boolean (some #(= anchors-artifact-kind (:kind %)) (:artifacts release))))

(defn from-release
  "The anchor set a release carries, as a proposal `admit-set` can judge.

  `release`  — the release manifest: `{:manifest-id :sequence :signatures
                :timestamp-ms :artifacts}`
  `carried`  — what the provider decoded from the `:anchors` artifact:
                `{:release-id :sequence :anchors :break-glass?}`
  `observed` — `{kind digest}`, what the provider actually fetched and hashed
  `state`    — this machine's anchor state

  The carried set names the release it was made for, and that name is checked
  against the release in hand. Without it, an anchor set extracted from one
  release could be applied alongside another's bytes — the hole `grant.ota`
  closes for admissions, in the one place where getting it wrong costs the
  device its ability to be corrected."
  [release carried observed state]
  (let [want (some #(when (= anchors-artifact-kind (:kind %)) (:sha256 %))
                   (:artifacts release))]
    (cond
      (nil? want)
      (deny :no-anchors-artifact {:aiueos.anchors/release-id (:manifest-id release)})

      (not= (:release-id carried) (:manifest-id release))
      (deny :anchors-for-another-release
            {:aiueos.anchors/release-id (:manifest-id release)
             :aiueos.anchors/carried-release-id (:release-id carried)})

      (not= want (get observed anchors-artifact-kind))
      (deny :anchors-digest-mismatch
            {:aiueos.anchors/release-id (:manifest-id release)
             :aiueos.anchors/expected want
             :aiueos.anchors/observed (get observed anchors-artifact-kind)})

      :else
      (admit {:aiueos.anchors/proposed
              {:set-id want
               :sequence (:sequence carried)
               :anchors (set (:anchors carried))
               :break-glass? (true? (:break-glass? carried))
               ;; Not self-asserted: a set is the bootstrap because this device
               ;; has nothing, never because the document said so.
               :bootstrap? (empty? (set (:current-anchors state)))
               :signatures (:signatures release)
               :timestamp-ms (:timestamp-ms release)
               :digest-matches? true}}))))

(defn admit-from-release
  "`from-release` then `admit-set`. The convenience does not skip a rule: the
  proposal it builds is judged by the same function a standalone set is."
  ([release carried observed state] (admit-from-release release carried observed state default-policy))
  ([release carried observed state policy]
   (let [v (from-release release carried observed state)]
     (if-not (admitted? v)
       v
       (admit-set (:aiueos.anchors/proposed v) state policy)))))

;; ── the document, as bytes someone can put in an image ─────────────────────
;;
;; ADR-0046 left this unspecified, which meant the release-borne path had a
;; judgement and nothing to judge. The encoding is the canonical form
;; `grant.key-lifecycle/document-bytes` already produces for every other
;; signed document in this repository — a second canonicaliser would be a
;; signature-confusion hazard, not a convenience — so this namespace supplies
;; the *shape* and the JVM side supplies the bytes.

(def document-version 1)

(def document-signature-key
  "Reserved. A release-borne set needs no signature of its own: the manifest
  binds its digest and the manifest is signed. The key is named anyway so the
  canonical bytes are the same with and without one, and a set delivered some
  other way can carry it."
  :anchors/signature)

(defn document
  "The anchor-set document, as data. Pins are sorted, because a set has no
  order and a digest does."
  [{:keys [release-id sequence anchors break-glass?]}]
  {:anchors/version document-version
   :anchors/release-id release-id
   :anchors/sequence sequence
   :anchors/anchors (vec (sort (map str anchors)))
   :anchors/break-glass? (true? break-glass?)})

(defn pin-well-formed?
  "Whether PIN is 64 lowercase hex characters, which is the only thing a
  SHA-256 of a SubjectPublicKeyInfo can be.

  Public because `grant.cloud` asks the same question of the pins in a live
  policy, and two answers to \"what is a pin\" is one answer too many."
  [pin]
  (boolean (re-matches #"[0-9a-f]{64}" (str pin))))

(defn read-document
  "Validate an already-read document map into the `carried` shape
  `from-release` takes. Parsing text is the caller's; this decides.

  Fail-closed, and one of the checks is load-bearing beyond the usual: **a pin
  that is not 64 lowercase hex characters is refused rather than carried**. A
  malformed pin can never match a measured key, so a set containing one is a
  set that partly cannot work — and it would arrive looking entirely valid."
  [doc]
  (let [pins (:anchors/anchors doc)]
    (cond
      (not (map? doc))
      (deny :document-malformed {})

      (not= document-version (:anchors/version doc))
      (deny :document-version-unknown {:aiueos.anchors/version (:anchors/version doc)})

      (or (nil? (:anchors/release-id doc)) (nil? (:anchors/sequence doc))
          (not (coll? pins)))
      (deny :document-malformed {:aiueos.anchors/release-id (:anchors/release-id doc)})

      (not (every? pin-well-formed? pins))
      (deny :document-pin-malformed
            {:aiueos.anchors/malformed (vec (remove pin-well-formed? pins))})

      :else
      (admit {:aiueos.anchors/carried
              {:release-id (:anchors/release-id doc)
               :sequence (:anchors/sequence doc)
               :anchors (set pins)
               :break-glass? (true? (:anchors/break-glass? doc))}}))))
