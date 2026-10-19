(ns kotoba.host-providers
  "Capability-guarded host provider dispatch for the CLJ runtime slice
  (issue #263).

  Every host-import op from the kotoba core capability contract
  (clipboard_read, http_fetch, fs_read, ...) maps to a
  kotoba.lang.capability-values capability kind (:host/clipboard-read,
  :host/http, :host/fs-read, ...). At run time the launcher builds
  CACAO-style grants and a local policy from its existing policy EDN and
  dispatches each provider invocation through
  kotoba.lang.capability-host/guard-call, so:

  - a denied call NEVER reaches the provider handler (fail closed), and
  - every call — grant, denial, or handler error — leaves a receipt in the
    run's audit journal (surfaced as :kotoba.host/receipts in the launcher
    result).

  Legacy behavior is preserved: a `run` without `--policy` installs no guard
  and host-import ops remain statically rejected as :capability-not-granted,
  exactly as before. Enforcement happens only when a capability policy is
  supplied.

  Policy EDN vocabulary (superset of the existing wasm/provider policy):

  {:kotoba.policy/capabilities #{:clipboard/text :http/fetch ...}
   ;; optional per-capability resource scope (default :any)
   :kotoba.policy/capability-resources {:clipboard/text #{\"clipboard:system\"}}
   ;; optional per-capability grant expiry, enforced at call time
   :kotoba.policy/capability-expires {:clipboard/text \"2027-01-01\"}}

  The default handlers are deterministic Rust-free stubs (the interpreter
  slice has no real memory ABI); concrete native providers (pbcopy/pbpaste,
  an HTTP client, ...) plug in by passing a :handlers map to `host-call`."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [kotoba.cap-table :as cap-table]
            [kotoba.core.contracts :as core-contracts]
            [kotoba.kgraph :as kgraph]
            [kotoba.lang.capability-host :as capability-host]
            [kotoba.lang.capability-values :as capability-values]
            [kotoba.runtime :as runtime]))

(def op->kind
  "Host-import op (capability contract symbol) -> capability kind understood
  by kotoba.lang.capability-values/effect-for-kind. Owned by kotoba.runtime
  (the static capability gate needs it too); aliased here for provider code."
  runtime/op->kind)

(defn op-capability
  "Contract capability name (e.g. \"clipboard/text\") for a host-import op."
  [op]
  (get-in runtime/host-imports [op :capability]))

(defn- named-lookup
  "Look up CAP-NAME in a policy map keyed by capability keyword or string."
  [m cap-name]
  (some (fn [[k v]]
          (when (= cap-name (core-contracts/capability-name k))
            v))
        m))

(def network-cap-names
  "Contract capability names that are network egress. Safe default: a
  missing `:kotoba.policy/capability-resources` entry fails closed
  (empty set) unless the policy explicitly sets
  `:kotoba.policy/http-require-allowlist false` (legacy opt-out)."
  #{"http/fetch" "http/post"})

(defn http-require-allowlist?
  "True when POLICY requires network resource allowlists (safe default).
  Only an explicit `false` opts out."
  [policy]
  (not (false? (:kotoba.policy/http-require-allowlist policy))))

(defn normalize-policy
  "Apply safe-runtime defaults to a loaded policy map. Currently stamps
  `:kotoba.policy/http-require-allowlist true` when the key is absent so
  operators see the effective policy in receipts/debug."
  [policy]
  (cond
    (nil? policy) nil
    (contains? policy :kotoba.policy/http-require-allowlist) policy
    :else (assoc policy :kotoba.policy/http-require-allowlist true)))

(defn- resource-scope
  "Grant resource set for CAP-NAME under POLICY. Network caps default to
  empty (deny all URLs) unless the operator opts out with
  `:kotoba.policy/http-require-allowlist false` or supplies an explicit
  `:kotoba.policy/capability-resources` allowlist."
  [policy cap-name]
  (let [resources (named-lookup (:kotoba.policy/capability-resources policy)
                                cap-name)]
    (cond
      (nil? resources)
      (if (and (contains? network-cap-names cap-name)
               (http-require-allowlist? policy))
        #{}
        #{:any})

      (= :any resources) #{:any}
      :else (set resources))))

(defn- grant-expiry
  [policy cap-name]
  (named-lookup (:kotoba.policy/capability-expires policy) cap-name))

(defn- granted-kinds
  "Seq of [kind cap-name] for every guarded kind whose contract capability
  appears in the policy's :kotoba.policy/capabilities."
  [policy]
  (let [caps (core-contracts/policy-capabilities policy)]
    (distinct
     (keep (fn [[op kind]]
             (let [cap-name (op-capability op)]
               (when (and cap-name (contains? caps cap-name))
                 [kind cap-name])))
           op->kind))))

(defn policy-grants
  "CACAO-style grants derived from the launcher policy EDN: one grant per
  guarded host kind enabled by :kotoba.policy/capabilities, scoped by
  :kotoba.policy/capability-resources and expiring per
  :kotoba.policy/capability-expires."
  [policy]
  (vec (for [[kind cap-name] (granted-kinds policy)]
         {:grant/kind kind
          :grant/resources (resource-scope policy cap-name)
          :grant/expires (grant-expiry policy cap-name)
          :grant/id (str "policy:" cap-name)})))

(defn local-policy
  "kotoba.lang.capability-values local policy derived from the launcher
  policy EDN. Propagates :kotoba.policy/forbid-wildcard (S4b least-privilege)."
  [policy]
  (cond-> {:policy/allow
           (into {} (for [[kind cap-name] (granted-kinds policy)]
                      [kind (resource-scope policy cap-name)]))}
    (true? (get policy :kotoba.policy/forbid-wildcard))
    (assoc :policy/forbid-wildcard true)))

(defn grants->policy
  "Launcher policy EDN synthesized from externally supplied (CACAO chain)
  grants — used when `run --cacao` is given WITHOUT `--policy`. The static
  capability gate then admits exactly the contract capabilities whose host
  kinds the chain grants, and the derived local policy allows :any for those
  kinds (no `:kotoba.policy/capability-resources` narrowing): the local
  policy remains the narrowing side, and absent an explicit `--policy` it
  defaults to allowing whatever the chain grants. The grants themselves stay
  the authorization side of the intersection."
  [grants]
  (let [kinds (into #{} (map :grant/kind) grants)]
    {:kotoba.policy/capabilities
     (into #{} (keep (fn [[op kind]]
                       (when (contains? kinds kind)
                         (op-capability op))))
           op->kind)}))

(defn- canonical-path
  "Best-effort canonicalized (symlink/`..`-resolved) path string for S, used
  ONLY for capability resource-scope comparison below -- the actual fs-read/
  fs-write I/O always runs against the guest's ORIGINAL path string via
  `io/file`, exactly like a native fs call would resolve it against the JVM
  process's real cwd. Falls back to the raw string when canonicalization
  itself throws (e.g. `fs-write`'s target parent directory doesn't exist
  yet)."
  [^String s]
  (try (.getCanonicalPath (io/file s)) (catch Exception _ s)))

(defn- fs-path-permitted?
  "True when PATH (the guest-supplied literal fs-read/fs-write path
  argument) is inside CONCRETE's :cap/resource scope.
  kotoba.lang.capability-values/intersect-grants (run by guard-call, inside
  `host-call` below) only ever requests the UNIVERSAL `:any` capability for
  a host KIND -- the guest's actual path argument isn't known until AFTER
  the guard decision runs, so a policy that scopes `:fs/app-data` to
  specific paths (`:kotoba.policy/capability-resources`) would otherwise be
  silently ignored once the capability kind is granted at all. This extra,
  per-call check is what actually enforces that narrowing; it mirrors
  kotoba.wasm-exec's `resource-permitted?` (identical policy vocabulary:
  `:any`, a bare resource string, or a set of them) -- duplicated rather
  than shared because kotoba.wasm-exec already requires this namespace, so
  the reverse require would be circular. Comparison is on the canonicalized
  path so a granted resource string can't be trivially defeated by a
  `./`-prefixed or symlinked spelling of the same file."
  [concrete path]
  (let [scope (:cap/resource concrete)]
    (boolean
     (or (nil? concrete)
         (= :any scope)
         (let [target (canonical-path path)]
           (cond
             (string? scope) (= (canonical-path scope) target)
             (set? scope) (some #(= (canonical-path %) target) scope)
             :else false))))))

(defn- fs-check-permitted!
  "Throws (fail closed) when PATH is outside CONCRETE's granted resource
  scope. Called from inside an already-GRANTED fs-read/fs-write handler
  (guard-call's own kind-level grant check already ran and passed) -- this
  is the finer per-path check `fs-path-permitted?` documents. Thrown from
  inside the :handler fn passed to guard-call, so `capability-host/guard-call`
  itself records the normal :error receipt and rethrows -- no separate
  receipt shape to invent here."
  [op concrete path]
  (when-not (fs-path-permitted? concrete path)
    (throw (ex-info (str op ": path outside granted capability resource scope")
                     {:kotoba.host/denied :resource-not-permitted
                      :kotoba.host/call op
                      :kotoba.host/path path}))))

(def default-handlers
  "Deterministic Rust-free provider stubs, keyed by host-import op, EXCEPT
  fs-read/fs-write and clock-monotonic below, which are real (issue #263 v0.1
  slice, ADR-2607182430 in the com-junkawasaki/root superproject): fs-read
  really reads a file's bytes off disk (as a UTF-8 string) and fs-write
  really writes one, both gated a second time by `fs-check-permitted!`
  above; clock-monotonic is a real System/nanoTime read (no filesystem/
  network surface to gate beyond the capability KIND itself). Each handler
  is (fn [concrete-cap args] result); args here are plain literal Kotoba
  values (a path string, file content), NOT the (ptr,len) WASM-ABI shape --
  see `kgraph-handlers`' docstring for why the CLJ interpreter slice uses
  this convention (it has no linear memory to marshal a ptr/len pair
  through). host-i64-roundtrip echoes its argument (matching the
  interpreter builtin); the remaining pointer-ABI providers return 0 (the
  success status of the provider result ABI) since the interpreter has no
  real memory to read a ptr/len pair out of — `str-ptr` always evaluates to
  0 here (kotoba.runtime/builtin-fns). Real native providers for those (or,
  for kgraph-*, `kgraph-handlers` below / kotoba.wasm-exec's Chicory host
  functions for genuine WASM execution, which ALREADY has real fs-read/
  fs-write/http-fetch/clipboard/keychain/etc. behind a sandboxed fs-root —
  see kotoba.wasm-exec/real-op-effects, a separate execution path from this
  interpreter slice) replace these via the :handlers option of `host-call`."
  {'notify-show (fn [_cap _args] 0)
   'clipboard-read (fn [_cap _args] 0)
   'clipboard-write (fn [_cap _args] 0)
   'clipboard-write-str (fn [_cap _args] 0)
   'http-fetch (fn [_cap _args] 0)
   'keychain-read (fn [_cap _args] 0)
   'keychain-write (fn [_cap _args] 0)
   'fs-read (fn [concrete args]
              (let [path (first args)]
                (fs-check-permitted! 'fs-read concrete path)
                (let [f (io/file path)]
                  (when (.isFile f)
                    (slurp f)))))
   'fs-write (fn [concrete args]
               (let [[path content] args]
                 (fs-check-permitted! 'fs-write concrete path)
                 (let [f (io/file path)]
                   (when-let [parent (.getParentFile f)]
                     (.mkdirs parent))
                   (spit f (str content))
                   (count (str content)))))
   'host-i64-roundtrip (fn [_cap args] (first args))
   'kgraph-assert! (fn [_cap _args] 0)
   'kgraph-retract! (fn [_cap _args] 0)
   'kgraph-get-objects (fn [_cap _args] 0)
   'kgraph-query (fn [_cap _args] 0)
   ;; aiueos default kernel capabilities (ADR-2607022700) -- deterministic
   ;; stubs like every other provider here; a native aiueos kototama adapter
   ;; (wasmtime hosting, real MMIO/DMA/IRQ) plugs in via :handlers, never by
   ;; editing these defaults. i64-result ops return 0 (a null/no-op
   ;; sentinel, same convention as the ptr/len ABI's 0-status stubs above).
   'log-write (fn [_cap _args] 0)
   'clock-monotonic (fn [_cap _args] (System/nanoTime))
   'random-bytes (fn [_cap _args] 0)
   'topic-publish (fn [_cap _args] 0)
   'topic-poll (fn [_cap _args] 0)
   'topic-take (fn [_cap _args] 0)
   'topic-count (fn [_cap _args] 0)
   'pci-config (fn [_cap _args] 0)
   'dma-map (fn [_cap _args] 0)
   'irq-subscribe (fn [_cap _args] 0)
   'mmio-map (fn [_cap _args] 0)})

(defn kgraph-handlers
  "Real (non-stub) interpreter-mode kgraph-* handlers backed by STORE (an
  atom of `kotoba.kgraph` datoms; a fresh one per call when omitted). Unlike
  the memory-ABI-oriented default stubs, these read their FIRST argument as a
  literal EDN string directly (the interpreter has no linear memory to marshal
  a ptr/len pair through, so callers pass the request/query EDN as a plain
  string literal instead of `(str-ptr ...)`). Pass as `:handlers` to
  `host-call`/`guarded-run-result` to exercise the real store from `run`
  without going through kotoba.wasm-exec's WASM/Chicory path."
  ([] (kgraph-handlers (atom [])))
  ([store]
   (merge default-handlers
          {'kgraph-assert! (fn [_cap [datom-edn]]
                             (swap! store kgraph/assert-datom (edn/read-string datom-edn))
                             0)
           'kgraph-retract! (fn [_cap [datom-edn]]
                              (swap! store kgraph/retract-datom (edn/read-string datom-edn))
                              0)
           'kgraph-get-objects (fn [_cap [entity-edn]]
                                 (pr-str (kgraph/get-objects @store (edn/read-string entity-edn))))
           'kgraph-query (fn [_cap [query-edn]]
                           (pr-str (kgraph/query @store (edn/read-string query-edn))))})))

(defn journal
  "Append-only receipt recorder ({:record! fn :entries fn}) for a guarded run."
  []
  (capability-host/journal))

(defn capability-query-fn
  "Interpreter binding for `has-capability?`: a policy lookup, not a host
  effect, so it is not receipted."
  [policy]
  (let [caps (core-contracts/policy-capabilities policy)]
    (fn [cap]
      (contains? caps (core-contracts/capability-name cap)))))

(defn host-call
  "Build the guarded host-call fn handed to kotoba.runtime/run:
  (fn [op args] result). Every invocation goes through
  kotoba.lang.capability-host/guard-call with grants/policy derived from the
  launcher policy EDN; receipts flow to :record! (see `journal`). A denial
  throws ex-info carrying :kotoba.host/denied, :kotoba.host/call, and the
  denial :kotoba.host/receipt — the provider handler is never invoked.

  When OPTS carries :cacao-grants (verified CACAO delegation-chain grants,
  see kotoba.lang.capability-cacao), those REPLACE the policy-derived grants
  in the intersection; the local policy side still derives from POLICY, so an
  explicit policy narrows the chain's resource set."
  ([policy] (host-call policy nil))
  ([policy {:keys [record! now handlers cacao-grants]}]
   (let [grants (or cacao-grants (policy-grants policy))
         allow (local-policy policy)
         now (or now (str (java.time.LocalDate/now)))
         handlers (or handlers default-handlers)]
     (fn guarded-host-call [op args]
       (let [kind (get op->kind op)
             handler (get handlers op)]
         (when-not (and kind handler)
           (throw (ex-info "host op has no capability guard"
                           {:kotoba.host/op op})))
         (let [outcome (capability-host/guard-call
                        {:call (keyword "kotoba.host" (str op))
                         :requested (capability-values/make-cap kind :any)
                         :cacao-grants grants
                         :local-policy allow
                         :now now
                         :record! record!
                         :handler (fn [concrete] (handler concrete (vec args)))})]
           (if (:kotoba.host/ok? outcome)
             (:kotoba.host/result outcome)
             (throw (ex-info "host call denied by capability guard"
                             {:kotoba.host/denied (:kotoba.host/denied outcome)
                              :kotoba.host/call op
                              :kotoba.host/receipt (:kotoba.host/receipt outcome)})))))))))

;; ---------------------------------------------------------------------------
;; Capability-passing (S4b): cap-acquire + <op>-with use variants

(defn- use-receipt
  [concrete now call outcome handle extra]
  (merge (assoc (capability-values/receipt concrete now call)
                :receipt/outcome outcome
                :receipt/cap-handle handle)
         extra))

(defn host-call-with
  "Build the capability-passing host-call fn: (fn [base-op handle args] result).

  HANDLE must have been issued by kotoba.cap-table/acquire! on TABLE. The
  stored capability IS the intersected one, so no re-intersection happens at
  use time; expiry is re-checked against :now, and the capability kind must
  match the op (kotoba.cap-table/resolve-use). Provider handlers are looked
  up by the BASE op — a `<op>-with` call reaches the same provider as `<op>`,
  only the authorization path differs. Every use — grant, denial (unknown
  handle, kind mismatch, expiry), or handler error — leaves a receipt
  carrying :receipt/cap-handle; :receipt/call is the `<op>-with` surface.
  A denial throws ex-info with :kotoba.host/denied (fail closed, the provider
  handler is never invoked)."
  [table {:keys [record! now handlers]}]
  (let [now (or now (str (java.time.LocalDate/now)))
        handlers (or handlers default-handlers)]
    (fn guarded-host-call-with [op handle args]
      (let [kind (get op->kind op)
            handler (get handlers op)
            with-op (get runtime/op->with-op op)]
        (when-not (and kind handler with-op)
          (throw (ex-info "host op has no capability guard"
                          {:kotoba.host/op op})))
        (let [call (keyword "kotoba.host" (str with-op))
              ;; consume-use!: handle is affine at runtime (S2 defense-in-depth).
              resolved (cap-table/consume-use! table handle kind now)]
          (if-not (:ok? resolved)
            (let [receipt (use-receipt (cap-table/resolve-cap table handle)
                                       now call :denied handle
                                       {:receipt/denied (:denied resolved)})]
              (when record! (record! receipt))
              (throw (ex-info "capability handle rejected at host-call time"
                              {:kotoba.host/denied (:denied resolved)
                               :kotoba.host/call with-op
                               :kotoba.host/receipt receipt})))
            (let [concrete (:cap resolved)
                  invoked (try
                            {:value (handler concrete (vec args))}
                            (catch Exception e
                              {:error e}))]
              (if (contains? invoked :error)
                (let [e (:error invoked)
                      receipt (use-receipt concrete now call :error handle
                                           {:receipt/error (or (ex-message e) (str e))})]
                  (when record! (record! receipt))
                  (throw e))
                (let [receipt (use-receipt concrete now call :ok handle nil)]
                  (when record! (record! receipt))
                  (:value invoked))))))))))

(defn capability-passing-fns
  "Interpreter bindings for the S4b capability-passing surface: 'cap-acquire
  plus every '<op>-with use variant (kotoba.runtime/op->with-op). Handles are
  per-run, issued and resolved against TABLE (kotoba.cap-table/make-table).

  (cap-acquire <kind-kw> <resource>) intersects policy ∩ grants ∩ requested
  ONCE and returns the handle; a denial at acquisition throws the same
  ex-info shape as a denied host call (:kotoba.host/denied, so the run fails
  closed with a :host-call-denied problem and :kotoba.runtime/call
  :cap/acquire). (<op>-with <handle> <args...>) resolves the handle through
  `host-call-with` above. As with `host-call`, OPTS :cacao-grants replaces
  the policy-derived grants at acquisition time."
  [table policy {:keys [record! now cacao-grants] :as opts}]
  (let [now (or now (str (java.time.LocalDate/now)))
        opts (assoc opts :now now)
        grants (or cacao-grants (policy-grants policy))
        allow (local-policy policy)
        call-with (host-call-with table opts)
        acquire (fn cap-acquire [kind resource]
                  (let [outcome (cap-table/acquire! table {:kind kind
                                                           :resource resource
                                                           :grants grants
                                                           :policy allow
                                                           :now now
                                                           :record! record!})]
                    (if (:kotoba.host/ok? outcome)
                      (:kotoba.host/result outcome)
                      (throw (ex-info "capability acquisition denied"
                                      {:kotoba.host/denied (:kotoba.host/denied outcome)
                                       :kotoba.host/call :cap/acquire
                                       :kotoba.host/receipt (:kotoba.host/receipt outcome)})))))]
    (into {'cap-acquire acquire}
          (map (fn [[base with-op]]
                 [with-op (fn [handle & args]
                            (call-with base handle (vec args)))]))
          runtime/op->with-op)))
