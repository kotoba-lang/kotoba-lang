#!/usr/bin/env nbb
(ns generate-docs-reference
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            ["fs" :as fs]
            ["path" :as path]))

(def args (set (drop 2 (js->clj (.-argv js/process)))))
(def check? (contains? args "--check"))

(defn read-edn [p]
  (reader/read-string (fs/readFileSync p "utf8")))

(def cli (read-edn "lang/cli.edn"))
(def stdlib (read-edn "lang/conformance/stdlib/manifest.edn"))
(def diagnostics (read-edn "lang/diagnostics.edn"))
(def release-binding (read-edn "lang/docs-release.edn"))

(defn heading [s] (str "# " s "\n\n"))
(defn code-list [xs]
  (str/join ", " (map #(str "`" % "`") xs)))
(defn display [x]
  (cond (keyword? x) (name x) (symbol? x) (name x) :else (str x)))

(defn option-line [{:keys [id flags type values default required? repeatable? description]}]
  (str "- **" (if (seq flags) (code-list flags) (str "`" (display id) "`")) "** — " description
       " Type: `" (display type) "`"
       (when (seq values) (str "; values: " (code-list (map display values))))
       (when (some? default) (str "; default: `" (display default) "`"))
       (when required? "; required")
       (when repeatable? "; repeatable") ".\n"))

(defn cli-doc []
  (str (heading "Generated Kotoba CLI reference")
       "> Generated from [`lang/cli.edn`](../../lang/cli.edn). Do not edit by hand.\n\n"
       "Contract version: `" (:kotoba.cli.contract/version cli) "`. "
       "Host executables are adapters; unsupported commands must fail explicitly.\n\n"
       (apply str
              (for [{:keys [id tier summary positionals subcommands options]}
                    (:kotoba.cli.contract/commands cli)]
                (str "## `kotoba " (name id) "`\n\n"
                     summary "\n\n"
                     "Maturity tier: `" (name tier) "`.\n\n"
                     (when (seq subcommands)
                       (str "Subcommands: " (code-list (map display subcommands)) ".\n\n"))
                     (when (seq positionals)
                       (str "Positionals:\n\n" (apply str (map option-line positionals)) "\n"))
                     (when (seq options)
                       (str "Options:\n\n" (apply str (map option-line options)) "\n")))))))

(defn stdlib-doc []
  (let [module (first (:modules stdlib))
        builtins (:language-builtins stdlib)]
    (str (heading "Generated Kotoba standard-library reference")
         "> Generated from [`lang/conformance/stdlib/manifest.edn`](../../lang/conformance/stdlib/manifest.edn). Do not edit by hand.\n\n"
         "The bounded public module list is `" (name (:status module)) "`. "
         "Adding a public name requires a manifest/version change and conformance evidence.\n\n"
         "## `" (name (:id module)) "`\n\n"
         "Source: [`" (:path module) "`](../../" (:path module) ").\n\n"
         "Records: " (code-list (sort (map display (:records module)))) ".\n\n"
         "Public names: " (code-list (sort (map display (:public-names module)))) ".\n\n"
         "## Language built-ins\n\n"
         "String operations: " (code-list (sort (map display (:string-ops builtins)))) ".\n\n"
         "Option sugar: " (code-list (sort (map display (:option-sugar builtins)))) ".\n\n"
         "These built-ins are not ambiently prelude-loaded; admission is controlled by the cited language authorities.\n")))

(defn diagnostics-doc []
  (str (heading "Generated Kotoba diagnostic-code reference")
       "> Generated from [`lang/diagnostics.edn`](../../lang/diagnostics.edn). Do not edit by hand.\n\n"
       "Coverage is `" (name (:coverage diagnostics)) "`, not an exhaustive list of compiler or host errors. "
       "Codes are stable within a language profile; message wording is informational.\n\n"
       (apply str
              (for [{:keys [code phase summary action source]} (:diagnostics diagnostics)]
                (str "## `" code "`\n\n"
                     "Phase: `" (name phase) "`.\n\n"
                     summary "\n\n"
                     "**Recovery:** " action "\n\n"
                     "Authority: [`" source "`](../../" source ").\n\n")))))

(defn release-doc []
  (let [contract (:contract release-binding)
        language (:language-release release-binding)
        implementation (:implementation-release release-binding)
        public (:public-default release-binding)]
    (str (heading "Generated Kotoba release binding")
         "> Generated from [`lang/docs-release.edn`](../../lang/docs-release.edn). Do not edit by hand.\n\n"
         "Public default: **" (str/upper-case (name (:status public))) "** (`" (:code public) "`).\n\n"
         (:reason public) "\n\n"
         "| Axis | Recorded value |\n|---|---|\n"
         "| Current contract | language profile " (:language-profile contract)
         ", package contract " (:package-contract contract) " |\n"
         "| Language release | " (:version language) ", language profile "
         (:language-profile language) " |\n"
         "| Implementation release | `" (:tag implementation) "` at `"
         (subs (:commit implementation) 0 12) "` |\n"
         "| Implementation profile binding | `" (name (:language-profile-binding implementation)) "` |\n\n"
         "Promotion requires: " (code-list (sort (map display (:promotion-requires release-binding)))) ".\n")))

(defn search-entry [kind title body url keywords]
  {:kind kind :title title :body body :url url :keywords (vec keywords)})

(defn search-index []
  (vec
   (concat
    [(search-entry :release "Release binding"
                   (get-in release-binding [:public-default :reason])
                   "docs/generated/release.md"
                   [(name (get-in release-binding [:public-default :code]))
                    (get-in release-binding [:implementation-release :tag])])]
    (for [{:keys [id summary options subcommands]} (:kotoba.cli.contract/commands cli)]
      (search-entry :cli (str "kotoba " (name id)) summary
                    "docs/generated/cli.md"
                    (concat (map display subcommands)
                            (mapcat #(concat (map display (:flags %)) [(display (:id %))]) options))))
    (for [n (sort-by str (:public-names (first (:modules stdlib))))]
      (search-entry :stdlib (display n) "Bounded core standard-library public name."
                    "docs/generated/stdlib.md" ["core" "stdlib"]))
    (for [{:keys [code phase summary action]} (:diagnostics diagnostics)]
      (search-entry :diagnostic (str code) (str summary " " action)
                    "docs/generated/diagnostics.md" [(name phase)])))))

(def outputs
  {"docs/generated/cli.md" (cli-doc)
   "docs/generated/stdlib.md" (stdlib-doc)
   "docs/generated/diagnostics.md" (diagnostics-doc)
   "docs/generated/release.md" (release-doc)
   "docs/search-index.edn" (str (pr-str (search-index)) "\n")})

(defn write-or-check! [[p content]]
  (if check?
    (when (or (not (fs/existsSync p))
              (not= content (fs/readFileSync p "utf8")))
      (println "GENERATED DOC DRIFT" p)
      false)
    (do (fs/mkdirSync (path/dirname p) #js {:recursive true})
        (fs/writeFileSync p content)
        (println "wrote" p)
        true)))

(let [results (mapv write-or-check! outputs)]
  (if (every? #(not= false %) results)
    (do (println (if check? "GENERATED DOCS PASS" "GENERATED DOCS WRITTEN")
                 {:files (count outputs) :entries (count (search-index))})
        (js/process.exit 0))
    (js/process.exit 1)))
