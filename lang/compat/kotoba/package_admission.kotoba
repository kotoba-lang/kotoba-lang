(ns kotoba.package-admission
  "Safe-execution admission gate for Kotoba package inputs (issue #262 / F-001).

  Validates a `kotoba.lock.edn` (and optionally the package manifest it locks)
  through the `kotoba.lang.package-contract` validation kernel from
  kotoba-lang/kotoba-lang, and layers launcher-owned safe-mode checks on top
  (local-path dependencies are never admitted). Every verification — accept or
  reject — emits a package-verification receipt; the receipt is the
  release-evidence artifact required before a safe build may proceed.

  `manifest-integrity-error` closes a real gap `kotoba.lang.package-contract/
  cid?` alone doesn't (kotoba-lang/kotoba-lang#13 made `cid?` a genuine
  CIDv1 structural check, but structural validity says nothing about
  whether a CID actually matches the content it claims to pin): a
  manifest's own self-declared `:manifest-cid` is recomputed from the
  manifest's actual content (canonical DAG-CBOR + CIDv1, `cbor.core`/
  `multiformats.core`) and compared against the declared value. A manifest
  edited without updating its pinned CID -- or a manifest whose CID was
  copied from a different package entirely -- is rejected here, where the
  previous shape-only check would have silently accepted it as long as the
  string merely looked CID-shaped."
  (:require [cbor.core :as cbor]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [clojure.string :as str]
            [kotoba.compiler.artifact :as artifact]
            [kotoba.lang.package-contract :as package-contract]
            [kotoba.lang.package-registry :as package-registry]
            [multiformats.core :as mf])
  (:import [java.time Instant]))

(defn sha256-bytes [^bytes bytes]
  (let [digest (.digest (java.security.MessageDigest/getInstance "SHA-256") bytes)]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) digest))))

(defn sha256-text [text]
  (sha256-bytes (.getBytes ^String text java.nio.charset.StandardCharsets/UTF_8)))

(def local-path-message
  "local-path dependency not allowed in safe mode")

(def problem-codes
  "Map kotoba.lang.package-contract (and admission-layer) messages to stable
  problem keywords surfaced in receipts."
  {"lock version 1 required" :package/lock-version-unsupported
   "lock deps vector required" :package/lock-deps-required
   "missing required lock field" :package/missing-lock-field
   "cid required" :package/cid-required
   "signer required" :package/signer-required
   "signer not currently trusted" :package/signer-not-trusted
   "capability grant exceeds package declaration" :package/capability-exceeds-declaration
   "unknown package kind" :package/unknown-kind
   "contract surface vector required" :package/contract-surface-invalid
   "contract surface keyword required" :package/contract-surface-invalid
   "missing required package field" :package/missing-manifest-field
   "missing required source field" :package/missing-source-field
   "repo-rid cid required" :package/cid-required
   "tree cid required" :package/cid-required
   "manifest cid required" :package/cid-required
   "capabilities vector required" :package/capabilities-invalid
   "signature required" :package/signature-required
   "signature missing required field" :package/signature-invalid
   "signature did required" :package/signature-invalid
   "signature alg unsupported" :package/signature-alg-unsupported
   "signature bytes required" :package/signature-invalid
   "signature verification failed" :package/signature-verification-failed
   "manifest cid does not match manifest content" :package/manifest-cid-mismatch
   "component cid required" :package/component-cid-required
   "component cid does not match component content" :package/component-cid-mismatch
   "dependency manifest required" :package/dependency-manifest-required
   "unexpected dependency manifest" :package/unexpected-dependency-manifest
   "dependency manifest does not match lock entry" :package/dependency-manifest-mismatch
   "dependency capability grant exceeds signed request" :package/capability-exceeds-manifest
   "dependency manifest closure does not match lock" :package/dependency-closure-mismatch
   "dependency signer is not explicitly trusted" :package/signer-not-trusted
   local-path-message :package/local-path-dependency})

(defn problem-code
  [message]
  (get problem-codes message :package/invalid))

;; ---------------------------------------------------------------------------
;; Admission-layer safe-mode checks (not owned by package-contract)

