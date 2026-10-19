(ns chain.authorized
  "Was every commit in this history allowed to be made?

  `chain.core/verify-causal` answers whether a DAG is *sound*: CIDs re-derive,
  cited parents exist, the Lamport clock agrees with the edges. It says
  nothing about permission, and it says so deliberately — `core`'s own comment
  is that deciding a grant was validly issued belongs elsewhere and *a chain
  that pretended to do it would be trusted for something it never checked*.

  This namespace does not decide either. It **joins**: it walks the DAG, and
  for every commit asks an injected resolver about the authority that commit
  names. Issuance stays wherever it was; what was missing is that nothing
  connected the two, so a commit could carry an `authority` CID pointing at a
  grant that was revoked before it was written and every check in the stack
  would still pass.

  ## The two things a caller must state

  Both are required, with no default, matching this codebase's stance
  everywhere else that a silent default is how a security property quietly
  becomes untrue.

  **`:as-of`** — the instant to judge validity at. The DAG carries a Lamport
  clock, which orders events but is not a time; grants carry wall-clock
  `:expires-at` and `:revoked-at`. Those two are not convertible, and pretending
  otherwise is the interesting failure here: a Lamport clock cannot tell you
  whether a grant had expired, and a wall clock cannot tell you which commit
  came first. So the caller names the instant, and the report repeats it back —
  a verdict without the instant it was computed at is not a verdict.

  **`:revocation`** — `:retroactive` or `:prospective`.

  - `:prospective` — a revoked grant invalidates nothing already written.
    History is a record of what was permitted *then*. Choose this when
    receipts are evidence of past authorization.
  - `:retroactive` — a revoked grant invalidates every commit under it.
    Choose this when revocation means *that key was never trustworthy*, which
    is what a compromise means.

  There is no right answer, which is exactly why there is no default.

  ## Merges do not launder

  `:ok?` is false when **any** reachable commit fails. Merging an
  unauthorized branch into an authorized one therefore produces an
  unauthorized history, not a clean one — the merge commit's own authority
  covers the merge, not everything it pulled in. The opposite rule would make
  \"merge it into something legitimate\" a way to bless anything, and it is
  the rule a reader assumes if nobody writes it down."
  (:require [chain.core :as chain]))

(def revocation-policies
  "Both spellings a caller may declare. Unknown is refused rather than guessed."
  #{:retroactive :prospective})

(defn- grant-verdict
  "Why this grant does not permit this actor at this instant, or nil."
  [{:keys [subject revoked-at not-before expires-at]} actor as-of policy]
  (cond
    (nil? subject) :grant-without-subject
    (not= subject actor) :actor-not-the-subject
    (and not-before (neg? (compare as-of not-before))) :not-yet-valid
    (and expires-at (not (neg? (compare as-of expires-at)))) :expired
    (and revoked-at (= policy :retroactive)) :revoked
    (and revoked-at (= policy :prospective)
         (not (neg? (compare as-of revoked-at)))) :revoked
    :else nil))

(defn verify-authorized
  "Verify the DAG reachable from `cid`, and that every commit in it was
  permitted.

  ```clojure
  (verify-authorized get-fn head
                     {:resolve-grant (fn [authority-cid] {:subject \"alice\" ...})
                      :as-of \"2026-08-18T00:00:00Z\"
                      :revocation :prospective})
  ```

  `resolve-grant` is injected because this namespace does not own grants and
  must not learn to: it takes the authority CID a commit names and returns
  `{:subject :revoked-at :not-before :expires-at}`, or nil when it has never
  heard of it. Timestamps are compared with `compare`, so any consistently
  ordered representation works — ISO-8601 strings sort correctly and are the
  obvious choice.

  Returns a report, never a boolean:

  ```clojure
  {:ok? false :as-of \"...\" :revocation :prospective
   :visited 7 :causal {:ok? true ...}
   :findings [{:cid \"bafy…\" :actor \"bob\" :authority \"bafy…\"
               :reason :revoked}]}
  ```

  `:findings` is every commit that failed, not the first — an audit that stops
  at one is an audit you have to run repeatedly to learn what is wrong. It is
  empty exactly when `:ok?` is true, and `:visited` is the evidence floor: a
  report over zero commits is not a pass, and `:ok? false :reason :empty` says
  so rather than returning a clean sheet for a history nobody read.

  The causal report from `chain.core/verify-causal` is included whole under
  `:causal` and its failure is this function's failure. Authorization over a
  history whose CIDs do not re-derive would be a statement about bytes nobody
  verified."
  [get-fn cid {:keys [resolve-grant as-of revocation max-visits]
               :or {max-visits 4096}}]
  (when-not (ifn? resolve-grant)
    (throw (ex-info "chain.authorized: :resolve-grant is required"
                    {:type ::no-resolver})))
  (when (nil? as-of)
    (throw (ex-info (str "chain.authorized: :as-of is required. A Lamport "
                         "clock cannot say whether a grant had expired.")
                    {:type ::no-as-of})))
  (when-not (contains? revocation-policies revocation)
    (throw (ex-info (str "chain.authorized: declare :revocation as "
                         ":retroactive or :prospective — there is no right "
                         "answer, which is why there is no default")
                    {:type ::no-revocation-policy :declared revocation
                     :allowed revocation-policies})))
  (let [causal (chain/verify-causal get-fn cid max-visits)
        base {:as-of as-of :revocation revocation :causal causal}]
    (if-not (:ok? causal)
      (assoc base :ok? false :visited (:visited causal 0) :findings []
             :reason (:reason causal))
      (let [{:keys [commits order]} (chain/walk-causal get-fn cid max-visits)
            findings
            (vec (keep (fn [c]
                         (let [{:keys [actor authority]} (get commits c)]
                           (cond
                             (nil? authority)
                             {:cid c :actor actor :authority nil
                              :reason :no-authority}

                             (nil? actor)
                             {:cid c :actor nil :authority authority
                              :reason :no-actor}

                             :else
                             (if-let [grant (resolve-grant authority)]
                               (when-let [why (grant-verdict grant actor as-of
                                                             revocation)]
                                 {:cid c :actor actor :authority authority
                                  :reason why})
                               {:cid c :actor actor :authority authority
                                :reason :unknown-authority}))))
                       order))]
        (if (empty? order)
          (assoc base :ok? false :visited 0 :findings [] :reason :empty)
          (assoc base :ok? (empty? findings) :visited (count order)
                 :findings findings))))))

(defn authorized?
  "`verify-authorized`, as a boolean, for a caller that has already decided
  what it will do about a failure.

  Deliberately not the primary form: a boolean cannot distinguish *this
  history is permitted* from *this history could not be checked*, and those
  need different responses."
  [get-fn cid opts]
  (:ok? (verify-authorized get-fn cid opts)))
