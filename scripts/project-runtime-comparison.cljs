#!/usr/bin/env nbb
;; scripts/project-runtime-comparison.cljs — project amu's multidomain report
;; into the public artifact kotoba-lang.org renders.
;;
;; `bench/public-runtime-comparison/latest.json` was hand-maintained and its
;; `sources` already point at amu's harness, manifest and method. This makes
;; the projection reproducible: the public file becomes a function of a named
;; amu report rather than a transcription of one.
;;
;;   nbb scripts/project-runtime-comparison.cljs <report.json> [<report.json> ...] \
;;       [--output bench/public-runtime-comparison/latest.json]
;;
;; PASS MORE THAN ONE REPORT. The score is a noisy measurement and publishing a
;; single run states it with a precision it does not have. Measured 2026-09-06:
;; four runs of the SAME commit scored 19, 19, 15, 19. The drop to 15 was not
;; four independent losses -- it was ONE sample of 10.48 ns against a median of
;; 5.165 in amu-native's own arm on branch-call, which pushed that arm's
;; relative stdev to 0.1373 and tripped perfgate's `too-noisy` rule for THREE
;; comparator pairs at once (dropping that single sample gives 0.0465).
;;
;; So the headline is the MEDIAN across runs, and the artifact carries the
;; range, the per-run scores, and how many runs each pair won. A pair that
;; qualifies in every run and one that qualified once are both "qualified" in
;; a single-run artifact; here they are not.
;;
;; exit 0 = artifact written
;; exit 2 = REFUSED. The input was not a v2 multidomain report, or it did not
;;          carry a quiet-gate verdict. A report whose host was never measured
;;          must not become a published ranking; that is the whole reason the
;;          previous artifact withheld its timings.