(defn path-looking?
  "True when a string looks like a filesystem path rather than a git ref,
  package name, or CID."
  [x]
  (and (string? x)
       (or (str/starts-with? x "/")
           (str/starts-with? x "./")
           (str/starts-with? x "../")
           (str/starts-with? x "~")
           (str/starts-with? x "file:")
           (str/includes? x "\\"))))

(def local-root-keys
  [:package/local-root :dep/local-root :dep/local-path :dep/path :dep/root])

(def path-checked-ref-keys
  [:dep/ref :dep/url :dep/source])

(defn local-path-error
  "Safe mode never admits a dependency resolved from a local path: a local
  path has no repo RID / CID provenance, so its content cannot be pinned or
  attested. Returns a package-contract-shaped error map or nil."
  [dep]
  (or (some (fn [k]
              (when (contains? dep k)
                (package-contract/invalid local-path-message
                                          {:field k
                                           :value (get dep k)
                                           :dependency (:dep/name dep)})))
            local-root-keys)
      (some (fn [k]
              (let [value (get dep k)]
                (when (path-looking? value)
                  (package-contract/invalid local-path-message
                                            {:field k
                                             :value value
                                             :dependency (:dep/name dep)}))))
            path-checked-ref-keys)))

;; ---------------------------------------------------------------------------
;; Verification

