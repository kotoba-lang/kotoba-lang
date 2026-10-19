(ns kotoba.lang.witness-quorum.outcome
  "What a decided quorum round does to the witnesses that took part.

  Replaces `kotoba.lang.witness-quorum.slashing` (removed 2026-08-05,
  ADR-2608055000 G1). That namespace connected a quorum verdict to
  `stake/slash!`, so a witness in the `:minority` -- and, because
  `quorum/quorum-state` folded `:escalate` into `:minority`, a witness that
  merely declined to judge -- had bond confiscated for disagreeing with the
  majority.

  That is the wrong shape and this repo is the wrong place for it:

  1. **Disagreement is not an objective fault.** The majority is not
     definitionally right. Slashing the minority makes 'vote with the crowd'
     the dominant strategy, which destroys the only thing an independent
     witness is for. ADR-2608055000 states the test a fault must pass before
     it may cost anyone money: a third party who does not trust the accuser,
     does not know the network state, and does not know the time must be able
     to settle it with signature verification alone. Today only equivocation
     passes -- and detecting it is `inga.stake`'s job, not this repo's.
  2. **Abstention is an honest report.** A witness that says 'I cannot decide
     this' told the truth. Punishing it teaches witnesses to guess.
  3. ADR-2607995000 promoted equivocation-only to the ZONE-WIDE slashing
     rule. This namespace was the one implementation violating it, and the
     only slash path actually wired into a caller (murakumo's overlay).

  So a quorum outcome now moves REPUTATION and nothing else. Reputation is
  the right instrument: it gates future selection (`reputation/eligible-fleet`)
  rather than confiscating property, it is reversible by behaving well, and it
  never claims to be a Byzantine-security mechanism.

  **This namespace cannot touch stake, by construction.** `apply-quorum-outcome`
  is not handed a stake ledger, so restoring the old behaviour requires
  changing the signature -- a deliberate act, not an accident. That is the
  point: the previous design failed a review, not a test."
  (:require [kotoba.lang.witness-quorum.reputation :as reputation]))

(defn- cell-key [a] (str (:cell-node a) "::" (:cell-id a)))

(defn apply-quorum-outcome
  "Given a decided quorum-state (`{:kind :witnessed|:rejected :matching [...]
  :minority [...] :abstained [...]}`, per
  `kotoba.lang.witness-quorum.quorum/quorum-state`), update `reputation-db`
  for every participating cell in one pass:

    :matching   reputation +1 correct.
    :minority   reputation +1 incorrect. NO other consequence -- their bond,
                if any, is untouched and unreachable from here.
    :abstained  NOTHING. Not a correct answer, not an incorrect one, not an
                observation. A cell that abstains keeps exactly the score it
                had, so abstaining can never push it under
                `reputation/below-threshold?`.

  `:pending`/`:escalated` quorum-states should NOT be passed here (not a
  decision -- same caveat `reputation/record-quorum-outcomes` documents).

  Returns `{:reputation-db db' :outcomes {:agreed [...] :disagreed [...]
  :abstained [...]}}` -- `:outcomes` is the audit trail of who was counted
  how, in cell-key form. It replaces the old `:slashed` vector, which
  reported units confiscated."
  [reputation-db quorum-state]
  {:reputation-db (reputation/record-quorum-outcomes reputation-db quorum-state)
   :outcomes {:agreed (mapv cell-key (:matching quorum-state))
              :disagreed (mapv cell-key (:minority quorum-state))
              :abstained (mapv cell-key (:abstained quorum-state))}})
