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
         '[kotoba.site.hero :as hero]
         '[kotoba.site.chart :as chart]
         '[kotoba.site.catalog :as catalog]
         '[html.core :as html]
         '["crypto" :as crypto]
         '["fs" :as fs]
         '["path" :as path])

(def authority-files
  ["lang/safety-claims.edn"
   "lang/surface-status.edn"
   "lang/elaboration-pipeline.edn"
   "lang/typed-eval.edn"
   "lang/wasm-component-platform.edn"
   "lang/library-publication.edn"
   "lang/docs-release.edn"
   "lang/product-defaults.edn"
   "site/sponsorship.edn"
   "security/cryptographic-boundaries.edn"
   "docs/search-index.edn"])

(def authority
  (into {} (for [f authority-files]
             [f (reader/read-string (fs/readFileSync f "utf8"))])))

(def safety-claims  (authority "lang/safety-claims.edn"))
(def surface-status (authority "lang/surface-status.edn"))
(def platform       (authority "lang/wasm-component-platform.edn"))
(def elaboration    (authority "lang/elaboration-pipeline.edn"))
(def typed-eval     (authority "lang/typed-eval.edn"))
(def library-publication (authority "lang/library-publication.edn"))
(def docs-release   (authority "lang/docs-release.edn"))
(def product-defaults (authority "lang/product-defaults.edn"))
(def sponsorship    (authority "site/sponsorship.edn"))
(def search-index   (authority "docs/search-index.edn"))

(def sponsorship-live? (= :live (:status sponsorship)))
(def sponsor-profile-url (get-in sponsorship [:platform :profile-url]))

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
(def package-registry-path (path/join "lang" "package-registry.edn"))
(def cryptographic-boundaries-path
  (path/join "security" "cryptographic-boundaries.edn"))
(def package-ipfs-path (path/join "site" "assets" "ipfs"))

(def logo-source-path
  (path/join "site" "assets" "kotoba-wordmark.png"))

(def og-card-source-path
  (path/join "site" "assets" "kotoba-og-card.png"))

(def favicon-source-path
  (path/join "site" "assets" "kotoba-favicon.png"))

(def favicon-ico-source-path
  (path/join "site" "assets" "kotoba-favicon.ico"))

(def site-origin "https://kotoba-lang.org")

(def benchmark-source-path
  (path/join "bench" "public-compile-comparison" "latest.json"))

(def runtime-benchmark-source-path
  (path/join "bench" "public-runtime-comparison" "latest.json"))

(def end-to-end-benchmark-source-path
  (path/join "bench" "public-end-to-end-comparison" "latest.json"))

(def domain-benchmark-source-path
  (path/join "bench" "public-domain-comparison" "latest.json"))

(def build-scaling-source-path
  (path/join "bench" "public-build-scaling" "latest.json"))

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

(def end-to-end-benchmark
  (js->clj (js/JSON.parse (fs/readFileSync end-to-end-benchmark-source-path "utf8"))
           :keywordize-keys true))

(def domain-benchmark
  (js->clj (js/JSON.parse (fs/readFileSync domain-benchmark-source-path "utf8"))
           :keywordize-keys true))

(def build-scaling
  (js->clj (js/JSON.parse (fs/readFileSync build-scaling-source-path "utf8"))
           :keywordize-keys true))

(def cold-start
  "The one speed ordering on this page that its own gate qualifies.

  Four of the five public reports record a FAILED quiet-host gate, so none of
  them may rank anything, and this page says so next to each of them. The
  build-scaling report is the exception and the reason is structural rather
  than lucky: its lanes are interleaved on one host and every ordering is put
  through perfgate at its own unrelaxed default policy, which refuses any gap
  that falls inside the two arms' combined spread. A gap that survives that
  test survives the host being busy.

  At the smallest source size the released Kotoba CLI passes it against every
  comparator the host could build. That — bounded to this host, this size and
  this run — is the only thing the page is allowed to call fastest, and
  `:qualified?` is read from the report rather than asserted here: if a rerun
  loses one of those orderings, the hero panel demotes itself."
  (let [scale (first (:scales build-scaling))
        lanes (:lanes build-scaling)
        lead :kotoba-wasm-cli
        ordering (get-in scale [:ordering lead])
        base (get-in scale [:lanes lead :summary :median])
        row (fn [id]
              (let [r (get-in scale [:lanes id])
                    m (get-in r [:summary :median])]
                (cond-> {:id id
                         :label (get-in lanes [id :label])
                         :target (get-in lanes [id :target])
                         :status (:status r)
                         :lead? (= id lead)}
                  (= "measured" (:status r))
                  (assoc :median m :ratio (/ m base)))))]
    {:lead lead
     :k (:k scale)
     :median base
     :samples (get-in scale [:lanes lead :n])
     :rows (map row [lead :clang-native :rustc-wasm :rustc-native :javac])
     :beaten (count ordering)
     :qualified? (boolean (and (seq ordering)
                               (every? :qualifiedFaster (vals ordering))))
     :date (subs (:generatedAt build-scaling) 0 10)
     :host (get-in build-scaling [:environment :cpu])}))

(def library-catalog
  (reader/read-string (fs/readFileSync (path/join "site" "library-catalog.edn") "utf8")))

(def library-taxonomy
  (reader/read-string (fs/readFileSync (path/join "site" "library-taxonomy.edn") "utf8")))

(def tagged-catalog
  "Every public repository in the org, tagged.

  `:untagged` is published on the page rather than swallowed. 1,160 of these
  repositories carry no GitHub description, so the only evidence a domain tag
  could come from is the name — and for some of them the name is a metaphor
  (`kuro`, `kobo`, `byoubu`) that says nothing about the function on purpose.
  Assigning those a nearest-guess tag would make the catalogue look complete
  while making it wrong, and a reader could not tell the two apart."
  (catalog/tag-all library-taxonomy library-catalog))

(def play-source (fs/readFileSync play-source-path "utf8"))
(def play-provenance (reader/read-string (fs/readFileSync play-provenance-path "utf8")))
(def play-sha256 (get-in play-provenance [:outputs :primary :sha256]))
(def dependencies (reader/read-string (fs/readFileSync dependency-manifest-path "utf8")))
(def package-registry-bytes (fs/readFileSync package-registry-path))
(def public-package-registry
  (reader/read-string (.toString package-registry-bytes "utf8")))
(def reference-package (first (:records public-package-registry)))

(defn- base32-lower [bytes]
  (loop [xs (seq bytes) acc 0 bits 0 out ""]
    (cond
      (>= bits 5)
      (let [remaining (- bits 5)
            digit (bit-and 31 (unsigned-bit-shift-right acc remaining))
            mask (if (zero? remaining) 0 (dec (bit-shift-left 1 remaining)))]
        (recur xs (bit-and acc mask) remaining
               (str out (.charAt "abcdefghijklmnopqrstuvwxyz234567" digit))))

      xs
      (recur (next xs) (bit-or (bit-shift-left acc 8) (first xs)) (+ bits 8) out)

      (pos? bits)
      (let [digit (bit-and 31 (bit-shift-left acc (- 5 bits)))]
        (str out (.charAt "abcdefghijklmnopqrstuvwxyz234567" digit)))

      :else out)))

(def package-registry-cid
  (let [digest (.digest (.update (crypto/createHash "sha256") package-registry-bytes))]
    (str "b" (base32-lower (concat [1 85 18 32] digest)))))