(def blocked-key-statuses
  "Key-register statuses that must not authorize NEW package artifacts.
  Historical verification of past receipts may still use retired keys
  elsewhere; admission of new locks only trusts :active (and optionally
  :pre-active is also blocked)."
  #{:revoked :expired :compromised :retired :pre-active})

(defn key-register-blocked-signers
  "From a key-register EDN map (`:keys` vector of key records), collect
  signer/key ids whose `:key/status` is not safe for new artifacts.
  Accepts either `:key/id` or `:key/signer` as the identity string."
  [key-register]
  (into #{}
        (keep (fn [k]
                (when (contains? blocked-key-statuses (:key/status k))
                  (or (:key/signer k) (:key/id k)))))
        (or (:keys key-register) [])))

(defn merge-key-register-into-trust
  "Fold KEY-REGISTER blocked signers into TRUST's :revoked-signers so the
  existing package-contract lockfile kernel rejects them as
  'signer not currently trusted' without a second code path."
  [trust key-register]
  (let [blocked (key-register-blocked-signers key-register)
        trust (or trust {})]
    (if (seq blocked)
      (update trust :revoked-signers
              (fn [xs] (into (set (or xs #{})) blocked)))
      trust)))

(defn trust-context
  "Build the trust context handed to the package-contract lockfile validator.

  `trust` is the optional trust EDN (`:declared-capabilities`,
  `:revoked-signers`, `:expired-signers`, `:compromised-signers`, and
  optional `:key-register` map or inline `:keys`). When the trust EDN does
  not pin `:declared-capabilities`, the manifest's declared
  `:kotoba.package/capabilities` are used; with neither, no capability grant
  is admitted (strict default).

  When trust carries `:key-register` (or is itself a key-register with
  `:keys`), blocked statuses are merged into :revoked-signers (R-002)."
  [trust manifest]
  (let [trust (or trust {})
        key-reg (or (:key-register trust)
                    (when (seq (:keys trust))
                      trust))
        trust (if key-reg
                (merge-key-register-into-trust
                 (dissoc trust :key-register :keys)
                 key-reg)
                trust)]
    (assoc trust
           :declared-capabilities
           (vec (or (:declared-capabilities trust)
                    (:kotoba.package/capabilities manifest)
                    [])))))

(defn manifest-without-self-cid
  "MANIFEST with its own self-declared :manifest-cid removed, AND its
  :kotoba.package/signatures removed -- both are content a manifest's CID
  must be computed OVER TOP OF, never included IN. :manifest-cid's exclusion
  is the obvious case (the same reason a git commit's hash never covers its
  own hash, or an IPFS DAG node's CID never covers its own CID field).
  :signatures' exclusion is required for the SAME reason once
  `kotoba.lang.package-contract/signatures-error` does real Ed25519
  verification (kotoba-lang PR #16, 2607131500): a signer's :sig attests to
  this manifest's :manifest-cid (`(signed-bytes manifest-cid)`), so if
  :manifest-cid's own hash also covered :signatures, producing a
  self-consistent manifest+signature pair would require solving a circular
  fixed point (the signature depends on the CID, and -- had :signatures not
  been excluded here -- the CID would depend on the signature) with no
  general closed-form solution. Excluding :signatures from the hashed
  content breaks the cycle: the CID is a pure function of the manifest's
  substantive fields, computed once, then signed, and the signature can be
  attached/rotated/added afterward without ever changing what the CID
  covers.

  KNOWN LIMITATION (independent review of PR #305, 2607131600): since
  :manifest-cid no longer covers :signatures, the CID cannot bind WHICH or
  HOW MANY signers vouched for this content -- `signatures-error` verifies
  every signature entry PRESENT is individually valid, but nothing requires
  a minimum count, a quorum, or membership in an authorized-signer set,
  so a manifest with one legitimate co-signer's entry silently removed (CID
  untouched) still passes admission today. Not currently exploitable
  because no policy in this codebase relies on that binding yet (this repo's
  only signer-list logic, :dep/signers in LOCK entries, is unrelated -- it
  gates dependencies against a revoked/expired/compromised denylist, not a
  manifest's own signer set). An n-of-m or quorum signing policy, if ever
  built, needs its own binding mechanism independent of :manifest-cid."
  [manifest]
  (-> manifest
      (update :kotoba.package/source dissoc :manifest-cid)
      (dissoc :kotoba.package/signatures)))

(defn compute-manifest-cid
  "The real CIDv1 (canonical DAG-CBOR + sha2-256, `cbor.core`/
  `multiformats.core` -- the same CID shape `kotoba.lang.package-contract/
  cid?` now structurally validates, kotoba-lang/kotoba-lang#13) of
  MANIFEST's actual content, excluding its own self-declared :manifest-cid."
  [manifest]
  (mf/cidv1-dag-cbor (cbor/encode (manifest-without-self-cid manifest))))

(defn manifest-integrity-error
  "nil if MANIFEST's self-declared :manifest-cid matches what its content
  actually hashes to; a package-contract-shaped error otherwise. Only
  meaningful once the shape check (`package-contract/package-manifest-error`)
  has already confirmed :manifest-cid is CID-shaped at all -- a missing or
  malformed field is that check's problem to report, not this one's."
  [manifest]
  (let [declared (get-in manifest [:kotoba.package/source :manifest-cid])]
    (when (package-contract/cid? declared)
      (let [computed (compute-manifest-cid manifest)]
        (when (not= declared computed)
          (package-contract/invalid "manifest cid does not match manifest content"
                                    {:declared declared :computed computed}))))))

(defn lock-level-error
  [lock]
  (or (when-not (= 1 (:kotoba.lock/version lock))
        (package-contract/invalid "lock version 1 required"
                                  {:value (:kotoba.lock/version lock)}))
      (when-not (vector? (:deps lock))
        (package-contract/invalid "lock deps vector required"
                                  {:value (:deps lock)}))))

(defn dep-error
  "First problem for a single lock entry: admission-layer safe-mode checks,
  then the package-contract lockfile kernel.

  OPTS may carry `:component-bytes` for L3 component-cid content integrity."
  ([dep trust] (dep-error dep trust nil))
  ([dep trust {:keys [component-bytes]}]
   (or (local-path-error dep)
       (package-contract/lockfile-error
        {:kotoba.lock/version 1 :deps [dep]}
        trust
        (when component-bytes
          {:component-bytes-by-dep {(:dep/name dep) component-bytes}})))))

(defn ->problem
  [source error]
  (merge {:kotoba.package/problem (problem-code (:message error))
          :kotoba.package/message (:message error)
          :kotoba.package/data (:data error)}
         source))

(defn dep-entry
  [dep error]
  (cond-> {:package/id (str (:dep/name dep) "@" (:dep/version dep))
           :package/repo-rid (:dep/repo-rid dep)
           :package/manifest-cid (:dep/manifest-cid dep)
           :package/tree-cid (:dep/tree-cid dep)
           :package/result (if error :rejected :accepted)}
    (:dep/kind dep) (assoc :package/kind (:dep/kind dep))
    (package-contract/component-cid-of dep)
    (assoc :package/component-cid (package-contract/component-cid-of dep)
           :package/build (:dep/build dep))))

(defn checked-at []
  (str (Instant/now)))

(defn verify-lock
  "Verify a parsed lock (plus optional parsed manifest and trust EDN) and
  return the package-verification receipt. The receipt is emitted on both
  accept and reject.

  OPTS may carry `:component-bytes-by-dep` {dep-name bytes} for L3 component
  content integrity (CID guest packages)."
  [{:keys [lock lock-path manifest manifest-path trust component-bytes-by-dep]}]
  (let [tc (trust-context trust manifest)
        manifest-error (when manifest (package-contract/package-manifest-error manifest))
        ;; Only check integrity once the shape check passed -- a missing or
        ;; malformed :manifest-cid is manifest-error's problem to report, not
        ;; a mismatch (there is nothing valid to mismatch against yet).
        integrity-error (when (and manifest (not manifest-error))
                          (manifest-integrity-error manifest))
        lock-error (lock-level-error lock)
        dep-results (mapv (fn [dep]
                            [dep (dep-error dep tc
                                            {:component-bytes
                                             (get component-bytes-by-dep
                                                  (:dep/name dep))})])
                          (:deps lock))
        problems (vec (concat
                       (when manifest-error
                         [(->problem {:kotoba.package/input :manifest
                                      :kotoba.package/path manifest-path}
                                     manifest-error)])
                       (when integrity-error
                         [(->problem {:kotoba.package/input :manifest
                                      :kotoba.package/path manifest-path}
                                     integrity-error)])
                       (when lock-error
                         [(->problem {:kotoba.package/input :lock
                                      :kotoba.package/path lock-path}
                                     lock-error)])
                       (keep (fn [[dep error]]
                               (when error
                                 (->problem {:kotoba.package/input :lock-entry
                                             :kotoba.package/dependency (:dep/name dep)}
                                            error)))
                             dep-results)))]
    {:kotoba.package/verified? (empty? problems)
     :kotoba.package/lock-path lock-path
     :kotoba.package/manifest-path manifest-path
     :kotoba.package/checked-at (checked-at)
     :kotoba.package/problems problems
     :kotoba.package/entries (mapv (fn [[dep error]] (dep-entry dep error)) dep-results)}))

(defn- dependency-manifest-mismatch [dep manifest]
  (let [manifest-signers (set (map :did (:kotoba.package/signatures manifest)))
        expected {:name (:dep/name dep)
                  :version (:dep/version dep)
                  :repo-rid (:dep/repo-rid dep)
                  :commit (:dep/commit dep)
                  :tree-cid (:dep/tree-cid dep)
                  :manifest-cid (:dep/manifest-cid dep)
                  :signers (set (:dep/signers dep))}
        actual {:name (:kotoba.package/name manifest)
                :version (:kotoba.package/version manifest)
                :repo-rid (:kotoba.package/repo-rid manifest)
                :commit (get-in manifest [:kotoba.package/source :git-commit])
                :tree-cid (get-in manifest [:kotoba.package/source :tree-cid])
                :manifest-cid (get-in manifest [:kotoba.package/source :manifest-cid])
                :signers manifest-signers}]
    (when-not (= expected actual)
      (package-contract/invalid "dependency manifest does not match lock entry"
                                {:expected expected :actual actual}))))

(defn- dependency-capability-error [dep manifest]
  (let [grant (set (:dep/capabilities dep))
        requested (set (:kotoba.package/capabilities manifest))]
    (when-not (set/subset? grant requested)
      (package-contract/invalid
       "dependency capability grant exceeds signed request"
       {:dependency (:dep/name dep)
        :grant (vec (sort grant))
        :requested (vec (sort requested))}))))

(defn- manifest-dependency-error [owner dependencies deps-by-name]
  (cond
    (not (vector? dependencies))
    (package-contract/invalid
     "dependency manifest closure does not match lock"
     {:reason :dependencies-vector-required :dependency owner})

    :else
    (let [names (mapv :dep/name dependencies)]
      (or
       (when-let [invalid (first (remove #(and (map? %)
                                               (set/subset?
                                                (set (keys %))
                                                #{:dep/name :dep/version :dep/kind})
                                               (string? (:dep/name %))
                                               (not (str/blank? (:dep/name %)))
                                               (string? (:dep/version %))
                                               (not (str/blank? (:dep/version %)))
                                               (or (not (contains? % :dep/kind))
                                                   (contains?
                                                    package-contract/allowed-package-kinds
                                                    (:dep/kind %))))
                                        dependencies))]
         (package-contract/invalid
          "dependency manifest closure does not match lock"
          {:reason :dependency-coordinate-invalid
           :dependency owner :value invalid}))
       (when-not (= (count names) (count (set names)))
         (package-contract/invalid
          "dependency manifest closure does not match lock"
          {:reason :duplicate-dependency-edge :dependency owner :targets names}))
       (when (some #{owner} names)
         (package-contract/invalid
          "dependency manifest closure does not match lock"
          {:reason :self-dependency-edge :dependency owner}))
       (some
        (fn [coordinate]
          (let [target (get deps-by-name (:dep/name coordinate))]
            (cond
              (nil? target)
              (package-contract/invalid
               "dependency manifest closure does not match lock"
               {:reason :dependency-target-missing
                :dependency owner :target (:dep/name coordinate)})

              (not= (:dep/version coordinate) (:dep/version target))
              (package-contract/invalid
               "dependency manifest closure does not match lock"
               {:reason :dependency-version-mismatch :dependency owner
                :target (:dep/name coordinate)
                :declared (:dep/version coordinate)
                :locked (:dep/version target)})

              (and (contains? coordinate :dep/kind)
                   (not= (:dep/kind coordinate) (:dep/kind target)))
              (package-contract/invalid
               "dependency manifest closure does not match lock"
               {:reason :dependency-kind-mismatch :dependency owner
                :target (:dep/name coordinate)
                :declared (:dep/kind coordinate)
                :locked (:dep/kind target)}))))
        dependencies)))))

(defn- dependency-cycle [dependency-manifests]
  (let [edges (into {}
                    (map (fn [[name manifest]]
                           [name (mapv :dep/name
                                       (:kotoba.package/dependencies manifest))]))
                    dependency-manifests)]
    (letfn [(visit [node visiting visited path]
              (cond
                (contains? visiting node)
                (conj (vec (drop-while #(not= node %) path)) node)

                (contains? visited node) nil

                :else
                (some #(visit % (conj visiting node) (conj visited node)
                              (conj path node))
                      (get edges node []))))]
      (some #(visit % #{} #{} []) (sort (keys edges))))))

(defn verify-project-lock
  "Strict closed-project package verification. Every lock dependency must have
  exactly one cryptographically valid manifest, its identity/interface fields
  must equal the lock entry, the lock capability grant must be a subset of the
  signed manifest request, and every signer must be explicitly allowlisted by
  the trust policy. Signed direct dependency coordinates must resolve exactly
  inside the lock with matching versions/kinds; duplicate identities, missing
  targets, ambient coordinate fields, and cycles fail closed. Extra manifests
  fail closed."
  [{:keys [lock lock-path trust dependency-manifests dependency-manifest-paths]}]
  (let [base (verify-lock {:lock lock :lock-path lock-path :trust trust})
        lock-names (mapv :dep/name (:deps lock))
        duplicate-lock-names (->> lock-names frequencies
                                  (keep (fn [[name n]] (when (> n 1) name)))
                                  sort vec)
        deps-by-name (into {} (map (juxt :dep/name identity)) (:deps lock))
        manifest-names (set (keys dependency-manifests))
        dep-names (set (keys deps-by-name))
        trusted (set (:trusted-signers trust))
        missing (sort (set/difference dep-names manifest-names))
        extra (sort (set/difference manifest-names dep-names))
        manifest-problems
        (vec
         (concat
          (when (seq duplicate-lock-names)
            [(->problem
              {:kotoba.package/input :lock}
              (package-contract/invalid
               "dependency manifest closure does not match lock"
               {:reason :duplicate-lock-dependency
                :dependencies duplicate-lock-names}))])
          (map (fn [name]
                 (->problem {:kotoba.package/input :dependency-manifest
                             :kotoba.package/dependency name}
                            (package-contract/invalid "dependency manifest required"
                                                      {:dependency name})))
               missing)
          (map (fn [name]
                 (->problem {:kotoba.package/input :dependency-manifest
                             :kotoba.package/dependency name}
                            (package-contract/invalid "unexpected dependency manifest"
                                                      {:dependency name})))
               extra)
          (keep
           (fn [name]
             (let [dep (get deps-by-name name)
                   manifest (get dependency-manifests name)
                   error (or (package-contract/package-manifest-error manifest)
                             (manifest-integrity-error manifest)
                             (dependency-manifest-mismatch dep manifest)
                             (dependency-capability-error dep manifest)
                             (manifest-dependency-error
                              name (:kotoba.package/dependencies manifest)
                              deps-by-name)
                             (when-let [untrusted
                                        (seq (set/difference
                                              (set (:dep/signers dep)) trusted))]
                               (package-contract/invalid
                                "dependency signer is not explicitly trusted"
                                {:dependency name :signers (vec (sort untrusted))})))]
               (when error
                 (->problem {:kotoba.package/input :dependency-manifest
                             :kotoba.package/dependency name
                             :kotoba.package/path (get dependency-manifest-paths name)}
                            error))))
           (sort (set/intersection dep-names manifest-names)))
          (when-let [cycle (and (= dep-names manifest-names)
                                (dependency-cycle dependency-manifests))]
            [(->problem
              {:kotoba.package/input :dependency-manifest}
              (package-contract/invalid
               "dependency manifest closure does not match lock"
               {:reason :dependency-cycle :cycle cycle}))])))
        problems (into (:kotoba.package/problems base) manifest-problems)]
    (assoc base
           :kotoba.package/verified? (empty? problems)
           :kotoba.package/problems problems
           :kotoba.package/dependency-manifest-digests
           (into (sorted-map)
                 (map (fn [[name manifest]]
                        [name (artifact/sha256 manifest)]))
                 dependency-manifests))))

(defn receipt-evidence
  "Deterministic, path/time-free receipt value sealed into build identity."
  [receipt]
  {:kotoba.package/schema :kotoba.package/verification-evidence-v1
   :kotoba.package/verified? (:kotoba.package/verified? receipt)
   :kotoba.package/entries (vec (sort-by :package/id (:kotoba.package/entries receipt)))
   :kotoba.package/dependency-manifest-digests
   (:kotoba.package/dependency-manifest-digests receipt)
   :kotoba.package/problems (:kotoba.package/problems receipt)})

(defn receipt-digest [receipt]
  ;; Use the compiler's already-native-verified canonical EDN hash here. DAG-
  ;; CBOR remains authoritative for package CIDs, but receipt evidence is a
  ;; distinct versioned schema and must not introduce a second runtime codec
  ;; initialization path into the Graal launcher.
  (artifact/sha256 (receipt-evidence receipt)))

(defn compute-component-cid
  "CIDv1-raw of COMPONENT-BYTES — the pin used for :dep/component-cid on
  guest component packages (L3, ADR-2607180900)."
  [component-bytes]
  (mf/cidv1-raw component-bytes))

(defn guest-package-dep
  "Build a lock-entry map for a closed CID guest package (not ambient require).

  Requires name/version/repo-rid/commit/tree-cid/manifest-cid/signers plus a
  real component-cid over COMPONENT-BYTES. Kind defaults to :component.
  Capabilities default to [] (deny-by-default)."
  [{:keys [name version repo-rid commit tree-cid manifest-cid signers
           component-bytes capabilities kind ref]
    :or {capabilities [] kind :component ref "refs/heads/main"}}]
  (let [component-cid (compute-component-cid component-bytes)]
    {:dep/name name
     :dep/version version
     :dep/kind kind
     :dep/repo-rid repo-rid
     :dep/ref ref
     :dep/commit commit
     :dep/tree-cid tree-cid
     :dep/manifest-cid manifest-cid
     :dep/signers (vec signers)
     :dep/capabilities (vec capabilities)
     :dep/component-cid component-cid
     :dep/build {:deterministic true
                 :component-cid component-cid}}))

(defn admit-guest-component
  "L3: admit a single closed guest component package (CID-pinned, no require).
  COMPONENT-BYTES are the built artifact (wasm / sealed graph payload).
  Returns the same shape as `admit` over an in-memory lock."
  [{:keys [name version repo-rid commit tree-cid manifest-cid signers
           component-bytes capabilities trust]}]
  (let [dep (guest-package-dep
             {:name name :version version :repo-rid repo-rid :commit commit
              :tree-cid tree-cid :manifest-cid manifest-cid :signers signers
              :component-bytes component-bytes :capabilities capabilities})
        lock {:kotoba.lock/version 1 :deps [dep]}
        receipt (verify-lock
                 {:lock lock
                  :lock-path "<guest-component>"
                  :trust trust
                  :component-bytes-by-dep {name component-bytes}})]
    {:kotoba.admission/ok? (:kotoba.package/verified? receipt)
     :kotoba.admission/code (if (:kotoba.package/verified? receipt)
                              :package-verified
                              :package-rejected)
     :kotoba.admission/receipt receipt
     :kotoba.admission/dep dep}))

;; ---------------------------------------------------------------------------
;; File-level admission

(defn read-edn-file
  [path]
  (let [file (io/file path)]
    (if (and (.isFile file) (.canRead file))
      (try
        {:ok? true :value (edn/read-string (slurp file))}
        (catch Exception e
          {:ok? false :error (.getMessage e)}))
      {:ok? false :error "file not readable"})))

(defn write-receipt!
  [path receipt]
  (let [file (io/file path)]
    (io/make-parents file)
    (spit file (with-out-str (pprint/pprint receipt)))))

(def usage
  "kotoba package verify --lock <kotoba.lock.edn> [--manifest <package-manifest.edn>] [--trust <trust.edn>] [--key-register <key-register.edn>] [--receipt <out.edn>] [--json]")

(defn not-readable
  [code path error]
  {:kotoba.admission/ok? false
   :kotoba.admission/code code
   :kotoba.admission/error {:kotoba.package/path path
                            :kotoba.package/error (:error error)}})

(defn admit
  "Run the package admission gate over file paths. Returns
  {:kotoba.admission/ok? bool
   :kotoba.admission/code keyword
   :kotoba.admission/receipt receipt (when the inputs were readable)
   :kotoba.admission/receipt-path path (when a receipt file was written)
   :kotoba.admission/error data (when the inputs were not readable)}

  Optional key-register-path folds non-active key statuses into trust
  revoked-signers (R-002)."
  [{:keys [lock-path manifest-path trust-path key-register-path receipt-path]}]
  (if-not lock-path
    {:kotoba.admission/ok? false
     :kotoba.admission/code :package/missing-lock-option
     :kotoba.admission/error {:kotoba.package/usage usage}}
    (let [lock (read-edn-file lock-path)
          manifest (some-> manifest-path read-edn-file)
          trust (some-> trust-path read-edn-file)
          key-reg (some-> key-register-path read-edn-file)]
      (cond
        (not (:ok? lock))
        (not-readable :package/lock-not-readable lock-path lock)

        (and manifest (not (:ok? manifest)))
        (not-readable :package/manifest-not-readable manifest-path manifest)

        (and trust (not (:ok? trust)))
        (not-readable :package/trust-not-readable trust-path trust)

        (and key-reg (not (:ok? key-reg)))
        (not-readable :package/key-register-not-readable key-register-path key-reg)

        :else
        (let [trust-value (cond-> (or (:value trust) {})
                            (:ok? key-reg)
                            (assoc :key-register (:value key-reg)))
              receipt (verify-lock {:lock (:value lock)
                                    :lock-path lock-path
                                    :manifest (:value manifest)
                                    :manifest-path manifest-path
                                    :trust trust-value})
              verified? (:kotoba.package/verified? receipt)]
          (when receipt-path
            (write-receipt! receipt-path receipt))
          (cond-> {:kotoba.admission/ok? verified?
                   :kotoba.admission/code (if verified? :package-verified :package-rejected)
                   :kotoba.admission/receipt receipt}
            receipt-path (assoc :kotoba.admission/receipt-path receipt-path)))))))

(defn cli-result
  "Wrap an admission run as a launcher CLI result map."
  [opts]
  (let [admission (admit opts)]
    {:kotoba.cli/ok? (:kotoba.admission/ok? admission)
     :kotoba.cli/code (:kotoba.admission/code admission)
     :kotoba.cli/data (cond-> {}
                        (:kotoba.admission/receipt admission)
                        (assoc :kotoba.package/receipt (:kotoba.admission/receipt admission))

                        (:kotoba.admission/receipt-path admission)
                        (assoc :kotoba.package/receipt-path (:kotoba.admission/receipt-path admission))

                        (:kotoba.admission/error admission)
                        (assoc :kotoba.package/error (:kotoba.admission/error admission)))}))

(defn resolve-lock-with-registry
  "Resolve version-only dependency requests through a package registry into
  a full lock, then verify-lock (fail-closed). REGISTRY is parsed EDN;
  REQUESTS is a vector of {:name :version :capabilities?}.

  Returns the same shape as `admit` plus :kotoba.admission/lock when ok."
  [{:keys [registry requests trust]}]
  (let [resolved (package-registry/lock-from-requests registry requests)]
    (if-not (:ok? resolved)
      {:kotoba.admission/ok? false
       :kotoba.admission/code :package/registry-resolve-failed
       :kotoba.admission/error {:kotoba.package/problems (:problems resolved)}}
      (let [receipt (verify-lock {:lock (:lock resolved)
                                  :lock-path "<registry-resolved>"
                                  :trust trust})]
        {:kotoba.admission/ok? (:kotoba.package/verified? receipt)
         :kotoba.admission/code (if (:kotoba.package/verified? receipt)
                                  :package-verified
                                  :package-rejected)
         :kotoba.admission/receipt receipt
         :kotoba.admission/lock (:lock resolved)}))))

(defn safe-release-ready?
  "F-001 / F-007 partial + L3: a safe release may proceed only when a package-
  verification receipt exists, is verified, and carries no reject entries.
  Optional `:require-component-cid?` (default false) additionally demands
  every accepted entry pin a component-cid (guest component packages)."
  ([receipt] (safe-release-ready? receipt nil))
  ([receipt {:keys [require-component-cid?] :or {require-component-cid? false}}]
   (cond
     (nil? receipt)
     {:ok? false
      :problems [{:kotoba.package/problem :package/missing-receipt
                  :kotoba.package/message "package verification receipt required for safe release"}]}

     (not (map? receipt))
     {:ok? false
      :problems [{:kotoba.package/problem :package/invalid-receipt
                  :kotoba.package/message "receipt must be a map"}]}

     (not (true? (:kotoba.package/verified? receipt)))
     {:ok? false
      :problems (into [{:kotoba.package/problem :package/not-verified
                        :kotoba.package/message "receipt is not verified"}]
                      (:kotoba.package/problems receipt))}

     (some #(= :rejected (:package/result %)) (:kotoba.package/entries receipt))
     {:ok? false
      :problems [{:kotoba.package/problem :package/rejected-entry
                  :kotoba.package/message "receipt contains rejected package entries"}]}

     (and require-component-cid?
          (let [entries (:kotoba.package/entries receipt)]
            (or (empty? entries)
                (some (fn [e]
                        (not (package-contract/cid?
                              (or (:package/component-cid e)
                                  (get-in e [:package/build :component-cid])))))
                      entries))))
     {:ok? false
      :problems [{:kotoba.package/problem :package/component-cid-required
                  :kotoba.package/message "safe release of component packages requires component-cid pins"}]}

     :else
     {:ok? true :problems []})))
