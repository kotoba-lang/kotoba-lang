(ns kotoba.compiler.nbb.package-lock
  "Resolve a Kotoba package lock (`kotoba.lock.edn`,
  `:kotoba.lock/version 1`) into source roots the project linker can read,
  without a JVM and without the network.

  WHAT THIS IS FOR. `ADR-kotoba-package-cid-lock` (accepted 2026-06-30)
  specified the lock, `kotoba.lang.package-contract` validates it, and the
  conformance corpus exercises it -- but its own maturity list has recorded
  M5, \"`kotoba-lang/registry` or `kotoba-cli` consumes the same suite\", as
  outstanding since the day it was written. Nothing read a lock. A package
  could be published, signed and pinned, and no compiler would look at it, so
  the only way to depend on Kotoba code was to hand `--source-path` a
  directory and hope. This namespace is that missing consumer.

  THE DIVISION OF LABOUR, because two locks now exist and they are not
  competing:

    kotoba.lock.edn      WHO and WHAT MAY IT DO -- package name and version,
    (this file)          repo identity, the commit it came from, the signer
                         DIDs, and the capability grant. Authority.

    kotoba.modules.edn   WHICH BYTES -- every module in the closed graph by
    (module_lock.cljs)   CID, read from a content-addressed block directory.
                         Integrity.

  They compose in that order: a package lock resolves to source roots, the
  linker walks them, and `module-lock` pins the result. Neither subsumes the
  other. A module lock says nothing about who signed the code; a package lock
  says nothing about the bytes of an individual module.

  NO NETWORK, ON PURPOSE. This does not fetch. Dependency trees are read from
  `--packages <dir>`, the way locked module bytes are read from `--blocks`.
  Fetching is the authoring side's job, and keeping it there is what lets a
  build be reproduced on a machine with no credentials and no route out.

  WHAT IS STRICTER HERE THAN IN THE CONTRACT. `:dep/definition-cids` is
  optional in `kotoba.lang.package-contract` -- deliberately, because
  effectful code stays component-addressed until its capability interface is
  in the typed KIR. It is REQUIRED here. The reason is that without it this
  namespace has nothing to check the materialised tree against: the lock's
  `:dep/tree-cid` pins a codebase head this route never computes, and
  `:dep/repo-rid` is a commitment to a URL, not a locator. Admitting a
  directory because it sits at the right path, under a name the lock
  mentions, would be a lock in name only -- the same failure `module_lock`
  refuses when it declines to fall back to a path search. A dep without
  definition CIDs is refused with the command that produces them."
  (:require ["node:child_process" :as child]
            ["node:crypto" :as crypto]
            ["node:fs" :as fs]
            ["node:path" :as node-path]
            [clojure.set :as set]
            [clojure.string :as str]
            [kotoba.lang.package-contract :as contract]
            [kotoba.compiler.nbb.cli-support :as support]))

(def lock-schema :kotoba.lock/version)

(defn- reject! [message data]
  (throw (ex-info message (assoc data :phase :package-lock))))

(defn- bigint?
  "True for a JS BigInt. `goog/typeOf` and `js*` are both unavailable under
  nbb/SCI, and `instance?` is false for a primitive; reading `.constructor`
  boxes it, which is why this is the check. Guarded because that read throws
  on nil."
  [x]
  (and (some? x) (= js/BigInt (.-constructor x))))