(def syntax-dependency
  (first (filter #(= :syntax-highlighting (:id %)) (:build-time dependencies))))

(when-not (fs/existsSync dds-css-path)
  (println "site/generate.cljs: jp-go-dds CSS not found:" dds-css-path)
  (println "  set JP_GO_DDS_ROOT to the jp-go-digital-design-system checkout")
  (js/process.exit 1))

(when-not (fs/existsSync logo-source-path)
  (println "site/generate.cljs: Kotoba wordmark not found:" logo-source-path)
  (js/process.exit 1))

(when-not (and (fs/existsSync og-card-source-path)
               (fs/existsSync favicon-source-path)
               (fs/existsSync favicon-ico-source-path))
  (println "site/generate.cljs: OG card / favicon assets not found under site/assets/")
  (println "  regenerate them from site/assets/meta-src/ with headless Chrome screenshots")
  (println "  favicon.ico comes from png_to_ico conversion of the checked-in 64x64 PNG")
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

(when-not (and (= 1 (:kotoba.registry/version public-package-registry))
               (= 1 (count (:records public-package-registry)))
               (= "kotoba-lang/reference-math" (:registry/name reference-package))
               (string? (:registry/release-cid reference-package))
               (= "ed25519+ml-dsa-65" (:registry/pqc-suite reference-package))
               (string? (:registry/pqc-attestation-cid reference-package))
               (str/starts-with? (:registry/pqc-key-id reference-package) "sha256:")
               (= 2 (count (:registry/providers reference-package))))
  (throw (js/Error.
          "public reference package registry is incomplete")))

(def public-contact-email "support@kotoba-lang.org")

(defn- thousands [n]
  (str/replace (str n) #"\B(?=(\d{3})+$)" ","))

(defn code [s] [:code {:class "kot-code"} s])
(defn caption [& children] (into [:p {:class "kot-muted kot-caption"}] children))
(defn external-link [href label]
  [:a {:class "kot-link" :href href :rel "noreferrer"} label])
(defn mail-link []
  [:a {:class "kot-link" :href (str "mailto:" public-contact-email)}
   public-contact-email])
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
   ".kot-header{position:relative;z-index:2;overflow:clip;"
   "background:var(--hig-color-system-background);"
   "border-bottom:var(--hig-hairline) solid var(--hig-color-separator)}"
   ".kot-header__inner{display:flex;align-items:flex-start;flex-direction:column;"
   "gap:var(--hig-spacing-3);padding-block:var(--hig-spacing-3)}"
   ".kot-wordmark{display:inline-flex;align-items:center;gap:.12em;text-decoration:none}"
   ".kot-paren{font-family:var(--hig-font-mono);font-size:2.1rem;line-height:1;"
   "font-weight:400;color:var(--hig-color-tint)}"
   ;; A texture, not data: hairline strokes at low opacity, and it sits behind
   ;; the header row so the nav keeps the surface it is measured against.
   ;; The motif lives in the page margins, never over the nav. Below 64rem the
   ;; container fills the viewport and there is no margin to put it in, so it
   ;; is not drawn at all rather than drawn on top of something.
   ".kot-lisp-edge{display:none}"
   ".kot-lisp-nest{display:block;height:100%;width:auto;max-width:none;fill:none;"
   "stroke:var(--hig-color-tint);stroke-width:2;stroke-linecap:round}"
   ".kot-header__inner{position:relative;z-index:1}"
   ".kot-logo{display:block;height:var(--hig-spacing-7);width:auto}"
   ".kot-nav{display:flex;align-items:center;justify-content:flex-start;flex-wrap:wrap;"
   "gap:var(--hig-spacing-2);width:100%}"
   ".kot-hero{position:relative;overflow:clip;padding-block:var(--hig-spacing-8)}"
   ".kot-hero-canvas{position:absolute;inset:0;z-index:0;pointer-events:none;"
   "color:var(--hig-color-tint)}"
   ".kot-hero-canvas>canvas,.kot-hero-canvas>svg{position:absolute;inset:0;width:100%;height:100%;display:block}"
   ".kot-hero-canvas>svg{fill:var(--hig-color-tint);opacity:.55}"
   ".kot-hero>.dds-ext-container{position:relative;z-index:1}"
   ".kot-eyebrow{margin:0 0 var(--hig-spacing-3);color:var(--hig-color-tint);"
   "font-weight:700;letter-spacing:.06em;text-transform:uppercase}"
   ".kot-hero h1{max-width:24ch;margin:0 0 var(--hig-spacing-4);text-wrap:balance}"
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
   ;; DADS's blue text chip resolves to a step whose dark mirror measures
   ;; 4.42:1 against the dark surface — below WCAG AA for its own 16px normal
   ;; weight (measured 2026-09-07). Point it at the accent this page already
   ;; uses everywhere else rather than inventing a colour: 11.10:1 light,
   ;; 8.76:1 dark, and chips now match the eyebrow instead of being a second
   ;; blue. This is an upstream jp-go-dds.dark gap, not a local preference.
   ".dads-chip-label[data-style=\"text\"][data-color=\"blue\"]{color:var(--hig-color-tint)}"
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
   ;; ── repository catalogue ─────────────────────────────────────────────────
   ".kot-cat-controls{margin-block:var(--hig-spacing-4)}"
   ".kot-cat-controls .dads-input-text{display:block}"
   ".kot-cat-controls .dads-input-text__input{width:100%;max-width:28rem}"
   ".kot-cat-tags{display:flex;flex-wrap:wrap;gap:var(--hig-spacing-2);"
   "margin-block:var(--hig-spacing-3)}"
   ".kot-cat-tags .dads-button[aria-pressed=\"true\"]{background:var(--hig-color-tint);"
   "color:var(--hig-color-system-background);border-color:var(--hig-color-tint)}"
   ".kot-cat-list{list-style:none;margin:var(--hig-spacing-4) 0 0;padding:0}"
   ".kot-lib{display:grid;gap:var(--hig-spacing-1);padding-block:var(--hig-spacing-3);"
   "border-top:1px solid var(--hig-color-separator)}"
   ".kot-lib[hidden]{display:none}"
   ".kot-lib-name{font-family:var(--hig-font-mono);font-weight:700;"
   "color:var(--hig-color-tint);text-underline-offset:.18em;justify-self:start}"
   ".kot-lib-flag{justify-self:start;font-size:var(--hig-text-caption1-font-size);"
   "color:var(--hig-color-secondary-label);text-transform:uppercase;letter-spacing:.04em}"
   ".kot-lib-desc{color:var(--hig-color-secondary-label);"
   "font-size:var(--hig-text-footnote-font-size);"
   "line-height:var(--hig-text-footnote-line-height)}"
   ".kot-lib-tags{display:flex;flex-wrap:wrap;gap:var(--hig-spacing-1)}"
   ".kot-lib-tag{padding:.1em .5em;border-radius:var(--hig-radius-capsule);"
   "background:var(--hig-color-tertiary-system-fill);color:var(--hig-color-label);"
   "font-family:var(--hig-font-mono);font-size:var(--hig-text-caption1-font-size)}"
   ".kot-footer{padding-block:var(--hig-spacing-7);"
   "border-top:var(--hig-hairline) solid var(--hig-color-separator)}"
   "@media(min-width:36rem){.kot-actions{display:flex;flex-wrap:wrap}"
   ".kot-hero h1{font-size:2.75rem;line-height:1.15}}"
   "@media(min-width:64rem){"
   ".kot-lisp-edge{display:block;position:absolute;inset-block:0;width:5.25rem;"
   "opacity:.22;pointer-events:none;z-index:0}"
   ".kot-lisp-edge[data-side=start]{inset-inline-start:0}"
   ".kot-lisp-edge[data-side=end]{inset-inline-end:0;transform:scaleX(-1)}}"
   "@media(min-width:48rem){.kot-header{position:sticky;top:0}"
   ".kot-header__inner{align-items:center;flex-direction:row;justify-content:space-between}"
   ".kot-nav{justify-content:flex-end;width:auto}.kot-hero{padding-block:var(--hig-spacing-10) var(--hig-spacing-9)}}"
   ;; ── charts ───────────────────────────────────────────────────────────────
   ;; Every mark reads a --hig-* token, so jp-go-dds.dark carries the whole
   ;; chart layer into dark mode without a second palette. Bar length is TIME,
   ;; so the fastest lane is the shortest bar and each chart says so.
   ;; The row layout has to answer to the width of the CHART, not the window:
   ;; the same component is 60rem wide in the hero and 17rem wide inside a
   ;; small-multiple card. A viewport media query gives the narrow card the
   ;; wide layout and squeezes its bars to a sliver (measured).
   ".kot-chart{margin-block:var(--hig-spacing-5) var(--hig-spacing-3);"
   "container-type:inline-size}"
   ".kot-chart-scroll{max-width:100%;overflow-x:auto}"
   ".kot-chart-axis{margin:var(--hig-spacing-2) 0 0;color:var(--hig-color-secondary-label);"
   "font-size:var(--hig-text-footnote-font-size);line-height:var(--hig-text-footnote-line-height)}"
   ".kot-bars{list-style:none;margin:0;padding:0;display:grid;gap:var(--hig-spacing-3)}"
   ".kot-bar{display:grid;grid-template-columns:minmax(0,1fr) auto;"
   "column-gap:var(--hig-spacing-3);row-gap:var(--hig-spacing-1);align-items:center}"
   ;; Rows are explicit: auto-placement puts the value BELOW a track that
   ;; spans both columns, which detaches it from the label it belongs to.
   ".kot-bar-label{grid-row:1;grid-column:1;font-weight:700;line-height:1.3}"
   ".kot-bar-value{grid-row:1;grid-column:2}"
   ".kot-bar-track{grid-row:2;grid-column:1/-1}"
   ".kot-bar-sub{display:block;font-weight:400;color:var(--hig-color-secondary-label);"
   "font-size:var(--hig-text-footnote-font-size)}"
   ".kot-bar-track{position:relative;display:block;height:var(--hig-spacing-3);"
   "border-radius:2px;background:var(--hig-color-tertiary-system-fill)}"
   ".kot-bar-track--absent{background:none;height:auto}"
   ".kot-bar-fill{position:absolute;inset-block:0;inset-inline-start:0;width:var(--w);"
   "min-width:2px;background:var(--hig-color-tertiary-label);"
   "border-start-end-radius:var(--hig-spacing-1);border-end-end-radius:var(--hig-spacing-1)}"
   ".kot-bar[data-lead] .kot-bar-fill{background:var(--hig-color-tint)}"
   ".kot-bar-absent{display:block;overflow-wrap:anywhere;color:var(--hig-color-secondary-label);"
   "font-size:var(--hig-text-footnote-font-size);line-height:var(--hig-text-footnote-line-height)}"
   ".kot-bar-value{font-family:var(--hig-font-mono);font-variant-numeric:tabular-nums;"
   "text-align:end;white-space:nowrap;line-height:1.3}"
   ".kot-bar-note{display:block;font-family:var(--hig-font-text);font-variant-numeric:normal;"
   "font-size:var(--hig-text-footnote-font-size);color:var(--hig-color-secondary-label)}"
   ".kot-bar[data-lead] .kot-bar-note{color:var(--hig-color-label);font-weight:700}"
   ;; Narrow charts are the base: label and value on one line, the track on
   ;; its own line under them. From 30rem of CHART width the row becomes
   ;; label / track / value, which is where a ranked bar chart actually reads.
   "@container (min-width:30rem){"
   ".kot-bar{grid-template-columns:minmax(8rem,13rem) minmax(0,1fr) minmax(5.5rem,auto)}"
   ".kot-bar-label,.kot-bar-track,.kot-bar-value{grid-row:auto;grid-column:auto}}"
   ".kot-dv{display:grid;grid-template-columns:minmax(3.5rem,1fr) auto;align-items:center;"
   "gap:var(--hig-spacing-2);min-width:9rem}"
   ".kot-dv-track{position:relative;display:block;height:var(--hig-spacing-3);"
   "border-radius:2px;background:var(--hig-color-tertiary-system-fill)}"
   ".kot-dv-track::before{content:\"\";position:absolute;inset-block:0;"
   "inset-inline-start:calc(50% - 1px);width:2px;background:var(--hig-color-separator)}"
   ".kot-dv-fill{position:absolute;inset-block:0;width:calc(var(--w)/2);min-width:2px}"
   ".kot-dv[data-sign=pos] .kot-dv-fill{inset-inline-start:50%;background:var(--hig-color-tint);"
   "border-start-end-radius:var(--hig-spacing-1);border-end-end-radius:var(--hig-spacing-1)}"
   ".kot-dv[data-sign=neg] .kot-dv-fill{inset-inline-end:50%;background:var(--hig-palette-orange);"
   "border-start-start-radius:var(--hig-spacing-1);border-end-start-radius:var(--hig-spacing-1)}"
   ".kot-dv:not([data-qualified]) .kot-dv-fill{opacity:.5}"
   ".kot-dv-value{font-family:var(--hig-font-mono);font-variant-numeric:tabular-nums;"
   "white-space:nowrap;font-size:var(--hig-text-footnote-font-size)}"
   ".kot-dv-tick{margin-inline-start:.3em;color:var(--hig-color-tint);font-weight:700}"
   ".kot-lines{display:block;min-width:46rem}"
   ".kot-grid{stroke:var(--hig-color-separator)}"
   ".kot-tick,.kot-axis-title,.kot-line-label{font-family:var(--hig-font-text);"
   "font-size:var(--hig-text-caption2-font-size);fill:var(--hig-color-secondary-label)}"
   ".kot-tick-y{text-anchor:end}"
   ".kot-tick-x,.kot-axis-title{text-anchor:middle}"
   ".kot-line{fill:none;stroke:var(--hig-color-tertiary-label);stroke-width:2;"
   "stroke-linecap:round;stroke-linejoin:round}"
   ".kot-line[data-accent]{stroke:var(--hig-color-tint);stroke-width:3}"
   ".kot-end{fill:var(--hig-color-tertiary-label);stroke:var(--hig-color-system-background);stroke-width:2}"
   ".kot-end[data-accent]{fill:var(--hig-color-tint)}"
   ".kot-end-broke path,.kot-end-refused path{fill:none;"
   "stroke:var(--hig-color-tertiary-label);stroke-width:2;stroke-linecap:round}"
   ".kot-end-broke[data-accent] path,.kot-end-refused[data-accent] path{stroke:var(--hig-color-tint)}"
   ".kot-leader{fill:none;stroke:var(--hig-color-separator);stroke-width:1;"
   "stroke-dasharray:2 3}"
   ".kot-line-label[data-accent]{fill:var(--hig-color-label);font-weight:700}"
   ;; ── the speed panel on the first screen ──────────────────────────────────
   ".kot-speed{margin:var(--hig-spacing-7) 0 0;padding:var(--hig-spacing-5);"
   "border:1px solid var(--hig-color-separator);border-radius:var(--hig-radius-md);"
   "background:var(--hig-color-system-background)}"
   ".kot-speed h2{margin:var(--hig-spacing-3) 0 0;text-wrap:balance}"
   ".kot-speed-figure{display:flex;align-items:flex-end;flex-wrap:wrap;"
   "column-gap:var(--hig-spacing-4);row-gap:var(--hig-spacing-2);"
   "margin-block:var(--hig-spacing-4) var(--hig-spacing-2)}"
   ".kot-speed-figure p{margin:0}"
   ".kot-speed-value{font-weight:700;line-height:1;letter-spacing:-.01em;"
   "font-size:clamp(calc(44 / 16 * 1rem),11vw,calc(64 / 16 * 1rem))}"
   ".kot-speed-unit{margin-inline-start:.1em;font-size:.4em;color:var(--hig-color-secondary-label)}"
   ".kot-speed-sub{max-width:28rem;padding-block-end:.35rem;color:var(--hig-color-secondary-label);"
   "font-size:var(--hig-text-footnote-font-size);line-height:var(--hig-text-footnote-line-height)}"
   ;; ── reveal ───────────────────────────────────────────────────────────────
   ;; The final state is what the CSS declares; `kot-anim` is added by a head
   ;; script only when it is about to observe and motion is not reduced. With
   ;; no JavaScript the charts render complete rather than empty.
   ".kot-anim .kot-chart .kot-bar-fill,.kot-anim .kot-chart .kot-dv-fill{"
   "transition:width .85s cubic-bezier(.22,.61,.36,1)}"
   ".kot-anim .kot-chart:not(.is-in) .kot-bar-fill,"
   ".kot-anim .kot-chart:not(.is-in) .kot-dv-fill{width:0;min-width:0}"
   ".kot-anim .kot-chart .kot-line{stroke-dasharray:1000;transition:stroke-dashoffset 1.4s ease-out}"
   ".kot-anim .kot-chart:not(.is-in) .kot-line{stroke-dashoffset:1000}"
   ".kot-anim .kot-chart .kot-end,.kot-anim .kot-chart .kot-leader,"
   ".kot-anim .kot-chart .kot-line-label{transition:opacity .5s ease-out .8s}"
   ".kot-anim .kot-chart:not(.is-in) .kot-end,"
   ".kot-anim .kot-chart:not(.is-in) .kot-leader,"
   ".kot-anim .kot-chart:not(.is-in) .kot-line-label{opacity:0}"
   "@media(prefers-reduced-motion:reduce){"
   ".kot-anim .kot-chart *{transition:none!important}"
   ".kot-anim .kot-chart:not(.is-in) .kot-bar-fill,"
   ".kot-anim .kot-chart:not(.is-in) .kot-dv-fill{width:var(--w);min-width:2px}"
   ".kot-anim .kot-chart:not(.is-in) .kot-line{stroke-dashoffset:0}"
   ".kot-anim .kot-chart:not(.is-in) .kot-end,"
   ".kot-anim .kot-chart:not(.is-in) .kot-leader,"
   ".kot-anim .kot-chart:not(.is-in) .kot-line-label{opacity:1}}"))

(def primary-links
  [{:label "Docs" :href "#docs"}
   {:label "Play" :href "#play"}
   {:label "Libraries" :href "#libraries"}
   {:label "Roadmap" :href "#roadmap"}
   {:label "Community" :href "#community"}
   {:label "Sponsor" :href "./sponsor/"}
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

(def ^:private lisp-nest
  "Four nested opening parentheses, drawn as one cubic each.

  Placed once in each margin — the right one mirrored — so the header reads as
  a single enclosing form: `(((( … ))))`. The first attempt put a wide nest
  across the whole header and it was wrong twice over: stretched to the header
  height the arcs were clipped into unrelated curves, and centred they landed
  directly behind the nav, so a texture became scratch marks over the links.
  In the margins there is nothing to sit on top of, and the motif says what it
  is: this is a Lisp, and the page is one form.

  Drawn rather than fetched — an inline SVG makes no request, and its stroke
  is `--hig-color-tint`, so it follows the design system into dark mode like
  every other mark here. `aria-hidden`, because it is ornament and the
  wordmark link already carries the accessible name."
  (let [w 84 h 76 depth 4
        arc (fn [x]
              (let [c (- x 15)]
                (str "M" x " 9 C" c " 27 " c " 49 " x " 67")))]
    [:svg {:class "kot-lisp-nest" :viewBox (str "0 0 " w " " h)
           :aria-hidden "true" :focusable "false"
           :preserveAspectRatio "xMaxYMid meet"}
     (for [i (range depth)]
       [:path {:d (arc (+ 22 (* i 16)))
               :opacity (.toFixed (- 0.95 (* i 0.18)) 2)}])]))

(def lisp-backdrop
  (list [:div {:class "kot-lisp-edge" :data-side "start"} lisp-nest]
        [:div {:class "kot-lisp-edge" :data-side "end"} lisp-nest]))

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
      lisp-backdrop
      (dds/container
       [:div {:class "kot-header__inner"}
        [:a {:class "kot-wordmark" :href (str root "#top") :aria-label "Kotoba home"}
         ;; `( KOTOBA )` — the wordmark read as one form. Text, not an image,
         ;; so it scales with the type and inherits the tint in both themes.
         ;; The link's aria-label already names it, so the glyphs are hidden.
         [:span {:class "kot-paren" :aria-hidden "true"} "("]
         [:img {:class "kot-logo" :src (str root "kotoba-wordmark.png")
                :width 480 :height 68 :alt "Kotoba"}]
         [:span {:class "kot-paren" :aria-hidden "true"} ")"]]
        [:nav {:class "kot-nav" :aria-label "Primary"}
         (for [{:keys [label href]} primary-links]
           (dds/button label {:type :text :size "sm" :href (local-href href)}))
         (dds/button "GitHub" {:type :outline :size "sm"
                                :href "https://github.com/kotoba-lang/kotoba-lang"})]])])))

(def fallback-svg
  (let [pts (hero/stroke-samples 36)
        circles (map (fn [{:keys [x y r density]}]
                       [:circle {:cx (-> x (* 200) (.toFixed 2))
                                 :cy (-> y (* 200) (.toFixed 2))
                                 :r (-> (* 14 r) (.toFixed 2))
                                 :opacity (-> (+ 0.35 (* 0.65 density)) (.toFixed 2))}])
                     pts)]
    (str "<svg viewBox=\"0 0 200 200\" preserveAspectRatio=\"xMidYMid slice\" "
         "aria-hidden=\"true\" focusable=\"false\" id=\"kot-hero-fallback\">"
         (html/->html circles)
         "</svg>")))

(def FXN (str hero/drift-freq-x))
(def FYN (str hero/drift-freq-y))
(def AMAXV (str hero/alpha-max))

(def chart-anim-head-js
  "Runs in <head>, before first paint, so the charts never render complete and
  then collapse. It only arms the reveal — the collapsed state lives behind
  `.kot-anim`, so with no JavaScript, no IntersectionObserver, or reduced
  motion the charts render finished instead of empty."
  (str "(function(){try{"
       "if(!('IntersectionObserver' in window))return;"
       "if(window.matchMedia&&matchMedia('(prefers-reduced-motion: reduce)').matches)return;"
       "document.documentElement.classList.add('kot-anim');"
       "}catch(e){}})();"))

(def chart-anim-js
  (str "(function(){"
       "var r=document.documentElement;"
       "if(!r.classList.contains('kot-anim'))return;"
       "function go(){"
       "var cs=[].slice.call(document.querySelectorAll('.kot-chart'));"
       "function all(){cs.forEach(function(c){c.classList.add('is-in');});}"
       "try{var io=new IntersectionObserver(function(es){"
       "es.forEach(function(e){if(e.isIntersecting){e.target.classList.add('is-in');io.unobserve(e.target);}});"
       "},{threshold:0.12,rootMargin:'0px 0px -6% 0px'});"
       ;; A chart that is never observed, or whose observer never fires, must
       ;; not stay empty: the fallback reveals everything unconditionally.
       "cs.forEach(function(c){io.observe(c);});setTimeout(all,2500);}catch(e){all();}}"
       "if(document.readyState==='loading')"
       "document.addEventListener('DOMContentLoaded',go);else go();"
       "})();"))

(def hero-js
  (let [points-js (str "var P=new Uint8Array([" (str/join "," (vec (hero/quantized-bytes))) "]),"
                       "N=" hero/point-count ",FP=" hero/hero-fps ",DUR=" hero/hero-duration-seconds ","
                       "AX=" hero/drift-amplitude-x ",AY=" hero/drift-amplitude-y ","
                       "FX=" hero/drift-freq-x ",FY=" hero/drift-freq-y ","
                       "AMAX=" hero/alpha-max ";")]
    (str points-js
         "document.addEventListener('DOMContentLoaded',function(){"
         "var wrap=document.getElementById('kot-hero-canvas');"
         "if(!wrap||matchMedia('(prefers-reduced-motion: reduce)').matches)return;"
         ;; The dot colour is not baked: `.kot-hero-canvas` carries
         ;; `color:var(--hig-color-tint)`, so the resolved value follows
         ;; jp-go-dds.dark's mirrored ramp. The shader writes sRGB directly
         ;; (the canvas format is a plain unorm), so the channels are used as
         ;; read — linearising here would shift the hue.
         "var TC='0.0,0.09,0.757';"
         "try{var _m=getComputedStyle(wrap).color.match(/[0-9.]+/g);"
         "if(_m&&_m.length>=3){TC=(_m[0]/255).toFixed(4)+','+(_m[1]/255).toFixed(4)+','+(_m[2]/255).toFixed(4);}}catch(e){}"
         "var canvas=document.createElement('canvas');"
         "var drawn=false;"
         "function svgOnly(){if(drawn)return;drawn=true;"
         "var s=document.getElementById('kot-hero-fallback');"
         "if(s)wrap.appendChild(s);}"
         "function useEngine(c){try{wrap.replaceChild(c,document.getElementById('kot-hero-fallback'));}catch(e){wrap.appendChild(c);}}"
         "async function webgpu(){var gpu=navigator.gpu;if(!gpu)return false;"
         "var adapter=null;try{adapter=await gpu.requestAdapter();}catch(e){adapter=null;}"
         "if(!adapter)return false;var device=null;"
         "try{device=await adapter.requestDevice();}catch(e){device=null;}"
         "if(!device)return false;var ctx=null;"
         "try{ctx=canvas.getContext('webgpu');}catch(e){ctx=null;}"
         "if(!ctx)return false;var format=navigator.gpu.getPreferredCanvasFormat();"
         "try{ctx.configure({device:device,format:format,alphaMode:'premultiplied'});}catch(e){return false;}"
         "var wgsl='struct U{t:f32,ph:f32,ux:f32,uy:f32,ax:f32,ay:f32,w:f32,h:f32};"
         "@group(0)@binding(0)var<uniform>u:U;"
         "struct VOut{@builtin(position)pos:vec4f,@location(0)den:f32,@location(1)rad:f32,@location(2)cen:vec2f};"
         "@vertex fn vs(@builtin(vertex_index)vi:u32,@builtin(instance_index)ii:u32,@location(0)p:vec2f,@location(1)m:vec2f)->VOut{"
         "let corners=array<vec2f,6>(vec2f(-1.0,-1.0),vec2f(1.0,-1.0),vec2f(-1.0,1.0),vec2f(-1.0,1.0),vec2f(1.0,-1.0),vec2f(1.0,1.0));"
         "let ang=6.2831853*u.t*(0.3+0.7*fract(f32(ii)*0.61803399));"
         "let c=vec2f(u.ux,u.uy)+vec2f(u.ax*cos(ang*" FXN "+f32(ii)*0.37),u.ay*sin(ang*" FYN "+f32(ii)*0.61));"
         "let ndc=vec2f(c.x*2.0-1.0,1.0-c.y*2.0);"
         "let rad=m.x*2.0*min(u.w,u.h);"
         "let off=corners[vi]*vec2f(rad/u.w,rad/u.h);"
         "var o:VOut;"
         "o.pos=vec4f(ndc+off,0.0,1.0);"
         "o.den=m.y;o.rad=rad;o.cen=vec2f((ndc.x*0.5+0.5)*u.w,(ndc.y*0.5+0.5)*u.h);"
         "return o;}"
         "@fragment fn fs(f:VOut)->@location(0)vec4f{"
         "let d=length(f.pos.xy-f.cen)/max(f.rad,1.0);"
         "if(d>1.0){discard;}"
         "let a=" AMAXV "*(1.0-d*d)*u.ph*f.den;"
         "return vec4f('+TC+',a);}';"
         "var module=null;"
         "try{module=device.createShaderModule({code:wgsl});}catch(e){return false;}"
         "var pipeline=null;"
         "try{pipeline=device.createRenderPipeline({layout:'auto',"
         "vertex:{module:module,entryPoint:'vs',buffers:[{arrayStride:4,attributes:[{shaderLocation:0,offset:0,format:'unorm8x2'},{shaderLocation:1,offset:2,format:'unorm8x2'}]}]},"
         "fragment:{module:module,entryPoint:'fs',targets:[{format:format,blend:{color:{srcFactor:'src-alpha',dstFactor:'one-minus-src-alpha',operation:'add'},alpha:{srcFactor:'one',dstFactor:'one-minus-src-alpha',operation:'add'}}}]},"
         "primitive:{topology:'triangle-list'}});}catch(e){return false;}"
         "var vbuf=device.createBuffer({size:P.byteLength,usage:GPUBufferUsage.VERTEX|GPUBufferUsage.COPY_DST});"
         "device.queue.writeBuffer(vbuf,0,P);"
         "var ubuf=device.createBuffer({size:32,usage:GPUBufferUsage.UNIFORM|GPUBufferUsage.COPY_DST});"
         "var bind=device.createBindGroup({layout:pipeline.getBindGroupLayout(0),entries:[{binding:0,resource:{buffer:ubuf}}]});"
         "useEngine(canvas);"
         "function size(){var dpr=Math.min(window.devicePixelRatio||1,2);"
         "var r=wrap.getBoundingClientRect();canvas.width=Math.max(1,Math.round(r.width*dpr));"
         "canvas.height=Math.max(1,Math.round(r.height*dpr));}"
         "size();window.addEventListener('resize',size);"
         "var t0=null,last=0;"
         "function frame(ms){requestAnimationFrame(frame);"
         "if(ms-last<1000/FP-1)return;last=ms;"
         "if(t0===null)t0=ms;var t=(ms-t0)/1000;"
         "if(t>DUR){t0=ms;t=0;}"
         "var ph=0.55+0.45*Math.sin(t*0.35);"
         "var ang2=6.2831853*t/DUR;"
         "var ux=0.5+AX*Math.cos(ang2*FX),uy=0.5+AY*Math.sin(ang2*FY);"
         "var ax=AX*0.6*Math.sin(t*0.23),ay=AY*0.6*Math.sin(t*0.31+1.7);"
         "device.queue.writeBuffer(ubuf,0,new Float32Array([t,ph,ux,uy,ax,ay,canvas.width,canvas.height]));"
         "var enc=device.createCommandEncoder();"
         "var pass=enc.beginRenderPass({colorAttachments:[{view:ctx.getCurrentTexture().createView(),clearValue:{r:0,g:0,b:0,a:0},loadOp:'clear',storeOp:'store'}]});"
         "pass.setPipeline(pipeline);pass.setVertexBuffer(0,vbuf);pass.setBindGroup(0,bind);"
         "pass.draw(6,N);pass.end();"
         "device.queue.submit([enc.finish()]);}"
         "requestAnimationFrame(frame);return true;}"
         "function webgl2(){var gl=null;try{gl=canvas.getContext('webgl2',{antialias:true,alpha:true});}catch(e){gl=null;}"
         "if(!gl)return false;var vs='#version 300 es\\nin vec2 a;in vec2 m;uniform vec2 u;"
         "uniform float t,minWH,ax,ay,FX,FY;out float vden;void main(){"
         "float ang=6.2831853*t*(0.3+0.7*fract(float(gl_VertexID)*0.61803399));"
         "vec2 c=u+vec2(ax*cos(ang*FX+float(gl_VertexID)*0.37),ay*sin(ang*FY+float(gl_VertexID)*0.61));"
         "vden=m.y;gl_Position=vec4((c*2.0-1.0)*vec2(1.0,-1.0),0.0,1.0);"
         "gl_PointSize=max(1.0,m.x*2.0*minWH);}';"
         "var fs='#version 300 es\\nprecision mediump float;in float vden;uniform float amax,ph;out vec4 oc;void main(){"
         "vec2 d=gl_PointCoord-vec2(0.5);float r=length(d)*2.0;if(r>1.0)discard;"
         "float a=amax*(1.0-r*r)*ph*vden;oc=vec4('+TC+',a);}';"
         "function sh(t,s){var o=gl.createShader(t);gl.shaderSource(o,s);gl.compileShader(o);"
         "if(!gl.getShaderParameter(o,gl.COMPILE_STATUS)){throw new Error(gl.getShaderInfoLog(o));}return o;}"
         "var prog=gl.createProgram();gl.attachShader(prog,sh(gl.VERTEX_SHADER,vs));"
         "gl.attachShader(prog,sh(gl.FRAGMENT_SHADER,fs));gl.linkProgram(prog);"
         "if(!gl.getProgramParameter(prog,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(prog));"
         "gl.useProgram(prog);var buf=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buf);"
         "gl.bufferData(gl.ARRAY_BUFFER,P,gl.STATIC_DRAW);"
         "var loc=gl.getAttribLocation(prog,'a');gl.enableVertexAttribArray(loc);"
         "gl.vertexAttribPointer(loc,2,gl.UNSIGNED_BYTE,true,4,0);"
         "var locM=gl.getAttribLocation(prog,'m');gl.enableVertexAttribArray(locM);"
         "gl.vertexAttribPointer(locM,2,gl.UNSIGNED_BYTE,true,4,2);"
         "var U={};['u','t','minWH','ax','ay','FX','FY','amax','ph'].forEach(function(n){U[n]=gl.getUniformLocation(prog,n);});"
         "gl.uniform1f(U.FX,FX);gl.uniform1f(U.FY,FY);gl.uniform1f(U.amax,AMAX);"
         "useEngine(canvas);"
         "function size(){var dpr=Math.min(window.devicePixelRatio||1,2);"
         "var r=wrap.getBoundingClientRect();canvas.width=Math.max(1,Math.round(r.width*dpr));"
         "canvas.height=Math.max(1,Math.round(r.height*dpr));gl.viewport(0,0,canvas.width,canvas.height);"
         "gl.uniform1f(U.minWH,Math.min(canvas.width,canvas.height));}"
         "size();window.addEventListener('resize',size);"
         "var t0=null,last=0;function frame(ms){requestAnimationFrame(frame);"
         "if(ms-last<1000/FP-1)return;last=ms;"
         "if(t0===null)t0=ms;var t=(ms-t0)/1000;"
         "if(t>DUR){t0=ms;t=0;}"
         "gl.uniform1f(U.t,t);gl.uniform1f(U.ph,0.55+0.45*Math.sin(t*0.35));"
         "var ang=6.2831853*t/DUR;"
         "gl.uniform2f(U.u,0.5+AX*Math.cos(ang*FX),0.5+AY*Math.sin(ang*FY));"
         "gl.uniform1f(U.ax,AX*0.6*Math.sin(t*0.23));"
         "gl.uniform1f(U.ay,AY*0.6*Math.sin(t*0.31+1.7));"
         "gl.clearColor(0.0,0.0,0.0,0.0);gl.clear(gl.COLOR_BUFFER_BIT);"
         "gl.drawArrays(gl.POINTS,0,N);requestAnimationFrame(frame);}"
         "requestAnimationFrame(frame);return true;}"
         "webgpu().then(function(ok){if(ok)return;"
         "try{if(webgl2())return;}catch(e){}"
         "svgOnly();});});")))

(defn hero-canvas []
  [:div {:class "kot-hero-canvas" :id "kot-hero-canvas" :aria-hidden "true"}
   (html/raw fallback-svg)])

(defn- ms [x] (str (.toFixed x 2) " ms"))

(def lane-status-text
  "Why a lane produced no number. Never rendered as a zero-length bar: the
  fastest way to emit an artifact is to emit a broken one, and a chart that
  draws \"absent\" and \"instant\" alike rewards exactly that."
  {"invalid" "invalid artifact" "failed" "build failed"
   "unavailable" "no toolchain" "not-run" "budget spent"})

(defn speed-panel
  "The speed claim on the first screen.

  The word `fastest` appears here only while `cold-start` reports that every
  ordering behind it passed perfgate; if a rerun loses one, the chip, the
  heading and the sentence step down together rather than one of them being
  left behind."
  []
  (let [{:keys [median rows qualified? beaten date host k samples]} cold-start]
    [:div {:class "kot-speed"}
     (dds/chip-label (if qualified?
                       (str "FASTEST COLD BUILD · " beaten " OF " beaten
                            " ORDERINGS QUALIFIED")
                       "COLD BUILD · ORDERING UNQUALIFIED")
                     (if qualified? {} {:color "gray"}))
     (dds/heading 2
                  (if qualified?
                    "Fastest cold build of any toolchain on this host."
                    "Lowest cold-build median on this host; ordering unqualified.")
                  {:size "32"})
     [:div {:class "kot-speed-figure"}
      [:p {:class "kot-speed-value"} (.toFixed median 2)
       [:span {:class "kot-speed-unit"} "ms"]]
      [:p {:class "kot-speed-sub"}
       "Kotoba source to a WebAssembly artifact, process-cold — then executed, "
       "and the answer checked after the clock stopped."]]
     (chart/ranked-bars
      {:axis (str "Process-cold build wall time in milliseconds; shorter is faster. "
                  "K=" k " source, lanes interleaved on one host, "
                  samples " samples each.")
       :rows (for [{:keys [label target median ratio status lead?]} rows]
               (if (= "measured" status)
                 {:label label :sub target :value median
                  :display (ms median)
                  :note (if lead? "fastest here" (str (.toFixed ratio 1) "× Kotoba"))
                  :lead? lead?}
                 {:label label :sub target
                  :absent (get lane-status-text status status)}))})
     [:p {:class "kot-caption kot-muted"}
      (if qualified?
        (list (str "All " beaten " orderings pass ")
              (external-link "https://github.com/kotoba-lang/perfgate" "perfgate")
              " at its unrelaxed default policy — at least 5% and separated from the "
              "arms' own spread — so the ordering holds even though the host was busy. ")
        "The ordering did not pass its gate on this run. ")
      "Bounded to this host, this source size and this run: build time is not "
      "execution speed, the advantage narrows as the source grows, and the released "
      "binary has a hard correctness ceiling. All five benchmarks, including the ones "
      "that go against Kotoba, are below. Measured "
      date " on " host "."]
     [:div {:class "kot-actions"}
      (dds/button "See the five benchmarks" {:href "#benchmark"})
      (dds/button "Inspect the samples"
                  {:href "./benchmarks/build-scaling-latest.json" :type :text})]]))

(defn hero []
  [:section {:id "top" :class "kot-hero"}
   (hero-canvas)
   (dds/container
    [:p {:class "kot-eyebrow"} "A language AI agents can use, not abuse"]
    (dds/heading 1 "AI writes freely. Kotoba draws the boundary." {:size "48"})
    [:p {:class "kot-lead"}
     "Kotoba is an intuitive, declarative, security-first language and computing stack for AI agents—and for humans who vibe-code with them. Post-quantum cryptography is the admission floor for every new cryptographic boundary, not an optional mode."]
    (speed-panel)
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
            [:p "The host and provider enforce concrete scope and record the decision."])
      (card (dds/chip-label "POST-QUANTUM FLOOR")
            (dds/heading 3 "No classical-only downgrade" {:size "20"})
            [:p "New encryption and publication boundaries require ML-KEM or ML-DSA evidence and reject stripped PQ material."]))])])

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