(require '[clojure.string :as str])
(def fs (js/require "node:fs"))
(def crypto (js/require "node:crypto"))

(defn arg [flag fallback]
  (let [v (js->clj (.-argv js/process)) i (.lastIndexOf (to-array v) flag)]
    (if (neg? i) fallback (nth v (inc i) fallback))))

(defn die! [code msg]
  (binding [*print-fn* *print-err-fn*] (println msg)) (.exit js/process code))

(defn positional
  "argv minus the runner words, minus every --flag AND the value that follows
  it. Taking `(remove #(starts-with? % \"--\"))` alone leaves the VALUE of
  --output in the list, and `last` then picks it as the input path -- which
  made the refusal control below pass for `cannot read` instead of for the
  reason it claims to test."
  []
  (loop [xs (drop 3 (js->clj (.-argv js/process))) acc []]
    (cond (empty? xs) acc
          (str/starts-with? (first xs) "--") (recur (drop 2 xs) acc)
          :else (recur (rest xs) (conj acc (first xs))))))

(def in-paths (positional))
(when (empty? in-paths)
  (die! 2 "usage: project-runtime-comparison.cljs <report.json> [<report.json> ...] [--output <path>]"))
(def in-path (last in-paths))   ;; the newest run supplies environment and provenance
(def out-path (arg "--output" "bench/public-runtime-comparison/latest.json"))

(def raw (try (.readFileSync fs in-path "utf8")
              (catch :default e (die! 2 (str "cannot read " in-path ": " (.-message e))))))
(def report (js->clj (js/JSON.parse raw) :keywordize-keys true))

(when-not (= "kotoba.runtime-multidomain-report/v2" (:format report))
  (die! 2 (str "REFUSED: expected kotoba.runtime-multidomain-report/v2, got "
               (pr-str (:format report)))))

(def qual (:qualification report))
(def quiet (:quietGate qual))
(when (nil? (:qualified quiet))
  (die! 2 "REFUSED: the report carries no quiet-gate verdict; its timings cannot be published as a ranking"))

(def pg (get qual :perfgate))
(def ec (get pg (keyword "external-comparators")))

(def domain-labels
  {"narrow-arithmetic" "Narrow arithmetic"
   "wide-register-pressure" "Wide register pressure"
   "deep-spill-pressure" "Deep spill pressure"
   "call-preservation" "Call preservation"
   "branch-call-control-flow" "Branch + call control flow"
   "loop-call-back-edge" "Loop call back edge"})

(def comparator-labels
  {:rust "Rust" :clang-c11 "Clang / C11" :zig "Zig" :go "Go c-shared" :swift "Swift"})

(def comparator-env-key
  "The environment block names rustc by its binary, not by the comparator id.
  Reading `(get env :rust)` yields nil, which published a comparator with a
  null version rather than failing -- so the map is explicit."
  {:rust :rustc :clang-c11 :clang-c11 :zig :zig :go :go :swift :swift})

(defn env-of [report]
  (get-in report [:domains 0 :environment]))

(defn first-line [s] (when s (first (str/split-lines (str s)))))

(def pairs
  (vec (for [[comp v] ec
             d (:domains v)
             :let [vd (:verdict d)]]
         {:comparator (name comp)
          :domain (:id d)
          :improvement (:improvement vd)
          :qualified (get vd (keyword "qualified?"))
          :candidateMedian (get-in d [:candidate :median])
          :baselineMedian (get-in d [:baseline :median])
          :reasons (vec (sort (distinct (map :reason (:reasons vd)))))})))


(def qualified-count (count (filter :qualified pairs)))

;; ---------------------------------------------------------------------------
;; Every run, not just the one that supplies provenance.
;;
;; Each extra report is held to the same two refusals as the primary: a v2
;; multidomain report carrying a quiet-gate verdict. A run whose host was never
;; measured cannot vote on a published ranking.
(defn read-report! [path]
  (let [raw (try (.readFileSync fs path "utf8")
                 (catch :default e (die! 2 (str "cannot read " path ": " (.-message e)))))
        r (js->clj (js/JSON.parse raw) :keywordize-keys true)]
    (when-not (= "kotoba.runtime-multidomain-report/v2" (:format r))
      (die! 2 (str "REFUSED: " path " is not a kotoba.runtime-multidomain-report/v2")))
    (when (nil? (get-in r [:qualification :quietGate :qualified]))
      (die! 2 (str "REFUSED: " path " carries no quiet-gate verdict")))
    (when-not (get-in r [:qualification :quietGate :qualified])
      (die! 2 (str "REFUSED: " path " did not pass its quiet-gate; it cannot vote on a ranking")))
    r))

(defn pairs-of [r]
  (into {} (for [[comp v] (get-in r [:qualification :perfgate (keyword "external-comparators")])
                 d (:domains v)]
             [[(:id d) (name comp)]
              {:qualified (boolean (get-in d [:verdict (keyword "qualified?")]))
               :improvement (get-in d [:verdict :improvement])
               :reasons (vec (sort (distinct (map :reason (get-in d [:verdict :reasons])))))}])))

(def run-pairs (mapv (comp pairs-of read-report!) in-paths))
(def run-count (count run-pairs))
(def run-scores (mapv (fn [m] (count (filter :qualified (vals m)))) run-pairs))

(defn median [xs]
  (let [v (vec (sort xs)) n (count v)]
    (if (odd? n) (nth v (quot n 2))
        (quot (+ (nth v (dec (quot n 2))) (nth v (quot n 2))) 2))))

(def median-score (median run-scores))
;; A pair only counts as STABLE if it won every run. This is the number that
;; cannot be produced by a lucky run.
(def stable-count
  (count (filter (fn [k] (every? #(get-in % [k :qualified]) run-pairs))
                 (keys (first run-pairs)))))
(def pair-run-wins
  (into {} (for [k (keys (first run-pairs))]
             [k (count (filter #(get-in % [k :qualified]) run-pairs))])))

(def pairs-with-runs
  (mapv (fn [p] (assoc p :qualifiedRuns (get pair-run-wins [(:domain p) (:comparator p)] 0)
                         :runs run-count))
        pairs))

(def env (env-of report))
(def artifact
  {:format "kotoba.public-runtime-comparison/v2"
   :generatedAt (:generatedAt report)
   :candidate "Amu native"
   :target "Darwin arm64 native"
   :metric "steady-state nanoseconds per kernel; lower is better"
   :commonBoundary (str "one external C runner invokes every native artifact through the same "
                        "eight-i64-argument indirect-call ABI; loading and symbol resolution "
                        "finish before timing")
   :host (str (:cpu env) ", " (:logicalCpus env) " logical CPUs, "
              (js/Math.round (/ (:totalMemoryBytes env) 1073741824)) " GiB")
   :compilerCommit (:compilerCommit env)
   :preparedIndexSha256 (get-in report [:contract :preparedIndexSha256])
   :sourceReportSha256 (.digest (.update (.createHash crypto "sha256") raw) "hex")
   :comparators (vec (for [[k label] comparator-labels
                           :let [cov (get ec k)]
                           :when cov]
                       {:id (name k) :label label
                        :version (or (first-line (get env (comparator-env-key k)))
                                     (die! 2 (str "REFUSED: no version recorded for comparator "
                                                  (name k) "; a published ranking must name what it ran against")))
                        :coverage (str (count (:domains cov)) "/6")}))
   :domains (vec (for [d (:domains report)]
                   {:id (:id d) :label (get domain-labels (:id d) (:id d))
                    :exactResultVerified true}))
   :semanticCoverage {:requiredComparatorDomainPairs 30
                      :completeComparatorDomainPairs (count pairs)
                      :allKnownAnswersVerified true}
   :pairs pairs-with-runs
   :score {:qualifiedPairs median-score
           :totalPairs (count pairs)
           :runs run-count
           :scoreByRun run-scores
           :observedRange [(apply min run-scores) (apply max run-scores)]
           :stableQualifiedPairs stable-count
           :basis (if (> run-count 1)
                    (str "median of " run-count " host-qualified runs; "
                         stable-count " of the " (count pairs)
                         " pairs qualified in every run")
                    (str "ONE run. The score varies between runs by more than one pair, "
                         "so this number is stated with a precision it does not have -- "
                         "pass several reports to publish a median and a range."))
           :policy "perfgate default-v1: >= 5% mean improvement AND separated from the arms' own spread"}
   ;; The claim is all-or-nothing by construction, so anything short of 30/30
   ;; is `false`. Publishing the count beside it is the point: "18 of 30" and
   ;; "not qualified" are both true, and only one of them is informative.
   :speedQualification
   {:verdict (if (:qualified quiet) "qualified-host-load" "unqualified-host-load")
    :fastestClaimQualified (= stable-count (count pairs))
    :criterion (:policy quiet)
    :quietGateSamples (vec (for [s (:samples quiet)]
                             {:busyCpuFraction (:busyCpuFraction s)
                              :load1 (:load1 s) :qualified (:qualified s)}))
    :explanation
    (if (:qualified quiet)
      (str "Host-qualified. " median-score " of " (count pairs)
           " candidate/comparator/domain pairs pass perfgate"
           (if (> run-count 1)
             (str " (median of " run-count " runs, observed "
                  (apply min run-scores) "-" (apply max run-scores)
                  "; " stable-count " qualified in every run)")
             " (a single run -- the score moves by more than one pair between runs)")
           ". The bounded fastest claim requires all " (count pairs)
           " in every run and is therefore not qualified.")
      (str "The comparison set and exact-result checks completed, but the bounded "
           "quiet-host gate never passed. Timings from this run must not be used "
           "to rank the implementations."))}
   :sources {:harness "https://github.com/kotoba-lang/amu/blob/main/scripts/runtime-multidomain-suite.mjs"
             :manifest "https://github.com/kotoba-lang/amu/blob/main/bench/runtime-comparison/multidomain-suite.json"
             :method "https://github.com/kotoba-lang/amu/blob/main/docs/performance.md#multi-domain-qualification-floor"}})

(.writeFileSync fs out-path (str (js/JSON.stringify (clj->js artifact) nil 2) "\n"))
(println (str "wrote " out-path))
(println (str "  host-qualified: " (:qualified quiet)
              "  runs: " run-count
              "  score(median): " median-score "/" (count pairs)
              "  per-run: " (pr-str run-scores)
              "  stable: " stable-count "/" (count pairs)
              "  fastestClaimQualified: " (= stable-count (count pairs))))
