(ns aiueos.reproducibility
  "Reproducibility evidence for a release, computed where it can be computed.

  `kotoba.security.supply-chain/evaluate-reproducibility` has been able to judge
  a release's reproducibility for as long as it has existed, and nothing in this
  repository ever called it. `docs/deployment-profiles.md` names \"reproducible,
  signed, independently verified release pipeline\" as a `:regulated`
  requirement, and `aiueos.deployment-profile` checked the signed half and not
  the reproducible half.

  That is the same shape ADR-0016 and ADR-0017 each removed once already: a
  control exists, its evaluator exists, and the evidence it consumes is a
  placeholder. An evaluator with no caller at all is the degenerate case.

  Two of the evaluator's inputs are *derived* from records this repository
  already keeps fail-closed, so they are derived rather than asserted:

  - `:dependencies` come from the TCB inventory's git entries, and each one's
    `:dependency/exact-fetch` is checked against the checkout the running JVM
    actually resolved;
  - `:local-overrides?` is measured the same way. A `:local/root` override
    replaces a dependency's git checkout on the classpath, so a pinned git
    dependency the classpath cannot account for *is* the override.

  The rest — `fresh-clone?`, `hermetic?`, and the two artifact digests — are
  claims about a build environment this namespace cannot inspect. They are
  required from the caller and never defaulted to true, for the same reason
  `aiueos.sbom/provenance` requires `isolated-builder?`: a default would make
  every local build claim the property the document exists to establish."
  (:require [aiueos.tcb :as tcb]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kotoba.security.supply-chain :as supply-chain]))

(defn gitlibs-root
  "Where `tools.deps` puts git dependency checkouts, honouring the same
  overrides it does."
  []
  (io/file (or (System/getProperty "clojure.gitlibs.dir")
               (System/getenv "GITLIBS")
               (str (System/getProperty "user.home") "/.gitlibs"))))

(defn checkout-dir
  "The checkout directory for COORDINATE at GIT-SHA
  (`<gitlibs>/libs/io.github.kotoba-lang/security/<sha>`). The commit is part
  of the path, so a checkout at a different commit is a different directory —
  which is what makes presence on the classpath evidence of an exact fetch."
  [coordinate git-sha]
  (let [[group artifact] (str/split (str coordinate) #"/")]
    (io/file (gitlibs-root) "libs" group (or artifact group) git-sha)))

(defn- classpath-entries []
  (str/split (or (System/getProperty "java.class.path") "")
             (re-pattern (java.util.regex.Pattern/quote java.io.File/pathSeparator))))

(defn resolved-at-commit?
  "True when the running classpath is actually served by COORDINATE's checkout
  at GIT-SHA. A git dependency reaches the classpath as one or more source
  directories inside that checkout, so a prefix match is the whole test."
  [coordinate git-sha]
  (let [prefix (str (.getPath (checkout-dir coordinate git-sha)) java.io.File/separator)]
    (boolean (some #(str/starts-with? % prefix) (classpath-entries)))))

(defn dependencies
  "The `:dependencies` the shared evaluator wants, from the TCB inventory's git
  entries.

  `:dependency/exact-fetch` is `:passed` only when this JVM is running on that
  exact commit's checkout. `:unresolved` covers both a local override and an
  absent checkout: from here they are the same evidence — the classpath is not
  the pinned commit — and the evaluator treats them the same way."
  ([] (dependencies (tcb/read-inventory)))
  ([inventory]
   (into []
         (for [{:keys [coordinate git-url git-sha source]} (:tcb/external inventory)
               :when (= :git source)]
           {:dependency/coordinate coordinate
            :dependency/repository git-url
            :dependency/commit git-sha
            :dependency/exact-fetch (if (resolved-at-commit? coordinate git-sha)
                                      :passed
                                      :unresolved)}))))

(defn unresolved-dependencies
  "Pinned git dependencies the classpath cannot account for at their commit."
  [deps]
  (into [] (comp (remove #(= :passed (:dependency/exact-fetch %)))
                 (map :dependency/coordinate))
        deps))

(defn evidence
  "The evaluator's input map. FRESH-CLONE? and HERMETIC? are the caller's
  assertions about its build environment and are coerced with `true?`, so
  omitting one is a `false` claim rather than a silent pass."
  [{:keys [source-commit first-artifact-digest second-artifact-digest
           fresh-clone? hermetic? inventory]}]
  (let [deps (dependencies (or inventory (tcb/read-inventory)))]
    {:fresh-clone? (true? fresh-clone?)
     :hermetic? (true? hermetic?)
     :local-overrides? (boolean (seq (unresolved-dependencies deps)))
     :source-commit source-commit
     :first-artifact-digest first-artifact-digest
     :second-artifact-digest second-artifact-digest
     :dependencies deps}))

(defn qualify
  "Judge a release's reproducibility with the shared evaluator."
  [options]
  (supply-chain/evaluate-reproducibility (evidence options)))

(defn profile-evidence
  "The reproducibility half of `aiueos.deployment-profile`'s `:regulated`
  evidence. The violation list travels with the verdict so a refusal names its
  cause instead of only reporting a false."
  [qualification]
  {:reproducibility-qualified? (true? (:reproducibility/qualified? qualification))
   :reproducibility-artifact-digest (:reproducibility/artifact-digest qualification)
   :reproducibility-violations (vec (:reproducibility/violations qualification))})

(defn -main
  "Judge two independent builds of the same source commit.

      clojure -M:reproduce <source-commit> <digest-1> <digest-2> [--fresh-clone] [--hermetic]

  The digests are two release build receipts' `disk.sha256` as `sha256:<hex>`,
  from builds that must not share a working tree. `--fresh-clone` and
  `--hermetic` are assertions about the environment that produced them; a build
  that cannot truthfully make one must omit it and read the resulting
  violation."
  [& [source-commit first-digest second-digest & flags]]
  (if-not (and source-commit first-digest second-digest)
    (do (println "usage: <source-commit> <digest-1> <digest-2> [--fresh-clone] [--hermetic]")
        (System/exit 2))
    (let [result (qualify {:source-commit source-commit
                           :first-artifact-digest first-digest
                           :second-artifact-digest second-digest
                           :fresh-clone? (boolean (some #{"--fresh-clone"} flags))
                           :hermetic? (boolean (some #{"--hermetic"} flags))})]
      (prn result)
      (when-not (:reproducibility/qualified? result)
        (System/exit 1)))))
