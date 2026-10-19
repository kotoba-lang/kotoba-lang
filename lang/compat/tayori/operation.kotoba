(ns tayori.operation
  "CorrespondenceActor — one draft/send or revise/publish operation = one
  supervised actor run, a langgraph-clj StateGraph. Two flows share one
  auditable graph:

    ingest (record-op):  intake → record → END
        a thread/message/contact/document becomes a durable ground fact.
        This is the observe charter; always on, never an LLM call, never an
        outbound send/publish.

    assess (assess-op):  intake → advise → govern → decide → commit|hold|approval
        reply-LLM (sealed) proposes a reply draft / document revision, or (for
        send/publish) a pass-through recommendation over an already-committed
        draft/revision; ComplianceGovernor enforces no-actuation / redaction /
        consent / tenant-isolation; the phase gate adds caution; sending a
        reply or publishing a document ALWAYS routes to a human
        (interrupt-before :request-approval).

  Single invariant (the tayori analog of robotaxi's safety contract / kekkai's
  no-data-plane-actuation):
    the actor never sends a message or publishes a document the
    ComplianceGovernor would reject, and reply-LLM never actuates directly —
    committing a draft/revision is data (a 'casual commit'); only a human
    approval turns it into an outbound send/publish (a 'PR merge')."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [tayori.replyllm :as replyllm]
            [tayori.governor :as gov]
            [tayori.phase :as phase]
            [tayori.channel :as channel]
            [tayori.docport :as docport]
            [tayori.store :as store]))

(defn- request->record
  "Map an ingest request to a store ground-fact record."
  [{:keys [op thread contact document value]}]
  (case op
    :thread/register   {:kind :thread   :id thread   :value value}
    :message/ingest    {:kind :message  :id thread   :value value}
    :contact/register  {:kind :contact  :id contact  :value value}
    :document/register {:kind :document :id document :value value}))

(defn- subject [{:keys [thread document contact]}] (or thread document contact))

(defn- pending-record
  "The store record a clean/approved assess op commits. Draft/revise store
  the proposal itself; send/publish only flip the already-stored draft's/
  revision's :status (the send/publish is the EFFECT, tracked separately by
  commit-effects!, not new draft/revision content)."
  [op proposal subj]
  (case op
    :reply/draft
    {:kind :draft :id subj
     :value {:text (:text proposal) :confidence (:confidence proposal)
             :cites (:cites proposal) :redactions (:redactions proposal) :status :proposed}}
    :document/revise
    {:kind :revision :id subj
     :value {:diff (:diff proposal) :confidence (:confidence proposal)
             :cites (:cites proposal) :redactions (:redactions proposal) :status :proposed}}
    :reply/send    {:kind :draft :id subj :value {:status :sent}}
    :document/publish {:kind :revision :id subj :value {:status :published}}))

(defn- commit-effects!
  "Perform the op-specific EXTERNAL effect BEFORE anything is written to the
  store — if the Channel/DocTarget call throws (network error, GitHub
  conflict, …), no store mutation and no :committed ledger fact happen, so
  the store never durably claims a send/publish that didn't actually occur.

  `:document/revise` reads its diff from `record` (the commit about to be
  written), NOT from the store — the store doesn't have it yet at this point.
  `:reply/send`/`:document/publish` read the draft/revision to send/publish
  from the store, which is safe: that content was already committed in an
  EARLIER run (`:reply/draft`/`:document/revise`), not this one.

  Returns a map of extra store facts to merge in on success (currently just
  `:document/revise`'s returned :branch, so `:document/publish` later knows
  what to merge), or nil."
  [channel docport store {:keys [op thread document target]} record]
  (case op
    :document/revise
    (let [doc (store/document store document)
          {:keys [branch]} (docport/propose-revision! docport doc (get-in record [:value :diff]))]
      {:kind :revision :id document :value {:branch branch}})
    :reply/send
    (let [th (store/thread store thread) d (store/draft-of store thread)]
      (channel/send-reply! channel th (:text d))
      nil)
    :document/publish
    (let [doc (store/document store document) r (store/revision-of store document)]
      (docport/publish! docport doc target r)
      nil)
    nil))

(defn build
  "Compiles a CorrespondenceActor bound to `store` (any tayori.store/Store).
  opts: :advisor (default mock), :channel (default mock), :docport (default
  mock), :checkpointer (default in-mem)."
  [store & [{:keys [advisor channel docport checkpointer]
             :or   {advisor      (replyllm/mock-advisor)
                    channel      (tayori.channel/mock-channel)
                    docport      (tayori.docport/mock-doctarget)
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

      ;; ── ingest path: record a ground fact (observe), no LLM/governor ──
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
          (let [p (replyllm/-advise advisor store request)]
            {:proposal p :audit [(replyllm/trace request p)]})))

      (g/add-node :govern
        (fn [{:keys [request proposal]}]
          {:verdict (gov/check request proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [base (phase/verdict->disposition verdict)
                ph   (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)
                subj (subject request)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (gov/hold-fact request verdict)
                         reason (assoc :phase-reason reason :phase ph))]}
              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested :op (:op request) :subject subj
                        :reason (or reason (if (:high-stakes? verdict) :human-signoff
                                               :low-confidence))
                        :recommendation (:recommendation proposal)
                        :phase ph :confidence (:confidence verdict)}]}
              :commit
              {:disposition :commit :record (pending-record (:op request) proposal subj)}))))

      (g/add-node :request-approval
        (fn [{:keys [request proposal approval]}]
          (let [subj (subject request)]
            (if (= :approved (:status approval))
              {:disposition :commit
               :record (update (pending-record (:op request) proposal subj)
                               :value assoc :approved-by (:by approval))
               :audit [{:t :human-signoff :op (:op request) :subject subj
                        :by (:by approval) :recommendation (:recommendation proposal)}]}
              {:disposition :hold
               :audit [{:t :signoff-rejected :op (:op request) :subject subj
                        :disposition :hold :basis [:human-rejected]}]}))))

      ;; op-specific EXTERNAL effect FIRST, then the record + ledger — a
      ;; thrown effect leaves no trace of a send/publish that never happened.
      (g/add-node :commit
        (fn [{:keys [request record]}]
          (let [extra (commit-effects! channel docport store request record)]
            (store/record-datom! store record)
            (when extra (store/record-datom! store extra))
            (let [f {:t :committed :op (:op request) :subject (subject request)
                     :disposition :commit :basis (get-in record [:value :status] :proposed)}]
              (store/append-ledger! store f)
              {:audit [f]}))))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:tayori-hold :signoff-rejected} (:t %)) audit))]
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
