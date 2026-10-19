(ns kekkai.operation
  "CoordinationActor — one tailnet control-plane operation = one supervised
  actor run, a langgraph-clj StateGraph. Two flows share one auditable graph:

    ingest (record-op):  intake → record → END
        node/heartbeat/user/acl/route become durable EAVT ground datoms. This
        is the observe charter; always on, never an LLM call, never a data-plane
        push.

    assess (assess-op):  intake → advise → govern → decide → commit|hold|approval
        the coord-LLM (sealed) proposes an admission / netmap / route decision;
        the TailnetGovernor enforces zero-trust invariants (node-key validity,
        deny-by-default ACL, tag ownership, no route hijack, no actuation); the
        phase gate adds caution; admitting a machine or approving an exit node
        ALWAYS routes to a human admin (interrupt-before :request-approval).

  Single invariant (the kekkai analog of robotaxi's safety contract):
    the actor never installs a reachability edge / admits a device / approves a
    route the TailnetGovernor would reject, and never actuates the data plane
    (it publishes a netmap; the nodes open their own WireGuard tunnels)."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [kekkai.coordllm :as coordllm]
            [kekkai.governor :as gov]
            [kekkai.phase :as phase]
            [kekkai.store :as store]))

(defn- request->record
  "Map an ingest request to a store ground-datom record."
  [{:keys [op node user route tailnet peering value]}]
  (case op
    :node/register    {:kind :node      :id node    :value value}
    :node/heartbeat   {:kind :heartbeat :id node    :value value}
    :user/register    {:kind :user      :id user    :value value}
    :tailnet/register {:kind :tailnet   :id tailnet :value value}
    ;; A peering is PROPOSED as a ground fact and carries no approvals: the
    ;; approvals are separate, governed ops (:peering/approve). Letting the
    ;; proposer seed :approved-by would let one organization write down the
    ;; other's consent.
    :peering/propose  {:kind :peering   :id peering
                       :value (assoc value :approved-by [] :status "active")}
    ;; One ACL document per tailnet. `:tailnet` defaults through
    ;; store/policy-key, so a pre-tenant caller that publishes without naming
    ;; one still lands on the default tailnet rather than a new orphan key.
    :acl/publish      {:kind :policy    :id (store/policy-key tailnet) :value value}
    :route/advertise  {:kind :route     :id route   :value value}))

(defn- subject [{:keys [node user]}] (or node user))

(defn- commit-record
  "Build the commit-time record for an assess op. Reuses the existing
  :assessment store bucket/accessor for :treasury/release too (a :type
  field in the value distinguishes it) rather than adding a new Store
  protocol method + storage slot for one op -- same 'widen an existing
  component's remit' philosophy ADR-2607110300 applies to the governor,
  applied here to storage. `approval` is present only on the
  human-signoff path (nil on the rare auto-commit path -- unreachable
  for :treasury/release today since it's always high-stakes, but the
  branch stays correct if that ever changes)."
  [op subject-id proposal & [approval]]
  (case op
    :treasury/release
    {:kind :assessment :id subject-id
     :value (cond-> {:type :treasury-release
                     :witness-verdict (:witness-verdict proposal)
                     :amount (:amount proposal)
                     :recipient (:recipient proposal)
                     :status "released"}
              approval (assoc :approved-by (:by approval))
              (not approval) (assoc :by :auto))}
    {:kind :assessment :id subject-id
     :value (cond-> {:recommendation (:recommendation proposal)
                     :summary (:summary proposal)
                     :peers (:peers proposal) :route (:route proposal)}
              approval (assoc :approved-by (:by approval))
              (not approval) (assoc :by :auto))}))

