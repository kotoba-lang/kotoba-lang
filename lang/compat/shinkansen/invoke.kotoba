(ns shinkansen.invoke
  "One invocation shape for every transport, and the seam where authority
  is decided — the increment that makes the five axes orthogonal
  (SPEC §1.5, 2026-09-16).

  Before this namespace, shinkansen answered four of the five questions a
  request raises and conflated the fifth with the first:

    who   principal    nobody — no DID anywhere in dispatch or MCP
    what  artifact     the CID (identity, already the centre)
    where origin       {cid}.ipfs.* / {name}.itonami.app (already two Locations)
    may   grant        nobody — `actions/dispatch` validated SHAPE only, and
                       `lake_dispatch` took an event and nothing else, so
                       reaching the binding WAS the authority
    do    effect       the guest's re-frame effects go to the host (ADR D4),
                       but the ONE effect shinkansen itself performs — the
                       chain append — had no name and no gate

  And it spoke three vocabularies for one thing: `shinkansen.actions` calls
  an intent an event, `shinkansen.load` calls a query a load, and the MCP
  tool and the (documented, unbuilt) `POST /dispatch` each encode a dispatch
  in their own body. A URL path is a transport encoding; so is a JSON-RPC
  tool call; so is a CLI argv. The protocol semantic is one envelope:

    {:artifact  \"bafk…\"                 CID — WHAT computation (required)
     :principal \"did:key:z6Mk…\"        DID — WHO asks (optional on the wire,
                                         the authorizer decides if required)
     :grant     <opaque>                 the presented delegation (a Biscuit on
                                         the wire). Opaque here. A grant is an
                                         UPPER BOUND, never authority
                                         (capability-semantics :grant)
     :input     {:kind :query  :params {…}}      ask   — answers a value
                {:kind :action :event  [id …]}   intend — a re-frame event into
                                                 the state chain
                {:kind :event  :source s :frame f} happened — an environment
                                                 fact asserted into the app}

  What the framework does with it, in order, fail-closed at each step:

    check-envelope   the shape, by name (CIDv0 refused, an unknown kind
                     refused, a non-DID principal refused)
    effect-request   the ONE effect this kind asks for, on the artifact's
                     resource — :chain/append, :app/query, :app/assert
    authorize        call the injected `authorize-fn` with
                     {:principal :artifact :grant :effect}. NO authorize-fn
                     is a refusal (`:no-authorizer`), an answer that is not
                     a decision is a refusal (`:authorizer-answer-not-a-
                     decision`) — nil never puns to allow, the way a missing
                     policy became a public database once (kotoba-lang/
                     authority README).
    dispatch / query only after `:ok true`: the existing pure seams
                     (`actions/dispatch`, `load/run-load`) run, and the chain
                     entry records :principal and the authorizer's :receipt —
                     never the grant (capability-semantics
                     :raw-bearer-in-public-car :forbidden).

  What the framework does NOT do: verify a signature, parse a Biscuit, or
  hold a policy. That is `kotoba-lang/authority` (the lattice) and
  `org-biscuitsec`'s `biscuit.kotoba-logic/authorize` (the five-origin join);
  the host binds one of them to `authorize-fn`. Hostname and origin are
  NOT inputs to the decision: an origin is where a computation is
  isolated, not proof of who is asking (SPEC §1.5).

  Pure .cljc. Nothing here touches the network or the store."
  (:require [clojure.string :as str]
            [shinkansen.actions :as actions]
            [shinkansen.interaction :as interaction]
            [shinkansen.load :as load]))