(defn typed-eval-section []
  (dds/section
   {:id "typed-eval" :title "Lisp eval, without ambient host eval"}
   [:p {:class "kot-lead"}
    "Kotoba evaluates checked code as content-addressed data. The familiar "
    (code "(eval request)")
    " surface lowers to the typed " (code ":code/eval")
    " ability; it never receives source text, a reader form, a namespace, or a host object."]
   (dds/grid
    {:min "16rem"}
    (card (dds/chip-label "DEFINITION CID")
          (dds/heading 3 "What code?" {:size "20"})
          [:p "The CID selects a hash-verified checked-KIR definition and its CID-only dependency closure."])
    (card (dds/chip-label "ADMISSION CID")
          (dds/heading 3 "May it run here?" {:size "20"})
          [:p "The exact interface, complete effect row, current allowance, fuel, and decreasing eval depth are bound before execution."])
    (card (dds/chip-label "VALUE CID")
          (dds/heading 3 "What came back?" {:size "20"})
          [:p "The typed result is persisted as content-addressed evidence. Its hash cannot retroactively authorize an effect."]))
   [:blockquote {:class "kot-quote"}
    [:strong "Identity, authority, and result evidence are three different facts."]]
   (caption "Machine contract: "
            (code "lang/typed-eval.edn") ". Compiler wire capability: "
            (code (str (:compiler-wire-id typed-eval))) ". "
            "Bounded apply remains ordinary closed-module closure application.")))

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
   {:id "what" :title "Where Lisp's mind and GP 2's graph rewriting meet Rust's discipline"}
   [:p {:class "kot-lead"}
    "Kotoba is a small, data-oriented, Clojure-shaped language. Its design draws on Lisp's code-as-data tradition and "
    [:a {:href "https://uoycs-plasma.github.io/GP2/"} "GP 2's rule-based graph rewriting"]
    ", with static discipline around authority, effects, resources, packages, and artifact identity."]
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

(defn defaults-section []
  (apply dds/section
         {:id "defaults" :title "Defaults for an AI-first computing stack"}
         [:p {:class "kot-lead"}
          "These are engineering claims with their qualification attached. Default, bounded-ready, partial, and direction are different states; none is silently promoted to universal."]
         (dds/grid
          {:min "18rem"}
          (for [{:keys [status headline body]} (:claims product-defaults)]
            (card (dds/chip-label (-> status name str/upper-case
                                      (str/replace "-" " "))
                                  {:color (if (contains? #{:default :ready-bounded} status)
                                            "blue" "gray")})
                  (dds/heading 3 headline {:size "24"})
                  [:p body])))
         [[:p {:class "kot-caption kot-muted"}
           "Machine authority: " (code "lang/product-defaults.edn")
           ". Unlimited physical storage, zero copies everywhere, universal speed rank, AGI achieved, and unhackable remain forbidden absolute claims."]]))

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
           (caption "This executes a precompiled, immutable example. Editing arbitrary source in the browser is not yet a shipped compiler surface.")
           [:p {:class "kot-caption kot-muted"}
            "Interactive demos: "
            (external-link "https://kotoba-lang.github.io/wasm-webcomponent/examples/solar-helix/"
                           "solar-helix (guest-driven WebGPU render)")
            " · "
            (external-link "https://kotoba-lang.github.io/wasm-webcomponent/examples/kami-survivors/"
                           "kami-survivors (a .kotoba game)")
            " · "
            (external-link "https://kotoba-lang.github.io/wasm-webcomponent/examples/gpu-clear/"
                           "gpu-clear (WebGPU smoke)")
            ". Hosted on the wasm-webcomponent GitHub Pages surface; availability is per-browser WebGPU/WebAssembly support."]]))))

(def catalog-tag-order
  "Plane tags first, then domains, each in taxonomy order. The taxonomy file
  decides the order so the chip row and the tag vocabulary cannot drift."
  (concat (map :id (:planes library-taxonomy))
          (map :id (:domains library-taxonomy))))

