(ns shinkansen.actions
  "Declared mutations — the mutations increment (maturity axis C).

  A form POST or an agent dispatch is the SAME thing: an event into the
  state chain (shinkansen.state). This namespace turns declared events
  into a dispatch surface the browser and an MCP agent both use:

    {:events {:cart/add {:validate (fn [event] …)}}}

  → {:kind :action :event [::cart/add {:id x}]}   (a shinkansen.invoke envelope)
     → authorize (invoke) → validate → state/chain-entry
     → {:ok true :db-cid … :height …}

  Fail-closed: an undeclared event, or one failing its :validate fn, is
  refused BY NAME before any chain entry is built. The response carries
  the new db CID so the caller (browser or agent) can verify and follow
  the chain — one surface, both clients.

  This namespace is the POST-authorization step. It validates shape and
  declaration, not authority: a binding (HTTP route, MCP tool, CLI) must
  reach it through `shinkansen.invoke/dispatch`, which decides
  {:principal :artifact :grant :effect} first and refuses without an
  authorizer. Calling this directly from a binding is the hazard SPEC
  §1.5 names — reaching the binding becomes the authority.

  Pure .cljc. The host owns the HTTP binding and persistence; this owns
  the declaration → validation → chain-entry seam."
  (:require [shinkansen.form :as form]
            [shinkansen.state :as state]))

(defn declared?
  "Is this event id in the declaration?"
  [decl event-id]
  (contains? (:events decl) event-id))

(defn validate-event
  "Run the event's :validate — a fn over the event, or a shinkansen.form
  schema map (`{:fields …}`) over the event's values map (`[id values]`).
  Returns {:ok true} or {:ok false :reason ...} naming the event id; with
  a schema the refusal carries :errors / :messages by field, so the host
  can put them on the markup (form/field-attrs)."
  [decl event]
  (let [id (first event)
        v (get-in decl [:events id :validate])
        schema (when (and (map? v) (contains? v :fields)) v)
        f (cond schema (form/event-validator schema) (fn? v) v)]
    (cond
      (not (declared? decl id))
      {:ok false :reason :undeclared-event :event-id id}
      (and f (not (f event)))
      (cond-> {:ok false :reason :validation-failed :event-id id}
        schema (merge (select-keys (form/validate schema (if (map? (second event)) (second event) {}))
                                   [:errors :messages])))
      :else {:ok true})))

(defn dispatch
  "Validate then build the chain entry. `state-fn` is the guest's
  re-frame step (db + event → db); `cid-fn` the content-address hash;
  `prev-cid` the current chain head (nil = genesis).
  Returns {:ok true :entry …} or {:ok false :reason …} — the caller
  (browser POST handler, MCP agent) sees the same shape."
  [decl event {:keys [db state-fn cid-fn prev-cid height]}]
  (let [v (validate-event decl event)]
    (if-not (:ok v)
      v
      (let [next-db (state-fn db event)
            ;; was (resolve 'shinkansen.state/db-text): on nbb `resolve` answers
            ;; nil when nothing else has loaded the ns — green in the suite (state-test
            ;; loads it), null.call in the running host (host-node-check, 2026-09-16)
            text (state/db-text next-db)
            cid (cid-fn text)
            entry {:db next-db :db-cid cid :prev prev-cid :event event
                   :height (or height 0) :text text}]
        {:ok true :entry entry}))))
