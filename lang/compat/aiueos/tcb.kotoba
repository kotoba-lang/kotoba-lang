(ns aiueos.tcb
  "Machine-verifiable trusted-computing-base inventory.

  Two halves, both fail-closed:

  - **in-repository sources** (`:tcb/files`) are pinned by SHA-256 of the file
    itself, so changing trusted code without an inventory update fails;
  - **external dependencies** (`:tcb/external`) are pinned by *content*, not by
    a path or a bare version string, and cross-checked against the two other
    records of the same fact (`deps.edn`, `security-adoption.edn`) so the three
    cannot silently disagree.

  A Maven dependency's content address is the SHA-256 of its resolved jar; a git
  dependency's is its commit id (git's own content address for the tree). An
  entry that is neither content-addressable nor carrying an explicit
  `:assurance-gap` is an error — an unpinned dependency may not pass silently."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(def inventory-path "qualification/tcb-inventory.edn")
(def deps-path "deps.edn")
(def adoption-path "security-adoption.edn")
(def build-identity-path "qualification/build-identity.edn")

(def inventory-version
  "The only inventory shape this checker admits. Version 3 added
  `:tcb/classpath`, the transitive closure; version 2 added content addressing
  for `:tcb/external`; version 1 recorded it as unverified prose."
  3)

(def security-coordinate "io.github.kotoba-lang/security")

