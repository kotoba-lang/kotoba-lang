;; kotoba-lang.org — the language's public page.
;;
;; Built with the kotoba-lang design system (kotoba-ui.core only, per
;; CLAUDE.md / skill kotoba-uiux): no raw hex outside the one theme map, no
;; hand-written layout CSS, HIG text styles only.
;;
;; The point of this generator: the page is DERIVED FROM THIS REPO'S OWN
;; AUTHORITY FILES (lang/*.edn), not hand-copied prose. The eight safety
;; claims, their residual risks, the deliberately-absent surface, the
;; component/WASI platform pins and the identity non-goals are all read at
;; build time. When the spec changes, the page changes; it cannot drift into
;; claiming more than the spec claims.
;;
;; Run from the repository root (west layout: siblings under orgs/kotoba-lang):
;;
;;   nbb --classpath "../shitsuke/src:../css/src:../html/src:../liquid-glass-ui/src:../kotoba-ui/src:../byoubu/src:../byoubu-ui/src:../kotoba-kir/src:../kotoba-hir/src" \
;;       site/generate.cljs
;;
;; `kotoba-kir` and `kotoba-hir` are on that classpath because shitsuke moved
;; its raw-text safety check into a compiled `.kotoba` decision core:
;; `shitsuke.hiccup` -> `shitsuke.kotoba-oracle` -> `kotoba.kir` -> `kotoba.hir`.
;; They are load-bearing for LOADING the design system, not only for calling it.
;;
;; Output: site/dist/index.html (a single self-contained static document —
;; no build step, no runtime JS, for anyone visiting the page).

(require '[kotoba-ui.core :as ui]
         '[shitsuke.kotoba-oracle :as oracle]
         '[cljs.reader :as reader]
         '[clojure.string :as str]
         '["fs" :as fs]
         '["path" :as path])

;; ---------------------------------------------------------------------------
;; the raw-text decision core
;;
;; This page renders `[:script [:hiccup/raw ...]]` and `[:style [:hiccup/raw ...]]`,
;; and shitsuke routes exactly those raw-text elements through a compiled
;; `.kotoba` core. On ClojureScript there is no classpath to read the shipped
;; artifact from, so `->html` THROWS unless the KIR is registered first — a
;; deliberate refusal, because a silent fallback around a missing security core
;; is how an unchecked payload reaches a page (shitsuke README, "ClojureScript
;; consumers: what changed").
;;
;; The ids and the artifact path are read from the library rather than spelled
;; out here, so an upstream rename cannot leave this generator registering a
;; core that no longer exists while still appearing to work.

(def shitsuke-resources
  "Where shitsuke's shipped decision cores live.

  Defaults to the same west sibling layout the classpath above already assumes;
  `SHITSUKE_RESOURCES` overrides it for checkouts that are not laid out that
  way (a CI worktree, a one-off clone)."
  (or (some-> js/process.env.SHITSUKE_RESOURCES not-empty)
      (path/join ".." "shitsuke" "resources")))

(doseq [id (keys oracle/cores)]
  (let [artifact (path/join shitsuke-resources (oracle/resource-path id))]
    (when-not (fs/existsSync artifact)
      (println "site/generate.cljs: shitsuke decision core not found:" artifact)
      (println "  set SHITSUKE_RESOURCES to the shitsuke checkout's resources/ directory")
      (js/process.exit 1))
    (oracle/register-kir! id (reader/read-string (fs/readFileSync artifact "utf8")))))

;; ---------------------------------------------------------------------------
;; authority inputs

;; One list, used both to read the inputs and to cite them in the footer, so
;; the citation cannot drift away from what was actually read.
(def authority-files
  ["lang/safety-claims.edn"
   "lang/surface-status.edn"
   "lang/capability-semantics.edn"
   "lang/elaboration-pipeline.edn"
   "lang/wasm-component-platform.edn"
   "lang/code-identity.edn"
   "lang/safety-qualification.edn"
   "lang/docs-release.edn"
   "lang/diagnostics.edn"
   "lang/cli.edn"
   "lang/conformance/stdlib/manifest.edn"
   "docs/user-validation.edn"
   "docs/search-index.edn"])

(def authority
  (into {} (for [f authority-files]
             [f (reader/read-string (fs/readFileSync f "utf8"))])))

(def safety-claims  (authority "lang/safety-claims.edn"))
(def surface-status (authority "lang/surface-status.edn"))
(def capability     (authority "lang/capability-semantics.edn"))
(def platform       (authority "lang/wasm-component-platform.edn"))
(def identity-spec  (authority "lang/code-identity.edn"))
(def qualification  (authority "lang/safety-qualification.edn"))
(def elaboration    (authority "lang/elaboration-pipeline.edn"))
(def docs-release   (authority "lang/docs-release.edn"))
(def diagnostics    (authority "lang/diagnostics.edn"))
(def cli-contract   (authority "lang/cli.edn"))
(def stdlib         (authority "lang/conformance/stdlib/manifest.edn"))
(def user-validation (authority "docs/user-validation.edn"))
(def search-index   (authority "docs/search-index.edn"))

;; ---------------------------------------------------------------------------
;; theme — the one place in app code a hex color is legitimate (rule 5)

(def theme {:accent "#4F46E5" :accent-dark "#8B87FF" :appearance :auto})

;; ---------------------------------------------------------------------------
;; small helpers

(defn code
  "Inline code span. Styling comes from the app stylesheet below, which is
  unlayered and therefore always wins over the library layers."
  [s]
  [:code {:class "kot-code"} s])

(defn code-block [s]
  [:pre {:class "kot-pre"} [:code s]])

(defn sorted-names
  "Deterministic rendering order for a set of symbols/keywords."
  [coll]
  (sort (map name coll)))

(defn caption [s]
  [:p {:class "hig-caption1 kot-muted"} s])

(defn bullets [items]
  (into [:ul {:class "kot-list hig-footnote"}]
        (for [i items] [:li i])))

(defn label-text
  "Human label for a claim id like :t1-memory -> \"T1 · memory\"."
  [id]
  (let [[t & rest-parts] (clojure.string/split (name id) #"-")]
    (str (clojure.string/upper-case t) " · " (clojure.string/join " " rest-parts))))

;; ---------------------------------------------------------------------------
;; sections

(def intro-cards
  [["1 · Identity by content"
    "A definition is named by what it means, not by what it was called."
    (str "The identity is computed after desugar, type checking, effect "
         "inference and ability elaboration — and it seals the effect row, so "
         "a pure definition and one requiring network authority can never "
         "share a name. Unison's idea; not Unison's syntax and not a global "
         "codebase.")]
   ["2 · Memory safety, as a consequence"
    "The thesis is confinement. Memory safety falls out of it."
    (str "Admitted components cannot address runtime or native memory, and "
         "component memory operations are bounded or trap. That holds without "
         "a general ownership/borrow system: affine consumption is scoped to "
         "capability values alone, because what a program must not forge is "
         "authority, not pointers.")]
   ["3 · Component-first execution"
    "The unit that runs is a Wasm component, not a process."
    (str "Each component gets its own WIT world built from its declared "
         "effects. Undeclared imports are rejected and there is no ambient "
         "WASI. The tender links and instantiates, binding only what policy "
         "granted. Native AOT for ordinary applications is an explicit "
         "non-goal — the boundary is the point.")]])

(defn intro-section []
  (ui/section
   {:title "Three things to know" :wide true}
   (ui/grid
    {:min "280px"}
    (for [[title lede body] intro-cards]
      (ui/panel [[:h3 {:class "hig-headline"} title]
                 [:p {:class "hig-callout kot-lede"} lede]
                 [:p {:class "hig-subheadline"} body]])))
   (caption
    (str "Kotoba source is an EDN/Lisp subset: .kotoba is the canonical "
         "extension and .cljc is common source across Clojure, ClojureScript "
         "and Kotoba. It is a source profile with its own compatibility "
         "contract — not \"any JVM Clojure program runs\"."))))

(def ladder
  [["S" "capability sandbox + deny-by-default + reproducible, verified build"
    "what Kotoba targets"]
   ["A" "a small Wasm language with Rust-style ownership and borrowing" nil]
   ["B" "Clojure-shaped syntax + a safe subset + a borrow checker" nil]
   ["—" "a Clojure/ClojureScript guarded by a linter" "last place"]])

(defn thesis-section []
  (ui/section
   {:title "Memory safety is a consequence, not the thesis"}
   [:p {:class "hig-body"}
    "Most safe-language pitches begin and end with memory. Kotoba treats that "
    "as necessary and insufficient: a program that cannot corrupt memory but "
    "can still open a socket it was never given has not been contained. The "
    "safest program is not the one written in the strongest type system — it "
    "is the one that, when it is fully compromised, can still do nothing. So "
    "confinement is ranked above ownership:"]
   (ui/list-view
    (for [[grade text note] ladder]
      (ui/list-row
       [:span [:strong {:class "kot-grade"} grade] " " text]
       (when note {:trailing (ui/badge note)}))))
   [:blockquote {:class "kot-quote hig-callout"}
    "Against a mythos-class adversarial agent, a linter's red underline is a "
    "polite signpost. When something comes through the wall, the only thing "
    "that works is to have kept nothing outside it."]
   [:p {:class "hig-body"}
    "This is why there is no borrow checker over every value. T1 — admitted "
    "components cannot address runtime or native memory, and component memory "
    "operations are bounded or trap — is met by the admitted grammar and the "
    "runtime, not by an ownership system. Affine consumption exists, but only "
    "where forging a duplicate would create authority: a capability value may "
    "be consumed at most once per execution path."]
   (caption
    (str "The ladder is the language's own accepted design position (ADR — "
         "safe capability language). What it does not claim: the Wasm runtime "
         "engine stays inside the trusted computing base, and native loaders "
         "still require a second OS isolation boundary."))))

(def source-example
  "(ns example.greet)\n\n(defn greeting [name :string] :string\n  (string-concat \"hello, \" name))\n\n(defn main [] :string\n  (greeting \"kotoba\"))")

(def policy-example
  (str ";; effective scope =\n"
       ";;   requested ∩ delegated ∩ local policy\n"
       "{:policy/allow\n"
       " #{{:cap/kind     :host/http\n"
       "    :cap/resource \"https://api.example.com/\"\n"
       "    :cap/expires  \"2026-12-31T00:00:00Z\"}}\n"
       " :policy/forbid-wildcard true}"))

(defn source-section []
  (ui/section
   {:title "Source, and the policy that admits it" :wide true}
   (ui/grid
    {:min "340px"}
    (ui/panel [[:h3 {:class "hig-headline"} "A module"]
               (code-block source-example)
               (caption "Types are inline. No macros, no interop, no eval — those forms are not \"discouraged\", they are absent from the admitted grammar.")])
    (ui/panel [[:h3 {:class "hig-headline"} "A policy"]
               (code-block policy-example)
               [:p {:class "hig-caption2 kot-eyebrow"} "what denies"]
               [:p {:class "kot-chips"}
                (for [[rule outcome] (sort-by (comp name key) (:rules capability))
                      :when (= :deny outcome)]
                  (ui/chip (name rule)))]
               (caption (str "Scope may only attenuate, never widen, and the handler "
                             "receives a concrete post-intersection capability. "
                             "Production policy must forbid wildcard scope; every "
                             "attempt is receipted whether or not it succeeds."))]))))

(defn claims-section []
  (ui/section
   {:title "Eight claims, and what each still risks" :wide true}
   [:p {:class "hig-body"}
    "These are the language's qualification claims, read directly out of "
    (code "lang/safety-claims.edn")
    " when this page was built. Each one ships with its trusted computing base "
    "and its residual risk, because a safety claim without a stated boundary "
    "is marketing."]
   (ui/grid
    {:min "300px"}
    (for [{:keys [id claim tcb residual-risk]} (:claims safety-claims)]
      (ui/panel
       [[:p {:class "hig-caption2 kot-eyebrow"} (label-text id)]
        [:p {:class "hig-subheadline"} claim]
        (ui/divider)
        [:p {:class "hig-caption2 kot-eyebrow"} "trusted computing base"]
        [:p {:class "hig-caption1 kot-muted"} (clojure.string/join " · " tcb)]
        [:p {:class "hig-caption2 kot-eyebrow"} "residual risk"]
        (bullets residual-risk)])))
   (caption (str "Qualification level "
                 (clojure.string/upper-case (name (:kotoba.lang.safety-claims/qualification safety-claims)))
                 ", as of " (:kotoba.lang.safety-claims/as-of safety-claims) "."))))

(def absent-order
  [:no-ambient-authority :no-interop :no-ambient-mutation
   :no-unbounded-concurrency :no-guest-macros :explicit-errors])

(defn absent-section []
  (let [invariants (:invariants surface-status)
        limits (get-in invariants [:bounded-admission :limits])]
    (ui/section
     {:title "Deliberately absent" :wide true}
     [:p {:class "hig-body"}
      "Every entry below is a security constraint, not an unfinished feature. "
      "The distinction is tracked in "
      (code "lang/surface-status.edn")
      " so that \"not implemented yet\" can never be quietly confused with "
      "\"refused on purpose\"."]
     (ui/list-view
      (for [k absent-order
            :let [{:keys [surface reason]} (get invariants k)]]
        (ui/list-row
         [:div
          [:p {:class "hig-headline"}
           (interpose " " (for [s (sorted-names surface)] (code s)))]
          [:p {:class "hig-footnote kot-muted"} reason]])))
     (ui/panel
      [[:h3 {:class "hig-headline"} "Bounded admission"]
       [:p {:class "hig-subheadline"} (get-in invariants [:bounded-admission :reason])]
       (bullets (for [[k v] (sort-by (comp name key) limits)]
                  [:span (code (name k)) " " (str v)]))])
     (caption (str "Profile version " (:kotoba.lang.surface-status/profile-version surface-status)
                   ", as of " (:kotoba.lang.surface-status/as-of surface-status)
                   ". One more intentional simplification: affine consumption is "
                   "scoped to capability values only — a general ownership/borrow/"
                   "lifetime system is intentionally absent.")))))

(defn platform-section []
  (let [{:keys [component-model wasi]} (:upstream platform)
        world (:world platform)]
    (ui/section
     {:title "Component-first, and what that rules out" :wide true}
     (ui/grid
      {:min "280px"}
      (ui/panel
       [[:h3 {:class "hig-headline"} "WebAssembly components"]
        (bullets
         [[:span "Component Model pinned at " (code (subs (:revision component-model) 0 12))]
          [:span "WASI " (code (:version wasi)) " baseline"]
          [:span "world construction: " (code (name (:construction world)))]
          [:span "undeclared imports: " (code (name (:undeclared-imports world)))]
          [:span "ambient WASI: " (code (str (:ambient-wasi world)))]])
        (caption "Async functions, futures and streams are explicit bounded effects with cancellation, deadline and budgets — never ambient authority.")])
      (ui/panel
       [[:h3 {:class "hig-headline"} "Two lowering targets"]
        [:p {:class "hig-subheadline"}
         "The same checked intermediate representation lowers to either "
         (interpose ", "
                    (for [t (sorted-names (:targets (first (filter #(= :target-lowering (:id %))
                                                                   (:pipeline elaboration)))))]
                      (code t)))
         " — under the same rules: exact imports, deny-by-default admission."]
        (caption "Ordinary-application native AOT is an explicit non-goal: the execution boundary is the component.")])
      (ui/panel
       [[:h3 {:class "hig-headline"} "Who does what"]
        (bullets
         (for [role [:tender :broker :native-primitive]
               :let [{:keys [definition]} (get-in qualification [:terms role])]]
           [:span [:strong (name role)] " — " definition]))])))))

(defn pipeline-section []
  (let [model (:source-programming-model elaboration)]
    (ui/section
     {:title "From source to an admitted artifact" :wide true}
     [:p {:class "hig-body"}
      "Ten stages, each with a named owner and its own fail-closed rules. "
      "Note where effects enter: they are " [:em "inferred"] " across the call "
      "graph, and a declaration is a ceiling, not a floor — you cannot widen "
      "your own authority by writing a larger annotation."]
     (ui/list-view
      (for [{:keys [id owner rules]} (:pipeline elaboration)]
        (ui/list-row
         [:div
          [:p {:class "hig-headline"} (name id)]
          [:p {:class "hig-footnote kot-muted"}
           (interpose " · " (for [r (sorted-names rules)] (code r)))]]
         {:trailing [:span {:class "hig-caption2 kot-muted"} owner]})))
     (ui/grid
      {:min "280px"}
      (ui/panel [[:h3 {:class "hig-headline"} "What you write"]
                 [:p {:class "kot-chips"}
                  (for [k (sorted-names (:keeps model))] (ui/chip k))]])
      (ui/panel [[:h3 {:class "hig-headline"} "What you never write"]
                 [:p {:class "kot-chips"}
                  (for [k (sorted-names (:not-user-facing model))] (ui/chip k))]
                 (caption (str "Numeric capability IDs and WIT import syntax are "
                               "a wire ABI, not source vocabulary. Explicit "
                               "capability values are for attenuation, delegation, "
                               "resource scope, quota and deadline — not for "
                               "ordinary calls."))])))))

(def stage-titles
  {:ci0 "the contract"
   :ci1 "canonical typed-KIR encoding and identity test vectors"
   :ci2 "definition-addressed manifest fields and positive fixtures"
   :ci3 "negative fixtures for every sealed input"
   :ci4 "safe-build verifies identity against the package lock"
   :ci5 "typed ability/effect checking and the narrow WIT ABI"
   :ci6 "cross-implementation conformance"
   :ci7 "friendly source operations elaborate identically"})

(defn identity-section []
  (let [{:keys [canonical-input]} (get-in identity-spec [:identities :definition-cid])
        impl (:implementation identity-spec)
        foundation-statuses (select-keys impl [:ci4 :ci5])
        foundation-landed? (every? #(= :implemented (:status %))
                                    (vals foundation-statuses))
        pending-identities
        (for [[identity {:keys [status]}] (:identities identity-spec)
              :when (not= :implemented status)]
          (str (name identity) "=" (name status)))]
    (ui/section
     {:title "Identity by content, without the Unison surface" :wide true}
     [:p {:class "hig-body"}
      "Kotoba takes Unison's idea — a definition is named by what it is, not "
      "by the label someone typed above it — and deliberately leaves the rest. "
      "The identity is computed "
      [:em "after"]
      " desugar, type checking, effect inference and ability elaboration, so "
      "it names normalized semantics rather than text. Source formatting, "
      "package name and git ref are excluded."]
     [:p {:class "hig-body"} "What the identity seals:"]
     [:p {:class "kot-chips"}
      (for [k canonical-input] (ui/chip (name k)))]
     [:p {:class "hig-body"}
      "The effect row is in that list for a concrete reason. Without it, a "
      "pure definition and one requiring "
      (code ":host/http")
      " with identical KIR hash to the same identity — so a lock pinning the "
      "pure one would admit the effectful one. There is a negative fixture for "
      "exactly that substitution."]
     [:p {:class "hig-body"} "What is explicitly " [:em "not"] " adopted:"]
     [:p {:class "kot-chips"}
      (for [g (sort-by name (:non-goals identity-spec))]
        (ui/chip (name g)))]
     [:h3 {:class "hig-headline kot-stage-heading"} "Delivery stages"]
     (ui/list-view
      (for [[stage {:keys [status remaining note]}] (sort-by key impl)]
        (ui/list-row
         [:div
          [:p {:class "hig-headline"}
           (clojure.string/upper-case (name stage)) " — " (get stage-titles stage)]
          [:p {:class "hig-footnote kot-muted"} (or note remaining)]]
         {:trailing (ui/badge (name status))})))
     (caption
      (str "Read from lang/code-identity.edn at build time, so this table "
           "cannot claim more than the repository does. CI4 and CI5 are "
           (if foundation-landed? "both implemented. " "not both implemented. ")
           "That does not promote the other identity layers: "
           (clojure.string/join ", " pending-identities) ".")))))

(defn status-section []
  (ui/section
   {:title "Where this actually is"}
   [:p {:class "hig-body"}
    "Kotoba is a working compiler and a qualified bounded slice — not a "
    "finished general-purpose platform. The distinction is kept in the repo "
    "rather than softened here:"]
   (bullets
    ["Q1–Q8 pass for the bounded reference slice, including a CLJC-shadowed pure port, a denied/allowed capability port, and guarded native OS-isolation conformance."
     "Q9 fleet migration is authorized only for bounded Wave 1 tranches. Later waves and production deployment are not authorized, and the ClojureScript oracle is retained."
     "Runtime-engine vulnerabilities remain inside the trusted computing base; native loaders still require a second OS isolation boundary."
     "Key custody and revocation distribution remain operational, not linguistic, guarantees."])
   (caption "If a claim is not on this page, assume it is not being made.")))

(defn release-section []
  (let [contract (:contract docs-release)
        language (:language-release docs-release)
        implementation (:implementation-release docs-release)
        public (:public-default docs-release)]
    (ui/section
     {:title "Release binding" :wide true}
     [:p {:class "hig-body"}
      "Documentation does not silently equate a language profile with the latest binary tag. "
      "The machine binding records the independently verified public-default status:"]
     (ui/grid
      {:min "240px"}
      (ui/panel [[:p {:class "hig-caption2 kot-eyebrow"} "current contract"]
                 [:p {:class "hig-headline"}
                  "language profile " (:language-profile contract)]
                 (caption (str "package contract " (:package-contract contract)))])
      (ui/panel [[:p {:class "hig-caption2 kot-eyebrow"} "language release"]
                 [:p {:class "hig-headline"} (:version language)]
                 (caption (str "binds profile " (:language-profile language)))])
      (ui/panel [[:p {:class "hig-caption2 kot-eyebrow"} "implementation"]
                 [:p {:class "hig-headline"} (:tag implementation)]
                 (caption (str "profile binding: "
                               (name (:language-profile-binding implementation))))])
      (ui/panel [[:p {:class "hig-caption2 kot-eyebrow"} "public default"]
                 [:p {:class "hig-headline"}
                  (str/upper-case (name (:status public)))]
                 (caption (str (:code public))) ]))
     [:p {:class "hig-callout"} (:reason public)]
     [:p [:a {:class "kot-link"
              :href "https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/generated/release.md"}
          "Read the generated release binding"]]
     (caption "Promotion requires a signed envelope that binds the implementation commit, artifact digests, profile, package contract and conformance result; unverified platforms remain excluded."))))

(defn search-url [path]
  (str "https://github.com/kotoba-lang/kotoba-lang/blob/main/" path))

(defn search-section []
  (ui/section
   {:title "Search the checked reference" :wide true}
   [:p {:class "hig-body"}
    "Search commands, options, bounded standard-library names, stable diagnostic codes, and release status. The index is generated from machine authorities and runs locally in this page."]
   (ui/text-field {:id "kot-doc-search"
                   :type "search"
                   :placeholder "Try: compile, option-some, docs/link-missing"
                   :aria-label "Search Kotoba documentation reference"})
   [:p {:id "kot-doc-search-count" :class "hig-caption1 kot-muted"
        :aria-live "polite"}]
   [:div {:id "kot-doc-search-results"}
    (ui/list-view
     (for [{:keys [kind title body url keywords]} search-index
           :let [haystack (str/lower-case
                           (str title " " body " "
                                (str/join " " keywords)))]]
       [:div {:class "kot-search-item" :data-search haystack}
        (ui/list-row
         [:div
          [:p {:class "hig-headline"}
           [:a {:class "kot-link" :href (search-url url)} title]]
          [:p {:class "hig-footnote kot-muted"} body]]
         {:trailing (ui/badge (name kind))})]))]
   (caption (str (count search-index) " generated entries. No query leaves the browser."))))

(def search-js
  (str "document.addEventListener('DOMContentLoaded',function(){"
       "var input=document.getElementById('kot-doc-search');"
       "var count=document.getElementById('kot-doc-search-count');"
       "var items=Array.from(document.querySelectorAll('.kot-search-item'));"
       "function apply(){var q=input.value.trim().toLowerCase();var shown=0;"
       "items.forEach(function(item,i){var match=q?item.dataset.search.includes(q):i<8;"
       "item.hidden=!match;if(match){shown+=1;}});"
       "count.textContent=shown+' result'+(shown===1?'':'s');}"
       "input.addEventListener('input',apply);apply();});"))

(defn documentation-section []
  (ui/section
   {:title "Documentation" :wide true}
   [:p {:class "hig-body"}
    "Choose a route by what you need to accomplish. Each route points back to "
    "the machine-readable authority instead of creating another copy of the spec."]
   (ui/grid
    {:min "240px"}
    (ui/panel [[:h3 {:class "hig-headline"} "Learn"]
               [:p {:class "hig-footnote"} "Install the CLI, run an expression, build a module, and understand the compatibility boundary."]
               [:p [:a {:class "kot-link" :href "https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/getting-started.md"}
                    "Getting started"]]])
    (ui/panel [[:h3 {:class "hig-headline"} "Use"]
               [:p {:class "hig-footnote"} "Look up values, effects, errors, packages, standard libraries, and common tool workflows."]
               [:p [:a {:class "kot-link" :href "https://github.com/kotoba-lang/kotoba-lang/tree/main/docs/reference"}
                    "Language and tooling reference"]]])
    (ui/panel [[:h3 {:class "hig-headline"} "Implement"]
               [:p {:class "hig-footnote"} "Consume the semantics SSoT, admitted grammar, surface classification, and conformance fixtures."]
               [:p [:a {:class "kot-link" :href "https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/lang/semantics-ssot.md"}
                    "Implementation contract"]]])
    (ui/panel [[:h3 {:class "hig-headline"} "Evaluate"]
               [:p {:class "hig-footnote"} "Separate contract, documentation, release, operational, and ecosystem maturity before making a claim."]
               [:p [:a {:class "kot-link" :href "https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/maturity.md"}
                    "Maturity and comparison"]]]))
   (caption "The complete routing and ownership map is checked by scripts/check-docs.cljs.")))

(defn footer []
  (ui/section
   {:title "Read the source" :wide true}
   (ui/grid
    {:min "240px"}
    (ui/panel [[:h3 {:class "hig-headline"} "kotoba-lang/kotoba-lang"]
               [:p {:class "hig-footnote"} "The language authority: semantics, admitted grammar, capability semantics, safety claims, conformance fixtures."]
               [:p [:a {:class "kot-link" :href "https://github.com/kotoba-lang/kotoba-lang"} "github.com/kotoba-lang/kotoba-lang"]]])
    (ui/panel [[:h3 {:class "hig-headline"} "kotoba-lang/kotoba-core-contracts"]
               [:p {:class "hig-footnote"} "Source classification, package admission, and runtime-boundary contracts consumed by launchers."]
               [:p [:a {:class "kot-link" :href "https://github.com/kotoba-lang/kotoba-core-contracts"} "github.com/kotoba-lang/kotoba-core-contracts"]]])
    (ui/panel [[:h3 {:class "hig-headline"} "kotoba-lang/amu"]
               [:p {:class "hig-footnote"} "Frontend admission, effect inference, KIR, and the emit backends."]
               [:p [:a {:class "kot-link" :href "https://github.com/kotoba-lang/amu"} "github.com/kotoba-lang/amu"]]])
    (ui/panel [[:h3 {:class "hig-headline"} "kotoba-lang/kotoba"]
               [:p {:class "hig-footnote"} "Language and library substrate, host implementations, semantic-code identity, integration tests."]
               [:p [:a {:class "kot-link" :href "https://github.com/kotoba-lang/kotoba"} "github.com/kotoba-lang/kotoba"]]])
    (ui/panel [[:h3 {:class "hig-headline"} "kotoba-lang/kototama"]
               [:p {:class "hig-footnote"} "The tender: admits emitted components, links imports and exports, binds granted capabilities, enforces limits."]
               [:p [:a {:class "kot-link" :href "https://github.com/kotoba-lang/kototama"} "github.com/kotoba-lang/kototama"]]]))
   [:p {:class "hig-caption1 kot-muted"}
    "This page is generated by " (code "site/generate.cljs")
    " in kotoba-lang/kotoba-lang, from the authority files it cites: "
    (interpose ", " (for [f authority-files] (code f)))
    ". Change the spec and the page changes; it cannot claim more than the "
    "spec claims."]))

;; ---------------------------------------------------------------------------
;; app stylesheet — unlayered, token-only, no raw color/size values

(def app-css
  (str ".kot-code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;"
       "font-size:0.9em;background:var(--hig-color-quaternary-system-fill);"
       "padding:0 var(--hig-spacing-1);border-radius:var(--hig-radius-xs);"
       "white-space:nowrap}"
       ".kot-pre{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;"
       "background:var(--hig-color-quaternary-system-fill);"
       "padding:var(--hig-spacing-3);border-radius:var(--hig-radius-md);"
       "overflow-x:auto;font-size:var(--hig-text-footnote-font-size);"
       "line-height:var(--hig-text-footnote-line-height)}"
       ".kot-muted{color:var(--hig-color-secondary-label)}"
       ".kot-lede{color:var(--hig-color-label)}"
       ".kot-eyebrow{color:var(--hig-color-tertiary-label);"
       "text-transform:uppercase;letter-spacing:0.06em;"
       "margin-bottom:var(--hig-spacing-1)}"
       ".kot-grade{color:var(--hig-color-tint);margin-right:var(--hig-spacing-2)}"
       ".kot-quote{border-inline-start:var(--hig-hairline) solid var(--hig-color-tint);"
       "padding-inline-start:var(--hig-spacing-4);"
       "margin:var(--hig-spacing-5) 0;color:var(--hig-color-secondary-label)}"
       ".kot-list{padding-inline-start:var(--hig-spacing-5);"
       "color:var(--hig-color-secondary-label)}"
       ".kot-list li{margin-block:var(--hig-spacing-1)}"
       ".kot-chips{display:flex;flex-wrap:wrap;gap:var(--hig-spacing-2)}"
       ".kot-stage-heading{margin-top:var(--hig-spacing-7)}"
       ".kot-link{color:var(--hig-color-tint)}"
       ".kot-search-item[hidden]{display:none}"
       ".kot-cta{display:inline-flex;align-items:center;min-height:44px;"
       "padding:0 var(--hig-spacing-4);border-radius:var(--hig-radius-capsule);"
       "color:var(--hig-color-tint);text-decoration:none;"
       "border:var(--hig-hairline) solid var(--hig-color-separator)}"))

;; ---------------------------------------------------------------------------
;; page

(defn view []
  (ui/app-shell
   {:nav (ui/nav-bar "kotoba"
                     {:trailing [(ui/badge "capability-safe")]})}
   (ui/hero
    {:title "Kotoba"
     :tagline (str "A Clojure-shaped language that compiles to WebAssembly "
                   "components — where a program can only touch what it was "
                   "explicitly handed.")
     :actions [[:a {:class "kot-cta hig-headline"
                    :href "https://github.com/kotoba-lang/kotoba-lang"}
                "The language authority"]
               [:a {:class "kot-cta hig-headline"
                    :href "https://github.com/kotoba-lang/kotoba-lang/blob/main/lang/guest-grammar.edn"}
                "The admitted grammar"]]})
   (intro-section)
   (thesis-section)
   (source-section)
   (claims-section)
   (absent-section)
   (pipeline-section)
   (platform-section)
   (identity-section)
   (status-section)
   (release-section)
   (search-section)
   (documentation-section)
   (footer)
   [:script [:hiccup/raw search-js]]))

(def html
  (ui/->page
   {:title "Kotoba — a capability-safe language for WebAssembly components"
    :description (str "Kotoba is a Clojure-shaped, capability-confined language "
                      "that compiles to WebAssembly components. Deny-by-default "
                      "authority, declared effects, bounded admission, "
                      "reproducible artifacts.")
    :theme theme
    :head [:style [:hiccup/raw app-css]]}
   (view)))

(let [out (path/join "site" "dist")]
  (fs/mkdirSync out #js {:recursive true})
  (fs/writeFileSync (path/join out "index.html") html)
  (println "wrote" (path/join out "index.html")
           (str "(" (.-length html) " bytes)")))
