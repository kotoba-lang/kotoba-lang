(ns kotoba.lang.incidence
  "Content-addressed incidence values.

  An incidence is an immutable n-ary relation whose participants are named by
  roles. It is data and evidence, never authority by possession: admission to
  an effect still requires verified delegation, local policy, and a concrete
  capability value. The normative shape is `lang/incidence.edn`."
  (:require [clojure.set :as set]
            [cbor.core :as cbor]
            [kotoba.lang.code-identity :as identity]
            [multiformats.core :as mf]))

(def payload-version 1)

(def required-fields
  #{:incidence/kind
    :incidence/roles
    :incidence/facts
    :incidence/parents
    :incidence/evidence
    :incidence/policies})

(def organization-kinds #{:person :agent :organization :system})

(defn typed-ref
  "Construct an explicitly typed participant reference.

  Kinds are :cid for immutable Kotoba/IPLD identity, :did for an interoperable
  principal identifier, and :uri for an external resource. A plain string is
  deliberately not a reference."
  [kind value]
  {:ref/type kind :ref/value value})

(defn ref?
  [x]
  (and (map? x)
       (= #{:ref/type :ref/value} (set (keys x)))
       (case (:ref/type x)
         :cid (identity/cid? (:ref/value x))
         :did (and (string? (:ref/value x))
                   (boolean (re-matches #"did:[a-z0-9]+:[^\s]+" (:ref/value x))))
         :uri (and (string? (:ref/value x))
                   (boolean (re-matches #"[A-Za-z][A-Za-z0-9+.-]*:[^\s]+"
                                        (:ref/value x))))
         false)))

(defn- singleton-role? [roles role]
  (= 1 (count (get roles role))))

(defn- known-kind-error [block]
  (let [kind (:incidence/kind block)
        roles (:incidence/roles block)
        facts (:incidence/facts block)]
    (case kind
      :organization/constitution
      (when-not (and (= #{:organization/constituent} (set (keys roles)))
                     (contains? organization-kinds (:organization/kind facts)))
        {:problem :organization/constitution})

      :organization/member-added
      (when-not (and (= #{:organization :member} (set (keys roles)))
                     (singleton-role? roles :organization)
                     (singleton-role? roles :member)
                     (set? (:membership/roles facts))
                     (every? keyword? (:membership/roles facts)))
        {:problem :organization/member-added})

      :organization/member-removed
      (let [removes (:membership/removes facts)]
        (when-not (and (= #{:organization :member} (set (keys roles)))
                       (singleton-role? roles :organization)
                       (singleton-role? roles :member)
                       (set? removes)
                       (seq removes)
                       (every? identity/cid? removes)
                       (every? (:incidence/parents block) removes))
          {:problem :organization/member-removed}))

      :dataspace/retracted
      (let [retracts (:dataspace/retracts facts)]
        (when-not (and (= #{:dataspace/retractor} (set (keys roles)))
                       (singleton-role? roles :dataspace/retractor)
                       (set? retracts)
                       (seq retracts)
                       (every? identity/cid? retracts)
                       (every? (:incidence/parents block) retracts))
          {:problem :dataspace/retracted}))

      nil)))

(defn incidence-error
  "Return a fail-closed shape/canonicalization diagnostic, or nil. Unknown
  top-level fields are rejected so an author cannot believe unhashed metadata
  participates in identity."
  [block]
  (or
   (when-not (map? block)
     {:problem :incidence/not-a-map})
   (when (map? block)
     (let [actual (set (keys block))]
       (when (not= required-fields actual)
         {:problem :incidence/fields
          :missing (set/difference required-fields actual)
          :unknown (set/difference actual required-fields)})))
   (when-not (keyword? (:incidence/kind block))
     {:problem :incidence/kind})
   (let [roles (:incidence/roles block)]
     (when-not (and (map? roles)
                    (seq roles)
                    (every? keyword? (keys roles))
                    (every? #(and (set? %) (seq %) (every? ref? %))
                            (vals roles)))
       {:problem :incidence/roles}))
   (when-not (map? (:incidence/facts block))
     {:problem :incidence/facts})
   (some (fn [field]
           (let [xs (get block field)]
             (when-not (and (set? xs) (every? identity/cid? xs))
               {:problem :incidence/cid-set :field field})))
         [:incidence/parents :incidence/evidence :incidence/policies])
   (known-kind-error block)
   (try
     (identity/normalize block)
     nil
     (catch #?(:clj Exception :cljs :default) e
       {:problem :incidence/uncanonical-value
        :cause (:problem (ex-data e))}))))

(defn identity-payload
  [block]
  {:kotoba.incidence/version payload-version
   :kind (:incidence/kind block)
   :roles (:incidence/roles block)
   :facts (:incidence/facts block)
   :parents (:incidence/parents block)
   :evidence (:incidence/evidence block)
   :policies (:incidence/policies block)})

(defn canonical-bytes
  "Canonical DAG-CBOR bytes. Sets make participant and parent order
  non-semantic; `identity/normalize` supplies the cross-host total order."
  [block]
  (if-let [error (incidence-error block)]
    (throw (ex-info "invalid incidence block" error))
    (cbor/encode (identity/normalize (identity-payload block)))))

(defn canonical-hex
  "Hex form for frozen cross-implementation conformance vectors."
  [block]
  (let [digits "0123456789abcdef"]
    (apply str
           (mapcat (fn [b]
                     (let [v (bit-and b 0xff)]
                       [(nth digits (bit-shift-right v 4))
                        (nth digits (bit-and v 0x0f))]))
                   (seq (canonical-bytes block))))))

(defn incidence-cid
  [block]
  (mf/cidv1-dag-cbor (canonical-bytes block)))

(defn addressed
  "Return a transport envelope containing the immutable block and its CID."
  [block]
  {:incidence/cid (incidence-cid block)
   :incidence/block block})

(defn verify-addressed
  "Verify an addressed envelope before projection."
  [entry]
  (let [claimed (:incidence/cid entry)
        block (:incidence/block entry)]
    (cond
      (not (identity/cid? claimed))
      {:ok? false :reason :incidence/cid-invalid}

      :else
      (try
        (let [actual (incidence-cid block)]
          (if (= claimed actual)
            {:ok? true :cid actual :block block}
            {:ok? false :reason :incidence/hash-mismatch
             :claimed claimed :actual actual}))
        (catch #?(:clj Exception :cljs :default) e
          {:ok? false :reason :incidence/block-invalid :error (ex-data e)})))))

(defn admit-append
  "Admit BLOCK onto a known immutable DAG. Every parent must already exist;
  evidence and policy CIDs may resolve through another trusted store. This is
  structural admission only and never returns a capability."
  [known-cids block]
  (try
    (let [entry (addressed block)
          missing (set (remove (set known-cids) (:incidence/parents block)))]
      (if (seq missing)
        {:ok? false :reason :incidence/unknown-parent :missing missing}
        {:ok? true :entry entry}))
    (catch #?(:clj Exception :cljs :default) e
      {:ok? false :reason :incidence/block-invalid :error (ex-data e)})))

(defn project
  "Verify ENTRIES and build deterministic indexes by CID, kind, and role.
  Refuses the whole projection if one entry is malformed or substituted."
  [entries]
  (let [projection
        (reduce
         (fn [projection entry]
           (if-not (:ok? projection)
             (reduced projection)
             (let [verified (verify-addressed entry)]
               (if-not (:ok? verified)
                 (reduced verified)
                 (let [{:keys [cid block]} verified]
                   (if (contains? (:blocks projection) cid)
                     projection
                     (reduce-kv
                      (fn [p role refs]
                        (reduce (fn [p participant]
                                  (update-in p [:by-role role participant]
                                             (fnil conj #{}) cid))
                                p refs))
                      (-> projection
                          (assoc-in [:blocks cid] block)
                          (update-in [:by-kind (:incidence/kind block)]
                                     (fnil conj #{}) cid))
                      (:incidence/roles block))))))))
         {:ok? true :blocks {} :by-kind {} :by-role {}}
         entries)]
    (if-not (:ok? projection)
      projection
      (let [known (set (keys (:blocks projection)))
            missing (into #{}
                          (comp (mapcat :incidence/parents)
                                (remove known))
                          (vals (:blocks projection)))]
        (if (seq missing)
          {:ok? false :reason :incidence/incomplete-history :missing missing}
          projection)))))

(defn incidence
  "Construct the closed v1 block shape."
  [kind roles {:keys [facts parents evidence policies]
               :or {facts {} parents #{} evidence #{} policies #{}}}]
  {:incidence/kind kind
   :incidence/roles roles
   :incidence/facts facts
   :incidence/parents parents
   :incidence/evidence evidence
   :incidence/policies policies})

(defn assertion
  "Pure Syndicate-style assertion constructor: address an inert incidence
  value without publishing it or acquiring authority."
  [block]
  (addressed block))

(defn retract
  "Construct an append-only dataspace retraction targeting exact assertion
  CIDs. Targets are also parents, so structural admission requires them."
  [retractor target-cids opts]
  (when-not (and (set? target-cids)
                 (seq target-cids)
                 (every? identity/cid? target-cids))
    (throw (ex-info "retraction requires assertion CIDs"
                    {:problem :dataspace/retracts})))
  (incidence :dataspace/retracted
             {:dataspace/retractor #{retractor}}
             (-> opts
                 (update :parents #(into (or % #{}) target-cids))
                 (update :facts #(assoc (or % {})
                                        :dataspace/retracts target-cids)))))

(defn active-projection
  "Project the currently asserted dataspace. Retractions are monotonic
  tombstones in v1; they cannot resurrect another retraction."
  [entries]
  (let [p (project entries)]
    (if-not (:ok? p)
      p
      (let [blocks (:blocks p)
            retracted (into #{}
                            (comp
                             (filter #(= :dataspace/retracted
                                         (:incidence/kind %)))
                             (mapcat #(get-in % [:incidence/facts
                                                :dataspace/retracts] #{}))
                             (remove #(= :dataspace/retracted
                                         (get-in blocks [% :incidence/kind]))))
                            (vals blocks))
            active (apply dissoc blocks retracted)]
        {:ok? true
         :blocks blocks
         :active-blocks active
         :retracted retracted}))))

(defn observe
  "Query an active projection with inert EDN selectors.

  QUERY is `{:kind keyword? :roles {role typed-ref}}`; omitted fields are
  wildcards. This first slice intentionally has no reader tags, macro
  execution, callbacks, or capability resolution."
  [projection {:keys [kind roles]}]
  (if-not (:ok? projection)
    projection
    (if-not (and (or (nil? kind) (keyword? kind))
                 (or (nil? roles)
                     (and (map? roles)
                          (every? keyword? (keys roles))
                          (every? ref? (vals roles)))))
      {:ok? false :reason :dataspace/query-invalid}
      {:ok? true
       :matches
       (into {}
             (filter
              (fn [[_ block]]
                (and (or (nil? kind) (= kind (:incidence/kind block)))
                     (every? (fn [[role participant]]
                               (contains? (get-in block [:incidence/roles role] #{})
                                          participant))
                             roles))))
             (:active-blocks projection))})))

(def facet-fields
  "Closed shape for the pure facet lifecycle state. A facet value is inert
  state, not a publishing capability."
  #{:facet/status :facet/owner :facet/assertions :facet/withdrawal})

(defn facet-error
  "Return a fail-closed facet-state diagnostic, or nil. Runtime code may keep
  this value in a lexical binding, but possessing or reconstructing the EDN
  map does not grant authority to publish its emissions."
  [facet]
  (or
   (when-not (map? facet)
     {:problem :facet/not-a-map})
   (when (map? facet)
     (let [actual (set (keys facet))]
       (when (not= facet-fields actual)
         {:problem :facet/fields
          :missing (set/difference facet-fields actual)
          :unknown (set/difference actual facet-fields)})))
   (when-not (contains? #{:open :stopped} (:facet/status facet))
     {:problem :facet/status})
   (when-not (ref? (:facet/owner facet))
     {:problem :facet/owner})
   (let [assertions (:facet/assertions facet)]
     (when-not (and (map? assertions)
                    (every? (fn [[cid entry]]
                              (and (identity/cid? cid)
                                   (= cid (:incidence/cid entry))
                                   (:ok? (verify-addressed entry))
                                   (not= :dataspace/retracted
                                         (get-in entry [:incidence/block
                                                        :incidence/kind]))))
                            assertions))
       {:problem :facet/assertions}))
   (let [withdrawal (:facet/withdrawal facet)]
     (case (:facet/status facet)
       :open (when-not (nil? withdrawal)
               {:problem :facet/open-with-withdrawal})
       :stopped
       (when-not
        (or (and (empty? (:facet/assertions facet)) (nil? withdrawal))
            (and (map? withdrawal)
                 (:ok? (verify-addressed withdrawal))
                 (= :dataspace/retracted
                    (get-in withdrawal [:incidence/block :incidence/kind]))
                 (= #{(:facet/owner facet)}
                    (get-in withdrawal [:incidence/block :incidence/roles
                                        :dataspace/retractor]))
                 (= (set (keys (:facet/assertions facet)))
                    (get-in withdrawal [:incidence/block :incidence/facts
                                        :dataspace/retracts]))))
        {:problem :facet/withdrawal})
       nil))))

(defn facet
  "Open an inert Syndicate-style facet owned by OWNER. OWNER identifies the
  retractor in durable history; it is not itself a publishing capability."
  [owner]
  (let [state {:facet/status :open
               :facet/owner owner
               :facet/assertions {}
               :facet/withdrawal nil}]
    (if-let [error (facet-error state)]
      (throw (ex-info "invalid facet owner" error))
      state)))

(defn facet-assert
  "Purely plan assertion of BLOCK from an open facet.

  Returns `{:ok? true :facet next-state :emit [addressed-incidence]}`. An
  assertion already owned by the facet is idempotent and emits nothing.
  Publishing the returned entry remains a capability-guarded runtime effect."
  [state block]
  (if-let [error (facet-error state)]
    {:ok? false :reason :facet/state-invalid :error error}
    (cond
      (not= :open (:facet/status state))
      {:ok? false :reason :facet/stopped}

      (= :dataspace/retracted (:incidence/kind block))
      {:ok? false :reason :facet/retraction-not-owned}

      :else
      (try
        (let [entry (assertion block)
              cid (:incidence/cid entry)]
          (if (contains? (:facet/assertions state) cid)
            {:ok? true :facet state :emit []}
            {:ok? true
             :facet (assoc-in state [:facet/assertions cid] entry)
             :emit [entry]}))
        (catch #?(:clj Exception :cljs :default) e
          {:ok? false :reason :facet/assertion-invalid :error (ex-data e)})))))

(defn facet-stop
  "Stop a facet and deterministically plan withdrawal of every assertion it
  owns. The first stop emits one append-only retraction (or nothing for an
  empty facet); repeated stop is idempotent and emits nothing."
  ([state] (facet-stop state {}))
  ([state opts]
   (if-let [error (facet-error state)]
     {:ok? false :reason :facet/state-invalid :error error}
     (if (= :stopped (:facet/status state))
       {:ok? true :facet state :emit []}
       (let [targets (set (keys (:facet/assertions state)))
             withdrawal (when (seq targets)
                          (assertion
                           (retract (:facet/owner state) targets opts)))
             stopped (assoc state
                            :facet/status :stopped
                            :facet/withdrawal withdrawal)]
         {:ok? true
          :facet stopped
          :emit (cond-> [] withdrawal (conj withdrawal))})))))

(defn constitute
  "Constitute a person, agent, organization, or system through the same root
  relation. The returned block's CID is the organization's internal identity."
  [kind constituents opts]
  (when-not (contains? organization-kinds kind)
    (throw (ex-info "unknown organization kind"
                    {:problem :organization/kind :kind kind})))
  (incidence :organization/constitution
             {:organization/constituent (set constituents)}
             (update opts :facts #(assoc (or % {}) :organization/kind kind))))

(defn member-added
  [organization member membership-roles opts]
  (incidence :organization/member-added
             {:organization #{organization} :member #{member}}
             (update opts :facts #(assoc (or % {})
                                         :membership/roles
                                         (set membership-roles)))))

(defn member-removed
  "Remove exact member-added incidences. Targeting add CIDs, rather than a
  mutable member slot or wall-clock order, makes concurrent projection stable."
  [organization member add-cids opts]
  (when-not (and (set? add-cids) (seq add-cids) (every? identity/cid? add-cids))
    (throw (ex-info "membership removal requires member-added CIDs"
                    {:problem :membership/removes})))
  (incidence :organization/member-removed
             {:organization #{organization} :member #{member}}
             (-> opts
                 (update :parents #(into (or % #{}) add-cids))
                 (update :facts #(assoc (or % {})
                                        :membership/removes add-cids)))))

(defn project-membership
  "Project an organization membership OR-set from verified addressed entries.
  Returns active member-added entries; a remove only affects adds for the same
  organization and member, so a malicious cross-organization remove is inert."
  [entries organization]
  (let [p (project entries)]
    (if-not (:ok? p)
      p
      (let [blocks (:blocks p)
            org-ref #{organization}
            adds (into {}
                       (keep (fn [[cid block]]
                               (when (and (= :organization/member-added
                                             (:incidence/kind block))
                                          (= org-ref (get-in block [:incidence/roles
                                                                   :organization])))
                                 [cid block])))
                       blocks)
            removed (into #{}
                          (mapcat (fn [[_ block]]
                                    (if (and (= :organization/member-removed
                                                (:incidence/kind block))
                                             (= org-ref
                                                (get-in block [:incidence/roles
                                                               :organization])))
                                      (let [member (get-in block [:incidence/roles :member])]
                                        (filter (fn [add-cid]
                                                  (= member
                                                     (get-in adds [add-cid
                                                                   :incidence/roles
                                                                   :member])))
                                                (get-in block [:incidence/facts
                                                               :membership/removes] #{})))
                                      #{})))
                          blocks)]
        {:ok? true
         :organization organization
         :active-adds (apply dissoc adds removed)
         :members (into #{}
                        (mapcat #(get-in % [:incidence/roles :member]))
                        (vals (apply dissoc adds removed)))}))))
