#!/usr/bin/env nbb
;; scripts/generate-q9-inventory.cljs — the Q9 source inventory, measured.
;;
;; Replaces scripts/generate-q9-inventory.bb, which had four defects that all
;; pointed the same way: it reported a number nobody could reproduce.
;;
;;   1. `:generated-at "2026-07-18"` was a HARDCODED LITERAL. Re-running the
;;      generator rewrote the counts and kept the date, so a fresh measurement
;;      was indistinguishable from a 52-day-old one. This file reads a clock.
;;
;;   2. `rg --files orgs/<org>` without `--no-ignore`. The superproject does
;;      not track `orgs/` (west children are separate repositories), so ripgrep
;;      honours that ignore and drops almost everything. Measured 2026-09-08:
;;
;;        rg --files            orgs/kotoba-lang -g '*.cljc'  ->    152
;;        rg --files --no-ignore orgs/kotoba-lang -g '*.cljc'  ->  9,793
;;        recorded by the .bb generator on 2026-07-18            ->  9,419
;;
;;      The recorded figure is the right order of magnitude and the documented
;;      command no longer produces it. A `:measurement` key that does not
;;      reproduce its own numbers is worse than no key.
;;
;;   3. Five hardcoded organizations. west manages projects across more, so the
;;      denominator drifted every time a repository was registered elsewhere.
;;      The project set now comes from manifest/west.yml.
;;
;;   4. No way to say "not checked out". A repository absent from this machine
;;      counted as zero files, which reads as migrated. Absence is now
;;      :not-checked-out and is counted separately from :measured, because a
;;      repository nobody measured must not report what a measured-and-empty
;;      one reports.
;;
;; Why nbb and not kbb, given the 2026-09-07 kbb-first instruction: this walks
;; every checked-out west project on the machine in one pass. kbb's fs surface
;; is per-request through a capability provider with a 65536-byte request
;; ceiling; a fleet-wide directory walk is not an op it has, and the rule says
;; to add the op to kbb rather than shrink the guest. Adding a fleet-scale walk
;; capability is its own decision, so this stays nbb (the host 14 of the 25
;; scripts in this directory already use) and is recorded as a migration
;; candidate rather than done quietly.
;;
;;   nbb scripts/generate-q9-inventory.cljs [--root <workspace>] [--write]
;;
;; exit 0 wrote (or, without --write, would write) an inventory
;; exit 2 REFUSED — could not measure; nothing written

(ns generate-q9-inventory
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            ["fs" :as fs]
            ["path" :as path]
            ["child_process" :as cp]))