(defn normalize-numbers
  "Turn the bounded reader's BigInt integers back into JS numbers.

  Amu's EDN reader is the guest-source reader, whose numeric model is
  Kotoba's: an integer literal reads as i64, which on this host is a BigInt.
  That is right for source and wrong for a lock, because
  `kotoba.lang.package-contract` is portable `.cljc` written against Clojure
  numbers and asks `(= 1 (:kotoba.lock/version m))`. `1n` is not `1`.

  Measured 2026-09-06 before this existed: a lock this CLI had just WRITTEN
  and validated was refused on the way back in with \"package lock version 1
  required\", naming a version that was, in every sense a reader of the file
  would care about, 1. Producer and consumer disagreed about nothing except
  which host type an integer lands in.

  Values outside the safe-integer range are left as BigInt rather than
  silently rounded -- a lock does not carry any, and quietly losing precision
  to make a comparison succeed is the failure this function exists to stop,
  not a smaller version of it."
  [value]
  (cond
    (bigint? value)
    (if (<= (js/BigInt js/Number.MAX_SAFE_INTEGER) value) value (js/Number value))

    (map? value) (into (empty value)
                       (map (fn [[k v]] [(normalize-numbers k)
                                         (normalize-numbers v)]))
                       value)
    (set? value) (into (empty value) (map normalize-numbers) value)
    (vector? value) (mapv normalize-numbers value)
    (seq? value) (mapv normalize-numbers value)
    :else value))

;; ── the package directory ────────────────────────────────────────────────────

(defn package-dir-name
  "One path segment for a package name. `/` is the only character package
  names carry that a path segment cannot, and `+` is not legal in a package
  name, so the mapping is injective -- two different packages cannot land in
  one directory."
  [package-name]
  (str/replace package-name "/" "+"))

(defn dependency-root
  "Where `<packages>/` holds this dependency's tree. Addressed by commit, not
  by version: two locks pinning the same name at different commits coexist,
  and a re-tagged version cannot silently reuse the other's directory."
  [packages-dir dep]
  (.join node-path packages-dir
         (package-dir-name (:dep/name dep))
         (:dep/commit dep)))

(defn- directory? [p]
  (try (.isDirectory (.statSync fs p)) (catch :default _ false)))

;; ── provenance: is this checkout at the commit the lock names ────────────────