(defn- catalog-chip
  "One filter control. On the catalogue page it is a toggle button; on the
  landing page it is a link into the catalogue, so the landing page's chips
  keep working with JavaScript off."
  [tag n {:keys [href]}]
  (let [label (str (catalog/label-of library-taxonomy tag) " · " (thousands n))]
    (if href
      (dds/button label {:type :outline :size "sm"
                         :href (str href "#tag=" (catalog/topic-of library-taxonomy tag))})
      (dds/button label {:type :outline :size "sm"
                         :attrs {:data-cat-tag (catalog/topic-of library-taxonomy tag)
                                 :aria-pressed "false"}}))))

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
   (dds/heading 3 "Browse the whole organisation by tag" {:size "24"})
   [:p
    "There are " (thousands (:total tagged-catalog)) " public repositories in the "
    (code "kotoba-lang") " organisation. Every tag below is the GitHub topic of "
    "the same name, so the site filter and the org's topics are one vocabulary "
    "rather than two that drift. Pick one to open the catalogue already filtered."]
   [:div {:class "kot-cat-tags"}
    (for [tag catalog-tag-order
          :let [n (get (:counts tagged-catalog) tag 0)]
          :when (pos? n)]
      (catalog-chip tag n {:href "./libraries/"}))]
   [:p [:a {:class "kot-link" :href "./libraries/#catalog"}
        (str "Browse and filter all " (thousands (:total tagged-catalog)) " repositories")]]
   (caption
    (str "A repository is not a published package. Exactly "
         (count (:records public-package-registry))
         " library is published through the content-addressed registry; the rest of "
         "this list is discovery. Repository maturity labels do not imply 1.0 API "
         "stability, broad adoption, or production SLOs, and "
         (thousands (:untagged tagged-catalog))
         " repositories match no domain rule and are shown untagged rather than "
         "given the nearest label."))))

