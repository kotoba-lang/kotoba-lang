(ns biscuit.authorizer
  "Run the token's checks and the verifier's policies, in that order.

  A biscuit says two different things and conflating them is the classic
  error:

  - **checks** live in the token and must all pass. They are what the holder
    (and everyone who attenuated after) restricted themselves to.
  - **policies** live in the *authorizer* — the verifier's own rules — and
    the first one that matches decides. They are what the service allows.

  So a token cannot allow itself: **there is no `allow` inside a token.** A
  token with no checks is maximally permissive *as a token*, and still gets
  whatever the authorizer's policies grant, which may be nothing.

  ## Deny is the default and it is not a policy

  If no policy matches, the answer is `:no-policy-matched`, not `:deny`.
  Both refuse, and distinguishing them is what tells an operator that their
  policy set has a hole rather than that their rule fired."
  (:require [biscuit.datalog :as d]
            [biscuit.expression :as x]
            [biscuit.token :as token]))

(defn- check-verdict
  "-> `:pass`, `:fail`, or `{:refused reason}` for ONE check.

  A check of kind One passes when at least one derivation of its body also
  satisfies every expression. So a binding whose expressions this repo cannot
  evaluate does not sink the check: another binding may satisfy it cleanly, and
  refusing then would be stricter than the spec. But if NO binding passes and
  one refused, the answer is refused rather than failed -- the token may well
  be satisfiable and this implementation could not tell."
  [facts {:keys [body expressions]}]
  (let [bindings (d/query facts body)]
    (if (empty? bindings)
      :fail
      (loop [[b & more] (seq bindings) refusal nil]
        (if (nil? b)
          (or refusal :fail)
          (let [r (x/holds? (or expressions []) b)]
            (cond
              (:refused r) (recur more (or refusal r))
              (:ok? r) :pass
              :else (recur more refusal))))))))

(defn- run-block-checks
  [t index authorizer-facts budget]
  (let [{:keys [facts rules]} (token/scope t index)
        sat (d/saturate (concat facts authorizer-facts) rules budget)]
    (if (:refused sat)
      {:refused (:refused sat) :index index :detail (dissoc sat :refused)}
      (let [checks (get-in t [:biscuit/blocks index :block/checks])
            verdicts (mapv (fn [c] [c (check-verdict (:facts sat) c)]) checks)
            refused (first (keep (fn [[_ v]] (when (map? v) v)) verdicts))
            failed (into [] (keep (fn [[c v]] (when (= :fail v) c)) verdicts))]
        (cond
          refused {:refused (:refused refused) :index index :detail (:detail refused)}
          :else {:failed failed :index index})))))

(defn- undecoded-checks
  "Blocks that say they carry checks but did not hand any over.

  `biscuit.wire` withholds `:block/checks` entirely when any check in a block
  could not be decoded, so this is the one place that difference has to be
  noticed. Reading zero checks off such a block would satisfy every one of them
  vacuously -- a token becoming MORE permissive because it was harder to read."
  [t]
  (into []
        (keep-indexed
         (fn [i b]
           (when (and (pos? (or (:block/check-count b) 0))
                      (nil? (:block/checks b)))
             {:index i :refused (:block/checks-refused b)})))
        (:biscuit/blocks t)))

