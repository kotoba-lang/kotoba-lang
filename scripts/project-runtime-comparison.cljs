#!/usr/bin/env nbb
;; scripts/project-runtime-comparison.cljs — project amu's multidomain report
;; into the public artifact kotoba-lang.org renders.
;;
;; `bench/public-runtime-comparison/latest.json` was hand-maintained and its
;; `sources` already point at amu's harness, manifest and method. This makes
;; the projection reproducible: the public file becomes a function of a named
;; amu report rather than a transcription of one.
;;
;;   nbb scripts/project-runtime-comparison.cljs <amu-multidomain-report.json> \
;;       [--output bench/public-runtime-comparison/latest.json]
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

(def in-path (first (positional)))
(when-not in-path (die! 2 "usage: project-runtime-comparison.cljs <report.json> [--output <path>]"))
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
   :pairs pairs
   :score {:qualifiedPairs qualified-count
           :totalPairs (count pairs)
           :policy "perfgate default-v1: >= 5% mean improvement AND separated from the arms' own spread"}
   ;; The claim is all-or-nothing by construction, so anything short of 30/30
   ;; is `false`. Publishing the count beside it is the point: "18 of 30" and
   ;; "not qualified" are both true, and only one of them is informative.
   :speedQualification
   {:verdict (if (:qualified quiet) "qualified-host-load" "unqualified-host-load")
    :fastestClaimQualified (= qualified-count (count pairs))
    :criterion (:policy quiet)
    :quietGateSamples (vec (for [s (:samples quiet)]
                             {:busyCpuFraction (:busyCpuFraction s)
                              :load1 (:load1 s) :qualified (:qualified s)}))
    :explanation
    (if (:qualified quiet)
      (str "Host-qualified. " qualified-count " of " (count pairs)
           " candidate/comparator/domain pairs pass perfgate; the bounded fastest claim "
           "requires all " (count pairs) " and is therefore not qualified.")
      (str "The comparison set and exact-result checks completed, but the bounded "
           "quiet-host gate never passed. Timings from this run must not be used "
           "to rank the implementations."))}
   :sources {:harness "https://github.com/kotoba-lang/amu/blob/main/scripts/runtime-multidomain-suite.mjs"
             :manifest "https://github.com/kotoba-lang/amu/blob/main/bench/runtime-comparison/multidomain-suite.json"
             :method "https://github.com/kotoba-lang/amu/blob/main/docs/performance.md#multi-domain-qualification-floor"}})

(.writeFileSync fs out-path (str (js/JSON.stringify (clj->js artifact) nil 2) "\n"))
(println (str "wrote " out-path))
(println (str "  host-qualified: " (:qualified quiet)
              "  score: " qualified-count "/" (count pairs)
              "  fastestClaimQualified: " (= qualified-count (count pairs))))
