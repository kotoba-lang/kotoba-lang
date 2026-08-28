;; kotoba-lang.org — AI-native language landing page.
;;
;; The page is rendered with jp-go-dds (Digital Agency Design System), while
;; its product claims are derived from this repository's machine authorities.
;; No runtime network dependency or telemetry is added.

(require '[jp-go-dds.core :as dds]
         '[jp-go-dds.page :as page]
         '[jp-go-dds.tokens :as tokens]
         '[cljs.reader :as reader]
         '[clojure.string :as str]
         '["fs" :as fs]
         '["path" :as path])

(def authority-files
  ["lang/safety-claims.edn"
   "lang/surface-status.edn"
   "lang/elaboration-pipeline.edn"
   "lang/wasm-component-platform.edn"
   "lang/docs-release.edn"
   "docs/search-index.edn"])

(def authority
  (into {} (for [f authority-files]
             [f (reader/read-string (fs/readFileSync f "utf8"))])))

(def safety-claims  (authority "lang/safety-claims.edn"))
(def surface-status (authority "lang/surface-status.edn"))
(def platform       (authority "lang/wasm-component-platform.edn"))
(def elaboration    (authority "lang/elaboration-pipeline.edn"))
(def docs-release   (authority "lang/docs-release.edn"))
(def search-index   (authority "docs/search-index.edn"))

(def dds-root
  (or (some-> js/process.env.JP_GO_DDS_ROOT not-empty)
      (path/join ".." "jp-go-digital-design-system")))

(def dds-css-path
  (path/join dds-root "resources" "jp_go_dds" "dds.css"))

(def logo-source-path
  (path/join "site" "assets" "kotoba-wordmark.png"))

(def benchmark-source-path
  (path/join "bench" "public-compile-comparison" "latest.json"))

(def benchmark
  (js->clj (js/JSON.parse (fs/readFileSync benchmark-source-path "utf8"))
           :keywordize-keys true))

(when-not (fs/existsSync dds-css-path)
  (println "site/generate.cljs: jp-go-dds CSS not found:" dds-css-path)
  (println "  set JP_GO_DDS_ROOT to the jp-go-digital-design-system checkout")
  (js/process.exit 1))

(when-not (fs/existsSync logo-source-path)
  (println "site/generate.cljs: Kotoba wordmark not found:" logo-source-path)
  (js/process.exit 1))

(def dds-css (fs/readFileSync dds-css-path "utf8"))

(defn code [s] [:code {:class "kot-code"} s])
(defn caption [& children] (into [:p {:class "kot-muted kot-caption"}] children))
(defn external-link [href label]
  [:a {:class "kot-link" :href href :rel "noreferrer"} label])
(defn bullets [items]
  (into [:ul {:class "kot-list"}] (for [item items] [:li item])))
(defn card [& children] (apply dds/card children))

(def app-css
  (str
   ".kot-skip{position:absolute;inset-inline-start:var(--hig-spacing-2);"
   "transform:translateY(-150%);padding:var(--hig-spacing-2) var(--hig-spacing-3);"
   "background:var(--hig-color-system-background);color:var(--hig-color-label);z-index:3}"
   ".kot-skip:focus{transform:translateY(var(--hig-spacing-2))}"
   ".kot-header{position:relative;z-index:2;background:var(--hig-color-system-background);"
   "border-bottom:var(--hig-hairline) solid var(--hig-color-separator)}"
   ".kot-header__inner{display:flex;align-items:flex-start;flex-direction:column;"
   "gap:var(--hig-spacing-3);padding-block:var(--hig-spacing-3)}"
   ".kot-wordmark{display:inline-flex;align-items:center;text-decoration:none}"
   ".kot-logo{display:block;height:var(--hig-spacing-7);width:auto}"
   ".kot-nav{display:flex;align-items:center;justify-content:flex-start;flex-wrap:wrap;"
   "gap:var(--hig-spacing-2);width:100%}"
   ".kot-hero{padding-block:var(--hig-spacing-8)}"
   ".kot-eyebrow{margin:0 0 var(--hig-spacing-3);color:var(--hig-color-tint);"
   "font-weight:700;letter-spacing:.06em;text-transform:uppercase}"
   ".kot-hero h1{max-width:18ch;margin:0 0 var(--hig-spacing-4)}"
   ".kot-lead{max-width:48rem;margin:0;color:var(--hig-color-secondary-label)}"
   ".kot-actions{display:grid;grid-template-columns:minmax(0,1fr);gap:var(--hig-spacing-3);"
   "margin-top:var(--hig-spacing-6)}"
   ".kot-proof{margin-top:var(--hig-spacing-8)}"
   ".kot-card-title{margin-top:0}"
   ".kot-metric{margin:0 0 var(--hig-spacing-2);color:var(--hig-color-tint);font-weight:700}"
   ".kot-muted{color:var(--hig-color-secondary-label)}"
   ".kot-caption{font-size:var(--hig-text-footnote-font-size);"
   "line-height:var(--hig-text-footnote-line-height)}"
   ".kot-link{color:var(--hig-color-tint);text-underline-offset:.18em}"
   ".kot-search-item[hidden]{display:none}"
   ".kot-code{font-family:var(--hig-font-mono);font-size:var(--hig-text-footnote-font-size);"
   "background:var(--hig-color-quaternary-system-fill);padding:0 var(--hig-spacing-1);"
   "border-radius:var(--hig-radius-xs);overflow-wrap:anywhere}"
   ".kot-pre{font-family:var(--hig-font-mono);font-size:var(--hig-text-footnote-font-size);"
   "line-height:var(--hig-text-footnote-line-height);margin:0;overflow-x:auto;"
   "padding:var(--hig-spacing-4);background:var(--hig-color-quaternary-system-fill);"
   "border-radius:var(--hig-radius-md)}"
   ".kot-list{padding-inline-start:var(--hig-spacing-5)}"
   ".kot-list li+li{margin-top:var(--hig-spacing-2)}"
   ".kot-quote{margin:var(--hig-spacing-5) 0 0;padding-inline-start:var(--hig-spacing-4);"
   "border-inline-start:var(--hig-hairline) solid var(--hig-color-tint)}"
   ".kot-table-scroll{max-width:100%;overflow-x:auto}"
   ".kot-footer{padding-block:var(--hig-spacing-7);"
   "border-top:var(--hig-hairline) solid var(--hig-color-separator)}"
   "@media(min-width:36rem){.kot-actions{display:flex;flex-wrap:wrap}}"
   "@media(min-width:48rem){.kot-header{position:sticky;top:0}"
   ".kot-header__inner{align-items:center;flex-direction:row;justify-content:space-between}"
   ".kot-nav{justify-content:flex-end;width:auto}.kot-hero{padding-block:var(--hig-spacing-10) var(--hig-spacing-9)}}"))

(def primary-links
  [{:label "Why" :href "#why"}
   {:label "What" :href "#what"}
   {:label "Proof" :href "#proof"}
   {:label "Benchmark" :href "#benchmark"}
   {:label "Agents" :href "./llms.txt"}
   {:label "Architecture" :href "#architecture"}])

(def proof-signals
  [{:metric "33 cores"
    :title "Internal production dogfooding"
    :body "The wider Kotoba stack runs 33 inference cores internally. This proves the team operates its own stack; it is not customer traction, paid adoption, or revenue."}
   {:metric "8 claims"
    :title "Boundaries are machine-readable"
    :body "Safety claims name their trusted computing base, negative evidence, and residual risk instead of collapsing into an 'unhackable' slogan."}
   {:metric "deny by default"
    :title "No grant, no host effect"
    :body "An empty policy grants no filesystem, network, process, clock, model, or secret authority. Providers must also validate concrete resource scope."}])

(defn header []
  [:header {:class "kot-header"}
   (dds/container
    [:div {:class "kot-header__inner"}
     [:a {:class "kot-wordmark" :href "#top" :aria-label "Kotoba home"}
      [:img {:class "kot-logo" :src "./kotoba-wordmark.png"
             :width 480 :height 68 :alt "Kotoba"}]]
     [:nav {:class "kot-nav" :aria-label "Primary"}
      (for [{:keys [label href]} primary-links]
        (dds/button label {:type :text :size "sm" :href href}))
      (dds/button "GitHub" {:type :outline :size "sm"
                             :href "https://github.com/kotoba-lang/kotoba-lang"})]])])

(defn hero []
  [:section {:id "top" :class "kot-hero"}
   (dds/container
    [:p {:class "kot-eyebrow"} "A language AI agents can use, not abuse"]
    (dds/heading 1 "AI writes freely. Kotoba draws the boundary." {:size "48"})
    [:p {:class "kot-lead"}
     "Kotoba is an intuitive, declarative, security-first language and computing stack for AI agents—and for humans who vibe-code with them."]
    [:blockquote {:class "kot-quote"}
     [:strong "Existing software adds security around the program. Kotoba makes security a property of the whole computation."]]
    [:div {:class "kot-actions"}
     (dds/button "See how it works" {:href "#architecture" :size "lg"})
     (dds/button "Start with Kotoba" {:href "#start" :type :outline :size "lg"})
     (dds/button "AI agent setup" {:href "./agent-quickstart.md" :type :text :size "lg"})]
    [:div {:class "kot-proof"}
     (dds/grid {:min "14rem"}
      (card (dds/chip-label "DENY BY DEFAULT")
            (dds/heading 3 "No ambient authority" {:size "20"})
            [:p "No implicit filesystem, network, process, clock, model, or secrets."])
      (card (dds/chip-label "CHECKED KIR")
            (dds/heading 3 "Authority survives compilation" {:size "20"})
            [:p "Types, effects, resources, and target support are admitted before emission."])
      (card (dds/chip-label "HOST ENFORCED")
            (dds/heading 3 "Only the grant is bound" {:size "20"})
            [:p "The host and provider enforce concrete scope and record the decision."]))])])

(defn architecture-section []
  (dds/section
   {:id "architecture" :title "Security across the whole computation"}
   [:p {:class "kot-lead"}
    "The boundary is carried from intent to execution. Each stage narrows or verifies authority; no later stage is allowed to invent a grant."]
   (dds/grid
    {:min "13rem"}
    (card (dds/chip-label "1 · SOURCE")
          (dds/heading 3 "Declarative intent" {:size "20"})
          [:p "A small, Clojure-shaped surface keeps programs readable and excludes ambient escape hatches."])
    (card (dds/chip-label "2 · CHECK")
          (dds/heading 3 "Checked KIR" {:size "20"})
          [:p "Types and transitive effects become a target-independent, inspectable representation."])
    (card (dds/chip-label "3 · ADMIT")
          (dds/heading 3 "Intersect authority" {:size "20"})
          [:p "Requested, delegated, local-policy, resource, and target grants can only narrow."])
    (card (dds/chip-label "4 · IDENTIFY")
          (dds/heading 3 "Address the artifact" {:size "20"})
          [:p "Code, dependencies, policy, compiler contract, and target ABI bind the computation's identity."])
    (card (dds/chip-label "5 · ENFORCE")
          (dds/heading 3 "Bind at the host" {:size "20"})
          [:p "The runtime and provider bind only admitted capabilities, enforce finite budgets, and emit receipts."]))
   [:blockquote {:class "kot-quote"}
    [:strong "Content identity is not authority."]
    [:p "CID verification, signatures, revocation, host policy, resource checks, and OS isolation remain separate boundaries."]]))

(defn why-section []
  (dds/section
   {:id "why" :title "AI can write faster than humans can review"}
   [:p {:class "kot-lead"}
    "Generated code may be useful and still reach a file, network, secret, process, model, or payment surface the request never intended to expose."]
   (dds/grid
    {:min "18rem"}
    (card (dds/chip-label "THE OLD DEFAULT")
          (dds/heading 3 "Build broadly, constrain later" {:size "24"})
          [:p "A general-purpose program starts with ambient semantics. Sandboxes, IAM, containers, policy, and signing are added around it to recover the intended boundary."])
    (card (dds/chip-label "THE KOTOBA DEFAULT")
          (dds/heading 3 "Grant narrowly, then compile" {:size "24"})
          [:p "Effects and capabilities are part of the admitted computation. If the target cannot prove and bind the grant, it does not emit or run the artifact."]))
   [:p {:class "kot-caption kot-muted"}
    "Kotoba complements runtime and OS isolation; it does not make those layers unnecessary."]))

(defn what-section []
  (dds/section
   {:id "what" :title "Where Lisp's mind meets Rust's discipline"}
   [:p {:class "kot-lead"}
    "Kotoba is a small, data-oriented, Clojure-shaped language with static discipline around authority, effects, resources, packages, and artifact identity."]
   (dds/grid
    {:min "18rem"}
    (card (dds/chip-label "INTUITIVE")
          (dds/heading 3 "Code as readable data" {:size "24"})
          [:p "Immutable values, ordinary functions, explicit data, and a composable syntax are easy for humans and models to produce and inspect."])
    (card (dds/chip-label "DECLARATIVE")
          (dds/heading 3 "Say what may happen" {:size "24"})
          [:p "Effects, capabilities, resources, dependencies, and targets are visible inputs to admission—not surprises discovered after deployment."])
    (card (dds/chip-label "SECURITY-FIRST")
          (dds/heading 3 "Less language, harder boundary" {:size "24"})
          [:p "No ambient interop, runtime code loading, unrestricted mutation, guest-defined macros, or unbounded concurrency in the admitted component surface."]))
   [:blockquote {:class "kot-quote"}
    [:strong "A language AI agents can use, not abuse."]
    [:p "This is a confinement direction, not an 'unhackable' claim. The compiler, verifier, runtime, providers, policy roots, key custody, and OS isolation remain in the trusted computing base."]]))

(defn proof-section []
  (apply dds/section
         {:id "proof" :title "Proof, with the boundary attached"}
         [:p {:class "kot-lead"}
          "Kotoba separates implementation evidence from market traction and keeps residual risk next to every safety claim."]
         (dds/grid {:min "17rem"}
          (for [{:keys [metric title body]} proof-signals]
            (card [:p {:class "kot-metric"} metric]
                  (dds/heading 3 title {:size "24"})
                  [:p body])))
         [[:p {:class "kot-caption kot-muted"}
           "Internal production use is dogfooding evidence only. It does not imply external customers, paid pilots, or revenue."]]))

(defn start-section []
  (dds/section
   {:id "start" :title "Start in sixty seconds"}
   (dds/grid
    {:min "20rem"}
    (card (dds/heading 3 "Install and self-check" {:size "24"})
          [:pre {:class "kot-pre"}
           [:code "brew tap kotoba-lang/kotoba\nbrew trust kotoba-lang/kotoba\nbrew install kotoba\nkotoba selfhost check --json"]]
          (caption "Accept a valid response with an empty problem list."))
    (card (dds/heading 3 "Start with no authority" {:size "24"})
          [:p "An empty policy denies every host effect. Add only the resource-scoped capability the program needs."]
          [:pre {:class "kot-pre"}
           [:code "{:policy/allow #{}\n :policy/forbid-wildcard true}"]]
          (caption "HTTP, storage, and LLM hosted kits are not yet qualified for sale on a shipped backend.")))
   [:div {:class "kot-actions"}
    (dds/button "AI agent: executable quickstart"
                {:href "./agent-quickstart.md"})
    (dds/button "Open the getting-started guide"
                {:href "https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/getting-started.md"
                 :type :outline})
    (dds/button "Read CLI reference"
                {:href "https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/generated/cli.md"
                 :type :outline})]))

(defn benchmark-section []
  (let [kotoba (get-in benchmark [:results :kotoba])
        rust (get-in benchmark [:results :rust])
        ratio (get-in benchmark [:results :medianRatioKotobaToRust])
        runs (get-in benchmark [:method :runs])
        chip (get-in benchmark [:environment :chip])
        measured-date (subs (:generatedAt benchmark) 0 10)
        kotoba-version (str/upper-case (get-in benchmark [:environment :kotoba]))
        rust-version (str/join " " (take 2 (str/split (get-in benchmark [:environment :rustc]) #" ")))]
    (dds/section
     {:id "benchmark" :title "Measured against Rust, with the boundary attached"}
     [:p {:class "kot-lead"}
      "A process-cold comparison of two tiny programs that export " (code "main")
      ", return i64 42, emit WebAssembly, require zero imports, and are executed after every compile."]
     (dds/grid
      {:min "16rem"}
      (card (dds/chip-label kotoba-version)
            (dds/heading 3 (str (:medianMilliseconds kotoba) " ms median") {:size "24"})
            (caption (str (:p95Milliseconds kotoba) " ms p95 · " runs " samples")))
      (card (dds/chip-label (str/upper-case rust-version))
            (dds/heading 3 (str (:medianMilliseconds rust) " ms median") {:size "24"})
            (caption (str (:p95Milliseconds rust) " ms p95 · " runs " samples")))
      (card (dds/chip-label "THIS RUN")
            (dds/heading 3 (str ratio "× Rust elapsed") {:size "24"})
            (caption (str measured-date " · " chip " · alternating order"))))
     [:p
      "This measures tiny-workload toolchain startup on one machine, not general compile speed. "
      "The modules have different ABIs and runtime contracts, so their byte sizes are not ranked."]
     [:div {:class "kot-actions"}
      (dds/button "Inspect every sample"
                  {:href "./benchmarks/compile-wasm-latest.json"})
      (dds/button "Re-run the harness"
                  {:href "https://github.com/kotoba-lang/kotoba-lang/tree/main/bench/public-compile-comparison"
                   :type :outline})])))

(defn claims-section []
  (dds/section
   {:id "evidence" :title "Claims with their boundaries attached"}
   [:p {:class "kot-lead"}
    "These claims are generated from " (code "lang/safety-claims.edn") ". Each keeps its trusted computing base and residual risk visible, because a safety slogan without a boundary is only marketing."]
   (dds/grid
    {:min "19rem"}
    (for [{:keys [id claim tcb residual-risk]} (:claims safety-claims)]
      (card
       (dds/chip-label (str/upper-case (name id)))
       [:p claim]
       (dds/divider)
       [:p [:strong "Trusted computing base"]]
       (caption (str/join " · " tcb))
       [:p [:strong "Residual risk"]]
       (bullets residual-risk))))
   (caption "Qualification "
            (str/upper-case (name (:kotoba.lang.safety-claims/qualification safety-claims)))
            ", as of " (:kotoba.lang.safety-claims/as-of safety-claims) ".")))

(defn deliberate-section []
  (let [invariants (:invariants surface-status)]
    (dds/section
     {:title "What AI-written Kotoba cannot ask for"}
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "Deliberately absent language surface"
        :headers ["Boundary" "Why it is absent"]
        :row-header? true
        :rows (for [k [:no-ambient-authority :no-interop :no-ambient-mutation
                       :no-unbounded-concurrency :no-guest-macros :explicit-errors]
                    :let [{:keys [surface reason]} (get invariants k)]]
                [(str/join ", " (sort (map name surface))) reason])})]
     (caption "These are named security constraints in lang/surface-status.edn, not features missing from a roadmap."))))

(defn release-section []
  (let [contract (:contract docs-release)
        implementation (:implementation-release docs-release)
        public (:public-default docs-release)]
    (dds/section
     {:title "Release binding"}
     [:p {:class "kot-lead"}
      "A language profile and an implementation release are separate until a signed envelope binds them."]
     (dds/grid
      {:min "16rem"}
      (card (dds/chip-label "LANGUAGE")
            (dds/heading 3 (str "Profile " (:language-profile contract)) {:size "24"})
            (caption "package contract " (:package-contract contract)))
      (card (dds/chip-label "IMPLEMENTATION")
            (dds/heading 3 (:tag implementation) {:size "24"})
            (caption "profile binding: " (name (:language-profile-binding implementation))))
      (card (dds/chip-label "PUBLIC DEFAULT")
            (dds/heading 3 (str/upper-case (name (:status public))) {:size "24"})
            (caption (str (:code public)))))
     [:p (:reason public)]
     (external-link "https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/generated/release.md"
                    "Read the generated release evidence"))))

(defn search-url [doc-path]
  (str "https://github.com/kotoba-lang/kotoba-lang/blob/main/" doc-path))

(defn search-section []
  (dds/section
   {:title "Search the checked reference"}
   [:p {:class "kot-lead"}
    "Search commands, standard-library names, diagnostics, and release status. The index is generated from machine authorities and stays in this page."]
   (dds/form-field
    {:label "Search Kotoba documentation" :for "kot-doc-search"
     :support "Try: compile, option-some, docs/link-missing"
     :support-id "kot-doc-search-support"}
    (dds/input-text {:id "kot-doc-search" :type "search"
                     :aria-label "Search Kotoba documentation reference"
                     :aria-describedby "kot-doc-search-support"}))
   [:p {:id "kot-doc-search-count" :class "kot-caption kot-muted" :aria-live "polite"}]
   [:div {:id "kot-doc-search-results"}
    (for [{:keys [kind title body url keywords]} search-index
          :let [haystack (str/lower-case (str title " " body " " (str/join " " keywords)))]]
      [:div {:class "kot-search-item" :data-search haystack}
       (card (dds/chip-label (name kind))
             (dds/heading 3 title {:size "20"})
             [:p body]
             (external-link (search-url url) "Open reference"))])]
   (caption "No query leaves the browser.")))

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

(defn source-section []
  (dds/section
   {:id "source" :title "Read the contract or run the implementation"}
   (dds/grid
    {:min "18rem"}
    (card (dds/chip-label "LANGUAGE AUTHORITY")
          (dds/heading 3 "kotoba-lang/kotoba-lang" {:size "20"})
          [:p "Grammar, semantics, capability contracts, safety claims, CLI contract, documentation, and conformance fixtures."]
          (external-link "https://github.com/kotoba-lang/kotoba-lang" "Read the language authority"))
    (card (dds/chip-label "INSTALLABLE IMPLEMENTATION")
          (dds/heading 3 "kotoba-lang/kotoba" {:size "20"})
          [:p "CLI, host integrations, providers, runtime adapters, integration tests, and target-specific qualification evidence."]
          (external-link "https://github.com/kotoba-lang/kotoba" "Open the implementation"))
    (card (dds/chip-label "DOCUMENTATION")
          (dds/heading 3 "Learn, build, or evaluate" {:size "20"})
          [:p "Separate paths for first use, language reference, backend implementation, security boundaries, and maturity evidence."]
          (external-link "https://github.com/kotoba-lang/kotoba-lang/tree/main/docs" "Choose a documentation path")))
   [:p {:class "kot-caption kot-muted"}
    "Language profile " (get-in docs-release [:contract :language-profile])
    "; public-default release status: "
    (str/upper-case (name (get-in docs-release [:public-default :status]))) ". "]
   [:p {:class "kot-caption kot-muted"}
    "The primary portable platform is WebAssembly Components with WASI "
    (get-in platform [:upstream :wasi :version]) ". The elaboration pipeline has "
    (count (:pipeline elaboration)) " named, fail-closed stages."]))

(defn footer []
  [:footer {:class "kot-footer"}
   (dds/container
    [:p [:strong "Kotoba"] " — AI writes freely. Kotoba draws the boundary."]
    [:p {:class "kot-caption kot-muted"}
     "Generated by " (code "site/generate.cljs") " from "
     (str/join ", " authority-files) ". No telemetry. No runtime dependency."]
    [:p (external-link "https://github.com/kotoba-lang/kotoba-lang" "Source and license")])])

(defn view []
  [:div
   [:a {:class "kot-skip" :href "#main"} "Skip to content"]
   (header)
   [:main {:id "main"}
    (hero)
    (dds/container
     (why-section)
     (what-section)
     (proof-section)
     (architecture-section)
     (start-section)
     (benchmark-section)
     (claims-section)
     (deliberate-section)
     (release-section)
     (search-section)
     (source-section))]
   (footer)
   [:script search-js]])

(def html
  (page/->page
   {:title "Kotoba — security-first computing for AI agents and vibe coding"
    :description (str "AI writes freely. Kotoba draws the boundary. An intuitive, declarative, "
                      "security-first language and computing stack with checked KIR, explicit "
                      "capability and effect admission, content-addressed artifacts, and host enforcement.")
    :lang "en"
    :css dds-css
    :app-css (str tokens/skin-css "\n" app-css)}
   (view)))

(let [out (path/join "site" "dist")]
  (fs/mkdirSync out #js {:recursive true})
  (fs/writeFileSync (path/join out "index.html") html)
  (fs/copyFileSync logo-source-path (path/join out "kotoba-wordmark.png"))
  (doseq [[source target]
          [[benchmark-source-path (path/join out "benchmarks" "compile-wasm-latest.json")]
           [(path/join "site" "assets" "llms.txt") (path/join out "llms.txt")]
           [(path/join "site" "assets" "llms-full.txt") (path/join out "llms-full.txt")]
           [(path/join "site" "assets" "agent-quickstart.md") (path/join out "agent-quickstart.md")]]]
    (fs/mkdirSync (path/dirname target) #js {:recursive true})
    (fs/copyFileSync source target))
  ;; RFC 9116. Copied rather than generated so the published contact is a file
  ;; someone can read and edit in `site/assets/`, not a string buried in here —
  ;; and so a regeneration cannot silently drop it (a security.txt that
  ;; disappears looks exactly like one that was never published).
  (let [wk (path/join out ".well-known")]
    (fs/mkdirSync wk #js {:recursive true})
    (fs/copyFileSync (path/join "site" "assets" "security.txt")
                     (path/join wk "security.txt")))
  ;; Public machine contract for the external-trust discovery documents served
  ;; by Kotobase, Murakumo and Itonami. identity owns the schema and policy;
  ;; this authority site is only their deterministic HTTPS projection.
  (let [identity-root (or (.-KOTOBA_IDENTITY_ROOT js/process.env)
                          (path/join ".." "identity"))
        copies [[(path/join identity-root "resources" "public" "schemas"
                            "trust-profile" "v1.json")
                 (path/join out "schemas" "trust-profile" "v1")]
                [(path/join identity-root "resources" "public" "policies"
                            "trust" "human-passport" "itonami-v1.json")
                 (path/join out "policies" "trust" "human-passport"
                            "itonami-v1.json")]
                [(path/join identity-root "resources" "public" "policies"
                            "trust" "eas" "kotobase-v1.json")
                 (path/join out "policies" "trust" "eas"
                            "kotobase-v1.json")]]]
    (doseq [[source target] copies]
      (when-not (fs/existsSync source)
        (throw (js/Error. (str "required identity trust contract missing: " source))))
      (fs/mkdirSync (path/dirname target) #js {:recursive true})
      (fs/copyFileSync source target)))
  (println "wrote" (path/join out "index.html")
           (str "(" (.-length html) " bytes)")))