(def external-sources
  "`:source` values an external entry may declare.

  `:platform` is the host runtime (`java.base`), which this repository cannot
  content-address from inside itself — such an entry must carry an explicit
  `:assurance-gap` instead."
  #{:maven :git :platform})

(defn- hex [^bytes value]
  (apply str (map #(format "%02x" (bit-and (int %) 0xff)) value)))

(defn sha256-file [path]
  (with-open [input (io/input-stream path)]
    (let [digest (MessageDigest/getInstance "SHA-256")
          buffer (byte-array 16384)]
      (loop []
        (let [read (.read input buffer)]
          (when (pos? read)
            (.update digest buffer 0 read)
            (recur))))
      (hex (.digest digest)))))

(defn read-inventory []
  (edn/read-string (slurp inventory-path)))

(defn- read-edn-file [path]
  (let [file (io/file path)]
    (when (.exists file)
      (edn/read-string (slurp file)))))

(defn- maven-local-repo
  "The local Maven repository the running JVM resolved its jars from."
  [deps]
  (io/file (or (System/getProperty "maven.repo.local")
               (:mvn/local-repo deps)
               (str (System/getProperty "user.home") "/.m2/repository"))))

(defn maven-jar
  "The local-repository jar for COORDINATE at VERSION, in Maven's
  group-as-directories layout (`com.dylibso.chicory/wasm` 1.4.0 ->
  `com/dylibso/chicory/wasm/1.4.0/wasm-1.4.0.jar`)."
  [repo coordinate version]
  (let [[group artifact] (str/split (str coordinate) #"/")
        artifact (or artifact group)]
    (apply io/file repo (concat (str/split group #"\.")
                                [artifact version (str artifact "-" version ".jar")]))))

(defn- declared-dependencies
  "`deps.edn`'s `:deps` keyed by coordinate string. Alias dependencies are out
  of scope on purpose: `:tcb/excluded` already excludes tests and qualification
  fixtures, which is where they are used."
  [deps]
  (into {} (map (fn [[coordinate coords]] [(str coordinate) coords])) (:deps deps)))

(defn- maven-entry-errors [repo {:keys [coordinate version sha256]} declared-dep]
  (cond
    (not= version (:mvn/version declared-dep))
    [{:kind :external-version-drift :coordinate coordinate
      :expected version :actual (:mvn/version declared-dep)}]

    (not (string? sha256))
    [{:kind :external-unpinned :coordinate coordinate}]

    :else
    (let [jar (maven-jar repo coordinate version)]
      (cond
        (not (.exists jar))
        [{:kind :external-artifact-missing :coordinate coordinate
          :path (.getPath jar)}]

        (not= sha256 (sha256-file jar))
        [{:kind :external-digest-drift :coordinate coordinate
          :expected sha256 :actual (sha256-file jar)}]

        :else []))))

(defn- git-entry-errors [{:keys [coordinate git-sha]} declared-dep]
  (cond
    (not (string? git-sha))
    [{:kind :external-unpinned :coordinate coordinate}]

    (not= git-sha (:git/sha declared-dep))
    [{:kind :external-git-sha-drift :coordinate coordinate
      :expected git-sha :actual (:git/sha declared-dep)}]

    :else []))

(defn- entry-errors
  [repo declared {:keys [coordinate role source assurance-gap] :as entry}]
  (let [declared-dep (get declared coordinate)]
    (cond
      (not (keyword? role))
      [{:kind :external-missing-role :coordinate coordinate}]

      (not (contains? external-sources source))
      [{:kind :external-unknown-source :coordinate coordinate :source source}]

      (= :platform source)
      (if (keyword? assurance-gap)
        []
        [{:kind :external-unpinned :coordinate coordinate}])

      (nil? declared-dep)
      [{:kind :external-not-in-deps :coordinate coordinate}]

      (= :maven source) (maven-entry-errors repo entry declared-dep)
      :else (git-entry-errors entry declared-dep))))

(defn- adoption-errors
  "`security-adoption.edn` records the shared security package's commit for the
  adoption gate; the inventory records it for the TCB. They are the same fact,
  so a disagreement is an error rather than two independent truths."
  [entries adoption]
  (let [pinned (:security/git-sha adoption)
        recorded (some #(when (= security-coordinate (:coordinate %)) (:git-sha %))
                       entries)]
    (if (and (string? pinned) (string? recorded) (not= pinned recorded))
      [{:kind :external-adoption-drift :coordinate security-coordinate
        :expected recorded :actual pinned}]
      [])))

(defn- external-errors [inventory deps adoption]
  (let [declared (declared-dependencies deps)
        repo (maven-local-repo deps)
        entries (:tcb/external inventory)
        covered (into #{} (map :coordinate) entries)]
    (concat
     (mapcat #(entry-errors repo declared %) entries)
     ;; A runtime dependency that never reached the inventory is the drift the
     ;; per-entry checks above cannot see.
     (for [coordinate (sort (keys declared))
           :when (not (contains? covered coordinate))]
       {:kind :external-undeclared :coordinate coordinate})
     (adoption-errors entries adoption))))

;; --- platform floor --------------------------------------------------------
;;
;; `java.base` carries `:minimum-version` and an `:assurance-gap`, because a
;; host runtime cannot be content-addressed from inside this repository. That
;; is honest about what cannot be pinned, but it left the *declared* floor
;; unchecked against the one place in the repository that provisions a JDK, and
;; the two disagreed: the floor says 25 and `.github/workflows/ci.yml`
;; provisions temurin 21.
;;
;; Which of the two is wrong is a decision, not a fact this namespace can
;; derive, so the check does not pick a side and does not turn CI red for
;; having the disagreement. It requires the disagreement to be *recorded
;; accurately while it exists* — and to be removed once it stops existing.
;; Either resolution flips the same bit, so the record cannot rot into a stale
;; comment about a contradiction someone already fixed.

(def workflow-path ".github/workflows/ci.yml")

(defn provisioned-java-versions
  "Major versions provisioned by `actions/setup-java` steps in the CI
  workflow, or nil when the workflow is not readable from here.

  Read with a regex rather than a YAML parser on purpose: the value wanted is
  one scalar under a fixed key, and adding a YAML dependency to satisfy it
  would put a parser in the TCB this inventory exists to bound."
  ([] (provisioned-java-versions workflow-path))
  ([path]
   (let [file (io/file path)]
     (when (.exists file)
       (->> (re-seq #"(?m)^\s*java-version:\s*\"?(\d+)" (slurp file))
            (map (comp parse-long second))
            distinct
            sort
            vec)))))

(defn- platform-floor-errors [inventory]
  (let [entry (first (filter #(and (= :platform (:source %)) (:minimum-version %))
                             (:tcb/external inventory)))
        provisioned (provisioned-java-versions)]
    (when (and entry provisioned)
      (let [floor (:minimum-version entry)
            unmet (vec (remove #(>= % floor) provisioned))
            recorded (:floor-unmet-by-ci entry)]
        (cond
          (empty? provisioned)
          [{:kind :platform-floor-unmeasurable
            :coordinate (:coordinate entry) :workflow workflow-path}]

          (and (seq unmet) (nil? recorded))
          [{:kind :platform-floor-contradiction-unrecorded
            :coordinate (:coordinate entry) :minimum-version floor
            :provisioned unmet :workflow workflow-path}]

          (and (empty? unmet) (some? recorded))
          [{:kind :platform-floor-contradiction-stale
            :coordinate (:coordinate entry) :minimum-version floor
            :provisioned provisioned :recorded recorded}]

          (and (seq unmet)
               (not= unmet (vec (:provisioned recorded))))
          [{:kind :platform-floor-contradiction-drift
            :coordinate (:coordinate entry)
            :recorded (:provisioned recorded) :actual unmet}]

          :else nil)))))

;; --- transitive closure ----------------------------------------------------
;;
;; `:tcb/external` records what this repository *declares*. It cannot see what
;; those declarations drag in: `org.clojure/clojure`, `spec.alpha` and
;; `core.specs.alpha` are on every runtime classpath here and are named by no
;; declaration. `:tcb/classpath` records the jars that are actually loaded,
;; which is the closure `deps.edn` alone cannot express.

(defn classpath-jars
  "Every jar on the running JVM's classpath, as `{:jar <file-name> :path
  <absolute>}`, sorted by name. Directories are excluded: git dependencies and
  this repository's own `src` reach the classpath as source directories, and
  `:tcb/files` / `:tcb/external` already own those."
  []
  (->> (str/split (or (System/getProperty "java.class.path") "")
                  (re-pattern (java.util.regex.Pattern/quote java.io.File/pathSeparator)))
       (filter #(str/ends-with? % ".jar"))
       (map (fn [path] {:jar (.getName (io/file path)) :path path}))
       (sort-by :jar)
       vec))

(defn- classpath-errors
  "A jar on the classpath and not in the inventory is an unrecorded member of
  the TCB's closure; a recorded jar whose bytes changed is drift.

  The reverse — recorded but absent — is deliberately *not* an error. The check
  runs under more than one alias (`:tcb-check` loads five jars, `:test` nine),
  so a narrower classpath is normal. Requiring a bijection would force the
  inventory to describe one alias and fail under the other, which teaches
  people to skip the gate."
  [inventory]
  (let [recorded (into {} (map (juxt :jar identity)) (:tcb/classpath inventory))]
    (mapcat
     (fn [{:keys [jar path]}]
       (if-let [entry (get recorded jar)]
         (let [actual (sha256-file (io/file path))]
           (cond
             (not (keyword? (:role entry)))
             [{:kind :classpath-missing-role :jar jar}]
             (not= (:sha256 entry) actual)
             [{:kind :classpath-digest-drift :jar jar
               :expected (:sha256 entry) :actual actual}]
             :else []))
         [{:kind :classpath-unrecorded :jar jar}]))
     (classpath-jars))))

;; --- adopted build properties ----------------------------------------------
;;
;; `qualification/build-identity.edn` names the supply-chain properties this
;; repository holds on purpose. A list of properties is worth no more than the
;; `:tcb/external` list was before it was checked, so the same rule applies:
;; every claim must name a mechanism that exists and either a gate that enforces
;; it or an explicit gap. A property with neither is a claim nothing carries.

(def build-identity-version 1)

(defn- property-errors [tcb-paths {:keys [property statement mechanism gate assurance-gaps]}]
  (cond
    (not (keyword? property)) [{:kind :property-missing-name}]
    (not (and (string? statement) (not (str/blank? statement))))
    [{:kind :property-missing-statement :property property}]
    (empty? mechanism) [{:kind :property-without-mechanism :property property}]
    (and (empty? gate) (empty? assurance-gaps))
    ;; The whole point: an unenforced property must say so rather than read as
    ;; an established one.
    [{:kind :property-unenforced-and-ungapped :property property}]
    (not (every? keyword? assurance-gaps))
    [{:kind :property-malformed-gap :property property}]
    :else
    (concat
     (for [path (concat mechanism gate)
           :when (not (.exists (io/file path)))]
       {:kind :property-path-missing :property property :path path})
     ;; A mechanism inside `src/` is trusted code. Leaving it out of
     ;; `:tcb/files` would let the implementation of a declared property change
     ;; without the review the inventory exists to force.
     (for [path mechanism
           :when (and (str/starts-with? path "src/")
                      (not (contains? tcb-paths path)))]
       {:kind :property-mechanism-not-in-tcb :property property :path path}))))

(defn validate-build-identity
  "Errors in the adopted-property record, cross-checked against the checked-in
  TCB inventory.

  Both arguments default to the documents on disk, because this check is about
  two *records* agreeing with each other — the same relationship
  `security-adoption.edn` has with the inventory — rather than about whatever
  inventory value a caller happens to be validating."
  ([] (validate-build-identity (read-edn-file build-identity-path)))
  ([document] (validate-build-identity document (read-inventory)))
  ([document inventory]
   (let [tcb-paths (into #{} (map :path) (:tcb/files inventory))]
     (cond
       (nil? document) [{:kind :build-identity-missing :path build-identity-path}]

       (not= build-identity-version (:build-identity/version document))
       [{:kind :build-identity-unsupported-version
         :actual (:build-identity/version document)}]

       (empty? (:adopted document)) [{:kind :build-identity-empty}]

       :else
       (concat
        (mapcat #(property-errors tcb-paths %) (:adopted document))
        (for [{:keys [property reason]} (:non-goals document)
              :when (not (and (keyword? property)
                              (string? reason)
                              (not (str/blank? reason))))]
          {:kind :non-goal-without-reason :property property}))))))

(defn- file-errors [files]
  (mapcat
   (fn [{:keys [path role sha256]}]
     (let [file (io/file path)]
       (cond
         (not (.exists file))
         [{:kind :missing-file :path path}]
         (not (keyword? role))
         [{:kind :missing-role :path path}]
         (not= sha256 (sha256-file file))
         [{:kind :digest-drift :path path
           :expected sha256 :actual (sha256-file file)}]
         :else [])))
   files))

(defn validate
  "Return structured drift/errors. An inventory update must be an intentional
  review action; changing trusted code — in this repository or in a dependency
  — without updating its content address fails."
  ([] (validate (read-inventory)))
  ([inventory]
   (validate inventory (read-edn-file deps-path) (read-edn-file adoption-path)))
  ([inventory deps adoption]
   (let [files (:tcb/files inventory)
         paths (mapv :path files)
         errors
         (into []
               (concat
                (when-not (= inventory-version (:tcb/version inventory))
                  [{:kind :unsupported-version
                    :actual (:tcb/version inventory)}])
                (when-not (= (count paths) (count (set paths)))
                  [{:kind :duplicate-path}])
                (file-errors files)
                (external-errors inventory deps adoption)
                (platform-floor-errors inventory)
                (classpath-errors inventory)
                (validate-build-identity)))]
     {:valid? (empty? errors)
      :files (count files)
      :external (count (:tcb/external inventory))
      :classpath (count (:tcb/classpath inventory))
      :properties (count (:adopted (read-edn-file build-identity-path)))
      :errors errors})))

(defn print-classpath
  "Print the `:tcb/classpath` entries for the current classpath, for pasting
  into the inventory after review. Roles are a human judgement about what the
  jar can do, so they are not generated — a fresh jar prints `:role nil` and
  the reviewer names it."
  []
  (let [recorded (into {} (map (juxt :jar identity))
                       (:tcb/classpath (read-inventory)))]
    (doseq [{:keys [jar path]} (classpath-jars)]
      (prn {:jar jar
            :role (get-in recorded [jar :role])
            :scope (get-in recorded [jar :scope])
            :sha256 (sha256-file (io/file path))}))))

(defn -main [& args]
  (if (= "classpath" (first args))
    (print-classpath)
    (let [result (validate)]
      (prn (dissoc result :errors))
      (doseq [error (:errors result)] (prn error))
      (when-not (:valid? result)
        (System/exit 1)))))
