;; kotoba-lang.org — AI-native language landing page.
;;
;; The page is rendered with jp-go-dds (Digital Agency Design System), while
;; its product claims are derived from this repository's machine authorities.
;; No third-party runtime dependency or telemetry is added. Play fetches one
;; same-origin, digest-bound Wasm artifact generated from the checked source.

(require '[jp-go-dds.core :as dds]
         '[jp-go-dds.page :as page]
         '[jp-go-dds.tokens :as tokens]
         '[kotoba.grammar.highlight :as grammar-highlight]
         '[cljs.reader :as reader]
         '[clojure.string :as str]
         '["crypto" :as crypto]
         '["fs" :as fs]
         '["path" :as path])

(def authority-files
  ["lang/safety-claims.edn"
   "lang/surface-status.edn"
   "lang/elaboration-pipeline.edn"
   "lang/wasm-component-platform.edn"
   "lang/library-publication.edn"
   "lang/docs-release.edn"
   "docs/search-index.edn"])

(def authority
  (into {} (for [f authority-files]
             [f (reader/read-string (fs/readFileSync f "utf8"))])))

(def safety-claims  (authority "lang/safety-claims.edn"))
(def surface-status (authority "lang/surface-status.edn"))
(def platform       (authority "lang/wasm-component-platform.edn"))
(def elaboration    (authority "lang/elaboration-pipeline.edn"))
(def library-publication (authority "lang/library-publication.edn"))
(def docs-release   (authority "lang/docs-release.edn"))
(def search-index   (authority "docs/search-index.edn"))

(def dds-root
  (or (some-> js/process.env.JP_GO_DDS_ROOT not-empty)
      (path/join ".." "jp-go-digital-design-system")))

(def grammar-root
  (or (some-> js/process.env.KOTOBA_GRAMMAR_ROOT not-empty)
      (path/join ".." "grammar")))

(def dds-css-path
  (path/join dds-root "resources" "jp_go_dds" "dds.css"))

(def syntax-grammar-path
  (path/join grammar-root "syntaxes" "kotoba.tmLanguage.json"))

(def dependency-manifest-path (path/join "site" "dependencies.edn"))

(def logo-source-path
  (path/join "site" "assets" "kotoba-wordmark.png"))

(def benchmark-source-path
  (path/join "bench" "public-compile-comparison" "latest.json"))

(def runtime-benchmark-source-path
  (path/join "bench" "public-runtime-comparison" "latest.json"))

(def play-source-path (path/join "site" "assets" "play" "double-21.kotoba"))
(def play-wasm-path (path/join "site" "assets" "play" "double-21.wasm"))
(def play-provenance-path
  (path/join "site" "assets" "play" "double-21.wasm.provenance.edn"))

(def benchmark
  (js->clj (js/JSON.parse (fs/readFileSync benchmark-source-path "utf8"))
           :keywordize-keys true))

(def runtime-benchmark
  (js->clj (js/JSON.parse (fs/readFileSync runtime-benchmark-source-path "utf8"))
           :keywordize-keys true))

(def play-source (fs/readFileSync play-source-path "utf8"))
(def play-provenance (reader/read-string (fs/readFileSync play-provenance-path "utf8")))
(def play-sha256 (get-in play-provenance [:outputs :primary :sha256]))
(def dependencies (reader/read-string (fs/readFileSync dependency-manifest-path "utf8")))
(def syntax-dependency
  (first (filter #(= :syntax-highlighting (:id %)) (:build-time dependencies))))

(when-not (fs/existsSync dds-css-path)
  (println "site/generate.cljs: jp-go-dds CSS not found:" dds-css-path)
  (println "  set JP_GO_DDS_ROOT to the jp-go-digital-design-system checkout")
  (js/process.exit 1))

(when-not (fs/existsSync logo-source-path)
  (println "site/generate.cljs: Kotoba wordmark not found:" logo-source-path)
  (js/process.exit 1))

(when-not (fs/existsSync syntax-grammar-path)
  (println "site/generate.cljs: Kotoba TextMate grammar not found:" syntax-grammar-path)
  (println "  set KOTOBA_GRAMMAR_ROOT to the kotoba-lang/grammar checkout")
  (js/process.exit 1))

(def dds-css (fs/readFileSync dds-css-path "utf8"))
(def syntax-grammar-json (fs/readFileSync syntax-grammar-path "utf8"))
(def syntax-grammar
  (js->clj (js/JSON.parse syntax-grammar-json) :keywordize-keys true))
(def syntax-grammar-sha256
  (.digest (.update (crypto/createHash "sha256") syntax-grammar-json) "hex"))

(when-not (= (:artifact-sha256 syntax-dependency) syntax-grammar-sha256)
  (throw (js/Error.
          (str "Kotoba syntax grammar digest does not match site/dependencies.edn: "
               syntax-grammar-sha256))))

(when-not (= (:scope syntax-dependency) (:scopeName syntax-grammar))
  (throw (js/Error.
          (str "Kotoba syntax scope does not match site/dependencies.edn: "
               (:scopeName syntax-grammar)))))

(defn code [s] [:code {:class "kot-code"} s])
(defn caption [& children] (into [:p {:class "kot-muted kot-caption"}] children))
(defn external-link [href label]
  [:a {:class "kot-link" :href href :rel "noreferrer"} label])
(defn bullets [items]
  (into [:ul {:class "kot-list"}] (for [item items] [:li item])))
(defn card [& children] (apply dds/card children))

(defn scope-kind [scope]
  (cond
    (nil? scope) :plain
    (str/starts-with? scope "comment.") :comment
    (str/starts-with? scope "string.") :string
    (str/starts-with? scope "constant.numeric.") :number
    (str/starts-with? scope "constant.language.") :literal
    (str/starts-with? scope "constant.other.keyword.") :keyword
    (str/starts-with? scope "invalid.") :forbidden
    (str/starts-with? scope "keyword.") :form
    (str/starts-with? scope "support.function.") :function
    (str/starts-with? scope "entity.name.") :definition
    (str/starts-with? scope "punctuation.") :delimiter
    :else :symbol))

(defn highlighted-kotoba [source]
  (let [tokens (mapv #(assoc % :kind (scope-kind (:scope %)))
                     (grammar-highlight/tokenize source))
        reconstructed (apply str (map :text tokens))]
    (when-not (= source reconstructed)
      (throw (js/Error. "Kotoba syntax highlighting changed the displayed source")))
    (into [:code {:class "kot-source" :aria-label "Kotoba source code"}]
          (map (fn [{:keys [kind text]}]
                 (if (= :plain kind)
                   text
                   [:span {:class (str "kot-syntax-" (name kind))} text])))
          tokens)))

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
   ".kot-play{display:grid;gap:var(--hig-spacing-4)}"
   ".kot-play-status{min-height:1.5em;margin:0;font-family:var(--hig-font-mono)}"
   ".kot-explore{margin-top:var(--hig-spacing-5)}"
   ".kot-blog-entry+ .kot-blog-entry{margin-top:var(--hig-spacing-7);padding-top:var(--hig-spacing-7);"
   "border-top:var(--hig-hairline) solid var(--hig-color-separator)}"
   ".kot-code{font-family:var(--hig-font-mono);font-size:var(--hig-text-footnote-font-size);"
   "background:var(--hig-color-quaternary-system-fill);padding:0 var(--hig-spacing-1);"
   "border-radius:var(--hig-radius-xs);overflow-wrap:anywhere}"
   ".kot-pre{font-family:var(--hig-font-mono);font-size:var(--hig-text-footnote-font-size);"
   "line-height:var(--hig-text-footnote-line-height);margin:0;overflow-x:auto;"
   "padding:var(--hig-spacing-4);background:var(--hig-color-quaternary-system-fill);"
   "border-radius:var(--hig-radius-md)}"
   ".kot-source{display:block;white-space:pre;tab-size:2}"
   ".kot-syntax-comment{color:var(--hig-color-tertiary-label);font-style:italic}"
   ".kot-syntax-form,.kot-syntax-keyword,.kot-syntax-function{color:var(--hig-color-tint);font-weight:700}"
   ".kot-syntax-definition{color:var(--hig-color-label);font-weight:700;text-decoration:underline;"
   "text-decoration-color:var(--hig-color-tint);text-underline-offset:.18em}"
   ".kot-syntax-number,.kot-syntax-string,.kot-syntax-literal{color:var(--hig-color-label);font-weight:700}"
   ".kot-syntax-delimiter{color:var(--hig-color-secondary-label)}"
   ".kot-syntax-symbol{color:var(--hig-color-label)}"
   ".kot-syntax-forbidden{color:var(--hig-color-label);font-weight:700;text-decoration:underline wavy;"
   "text-decoration-color:var(--hig-color-tint);text-underline-offset:.18em}"
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
  [{:label "Docs" :href "#docs"}
   {:label "Play" :href "#play"}
   {:label "Libraries" :href "#libraries"}
   {:label "Roadmap" :href "#roadmap"}
   {:label "Community" :href "#community"}
   {:label "Blog" :href "./blog/"}
   {:label "Cloud" :href "#cloud"}])

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

(defn header
  ([] (header ""))
  ([root]
   (let [local-href (fn [href]
                      (cond
                        (str/blank? root) href
                        (str/starts-with? href "#") (str root href)
                        (str/starts-with? href "./") (str root (subs href 2))
                        :else href))]
     [:header {:class "kot-header"}
      (dds/container
       [:div {:class "kot-header__inner"}
        [:a {:class "kot-wordmark" :href (str root "#top") :aria-label "Kotoba home"}
         [:img {:class "kot-logo" :src (str root "kotoba-wordmark.png")
                :width 480 :height 68 :alt "Kotoba"}]]
        [:nav {:class "kot-nav" :aria-label "Primary"}
         (for [{:keys [label href]} primary-links]
           (dds/button label {:type :text :size "sm" :href (local-href href)}))
         (dds/button "GitHub" {:type :outline :size "sm"
                                :href "https://github.com/kotoba-lang/kotoba-lang"})]])])))

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

(defn developer-section []
  (dds/section
   {:id "docs" :title "Learn, try, then go deeper"}
   [:p {:class "kot-lead"}
    "A connected path from first program to language contracts, libraries, evidence, and deployment surfaces."]
   (dds/grid
    {:min "16rem"}
    (card (dds/chip-label "LEARN")
          (dds/heading 3 "Docs by intent" {:size "24"})
          [:p "Start with installation, learn the admitted language, or inspect the normative semantics and conformance data."]
          (external-link "https://github.com/kotoba-lang/kotoba-lang/tree/main/docs" "Open documentation map"))
    (card (dds/chip-label "READ CODE")
          (dds/heading 3 "One source, one answer" {:size "24"})
          [:p "The example below is the exact source compiled into the browser demo—not a JavaScript reimplementation."]
          [:a {:class "kot-link" :href "#code"} "Read the sample"])
    (card (dds/chip-label "RUN")
          (dds/heading 3 "Execute in this page" {:size "24"})
          [:p "Load a same-origin, digest-bound WebAssembly artifact and call its exported Kotoba main function."]
          [:a {:class "kot-link" :href "#play"} "Open Play"])
    (card (dds/chip-label "BUILD")
          (dds/heading 3 "Libraries and contracts" {:size "24"})
          [:p "Browse bounded core names, foundational libraries, package rules, and their current maturity boundary."]
          [:a {:class "kot-link" :href "#libraries"} "Browse libraries"]))))

(defn code-play-section []
  (dds/section
   {:id "code" :title "A small Kotoba program, running for real"}
   [:p {:class "kot-lead"}
    "Amu compiles this pure Kotoba source to the wasm32-browser profile. The checked-in artifact has no imports and returns 42."]
   (dds/grid
    {:min "21rem"}
    (card (dds/chip-label "KOTOBA SOURCE")
          [:pre {:class "kot-pre"} (highlighted-kotoba play-source)]
          (caption "Highlighting authority: "
                   (external-link "https://github.com/kotoba-lang/grammar" "kotoba-lang/grammar")
                   " → " (code "kotoba.grammar.highlight/tokenize") " → build-time HTML. "
                   "Editor scope contract: " (code "source.kotoba") ". "
                   "Browser highlighter dependency: none. "
                   [:a {:class "kot-link" :href "./dependencies.edn"} "Inspect dependencies"])
          (caption "Compile locally: kotoba compile double-21.kotoba --target wasm32-browser --output double-21.wasm"))
    (card [:div {:id "play" :class "kot-play"}
           (dds/chip-label "PLAY · WEBASSEMBLY")
           (dds/heading 3 "Run the verified artifact" {:size "24"})
           [:p "The browser fetches 344 bytes, verifies SHA-256, rejects every import, instantiates the module, and calls main()."]
           [:p [:strong "Expected result: "] (code "42")]
           (dds/button "Run Kotoba" {:id "kot-play-run" :size "lg"})
           [:p {:id "kot-play-status" :class "kot-play-status kot-muted"
                :role "status" :aria-live "polite"}
            "Ready. No code has run yet."]
           (caption "This executes a precompiled, immutable example. Editing arbitrary source in the browser is not yet a shipped compiler surface.")]))))

(defn libraries-section []
  (dds/section
   {:id "libraries" :title "Libraries, without hiding the package boundary"}
   [:p {:class "kot-lead"}
    "Kotoba libraries are content-addressed graphs. Names and GitHub repositories help people discover them; definition and signed release CIDs say exactly what they are."]
   (dds/grid
    {:min "18rem"}
    (card (dds/chip-label "BOUNDED CORE")
          (dds/heading 3 "Generated symbol reference" {:size "24"})
          [:p "Search the names admitted by the current bounded standard-library contract."]
          (external-link "https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/generated/stdlib.md" "Browse core symbols"))
    (card (dds/chip-label "FOUNDATIONAL")
          (dds/heading 3 "Data, effects, I/O, tooling" {:size "24"})
          [:p "Start with coll, spec, json, text, wit, async, time, fs, http, test, fmt, lint, and LSP contracts."]
          (external-link "https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/reference/tooling.md#standard-library" "Browse the library map"))
    (card (dds/chip-label "PACKAGE CONTRACT")
          (dds/heading 3 "Content-addressed dependencies" {:size "24"})
          [:p "Inspect exact dependency CIDs, identity layers, GitHub provenance, and the current publication boundary."]
          [:a {:class "kot-link" :href "./libraries/"} "Open the library catalog and publish flow"]))
   (caption "Repository maturity labels do not imply 1.0 API stability, broad adoption, or production SLOs.")))

(defn roadmap-section []
  (dds/section
   {:id "roadmap" :title "Roadmap: widen only after the boundary holds"}
   (dds/grid
    {:min "18rem"}
    (card (dds/chip-label "NOW")
          (dds/heading 3 "One versioned contract" {:size "24"})
          [:p "Keep grammar, effects, checked KIR, target adapters, qualification, and first-run documentation aligned."])
    (card (dds/chip-label "NEXT")
          (dds/heading 3 "Close provider gaps" {:size "24"})
          [:p "Expand typed request/result conformance, adversarial testing, receipts, revocation, and reproducible release operations."])
    (card (dds/chip-label "LATER")
          (dds/heading 3 "Earn wider deployment" {:size "24"})
          [:p "Widen production use after provider, host-isolation, rollback, and soak evidence—and grow inspectable declarative libraries."]))
   [:p (external-link "https://github.com/kotoba-lang/kotoba-lang#roadmap" "Read the maintained roadmap and non-goals")]
   (caption "Roadmap items are direction, not promises of shipped capability or delivery dates.")))

(defn community-section []
  (dds/section
   {:id "community" :title "Build the community in public"}
   [:p {:class "kot-lead"}
    "Kotoba does not yet claim a large community. Today the honest public meeting points are the source repositories, issue trackers, release history, and security channel."]
   (dds/grid
    {:min "18rem"}
    (card (dds/chip-label "DISCUSS & REPORT")
          (dds/heading 3 "Language issues" {:size "24"})
          [:p "Ask a design question, propose a documentation improvement, or report a reproducible language-contract problem."]
          (external-link "https://github.com/kotoba-lang/kotoba-lang/issues" "Open language issues"))
    (card (dds/chip-label "IMPLEMENT")
          (dds/heading 3 "Compiler and CLI issues" {:size "24"})
          [:p "Follow implementation work, releases, target support, and runtime integration in the installable implementation."]
          (external-link "https://github.com/kotoba-lang/kotoba/issues" "Open implementation issues"))
    (card (dds/chip-label "SECURITY")
          (dds/heading 3 "Report privately" {:size "24"})
          [:p "Use the published security policy for vulnerabilities; do not disclose exploitable details in a public issue."]
          (external-link "https://github.com/kotoba-lang/kotoba-lang/security/policy" "Read security policy")))
   [:p (external-link "https://github.com/orgs/kotoba-lang/repositories" "Explore all public Kotoba repositories")]))

(defn blog-cloud-section []
  (dds/section
   {:id "cloud" :title "From language boundary to separately governed services"}
   (dds/grid
    {:min "18rem"}
    (card (dds/chip-label "BLOG")
          (dds/heading 3 "Evidence before slogans" {:size "24"})
          [:p "Read short engineering notes that connect product claims to measurements, authority files, and remaining gates."]
          [:a {:class "kot-link" :href "./blog/"} "Read the Kotoba blog"])
    (card (dds/chip-label "KOTOBA CLOUD")
          (dds/heading 3 "Identity and deploy control" {:size "24"})
          [:p "The operational entrance for Passkey identity and CLI topology discovery. It carries admitted boundaries forward without replacing compiler or host enforcement."]
          (external-link "https://kotoba.cloud/" "Open Kotoba Cloud"))
    (card (dds/chip-label "KOTOBASE")
          (dds/heading 3 "Storage and receipt plane" {:size "24"})
          [:p "Content-addressed artifacts, durable state, execution receipts, identity-bound access, and federation surfaces."]
          (external-link "https://kotobase.net/" "Open Kotobase"))
    (card (dds/chip-label "MURAKUMO")
          (dds/heading 3 "Compute and inference plane" {:size "24"})
          [:p "Fleet compute and model-serving infrastructure. Availability and route qualification remain service-specific."]
          (external-link "https://murakumo.cloud/" "Open Murakumo"))
    (card (dds/chip-label "ITONAMI")
          (dds/heading 3 "Agent work plane" {:size "24"})
          [:p "Continuing agent work across workspaces, goals, evidence, tools, approvals, and governed effects."]
          (external-link "https://itonami.cloud/" "Open Itonami")))
   (caption "These services retain separate authority, availability, and qualification boundaries. Their connection is not proof that every Kotoba capability is available as a generally sold hosted service.")))

(defn benchmark-section []
  (let [kotoba (get-in benchmark [:results :kotoba])
        rust (get-in benchmark [:results :rust])
        ratio (get-in benchmark [:results :medianRatioKotobaToRust])
        runs (get-in benchmark [:method :runs])
        chip (get-in benchmark [:environment :chip])
        measured-date (subs (:generatedAt benchmark) 0 10)
        kotoba-version (str/upper-case (get-in benchmark [:environment :kotoba]))
        rust-version (str/join " " (take 2 (str/split (get-in benchmark [:environment :rustc]) #" ")))
        comparators (:comparators runtime-benchmark)
        domains (:domains runtime-benchmark)
        coverage (:semanticCoverage runtime-benchmark)
        speed (:speedQualification runtime-benchmark)
        runtime-date (subs (:generatedAt runtime-benchmark) 0 10)
        comparator-labels (str/join ", " (map :label comparators))]
    (dds/section
     {:id "benchmark" :title "Two benchmarks. Two different questions."}
     [:p {:class "kot-lead"}
      "Compile time asks how quickly a tiny program becomes executable. Runtime asks how fast already-built native code runs. The results below keep those questions—and their evidence status—separate."]
     (dds/grid
      {:min "16rem"}
      (card (dds/chip-label "COMPILE · MEASURED")
            (dds/heading 3 (str (:medianMilliseconds kotoba) " ms vs "
                                (:medianMilliseconds rust) " ms") {:size "24"})
            [:p (str kotoba-version " used " ratio "× the elapsed time of "
                     (str/upper-case rust-version) " in this exact run.")]
            (caption (str runs " alternating process-cold samples · " measured-date
                          " · " chip)))
      (card (dds/chip-label "RUNTIME · COVERAGE COMPLETE")
            (dds/heading 3 (str (count domains) " workloads × "
                                (count comparators) " comparators") {:size "24"})
            [:p (str "Amu native is exercised against " comparator-labels
                     " through one common native call boundary.")]
            (caption (str (:completeComparatorDomainPairs coverage) "/"
                          (:requiredComparatorDomainPairs coverage)
                          " comparator/workload pairs · exact answers verified")))
      (card (dds/chip-label "RUNTIME SPEED · PENDING" {:color "gray"})
            (dds/heading 3 "Not qualified" {:size "24"})
            [:p "The comparison ran, but the host never became quiet enough to support a speed ranking."]
            (caption (str "load1 " (.toFixed (:observedLoad1First speed) 2) " → "
                          (.toFixed (:observedLoad1Last speed) 3) " · required ≤ "
                          (.toFixed (:quietLoad1Limit speed) 1) " · " runtime-date))))
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "What each public benchmark does—and does not—establish"
        :headers ["Question" "Compared implementations" "Current conclusion"]
        :row-header? true
        :rows [["Tiny Wasm compile + execute"
                (str kotoba-version " vs " (str/upper-case rust-version))
                (str (:medianMilliseconds kotoba) " ms vs "
                     (:medianMilliseconds rust) " ms median; " ratio
                     "× Rust elapsed on this recorded M4 run only")]
               ["Native steady-state execution"
                (str "Amu native vs " comparator-labels)
                "All 30 semantic comparison cells are complete; speed ranking withheld because the quiet-host gate failed"]]})]
     (dds/heading 3 "What the native suite covers" {:size "24"})
     [:p
      "Each implementation returns an independently checked known answer. The suite rotates every engine pair in ABBA/BAAB order and measures after loading, mapping, and symbol lookup."]
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "Six required native runtime workloads"
        :headers ["Workload" "What it stresses" "Evidence status"]
        :row-header? true
        :rows (for [{:keys [label stress exactResultVerified]} domains]
                [label stress (if exactResultVerified
                                "Exact result verified; timing unqualified"
                                "Incomplete")])})]
     [:p
      [:strong "Bottom line: "]
      "The compile result is a real, narrow measurement. The runtime comparison universe is complete and reproducible, but this run does not establish that Amu is fastest. A quiet-host rerun must pass every comparator in every workload before that sentence becomes valid."]
     [:div {:class "kot-actions"}
      (dds/button "Inspect compile samples"
                  {:href "./benchmarks/compile-wasm-latest.json"})
      (dds/button "Inspect runtime evidence"
                  {:href "./benchmarks/runtime-native-latest.json"
                   :type :outline})
      (dds/button "Review the runtime method"
                  {:href (get-in runtime-benchmark [:sources :method])
                   :type :outline})
      (dds/button "Re-run compile harness"
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

(def play-js
  (str "document.addEventListener('DOMContentLoaded',function(){"
       "var button=document.getElementById('kot-play-run');"
       "var status=document.getElementById('kot-play-status');"
       "var expected='" play-sha256 "';"
       "function hex(bytes){return Array.from(bytes,function(b){return b.toString(16).padStart(2,'0');}).join('');}"
       "button.addEventListener('click',async function(){button.disabled=true;status.textContent='Verifying artifact…';"
       "try{var response=await fetch('./play/double-21.wasm',{cache:'no-store'});"
       "if(!response.ok)throw new Error('artifact fetch failed: HTTP '+response.status);"
       "var bytes=new Uint8Array(await response.arrayBuffer());"
       "var digest=hex(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)));"
       "if(digest!==expected)throw new Error('artifact digest mismatch');"
       "var module=await WebAssembly.compile(bytes);"
       "if(WebAssembly.Module.imports(module).length!==0)throw new Error('demo artifact requested a host import');"
       "var instance=await WebAssembly.instantiate(module,{});"
       "var result=instance.exports.main();"
       "if(result!==42n)throw new Error('unexpected result');"
       "status.textContent='✓ Kotoba returned '+result.toString()+' · SHA-256 verified · 0 imports';"
       "}catch(error){status.textContent='Could not run: '+error.message;}finally{button.disabled=false;}});});"))

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
     (str/join ", " authority-files) ". No telemetry. No third-party runtime dependency."]
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
     (developer-section)
     (code-play-section)
     (libraries-section)
     (roadmap-section)
     (community-section)
     (blog-cloud-section)
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
   [:script search-js]
   [:script play-js]])

(defn blog-view []
  [:div
   [:a {:class "kot-skip" :href "#main"} "Skip to content"]
   (header "../")
   [:main {:id "main"}
    (dds/container
     [:section {:id "top" :class "kot-hero"}
      [:p {:class "kot-eyebrow"} "Kotoba engineering notes"]
      (dds/heading 1 "Evidence before slogans" {:size "48"})
      [:p {:class "kot-lead"}
       "Short notes about language design, measurements, shipped boundaries, and what still remains unqualified."]]
     [:article {:class "kot-blog-entry"}
      [:p {:class "kot-eyebrow"} "28 August 2026 · Benchmarks"]
      (dds/heading 2 "Two benchmarks answer two different questions" {:size "32"})
      [:p "Compile time measures a tiny source-to-executable path. Native runtime measures already-built programs. Kotoba publishes them separately so a fast compile cannot be mistaken for a fast runtime—or the reverse."]
      [:p "The native comparison currently covers every required implementation/workload pair and verifies exact results, but its speed ranking remains withheld because the recorded host-load gate failed."]
      [:p [:a {:class "kot-link" :href "../#benchmark"} "Read the benchmark and inspect its evidence"]]]
     [:article {:class "kot-blog-entry"}
      [:p {:class "kot-eyebrow"} "28 August 2026 · Language design"]
      (dds/heading 2 "No ambient authority is a language boundary" {:size "32"})
      [:p "Kotoba programs do not begin with implicit filesystem, network, process, clock, model, or secret access. Source declares effects, admission intersects grants and policy, and the host binds only the resulting capabilities."]
      [:p "That design complements operating-system isolation; it does not replace the compiler, verifier, runtime, provider, key custody, or host policy in the trusted computing base."]
      [:p [:a {:class "kot-link" :href "../#architecture"} "See the computation boundary"]]])]
   (footer)])

(defn libraries-view []
  (let [surfaces (:kotoba.library-publication/surfaces library-publication)
        status (:kotoba.library-publication/status library-publication)]
    [:div
     [:a {:class "kot-skip" :href "#main"} "Skip to content"]
     (header "../")
     [:main {:id "main"}
      (dds/container
       [:section {:id "top" :class "kot-hero"}
        [:p {:class "kot-eyebrow"} "CONTENT-ADDRESSED LIBRARIES"]
        (dds/heading 1 "Names help you find code. Hashes say what it is." {:size "48"})
        [:p {:class "kot-lead"}
         "Kotoba CLI inspects and publishes the same CID graph it compiles. GitHub is provenance, a namespace is discovery, and immutable CIDs identify definitions, releases, builds, and artifacts."]
        [:div {:class "kot-actions"}
         (dds/button "Inspect with Kotoba CLI" {:href "#publish" :size "lg"})
         (dds/button "Machine-readable contract"
                     {:href "../.well-known/kotoba-libraries.json"
                      :type :outline :size "lg"})]]

       (dds/section
        {:id "identity" :title "One library, several identities"}
        (dds/grid
         {:min "17rem"}
         (card (dds/chip-label "DEFINITION CID")
               (dds/heading 3 "Meaning or checked KIR" {:size "20"})
               [:p "A name, full CID, and unambiguous #hash abbreviation resolve to the same definition."])
         (card (dds/chip-label "RELEASE CID")
               (dds/heading 3 "Signed namespace head" {:size "20"})
               [:p "The release graph selects exact definition CIDs and links its predecessor, making rollback detectable."])
         (card (dds/chip-label "SOURCE · BUILD · ARTIFACT")
               (dds/heading 3 "Keep provenance layers separate" {:size "20"})
               [:p "Source bytes, declared build inputs, and emitted bytes have different identities. None grants execution authority."])
         (card (dds/chip-label "GITHUB")
               (dds/heading 3 "Provenance, not identity" {:size "20"})
               [:p "Repository and commit links help review origin. They cannot replace CIDs or authorize a namespace update."])))

       (dds/section
        {:id "publish" :title "Inspect, sign, publish, discover"}
        [:pre {:class "kot-pre"}
         [:code "kotoba library inspect quadruple \\\n  --store .kotoba/codebase --namespace demo \\\n  --github https://github.com/kotoba-lang/demo\n\n# dry-run is the default\nkotoba library publish \\\n  --store .kotoba/codebase --namespace demo\n\n# explicit network effect: signed head + IPNS\nkotoba library publish \\\n  --store .kotoba/codebase --namespace demo --dry-run false"]]
        (dds/grid
         {:min "16rem"}
         (card (dds/chip-label "1 · INSPECT")
               (dds/heading 3 "Resolve the exact graph" {:size "20"})
               [:p "Return the release CID, definition CIDs, dependency CIDs, identity layer, and optional GitHub provenance."])
         (card (dds/chip-label "2 · AUTHORIZE")
               (dds/heading 3 "Sign the namespace head" {:size "20"})
               [:p "The existing local operator identity signs publication. A valid CID alone never proves who may publish it."])
         (card (dds/chip-label "3 · STORE + NAME")
               (dds/heading 3 "Reuse codebase and IPNS" {:size "20"})
               [:p "Missing blocks go to a verified endpoint when configured; IPNS names the signed head. No second package registry is invented."])
         (card (dds/chip-label "4 · DISCOVER")
               (dds/heading 3 "Project into the public catalog" {:size "20"})
               [:p "kotoba-lang.org explains the graph. kotoba.cloud owns publication control and deploy readiness without becoming storage."])))

       (dds/section
        {:id "status" :title "Current boundary"}
        (dds/grid
         {:min "18rem"}
         (card (dds/chip-label "LIVE")
               (dds/heading 3 "Local-signed IPNS publication" {:size "20"})
               [:p "The CLI inspect and dry-run path is implemented over the existing content-addressed codebase. Explicit apply reuses signed-head and IPNS publication."])
         (card (dds/chip-label "NOT LIVE")
               (dds/heading 3 "Passkey-hosted publish" {:size "20"})
               [:p "kotoba.cloud does not yet accept a hosted library apply. Passkey authorization, namespace governance, abuse controls, and catalog ingestion remain separate qualification work."])
         (card (dds/chip-label "STATUS")
               (dds/heading 3 (str/replace (name status) #"-" " ") {:size "20"})
               [:p "The machine contract exposes this evidence state so clients do not infer hosted capability from a public webpage."])
         (card (dds/chip-label "SOURCE")
               (dds/heading 3 "Review the implementation" {:size "20"})
               [:p (external-link "https://github.com/kotoba-lang/kotoba" "Kotoba CLI")]
               [:p (external-link "https://github.com/kotoba-lang/kotoba-lang" "Language and catalog authority")]
               [:p (external-link "https://github.com/kotoba-lang/codebase" "Content-addressed codebase")]))
        (caption "Hosted Passkey publication: "
                 (if (get-in surfaces [:hosted-passkey-publish :implemented])
                   "implemented" "not implemented")
                 ". Content identity is never execution authority."))

       (dds/section
        {:id "compare" :title "Compare libraries with the boundary attached"}
        [:p {:class "kot-lead"}
         "A comparison is evidence only when it names the exact library CID, workload, target, host, toolchain, sample count, measurement time, verified result, receipt, and residual limit."]
        (bullets ["Do not compare mutable latest aliases as if they were immutable releases."
                  "Separate API coverage, target compatibility, compile performance, runtime performance, and operational qualification."
                  "A faster isolated kernel is not a general production-performance claim."
                  "Unsupported or unmeasured cells stay explicit; they are not silently scored as zero."])))]
     (footer)]))

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

(def blog-html
  (page/->page
   {:title "Kotoba Blog — engineering notes and evidence"
    :description "Kotoba engineering notes about language design, benchmarks, evidence, and remaining qualification gates."
    :lang "en"
    :css dds-css
    :app-css (str tokens/skin-css "\n" app-css)}
   (blog-view)))

(def libraries-html
  (page/->page
   {:title "Kotoba Libraries — content-addressed publication and comparison"
    :description "Inspect, publish, discover, and compare Kotoba libraries by immutable definition and release CIDs, with GitHub provenance kept separate."
    :lang "en"
    :css dds-css
    :app-css (str tokens/skin-css "\n" app-css)}
   (libraries-view)))

(let [out (path/join "site" "dist")]
  (fs/mkdirSync out #js {:recursive true})
  (fs/writeFileSync (path/join out "index.html") html)
  (fs/mkdirSync (path/join out "blog") #js {:recursive true})
  (fs/writeFileSync (path/join out "blog" "index.html") blog-html)
  (fs/mkdirSync (path/join out "libraries") #js {:recursive true})
  (fs/writeFileSync (path/join out "libraries" "index.html") libraries-html)
  (fs/copyFileSync logo-source-path (path/join out "kotoba-wordmark.png"))
  (fs/copyFileSync dependency-manifest-path (path/join out "dependencies.edn"))
  (doseq [[source target]
          [[benchmark-source-path (path/join out "benchmarks" "compile-wasm-latest.json")]
           [runtime-benchmark-source-path (path/join out "benchmarks" "runtime-native-latest.json")]
           [(path/join "site" "assets" "llms.txt") (path/join out "llms.txt")]
           [(path/join "site" "assets" "llms-full.txt") (path/join out "llms-full.txt")]
           [(path/join "site" "assets" "agent-quickstart.md") (path/join out "agent-quickstart.md")]]]
    (fs/mkdirSync (path/dirname target) #js {:recursive true})
    (fs/copyFileSync source target))
  (doseq [name ["double-21.kotoba" "double-21.wasm"
                "double-21.wasm.provenance.edn" "double-21.wasm.publication.edn"]]
    (let [source (path/join "site" "assets" "play" name)
          target (path/join out "play" name)]
      (fs/mkdirSync (path/dirname target) #js {:recursive true})
      (fs/copyFileSync source target)))
  ;; RFC 9116. Copied rather than generated so the published contact is a file
  ;; someone can read and edit in `site/assets/`, not a string buried in here —
  ;; and so a regeneration cannot silently drop it (a security.txt that
  ;; disappears looks exactly like one that was never published).
  (let [wk (path/join out ".well-known")]
    (fs/mkdirSync wk #js {:recursive true})
    (fs/copyFileSync (path/join "site" "assets" "security.txt")
                     (path/join wk "security.txt"))
    (fs/writeFileSync
     (path/join wk "kotoba-libraries.json")
     (js/JSON.stringify (clj->js library-publication) nil 2)))
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
                            "kotobase-v1.json")]
                [(path/join identity-root "resources" "public" "policies"
                            "trust" "erc8004" "murakumo-v1.json")
                 (path/join out "policies" "trust" "erc8004"
                            "murakumo-v1.json")]
                [(path/join identity-root "resources" "public" "policies"
                            "trust" "erc8004" "murakumo-v1.json.signature.json")
                 (path/join out "policies" "trust" "erc8004"
                            "murakumo-v1.json.signature.json")]]]
    (doseq [[source target] copies]
      (when-not (fs/existsSync source)
        (throw (js/Error. (str "required identity trust contract missing: " source))))
      (fs/mkdirSync (path/dirname target) #js {:recursive true})
      (fs/copyFileSync source target)))
  (println "wrote" (path/join out "index.html")
           (str "(" (.-length html) " bytes)")))
