(ns kotoba.compiler.nbb.package-authoring
  "Produce what `kotoba.compiler.nbb.package-lock` consumes: a signed package
  manifest, and a `kotoba.lock.edn` resolved from a dependency declaration.

  WHERE tools.deps FITS, AND WHY IT IS ONLY HERE. Choosing between conflicting
  transitive git pins is a solved problem with an implementation
  (`tools.deps`), and a second resolver written in nbb would be free to
  disagree with the first. So this reuses it -- and confines it to authoring
  time, exactly as `scripts/lock-classpath.cljs` confines it for Amu's own
  classpath. The split is the whole point:

    authoring   `amu package-lock`  resolves a closure, may use a JDK,
                                    writes CID pins into kotoba.lock.edn
    build       `amu compile        reads the lock. No resolver, no network,
                 --package-lock`    no JDK, no path search.

  A commit id is a content address, so once the answer is recorded it can be
  reproduced without re-deriving it. `--jvm-free` therefore REFUSES
  `package-lock` rather than quietly resolving some other way: the honest
  answer to \"resolve this closure without a JDK\" is that this command does
  not, and a fallback that produced a different closure under the same flag
  would be worse than a refusal.

  `package-manifest` needs none of that and runs on nbb alone."
  (:require ["node:child_process" :as child]
            ["node:fs" :as fs]
            ["node:path" :as node-path]
            [clojure.string :as str]
            [ed25519.core :as ed25519]
            [kotoba.lang.package-contract :as contract]
            [multiformats.core :as mf]
            [kotoba.compiler.nbb.cli-support :as support]
            [kotoba.compiler.nbb.io :as io]
            [kotoba.compiler.nbb.package-lock :as package-lock]))

(defn write-edn!
  "Write EDN the way this CLI's own reader will accept it back.

  `pr-str` abbreviates a map whose keys share a namespace as `#:ns{...}`, and
  the bounded reader every EDN input here goes through refuses that dispatch.
  A manifest written with the default binding therefore round-trips through
  nothing: it is written successfully, and the next command that reads it
  fails with `unsupported reader dispatch` -- a message about the reader,
  pointing at the file, naming neither the writer that produced it nor the
  syntax at fault. Measured 2026-09-06 on the first generated manifest.

  Every map this namespace writes has uniformly-namespaced keys, so the
  abbreviation would fire on all of them."
  [path value]
  (io/write-text! path (binding [*print-namespace-maps* false] (pr-str value))))

(defn- reject! [message data]
  (throw (ex-info message (assoc data :phase :package-authoring))))

