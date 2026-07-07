(ns kotoba.lang.capability-values
  "First-class capability values for safe Kotoba (ADR-safe-capability-language,
  S4b). A capability value authorizes a specific action on a constrained
  resource set; a resource string is never authority by itself. The semantic
  contract is docs/lang/capability-values.md; conformance fixtures live under
  lang/capability-conformance/."
  (:require [clojure.set :as set]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Capability value shape

(def effect-for-kind
  "Capability kind -> effect keyword that must appear in the effect row of any
  function accepting a capability value of that kind. Extensible: new kinds
  (e.g. :egress, :secret-read) are added here to become supported."
  {:graph-read :graph-read
   :graph-write :graph-write
   :infer :infer
   ;; Host provider surfaces (issue #263): capability kinds for host-call
   ;; dispatch through kotoba.lang.capability-host/guard-call. Each host kind
   ;; is its own effect keyword, so a function accepting one must declare it.
   :host/clipboard-read :host/clipboard-read
   :host/clipboard-write :host/clipboard-write
   :host/http :host/http
   :host/fs-read :host/fs-read
   :host/fs-write :host/fs-write
   :host/keychain-read :host/keychain-read
   :host/keychain-write :host/keychain-write
   :host/notify :host/notify
   :host/ledger-append :host/ledger-append
   ;; kgraph-* (formerly kqe-*) EAVT graph-store host imports (kotoba.kgraph).
   :host/graph-assert :host/graph-assert
   :host/graph-retract :host/graph-retract
   :host/graph-get-objects :host/graph-get-objects
   :host/graph-query :host/graph-query
   ;; aiueos default kernel capabilities (aiueos.policy/default-kernel-caps,
   ;; ADR-2607022700) -- registered in kotoba.runtime/op->kind (kotoba-lang/
   ;; kotoba) since that ADR landed, but never added HERE. guard-call's
   ;; unsupported-kind check (this namespace) means every one of these 9
   ;; ops was denied at RUN time with :kotoba.host/denied :unsupported-kind
   ;; the moment a real caller supplied a runtime policy and actually
   ;; executed the guest -- kotoba's own `wasm emit`/`wasm run` test suite
   ;; only ever exercised the static compile-time capability gate (which
   ;; doesn't consult effect-for-kind at all) against these ops, so this
   ;; went undetected until kotoba.wasm-exec's real (non-stub) provider
   ;; implementations tried to actually guard-call them for the first time.
   :host/log-write :host/log-write
   :host/clock-monotonic :host/clock-monotonic
   :host/random-bytes :host/random-bytes
   :host/topic-publish :host/topic-publish
   :host/topic-subscribe :host/topic-subscribe
   :host/pci-config :host/pci-config
   :host/dma-map :host/dma-map
   :host/irq-subscribe :host/irq-subscribe
   :host/mmio-map :host/mmio-map
   ;; kotoba-lang/kototama's actor:host ABI (kototama.contract/
   ;; kototama.tender, ADR-2607062330/2607062400) mirrored into kotoba's own
   ;; real host-provider surface (kotoba.wasm-exec/real-op-effects) -- same
   ;; gap as the aiueos kinds above, caught at the same time.
   :host/identity-keypair :host/identity-keypair
   :host/identity-sign :host/identity-sign
   :host/identity-verify :host/identity-verify
   :host/hash-sha256 :host/hash-sha256
   :host/http-post :host/http-post
   :host/log-read :host/log-read})

(defn non-empty-string?
  [x]
  (and (string? x) (not (str/blank? x))))

(defn date-string?
  [x]
  (and (string? x) (some? (re-matches #"\d{4}-\d{2}-\d{2}" x))))

(defn resource-constraint?
  "A resource constraint is :any (the universe), a single cid string, or a
  non-empty set of cid strings (graph/model set)."
  [x]
  (or (= :any x)
      (non-empty-string? x)
      (and (set? x) (boolean (seq x)) (every? non-empty-string? x))))

(defn validate-cap
  "Shape check for a capability value. Returns {:ok? bool :problems [...]}."
  [cap]
  (let [problems
        (if-not (map? cap)
          [{:problem :cap/not-a-map :value cap}]
          (cond-> []
            (not (keyword? (:cap/kind cap)))
            (conj {:problem :cap/kind-invalid :value (:cap/kind cap)})

            (not (resource-constraint? (:cap/resource cap)))
            (conj {:problem :cap/resource-invalid :value (:cap/resource cap)})

            (and (contains? cap :cap/holder)
                 (not (non-empty-string? (:cap/holder cap))))
            (conj {:problem :cap/holder-invalid :value (:cap/holder cap)})

            (and (some? (:cap/expires cap))
                 (not (date-string? (:cap/expires cap))))
            (conj {:problem :cap/expires-invalid :value (:cap/expires cap)})

            (not (and (vector? (:cap/provenance cap))
                      (every? non-empty-string? (:cap/provenance cap))))
            (conj {:problem :cap/provenance-invalid
                   :value (:cap/provenance cap)})))]
    {:ok? (empty? problems) :problems problems}))

(defn capability?
  "True when X is a well-formed capability value. A plain resource string is
  never a capability."
  [x]
  (true? (:ok? (validate-cap x))))

(defn make-cap
  "Generic capability constructor. OPTS: {:holder :expires :provenance}."
  ([kind resource] (make-cap kind resource nil))
  ([kind resource {:keys [holder expires provenance]}]
   (cond-> {:cap/kind kind
            :cap/resource resource
            :cap/expires expires
            :cap/provenance (vec (or provenance []))}
     (some? holder) (assoc :cap/holder holder))))

(defn graph-read-cap
  ([resource] (make-cap :graph-read resource))
  ([resource opts] (make-cap :graph-read resource opts)))

(defn graph-write-cap
  ([resource] (make-cap :graph-write resource))
  ([resource opts] (make-cap :graph-write resource opts)))

(defn infer-cap
  ([resource] (make-cap :infer resource))
  ([resource opts] (make-cap :infer resource opts)))

;; ---------------------------------------------------------------------------
;; Effect-row consistency

(defn effects-consistent?
  "Checks that a function's declared effect row (a set of effect keywords)
  covers every effect implied by its capability parameters CAPS. Unknown
  capability kinds fail closed: they require their own kind keyword in the
  row rather than being silently treated as pure data.
  Returns {:ok? bool :missing #{...}}."
  [effect-row caps]
  (let [row (set effect-row)
        required (into #{}
                       (map (fn [c]
                              (get effect-for-kind (:cap/kind c) (:cap/kind c))))
                       caps)
        missing (set/difference required row)]
    {:ok? (empty? missing) :missing missing}))

;; ---------------------------------------------------------------------------
;; CACAO grant / local policy intersection

(defn denied?
  [x]
  (and (map? x) (contains? x :denied)))

(defn- ->scope
  "Normalizes a resource constraint to :any or a set of cid strings. A set
  containing :any collapses to :any (grant resources allow #{cid-or-:any})."
  [x]
  (cond
    (= :any x) :any
    (non-empty-string? x) #{x}
    (set? x) (if (contains? x :any)
               :any
               (into #{} (filter non-empty-string?) x))
    :else #{}))

(defn- scope-intersection
  [a b]
  (cond
    (= :any a) b
    (= :any b) a
    :else (set/intersection a b)))

(defn- scope-overlaps?
  [a b]
  (cond
    (= :any a) (or (= :any b) (boolean (seq b)))
    (= :any b) (boolean (seq a))
    :else (boolean (seq (set/intersection a b)))))

(defn- scope->resource
  [scope]
  (cond
    (= :any scope) :any
    (= 1 (count scope)) (first scope)
    :else scope))

(defn- expired-at?
  [expires now]
  (and (some? expires) (some? now) (neg? (compare expires now))))

(defn- earliest-expiry
  [dates]
  (->> dates (remove nil?) sort first))

(defn intersect-grants
  "Host-call time intersection. Takes
  {:requested <cap> :cacao-grants [<grant>] :local-policy <policy> :now date}
  where a grant is {:grant/kind kw :grant/resources #{cid-or-:any}
  :grant/expires date-or-nil :grant/id str} and local-policy is
  {:policy/allow {<kind kw> #{cid-or-:any}}}.

  Returns the CONCRETE capability actually authorized: resource scope is
  requested INTERSECT grants INTERSECT policy (:any acts as the universe),
  expiry is the earliest non-nil expiry among the requested capability and
  the contributing grants, provenance is the contributing grant ids. A
  wildcard (:any) result is only possible when requested, grants, and policy
  are all :any. Fails closed with {:denied <reason>} when the requested kind
  is unsupported (:unsupported-kind), every covering grant is expired at :now
  (:expired), or the intersection is empty (:empty-intersection)."
  [{:keys [requested cacao-grants local-policy now]}]
  (let [kind (:cap/kind requested)]
    (cond
      (not (capability? requested))
      {:denied :malformed-requested}

      (not (contains? effect-for-kind kind))
      {:denied :unsupported-kind}

      :else
      (let [requested-scope (->scope (:cap/resource requested))
            policy-scope (->scope (get-in local-policy [:policy/allow kind]))
            covering (filter (fn [g]
                               (and (= kind (:grant/kind g))
                                    (scope-overlaps?
                                     requested-scope
                                     (->scope (:grant/resources g)))))
                             cacao-grants)
            live (remove #(expired-at? (:grant/expires %) now) covering)]
        (cond
          (empty? covering) {:denied :empty-intersection}
          (empty? live) {:denied :expired}
          :else
          (let [grant-scope (reduce (fn [acc g]
                                      (let [s (->scope (:grant/resources g))]
                                        (if (or (= :any acc) (= :any s))
                                          :any
                                          (set/union acc s))))
                                    #{}
                                    live)
                result-scope (-> requested-scope
                                 (scope-intersection grant-scope)
                                 (scope-intersection policy-scope))]
            (if (and (not= :any result-scope) (empty? result-scope))
              {:denied :empty-intersection}
              (let [contributing (filter #(scope-overlaps?
                                           result-scope
                                           (->scope (:grant/resources %)))
                                         live)
                    expiry (earliest-expiry
                            (cons (:cap/expires requested)
                                  (map :grant/expires contributing)))]
                (cond-> {:cap/kind kind
                         :cap/resource (scope->resource result-scope)
                         :cap/expires expiry
                         :cap/provenance (into [] (map :grant/id) contributing)}
                  (some? (:cap/holder requested))
                  (assoc :cap/holder (:cap/holder requested)))))))))))

;; ---------------------------------------------------------------------------
;; Receipts

(defn receipt
  "Builds a runtime receipt for a host call performed under CONCRETE-CAP.
  The embedded capability must be the concrete (post-intersection) capability,
  never the broader requested one."
  [concrete-cap now call]
  {:receipt/cap concrete-cap
   :receipt/at now
   :receipt/call call})

(defn denial-receipt?
  "True when R records a denied host call (guard-call denial receipts carry
  :receipt/denied with the fail-closed reason)."
  [r]
  (and (map? r) (contains? r :receipt/denied)))

(defn validate-receipt
  "Shape check for a receipt. Returns {:ok? bool :problems [...]}.

  Grant/error receipts: the embedded :receipt/cap must itself be a well-formed
  capability value (a denial map or resource string is rejected) — it is the
  concrete, post-intersection capability. Denial receipts (:receipt/denied
  present) carry the *requested* capability (which may itself be malformed —
  that can be the reason for denial), so the cap check is relaxed and the
  denial reason must be a keyword instead. Optional :receipt/outcome must be
  one of :ok / :denied / :error when present."
  [r]
  (let [problems
        (if-not (map? r)
          [{:problem :receipt/not-a-map :value r}]
          (cond-> []
            (and (not (denial-receipt? r))
                 (not (capability? (:receipt/cap r))))
            (conj {:problem :receipt/cap-invalid :value (:receipt/cap r)})

            (and (denial-receipt? r)
                 (not (keyword? (:receipt/denied r))))
            (conj {:problem :receipt/denied-invalid :value (:receipt/denied r)})

            (and (contains? r :receipt/outcome)
                 (not (contains? #{:ok :denied :error} (:receipt/outcome r))))
            (conj {:problem :receipt/outcome-invalid :value (:receipt/outcome r)})

            (not (date-string? (:receipt/at r)))
            (conj {:problem :receipt/at-invalid :value (:receipt/at r)})

            (not (or (symbol? (:receipt/call r)) (keyword? (:receipt/call r))))
            (conj {:problem :receipt/call-invalid :value (:receipt/call r)})))]
    {:ok? (empty? problems) :problems problems}))

;; ---------------------------------------------------------------------------
;; Conformance-case runner (shared by the test suite and the bb gate)

(defn run-case
  "Runs one capability conformance fixture. DATA is the fixture EDN; TC is
  the manifest case entry selecting the checker via :type."
  [tc data]
  (case (:type tc)
    :cap-shape (validate-cap data)
    :effects (effects-consistent? (:effects data) (:caps data))
    :intersection (intersect-grants data)
    :receipt (let [result (intersect-grants (:intersection data))]
               (if (denied? result)
                 result
                 (receipt result (:now (:intersection data)) (:call data))))
    {:denied :unknown-case-type}))

(defn check-case
  "Runs a conformance case and compares the outcome with the expectations
  declared in TC. Returns {:ok? bool :case id :actual ...}."
  [tc data]
  (let [actual (run-case tc data)
        ok?
        (case [(:kind tc) (:type tc)]
          [:accept :cap-shape]
          (true? (:ok? actual))

          [:accept :effects]
          (true? (:ok? actual))

          [:accept :intersection]
          (and (not (denied? actual))
               (or (not (contains? tc :expected-resource))
                   (= (:expected-resource tc) (:cap/resource actual)))
               (or (not (contains? tc :expected-expires))
                   (= (:expected-expires tc) (:cap/expires actual)))
               (or (not (contains? tc :expected-provenance))
                   (= (:expected-provenance tc) (:cap/provenance actual))))

          [:accept :receipt]
          (and (true? (:ok? (validate-receipt actual)))
               (or (not (contains? tc :expected-resource))
                   (= (:expected-resource tc)
                      (get-in actual [:receipt/cap :cap/resource]))))

          [:expect-denied :intersection]
          (= (:denied tc) (:denied actual))

          [:expect-error :cap-shape]
          (and (false? (:ok? actual))
               (or (not (contains? tc :problem-contains))
                   (boolean (some #(= (:problem-contains tc) (:problem %))
                                  (:problems actual)))))

          [:expect-error :effects]
          (and (false? (:ok? actual))
               (or (not (contains? tc :missing))
                   (= (:missing tc) (:missing actual))))

          false)]
    {:ok? ok? :case (:id tc) :actual actual}))
