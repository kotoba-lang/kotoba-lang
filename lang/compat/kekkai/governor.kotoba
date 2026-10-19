(ns kekkai.governor
  "TailnetGovernor — the independent zero-trust layer that earns the coord-LLM
  the right to *propose* netmaps. The LLM has no notion of node-key validity,
  deny-by-default ACLs, tag ownership or the no-actuation charter, so this MUST
  be a separate system (rules over the EAVT ground datoms) able to *reject* a
  proposal and fall back to HOLD — the kekkai analog of robotaxi's MRC /
  itonami's cert hold.

  The actor is **coordinate → publish-netmap only**. It never pushes WireGuard
  config to a node and never carries a packet; admitting a machine into the
  tailnet is ALWAYS routed to a human admin (the Tailscale 'machine approval'
  toggle), and so is approving an exit node. Below, HARD invariants force HOLD
  (a human cannot approve past an unowned tag, an expired/revoked key, an
  un-permitted reachability edge, or a route hijack); a clean admission/exit
  still routes to a human (high-stakes).

  ## The organization boundary is a governor invariant, not an ACL rule

  One control plane serves many organizations (ADR-2608040100). A tailnet is
  one organization's overlay, and the boundary between two of them is enforced
  HERE, structurally, because it cannot be enforced by the policy language: ACL
  selectors are bare tokens, so two organizations that each named a tag
  `tag:server` match each other's grants. No rule anyone writes fixes that —
  the fix is to settle the boundary before any grant is consulted
  (`acl/edge-decision`), on a field the policy does not own.

  Crossing it requires a **peering**: a record naming both tailnets, carrying
  its own directional grants, valid only once BOTH organizations approved it.
  Approval is always a human call, like machine admission.

  HARD invariants:
    :node/admit
      1. Valid node key   — node presents a did:key, status ≠ revoked, key not
                            expired (key-expiry > now).
      2. Tenancy           — the claimed tailnet is registered and active, and
                            the node's OWNER belongs to it. Without the owner
                            check, any org's admin could enroll a machine into
                            any other org by naming its tailnet.
      3. Tag ownership     — every tag the node claims is authorized for its
                            owner by THAT TAILNET's tag-owners (no self-
                            escalation, and no other org deciding who may
                            claim a tag here).
      4. No-actuation      — effect must be :netmap (a control-plane record),
                            never a data-plane / WireGuard push.
    :access/assess
      1. Valid node key on the subject AND every proposed peer.
      2. Deny-by-default   — every proposed reachability edge is backed by an
                            ACL grant; an unbacked edge is rejected.
      3. Organization boundary — an edge into another tailnet is rejected
                            unless a mutually-approved peering carries a grant
                            for that direction (:cross-tailnet). Checked
                            BEFORE grants, so a colliding tag name can never
                            make a cross-org edge look 'backed by a grant'.
      4. No-actuation.
    :peering/approve
      1. Valid node key on the approving node.
      2. Party             — the peering exists, names two distinct registered
                            tailnets, `:as` is one of them, and the approving
                            node actually belongs to `:as`. Nobody signs for
                            an organization they are not in.
      3. No foreign grants — every grant originates at one of the two
                            endpoints; a third org's `:from` would smuggle in
                            a path for a party that signed nothing.
      4. No-actuation      — effect must be :peering-record.
    :route/approve
      1. Valid node key on the advertising node.
      2. No-hijack         — the cidr is not already approved for a DIFFERENT
                            node (no silent route takeover).
      3. No-actuation.
    :treasury/release (ADR-2607110300 Phase 3 -- widens this governor's remit
                       from network reachability to value: same deny-by-
                       default philosophy as acl.cljc's edge-allowed?, now
                       gating fund release on a witness-quorum verdict
                       instead of an ACL grant)
      1. Valid node key   — same identity check as every other op; the
                            requesting actor must be a live, non-revoked,
                            non-expired node (an actor has its own did:key,
                            per the CLAUDE.md Actor pattern).
      2. Witness quorum    — deny-by-default for value: an absent or
                            non-:witnessed witness-quorum verdict
                            (kotoba.lang.witness-quorum's :kind, injected via
                            the proposal, never vendored here) is a hard
                            violation, exactly like an unbacked reachability
                            edge is under :access/assess.
      3. No-actuation      — effect must be :treasury-release (a record for a
                            separate settlement step to read and execute);
                            this governor never moves funds itself.
    :witness/dispute (ADR-2607110300 Phase 4 -- a contested slashing
                      verdict; there is no automated dispute-resolution
                      algorithm anywhere in this system, deliberately: per
                      the ADR's own alternatives-considered section,
                      designing dispute economics against a hypothetical
                      counterparty is not verifiable today. This op
                      therefore has exactly one behavior: record the
                      dispute and ALWAYS escalate, unconditionally — never
                      auto-resolves, regardless of confidence or how clean
                      the proposal looks)
      1. Valid node key   — the disputing actor must itself be a live,
                            non-revoked, non-expired node.
      2. No-actuation      — effect must be :dispute-record (a record for
                            a human to read and decide); this governor
                            never resolves a dispute itself.
    :availability/slash (mirrors :treasury/release's shape exactly, but for
                         the punitive side of value: gates a slashing
                         action on a caller-supplied retrieval-availability
                         verdict from kotoba-lang/kotobase-peer's
                         availability.cljc `audit-outcome`
                         — `{:kotobase.availability/node ...
                            :kotobase.availability/cid ...
                            :kotobase.availability/epoch ...
                            :kotobase.availability/verdict ...}` —
                         instead of a witness-quorum verdict. This governor
                         does not run the retrieval audit itself, same
                         'caller collects, governor only censors' split as
                         witness-verdict-violations.)
      1. Valid node key   — same identity check as every other op; the
                            requesting actor must be a live, non-revoked,
                            non-expired node.
      2. Proof must have actually failed — deny-by-default for punitive
                            action: an absent verdict, or a verdict whose
                            `:kotobase.availability/verdict` is anything
                            other than :failed/:missed (i.e. :ok,
                            :malformed, or :verifier-lacks-replica — an
                            inconclusive outcome), is a hard violation.
                            Ambiguous evidence never authorizes a slash,
                            exactly like an unbacked reachability edge is
                            under :access/assess or a non-:witnessed
                            verdict is under :treasury/release.
      3. No-actuation      — effect must be :slash-record (a record for a
                            separate settlement step to read and execute);
                            this governor never adjusts balances or
                            reputation itself.
  SOFT:
    4. Confidence floor → escalate.
    5. Node admission, exit-route approval, PEERING APPROVAL, treasury
       release, witness disputes, AND availability slashing are all
       high-stakes → ALWAYS human. Disputes go further than the others:
       confidence is irrelevant, there is no path to :ok? at all for that op."
  (:require [clojure.string :as str]
            [kekkai.acl :as acl]
            [kekkai.store :as store]))