(defn repo-rid
  "A repository's identity CID: CIDv1-raw over its canonical URL bytes.

  The same construction `kotoba-lang/kotoba`'s reference package uses
  (`mf/cidv1-raw` of \"https://github.com/kotoba-lang/kotoba\"), so a rid
  produced here is the rid that corpus already carries. It is a COMMITMENT to
  a URL, not a locator -- nothing can fetch from a rid, which is why
  `--packages` exists."
  [url]
  (mf/cidv1-raw (.from js/Buffer url "utf8")))

(defn- read-key!
  "An Ed25519 signing key as `amu keygen` writes it. Read through the same
  bounded reader as every other EDN input."
  [path]
  (let [key (support/read-edn-file! path)
        seed (or (:seed-hex key) (:kotoba.key/seed-hex key))]
    (when-not (string? seed)
      (reject! "signing key has no :seed-hex" {:path path}))
    (ed25519/unhex seed)))

(defn build-manifest
  "A self-consistent, signed manifest for the package tree at ROOT.

  The fixed point is taken in the only order that terminates: hash the content
  WITHOUT the self-cid and WITHOUT signatures, write that in as
  `:manifest-cid`, then sign that cid. Both contract checks run here before
  anything is written -- a generator that emitted a manifest it had not
  verified would be the same trust-me the manifest exists to replace."
  [{:keys [root name version kind url capabilities commit seed]}]
  (let [base {:kotoba.package/name name
              :kotoba.package/version version
              :kotoba.package/kind (or kind :library)
              :kotoba.package/repo-rid (repo-rid url)
              :kotoba.package/source
              {:git-commit commit
               :tree-cid (mf/cidv1-raw (package-lock/tree-bytes root))}
              :kotoba.package/capabilities (vec capabilities)
              :kotoba.package/dependencies []}
        cid (contract/compute-manifest-cid base)
        did (ed25519/did-key-from-pub (ed25519/pubkey-from-seed seed))
        sig (ed25519/sign seed (.encode (js/TextEncoder.) cid))
        manifest (-> base
                     (assoc-in [:kotoba.package/source :manifest-cid] cid)
                     (assoc :kotoba.package/signatures
                            [{:did did :alg :ed25519
                              :sig (.toString (.from js/Buffer sig) "base64")}]))]
    (when-let [e (contract/package-manifest-error manifest)]
      (reject! (str "generated manifest fails its own shape check: "
                    (:message e)) (:data e)))
    (when-let [e (contract/manifest-integrity-error manifest)]
      (reject! (str "generated manifest fails its own integrity check: "
                    (:message e)) (:data e)))
    manifest))

(defn- git-out [dir args]
  (let [r (.spawnSync child "git" (clj->js (into ["-C" dir] args))
                      #js {:encoding "utf8"})]
    (when (and (not (.-error r)) (zero? (or (.-status r) 1)))
      (str/trim (.-stdout r)))))

(defn package-manifest!
  "amu package-manifest <root> --name <n> --version <v> --url <git url>
                        --key key.edn [--kind :library] [--output <file>]"
  [args]
  (let [root (or (second args)
                 (support/usage-error! "package-manifest <package root> is required"))
        opt (fn [flag] (support/option args flag))
        required (fn [flag]
                   (or (opt flag)
                       (support/usage-error! (str flag " is required"))))
        commit (or (opt "--commit")
                   (git-out root ["rev-parse" "HEAD"])
                   (support/usage-error!
                    (str "--commit is required: " root " is not a git checkout, "
                         "so the commit a manifest pins cannot be read from it")))
        manifest (build-manifest
                  {:root root
                   :name (required "--name")
                   :version (required "--version")
                   :kind (when-let [k (opt "--kind")] (keyword (str/replace k #"^:" "")))
                   :url (required "--url")
                   :capabilities (map #(keyword (str/replace % #"^:" ""))
                                      (support/options args "--capability"))
                   :commit commit
                   :seed (read-key! (required "--key"))})
        output (or (opt "--output")
                   (.join node-path root package-lock/manifest-file-name))]
    (write-edn! output manifest)
    {:ok true
     :format :kotoba.package-manifest/v1
     :output output
     :package (:kotoba.package/name manifest)
     :version (:kotoba.package/version manifest)
     :manifest-cid (get-in manifest [:kotoba.package/source :manifest-cid])
     :tree-cid (get-in manifest [:kotoba.package/source :tree-cid])
     :signers (mapv :did (:kotoba.package/signatures manifest))}))

(defn- git-run [args]
  (.spawnSync child "git" (clj->js args)
              #js {:encoding "utf8" :maxBuffer 33554432}))

(defn- git-ok? [r]
  (and (not (.-error r)) (zero? (or (.-status r) 1))))

(defn fetch-package!
  "Materialise one declared package at its pinned commit under
  `<packages>/<name>/<commit>`.

  FULL HISTORY, no `--depth`. The workspace retired shallow clones
  (ADR-2607211600) because a graft boundary makes ancestry answers wrong while
  looking authoritative, and this directory is the subject of a commit check --
  a shallow checkout would still report the right HEAD, so the failure would
  not show up here; it would show up later, somewhere else, as a repository
  that cannot answer questions about itself.

  Content-addressed and idempotent: the destination is named by the commit, so
  a second run over an existing directory verifies rather than refetches. It
  verifies rather than trusting the name -- a directory NAMED after a commit is
  not evidence that it holds one."
  [packages-dir {:keys [name url commit]}]
  (let [dest (package-lock/dependency-root packages-dir
                                           {:dep/name name :dep/commit commit})]
    (if (.existsSync fs dest)
      (let [head (git-run ["-C" dest "rev-parse" "HEAD"])]
        (cond
          (not (git-ok? head))
          {:name name :commit commit :root dest :outcome :present-unverified
           :note "already present, but not a git checkout -- HEAD not checked"}

          (= commit (str/trim (.-stdout head)))
          {:name name :commit commit :root dest :outcome :already-present}

          :else
          (reject! "a different commit is already materialised at this path"
                   {:dependency name :expected commit
                    :actual (str/trim (.-stdout head)) :root dest})))
      (do
        (when-not (string? url)
          (reject! "package declares no :git/url, so it cannot be fetched"
                   {:dependency name}))
        (.mkdirSync fs (.dirname node-path dest) #js {:recursive true})
        (let [done (atom false)]
          (try
            (let [clone (git-run ["clone" "--quiet" "--no-checkout" url dest])]
              (when-not (git-ok? clone)
                (reject! "git clone failed"
                         {:dependency name :url url
                          :stderr (some-> (.-stderr clone) str/trim)})))
            (let [checkout (git-run ["-C" dest "checkout" "--quiet" commit])]
              (when-not (git-ok? checkout)
                (reject! "the pinned commit is not in the fetched repository"
                         {:dependency name :commit commit
                          :stderr (some-> (.-stderr checkout) str/trim)})))
            (reset! done true)
            (finally
              ;; `git clone` leaves its destination behind on transport
              ;; failure. Remove only the directory this call created, so a
              ;; retry is a retry and not a resume from a half-tree.
              (when-not @done
                (.rmSync fs dest #js {:recursive true :force true})))))
        {:name name :commit commit :root dest :outcome :fetched}))))

(defn package-fetch!
  "amu package-fetch --deps kotoba.deps.edn --packages <dir>

  Populate the directory `--package-lock` reads from. This is the ONLY command
  here that touches the network, and it is separate from `package-lock` on
  purpose: producing a lock and fetching what it pins are different acts with
  different trust, and a `package-lock` that fetched would make every build
  that regenerates a lock a network operation.

  It does not verify manifests or tree CIDs. `package-lock` does that when it
  reads the trees, and `--package-lock` does it again at build time. Checking
  here as well would only move where the same refusal is printed."
  [args]
  (let [deps-path (or (support/option args "--deps")
                      (support/usage-error! "--deps <kotoba.deps.edn> is required"))
        packages (or (support/option args "--packages")
                     (support/usage-error! "--packages <dir> is required"))
        declaration (support/read-edn-file! deps-path)
        packages-map (:packages declaration)]
    (when-not (map? packages-map)
      (reject! "dependency declaration has no :packages map" {:path deps-path}))
    (when (empty? packages-map)
      (reject! "dependency declaration pins nothing" {:path deps-path}))
    (.mkdirSync fs packages #js {:recursive true})
    (let [results (mapv (fn [[name spec]]
                          (fetch-package!
                           packages
                           {:name name
                            :url (:git/url spec)
                            :commit (or (:git/sha spec)
                                        (reject! "package declares no :git/sha"
                                                 {:dependency name}))}))
                        (sort-by key packages-map))]
      {:ok true
       :format :kotoba.package-fetch/v1
       :packages packages
       :fetched (mapv #(dissoc % :note) results)
       ;; "already present" and "fetched" are different facts and must not
       ;; print the same, or a run that did nothing reads like a run that
       ;; brought everything down.
       :scanned (str "SCANNED\t" (count results) " declared\tfetched="
                     (count (filter #(= :fetched (:outcome %)) results))
                     "\tpresent="
                     (count (filter #(= :already-present (:outcome %)) results))
                     "\tunverified="
                     (count (filter #(= :present-unverified (:outcome %)) results)))})))

;; ── the lock ────────────────────────────────────────────────────────────────

(defn- definition-cids-of
  "The definition CIDs a materialised package exports, computed by asking the
  compiler rather than by trusting a field. DESCRIBE is injected so this
  namespace does not pull the frontend into the load closure of commands that
  never use it."
  [describe root entries]
  (vec (sort (distinct (mapcat #(describe (.join node-path root %)) entries)))))

(defn- dep-entry
  [describe packages-dir {:keys [name version commit url entries]}]
  (let [root (package-lock/dependency-root packages-dir
                                           {:dep/name name :dep/commit commit})]
    (when-not (.existsSync fs root)
      (reject! "dependency is not materialised"
               {:dependency name :expected root}))
    (let [manifest-path (.join node-path root package-lock/manifest-file-name)
          _ (when-not (.existsSync fs manifest-path)
              (reject! "dependency carries no package manifest"
                       {:dependency name :expected manifest-path
                        :remedy "amu package-manifest <root> --name … --key …"}))
          manifest (support/read-edn-file! manifest-path)
          cids (definition-cids-of describe root entries)]
      (when (empty? cids)
        (reject! "dependency exports no definitions to pin"
                 {:dependency name :entries entries}))
      {:dep/name name
       :dep/version version
       :dep/kind (or (:kotoba.package/kind manifest) :library)
       :dep/repo-rid (:kotoba.package/repo-rid manifest)
       :dep/ref (or (:ref url) (str "refs/heads/main"))
       :dep/commit commit
       :dep/tree-cid (get-in manifest [:kotoba.package/source :tree-cid])
       :dep/manifest-cid (get-in manifest [:kotoba.package/source :manifest-cid])
       :dep/signers (mapv :did (:kotoba.package/signatures manifest))
       :dep/capabilities (vec (:kotoba.package/capabilities manifest))
       :dep/definition-cids cids})))

(defn package-lock!
  "amu package-lock --deps kotoba.deps.edn --packages <dir> --output kotoba.lock.edn

  `kotoba.deps.edn` is a tools.deps-shaped declaration extended with what a
  Kotoba package needs and a Clojure classpath does not -- which module files
  are entry points, so their definitions can be pinned:

    {:packages
     {\"kotoba-lang/demo-util\"
      {:git/url \"https://github.com/kotoba-lang/demo-util.git\"
       :git/sha \"8e2c414…\"
       :version \"0.1.0\"
       :entries [\"src/demo/util.kotoba\"]}}}

  Every field written into the lock is READ from the dependency's own signed
  manifest or COMPUTED from its tree. Nothing is copied from the declaration
  except the coordinate: a declaration that claimed a tree-cid would be
  claiming the thing the lock exists to establish."
  [describe args]
  (let [deps-path (or (support/option args "--deps")
                      (support/usage-error! "--deps <kotoba.deps.edn> is required"))
        packages (or (support/option args "--packages")
                     (support/usage-error! "--packages <dir> is required"))
        output (or (support/option args "--output") "kotoba.lock.edn")
        declaration (support/read-edn-file! deps-path)
        packages-map (:packages declaration)]
    (when-not (map? packages-map)
      (reject! "dependency declaration has no :packages map" {:path deps-path}))
    (when (empty? packages-map)
      (reject! "dependency declaration pins nothing"
               {:path deps-path
                :note "an empty lock is legal, but writing one from an empty
                       declaration hides a mistyped key"}))
    (let [deps (mapv (fn [[name spec]]
                       (dep-entry describe packages
                                  {:name name
                                   :version (or (:version spec)
                                                (reject! "package declares no :version"
                                                         {:dependency name}))
                                   :commit (or (:git/sha spec)
                                               (reject! "package declares no :git/sha"
                                                        {:dependency name}))
                                   :url (:git/url spec)
                                   :entries (or (seq (:entries spec))
                                                (reject! "package declares no :entries"
                                                         {:dependency name
                                                          :note (str "entry modules are "
                                                                     "how definitions are "
                                                                     "found to pin")}))}))
                     (sort-by key packages-map))
          lock {:kotoba.lock/version 1 :deps deps}]
      ;; Validate what was just written with the same function that will
      ;; validate it on the way in. A producer that emits a lock its own
      ;; consumer would refuse is the drift this repository has been bitten by
      ;; before.
      (when-let [e (contract/lockfile-error
                    lock (package-lock/trust-context
                          {:declared-capabilities
                           (vec (distinct (mapcat :dep/capabilities deps)))}))]
        (reject! (str "generated lock fails the contract: " (:message e))
                 (:data e)))
      (write-edn! output lock)
      {:ok true
       :format :kotoba.package-lock/v1
       :output output
       :dependencies (mapv (fn [d] {:name (:dep/name d)
                                    :version (:dep/version d)
                                    :commit (:dep/commit d)
                                    :definitions (count (:dep/definition-cids d))})
                           deps)
       :scanned (str "SCANNED\t" (count deps) " dependencies")})))