(defn- git-head-sha
  "The commit a materialised dependency is actually at, or nil when it is not
  a git checkout at all. A directory NAMED after a commit is not evidence it
  holds that commit -- the same check `kotoba.compiler.nbb.classpath` makes
  for Amu's own pinned dependencies."
  [dir]
  (let [result (.spawnSync child "git" #js ["-C" dir "rev-parse" "HEAD"]
                           #js {:encoding "utf8"})]
    (when (and (not (.-error result)) (zero? (or (.-status result) 1)))
      (str/trim (.-stdout result)))))

(defn- commit-verdict
  "Three outcomes, and the third is not the second. A tree with no git
  metadata was not checked, and saying so in the report is the difference
  between `git-commit verified` and `git-commit UNVERIFIED` on the line the
  caller prints. Collapsing them would let an exported tarball read exactly
  like a checkout that matched."
  [dir dep]
  (if-let [head (git-head-sha dir)]
    (if (= head (:dep/commit dep))
      {:check :git-commit :outcome :verified :head head}
      (reject! "dependency checkout is not at the commit the lock pins"
               {:dependency (:dep/name dep)
                :pinned (:dep/commit dep) :actual head :root dir}))
    {:check :git-commit :outcome :unverified
     :reason "no git metadata in the materialised tree"}))

;; ── development-only shapes the lock must not carry ─────────────────────────

(def ^:private local-root-keys
  [:package/local-root :dep/local-root :dep/local-path :dep/path :dep/root])

(defn- local-path-error
  "`docs/lang/package-rules.md`: \"Local path dependencies are development-only
  unless the same content is locked by tree CID before safe-build.\" A lock
  reaching a compiler is past that point.

  Mirrors `kotoba.security.package-admission/local-path-error`, which is
  `.clj` and therefore unreachable from this route."
  [dep]
  (when-let [k (some #(when (contains? dep %) %) local-root-keys)]
    (reject! "local-path dependency not allowed in a consumed lock"
             {:dependency (:dep/name dep) :field k :value (get dep k)})))

(def tree-serialization :kotoba.package-tree/v1)

(def manifest-file-name "package-manifest.edn")

(def ^:private ignored-tree-entries
  ;; `package-manifest.edn` is EXCLUDED for the same reason a manifest's
  ;; `:manifest-cid` is excluded from its own hash: it is a statement ABOUT
  ;; this tree, written into it, and its own `:tree-cid` field is one of the
  ;; things it states. Including it makes the fixed point unreachable --
  ;; measured 2026-09-06, the first generated manifest hashed the tree, was
  ;; written into that tree, and the consumer then refused it with `tree cid
  ;; does not match tree content` against a manifest that had been correct one
  ;; file-write earlier.
  ;;
  ;; Nothing is left uncovered by the exclusion: the manifest's own bytes are
  ;; pinned by `:manifest-cid`, which the signature is over and
  ;; `manifest-integrity-error` recomputes.
  ;;
  ;; `.git` is excluded because it is not the package -- two checkouts of the
  ;; same commit differ inside it (packed vs loose objects, reflogs, remote
  ;; names), so a tree hash covering it would never match twice.
  #{".git" "node_modules" ".DS_Store" manifest-file-name})

(defn- tree-files
  "Every regular file under ROOT, as repository-relative POSIX paths, sorted.

  What is skipped, and why, is `ignored-tree-entries`."
  ([root] (vec (sort (tree-files root ""))))
  ([root prefix]
   (let [dir (if (str/blank? prefix) root (.join node-path root prefix))]
     (mapcat
      (fn [entry]
        (let [name (.-name entry)
              rel (if (str/blank? prefix) name (str prefix "/" name))]
          (cond
            (contains? ignored-tree-entries name) nil
            (.isDirectory entry) (tree-files root rel)
            (.isFile entry) [rel]
            :else nil)))
      (.readdirSync fs dir #js {:withFileTypes true})))))

(defn tree-bytes
  "The bytes `:tree-cid` is the CIDv1-raw of, for a package tree on disk.

  `kotoba.lang.package-contract/tree-cid-error` takes tree bytes from its
  caller and says why in its own docstring: a source tree is not part of the
  data that kernel receives, so it cannot define what hashing one means. This
  is that definition, and it lives here because this is the layer that has a
  filesystem.

  The serialization is a sorted listing of `<relative path>\t<sha256 hex>`,
  one per line, UTF-8. Deliberately NOT a tar or a zip: those carry mtimes,
  uids, and ordering that differ between two materialisations of the same
  content, so hashing one would answer \"was this archive built the same
  way\" rather than \"is this the same code\". Sorting is by the path bytes,
  so it does not depend on a locale.

  Versioned by `tree-serialization`: if this ever changes, tree CIDs computed
  under the old rule stop matching, and that must be a visible break rather
  than a silent one."
  [root]
  (let [lines (mapv (fn [rel]
                      (let [bytes (.readFileSync fs (.join node-path root rel))
                            digest (-> (.createHash crypto "sha256")
                                       (.update bytes) (.digest "hex"))]
                        (str rel "\t" digest)))
                    (tree-files root))]
    (when (empty? lines)
      (reject! "package tree is empty" {:root root}))
    (.from js/Buffer (str/join "\n" lines) "utf8")))

(defn- read-manifest! [root dep]
  (let [path (.join node-path root manifest-file-name)]
    (when-not (.existsSync fs path)
      (reject! "dependency tree carries no package manifest"
               {:dependency (:dep/name dep) :expected path
                :note (str "the lock names signers for this dependency; "
                           "without the manifest they signed, nothing binds "
                           "those DIDs to this code")}))
    (try
      (normalize-numbers (support/read-edn-file! path))
      (catch :default e
        (reject! "package manifest does not read as EDN"
                 {:dependency (:dep/name dep) :path path
                  :error (.-message e)})))))

(defn- manifest-verdicts!
  "Bind the lock entry to the manifest the signers actually signed.

  Four checks, and they are four because each is unbinding on its own:

    package-manifest-error   shape, and a REAL Ed25519 signature -- but over
                             the manifest's own self-declared cid
    manifest-integrity-error that self-declared cid is what the content
                             hashes to. Without this, every field except the
                             cid can be edited after signing
    identity                 the manifest is this package at this version.
                             A valid, self-consistent manifest for a
                             DIFFERENT package is still a valid manifest
    signers / capabilities   the DIDs the lock claims actually appear among
                             the manifest's signatures, and the grant does
                             not exceed what the package declares it may
                             request

  Both contract functions are portable as of kotoba-core-contracts 2ebe7461;
  before that they were `:clj`-only and this whole sequence was unreachable
  from a Node compiler."
  [manifest dep]
  (when-let [e (contract/package-manifest-error manifest)]
    (reject! (str "package manifest rejected: " (:message e))
             (assoc (:data e) :dependency (:dep/name dep))))
  (when-let [e (contract/manifest-integrity-error manifest)]
    (reject! (str "package manifest rejected: " (:message e))
             (assoc (:data e) :dependency (:dep/name dep))))
  (when-not (= (:kotoba.package/name manifest) (:dep/name dep))
    (reject! "package manifest is for a different package"
             {:dependency (:dep/name dep)
              :manifest-name (:kotoba.package/name manifest)}))
  (when-not (= (:kotoba.package/version manifest) (:dep/version dep))
    (reject! "package manifest is for a different version"
             {:dependency (:dep/name dep)
              :locked (:dep/version dep)
              :manifest-version (:kotoba.package/version manifest)}))
  (let [declared-cid (get-in manifest [:kotoba.package/source :manifest-cid])]
    (when-not (= declared-cid (:dep/manifest-cid dep))
      (reject! "manifest cid in the lock is not this manifest's cid"
               {:dependency (:dep/name dep)
                :locked (:dep/manifest-cid dep)
                :manifest declared-cid})))
  (let [signed-by (set (map :did (:kotoba.package/signatures manifest)))
        claimed (set (:dep/signers dep))]
    (when-let [absent (seq (set/difference claimed signed-by))]
      (reject! "the lock names signers who did not sign this manifest"
               {:dependency (:dep/name dep) :missing (vec (sort absent))})))
  (let [requested (set (:kotoba.package/capabilities manifest))
        granted (set (:dep/capabilities dep))]
    (when-let [over (seq (set/difference granted requested))]
      (reject! "lock grants capabilities the package does not declare"
               {:dependency (:dep/name dep) :granted (vec (sort over))
                :declared (vec (sort requested))})))
  [{:check :manifest-signature :outcome :verified
    :signers (vec (sort (map :did (:kotoba.package/signatures manifest))))}
   {:check :manifest-integrity :outcome :verified
    :manifest-cid (:dep/manifest-cid dep)}])

;; ── the source roots a dependency contributes ───────────────────────────────

(def ^:private default-source-subdirs ["src"])

(defn- dependency-source-roots
  "`<root>/src` when it exists, else `<root>`. A package that keeps its
  modules at the top of its tree is not malformed, and a package that uses
  `src/` must not also expose its `test/` or `scripts/` to the consumer's
  namespace search -- two modules claiming one namespace is an error the
  linker raises, and it should not be reachable by depending on a package
  that happens to have a test fixture with a colliding name."
  [root]
  (let [subdirs (filterv #(directory? (.join node-path root %))
                         default-source-subdirs)]
    (if (seq subdirs)
      (mapv #(.join node-path root %) subdirs)
      [root])))

;; ── the trust context the contract validates against ────────────────────────

(def ^:private honoured-trust-keys
  #{:declared-capabilities :revoked-signers :expired-signers :compromised-signers})

(def ^:private known-unhonoured-trust-keys
  "Keys `kotoba.security.package-admission/trust-context` acts on and this one
  does not. Named rather than lumped in with typos, so the refusal can say
  which behaviour is missing instead of only that something is."
  {:key-register (str "a key register maps signer DIDs to statuses, and blocked "
                      "statuses are merged into the revoked set. Not implemented "
                      "here: expand it into :revoked-signers before passing the "
                      "trust file to this route")
   :keys (str "an inline key register. Same as :key-register -- expand it into "
              ":revoked-signers")})

(defn trust-context
  "What the caller grants and whom it no longer trusts, as
  `kotoba.lang.package-contract/lockfile-error` wants it.

  The file shape is `kotoba.security.package-admission`'s: `:declared-capabilities`,
  `:revoked-signers`, `:expired-signers`, `:compromised-signers`. Deliberately
  the same file, so one trust document serves both routes.

  TWO DIFFERENCES, both stated because a trust file that behaves differently
  depending on which tool reads it is worse than two file formats:

  1. A key that this route does not act on is REFUSED, not ignored. The JVM
     route honours `:key-register` / `:keys`, folding blocked statuses into the
     revoked set; this one does not. Ignoring such a file would apply a
     STRICTLY WEAKER check while looking like it had applied the caller's --
     the trust document would name revoked signers and the build would accept
     them.

  2. Absent `:declared-capabilities`, this route grants NOTHING, where the JVM
     route falls back to the manifest's own declared capabilities. That is not
     a gap: `manifest-verdicts!` already checks the grant against the
     manifest's declaration, so folding the same comparison in here would
     answer one question twice and leave the caller's own ceiling unasked.
     The ADR's rule -- dependencies receive no host capability by default --
     is expressed as the default rather than as prose."
  [trust]
  (when trust
    (doseq [[k why] known-unhonoured-trust-keys]
      (when (contains? trust k)
        (reject! (str "this route does not honour " k " in a trust file")
                 {:key k :reason why})))
    (when-let [unknown (seq (remove honoured-trust-keys (keys trust)))]
      (reject! "unrecognised keys in the trust file"
               {:keys (vec (sort unknown))
                :honoured (vec (sort honoured-trust-keys))
                :reason (str "a misspelled key would otherwise be silently "
                             "empty, which reads exactly like a caller that "
                             "granted nothing")})))
  {:declared-capabilities (vec (:declared-capabilities trust))
   :revoked-signers (vec (:revoked-signers trust))
   :expired-signers (vec (:expired-signers trust))
   :compromised-signers (vec (:compromised-signers trust))})

(defn- tree-verdict!
  "The materialised tree hashes to the `:dep/tree-cid` the lock pins.

  This is the check that makes `--packages` safe to point at a directory
  somebody else populated. Without it the only thing tying the tree to the
  lock is its path."
  [root dep]
  (if-let [e (contract/tree-cid-error (:dep/tree-cid dep) (tree-bytes root))]
    (reject! (str "package tree rejected: " (:message e))
             (assoc (:data e) :dependency (:dep/name dep) :root root
                    :serialization tree-serialization))
    {:check :tree-cid :outcome :verified
     :serialization tree-serialization
     :tree-cid (:dep/tree-cid dep)}))

;; ── resolution ──────────────────────────────────────────────────────────────

(defn- validate-lock! [lock trust]
  (when-not (= 1 (:kotoba.lock/version lock))
    (reject! "package lock version 1 required"
             {:value (:kotoba.lock/version lock)}))
  (when-let [error (contract/lockfile-error lock (trust-context trust))]
    (reject! (:message error) (assoc (:data error) :contract true))))

(defn- definition-cids! [dep]
  (let [cids (:dep/definition-cids dep)]
    (when-not (and (vector? cids) (seq cids))
      (reject! (str "dependency does not pin its definitions, so the "
                    "materialised tree cannot be checked against the lock")
               {:dependency (:dep/name dep)
                :field :dep/definition-cids
                :remedy (str "amu definition-cids <entry> --source-path <dir> "
                             "-- add the CIDs it prints to this dep entry")}))
    (set cids)))

(defn resolve-lock
  "LOCK-PATH and PACKAGES-DIR to `{:source-paths [...] :dependencies [...]}`.

  Every dependency is refused rather than skipped: a lock this route cannot
  satisfy must not produce a shorter source path, because a shorter source
  path fails later as `namespace not found`, which reads like the guest's
  mistake."
  [lock-path packages-dir trust]
  (when-not (.existsSync fs lock-path)
    (reject! "package lock not found" {:path lock-path}))
  (when-not (directory? packages-dir)
    (reject! "--packages must name an existing directory" {:path packages-dir}))
  ;; Same bounded reader the module lock and `--policy` get. A lock arrives
  ;; from the caller and is supposed to make a build MORE constrained, so it
  ;; must not be the one input read without limits.
  (let [lock (try (normalize-numbers (support/read-edn-file! lock-path))
                  (catch :default e
                    (reject! "package lock does not read as EDN"
                             {:path lock-path :error (.-message e)})))
        _ (validate-lock! lock trust)
        deps (:deps lock)]
    (let [resolved
          (mapv (fn [dep]
                  (local-path-error dep)
                  (let [root (dependency-root packages-dir dep)]
                    (when-not (directory? root)
                      (reject! "dependency tree is not materialised"
                               {:dependency (:dep/name dep)
                                :commit (:dep/commit dep)
                                :expected root
                                :remedy (str "amu package-fetch --lock "
                                             lock-path " --packages "
                                             packages-dir)}))
                    {:name (:dep/name dep)
                     :version (:dep/version dep)
                     :commit (:dep/commit dep)
                     :signers (vec (:dep/signers dep))
                     :capabilities (vec (:dep/capabilities dep))
                     :definition-cids (definition-cids! dep)
                     :root root
                     :source-paths (dependency-source-roots root)
                     :checks (into [(commit-verdict root dep)
                                    (tree-verdict! root dep)]
                                   (manifest-verdicts! (read-manifest! root dep)
                                                       dep))}))
                deps)]
      {:lock lock-path
       :packages packages-dir
       :dependencies resolved
       :source-paths (into [] (mapcat :source-paths) resolved)})))

;; ── the check that binds the tree to the lock ───────────────────────────────

(defn verify-definitions
  "Every definition CID the lock pins must be present in the CIDs the
  compiler computed for the linked project.

  This is the step that makes the lock mean something. Definition CIDs are
  context-independent -- measured 2026-09-06: `double-it` hashes to
  `bafyreihmm4hh…` compiled alone and again as a dependency of another
  module -- so a CID the lock names either appears in the built graph or the
  bytes are not the bytes that were signed.

  Superset, not equality: the consumer's own definitions are in COMPUTED too,
  and a package may export more than a given consumer binds."
  [dependencies computed]
  (let [have (set computed)
        missing (keep (fn [dep]
                        (let [gone (set/difference (:definition-cids dep) have)]
                          (when (seq gone)
                            {:dependency (:name dep)
                             :missing (vec (sort gone))})))
                      dependencies)]
    (if (seq missing)
      (reject! "definitions the package lock pins are not in the built graph"
               {:dependencies (vec missing)
                :note (str "the materialised tree does not contain the code "
                           "this lock was signed over")})
      {:check :definition-cids
       :outcome :verified
       :pinned (reduce + 0 (map #(count (:definition-cids %)) dependencies))
       :computed (count have)})))

(defn report
  "What was checked, per dependency, in the command's own answer. A caller
  reading only `:ok true` cannot tell a verified commit from an unverifiable
  one; this makes the difference printable."
  [resolved definition-check]
  {:kotoba.package-lock/version 1
   :lock (:lock resolved)
   :packages (:packages resolved)
   :dependencies (mapv (fn [d]
                         {:name (:name d) :version (:version d)
                          :commit (:commit d)
                          :signers (:signers d)
                          :capabilities (:capabilities d)
                          :definitions (count (:definition-cids d))
                          :checks (:checks d)})
                       (:dependencies resolved))
   :definition-check definition-check
   :scanned (str "SCANNED\t" (count (:dependencies resolved)) " dependencies")})
