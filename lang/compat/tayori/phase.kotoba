(ns tayori.phase
  "Phase 0→3 staged rollout, gating only the ASSESS ops (draft/revise/send/
  publish decisions). Recording ground facts (a thread/message/contact/
  document appearing) is always on — that is tayori's observe charter
  (durable ground facts). The phase only decides how much autonomy
  *drafting* has; sending/publishing is never in scope for autonomy — it is
  a separate, always-human charter enforced by the governor's high-stakes
  flag, not by phase.

    0 ingest-only    — record threads/messages/contacts/documents; emit NO
                       drafts/revisions yet (shadow inbox).
    1 assisted       — draft/revise allowed, but always human even to commit
                       just the draft/revision text.
    2 assisted-draft — a clean+confident draft/revision may auto-commit (the
                       'casual git commit' — it is just proposed text sitting
                       on the thread/document for review); send/publish stay
                       human.
    3 supervised     — same autonomy as 2; send/publish are high-stakes and
                       ALWAYS route to a human (the 'PR merge' is always a
                       human call, regardless of phase).")

(def record-ops #{:thread/register :message/ingest :contact/register :document/register})
(def assess-ops #{:reply/draft :reply/send :document/revise :document/publish})

(def phases
  {0 {:label "ingest-only"    :assess #{}        :auto #{}}
   1 {:label "assisted"       :assess assess-ops :auto #{}}
   2 {:label "assisted-draft" :assess assess-ops :auto #{:reply/draft :document/revise}}
   3 {:label "supervised"     :assess assess-ops :auto #{:reply/draft :document/revise}}})

(def default-phase
  "The phase used when `context` carries no :phase at all
  (tayori.operation: (:phase context phase/default-phase)), AND the
  fallback `gate` itself uses for an unrecognized phase NUMBER
  (`(get phases phase (get phases default-phase))`). This is directly
  reachable by any ordinary caller that simply omits :phase -- not just
  malformed/malicious input -- so it must be the MOST CONSERVATIVE
  phase, never the most permissive. This was 3 (supervised, where
  :reply/draft and :document/revise can auto-commit) until a live check
  confirmed a caller who forgets :phase silently got maximum autonomy
  instead of the safe default -- the same accidental-fail-open shape
  already found and fixed this session in the shared talent.phase
  template (gftd-talent-actor) and its siblings newscaster.phase,
  wami.phase, kyoninka.phase, sng.phase, and itonami.phase, which all
  inherited the same bug. 1 (assisted) matches those fixes. :reply/send
  and :document/publish remain unaffected either way (never in any
  phase's :auto set -- sending/publishing always requires a human)."
  1)

(defn record-op? [op] (contains? record-ops op))

(defn gate
  "Adjust an assess op's governor disposition for the rollout phase.
  Returns {:disposition kw :reason kw|nil}. `:reply/send`/`:document/publish`
  are never in :auto, so they always escalate; the governor's high-stakes
  flag already forces this too — phase and governor agree by construction."
  [phase {:keys [op]} disposition]
  (let [{:keys [assess auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold disposition)        {:disposition :hold :reason nil}
      (not (contains? assess op))  {:disposition :hold :reason :phase-disabled}
      (and (= :commit disposition)
           (not (contains? auto op))) {:disposition :escalate :reason :phase-approval}
      :else                        {:disposition disposition :reason nil})))

(defn verdict->disposition [v]
  (cond (:hard? v) :hold (:escalate? v) :escalate :else :commit))