(defn catalog-section
  "The whole public repository list, rendered into the page and filtered in
  the browser.

  The list is server-rendered rather than built from an embedded JSON blob:
  with JavaScript off, or before the script runs, the complete catalogue is
  still the page. The controls are the part that needs a script, so they are
  the part that is hidden until one runs."
  []
  (let [{:keys [repos counts untagged described total]} tagged-catalog]
    (dds/section
     {:id "catalog" :title "Every public repository, filterable"}
     [:p {:class "kot-lead"}
      "The " (thousands total) " public repositories in the "
      (code "kotoba-lang") " organisation, with the tag vocabulary the "
      "organisation's GitHub topics use. Filter by name, by description, or by "
      "any combination of tags."]
     [:div {:class "kot-cat-controls" :id "kot-cat-controls" :hidden true}
      (dds/form-field
       {:label "Filter repositories" :for "kot-cat-q"
        :support "Try: os, driver, sim, wasm, ipld. Text narrows; tags widen — a repository matching any selected tag is shown."
        :support-id "kot-cat-q-support"}
       (dds/input-text {:id "kot-cat-q" :type "search"
                        :aria-label "Filter Kotoba repositories"
                        :aria-describedby "kot-cat-q-support"}))
      [:div {:class "kot-cat-tags"}
       (for [tag catalog-tag-order
             :let [n (get counts tag 0)]
             :when (pos? n)]
         (catalog-chip tag n {}))
       (dds/button "Clear" {:type :text :size "sm"
                            :attrs {:id "kot-cat-clear"}})]]
     [:p {:id "kot-cat-count" :class "kot-caption kot-muted" :aria-live "polite"}
      (str "Showing " (thousands total) " of " (thousands total) " repositories")]
     [:ol {:class "kot-cat-list" :id "kot-cat-list"}
      (for [{:keys [name description tags topics archived?]} repos]
        [:li {:class "kot-lib"
              :data-t (str/join " " (map #(catalog/topic-of library-taxonomy %) tags))}
         [:a {:class "kot-lib-name"
              :href (str "https://github.com/kotoba-lang/" name) :rel "noreferrer"}
          name]
         (when archived? [:span {:class "kot-lib-flag"} "archived"])
         (when description [:span {:class "kot-lib-desc"} description])
         (when (or (seq tags) (seq topics))
           [:span {:class "kot-lib-tags"}
            (for [t (distinct (concat (map #(catalog/topic-of library-taxonomy %) tags)
                                      topics))]
              [:span {:class "kot-lib-tag"} t])])])]
     (caption
      (str "Filtering runs in the browser and no query leaves it; with JavaScript "
           "off the complete list is still rendered above. "
           (thousands described) " of these repositories carry a GitHub description, "
           "so a domain tag has that much evidence to work from — "
           (thousands untagged) " match no domain rule and are shown untagged rather "
           "than assigned the nearest label. A repository list is discovery. It is not "
           "the package registry: exactly one library is published through the "
           "content-addressed registry described above, and a repository here is "
           "neither a released package nor an API-stability claim."))
     (caption "Tag vocabulary: " (code "site/library-taxonomy.edn")
              ". Repository snapshot: " (code "site/library-catalog.edn")
              ", fetched " (:fetched-at library-catalog) "."))))

(def catalog-js
  (str "(function(){"
       "var list=document.getElementById('kot-cat-list');if(!list)return;"
       "var items=[].slice.call(list.children);"
       ;; The haystack is built once from the rendered text. Repeating it in a
       ;; data-* attribute would put every description in the document twice
       ;; (measured: +250 KB) to save work this loop does in one pass at load.
       "var hay=items.map(function(e){return e.textContent.toLowerCase();});"
       "var tg=items.map(function(e){return ' '+(e.getAttribute('data-t')||'')+' ';});"
       "var q=document.getElementById('kot-cat-q');"
       "var count=document.getElementById('kot-cat-count');"
       "var chips=[].slice.call(document.querySelectorAll('[data-cat-tag]'));"
       "var on={};"
       "function n(x){return x.toLocaleString('en-US');}"
       "function apply(){var s=(q.value||'').trim().toLowerCase();"
       "var keys=Object.keys(on).filter(function(k){return on[k];});var shown=0;"
       "for(var i=0;i<items.length;i++){"
       "var ok=!s||hay[i].indexOf(s)>-1;"
       "if(ok&&keys.length){ok=false;"
       "for(var j=0;j<keys.length;j++){if(tg[i].indexOf(' '+keys[j]+' ')>-1){ok=true;break;}}}"
       "items[i].hidden=!ok;if(ok){shown++;}}"
       "count.textContent='Showing '+n(shown)+' of '+n(items.length)+' repositories';}"
       "function set(t,v){on[t]=v;chips.forEach(function(c){"
       "if(c.getAttribute('data-cat-tag')===t)c.setAttribute('aria-pressed',v?'true':'false');});}"
       "chips.forEach(function(c){c.addEventListener('click',function(){"
       "var t=c.getAttribute('data-cat-tag');set(t,!on[t]);apply();});});"
       "q.addEventListener('input',apply);"
       "var clear=document.getElementById('kot-cat-clear');"
       "if(clear)clear.addEventListener('click',function(){"
       "Object.keys(on).forEach(function(t){set(t,false);});q.value='';apply();});"
       ;; Deep link from the landing page's chips: /libraries/#tag=<topic>
       "function fromHash(){"
       "var m=/[#&]tag=([a-z0-9-]+)/.exec(location.hash);"
       "if(m&&chips.some(function(c){return c.getAttribute('data-cat-tag')===m[1];})){"
       "set(m[1],true);apply();}}"
       ;; A chip link followed from THIS page changes only the fragment, which
       ;; is a same-document navigation: without this listener the filter would
       ;; work from the landing page and do nothing from inside the catalogue.
       "window.addEventListener('hashchange',fromHash);"
       "fromHash();apply();"
       "var ctl=document.getElementById('kot-cat-controls');if(ctl)ctl.hidden=false;"
       "})();"))

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

(defn sponsor-section []
  (dds/section
   {:id "sponsor" :title "Fund the public boundary, without buying authority"}
   [:p {:class "kot-lead"}
    (if sponsorship-live?
      "GitHub Sponsors is open for people and organizations that want to sustain Kotoba's public language, tooling, qualification, and evidence."
      "The Kotoba GitHub Sponsors profile is being prepared. The project page is ready now and will expose a payment action only after GitHub approves the organization profile.")]
   (dds/grid
    {:min "18rem"}
    (card (dds/chip-label (if sponsorship-live? "OPEN" "PREPARING"))
          (dds/heading 3 "GitHub Sponsors" {:size "24"})
          [:p (if sponsorship-live?
                "Choose a one-time or monthly tier on GitHub. GitHub handles the payment and sponsorship management."
                "No sponsorship payment can be made through kotoba-lang.org while the GitHub profile is not live.")]
          [:p [:a {:class "kot-link" :href "./sponsor/"} "See sponsorship status and principles"]])
    (card (dds/chip-label "NO PURCHASE")
          (dds/heading 3 "Support is not authority" {:size "24"})
          [:p "Sponsorship does not buy a feature, roadmap priority, support SLA, private access, or a security exception."]))
   (caption "Sponsorship status: " (str/upper-case (name (:status sponsorship)))
            ". Checked " (:checked-at sponsorship) ".")))

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

(def build-scaling-lane-order
  [:kotoba-wasm-cli :amu-wasm :amu-native
   :rustc-wasm :rustc-native :clang-wasm :clang-native :javac])

(defn- scaling-cell
  "One cell of the scaling table.

  A lane that did not build is never rendered as a blank or a dash. The
  fastest way to emit an artifact is to emit a broken one, so a build that
  produced something which is not the program has to read differently from a
  build that was quick."
  [result]
  (case (:status result)
    "measured" (str (get-in result [:summary :median]) " ms")
    "invalid" "invalid artifact"
    "failed" "build failed"
    "unavailable" "no toolchain"
    "not-run" "budget spent"
    (or (:status result) "—")))

(defn build-scaling-section []
  (let [scales (:scales build-scaling)
        lanes (:lanes build-scaling)
        present (filter #(contains? lanes %) build-scaling-lane-order)
        absolute (:absoluteTimes build-scaling)
        measured-date (subs (:generatedAt build-scaling) 0 10)
        host (get-in build-scaling [:environment :cpu])
        hostname (get-in build-scaling [:environment :hostname])
        at (fn [k lane] (get-in (first (filter #(= k (:k %)) scales)) [:lanes lane]))
        median (fn [k lane] (get-in (at k lane) [:summary :median]))
        largest-measured (fn [lane]
                           (last (for [s scales
                                       :when (= "measured" (get-in s [:lanes lane :status]))]
                                   (:k s))))
        cli-top (largest-measured :kotoba-wasm-cli)
        amu-top (largest-measured :amu-wasm)
        cli-broken (first (for [s scales
                                :when (= "invalid" (get-in s [:lanes :kotoba-wasm-cli :status]))]
                            (:k s)))
        biggest (:k (last scales))
        ordering-at (fn [k] (get-in (first (filter #(= k (:k %)) scales))
                                    [:ordering :kotoba-wasm-cli] {}))
        ;; every size where the released CLI still emits a valid module — the
        ;; crossover is the result, so showing only the ends would hide it
        ordering-scales (for [sc scales
                              :when (= "measured" (get-in sc [:lanes :kotoba-wasm-cli :status]))]
                          (:k sc))
        smallest-ordering (ordering-at (:k (first scales)))
        cli-fastest-when-small (and (seq smallest-ordering)
                                    (every? :qualifiedFaster (vals smallest-ordering)))
        ;; Short lane names for the line ends. `Amu` appears twice in the
        ;; report's own labels, once per target, so the target has to be in
        ;; the name or two different lines get the same label.
        lane-short {:kotoba-wasm-cli "Kotoba CLI" :amu-wasm "Amu → Wasm"
                    :amu-native "Amu → native" :rustc-wasm "rustc → Wasm"
                    :rustc-native "rustc → native" :clang-native "clang → native"
                    :javac "javac"}
        scaling-series
        (for [id present
              :let [sts (map #(get-in % [:lanes id :status]) scales)
                    last-m (last (keep-indexed (fn [i st] (when (= "measured" st) i)) sts))
                    ;; What happened AFTER the last measured size, not the
                    ;; first gap anywhere: a lane that emitted a broken
                    ;; artifact and a lane that refused to build are two
                    ;; different results and get two different end marks.
                    after (when last-m (first (drop (inc last-m) sts)))
                    pts (vec (for [sc scales
                                   :when (= "measured" (get-in sc [:lanes id :status]))]
                               [(:k sc) (get-in sc [:lanes id :summary :median])]))]
              :when (seq pts)]
          {:id id
           :accent? (= id :kotoba-wasm-cli)
           :label (lane-short id (get-in lanes [id :label]))
           :points pts
           :end-kind (case after
                       "invalid" :broke
                       ("failed" "unavailable" "not-run") :refused
                       :ok)})]
    (list
     (dds/heading 3 "Build time as the source gets larger" {:size "24"})
     [:p
      "The benchmarks above build a program small enough to fit on one screen, "
      "which measures how quickly a toolchain starts. It says little about the "
      "number a developer actually waits on, which is the slope. This fifth "
      "benchmark generates the same program at increasing sizes — "
      (code "K") " independent four-operation functions and one entry point that "
      "calls all of them — and builds it through every toolchain on the host, in "
      "rotating order."]
     [:p
      "It then runs what each toolchain produced, after the clock has stopped. "
      "That check is not decoration. The fastest way to emit an artifact is to "
      "emit a broken one, so a lane that stopped working would otherwise post "
      "its best numbers exactly where it stopped working."]
     (chart/log-lines
      {:series scaling-series
       :x-ticks [[1 "K=1"] [32 "32"] [128 "128"] [512 "512"] [2048 "2048"]]
       :y-ticks [[20 "20 ms"] [100 "100 ms"] [1000 "1 s"] [10000 "10 s"]]
       :x-title "Generated functions (log scale)"
       :y-title "Build wall time (log scale)"})
     (caption
      (str "Both axes are logarithmic: the sources span three orders of magnitude "
           "and so do the times. A line ends in a dot where the run ended, in a "
           "cross where that lane emitted an artifact that is not the program, and "
           "in a bar where the toolchain refused to build. Those three are not the "
           "same event and the two failures below are not the same failure."))
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "Process-cold build wall time by source size; medians, one host, lanes interleaved"
        :headers (into ["Toolchain / target"]
                       (for [s scales] (str "K=" (:k s))))
        :row-header? true
        :rows (for [id present]
                (into [(str (get-in lanes [id :label]) " · "
                            (get-in lanes [id :target]))]
                      (for [s scales] (scaling-cell (get-in s [:lanes id])))))})]
     (caption
      (str "Measured " measured-date " on " hostname " (" host "). "
           "K is the number of generated functions; the Kotoba source runs from "
           (get-in (first scales) [:sourceLines :kotoba]) " to "
           (get-in (last scales) [:sourceLines :kotoba]) " lines. "
           "Targets, ABIs, optimisation levels and runtime contracts differ across "
           "lanes, so this asks about developer feedback latency, not equivalent work. "
           (if (:qualified absolute)
             "The host stayed under the quiet-load gate, so these milliseconds are absolute figures for this machine."
             (str "The host-load gate failed (load1 "
                  (str/join "–" (map str (take 2 (:observedLoad1 absolute))))
                  ", required ≤ " (:quietLoad1Limit absolute)
                  "), so these are observations of this run rather than portable figures. "
                  "Because the lanes are interleaved, the ordering is qualified separately."))))
     (dds/heading 3 "Which orderings survive the noise test" {:size "24"})
     [:p
      "A ratio is not a ranking. "
      (external-link "https://github.com/kotoba-lang/perfgate" "perfgate")
      " refuses any ordering whose gap falls inside the two arms' own spread, "
      "however large the ratio looks, and refuses an arm with too few samples or "
      "too much noise. It runs here at its own default policy, unrelaxed — a "
      "threshold loosened to let this run through would be a benchmark measuring "
      "its own thresholds. Because the lanes are interleaved on one host, a gap "
      "that survives this test survives the host being busy."]
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "Released Kotoba CLI against each comparator, at every size where it still emits a valid module"
        :headers ["Size" "Compared against" "Kotoba faster?" "Gap vs combined spread" "Why not, if not"]
        :row-header? true
        :rows (for [k ordering-scales
                    [id v] (sort-by key (ordering-at k))]
                (let [sep (:separation v)
                      reasons (distinct (map :reason (:reasons v)))]
                  [(str "K=" k)
                   (str (get-in lanes [id :label]) " · " (get-in lanes [id :target]))
                   (if (:qualifiedFaster v) "yes, qualified" "no")
                   (str (some-> (:gap sep) (.toFixed 1)) " ms vs "
                        (some-> (:summed-stdev sep) (.toFixed 1)) " ms")
                   (if (:qualifiedFaster v) "—" (str/join ", " reasons))]))})]
     (caption
      (str "improvement-below-threshold means the Kotoba lane was not faster at that size at all. "
           "The advantage is real and qualified at cold start, and it is gone against C by K=32 "
           "and against Rust by K=" cli-top ". That crossover is the result, so it is shown rather "
           "than summarised away."))
     (dds/heading 3 "Two failures that are not the same failure" {:size "24"})
     [:p
      "Three Kotoba lanes stopped working in this run, and publishing them as one "
      "row would have been wrong. One is a defect. The other two are declared "
      "bounds being enforced exactly as specified, and reporting those as defects "
      "would mean measuring the bounds instead of the compiler."]
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "What stopped, and what it means"
        :headers ["Observation" "Reading"]
        :row-header? true
        :rows [[(str "The released kotoba CLI emits a module that will not compile above "
                     cli-top " functions")
                (str "A defect, and the reason to validate inside a harness. At K="
                     (inc cli-top) " a call has to carry function index " cli-top
                     ", the first value that needs two LEB128 bytes, and the emitter writes "
                     "one. The bytes say it is not a missing encoder but an unused one: "
                     "local.set 128 is written 80 01, and call 128 one instruction later is "
                     "written 80. The count of truncated operands is exactly K minus " cli-top
                     ". The current compiler does not have it"
                     (if cli-broken (str " — Amu builds K=" cli-broken " correctly, and the "
                                         "fix has been on its emitter's default branch since "
                                         "before this release was tagged.") "."))]
               ["Every Kotoba lane traps at K=512 when built with default settings"
                (str "Not a defect. A Kotoba module carries a declared call-fuel budget and "
                     "the compiler default is 512 calls, which this workload crosses at K=512 "
                     "where the entry point calls 512 leaves. The harness declares "
                     (thousands (get-in build-scaling [:method :declaredKotobaFuel]))
                     " units explicitly and records that it did. C, Rust and Java have no "
                     "equivalent bound to raise.")]
               ["Amu refuses the module outright once it would hold more than 1,024 functions"
                (str "Also not a defect, and the opposite of the first row. max-functions is a "
                     "declared admission limit, so the compiler stops with "
                     "kotoba.error/subset-reject and names what it refused, rather than emitting "
                     "something that will not load. A loud ceiling and a silent one are very "
                     "different results, and only a harness that executes the artifact tells "
                     "them apart. The limit is per module: a larger program is a multi-module "
                     "project, which this single-file benchmark deliberately does not exercise.")]]})]
     (dds/heading 3 "What this establishes" {:size "24"})
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "The fifth benchmark's answers, including the ones that are unfavourable"
        :headers ["Question" "Answer from this run"]
        :row-header? true
        :rows [["How fast is a cold Kotoba build of a small module?"
                (str "The released CLI builds K=1 in " (median 1 :kotoba-wasm-cli)
                     " ms process-cold, artifact executed and answer checked — the "
                     "fastest first result of any lane measured here.")]
               ["How large a module can the released binary build?"
                (str "Up to " cli-top " functions. Beyond that it is not slower, it is wrong, "
                     "and this harness reports that as a failed lane rather than a fast one.")]
               ["Does build time stay competitive as the source grows?"
                (str "Through K=" cli-top " the released CLI is measured against Rust and C "
                     "in the table above. Past that point the only Kotoba compiler that "
                     "still emits a correct module is Amu, which runs on nbb rather than as "
                     "a released binary, and is roughly an order of magnitude slower at every "
                     "size measured — so at large sizes build speed is not currently a Kotoba "
                     "strength, and this page is not going to claim otherwise.")]
               ["How large a source has been built end to end?"
                (str "K=" (or amu-top biggest) " through Amu — "
                     (get-in (first (filter #(= amu-top (:k %)) scales)) [:sourceLines :kotoba])
                     " lines of Kotoba, artifact executed and the answer checked. That is one "
                     "function short of the declared 1,024 ceiling, and the next size up is "
                     "refused rather than mis-built.")]
               ["Is the emitted code fast?"
                "Out of scope here — this measures building, not running. The native runtime suite above asks that question."]]})]
     [:p
      [:strong "Bottom line: "]
      (if cli-fastest-when-small
        (str "At the smallest size the released binary is faster than every comparator here by "
             "a margin that survives the noise test, and it has a hard correctness ceiling at "
             cli-top " functions. ")
        (str "The released binary has the lowest cold-start median on this host, though not by "
             "a margin the noise test will qualify against every comparator, and it has a hard "
             "correctness ceiling at " cli-top " functions. "))
      "The compiler without that ceiling is roughly an order of magnitude slower at every size "
      "measured. Both facts come from the same run, and the harness that found them is public, "
      "so the run can be disagreed with."]
     [:div {:class "kot-actions"}
      (dds/button "Inspect build-scaling samples"
                  {:href "./benchmarks/build-scaling-latest.json"})
      (external-link "https://github.com/kotoba-lang/buildbench"
                     "Re-run it on your machine")
      (external-link "https://github.com/kotoba-lang/kotoba/issues/526"
                     "The defect, with its bytes")])))

(def benchmark-provenance
  "Where each published benchmark actually lives.

  A URL the report itself carries is preferred over one written here: the
  runtime suite and the build-scaling harness both name their own harness,
  manifest and method, so those are read out of the JSON. The other three run
  from this repository, and their paths are checked against the working tree
  at build time (see `benchmark-provenance-rows`) — a moved script fails the
  build instead of shipping a dead link."
  [{:id :compile
    :question "Compiler startup"
    :repo "kotoba-lang/kotoba-lang"
    :harness "scripts/benchmark-public-compile.mjs"
    :extra ["bench/public-compile-comparison/README.md"
            "bench/public-compile-comparison/main.kotoba"]
    :report "bench/public-compile-comparison/latest.json"
    :published "./benchmarks/compile-wasm-latest.json"}
   {:id :end-to-end
    :question "Developer loop"
    :repo "kotoba-lang/kotoba-lang"
    :harness "scripts/benchmark-public-end-to-end.mjs"
    :extra ["bench/public-end-to-end-comparison/README.md"]
    :report "bench/public-end-to-end-comparison/latest.json"
    :published "./benchmarks/end-to-end-latest.json"}
   {:id :runtime
    :question "Native runtime"
    :repo "kotoba-lang/amu"
    :harness-url (get-in runtime-benchmark [:sources :harness])
    :extra-urls [(get-in runtime-benchmark [:sources :manifest])
                 (get-in runtime-benchmark [:sources :method])]
    :projector "scripts/project-runtime-comparison.cljs"
    :report "bench/public-runtime-comparison/latest.json"
    :published "./benchmarks/runtime-native-latest.json"}
   {:id :build-scaling
    :question "Build scaling"
    :repo "kotoba-lang/buildbench"
    :harness-url (get-in build-scaling [:method :harness])
    :report "bench/public-build-scaling/latest.json"
    :published "./benchmarks/build-scaling-latest.json"}
   {:id :domains
    :question "Workload domains"
    :repo "kotoba-lang/kotoba-lang"
    :harness "scripts/benchmark-public-domains.mjs"
    :extra ["bench/public-domain-comparison/README.md"
            "bench/public-domain-comparison/probes"]
    :report "bench/public-domain-comparison/latest.json"
    :published "./benchmarks/domains-latest.json"}])

(def ^:private repo-blob-base "https://github.com/kotoba-lang/")

(defn- repo-path-url [repo path]
  (str repo-blob-base (subs repo (count "kotoba-lang/")) "/blob/main/" path))

(defn- check-local! [path]
  (when-not (fs/existsSync path)
    (throw (js/Error. (str "benchmark provenance points at a path that is not in "
                           "this tree: " path))))
  path)

(defn benchmark-provenance-rows
  "Rows for the provenance table, with every in-repo path verified to exist."
  []
  (for [{:keys [question repo harness harness-url extra extra-urls projector
                report published]} benchmark-provenance]
    (let [harness-link (if harness-url
                         (external-link harness-url
                                        (last (str/split harness-url #"/")))
                         (external-link (repo-path-url repo (check-local! harness))
                                        harness))
          more (concat
                (for [pth extra]
                  (external-link (repo-path-url repo (check-local! pth)) pth))
                (for [u extra-urls :when u]
                  (external-link u (last (str/split (first (str/split u #"#")) #"/"))))
                (when projector
                  [(external-link (repo-path-url "kotoba-lang/kotoba-lang"
                                                 (check-local! projector))
                                  projector)]))]
      [question
       (external-link (str "https://github.com/" repo) repo)
       (into [:span] (interpose " · " (cons harness-link more)))
       [:span
        [:a {:class "kot-link" :href published} "published JSON"]
        " · "
        (external-link (repo-path-url "kotoba-lang/kotoba-lang" (check-local! report))
                       report)]])))

(defn benchmark-section []
  (let [kotoba (get-in benchmark [:results :kotoba])
        rust (get-in benchmark [:results :rust])
        c (get-in benchmark [:results :c])
        jvm (get-in benchmark [:results :jvm])
        compile-speed (:speedQualification benchmark)
        compile-results [{:label "Kotoba" :target "WebAssembly" :result kotoba}
                         {:label "Rust / rustc" :target "WebAssembly" :result rust}
                         {:label "C / Clang" :target "WebAssembly" :result c}
                         {:label "JVM / javac" :target "JVM class" :result jvm}]
        runs (get-in benchmark [:method :runs])
        chip (get-in benchmark [:environment :chip])
        measured-date (subs (:generatedAt benchmark) 0 10)
        kotoba-version (str/upper-case (get-in benchmark [:environment :kotoba]))
        rust-version (str/join " " (take 2 (str/split (get-in benchmark [:environment :rustc]) #" ")))
        comparators (:comparators runtime-benchmark)
        domains (:domains runtime-benchmark)
        coverage (:semanticCoverage runtime-benchmark)
        speed (:speedQualification runtime-benchmark)
        runtime-score (:score runtime-benchmark)
        runtime-pairs (:pairs runtime-benchmark)
        pair-index (into {} (map (juxt (juxt :domain :comparator) identity)) runtime-pairs)
        runtime-date (subs (:generatedAt runtime-benchmark) 0 10)
        improvements (keep #(some-> (:improvement %) (* 100)) runtime-pairs)
        improvement-gain (apply max 1 (filter pos? improvements))
        improvement-loss (apply max 1 (map - (filter neg? improvements)))
        comparator-labels (str/join ", " (map :label comparators))
        end-speed (:speedQualification end-to-end-benchmark)
        end-results (map #(get-in end-to-end-benchmark [:results (keyword %)])
                         (get-in end-to-end-benchmark [:coverage :measuredToolchains]))
        domain-results (:tools domain-benchmark)
        domain-qualified (get-in domain-benchmark [:qualification :qualified])
        domain-workload-order [:string :collection :allocation :io :concurrency :realApp]
        stage-ms (fn [stage]
                   (if (= "measured" (:status stage))
                     (str (:medianMilliseconds stage) " ms")
                     "N/A"))]
    (dds/section
     {:id "benchmark" :title "Five benchmarks. Five different questions."}
     [:p {:class "kot-lead"}
      "Compiler startup asks how quickly one tiny source becomes an artifact. Build scaling asks what happens to that number when the source stops being tiny—and whether the artifact still answers. The developer loop separates resolution, checking, builds, and first result. Native runtime asks how fast already-built code runs. The workload-domain suite asks how strings, collections, allocation, I/O, concurrency, and a small real application behave. The results keep all five questions—and their evidence status—separate."]
     (dds/grid
      {:min "16rem"}
      (card (dds/chip-label "BUILD STARTUP · RANK UNQUALIFIED" {:color "gray"})
            (dds/heading 3 "4 toolchains, 21 runs each" {:size "24"})
            [:p (str "Kotoba " (:medianMilliseconds kotoba) " ms · Rust "
                     (:medianMilliseconds rust) " ms · C "
                     (:medianMilliseconds c) " ms · JVM "
                     (:medianMilliseconds jvm) " ms median.")]
            (caption (str runs " rotating process-cold samples · load1 "
                          (:observedLoad1First compile-speed) " → "
                          (:observedLoad1Last compile-speed) " · required ≤ "
                          (:quietLoad1Limit compile-speed) " · " measured-date
                          " · " chip)))
      (card (dds/chip-label "RUNTIME · COVERAGE COMPLETE")
            (dds/heading 3 (str (count domains) " workloads × "
                                (count comparators) " comparators") {:size "24"})
            [:p (str "Amu native is exercised against " comparator-labels
                     " through one common native call boundary.")]
            (caption (str (:completeComparatorDomainPairs coverage) "/"
                          (:requiredComparatorDomainPairs coverage)
                          " comparator/workload pairs · exact answers verified")))
      (card (dds/chip-label (str "RUNTIME SPEED · " (:qualifiedPairs runtime-score)
                                 "/" (:totalPairs runtime-score) " QUALIFIED")
                            (when-not (:fastestClaimQualified speed) {:color "gray"}))
            (dds/heading 3 (str (:qualifiedPairs runtime-score) " of "
                                (:totalPairs runtime-score) " pairs") {:size "24"})
            [:p (str "Amu native wins " (:qualifiedPairs runtime-score) " of the "
                     (:totalPairs runtime-score)
                     " comparator/workload pairs by at least 5%, separated from the arms' "
                     "own spread. The bounded fastest claim needs every pair, so it stays "
                     "unqualified — the count is the informative half.")]
            ;; "unqualified" reads as "not yet". For at least two pairs it is
            ;; "never": the three compilers emit the same instructions.
            (when-let [unwinnable (seq (:pairs (:provenUnwinnablePairs runtime-benchmark)))]
              [:p (str "At least " (count unwinnable) " of those pairs cannot be won at all. "
                       "On narrow-arithmetic, amu, Apple clang -O3 and rustc -O3 compile the "
                       "kernel to the same 61-instruction sequence — clang and rustc "
                       "byte-identical, amu differing only in register numbers. A 5% margin "
                       "over identical code does not exist, so the bounded claim is "
                       "unattainable rather than merely unmet.")])
            ;; The score is a measurement, not a constant. Four runs of one
            ;; commit scored 19, 19, 15, 19 (2026-09-06), because a single
            ;; outlier sample in one arm can trip perfgate's noise rule for
            ;; several pairs at once. Publishing the bare median would repeat
            ;; the overstatement this card used to make.
            (when-let [runs (:runs runtime-score)]
              (when (> runs 1)
                [:p (str "Median of " runs " host-qualified runs; the score ranged "
                         (first (:observedRange runtime-score)) "–"
                         (last (:observedRange runtime-score))
                         " and " (:stableQualifiedPairs runtime-score) " of the "
                         (:totalPairs runtime-score)
                         " pairs qualified in every one. A single noisy sample can "
                         "disqualify several pairs at once, so a one-run score is "
                         "not precise to one pair.")]))
            (caption (str "busy-CPU " (str/join " → " (map #(.toFixed (:busyCpuFraction %) 3)
                                                           (:quietGateSamples speed)))
                          " · required ≤ 0.10 · " runtime-date)))
      (card (dds/chip-label "DEVELOPER LOOP · RANK UNQUALIFIED" {:color "gray"})
            (dds/heading 3 (str (count end-results) " toolchain paths") {:size "24"})
            [:p "Dependency resolution, checking, clean and no-change builds, and process-cold first result are recorded separately."]
            (caption (str (get-in end-to-end-benchmark [:method :runs])
                          " samples per measured stage · load1 "
                          (:observedLoad1First end-speed) " → "
                          (:observedLoad1Last end-speed) " · required ≤ "
                          (:quietLoad1Limit end-speed))))
     (card (dds/chip-label "BUILD SCALING · CEILING FOUND" {:color "gray"})
            (dds/heading 3 (str (count (:scales build-scaling)) " source sizes") {:size "24"})
            [:p "The same program from one function to "
             (:k (last (:scales build-scaling)))
             ", built by every toolchain on the host and then executed. "
             "The released binary has the lowest cold-start cost here and a correctness "
             "ceiling above 128 functions."]
            (caption (str "artifacts checked after the clock stops · load1 "
                          (str/join " → " (map str (take 2 (get-in build-scaling [:absoluteTimes :observedLoad1]))))
                          " · required ≤ " (get-in build-scaling [:absoluteTimes :quietLoad1Limit])
                          " · " (subs (:generatedAt build-scaling) 0 10))))
     (card (dds/chip-label "WORKLOAD DOMAINS · RANK UNQUALIFIED" {:color "gray"})
            (dds/heading 3 "6 domains × 6 runtime paths" {:size "24"})
            [:p "Strings, collections, allocation, file I/O, four-worker concurrency, and a request-admission policy application kernel are correctness checked."]
            (caption (str (get-in domain-benchmark [:method :runs])
                          " samples in both process-cold and amortized lanes · load1 "
                          (get-in domain-benchmark [:machine :load1Before]) " → "
                          (get-in domain-benchmark [:machine :load1After])
                          (if domain-qualified " · qualified" " · rank withheld")))))
     (build-scaling-section)
     (dds/heading 3 "How long each native workload actually takes" {:size "24"})
     [:p
      "The grid below reports the margin between two arms. That is the number "
      "perfgate rules on, but a percentage on its own does not say whether a "
      "workload runs in five milliseconds or five hundred, and it hides the "
      "difference between a contested pair and an irrelevant one. These panels are "
      "the medians those margins are computed from. Amu native is the coloured lane "
      "in every panel — including the panels where it is not first. Each panel is "
      "scaled to its own slowest arm, because the question a panel answers is who is "
      "faster in that workload."]
     (dds/grid
      {:min "19rem"}
      (for [d domains
            :let [ps (filter #(= (:id d) (:domain %)) runtime-pairs)
                  cand (:candidateMedian (first ps))
                  rows (sort-by :value
                                (cons {:label (:candidate runtime-benchmark)
                                       :value cand :lead? true}
                                      (for [pr ps
                                            :let [c (first (filter #(= (:comparator pr) (:id %))
                                                                   comparators))]]
                                        {:label (:label c)
                                         :value (:baselineMedian pr)})))
                  best (:value (first rows))]]
        (card
         (dds/heading 4 (:label d) {:size "20"})
         (chart/ranked-bars
          {:rows (for [{:keys [label value lead?]} rows]
                   {:label label :value value
                    :display (str (.toFixed value 2) " ms")
                    :note (when lead?
                            (if (= value best)
                              "fastest here"
                              (str (.toFixed (/ value best) 2) "× the fastest here")))
                    :lead? lead?})}))))
     (caption
      (str "Median milliseconds over " (:runs runtime-score)
           " host-qualified runs; shorter is faster. Every arm returned the same "
           "independently checked answer, and the candidate median is one value per "
           "workload — the suite rotates each engine pair in ABBA/BAAB order, so the "
           "same Amu artifact is timed once per workload and then compared against "
           "each arm in turn. Unlike the other four benchmarks on this page, this "
           "one's quiet-host gate PASSED (" (name (:verdict speed))
           "), so these are figures for this host rather than observations only. The "
           "bounded fastest claim still needs all " (:totalPairs runtime-score)
           " pairs, which is what the grid below is for."))
     (dds/heading 3 "Every runtime pair, win or loss" {:size "24"})
     [:p
      "The bounded claim is all-or-nothing, so a single unqualified pair makes it false. "
      "Publishing only that verdict would hide which pairs are contested, so the whole "
      "grid is here. A cell is the mean improvement of Amu native over that comparator "
      "on that workload; positive means Amu is faster, and a check marks the pairs that "
      "clear perfgate — at least 5% and separated from the arms' own spread."]
     [:div {:class "kot-table-scroll kot-chart"}
      (dds/table
       {:caption (str "Amu native vs each comparator, " runtime-date
                      ", host-qualified; ✓ = passes perfgate")
        :headers (into ["Workload"] (map :label comparators))
        :row-header? true
        :rows (for [d domains]
                (into [(:label d)]
                      (for [c comparators
                            :let [pair (pair-index [(:id d) (:id c)])
                                  pct (some-> (:improvement pair) (* 100))]]
                        (if (nil? pair)
                          "—"
                          (chart/diverging-cell
                           {:value pct
                            :display (str (when (pos? pct) "+") (.toFixed pct 1) "%")
                            :qualified? (:qualified pair)
                            :neg-max improvement-loss :pos-max improvement-gain
                            :title (if (:qualified pair)
                                     (str "Passes perfgate; qualified in "
                                          (:qualifiedRuns pair) " of "
                                          (:runs pair) " runs")
                                     (str "Not qualified: "
                                          (str/join ", " (:reasons pair))))})))))})]
     (caption
      (str "Each cell is a bar grown from a centre line: right of it Amu native is "
           "faster, left of it slower. The two directions are scaled separately — "
           "the wins run to +" (.toFixed improvement-gain 0) "% and the losses only to −"
           (.toFixed improvement-loss 0)
           "%, so one shared scale would flatten every contested pair into the same "
           "invisible sliver. Sign is also carried by the side of the line and by the "
           "signed number, so no reading of this grid depends on telling two colours apart."))
     (caption (str (:qualifiedPairs runtime-score) " of " (:totalPairs runtime-score)
                   " pairs qualified"
                   (if-let [runs (:runs runtime-score)]
                     (if (> runs 1)
                       (str " (median of " runs "; " (:stableQualifiedPairs runtime-score)
                            " in every run)")
                       " (one run)")
                     "")
                   " · candidate "
                   (subs (:compilerCommit runtime-benchmark) 0 12)
                   " · " (:host runtime-benchmark)))
     (dds/heading 3 "Optimization delivery after the published run" {:size "24"})
     [:p
      "The dated benchmark above remains immutable. New implementation slices are listed separately until the same-artifact suite reruns and passes its qualification gates."]
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "Implemented surfaces that are not yet new speed claims"
        :headers ["Surface" "Delivered" "Evidence boundary"]
        :row-header? true
        :rows [["Native vectors / allocation"
                "Bounded non-escaping vector literals are escape-proved and scalar-replaced on x86-64 and AArch64."
                "211 backend tests / 2,442 assertions; escaping vectors retain the checked host ABI. No new ranked timing yet."]
               ["String SIMD"
                "POSIX checked equality uses explicit 16-byte NEON or SSE2 comparison after handle and canonical UTF-8 validation."
                "Optimized assembly and both native ISA semantic vectors verified. Windows remains separately pinned; latency rank pending."]
               ["Async I/O capability"
                "Root-confined eventual read/write/list/exists/delete use CompletableFuture on JVM and fs.promises on Node."
                "JVM and Node real-filesystem tests pass. The public standalone Wasm benchmark still has no admitted host binding, so its I/O cell remains N/A."]
               ["Structured concurrency"
                "A bounded 32-child fail-fast scope joins, cancels siblings, and prevents child lifetime escape as canonical Kotoba state."
                "996 parity assertions across .kotoba authority and CLJC load path. This is structured lifetime semantics, not an OS-thread throughput result."]
               ["Kotoba CLI"
                "kotoba test/build consume the new compiler pin; kotoba compile emits sealed x86-64 and AArch64 KEXE directly."
                "Public CLI lifecycle and AArch64 vector artifact verified. Native --run stays refused until a measured loader receipt is wired."]]})]
     (dds/heading 3 "Compiler startup, four toolchains" {:size "24"})
     (chart/ranked-bars
      {:axis (str "Process-cold wall time for one tiny source, in milliseconds; "
                  "shorter is faster. " runs " rotating samples per toolchain on "
                  chip ". The host-load gate FAILED on this run, so these are "
                  "observations of one machine, not a ranking.")
       :rows (for [{:keys [label target result]} compile-results]
               {:label label :sub target
                :value (:medianMilliseconds result)
                :display (str (:medianMilliseconds result) " ms")
                :note (if (= 1 (:medianRatioToKotoba result))
                        "the baseline"
                        (str (:medianRatioToKotoba result) "× Kotoba"))
                :lead? (= "Kotoba" label)})})
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "Tiny source-to-artifact process-cold build measurement"
        :headers ["Toolchain" "Output" "Median" "p95" "Relative elapsed time"]
        :row-header? true
        :rows (for [{:keys [label target result]} compile-results]
                [label
                 target
                 (str (:medianMilliseconds result) " ms")
                 (str (:p95Milliseconds result) " ms")
                 (str (:medianRatioToKotoba result) "× Kotoba")])})]
     (caption
      (str kotoba-version " · " (str/upper-case rust-version) " · "
           (get-in benchmark [:environment :clang]) " · "
           (get-in benchmark [:environment :javac])
           ". Kotoba, Rust, and C emit Wasm; javac emits a class file. "
           "Different targets and compiler work make this a startup observation, not a universal ranking. "
           "The recorded host-load gate failed, so the table is not a qualified speed rank."))
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "Dependency-free tiny-project developer-loop medians; N/A means no separate phase was measured"
        :headers ["Toolchain / target" "Resolve" "Check" "Clean build" "No-change build" "Start + execute" "Clean build + first result"]
        :row-header? true
        :rows (for [result end-results]
                [(str (:label result) " · " (:target result))
                 (stage-ms (get-in result [:stages :dependencyResolution]))
                 (stage-ms (get-in result [:stages :checkOrAdmission]))
                 (stage-ms (get-in result [:stages :cleanBuild]))
                 (stage-ms (get-in result [:stages :noChangeBuild]))
                 (stage-ms (get-in result [:stages :processColdStartupAndExecution]))
                 (stage-ms (get-in result [:loops :cleanBuildAndFirstRun]))])})]
     (caption
      "Every emitted artifact produced 42 in a fresh process. Targets and runtime contracts differ; N/A is never zero. The host-load gate failed, so these are reproducible observations rather than a cross-language speed ranking.")
     (dds/heading 3 "Six domains, side by side" {:size "24"})
     [:p
      "Each panel is scaled to its own slowest lane, because the question a panel "
      "answers is who is faster in that domain, not how the domains compare to each "
      "other. Kotoba's lane is the coloured one in every panel — including the "
      "panels where it is last. Its standalone Wasm artifact runs through a Node "
      "host and pays that startup on every process-cold sample, while Rust, C and Go "
      "run as native binaries; where the target has no ambient filesystem or thread "
      "contract at all, the lane is absent rather than zero."]
     (dds/grid
      {:min "19rem"}
      (for [[wid {:keys [label]}] (map (juxt identity #(get-in domain-benchmark [:workloads %]))
                                       domain-workload-order)
            :let [measured (->> domain-results
                                (keep (fn [t] (let [r (get-in t [:results wid])]
                                                (when (= "measured" (:status r))
                                                  {:label (:label t)
                                                   :value (:medianMilliseconds r)}))))
                                (sort-by :value))
                  best (:value (first measured))
                  absent (->> domain-results
                              (keep (fn [t] (when-not (= "measured" (get-in t [:results wid :status]))
                                              {:label (:label t)
                                               :absent "N/A — not in this target's contract"}))))]]
        (card
         (dds/heading 4 label {:size "20"})
         (chart/ranked-bars
          {:rows (concat
                  (for [{:keys [label value]} measured]
                    (let [kotoba? (str/starts-with? label "Kotoba")]
                      {:label label :value value
                       :display (str value " ms")
                       :note (when kotoba?
                               (if (= value best)
                                 "fastest here"
                                 (str (.toFixed (/ value best) 1) "× the fastest here")))
                       :lead? kotoba?}))
                  absent)}))))
     (caption
      "Process-cold medians in milliseconds; shorter is faster. The host-load gate "
      "failed on this run, so these panels are observations rather than a ranking, "
      "and the amortized lane below tells a different story again.")
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "Process-cold workload-domain medians; N/A means the target contract does not provide that capability"
        :headers ["Runtime path" "String" "Collection" "Allocation" "File I/O" "Concurrency" "Real app"]
        :row-header? true
        :rows (for [result domain-results]
                [(:label result)
                 (stage-ms (get-in result [:results :string]))
                 (stage-ms (get-in result [:results :collection]))
                 (stage-ms (get-in result [:results :allocation]))
                 (stage-ms (get-in result [:results :io]))
                 (stage-ms (get-in result [:results :concurrency]))
                 (stage-ms (get-in result [:results :realApp]))])})]
     (caption
      "Every sample returned the exact reference checksum. Kotoba uses its emitted Wasm and declared typed ABI; its standalone target has no ambient filesystem or thread contract, so those cells are reasoned N/A. The recorded host-load gate failed, so medians are observations, not a ranking.")
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "Amortized in-process batch medians per base workload; N/A keeps the same capability boundary"
        :headers ["Runtime path" "String" "Collection" "Allocation" "File I/O" "Concurrency" "Real app"]
        :row-header? true
        :rows (for [result domain-results]
                [(:label result)
                 (stage-ms (get-in result [:amortizedResults :string]))
                 (stage-ms (get-in result [:amortizedResults :collection]))
                 (stage-ms (get-in result [:amortizedResults :allocation]))
                 (stage-ms (get-in result [:amortizedResults :io]))
                 (stage-ms (get-in result [:amortizedResults :concurrency]))
                 (stage-ms (get-in result [:amortizedResults :realApp]))])})]
     (caption
      "Each larger in-process batch is divided by its declared workload multiplier. This amortizes startup but does not fully remove process, VM, or Wasm instantiation cost, so it is not labeled a perfectly warmed steady-state result. Kotoba's pure inc/dec map chains are fused into reduce without intermediate vectors; callbacks outside that proven subset keep eager materialization.")
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "What each public benchmark does—and does not—establish"
        :headers ["Question" "Compared implementations" "Current conclusion"]
        :row-header? true
        :rows [["Tiny Wasm compile + execute"
                "Kotoba, Rust, C, and JVM toolchains"
                (str "Four process-cold medians published above; only Kotoba/Rust/C share the Wasm target, and no general build-speed rank is claimed")]
               ["Tiny-project developer loop"
                "Kotoba, Rust, C, Zig, TinyGo, Go, Swift, JVM, AssemblyScript, .NET IL, and .NET Native AOT"
                "Seven samples per available stage are published; target differences and a failed host-load gate prohibit a universal ranking"]
               ["Native steady-state execution"
                (str "Amu native vs " comparator-labels)
                "All 30 semantic comparison cells are complete; speed ranking withheld because the quiet-host gate failed"]
               ["Strings, collections, allocation, I/O, concurrency, and real app"
                "Kotoba, Rust, C, Go, JVM, and JavaScript runtime paths"
                "Exact checksums and process-cold plus amortized samples are published; standalone Kotoba I/O and threads are N/A, while its pure request-admission application is measured; the failed load gate withholds ranking"]]})]
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
     (dds/heading 3 "Where each benchmark lives" {:size "24"})
     [:p
      "Every number above comes from a public harness and a committed report, so a "
      "run can be repeated and a claim can be disagreed with. The in-repo paths in "
      "this table are checked against the working tree when this page is generated: "
      "a harness that moves fails the build rather than shipping a dead link."]
     [:div {:class "kot-table-scroll"}
      (dds/table
       {:caption "Harness, method and report for each published benchmark"
        :headers ["Benchmark" "Repository" "Harness and method" "Report"]
        :row-header? true
        :rows (benchmark-provenance-rows)})]
     (caption
      (list "The gate every ordering on this page is put through is "
            (external-link "https://github.com/kotoba-lang/perfgate" "kotoba-lang/perfgate")
            ", run at its own unrelaxed default policy. A threshold loosened to let a "
            "run through would be a benchmark measuring its own thresholds."))
     [:p
      [:strong "Bottom line: "]
      ;; This sentence used to say that NO current run qualified a speed
      ;; ranking because its quiet-host gate failed. That was true of four of
      ;; the five reports and false of the two that matter: the runtime suite
      ;; records `qualified-host-load`, and the build-scaling orderings clear
      ;; perfgate at K=1. Reading the verdicts out of the reports keeps the
      ;; sentence from going stale again.
      (str "The artifacts, exact results and samples are real in all five benchmarks. "
           "Three of them — compiler startup, the developer loop and the workload "
           "domains — failed their quiet-host gate, so they rank nothing and are "
           "published as observations. The native runtime suite passed its gate and "
           "wins " (:qualifiedPairs runtime-score) " of its " (:totalPairs runtime-score)
           " pairs, short of the every-pair claim it would need. Build scaling "
           "qualifies its cold-start ordering against every comparator on the host "
           "and finds a correctness ceiling in the same run. No universal speed rank "
           "is claimed anywhere on this page, and none of these runs licenses one.")]
     [:div {:class "kot-actions"}
      (dds/button "Inspect compile samples"
                  {:href "./benchmarks/compile-wasm-latest.json"})
      (dds/button "Inspect runtime evidence"
                  {:href "./benchmarks/runtime-native-latest.json"
                   :type :outline})
      (dds/button "Inspect developer-loop samples"
                  {:href "./benchmarks/end-to-end-latest.json"
                   :type :outline})
      (dds/button "Inspect workload-domain samples"
                  {:href "./benchmarks/domains-latest.json"
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

(defn footer
  ([] (footer :en "legal/"))
  ([locale] (footer locale (if (= locale :ja) "../legal/" "legal/")))
  ([locale legal-href]
   (let [ja? (= locale :ja)
         operator (if ja?
                    "公開運営者: Kotoba Labs Inc."
                    "Public operator: Kotoba Labs Inc.")
         contact-label (if ja? "公開連絡先: " "Public contact: ")
         sales (if ja?
                 "営業連絡先: Ryo Awai."
                 "Sales contact: Ryo Awai.")
         legal-label (if ja? "運営情報" "Operator")]
     [:footer {:class "kot-footer"}
      (dds/container
       [:p [:strong "Kotoba"] " — AI writes freely. Kotoba draws the boundary."]
       [:p {:class "kot-caption kot-muted"}
        operator " " contact-label (mail-link) ". " sales]
       [:p {:class "kot-caption kot-muted"}
        "Generated by " (code "site/generate.cljs") " from "
        (str/join ", " authority-files) ". No telemetry. No third-party runtime dependency."]
       [:p
        [:a {:class "kot-link" :href legal-href} legal-label]
        " · "
        (external-link "https://github.com/kotoba-lang/kotoba-lang" "Source and license")])])))

(defn view []
  [:div
   [:a {:class "kot-skip" :href "#main"} "Skip to content"]
   (header)
   [:main {:id "main"}
    (hero)
    (dds/container
     (why-section)
     (what-section)
     (defaults-section)
     (developer-section)
     (code-play-section)
     (libraries-section)
     (roadmap-section)
     (community-section)
     (sponsor-section)
     (blog-cloud-section)
     (proof-section)
     (architecture-section)
     (typed-eval-section)
     (start-section)
     (benchmark-section)
     (claims-section)
     (deliberate-section)
     (release-section)
     (search-section)
     (source-section))]
   (footer)
   [:script search-js]
   [:script play-js]
   [:script hero-js]
   [:script chart-anim-js]])

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
      [:p {:class "kot-eyebrow"} "31 August 2026 · Benchmarks"]
      (dds/heading 2 "A fifth benchmark, and the claim it would not support" {:size "32"})
      [:p "The four benchmarks all built a program small enough to fit on one screen, which measures how quickly a toolchain starts rather than the number a developer waits on. The new build-scaling suite generates the same program at eight sizes, builds it through every toolchain on the host, and then executes what each one produced."]
      [:p "Executing the artifact is what makes it a benchmark rather than a stopwatch. The released Kotoba CLI emits an invalid WebAssembly module above 128 functions and exits successfully, so an unvalidated harness would have recorded its best numbers exactly where it had stopped working. Two further Kotoba lanes stop at declared bounds — a call-fuel budget and a function-count admission limit — which are the opposite result and had to be reported as such."]
      [:p "The suite was added to show build speed as a strength. It shows the released binary with the lowest cold-start build cost of any lane measured, and it shows that at large sizes build speed is not currently a Kotoba strength. Both halves are in the same table."]
      [:p [:a {:class "kot-link" :href "../#benchmark"} "Read the build-scaling results"]]]
     [:article {:class "kot-blog-entry"}
      [:p {:class "kot-eyebrow"} "29 August 2026 · Benchmarks"]
      (dds/heading 2 "Four benchmarks answer four different questions" {:size "32"})
      [:p "Compiler startup measures one tiny source-to-artifact path. The developer-loop suite separates resolution, checking, clean and no-change builds, and first result across eleven toolchain paths. Native runtime measures already-built programs. A fourth suite compares strings, collections, allocation, I/O, concurrency, and a small real application across six representative runtime paths. Kotoba publishes them separately so one fast phase cannot be mistaken for universal speed."]
      [:p "Every emitted result is checked, but all speed rankings remain withheld because the recorded host-load gates failed."]
      [:p [:a {:class "kot-link" :href "../#benchmark"} "Read the benchmark and inspect its evidence"]]]
     [:article {:class "kot-blog-entry"}
      [:p {:class "kot-eyebrow"} "28 August 2026 · Language design"]
      (dds/heading 2 "No ambient authority is a language boundary" {:size "32"})
      [:p "Kotoba programs do not begin with implicit filesystem, network, process, clock, model, or secret access. Source declares effects, admission intersects grants and policy, and the host binds only the resulting capabilities."]
      [:p "That design complements operating-system isolation; it does not replace the compiler, verifier, runtime, provider, key custody, or host policy in the trusted computing base."]
      [:p [:a {:class "kot-link" :href "../#architecture"} "See the computation boundary"]]])]
   (footer :en "../legal/")])

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
         (dds/button "日本語" {:href "../ja/libraries/" :type :outline :size "lg"})
         (dds/button "Machine-readable contract"
                     {:href "../.well-known/kotoba-libraries.json"
                      :type :outline :size "lg"})
         (dds/button "Package registry (EDN)"
                     {:href "../.well-known/kotoba-package-registry.edn"
                      :type :outline :size "lg"})]]

       (dds/section
        {:id "identity" :title "One library, several identities"}
        (dds/grid
         {:min "17rem"}
         (card (dds/chip-label "DEFINITION CID")
               (dds/heading 3 "Meaning or checked KIR" {:size "20"})
               [:p "A name, full CID, and unambiguous #hash abbreviation resolve to the same definition."])
         (card (dds/chip-label "RELEASE CID")
               (dds/heading 3 "Executable release root" {:size "20"})
               [:p "One immutable IPLD root binds the namespace head, exact definitions, raw Wasm artifacts, compile receipts, and reproducibility evidence."])
         (card (dds/chip-label "SOURCE · BUILD · ARTIFACT")
               (dds/heading 3 "Keep provenance layers separate" {:size "20"})
               [:p "Source bytes, declared build inputs, and emitted bytes have different identities. None grants execution authority."])
         (card (dds/chip-label "GITHUB")
               (dds/heading 3 "Provenance, not identity" {:size "20"})
               [:p "Repository and commit links help review origin. They cannot replace CIDs or authorize a namespace update."])))

       (dds/section
        {:id "publish" :title "Inspect, sign, publish, discover"}
        [:pre {:class "kot-pre"}
         [:code (str "# install the live dual-signed release; execution is then local and CID-locked\n"
                     "kotoba package add kotoba-lang/reference-math@0.1.0 --catalog-cid " package-registry-cid "\n"
                     "kotoba package run kotoba-lang/reference-math  # 42")]]
        [:p {:class "kot-lead"}
         "Post-quantum cryptography is the Kotoba admission floor, not an optional mode. This reference package is verified with Ed25519 and FIPS 204 ML-DSA-65 before installation and again before safe execution. External Passkeys and transport remain separately qualified boundaries."]
        [:pre {:class "kot-pre"}
         [:code "kotoba library inspect quadruple \\\n  --store .kotoba/codebase --namespace demo \\\n  --github https://github.com/kotoba-lang/demo\n\n# dry-run is the default\nkotoba library publish \\\n  --store .kotoba/codebase --namespace demo --hosted\n\n# replicate the exact release closure to two storage origins\nkotoba library publish \\\n  --store .kotoba/codebase --namespace demo --hosted --dry-run false \\\n  --pqc-seed-file <ml-dsa-seed> \\\n  --provider east=https://east.example --provider-token-file <east-token> \\\n  --provider west=https://west.example --provider-token-file <west-token>\n\n# verify every byte and two routed peer IDs, then run by release CID\nkotoba library verify ipfs://<release-cid> --store .kotoba/codebase \\\n  --provider east=https://east.example --provider west=https://west.example\nkotoba library run ipfs://<release-cid> --entry answer \\\n  --store .kotoba/codebase \\\n  --provider east=https://east.example --provider west=https://west.example"]]
        (dds/grid
         {:min "16rem"}
         (card (dds/chip-label "1 · INSPECT")
               (dds/heading 3 "Resolve the exact graph" {:size "20"})
               [:p "Resolve the namespace head, definition CIDs, dependency CIDs, identity layer, and optional GitHub provenance before creating a release."])
         (card (dds/chip-label "2 · AUTHORIZE")
               (dds/heading 3 "Sign the namespace head" {:size "20"})
               [:p "The local operator key signs publication. Passkey confirms the Stable Principal, while ML-DSA-65 signs every publication field and is pinned to that Principal; none of the three gates replaces another."])
         (card (dds/chip-label "3 · REPLICATE + VERIFY")
               (dds/heading 3 "Two stores, two peers, every byte" {:size "20"})
               [:p "The CLI re-fetches the full DAG and raw artifacts from each storage origin, then counts distinct libp2p peer IDs observed through delegated routing."])
         (card (dds/chip-label "4 · NAME + RUN")
               (dds/heading 3 "Approve a name; execute a hash" {:size "20"})
               [:p "Passkey plus the pinned ML-DSA-65 key controls the mutable IPNS relay. This application-layer co-approval does not make the authenticator's Passkey post-quantum. Wasm execution addresses the immutable release CID and export."])))

       (catalog-section)

       (dds/section
        {:id "status" :title "Current boundary"}
        (dds/grid
         {:min "18rem"}
         (card (dds/chip-label "LIVE")
               (dds/heading 3 "Executable release closure" {:size "20"})
               [:p "The CLI builds and transfers one CID closure containing definitions, raw Wasm, compile receipts, compiler contract, policy, and package-lock evidence."]
               [:p (code (:registry/release-cid reference-package))])
         (card (dds/chip-label "LIVE")
               (dds/heading 3 "Passkey-hosted publish" {:size "20"})
               [:p "The CLI replicates the immutable closure and returns a fragment-only approval URL containing an ML-DSA-65 signature. kotoba.cloud requires the Passkey session and atomically pins the first valid ML-DSA key to the Stable Principal without receiving either signing seed or storage tokens."])
         (card (dds/chip-label "STATUS")
               (dds/heading 3 (str/replace (name status) #"-" " ") {:size "20"})
               [:p "A release remains pending until two byte-complete storage origins and two distinct routed peer IDs produce an availability-proof CID. One gateway never qualifies."])
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
     (footer :en "../legal/")
     [:script catalog-js]]))

(defn libraries-ja-view []
  (let [surfaces (:kotoba.library-publication/surfaces library-publication)
        status (:kotoba.library-publication/status library-publication)]
    [:div
     [:a {:class "kot-skip" :href "#main"} "本文へ移動"]
     (header "../../")
     [:main {:id "main"}
      (dds/container
       [:section {:id "top" :class "kot-hero"}
        [:p {:class "kot-eyebrow"} "CONTENT-ADDRESSED LIBRARIES"]
        (dds/heading 1 "名前はコードを見つける。Hash は、それが何かを示す。" {:size "48"})
        [:p {:class "kot-lead"}
         "Kotoba CLI は、compile するものと同じ CID graph を inspect・publish します。GitHub は provenance、namespace は discovery、不変 CID は definition・release・build・artifact の identity です。"]
        [:div {:class "kot-actions"}
         (dds/button "Kotoba CLI で確認" {:href "#publish" :size "lg"})
         (dds/button "English" {:href "../../libraries/" :type :outline :size "lg"})
         (dds/button "機械可読 contract"
                     {:href "../../.well-known/kotoba-libraries.json"
                      :type :outline :size "lg"})
         (dds/button "Package registry (EDN)"
                     {:href "../../.well-known/kotoba-package-registry.edn"
                      :type :outline :size "lg"})]]

       (dds/section
        {:id "identity" :title "1 つの library、複数の identity"}
        (dds/grid
         {:min "17rem"}
         (card (dds/chip-label "DEFINITION CID")
               (dds/heading 3 "意味、または checked KIR" {:size "20"})
               [:p "名前・完全 CID・曖昧でない #hash 短縮形は、同じ definition を解決します。"])
         (card (dds/chip-label "RELEASE CID")
               (dds/heading 3 "実行可能な release root" {:size "20"})
               [:p "一つの不変 IPLD root が namespace head、exact definition、raw Wasm、compile receipt、再現性 evidence を結びます。"])
         (card (dds/chip-label "SOURCE · BUILD · ARTIFACT")
               (dds/heading 3 "provenance の層を混ぜない" {:size "20"})
               [:p "source bytes、宣言された build inputs、生成物 bytes は別の identity です。いずれも execution authority ではありません。"])
         (card (dds/chip-label "GITHUB")
               (dds/heading 3 "provenance であって identity ではない" {:size "20"})
               [:p "repository と commit は出所の review に使いますが、CID を置き換えず、namespace 更新権限も与えません。"])))

       (dds/section
        {:id "publish" :title "inspect、署名、publish、discover"}
        [:pre {:class "kot-pre"}
         [:code (str "# live の二重署名 release を導入。以後は local の CID lock を使う\n"
                     "kotoba package add kotoba-lang/reference-math@0.1.0 --catalog-cid " package-registry-cid "\n"
                     "kotoba package run kotoba-lang/reference-math  # 42")]]
        [:p {:class "kot-lead"}
         "耐量子暗号は Kotoba の admission floor であり、任意の mode ではありません。この reference package は install 前と safe execution 前に Ed25519 と FIPS 204 ML-DSA-65 の両方を検証します。外部 Passkey と transport は別に検証する境界です。"]
        [:pre {:class "kot-pre"}
         [:code "kotoba library inspect quadruple \\\n  --store .kotoba/codebase --namespace demo \\\n  --github https://github.com/kotoba-lang/demo\n\n# 既定は dry-run\nkotoba library publish \\\n  --store .kotoba/codebase --namespace demo --hosted\n\n# exact release closure を 2 storage origin へ複製\nkotoba library publish \\\n  --store .kotoba/codebase --namespace demo --hosted --dry-run false \\\n  --pqc-seed-file <ml-dsa-seed> \\\n  --provider east=https://east.example --provider-token-file <east-token> \\\n  --provider west=https://west.example --provider-token-file <west-token>\n\n# 全 byte と 2 routed peer ID を検証し、release CID から実行\nkotoba library verify ipfs://<release-cid> --store .kotoba/codebase \\\n  --provider east=https://east.example --provider west=https://west.example\nkotoba library run ipfs://<release-cid> --entry answer \\\n  --store .kotoba/codebase \\\n  --provider east=https://east.example --provider west=https://west.example"]]
        (dds/grid
         {:min "16rem"}
         (card (dds/chip-label "1 · INSPECT")
               (dds/heading 3 "exact graph を解決" {:size "20"})
               [:p "release 作成前に namespace head、definition CID、dependency CID、identity layer、任意の GitHub provenance を確認します。"])
         (card (dds/chip-label "2 · AUTHORIZE")
               (dds/heading 3 "namespace head に署名" {:size "20"})
               [:p "local operator key が署名し、Passkey が Stable Principal を確認し、ML-DSA-65 が公開対象の全 field に署名してその Principal へ固定されます。3つの gate は互いを置き換えません。"])
         (card (dds/chip-label "3 · REPLICATE + VERIFY")
               (dds/heading 3 "2 storage、2 peer、全 byte" {:size "20"})
               [:p "CLI は各 storage origin から DAG と raw artifact を再取得して検証し、delegated routing が返す distinct libp2p peer ID を数えます。"])
         (card (dds/chip-label "4 · NAME + RUN")
               (dds/heading 3 "名前を承認し、hash を実行" {:size "20"})
               [:p "Passkey と固定済み ML-DSA-65 key が mutable IPNS relay を制御します。この application-layer co-approval は authenticator の Passkey 自体を耐量子化しません。Wasm 実行は immutable release CID と export を指定します。"])))

       (dds/section
        {:id "status" :title "現在の境界"}
        (dds/grid
         {:min "18rem"}
         (card (dds/chip-label "LIVE")
               (dds/heading 3 "実行可能 release closure" {:size "20"})
               [:p "CLI は definition、raw Wasm、compile receipt、compiler contract、policy、package-lock evidence を一つの CID closure として構築・転送します。"]
               [:p (code (:registry/release-cid reference-package))])
         (card (dds/chip-label "LIVE")
               (dds/heading 3 "Passkey-hosted publish" {:size "20"})
               [:p "CLI は immutable closure を複製し、ML-DSA-65 署名を含む fragment-only の承認 URL を返します。kotoba.cloud は Passkey session を要求し、最初の有効な ML-DSA key を Stable Principal へ原子的に固定します。どちらの signing seed も storage token も受け取りません。"])
         (card (dds/chip-label "STATUS")
               (dds/heading 3 (str/replace (name status) #"-" " ") {:size "20"})
               [:p "2 つの byte-complete storage origin と 2 distinct routed peer ID が availability-proof CID を作るまで pending です。1 gateway だけでは分散認定しません。"])
         (card (dds/chip-label "SOURCE")
               (dds/heading 3 "実装を review" {:size "20"})
               [:p (external-link "https://github.com/kotoba-lang/kotoba" "Kotoba CLI")]
               [:p (external-link "https://github.com/kotoba-lang/kotoba-lang" "Language / catalog authority")]
               [:p (external-link "https://github.com/kotoba-lang/codebase" "Content-addressed codebase")]))
        (caption "Hosted Passkey publication: "
                 (if (get-in surfaces [:hosted-passkey-publish :implemented])
                   "implemented" "not implemented")
                 "。content identity は execution authority ではありません。"))

       (dds/section
        {:id "compare" :title "境界を付けて library を比較する"}
        [:p {:class "kot-lead"}
         "比較には exact library CID、workload、target、host、toolchain、sample count、測定時刻、verified result、receipt、残る制約を含めます。"]
        (bullets ["mutable な latest alias を、不変 release として比較しない。"
                  "API coverage、target compatibility、compile performance、runtime performance、operational qualification を分ける。"
                  "isolated kernel の速度を production 全体の性能 claim にしない。"
                  "unsupported / unmeasured は明示し、0 点として隠さない。"])))]
     (footer :ja "../legal/")]))

(defn legal-view []
  [:div
   [:a {:class "kot-skip" :href "#main"} "Skip to content"]
   (header "../")
   [:main {:id "main"}
    (dds/container
     [:section {:id "top" :class "kot-hero"}
      [:p {:class "kot-eyebrow"} "Public operator"]
      (dds/heading 1 "Kotoba Labs Inc." {:size "48"})
      [:p {:class "kot-lead"}
       "kotoba-lang.org is operated by Kotoba Labs Inc. This page names the "
       "public operator and the public contact mailbox. It is not a Specified "
       "Commercial Transactions Act table."]]
     [:section {:id "contact"}
      (dds/heading 2 "Public contact" {:size "32"})
      [:p "Public contact: " (mail-link) "."]
      [:p "Sales contact: Ryo Awai."]
      [:p {:class "kot-caption kot-muted"}
       "Security reports use GitHub private vulnerability reporting. "
       "This page does not publish a street address, telephone number, "
       "or company registration number."]])]
   (footer :en "./")])

(defn legal-ja-view []
  [:div
   [:a {:class "kot-skip" :href "#main"} "本文へ移動"]
   (header "../../")
   [:main {:id "main"}
    (dds/container
     [:section {:id "top" :class "kot-hero"}
      [:p {:class "kot-eyebrow"} "公開運営者"]
      (dds/heading 1 "Kotoba Labs Inc." {:size "48"})
      [:p {:class "kot-lead"}
       "kotoba-lang.org の公開運営者は Kotoba Labs Inc. です。このページは公開運営者と公開連絡先のみを示します。特定商取引法に基づく表記表ではありません。"]]
     [:section {:id "contact"}
      (dds/heading 2 "公開連絡先" {:size "32"})
      [:p "公開連絡先: " (mail-link) "。"]
      [:p "営業連絡先: Ryo Awai。"]
      [:p {:class "kot-caption kot-muted"}
       "脆弱性報告は GitHub の private vulnerability reporting を使います。所在地、電話番号、登記番号は掲載していません。"]])]
   (footer :ja "./")])

(defn sponsor-view [locale]
  (let [ja? (= locale :ja)
        root (if ja? "../../" "../")
        uses (:use-of-funds sponsorship)
        non-exchange (:non-exchange sponsorship)]
    [:div
     [:a {:class "kot-skip" :href "#main"} (if ja? "本文へ移動" "Skip to content")]
     (header root)
     [:main {:id "main"}
      (dds/container
       [:section {:id "top" :class "kot-hero"}
        [:p {:class "kot-eyebrow"}
         (if sponsorship-live? "GITHUB SPONSORS" "GITHUB SPONSORS · PREPARING")]
        (dds/heading 1
                     (if ja? "Kotoba の公開基盤を支える" "Sustain Kotoba's public foundation")
                     {:size "48"})
        [:p {:class "kot-lead"}
         (if ja?
           (if sponsorship-live?
             "GitHub Sponsors で、Kotoba の言語仕様、ツール、検証、公開 evidence を継続的に支援できます。"
             "Kotoba organization の GitHub Sponsors profile は準備中です。GitHub の承認前に、このサイトが支払いを受け付けることはありません。")
           (if sponsorship-live?
             "Use GitHub Sponsors to sustain Kotoba's language contract, tooling, qualification, and public evidence."
             "The Kotoba organization profile is being prepared. This site will not present a payment action before GitHub approves it."))]
        [:div {:class "kot-actions"}
         (if sponsorship-live?
           (dds/button (if ja? "GitHub Sponsors で支援" "Sponsor on GitHub")
                       {:href sponsor-profile-url :size "lg"})
           (dds/button (if ja? "GitHub で準備状況を見る" "Follow the launch on GitHub")
                       {:href "https://github.com/kotoba-lang/kotoba-lang" :size "lg"}))
         (dds/button (if ja? "English" "日本語")
                     {:href (if ja? "../../sponsor/" "../ja/sponsor/")
                      :type :outline :size "lg"})
         (dds/button (if ja? "機械可読 status" "Machine-readable status")
                     {:href (str root "sponsorship.edn")
                      :type :outline :size "lg"})]]

       (dds/section
        {:id "flow" :title (if ja? "支援の流れ" "How sponsorship works")}
        (dds/grid
         {:min "16rem"}
         (card (dds/chip-label "1")
               (dds/heading 3 (if ja? "使途と原則を確認" "Review the purpose") {:size "20"})
               [:p (if ja?
                     "支援対象と、支援によって購入できないものをこのページで確認します。"
                     "Read what funding sustains and what sponsorship cannot purchase.")])
         (card (dds/chip-label "2")
               (dds/heading 3 (if ja? "GitHub で tier を選択" "Choose a tier on GitHub") {:size "20"})
               [:p (if ja?
                     "profile 公開後、GitHub で単発または月次の tier を選択します。"
                     "After the profile is live, choose a one-time or monthly tier on GitHub.")])
         (card (dds/chip-label "3")
               (dds/heading 3 (if ja? "GitHub で管理" "Manage it on GitHub") {:size "20"})
               [:p (if ja?
                     "支払い、領収、変更、停止は GitHub の sponsorship 画面で管理します。"
                     "GitHub handles payment, receipts, changes, and cancellation.")]))
        (caption (if ja?
                   (str "現在の状態: " (str/upper-case (name (:status sponsorship))) "。確認日: " (:checked-at sponsorship) "。")
                   (str "Current status: " (str/upper-case (name (:status sponsorship))) ". Checked " (:checked-at sponsorship) "."))))

       (dds/section
        {:id "funds" :title (if ja? "支援で継続するもの" "What funding sustains")}
        (bullets (if ja?
                   ["言語 contract、documentation、conformance fixture"
                    "compiler、CLI、provider qualification"
                    "再現可能 release、security review、公開 evidence"]
                   uses)))

       (dds/section
        {:id "boundary" :title (if ja? "支援と購入を分ける" "Sponsorship is not a purchase")}
        (bullets (if ja?
                   ["支援は feature、roadmap priority、support SLA、security exception を購入するものではありません。"
                    "公開 roadmap は方向性であり、納期や提供を約束するものではありません。"]
                   non-exchange))
        [:p (if ja?
              "支払いを伴わない参加は、issue、documentation、code contribution から始められます。"
              "Participation without payment starts with issues, documentation, and code contributions.")]
        [:p (external-link "https://github.com/orgs/kotoba-lang/repositories"
                           (if ja? "公開 repository を見る" "Explore public repositories"))])
       )]
     (footer locale "../legal/")]))

(defn- favicon-link []
  [:link {:rel "icon" :type "image/png" :href "/kotoba-favicon.png"}])

(defn- apple-touch-icon-link []
  [:link {:rel "apple-touch-icon" :href "/kotoba-favicon.png"}])

(defn- og-head
  "Open Graph / Twitter card / canonical head tags. og:image uses the full
  absolute URL social scrapers require; canonical is this page's own URL.
  The description meta itself comes from page/->page's :description option —
  this must not emit a second one. og:title and og:description are emitted
  explicitly because some scrapers do not fall back to <title>/<meta
  description> when og: properties are present but these two are missing."
  ([path title description]
   (list
    [:link {:rel "canonical" :href (str site-origin path)}]
    [:meta {:property "og:site_name" :content "Kotoba"}]
    [:meta {:property "og:type" :content "website"}]
    [:meta {:property "og:url" :content (str site-origin path)}]
    [:meta {:property "og:title" :content title}]
    [:meta {:property "og:description" :content description}]
    [:meta {:property "og:image" :content (str site-origin "/kotoba-og-card.png")}]
    [:meta {:property "og:image:width" :content "1200"}]
    [:meta {:property "og:image:height" :content "630"}]
    [:meta {:property "og:image:alt" :content "AI writes freely. Kotoba draws the boundary."}]
    [:meta {:name "twitter:card" :content "summary_large_image"}]
    [:meta {:name "twitter:image" :content (str site-origin "/kotoba-og-card.png")}])))

(def html
  (page/->page
   {:title "Kotoba — post-quantum-by-default computing for AI agents"
    :description "AI writes freely. Kotoba draws the boundary — a security-first, post-quantum-by-default language and computing stack."
    :lang "en"
    :css dds-css
    :dark? true
    :app-css (str tokens/skin-css "\n" app-css)
    :head (list (favicon-link)
                (apple-touch-icon-link)
                [:script chart-anim-head-js]
                (og-head "/" "Kotoba — post-quantum-by-default computing for AI agents"
                         "AI writes freely. Kotoba draws the boundary — a security-first, post-quantum-by-default language and computing stack."))}
   (view)))

(def blog-html
  (page/->page
   {:title "Kotoba Blog — engineering notes and evidence"
    :description "Kotoba engineering notes about language design, benchmarks, evidence, and remaining qualification gates."
    :lang "en"
    :css dds-css
    :dark? true
    :app-css (str tokens/skin-css "\n" app-css)
    :head (list (favicon-link)
                (apple-touch-icon-link)
                (og-head "/blog/" "Kotoba Blog — engineering notes and evidence"
                         "Engineering notes on language design, benchmarks, evidence, and qualification gates."))}
   (blog-view)))

(def libraries-html
  (page/->page
   {:title "Kotoba Libraries — content-addressed publication and comparison"
    :description "Inspect, publish, discover, and compare Kotoba libraries by immutable definition and release CIDs, with GitHub provenance kept separate."
    :lang "en"
    :css dds-css
    :dark? true
    :app-css (str tokens/skin-css "\n" app-css)
    :head (list (favicon-link)
                (apple-touch-icon-link)
                (og-head "/libraries/" "Kotoba Libraries — content-addressed publication and comparison"
                         "Inspect, publish, discover, and compare libraries by immutable definition and release CIDs."))}
   (libraries-view)))

(def libraries-ja-html
  (page/->page
   {:title "Kotoba Libraries — content-addressed publication と比較"
    :description "不変な definition CID と release CID を使って Kotoba library を inspect、publish、discover、compare し、GitHub provenance を identity と分けます。"
    :lang "ja"
    :css dds-css
    :dark? true
    :app-css (str tokens/skin-css "\n" app-css)
    :head (list (favicon-link)
                (apple-touch-icon-link)
                (og-head "/ja/libraries/" "Kotoba Libraries — content-addressed publication と比較"
                         "不変な definition CID と release CID で Kotoba library を inspect、publish、discover、compare します。"))}
   (libraries-ja-view)))

(def legal-html
  (page/->page
   {:title "Kotoba Labs Inc. — public operator"
    :description "kotoba-lang.org is operated by Kotoba Labs Inc. Public contact: support@kotoba-lang.org."
    :lang "en"
    :css dds-css
    :dark? true
    :app-css (str tokens/skin-css "\n" app-css)
    :head (list (favicon-link)
                (apple-touch-icon-link)
                (og-head "/legal/" "Kotoba Labs Inc. — public operator"
                         "kotoba-lang.org is operated by Kotoba Labs Inc. Public contact: support@kotoba-lang.org."))}
   (legal-view)))

(def legal-ja-html
  (page/->page
   {:title "Kotoba Labs Inc. — 公開運営者"
    :description "kotoba-lang.org の公開運営者は Kotoba Labs Inc. です。公開連絡先: support@kotoba-lang.org。"
    :lang "ja"
    :css dds-css
    :dark? true
    :app-css (str tokens/skin-css "\n" app-css)
    :head (list (favicon-link)
                (apple-touch-icon-link)
                (og-head "/ja/legal/" "Kotoba Labs Inc. — 公開運営者"
                         "公開運営者は Kotoba Labs Inc.。連絡先: support@kotoba-lang.org。"))}
   (legal-ja-view)))

(def sponsor-html
  (page/->page
   {:title "Sponsor Kotoba — GitHub Sponsors"
    :description "Sustain Kotoba's public language contracts, tooling, qualification, and evidence through GitHub Sponsors."
    :lang "en"
    :css dds-css
    :dark? true
    :app-css (str tokens/skin-css "\n" app-css)
    :head (list (favicon-link)
                (apple-touch-icon-link)
                (og-head "/sponsor/" "Sponsor Kotoba — GitHub Sponsors"
                         "Sustain Kotoba's public language contracts, tooling, qualification, and evidence."))}
   (sponsor-view :en)))

(def sponsor-ja-html
  (page/->page
   {:title "Kotoba を支援 — GitHub Sponsors"
    :description "GitHub Sponsors で Kotoba の公開言語仕様、ツール、検証、evidence を継続的に支援します。"
    :lang "ja"
    :css dds-css
    :dark? true
    :app-css (str tokens/skin-css "\n" app-css)
    :head (list (favicon-link)
                (apple-touch-icon-link)
                (og-head "/ja/sponsor/" "Kotoba を支援 — GitHub Sponsors"
                         "Kotoba の公開言語仕様、ツール、検証、evidence を継続的に支援します。"))}
   (sponsor-view :ja)))

(let [out (path/join "site" "dist")]
  (fs/mkdirSync out #js {:recursive true})
  (fs/writeFileSync (path/join out "index.html") html)
  (fs/mkdirSync (path/join out "blog") #js {:recursive true})
  (fs/writeFileSync (path/join out "blog" "index.html") blog-html)
  (fs/mkdirSync (path/join out "libraries") #js {:recursive true})
  (fs/writeFileSync (path/join out "libraries" "index.html") libraries-html)
  (fs/mkdirSync (path/join out "ja" "libraries") #js {:recursive true})
  (fs/writeFileSync (path/join out "ja" "libraries" "index.html") libraries-ja-html)
  (fs/mkdirSync (path/join out "legal") #js {:recursive true})
  (fs/writeFileSync (path/join out "legal" "index.html") legal-html)
  (fs/mkdirSync (path/join out "ja" "legal") #js {:recursive true})
  (fs/writeFileSync (path/join out "ja" "legal" "index.html") legal-ja-html)
  (fs/mkdirSync (path/join out "sponsor") #js {:recursive true})
  (fs/writeFileSync (path/join out "sponsor" "index.html") sponsor-html)
  (fs/mkdirSync (path/join out "ja" "sponsor") #js {:recursive true})
  (fs/writeFileSync (path/join out "ja" "sponsor" "index.html") sponsor-ja-html)
  (fs/copyFileSync (path/join "site" "sponsorship.edn")
                   (path/join out "sponsorship.edn"))
  (fs/copyFileSync logo-source-path (path/join out "kotoba-wordmark.png"))
  (fs/copyFileSync og-card-source-path (path/join out "kotoba-og-card.png"))
  (fs/copyFileSync favicon-source-path (path/join out "kotoba-favicon.png"))
  ;; /favicon.ico for legacy agents that request it without an explicit <link>.
  (fs/copyFileSync favicon-ico-source-path (path/join out "favicon.ico"))
  ;; Cloudflare Workers static assets: _headers lives at the dist root.
  (fs/copyFileSync (path/join "site" "_headers") (path/join out "_headers"))
  (fs/copyFileSync dependency-manifest-path (path/join out "dependencies.edn"))
  (doseq [[source target]
          [[benchmark-source-path (path/join out "benchmarks" "compile-wasm-latest.json")]
           [runtime-benchmark-source-path (path/join out "benchmarks" "runtime-native-latest.json")]
           [end-to-end-benchmark-source-path (path/join out "benchmarks" "end-to-end-latest.json")]
           [domain-benchmark-source-path (path/join out "benchmarks" "domains-latest.json")]
           [build-scaling-source-path (path/join out "benchmarks" "build-scaling-latest.json")]
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
     (js/JSON.stringify (clj->js library-publication) nil 2))
    (fs/copyFileSync package-registry-path
                     (path/join wk "kotoba-package-registry.edn"))
    (fs/copyFileSync cryptographic-boundaries-path
                     (path/join wk "kotoba-cryptographic-boundaries.edn")))
  ;; Static raw-IPFS surface. The CLI re-hashes every response and compares
  ;; both origins; this directory is transport, not naming authority.
  (let [ipfs-out (path/join out "ipfs")]
    ;; Keep the generated gateway projection exact. A recursive copy alone
    ;; preserves blocks removed from the source and can keep an obsolete
    ;; publication address reachable after a registry rotation.
    (fs/rmSync ipfs-out #js {:recursive true :force true})
    (fs/cpSync package-ipfs-path ipfs-out #js {:recursive true}))
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