(def confidence-floor 0.6)

(defn- now-of [request] (:now request store/demo-now))

;; ───────────────────────── invariant checks ─────────────────────────

(defn- key-violations [nd now label]
  (cond-> []
    (nil? nd)
    (conj {:rule :no-node :detail (str "未登録ノード " label)})
    (and nd (str/blank? (:did nd)))
    (conj {:rule :no-node-key :detail "node key(did:key)未提示"})
    (and nd (= "revoked" (:status nd)))
    (conj {:rule :revoked-key :detail "ノード鍵が失効済み(revoked)"})
    (and nd (number? (:key-expiry nd)) (<= (:key-expiry nd) now))
    (conj {:rule :expired-key :detail "ノード鍵の有効期限切れ"})))

(defn- tag-violations [policy nd]
  (let [bad (acl/unowned-tags policy nd)]
    (when (seq bad)
      [{:rule :tag-not-owned
        :detail (str "所有者が認可されていない tag を主張: " bad)}])))

(defn- actuation-violations [proposal]
  ;; coordinate→publish-netmap: the actor writes a :netmap control-plane record,
  ;; never a WireGuard/data-plane push.
  (when (not= :netmap (:effect proposal))
    [{:rule :no-actuation
      :detail (str "actor はデータ面(WireGuard)を作動させない(coordinate→netmap)。effect="
                   (:effect proposal))}]))

