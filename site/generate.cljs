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
  [{:label "Docs" :href "https://github.com/kotoba-lang/kotoba-lang/tree/main/docs"}
   {:label "Protocol" :href "https://github.com/kotoba-lang/kotoba-protocol"}
   {:label "Spec" :href "https://github.com/kotoba-lang/spec"}])

(def market-signals
  [{:metric "46% vs 33%"
    :title "AI use grew faster than trust"
    :body "More developers distrust AI output accuracy than trust it. The winning language cannot ask users to believe generated code; it must make authority inspectable and enforceable."
    :href "https://survey.stackoverflow.co/2025/ai"
    :source "Stack Overflow Developer Survey 2025"}
   {:metric "81%"
    :title "Agent security is already a mainstream concern"
    :body "Developers report concern about the security and privacy of AI agents. Permission prompts are a workflow; least authority has to survive compilation and execution."
    :href "https://survey.stackoverflow.co/2025/ai"
    :source "Stack Overflow Developer Survey 2025"}
   {:metric "10,000+"
    :title "Mythos-class systems changed the threat ceiling"
    :body "Anthropic reports that Project Glasswing partners found more than ten thousand high- or critical-severity flaws. Kotoba does not claim an unhackable runtime; it limits what admitted code can reach after compromise."
    :href "https://www.anthropic.com/news/expanding-project-glasswing"
    :source "Anthropic, Project Glasswing"}])

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
    [:p {:class "kot-eyebrow"} "The language for AI agents and vibe coding"]
    (dds/heading 1 "Let AI write the code. Never hand it the keys." {:size "48"})
    [:p {:class "kot-lead"}
     "Kotoba is an AI-native, capability-safe language. Generated programs can touch only explicitly granted resources—even when a Mythos-class agent is looking for a way out."]
    [:div {:class "kot-actions"}
     (dds/button "Start in 60 seconds" {:href "#start" :size "lg"})
     (dds/button "View the language authority" {:href "https://github.com/kotoba-lang/kotoba-lang"
                                                  :type :outline :size "lg"})]
    [:div {:class "kot-proof"}
     (dds/grid {:min "14rem"}
      (card (dds/chip-label "DENY BY DEFAULT")
            (dds/heading 3 "No ambient authority" {:size "20"})
            [:p "No implicit filesystem, network, process, clock, or secrets."])
      (card (dds/chip-label "CHECK BEFORE RUN")
            (dds/heading 3 "Effects are admitted" {:size "20"})
            [:p "Transitive effects become exact component imports before emission."])
      (card (dds/chip-label "PORTABLE + NATIVE")
            (dds/heading 3 "One checked IR" {:size "20"})
            [:p "WebAssembly Components first; sealed, bounded native AOT when selected."]))])])

(def stack-links
  [{:name "Documentation"
    :role "Learn the language, use the toolchain, implement a backend, or inspect maturity evidence."
    :href "https://github.com/kotoba-lang/kotoba-lang/tree/main/docs"
    :cta "Open docs"}
   {:name "Protocol"
    :role "Normative layer, vocabulary, datom, IPLD, IPNS, IPFS, and application-model contracts."
    :href "https://github.com/kotoba-lang/kotoba-protocol"
    :cta "Open protocol"}
   {:name "Spec"
    :role "The data layer of the foundational standard library: small, reusable data specifications."
    :href "https://github.com/kotoba-lang/spec"
    :cta "Open spec"}
   {:name "Kotobase"
    :role "Persistent Datalog and content-addressed database for durable application state."
    :href "https://github.com/kotoba-lang/kotobase"
    :cta "Open Kotobase"}
   {:name "Murakumo"
    :role "Hosting, placement, deployment, and fleet control plane for the Kotoba component mesh."
    :href "https://github.com/kotoba-lang/murakumo"
    :cta "Open Murakumo"}])

(defn stack-section []
  (dds/section
   {:id "explore" :title "From language contract to running system"}
   [:p {:class "kot-lead"}
    "The language authority, implementation, data plane, and fleet are separate on purpose. Open the layer you need without guessing which repository owns it."]
   (dds/grid
    {:min "17rem"}
    (card (dds/chip-label "LANGUAGE AUTHORITY")
          (dds/heading 3 "kotoba-lang/kotoba-lang" {:size "20"})
          [:p "Normative language specification, grammar, machine-readable contracts, public CLI contract, and conformance."]
          (external-link "https://github.com/kotoba-lang/kotoba-lang" "Open language authority"))
    (card (dds/chip-label "IMPLEMENTATION")
          (dds/heading 3 "kotoba-lang/kotoba" {:size "20"})
          [:p "Installable CLI, runtime and host implementations, providers, integration tests, and qualification evidence that consume the language authority."]
          (external-link "https://github.com/kotoba-lang/kotoba" "Open implementation")))
   (dds/grid
    {:min "16rem"}
    (for [{:keys [name role href cta]} stack-links]
      (card (dds/heading 3 name {:size "20"})
            [:p role]
            (external-link href cta))))))

(defn why-section []
  (apply dds/section
         {:id "why" :title "The market moved from autocomplete to agents"}
         [:p {:class "kot-lead"}
          "Vibe coding is not a temporary syntax trend. It moves code production to models while leaving humans accountable for effects. Kotoba is positioned at that missing boundary: let the model produce more, while the language grants less."]
         (dds/grid {:min "17rem"}
          (for [{:keys [metric title body href source]} market-signals]
            (card [:p {:class "kot-metric"} metric]
                  (dds/heading 3 title {:size "24"})
                  [:p body]
                  (external-link href source))))
         [[:p {:class "kot-caption kot-muted"}
           "External market evidence explains timing; it is not language qualification evidence."]]))

