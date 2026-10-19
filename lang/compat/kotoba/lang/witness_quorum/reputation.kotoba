(ns kotoba.lang.witness-quorum.reputation
  "Reputation-weighted witness selection (ADR-2607110300 Phase 4). Tracks
  each witness cell's historical agreement with the eventual quorum
  majority verdict, and lets selection exclude cells whose track record is
  poor -- the concrete mechanism 'reputation-weighted witness selection'
  takes here (exclusion below a threshold, not a continuous probability
  weight -- simpler, and sufficient without inventing a weighted sampler
  nothing else in this repo needs).

  Honesty note (ADR-2607110300 Phase 4): this is exercised against
  kotoba-lang/murakumo's real fleet.edn (a genuine production fleet
  inventory, not a synthetic mock) as the ADR's chosen validation path,
  but every cell in that fleet is still operated by the same party.
  Reputation tracking alone does not make the system
  '分散型経済/ブロックチェーン' -- see the ADR's Phase 4 labeling rule. It
  becomes meaningful once cells are run by independent third-party
  operators, which this module does not require or assume."
  )

(def empty-reputation {})

(defn record-outcome
  "Update `reputation-db` (map of cell-key -> {:correct n :total n}) with
  one cell's outcome for a DECIDED quorum (`:kind :witnessed` or
  `:rejected` from `quorum/quorum-state` -- `:pending`/`:escalated` are
  not decisions, callers should not record those). `agreed?` is true when
  the cell's own attestation verdict matched the eventual quorum majority
  verdict."
  [reputation-db cell-key agreed?]
  (-> reputation-db
      (update-in [cell-key :total] (fnil inc 0))
      (update-in [cell-key :correct] (fnil + 0) (if agreed? 1 0))))

(defn record-quorum-outcomes
  "Given a decided quorum-state (`{:kind :witnessed|:rejected :matching
  [...] :minority [...] :abstained [...]}`, exactly what
  kotoba.lang.witness-quorum.quorum/quorum-state returns), update every
  JUDGING cell's reputation in one pass: :matching cells agreed with the
  majority, :minority cells did not.

  `:abstained` cells (verdict `:escalate`) are deliberately NOT recorded --
  not as correct, not as incorrect, and not as an observation at all. An
  abstaining cell keeps the exact score and observation count it had, so
  abstention can never move it toward `below-threshold?`. Declining to judge
  is an honest report about the cell's own confidence, and scoring it as a
  wrong answer is what makes guessing-with-the-majority the safe play
  (ADR-2608055000 G1; before 2026-08-05 `quorum-state` folded `:escalate`
  into `:minority`, so this list did not exist and abstainers were scored as
  wrong)."
  [reputation-db quorum-state]
  (let [cell-key (fn [a] (str (:cell-node a) "::" (:cell-id a)))]
    (as-> reputation-db db
      (reduce (fn [db a] (record-outcome db (cell-key a) true)) db (:matching quorum-state))
      (reduce (fn [db a] (record-outcome db (cell-key a) false)) db (:minority quorum-state)))))

(defn score
  "Fraction of decided quorums this cell agreed with the majority on, in
  [0,1]. A cell with no recorded history scores `default` (1.0 -- assume
  good faith for a cell with no track record yet; `below-threshold?`
  separately requires a minimum observation count before a low score can
  exclude a cell, so a brand-new cell is never punished for silence)."
  ([reputation-db cell-key] (score reputation-db cell-key 1.0))
  ([reputation-db cell-key default]
   (let [{:keys [correct total]} (get reputation-db cell-key)]
     (if (and total (pos? total))
       (/ correct total)
       default))))

(defn below-threshold?
  "True iff the cell has at least `min-observations` recorded outcomes AND
  its score is below `min-score`. A cell with too little history is never
  judged either way -- this only excludes a DEMONSTRATED bad track
  record, not an unproven one."
  [reputation-db cell-key min-score min-observations]
  (let [{:keys [correct total] :or {correct 0 total 0}} (get reputation-db cell-key)]
    (and (>= total min-observations)
         (< (/ correct total) min-score))))

(defn eligible-fleet
  "Filter `fleet` (witness-quorum fleet-cell maps, e.g. from
  selector/flatten-fleet) down to cells that are NOT below-threshold per
  `reputation-db` -- the selection-time gate a caller applies BEFORE
  selector/select-witnesses, so a persistently-disagreeing cell stops
  being eligible for selection at all."
  [fleet reputation-db min-score min-observations]
  (vec (remove #(below-threshold? reputation-db (:key %) min-score min-observations) fleet)))