(defn run-checks
  "Run the checks of a token whose signature the CALLER has already verified.

  ## Why this exists, and why it takes the verification as a value

  `authorize` verifies and then checks, which is right for a hand-written
  token. A **wire** token cannot go through it: `biscuit.token/verify`
  recomputes the model payload, while a wire token's signature is over
  protobuf bytes, so the two disagree and the failure looks exactly like a
  forgery. The wire path verifies with `biscuit.wire/verify` instead -- and
  then had nowhere to run the checks it just decoded.

  Without this, a consumer wanting to honour those checks reaches for
  `biscuit.datalog` directly and a **second check evaluator** starts living
  outside this library, where the rules about vacuous satisfaction and block
  scope have to be remembered again.

  `verification` is the map `wire/verify` (or `token/verify`) returned. Passing
  it is not ceremony: a signature check can be forgotten, and a function that
  takes the result as an argument cannot be called as if one had happened. It
  can be forged by a caller who is trying to; it cannot be omitted by a caller
  who is not thinking about it.

  -> `{:allowed? true :blocks n}` or `{:allowed? false :reason kw …}`.

  `:checks-not-decoded` is its own refusal, and separate from a check that
  FAILED. One says the token asked for something this implementation cannot
  evaluate; the other says the token asked and the answer was no.

  Only `:facts` is taken, not `:rules`. Checks reason with the token's own
  rules under `biscuit.token/scope`; letting a verifier inject rules here would
  let the authorizer help a check pass, which is backwards -- checks are what
  the holder restricted ITSELF to. Accepting a `:rules` key and ignoring it
  would be worse than not accepting one."
  [t verification {:keys [facts budget] :or {budget d/default-budget}}]
  (let [blocks (:biscuit/blocks t)]
    (cond
      (not (:ok? verification))
      {:allowed? false :reason (or (:reason verification) :not-verified)
       :verified? false}

      (empty? blocks)
      {:allowed? false :reason :no-blocks :verified? true}

      :else
      (let [missing (undecoded-checks t)]
        (if (seq missing)
          {:allowed? false :reason :checks-not-decoded :verified? true
           :detail missing}
          (let [results (mapv #(run-block-checks t % (vec facts) budget)
                              (range (count blocks)))
                refused (first (filter :refused results))
                failed (mapcat :failed results)]
            (cond
              refused {:allowed? false :reason (:refused refused) :verified? true
                       :index (:index refused)}
              (seq failed) {:allowed? false :reason :check-failed :verified? true
                            :failed (vec failed)}
              :else {:allowed? true :blocks (count blocks)})))))))

(defn authorize
  "`{:allowed? bool :reason kw …}`.

  `authorizer` is `{:facts [...] :rules [...] :policies [{:kind :allow|:deny
  :body [...]} ...]}` — the verifier's own knowledge, which is never in the
  token. `:policy-token-facts? false` keeps token facts and rules out of policy
  saturation while still evaluating every token check in its scoped block.
  This is required when an adapter projects verified token authority into a
  narrower provenance such as `grant:`: a token must not impersonate trusted
  compiler, local-policy, or runtime predicates. The default is true for the
  ordinary Biscuit authorizer surface.

  Order is fixed and load-bearing: **verify, then checks, then policies.**
  Running policies first would let a token whose signature does not verify
  reach a rule that says `allow`."
  [t {:keys [root-public-key verify-fn facts rules policies budget
             policy-token-facts?]
      :or {budget d/default-budget policy-token-facts? true}}]
  (let [v (token/verify t root-public-key verify-fn)]
    (if-not (:ok? v)
      {:allowed? false :reason (:reason v) :verified? false}
      (let [n (count (:biscuit/blocks t))
            results (mapv #(run-block-checks t % facts budget) (range n))
            refused (first (filter :refused results))
            failed (mapcat :failed results)]
        (cond
          refused
          {:allowed? false :reason (:refused refused) :verified? true
           :block (:index refused)}

          (seq failed)
          {:allowed? false :reason :check-failed :verified? true
           :failed (vec failed)}

          :else
          ;; Policies see the authorizer's facts plus the WHOLE token's facts:
          ;; the service is deciding about the token as presented, and it is
          ;; the party that wrote these rules.
          (let [all (into (vec facts)
                          (when policy-token-facts?
                            (mapcat :block/facts (:biscuit/blocks t))))
                all-rules (into (vec rules)
                                (when policy-token-facts?
                                  (mapcat :block/rules (:biscuit/blocks t))))
                sat (d/saturate all all-rules budget)]
            (if (:refused sat)
              {:allowed? false :reason (:refused sat) :verified? true}
              (if-let [hit (first (filter #(d/satisfied? (:facts sat) (:body %)) policies))]
                {:allowed? (= :allow (:kind hit)) :verified? true
                 :reason (if (= :allow (:kind hit)) :allowed-by-policy :denied-by-policy)
                 :policy hit}
                {:allowed? false :verified? true :reason :no-policy-matched}))))))))
