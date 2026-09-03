#!/usr/bin/env nbb
;; The cross-repository question no repository-local check can answer.
;;
;; Every consumer pins a digest and compares its own copy against it, so every
;; consumer is green while being at a different digest from every other. That
;; is not a hypothetical: measured 2026-09-03, hours after a coordinated pass
;; had brought all seven copies of `lang/guest-grammar.edn` to one digest,
;; there were THREE digests across five repositories, and every repository's
;; own suite was green. A local check cannot see this. It compares what is on
;; its own disk to a number it also carries, and a copy and a constant are
;; consistent with each other at any value.
;;
;; `local-and-sibling-vendors-match-authority` was written to look across
;; repositories, and cannot: its sibling paths exist only in the west monorepo
;; layout, each is guarded, and an absent one is `:missing` and tolerated. In a
;; single-repository clone it compares one file -- this repository's own copy
;; of itself -- and reports green (ADR-2608136000: a check that could not run
;; returning the value of a check that ran and found nothing wrong).
;;
;; This asks GitHub for each consumer's DEFAULT BRANCH. That answer does not
;; depend on what happens to be checked out, which is also what makes the
;; converse assertion possible: a deferral naming a copy that is actually in
;; sync is a finding here, where locally it could only ever be a printed note.
;;
;; EXIT CODES
;;   0  every copy is the authority, or differs and is recorded as deferred
;;   1  findings: undeferred drift, a stale deferral, a retired copy that came
;;      back, a deferral naming no registered copy, an absent registered copy
;;   2  COULD NOT MEASURE -- at least one copy was unreachable. Distinct from
;;      both of the above on purpose: a run that could not ask must not answer
;;      with the value of a run that asked and found nothing wrong.
;;
;; USAGE
;;   nbb scripts/check-vendored-copies-fleet.cljs [--registry <path>] [--json]