(defn- ports-within-grant?
  "True iff every port the proposal claims for a peer is actually covered by
  the matching grant's `granted` ports. `[\"*\"]` in the GRANT is the only
  legitimate wildcard (matches anything); a wildcard/absent :ports on the
  PROPOSAL is never trusted on its own -- it must still be checked against
  what the grant actually allows.

  An omitted/empty `proposed` is NOT auto-accepted: (every? pred []) is
  vacuously true, which would silently trust an under-specified proposal
  (no :ports claimed at all) unless the grant itself is a full [\"*\"]
  wildcard -- matching this function's own documented intent rather than
  defaulting open on missing information."
  [granted proposed]
  (cond
    (= ["*"] (vec granted)) true
    (empty? proposed)       false
    :else                   (every? (set granted) proposed)))

(defn- deny-by-default-violations [subject proposal st now]
  ;; every proposed peer edge must be backed by a grant AND the peer must be a
  ;; live authorized node — zero-trust: no implicit allow. A grant existing at
  ;; all is not enough: the proposal's claimed :ports must also fit inside the
  ;; grant's actual allowed ports, or a proposal can silently over-claim
  ;; access (e.g. claiming ["*"] against a grant scoped to [22 443]) and still
  ;; pass as "backed by a grant."
  ;;
  ;; The decision comes from acl/edge-decision, which settles the ORGANIZATION
  ;; boundary before it looks at any grant. That ordering is the whole point:
  ;; two orgs that both named a tag `tag:server` would otherwise match each
  ;; other's grants, and the resulting edge would look perfectly "backed by a
  ;; grant" to a check that only asked whether some grant matched.
  (let [plane (store/plane st)]
    (->> (:peers proposal)
         (keep (fn [{:keys [peer ports]}]
                 (let [pn (store/node st peer)
                       d  (acl/edge-decision plane subject pn)]
                   (cond
                     (seq (key-violations pn now peer))
                     {:rule :peer-key-invalid :detail (str "到達先ノード鍵が無効: " peer)}
                     (not= "authorized" (:status pn))
                     {:rule :peer-unauthorized :detail (str "到達先が未認可: " peer)}
                     (= :cross-tailnet (:reason d))
                     {:rule :cross-tailnet
                      :detail (str "組織(tailnet)を跨ぐ到達edgeを提案。有効な peering が無い: "
                                   (:from d) " → " (:to d) " (" peer ")")}
                     (= :peering-grant-missing (:reason d))
                     {:rule :cross-tailnet
                      :detail (str "peering " (:peering d) " に " (:from d)
                                   " 発の grant が無い到達edgeを提案: → " peer)}
                     (not (:allowed? d))
                     {:rule :deny-by-default
                      :detail (str "ACL grant のない到達edgeを提案: → " peer)}
                     (not (ports-within-grant? (:ports d) (or ports [])))
                     {:rule :deny-by-default
                      :detail (str "grant の許可範囲(" (:ports d) ")を超えるportを提案: → "
                                   peer " " ports)}))))
         vec)))

(defn- tenancy-violations
  "The organization boundary as an admission question rather than a
  reachability one.

  A node is admitted into exactly one tailnet, and three things have to agree
  before that admission means anything: the tailnet exists and is active, the
  owner belongs to it, and — because tag ownership is read from the tailnet's
  own policy — the node is not claiming membership of an organization that has
  not published a policy at all. Skipping the owner check would let any
  organization's admin enroll a machine into any other organization simply by
  naming its tailnet in the registration."
  [st nd]
  (let [tn-id (acl/tailnet-of nd)
        tn    (store/tailnet st tn-id)
        owner (store/user st (:user nd))]
    (cond-> []
      ;; The default tailnet is allowed to have no record: it is where every
      ;; pre-tenant node already lives, and demanding registration for it would
      ;; deny an existing deployment its entire node set on upgrade.
      (and (nil? tn) (not= acl/default-tailnet tn-id))
      (conj {:rule :no-tailnet
             :detail (str "未登録の tailnet に所属を主張: " tn-id)})
      (and tn (not= "active" (:status tn)))
      (conj {:rule :tailnet-inactive
             :detail (str "tailnet が active でない: " tn-id " (" (:status tn) ")")})
      (and owner (not= tn-id (acl/tailnet-of owner)))
      (conj {:rule :owner-tailnet-mismatch
             :detail (str "所有者 " (:user nd) " は tailnet " (acl/tailnet-of owner)
                          " の所属で、" tn-id " へノードを持ち込めない")}))))

(defn- witness-verdict-violations
  "Deny-by-default for value release (ADR-2607110300 Phase 3): mirrors
  deny-by-default-violations' philosophy (no implicit allow) but the
  'grant' is a witness-quorum verdict instead of an ACL edge. `proposal`
  carries `:witness-verdict` — the caller's own witness-quorum result map
  (`{:kind :witnessed|:rejected|:pending|:escalated ...}`, the same shape
  kotoba.lang.witness-quorum.quorum/quorum-state returns and
  cloud-murakumo.ledger.witness/witness-run threads through as `:state`).
  This governor does not run quorum collection itself -- it only censors
  the verdict a caller already collected, same as it never runs ACL
  matching itself in isolation from acl.cljc."
  [proposal]
  (let [verdict (:witness-verdict proposal)]
    (cond
      (nil? verdict)
      [{:rule :no-witness-verdict :detail "witness quorum の検証結果が提示されていない"}]
      (not= :witnessed (:kind verdict))
      [{:rule :witness-quorum-not-reached
        :detail (str "witness quorum が到達していない: " (:kind verdict))}]
      :else [])))

(defn- treasury-actuation-violations
  "Same no-actuation philosophy as actuation-violations, scoped to the
  value-release effect: an approved proposal must resolve to a
  :treasury-release record for a separate settlement step, never a direct
  fund movement carried by this governor."
  [proposal]
  (when (not= :treasury-release (:effect proposal))
    [{:rule :no-actuation
      :detail (str "actor は treasury 残高を直接動かさない(govern は hold|commit のみ)。effect="
                   (:effect proposal))}]))

(defn- dispute-actuation-violations
  "Same no-actuation philosophy as actuation-violations/
  treasury-actuation-violations, scoped to disputes: an approved proposal
  must resolve to a :dispute-record for a human to read and decide, never
  a resolution this governor computes itself. There is deliberately no
  'dispute-resolved-in-favor-of-X' verdict shape anywhere in this
  namespace (ADR-2607110300 Phase 4)."
  [proposal]
  (when (not= :dispute-record (:effect proposal))
    [{:rule :no-actuation
      :detail (str "actor は異議申立ての記録のみを行い、判定は行わない(必ず human が最終判断)。effect="
                   (:effect proposal))}]))

(defn- retrieval-verdict-violations
  "Deny-by-default for punitive action: mirrors witness-verdict-violations'
  philosophy (no implicit allow) but the 'grant' is a retrieval-availability
  proof instead of a witness-quorum verdict. `proposal` carries
  `:retrieval-verdict` — the caller's own audit-outcome result map (same
  shape kotoba-lang/kotobase-peer's availability.cljc `audit-outcome`
  returns: `{:kotobase.availability/node ... :kotobase.availability/cid ...
  :kotobase.availability/epoch ... :kotobase.availability/verdict v}`).
  This governor does not run the retrieval audit itself -- it only censors
  the verdict a caller already collected, same as it never runs witness
  quorum collection in isolation from witness-verdict-violations. Only
  :failed/:missed authorize a slash; :ok, :malformed and
  :verifier-lacks-replica are inconclusive outcomes and must never
  authorize a punitive action."
  [proposal]
  (let [verdict (:retrieval-verdict proposal)]
    (cond
      (nil? verdict)
      [{:rule :no-retrieval-verdict :detail "retrieval-availability の検証結果が提示されていない"}]
      (not (#{:failed :missed} (:kotobase.availability/verdict verdict)))
      [{:rule :retrieval-proof-not-failed
        :detail (str "retrieval-availability 証明が失敗/未達ではない(slash不可): "
                     (:kotobase.availability/verdict verdict))}]
      :else [])))

(defn- slash-actuation-violations
  "Same no-actuation philosophy as actuation-violations/
  treasury-actuation-violations, scoped to slashing: an approved proposal
  must resolve to a :slash-record for a separate settlement step to read
  and execute, never a direct balance/reputation adjustment carried by
  this governor."
  [proposal]
  (when (not= :slash-record (:effect proposal))
    [{:rule :no-actuation
      :detail (str "actor はノードの残高/reputationを直接動かさない(govern は record のみ)。effect="
                   (:effect proposal))}]))

(defn- hijack-violations
  "A route takeover is a conflict WITHIN one organization's tailnet.

  Scoped per-tailnet because private address space is shared by design:
  10.0.0.0/24 is not a globally unique name, and two organizations both
  advertising it is the normal case, not a hijack. A global scan reports the
  second organization to register a perfectly ordinary RFC1918 subnet as
  hijacking the first — and, worse, the first org learns of the second's
  internal addressing from the denial. The check that matters is whether
  SOMEONE ELSE IN MY OWN TAILNET already owns this prefix."
  [st node-id route]
  (when route
    (let [tn (acl/tailnet-of (store/node st node-id))
          conflict (->> (store/all-routes st)
                        (filter #(and (= (:cidr route) (:cidr %))
                                      (not= node-id (:node %))
                                      (:approved? %)
                                      (= tn (acl/tailnet-of (store/node st (:node %)))))))]
      (when (seq conflict)
        [{:rule :route-hijack
          :detail (str (:cidr route) " は同一 tailnet(" tn ") 内で既に別ノードに承認済み: "
                       (mapv :node conflict))}]))))

;; ───────────────────────── peering (cross-org) ─────────────────────────

(defn- peering-actuation-violations
  "Same no-actuation philosophy as actuation-violations, scoped to peering: an
  approved proposal must resolve to a :peering-record, never a netmap this
  governor publishes on the strength of a peering that is still one signature
  short."
  [proposal]
  (when (not= :peering-record (:effect proposal))
    [{:rule :no-actuation
      :detail (str "actor は peering の記録のみを行う(netmap 発行は別 op)。effect="
                   (:effect proposal))}]))

(defn- peering-violations
  "A peering approval is one organization speaking for itself, and nothing else.

  `request` carries `:peering` (the record's id) and `:as` (the tailnet the
  approver is acting for). The invariants:

    1. The peering exists and names two DISTINCT, registered tailnets. A
       self-peering would be a second, quieter way to answer an intra-tailnet
       reachability question that the ACL already answers.
    2. `:as` is one of the two endpoints. Otherwise a third organization —
       or the actor itself — could sign on behalf of parties to an agreement
       it is not part of.
    3. The approving node belongs to `:as`. Holding a valid node key is not
       the same as holding it in the organization being committed.
    4. Every grant the peering carries is directional and originates at one of
       the two endpoints — an `:from` naming a third tailnet would smuggle a
       path for an organization that never signed anything."
  [st request]
  (let [pr    (first (filter #(= (:peering request) (:id %)) (store/all-peerings st)))
        as    (:as request)
        ends  (when pr (acl/peering-endpoints pr))
        subj  (store/node st (:node request))]
    (cond-> []
      (nil? pr)
      (conj {:rule :no-peering :detail (str "未登録の peering: " (:peering request))})

      (and pr (or (not= 2 (count ends)) (some nil? ends)))
      (conj {:rule :peering-self
             :detail (str "peering は異なる2つの tailnet を指す必要がある: " ends)})

      (and pr (not (contains? ends as)))
      (conj {:rule :not-a-party
             :detail (str "承認者 tailnet " as " はこの peering の当事者ではない: " ends)})

      (and pr subj (not= as (acl/tailnet-of subj)))
      (conj {:rule :approver-tailnet-mismatch
             :detail (str "承認ノード " (:node request) " は tailnet "
                          (acl/tailnet-of subj) " 所属で、" as " を代表できない")})

      (and pr (seq (remove #(contains? ends (:from %)) (:grants pr))))
      (conj {:rule :foreign-grant
             :detail (str "当事者でない tailnet 発の grant を含む: "
                          (mapv :from (remove #(contains? ends (:from %)) (:grants pr))))}))))

(defn check
  "Censors a coord-LLM proposal for a tailnet op. Returns
   {:ok? :violations :confidence :hard? :escalate? :high-stakes?}.

   Hard violations force HOLD and cannot be overridden. Node admission and exit-
   route approval are high-stakes → human admin sign-off even when clean."
  [request proposal st]
  (let [now    (now-of request)
        subj   (store/node st (:node request))
        ;; the SUBJECT's own tailnet policy — never a global one. Reading tag
        ;; ownership from another organization's ACL would let that org decide
        ;; who may claim a tag here.
        policy (store/policy-of st (acl/tailnet-of subj))
        route  (when (= :route/approve (:op request))
                 (first (filter #(= (:route request) (:id %)) (store/routes-of st (:node request)))))
        hard (case (:op request)
               :node/admit
               (into [] (concat (key-violations subj now (:node request))
                                (tenancy-violations st subj)
                                (tag-violations policy subj)
                                (actuation-violations proposal)))
               :access/assess
               (into [] (concat (key-violations subj now (:node request))
                                (deny-by-default-violations subj proposal st now)
                                (actuation-violations proposal)))
               :peering/approve
               (into [] (concat (key-violations subj now (:node request))
                                (peering-violations st request)
                                (peering-actuation-violations proposal)))
               :route/approve
               (into [] (concat (key-violations subj now (:node request))
                                (hijack-violations st (:node request) route)
                                (actuation-violations proposal)))
               :treasury/release
               (into [] (concat (key-violations subj now (:node request))
                                (witness-verdict-violations proposal)
                                (treasury-actuation-violations proposal)))
               :witness/dispute
               (into [] (concat (key-violations subj now (:node request))
                                (dispute-actuation-violations proposal)))
               :availability/slash
               (into [] (concat (key-violations subj now (:node request))
                                (retrieval-verdict-violations proposal)
                                (slash-actuation-violations proposal)))
               ;; an unrecognized :op is itself a hard violation (fail-closed:
               ;; a not-yet-wired op must never silently pass as clean) --
               ;; same invariant denrei/koyomi/tayori's governors already
               ;; enforce for their own ops.
               [{:rule :unrecognized-op :detail (str "未対応 op: " (:op request))}])
        conf    (:confidence proposal 0.0)
        low?    (< conf confidence-floor)
        stakes? (or (= :node/admit (:op request))
                    (= :treasury/release (:op request))
                    (= :witness/dispute (:op request))
                    (= :availability/slash (:op request))
                    ;; opening a path between two organizations is at least as
                    ;; consequential as admitting one machine — always human.
                    (= :peering/approve (:op request))
                    (and (= :route/approve (:op request)) (= "exit" (:kind route))))
        hard?   (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact [request verdict]
  {:t (case (:op request)
        :treasury/release :treasury-hold
        :witness/dispute :witness-dispute-hold
        :availability/slash :availability-slash-hold
        :peering/approve :peering-hold
        :tailnet-hold)
   :op (:op request) :node (:node request)
   :disposition :hold :basis (mapv :rule (:violations verdict))
   :violations (:violations verdict) :confidence (:confidence verdict)})