(def ^:const kinds
  "The three things an invocation can be. `query` asks and gets a value,
  `action` intends a change (a re-frame event into the chain), `event`
  asserts that something happened outside (a webhook, a stream frame)."
  #{:query :action :event})

(def ^:const effects
  "The one effect each kind requests. Naming it is what lets the authorizer
  answer — a request with no effect name cannot be granted, only waved
  through."
  {:query  :app/query
   :action :chain/append
   :event  :app/assert})

(defn- cid? [s]
  (and (string? s) (boolean (re-matches #"b[a-z2-7]{20,}" s))))

(defn- cidv0? [s]
  (and (string? s) (str/starts-with? s "Qm")))

(defn- did? [s]
  (and (string? s) (boolean (re-matches #"did:[a-z0-9]+:.+" s))))

(defn check-envelope
  "Validate the envelope's shape. Returns {:ok true :envelope env} or
  {:ok false :reason …} naming the first defect:
    :artifact-required        no :artifact
    :cidv0-refused            :artifact is a Qm… CID (v0 has no codec, the
                              planes only serve CIDv1)
    :artifact-not-a-cid       :artifact is not a CIDv1 base32 label
    :input-required           no :input map
    :unknown-kind             :input :kind not in `kinds` (named in :kind)
    :action-needs-event       :action without a vector :event
    :query-needs-params       :query whose :params is not a map (an absent
                              :params is {} — an empty ask is still an ask)
    :event-needs-source       :event without a :source (an unattributed
                              fact is not an event, it is noise)
    :principal-not-a-did      :principal present but not did:<method>:…
  Presence of :grant proves nothing and is not checked here."
  [env]
  (let [{:keys [artifact principal input]} env
        kind (:kind input)]
    (cond
      (nil? artifact) {:ok false :reason :artifact-required}
      (cidv0? artifact) {:ok false :reason :cidv0-refused :artifact artifact}
      (not (cid? artifact)) {:ok false :reason :artifact-not-a-cid :artifact artifact}
      (not (map? input)) {:ok false :reason :input-required}
      (not (contains? kinds kind)) {:ok false :reason :unknown-kind :kind kind}
      (and (= kind :action) (not (vector? (:event input))))
      {:ok false :reason :action-needs-event}
      (and (= kind :query) (not (map? (or (:params input) {}))))
      {:ok false :reason :query-needs-params}
      (and (= kind :event) (nil? (:source input)))
      {:ok false :reason :event-needs-source}
      (and (some? principal) (not (did? principal)))
      {:ok false :reason :principal-not-a-did :principal principal}
      :else {:ok true
             :envelope (cond-> env
                         (= kind :query) (update-in [:input :params] #(or % {})))})))

(defn resource
  "The resource an invocation touches: the artifact's chain for an action,
  the artifact itself for a query, the artifact's event inbox for an
  event. A `kotoba://` scope string, so `kotoba-lang/authority`'s segment
  lattice covers it without a new scheme."
  [{:keys [artifact input]}]
  (case (:kind input)
    :action (str "kotoba://app/" artifact "/chain")
    :query  (str "kotoba://app/" artifact)
    :event  (str "kotoba://app/" artifact "/events/" (:source input))
    nil))

(defn effect-request
  "The effect this envelope asks the authorizer to allow:
    {:effect :chain/append :resource \"kotoba://app/<cid>/chain\" :event [...]}
  A checked envelope always has one — there is no kind without an effect,
  because an invocation the authorizer cannot name cannot be decided."
  [{:keys [input] :as env}]
  (let [kind (:kind input)]
    (cond-> {:effect (get effects kind) :resource (resource env)}
      (= kind :action) (assoc :event (:event input))
      (= kind :query) (assoc :params (:params input))
      (= kind :event) (assoc :source (:source input)))))

(defn authorize
  "Decide one envelope through the injected seam. `authorize-fn` receives
  {:principal :artifact :grant :effect} (the effect is `effect-request`'s
  map) and must answer a map with :ok. Returns
    {:ok true  :envelope env :effect effect :receipt {…}}
    {:ok false :reason :no-authorizer}                  no seam bound
    {:ok false :reason :authorizer-answer-not-a-decision :answer a}
                                                         nil / non-map / no :ok
    {:ok false :reason :denied :authorizer <its reason> :effect effect}
  plus every `check-envelope` refusal, first. The receipt is the
  authorizer's answer minus anything named :grant/:token — what the chain
  may record. The grant itself is never in the return value."
  [env {:keys [authorize-fn]}]
  (let [chk (check-envelope env)]
    (cond
      (not (:ok chk)) chk
      (not (fn? authorize-fn)) {:ok false :reason :no-authorizer}
      :else
      (let [env (:envelope chk)
            effect (effect-request env)
            answer (authorize-fn {:principal (:principal env)
                                  :artifact (:artifact env)
                                  :grant (:grant env)
                                  :effect effect})]
        (cond
          (or (not (map? answer)) (not (contains? answer :ok)))
          {:ok false :reason :authorizer-answer-not-a-decision :answer answer}
          (not (:ok answer))
          {:ok false :reason :denied :authorizer (:reason answer) :effect effect}
          :else
          {:ok true :envelope env :effect effect
           :receipt (dissoc answer :grant :token)})))))

(defn dispatch
  "An :action, end to end: authorize → `actions/dispatch` (declaration +
  validation + chain entry). The entry carries :principal and :receipt so
  the chain says WHO caused each height and on WHOSE decision — and never
  the grant. `opts` are `actions/dispatch`'s (db state-fn cid-fn prev-cid
  height) plus :authorize-fn. A non-action envelope is refused by name
  (`:not-an-action`) — a query does not append and an event is not an
  intent."
  [decl env opts]
  (let [a (authorize env opts)]
    (cond
      (not (:ok a)) a
      (not= :action (get-in a [:envelope :input :kind]))
      {:ok false :reason :not-an-action :kind (get-in a [:envelope :input :kind])}
      :else
      (let [r (actions/dispatch decl (get-in a [:envelope :input :event]) opts)]
        (if (:ok r)
          (update r :entry assoc
                  :principal (get-in a [:envelope :principal])
                  :receipt (:receipt a))
          r)))))

(defn query
  "A :query, end to end: authorize → `load/run-load` with the envelope's
  params. The answer is a station (a data CID) like every other value in
  shinkansen; the result carries :principal and :receipt for the same
  reason the chain entry does. `opts`: :authorize-fn, :load-fn, :cid-fn,
  :mode. A non-query envelope is refused by name (`:not-a-query`)."
  [env {:keys [load-fn cid-fn mode] :as opts}]
  (let [a (authorize env opts)]
    (cond
      (not (:ok a)) a
      (not= :query (get-in a [:envelope :input :kind]))
      {:ok false :reason :not-a-query :kind (get-in a [:envelope :input :kind])}
      :else
      (let [r (load/run-load {:mode mode :load-fn load-fn :cid-fn cid-fn
                              :params (get-in a [:envelope :input :params])})]
        (if (:ok r)
          (assoc r :principal (get-in a [:envelope :principal]) :receipt (:receipt a))
          r)))))

(defn from-mcp
  "The MCP adapter: a `lake_dispatch` tool call plus the session's identity
  → envelope. The principal and grant come from the SESSION (bound once by
  whoever started the stdio server), never from the tool arguments — a
  token in every tool call's arguments is a token in every log line."
  [{:keys [artifact event]} {:keys [principal grant]}]
  (let [event (when (sequential? event) (vec event))
        ;; over the stdio JSON wire the event id is a string; the
        ;; declaration speaks keywords — one vocabulary (interaction)
        event (if (string? (first event))
                (assoc event 0 (interaction/action->event-id (first event)))
                event)]
    {:artifact artifact
     :principal principal
     :grant grant
     :input {:kind :action :event event}}))

(defn from-route
  "The HTTP adapter's read half: a resolved route (`routes/resolve-path`)
  → a :query envelope. The path was a NAME; what it named is the artifact,
  and its params are the query's params — the URL never reaches the
  authorizer."
  [{:keys [document params]} {:keys [principal grant]}]
  {:artifact document
   :principal principal
   :grant grant
   :input {:kind :query :params (or params {})}})
