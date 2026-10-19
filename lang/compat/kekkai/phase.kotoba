(ns kekkai.phase
  "Phase 0→3 staged rollout, gating only the ASSESS ops (netmap/admission/route
  decisions). Recording tailnet ground datoms (register a node, log a heartbeat,
  publish ACL, advertise a route) is always on — that is kekkai's observe
  charter (durable EAVT facts). The phase only decides how much autonomy the
  *control-plane decisions* have, and can only add caution.

    0 observe-only  — record join requests / heartbeats / ACL / advertised
                      routes; emit NO netmap decisions yet (shadow tailnet).
    1 assisted      — access netmaps allowed, but always human for admission.
    2 assisted-net  — access + subnet-route decisions may auto-commit when
                      clean; node admission and exit nodes stay human.
    3 supervised    — access/subnet auto-commit when clean+confident; node
                      admission and exit-node approval are high-stakes and
                      ALWAYS route to a human admin (machine approval is a human
                      call, like Tailscale).

  Value governance (:treasury/release, ADR-2607110300 Phase 3) is active at
  every phase above, including 0 -- it never auto-commits regardless of
  phase (always high-stakes, see governor.cljc), so there is no rollout
  ladder for it to climb; gating it behind the NETWORK phase would just be
  wrong (fund release has nothing to do with tailnet maturity).")

(def record-ops #{:node/register :node/heartbeat :user/register
                  :tailnet/register :peering/propose
                  :acl/publish :route/advertise})
(def assess-ops #{:node/admit :access/assess :route/approve})

(def tenancy-ops
  "Cross-organization ops (ADR-2608040100): approving a peering opens a path
  between two tailnets.

  Active at every phase, including 0, for the same reason :treasury/release
  is: it never auto-commits (always high-stakes, see governor.cljc), so there
  is no rollout ladder for it to climb. Gating it behind the NETWORK phase
  would be worse than pointless — a shadow-mode deployment still has to be
  able to record that two organizations agreed, and withholding the *approval*
  op while `:peering/propose` records freely would leave proposals piling up
  with no way to consummate or refuse them."
  #{:peering/approve})

;; ADR-2607110300 Phase 3: value governance (fund release) is orthogonal to
;; the tailnet rollout ladder below -- a system can govern fund release
;; before it trusts automated NETWORK decisions, so :treasury/release is
;; active in :assess at every phase, including 0. :witness/dispute (Phase 4)
;; is deliberately NOT here: this governor has no automated
;; dispute-resolution shape (see governor.cljc's
;; dispute-actuation-violations docstring -- there is no
;; "dispute-resolved-in-favor-of-X" verdict) to route through :decide/:commit,
;; so it stays reachable via governor/check + hold-fact directly, not this
;; StateGraph, until dispute-resolution semantics are actually designed.
(def value-ops #{:treasury/release})

(def ^:private always-on (into value-ops tenancy-ops))

(def phases
  {0 {:label "observe-only" :assess always-on                   :auto #{}}
   1 {:label "assisted"     :assess (into assess-ops always-on) :auto #{}}
   2 {:label "assisted-net" :assess (into assess-ops always-on) :auto #{:access/assess :route/approve}}
   3 {:label "supervised"   :assess (into assess-ops always-on) :auto #{:access/assess :route/approve}}})

(def default-phase
  "The phase used when `context` carries no :phase at all
  (kekkai.operation: (:phase context phase/default-phase)), AND the
  fallback `gate` itself uses for an unrecognized phase NUMBER
  (`(get phases phase (get phases default-phase))`). This is directly
  reachable by any ordinary caller that simply omits :phase -- not just
  malformed/malicious input -- so it must be the MOST CONSERVATIVE
  phase, never the most permissive. This was 3 (supervised, where
  :access/assess and :route/approve can auto-commit) until a live check
  confirmed a caller who forgets :phase silently got maximum autonomy
  instead of the safe default -- the same accidental-fail-open shape
  already found and fixed this session in the shared talent.phase
  template (gftd-talent-actor) and its siblings newscaster.phase,
  wami.phase, kyoninka.phase, sng.phase, itonami.phase, tayori.phase,
  goyoukiki.phase, denrei.phase, shoko.phase, teian.phase, and
  koyomi.phase, which all inherited the same bug. 1 (assisted) matches
  those fixes: :treasury/release is unaffected either way (never in any
  phase's :auto set -- fund release always requires a human), and
  :node/admit likewise remains always-escalate."
  1)

(defn record-op? [op] (contains? record-ops op))

(defn gate
  "Adjust an assess op's governor disposition for the rollout phase.
  Returns {:disposition kw :reason kw|nil}. :node/admit is never in :auto, so
  it always escalates; an exit-route approval is flagged high-stakes by the
  governor and likewise escalates."
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