(defn- commit-effects!
  "Apply the op-specific control-plane write on commit. Admission flips the
  node to authorized (a membership record, NOT a data-plane push); route
  approval flips the advertised route's approved? flag; peering approval adds
  ONE organization's consent. All are recorded as ground datoms — never a
  WireGuard actuation.

  The peering write adds `:as` to `:approved-by` and never sets the whole
  vector, because the two organizations approve in two separate runs of this
  graph. Writing the vector would let whichever org committed second erase the
  first one's consent — and `acl/peering-active?` demands both, so the erased
  approval would silently close a path everyone believed was open."
  [store {:keys [op node route peering as]}]
  (case op
    :node/admit    (store/record-datom! store {:kind :node :id node :value {:status "authorized"}})
    :route/approve (when-let [r (first (filter #(= route (:id %)) (store/routes-of store node)))]
                     (store/record-datom! store {:kind :route :id route
                                                 :value (assoc r :approved? true)}))
    :peering/approve
    (when-let [pr (first (filter #(= peering (:id %)) (store/all-peerings store)))]
      (store/record-datom! store {:kind :peering :id peering
                                  :value {:approved-by (vec (distinct (conj (vec (:approved-by pr)) as)))}}))
    nil))

(defn build
  "Compiles a CoordinationActor bound to `store` (any kekkai.store/Store).
  opts: :advisor (default mock), :checkpointer (default in-mem)."
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (coordllm/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}   ; :phase + (future) authn
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      ;; ── ingest path: record a ground datom (observe), no LLM/governor ──
      (g/add-node :record
        (fn [{:keys [request]}]
          (let [rec (request->record request)
                f   {:t :recorded :op (:op request) :subject (subject request)
                     :disposition :record :basis (:kind rec)}]
            (store/record-datom! store rec)
            (store/append-ledger! store f)
            {:disposition :record :audit [f]})))

      ;; ── assess path ──
      (g/add-node :advise
        (fn [{:keys [request]}]
          ;; ADR-2607110300 Phase 3: a :treasury/release "proposal" is an
          ;; objective fact the caller already assembled (a witness-quorum
          ;; verdict + amount/recipient), not a judgment call -- running it
          ;; through the coord-LLM would be a category error, so value-ops
          ;; skip -advise; the caller seeds :proposal directly at graph
          ;; invocation, and :advise only records that it arrived.
          (if (contains? phase/value-ops (:op request))
            {:audit [{:t :value-proposal-received :op (:op request) :node (subject request)}]}
            (let [p (coordllm/-advise advisor store request)]
              {:proposal p :audit [(coordllm/trace request p)]}))))

      (g/add-node :govern
        (fn [{:keys [request proposal]}]
          {:verdict (gov/check request proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [base (phase/verdict->disposition verdict)
                ph   (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (gov/hold-fact request verdict)
                         reason (assoc :phase-reason reason :phase ph))]}
              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested :op (:op request) :node (subject request)
                        :reason (or reason (if (:high-stakes? verdict) :admin-signoff
                                               :low-confidence))
                        :recommendation (:recommendation proposal)
                        :phase ph :confidence (:confidence verdict)}]}
              :commit
              {:disposition :commit
               :record (commit-record (:op request) (subject request) proposal)}))))

      (g/add-node :request-approval
        (fn [{:keys [request proposal approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :record (commit-record (:op request) (subject request) proposal approval)
             :audit [{:t :admin-signoff :op (:op request)
                      :node (subject request) :by (:by approval)
                      :recommendation (:recommendation proposal)}]}
            {:disposition :hold
             :audit [(merge (gov/hold-fact request
                                           (assoc verdict :violations
                                                  [{:rule :admin-rejected}]))
                            {:t :signoff-rejected})]})))

      ;; commit an assessment datom + op-specific control-plane write + ledger.
      (g/add-node :commit
        (fn [{:keys [request record]}]
          (store/record-datom! store record)
          (commit-effects! store request)
          (let [basis (or (get-in record [:value :recommendation])
                          (get-in record [:value :status]))
                f {:t :committed :op (:op request) :subject (subject request)
                   :disposition :commit :basis basis}]
            (store/append-ledger! store f)
            {:audit [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          ;; :treasury-hold/:witness-dispute-hold are governor/hold-fact's
          ;; :t for :treasury/release and :witness/dispute (ADR-2607110300
          ;; Phase 3/4) -- included alongside :tailnet-hold so a value-op's
          ;; hold fact actually reaches the ledger instead of being
          ;; silently dropped by this filter.
          (when-let [hf (last (filter #(#{:tailnet-hold :treasury-hold :witness-dispute-hold
                                          :signoff-rejected} (:t %)) audit))]
            (store/append-ledger! store (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      ;; intake routes ingest vs assess.
      (g/add-conditional-edges :intake
        (fn [{:keys [request]}]
          (if (phase/record-op? (:op request)) :record :advise)))
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)
      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition :commit :commit, :escalate :request-approval, :hold)))
      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}] (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :record)
      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer checkpointer :interrupt-before #{:request-approval}})))