(ns check-vendored-copies-fleet
  (:require ["node:child_process" :as cp]
            ["node:crypto" :as crypto]
            ["node:fs" :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def argv (vec (drop 2 (js->clj (.-argv js/process)))))

(defn- arg [flag default]
  (let [i (.indexOf argv flag)]
    (if (neg? i) default (nth argv (inc i) default))))

(def registry-path (arg "--registry" "lang/vendored-copies.edn"))
(def json? (some #{"--json"} argv))

(defn- die! [code msg]
  (println msg)
  (.exit js/process code))

(defn- sha256 [buf]
  (-> (.createHash crypto "sha256") (.update buf) (.digest "hex")))

(def ^:private gh-available?
  (try
    (cp/execFileSync "gh" #js ["--version"] #js {:stdio "ignore"})
    true
    (catch :default _ false)))

(defn- fetch-gh
  "{:status :present/:absent/:unreachable :sha ... :route \"gh\"}"
  [repo path]
  (try
    (let [buf (cp/execFileSync "gh"
                               #js ["api" (str "repos/" repo "/contents/" path)
                                    "-H" "Accept: application/vnd.github.raw"]
                               #js {:maxBuffer 33554432
                                    :stdio #js ["ignore" "pipe" "pipe"]})]
      {:status :present :sha (sha256 buf) :bytes (.-length buf) :route "gh"})
    (catch :default e
      (let [err (str (some-> (.-stderr e) .toString))]
        (if (or (str/includes? err "404") (str/includes? err "Not Found"))
          {:status :absent :route "gh"}
          {:status :error :detail (str/trim err) :route "gh"})))))

(defn- fetch-raw [repo path]
  (let [tmp (str (or (.-TMPDIR (.-env js/process)) "/tmp")
                 "/vendored-copy-" (.now js/Date) "-" (rand-int 1e9))]
    (try
      (let [code (-> (cp/execFileSync
                      "curl" #js ["-sS" "-o" tmp "-w" "%{http_code}"
                                  (str "https://raw.githubusercontent.com/"
                                       repo "/HEAD/" path)]
                      #js {:stdio #js ["ignore" "pipe" "pipe"]})
                     .toString str/trim)]
        (cond
          (= "200" code) (let [buf (.readFileSync fs tmp)]
                           {:status :present :sha (sha256 buf)
                            :bytes (.-length buf) :route "raw"})
          (= "404" code) {:status :absent :route "raw"}
          :else {:status :error :detail (str "HTTP " code) :route "raw"}))
      (catch :default e
        {:status :error :detail (str/trim (str (or (some-> (.-stderr e) .toString)
                                                   (.-message e))))
         :route "raw"})
      (finally
        (try (.unlinkSync fs tmp) (catch :default _ nil))))))

(defn- fetch-copy
  "Ask the fresher route first. A route that could not answer falls through to
  the other; only when BOTH fail is the copy unreachable, and that is exit 2."
  [repo path]
  (let [a (if gh-available? (fetch-gh repo path) {:status :error :route "gh" :detail "gh not installed"})]
    (if (= :error (:status a))
      (let [b (fetch-raw repo path)]
        (if (= :error (:status b))
          (assoc b :status :unreachable
                 :detail (str "gh: " (:detail a) " | raw: " (:detail b)))
          b))
      a)))

;; --------------------------------------------------------------------------

(def registry
  (try
    (edn/read-string (.readFileSync fs registry-path "utf8"))
    (catch :default e
      (die! 2 (str "REFUSED\tcannot read " registry-path ": " (.-message e)
                   "\nA run that could not read the registry has measured nothing.")))))

(def authority-repo (:kotoba.lang.vendored-copies/authority registry))
(def copies (vec (:copies registry)))
(def retired (vec (:retired registry)))
(def deferrals (or (:deferrals registry) {}))

(defn- copy-key [{:keys [repo path]}] (str repo " " path))

(when (empty? copies)
  (die! 2 "REFUSED\tthe registry names no copies; nothing to compare"))

(def authorities (vec (distinct (map :authority copies))))

(println (str "REGISTRY\t" registry-path "\tauthority " authority-repo
              "\troute " (if gh-available? "gh (raw fallback)" "raw only")))

;; ---- the authority side ---------------------------------------------------
(def authority-digests
  (into {}
        (map (fn [a]
               (let [r (fetch-copy authority-repo a)]
                 [a r])))
        authorities))

(def unreachable-authorities
  (vec (for [[a r] authority-digests
             :when (not= :present (:status r))]
         [a (:status r) (:detail r)])))

;; ---- every registered copy ------------------------------------------------
(def results
  (mapv (fn [c]
          (let [r (fetch-copy (:repo c) (:path c))
                auth (get authority-digests (:authority c))
                verdict (cond
                          (not= :present (:status r)) (:status r)
                          (not= :present (:status auth)) :authority-unreachable
                          (= (:sha r) (:sha auth)) :in-sync
                          ;; NOT `:behind`. A digest comparison cannot say which
                          ;; side moved. Measured 2026-09-03: kotoba-sema's copy
                          ;; of capability-catalog.edn differed because the COPY
                          ;; was edited -- two capability ids added to a
                          ;; vendored authority file in the consumer repo -- so
                          ;; a verdict of "behind" would have named the wrong
                          ;; repository as the one that owed a resync.
                          :else :diverged)]
            (assoc c :result r :verdict verdict
                   :authority-sha (:sha auth)
                   :deferred? (contains? deferrals (copy-key c)))))
        copies))

;; ---- retired copies must stay retired -------------------------------------
(def retired-checked
  (vec (for [r retired
             :when (and (:repo r) (:path r))]
         (assoc r :result (fetch-copy (:repo r) (:path r))))))

;; ---- report ---------------------------------------------------------------
(defn- short [s] (if s (subs s 0 8) "--------"))

(println "")
(println (str/join "\t" ["VERDICT" "DIGEST" "AUTHORITY" "REPO" "PATH"]))
(doseq [r results]
  (println (str/join "\t" [(str (when (:deferred? r) "DEFERRED/")
                                (name (:verdict r)))
                           (short (get-in r [:result :sha]))
                           (short (:authority-sha r))
                           (:repo r)
                           (:path r)])))

(def measured (filterv #(= :present (get-in % [:result :status])) results))
(def unreachable (filterv #(= :unreachable (:verdict %)) results))
(def absent (filterv #(= :absent (:verdict %)) results))
(def diverged (filterv #(= :diverged (:verdict %)) results))
(def in-sync (filterv #(= :in-sync (:verdict %)) results))

(println "")
;; EVIDENCE FLOOR. `measured` counts copies whose bytes were actually read.
;; A run that read none must not read as a run that found nothing wrong, so it
;; exits 2 below rather than 0.
(println (str "COMPARED\t" (count measured) "/" (count copies)
              "\tregistered copies fetched and hashed"))
(println (str "SCANNED\t" (count authorities) "\tauthority files in "
              authority-repo))
(println (str "DEFERRED\t" (count deferrals)
              "\tcopies recorded as knowingly behind, by name"))
(println (str "RETIRED\t" (count retired-checked)
              "\tpaths checked for a copy that came back"))

(def findings (atom []))
(defn- finding! [code & parts]
  (swap! findings conj (str code "\t" (str/join "\t" parts))))

;; 1. drift that nobody recorded
(doseq [r diverged
        :when (not (:deferred? r))]
  (finding! "DRIFT" (:repo r) (:path r)
            (str (short (get-in r [:result :sha])) " != authority "
                 (short (:authority-sha r)))
            "resync it, or record it under :deferrals with a date, a reason and what closes it -- and read the diff before assuming the COPY is the side that moved"))

;; 2. THE CONVERSE. A deferral cannot name a copy that is actually in sync.
;;    This is the assertion a local run cannot make, because locally the
;;    sibling's state depends on which revision happens to be checked out.
(doseq [r in-sync
        :when (:deferred? r)]
  (finding! "STALE-DEFERRAL" (:repo r) (:path r)
            (str "in sync at " (short (get-in r [:result :sha])))
            "delete the :deferrals entry; a record must not outlive what it excuses"))

;; 3. ...and it cannot name something the registry does not list at all.
(let [known (into #{} (map copy-key) copies)]
  (doseq [k (sort (keys deferrals))
          :when (not (contains? known k))]
    (finding! "UNKNOWN-DEFERRAL" k
              "names no registered copy; a deferral for a path nothing compares excuses nothing")))

;; 4. every deferral carries all four fields; a bare skip list is refused
(doseq [[k v] (sort-by key deferrals)]
  (let [missing (remove #(contains? v %) [:as-of :reason :closes-when])]
    (when (seq missing)
      (finding! "BARE-DEFERRAL" k (str "missing " (pr-str (vec missing)))))))

;; 5. a retired copy that came back
(doseq [r retired-checked]
  (case (get-in r [:result :status])
    :present (finding! "RETIRED-COPY-RETURNED" (:repo r) (:path r)
                       (str "retired " (:retired r))
                       (or (:refused-by r) "no local refusal is registered for this path"))
    :unreachable (finding! "UNVERIFIED-RETIREMENT" (:repo r) (:path r)
                           (get-in r [:result :detail]))
    nil))

;; 6. a registered copy that is not there is a stale registry, not a pass
(doseq [r absent]
  (finding! "ABSENT" (:repo r) (:path r)
            "registered as a copy but no such file on the default branch; delete the entry or move it to :retired"))

(def could-not-measure
  (or (seq unreachable) (seq unreachable-authorities)
      (zero? (count measured))))

(doseq [[a status detail] unreachable-authorities]
  (println (str "UNVERIFIED\tauthority " a "\t" (name status) "\t" detail)))
(doseq [r unreachable]
  (println (str "UNVERIFIED\t" (:repo r) "\t" (:path r) "\t"
                (get-in r [:result :detail]))))

(when json?
  (println (js/JSON.stringify
            (clj->js {:compared (count measured) :registered (count copies)
                      :findings @findings
                      :rows (mapv #(hash-map :repo (:repo %) :path (:path %)
                                             :verdict (name (:verdict %))
                                             :deferred (boolean (:deferred? %))
                                             :sha (get-in % [:result :sha]))
                                  results)})
            nil 2)))

(println "")
(cond
  could-not-measure
  (die! 2 (str "COULD NOT MEASURE\t" (count unreachable) " copies and "
               (count unreachable-authorities) " authority files were unreachable.\n"
               "Refusing to report a pass on copies that were never compared."))

  (seq @findings)
  (do (doseq [f @findings] (println f))
      (die! 1 (str "FINDINGS\t" (count @findings)
                   "\tacross " (count copies) " registered copies")))

  :else
  (die! 0 (str "PASS\t" (count in-sync) " in sync, " (count diverged)
               " diverged and deferred, across " (count copies)
               " registered copies of " (count authorities) " authorities")))