(defn how-section []
  (dds/section
   {:id "how" :title "Vibe coding, with a hard boundary"}
   [:p {:class "kot-lead"}
    "Do not decide whether generated code is trustworthy. Decide exactly what it may do, then make everything else structurally unavailable."]
   (dds/grid
    {:min "18rem"}
    (card (dds/chip-label "1 · WRITE")
          (dds/heading 3 "Prompt the program" {:size "24"})
          [:p "AI writes a small, Clojure-shaped source profile. Macros, eval, ambient interop, and unbounded concurrency are absent from the admitted grammar."])
    (card (dds/chip-label "2 · ADMIT")
          (dds/heading 3 "Compile the authority" {:size "24"})
          [:p "Type checking and transitive effect inference produce exact imports. Requested, delegated, and local policy intersect; authority can only narrow."])
    (card (dds/chip-label "3 · RUN")
          (dds/heading 3 "Bind only the grant" {:size "24"})
          [:p "The tender binds only admitted capabilities. Ungranted effects are absent or unbound, and attempts are receipted whether they succeed or fail."]))
   [:blockquote {:class "kot-quote"}
    [:strong "Mythos can search for a weakness. It still cannot mint a capability."]
    [:p "This is a confinement claim, not an 'unhackable' claim: the Wasm engine, providers, policy, key custody, and native OS isolation remain in the trusted computing base."]]))

(defn start-section []
  (dds/section
   {:id "start" :title "Start in sixty seconds"}
   (dds/grid
    {:min "20rem"}
    (card (dds/heading 3 "Run Kotoba" {:size "24"})
          [:pre {:class "kot-pre"}
           [:code "brew tap kotoba-lang/kotoba\nbrew install kotoba\nkotoba -e '(+ 1 2)'\nkotoba compile examples/hello.kotoba --target wasm --output hello.wasm --json"]]
          (caption "The -e command is compile-and-run sugar, not runtime eval."))
    (card (dds/heading 3 "Start with no authority" {:size "24"})
          [:p "An empty policy denies every host effect. Add only the resource-scoped capability the program needs."]
          [:pre {:class "kot-pre"}
           [:code "{:policy/allow #{}\n :policy/forbid-wildcard true}"]]
          (caption "HTTP, storage, and LLM hosted kits are not yet qualified for sale on a shipped backend.")))
   [:div {:class "kot-actions"}
    (dds/button "Open the getting-started guide"
                {:href "https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/getting-started.md"})
    (dds/button "Read CLI reference"
                {:href "https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/generated/cli.md"
                 :type :outline})]))

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

(def github-projects
  [{:name "kotoba-lang/kotoba-lang" :role "Language authority, semantics, grammar, claims, and conformance"
    :href "https://github.com/kotoba-lang/kotoba-lang"}
   {:name "kotoba-lang/amu" :role "Compiler frontend, effect inference, checked KIR, Wasm and native emitters"
    :href "https://github.com/kotoba-lang/amu"}
   {:name "kotoba-lang/kotoba" :role "Language and library substrate, hosts, identities, and integration tests"
    :href "https://github.com/kotoba-lang/kotoba"}
   {:name "kotoba-lang/kototama" :role "Tender that admits components and binds granted capabilities"
    :href "https://github.com/kotoba-lang/kototama"}
   {:name "kotoba-lang/kotoba-core-contracts" :role "Package admission and runtime-boundary contracts"
    :href "https://github.com/kotoba-lang/kotoba-core-contracts"}
   {:name "kotoba-lang/kotobase" :role "Persistent Datalog and content-addressed application state"
    :href "https://github.com/kotoba-lang/kotobase"}
   {:name "kotoba-lang/murakumo" :role "Hosting, placement, deployment, and fleet control plane"
    :href "https://github.com/kotoba-lang/murakumo"}
   {:name "kotoba-lang" :role "All repositories in the language ecosystem"
    :href "https://github.com/kotoba-lang"}])

(defn source-section []
  (dds/section
   {:id "source" :title "Open source, from language to runtime"}
   (dds/grid
    {:min "18rem"}
    (for [{:keys [name role href]} github-projects]
      (card (dds/heading 3 name {:size "20"})
            [:p role]
            (external-link href "Open on GitHub"))))
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
    [:p [:strong "Kotoba"] " — let AI write more; grant the program less."]
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
     (stack-section)
     (why-section)
     (how-section)
     (start-section)
     (claims-section)
     (deliberate-section)
     (release-section)
     (search-section)
     (source-section))]
   (footer)
   [:script search-js]])

(def html
  (page/->page
   {:title "Kotoba — the capability-safe language for AI agents and vibe coding"
    :description (str "Let AI write the code without handing it the keys. Kotoba is an "
                      "AI-native language with deny-by-default capabilities, checked effects, "
                      "bounded admission, WebAssembly Components, and bounded native AOT.")
    :lang "en"
    :css dds-css
    :app-css (str tokens/skin-css "\n" app-css)}
   (view)))

(let [out (path/join "site" "dist")]
  (fs/mkdirSync out #js {:recursive true})
  (fs/writeFileSync (path/join out "index.html") html)
  (fs/copyFileSync logo-source-path (path/join out "kotoba-wordmark.png"))
  (println "wrote" (path/join out "index.html")
           (str "(" (.-length html) " bytes)")))