(def argv (vec (drop 2 (js->clj js/process.argv))))
(defn opt [flag] (let [i (.indexOf argv flag)] (when (pos? i) (nth argv (inc i) nil))))
(def write? (boolean (some #{"--write"} argv)))

(def root
  (let [r (or (opt "--root") (.-Q9_ROOT js/process.env) "../../..")]
    (path/resolve r)))

(defn refuse! [msg data]
  (println "REFUSED" msg (pr-str data))
  (js/process.exit 2))

;; --- the denominator: west projects, not a hardcoded org list --------------
(def west-yml (path/join root "manifest" "west.yml"))

(defn west-project-paths
  "Every `path:` line in the west manifest. Parsed textually on purpose: the
  manifest is generated and its shape is stable, and adding a YAML dependency
  to read one key would be a larger commitment than this needs."
  []
  (when-not (fs/existsSync west-yml)
    (refuse! "manifest/west.yml not found — cannot establish the project set"
             {:looked-at west-yml :root root}))
  (->> (str/split-lines (fs/readFileSync west-yml "utf8"))
       (keep (fn [l] (second (re-find #"^\s*path:\s*(\S+)\s*$" l))))
       distinct
       sort
       vec))

;; --- the numerator: one scan, bucketed --------------------------------------
(def extensions ["cljc" "cljs" "clj" "kotoba" "cljk"])

(def exclusions
  ["!**/node_modules/**" "!**/.git/**" "!**/target/**"
   "!**/dist/**" "!**/build/**" "!**/.shadow-cljs/**"])

(defn scan
  "Paths of every EXT under orgs/, ignores explicitly OFF. Returns nil when the
  scan could not run, which the caller must not read as an empty result."
  [ext]
  (let [args (concat ["--files" "--no-ignore" "orgs"]
                     (mapcat (fn [g] ["-g" g]) exclusions)
                     ["-g" (str "*." ext)])]
    (try
      (let [out (.toString (cp/execFileSync "rg" (clj->js args)
                                            #js {:cwd root :encoding "utf8"
                                                 :maxBuffer 512000000}))]
        (vec (remove str/blank? (str/split-lines out))))
      (catch :default e
        ;; rg exits 1 for "no files matched", which is a real empty result.
        ;; Any other status is a failure to measure and must not become 0.
        (if (= 1 (.-status e)) [] nil)))))

(defn repo-of [p] (str/join "/" (take 3 (str/split p #"/"))))

(let [_ (when-not (fs/existsSync (path/join root "orgs"))
          (refuse! "no orgs/ directory under root" {:root root}))
      projects (west-project-paths)
      _ (when (empty? projects)
          (refuse! "west.yml parsed to zero project paths" {:file west-yml}))
      by-ext (into {} (for [e extensions] [e (scan e)]))
      failed (keep (fn [[e v]] (when (nil? v) e)) by-ext)
      _ (when (seq failed)
          (refuse! "ripgrep failed for at least one extension"
                   {:extensions (vec failed)}))
      ;; a west project is measured only if its directory is actually here
      present? (fn [p] (fs/existsSync (path/join root p)))
      checked-out (vec (filter present? projects))
      absent (vec (remove present? projects))
      counts-for (fn [p]
                   (into {} (for [e extensions]
                              [(keyword e)
                               (count (filter #(str/starts-with? % (str p "/"))
                                              (get by-ext e)))])))
      repositories (mapv (fn [p]
                           (let [c (counts-for p)]
                             {:repository p
                              :status :measured
                              :counts c
                              :legacy-total (+ (:cljc c) (:cljs c) (:clj c))
                              :kotoba-total (+ (:kotoba c) (:cljk c))}))
                         checked-out)
      totals (into {} (for [e extensions]
                        [(keyword e) (count (get by-ext e))]))
      ;; paths under orgs/ that no west project claims -- worktrees, verify
      ;; runs, stray trees. Named rather than folded into a repository.
      claimed (set (map repo-of (mapcat val by-ext)))
      unclaimed (vec (sort (remove (set projects) claimed)))
      inventory
      {:kotoba.lang.q9.inventory/version 2
       :generated-at (.toISOString (js/Date.))
       :generator "scripts/generate-q9-inventory.cljs"
       :supersedes "scripts/generate-q9-inventory.bb"
       :measurement
       {:project-set "every `path:` in manifest/west.yml"
        :scan "rg --files --no-ignore orgs -g '*.<ext>' minus node_modules/.git/target/dist/build/.shadow-cljs"
        :why-no-ignore "the superproject does not track orgs/, so ripgrep's default ignore drops west children"
        :extensions extensions
        :scope-warning
        (str "Counts describe THIS CHECKOUT, not the fleet. "
             (count absent) " of " (count projects)
             " west projects are not present here and are :not-checked-out, "
             "which is UNMEASURED and must not be read as zero.")}
       :project-count (count projects)
       :measured-count (count checked-out)
       :not-checked-out-count (count absent)
       :not-checked-out absent
       :unclaimed-paths unclaimed
       :totals totals
       :repositories repositories}]
  ;; Writes relative to CWD, i.e. into whatever checkout is running this --
  ;; never into `<root>/orgs/kotoba-lang/kotoba-lang`, which on a west
  ;; workspace is the SHARED checkout that CLAUDE.md forbids editing.
  (when write?
    (fs/writeFileSync "lang/q9-inventory.edn" (str (pr-str inventory) "\n")))
  (println (str "SCANNED\t" (count checked-out) "/" (count projects) "\twest projects present"))
  (println (str "UNMEASURED\t" (count absent) "\tnot checked out here"))
  (doseq [e extensions]
    (println (str "  ." e "\t" (get totals (keyword e)))))
  (println (str "UNCLAIMED\t" (count unclaimed) "\tpaths under orgs/ no west project claims"))
  (println (if write? "WROTE lang/q9-inventory.edn" "DRY RUN — pass --write to update lang/q9-inventory.edn")))
