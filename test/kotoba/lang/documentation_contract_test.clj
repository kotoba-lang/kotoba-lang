(ns kotoba.lang.documentation-contract-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import [java.io File]))

(defn file-at [root path]
  (File. ^File root ^String path))

(defn read-edn [root path]
  (edn/read-string (slurp (file-at root path))))

(defn markdown-targets [text]
  (map second (re-seq #"\[[^\]]*\]\(([^)]+)\)" text)))

(defn relative-target [^File source target]
  (let [target (-> target
                   (str/replace #"^<|>$" "")
                   (str/split #"#" 2)
                   first)]
    (when (and (seq target)
               (not (re-find #"^[a-zA-Z][a-zA-Z0-9+.-]*:" target))
               (not (str/starts-with? target "/")))
      (File. (.getParentFile source) target))))

(defn problems [root]
  (let [root (.getCanonicalFile (File. root))
        authority-map (read-edn root "docs/authority-map.edn")
        checked (:checked-documents authority-map)
        missing (for [path checked
                      :when (not (.isFile (file-at root path)))]
                  {:code :docs/document-missing :path path})
        broken (for [path checked
                     :let [source (file-at root path)]
                     :when (.isFile source)
                     target (markdown-targets (slurp source))
                     :let [resolved (relative-target source target)]
                     :when (and resolved (not (.exists resolved)))]
                 {:code :docs/link-missing :source path :target target})
        routes (for [[audience {:keys [start next]}] (:audiences authority-map)
                     path (cons start next)
                     :when (not (.isFile (file-at root path)))]
                 {:code :docs/route-missing :audience audience :path path})
        grammar (when (.isFile (file-at root "lang/guest-grammar.edn"))
                  (read-edn root "lang/guest-grammar.edn"))
        surface (when (.isFile (file-at root "lang/surface-status.edn"))
                  (read-edn root "lang/surface-status.edn"))
        pipeline (when (.isFile (file-at root "lang/elaboration-pipeline.edn"))
                   (read-edn root "lang/elaboration-pipeline.edn"))
        versions (when (and grammar surface pipeline)
                   {:grammar (:kotoba.lang.guest-grammar/profile-version grammar)
                    :surface (:kotoba.lang.surface-status/profile-version surface)
                    :pipeline (get-in pipeline [:contract-versions :language-profile])})
        drift (when (and versions (not (apply = (vals versions))))
                [{:code :docs/profile-version-drift :versions versions}])]
    (vec (concat missing broken routes drift))))

(deftest checked-documentation-contract-is-consistent
  (is (empty? (problems "."))))

(deftest broken-link-fixture-proves-the-negative-path
  (let [found (problems "test/fixtures/docs-negative")]
    (is (= [:docs/link-missing] (mapv :code found)))
    (is (= "missing.md" (:target (first found))))))

(deftest release-binding-names-the-verified-profile-6-artifact
  (let [binding (read-edn (File. ".") "lang/docs-release.edn")
        policy (read-edn (File. ".") "lang/version-policy.edn")
        surface (read-edn (File. ".") "lang/surface-status.edn")
        trust (read-edn (File. ".") "lang/release-trust.edn")
        implementation (:implementation-release binding)
        envelope (:signed-envelope implementation)]
    (is (= (:kotoba.lang.surface-status/profile-version surface)
           (get-in binding [:contract :language-profile])))
    (is (= (:release/language-profile policy)
           (get-in binding [:language-release :language-profile])))
    (is (= 6 (get-in binding [:language-release :language-profile])))
    (is (= "0.7.0" (get-in binding [:language-release :version])))
    (is (= :verified
           (get-in binding [:implementation-release :language-profile-binding])))
    (is (= 6 (get-in binding [:implementation-release :language-profile])))
    (is (= 1 (get-in binding [:implementation-release :package-contract])))
    (is (= #{:darwin-arm64}
           (get-in binding [:implementation-release :platforms])))
    (is (= "6d2ad543f48391b91bec63b50a7fdb7ba8fe8828"
           (get-in binding [:implementation-release :commit])))
    (is (= :verified
           (get-in binding [:implementation-release :signed-envelope
                            :signature-status])))
    (is (= "sha256:e9d8186c4e54aa95e53e56877a794dcd890c6b296a6e5bd2bfd9cccc8ce0638c"
           (get-in implementation [:artifact-digests :darwin-arm64])))
    (is (= "66f6368dabfea6b6a842fb6fa10d261e4e3545a3667ec227740c26a0433b4f2e"
           (:sha256 envelope)))
    (is (= :active (get-in trust [:signers (:signer envelope) :status])))
    (is (= :released (get-in binding [:public-default :status])))
    (is (= :docs/release-bound-profile
           (get-in binding [:public-default :code])))))

(deftest diagnostic-registry-is-stable-and-source-backed
  (let [registry (read-edn (File. ".") "lang/diagnostics.edn")
        entries (:diagnostics registry)
        codes (map :code entries)]
    (is (= 1 (:kotoba.diagnostics/version registry)))
    (is (= (count codes) (count (set codes))))
    (is (every? keyword? codes))
    (is (every? #(and (keyword? (:phase %))
                      (not (str/blank? (:summary %)))
                      (not (str/blank? (:action %)))
                      (.isFile (file-at (File. ".") (:source %))))
                entries))))

(deftest external-validation-cannot-be-closed-by-automation
  (let [validation (read-edn (File. ".") "docs/user-validation.edn")
        required (get-in validation
                         [:protocol :external-completion-requires :required-tasks])]
    (is (= #{:install :first-run :capability-denial :error-recovery} required))
    (is (= :implemented (get-in validation [:automation :status])))
    (is (empty? (:observations validation)))
    (is (= :pending (get-in validation [:external-gate :status])))))

(deftest generated-search-index-covers-each-reference-kind
  (let [entries (read-edn (File. ".") "docs/search-index.edn")]
    (is (= #{:release :cli :stdlib :diagnostic} (set (map :kind entries))))
    (is (= 51 (count entries)))
    (is (every? #(and (string? (:title %))
                      (string? (:body %))
                      (string? (:url %))) entries))))

(deftest generated-site-embeds-private-local-search-and-release-block
  (let [html (slurp "site/dist/index.html")
        search-items (count (re-seq #"class=\"kot-search-item\"" html))
        index-entries (count (read-edn (File. ".") "docs/search-index.edn"))]
    (is (str/includes? html "id=\"kot-doc-search\""))
    (is (str/includes? html "aria-label=\"Search Kotoba documentation reference\""))
    (is (str/includes? html "No query leaves the browser."))
    (is (str/includes? html "docs/release-bound-profile"))
    (is (str/includes? html "profile binding: verified"))
    (is (str/includes? html "Proof, with the boundary attached"))
    (is (str/includes? html "Where Lisp's mind and GP 2's graph rewriting meet Rust's discipline"))
    (is (str/includes? html "href=\"https://uoycs-plasma.github.io/GP2/\""))
    (is (str/includes? html "./benchmarks/compile-wasm-latest.json"))
    (is (str/includes? html "./benchmarks/end-to-end-latest.json"))
    (is (str/includes? html "./benchmarks/domains-latest.json"))
    (is (str/includes? html "4 toolchains, 21 runs each"))
    (is (str/includes? html "11 toolchain paths"))
    (is (str/includes? html "DEVELOPER LOOP · RANK UNQUALIFIED"))
    (is (str/includes? html "WORKLOAD DOMAINS · RANK UNQUALIFIED"))
    (is (str/includes? html "Amortized in-process batch medians"))
    (is (str/includes? html "map chains are fused into reduce"))
    (is (str/includes? html "BUILD STARTUP · RANK UNQUALIFIED"))
    (is (str/includes? html "C / Clang"))
    (is (str/includes? html "JVM / javac"))
    (is (str/includes? html "./agent-quickstart.md"))
    (is (.isFile (file-at (File. ".") "site/dist/llms.txt")))
    (is (.isFile (file-at (File. ".") "site/dist/llms-full.txt")))
    (is (.isFile (file-at (File. ".") "site/dist/agent-quickstart.md")))
    (is (.isFile (file-at (File. ".") "site/dist/benchmarks/compile-wasm-latest.json")))
    (is (.isFile (file-at (File. ".") "site/dist/benchmarks/end-to-end-latest.json")))
    (is (.isFile (file-at (File. ".") "site/dist/benchmarks/domains-latest.json")))
    (is (= (slurp "bench/public-end-to-end-comparison/latest.json")
           (slurp "site/dist/benchmarks/end-to-end-latest.json"))
        "the published developer-loop report must be byte-identical to its measured source")
    (is (= (slurp "bench/public-domain-comparison/latest.json")
           (slurp "site/dist/benchmarks/domains-latest.json"))
        "the published workload-domain report must be byte-identical to its measured source")
    (is (= (read-edn (File. ".") "security/cryptographic-boundaries.edn")
           (read-edn (File. ".")
                     "site/dist/.well-known/kotoba-cryptographic-boundaries.edn"))
        "the public crypto inventory projection must remain byte-semantically exact")
    (is (= 51 search-items))
    (is (= index-entries search-items))
    (is (str/includes? html ">kotoba id<"))
    (is (str/includes? html "input.addEventListener('input',apply)"))))

(deftest generated-library-catalog-has-mutually-linked-english-and-japanese-pages
  (let [english (slurp "site/dist/libraries/index.html")
        japanese (slurp "site/dist/ja/libraries/index.html")]
    (is (str/includes? english "href=\"../ja/libraries/\">日本語</a>"))
    (is (str/includes? japanese "lang=\"ja\""))
    (is (str/includes? japanese "名前はコードを見つける。Hash は、それが何かを示す。"))
    (is (str/includes? japanese "href=\"../../libraries/\">English</a>"))
    (is (str/includes? japanese "Passkey-hosted publish"))
    (is (str/includes? japanese "fragment-only の承認 URL"))
    (is (str/includes? japanese "--hosted"))))

(deftest generated-site-uses-the-pinned-portable-highlight-library
  (let [manifest (read-edn (File. ".") "site/dependencies.edn")
        published (read-edn (File. ".") "site/dist/dependencies.edn")
        syntax (first (filter #(= :syntax-highlighting (:id %))
                              (:build-time manifest)))
        html (slurp "site/dist/index.html")]
    (is (= manifest published) "the public dependency manifest must be exact")
    (is (= "kotoba-lang/grammar" (:repository syntax)))
    (is (= "4457d76212e52e277b15a0427ea66b5dd50e657e" (:revision syntax)))
    (is (= "kotoba.grammar.highlight/tokenize" (:api syntax)))
    (is (= "site/generate.cljs/highlighted-kotoba" (:consumer syntax)))
    (is (false? (:runtime-dependency syntax)))
    (is (str/includes? html "kotoba.grammar.highlight/tokenize"))
    (doseq [class ["kot-syntax-comment" "kot-syntax-form"
                   "kot-syntax-definition" "kot-syntax-function"
                   "kot-syntax-number" "kot-syntax-delimiter"
                   "kot-syntax-symbol"]]
      (is (str/includes? html class) (str class " must remain rendered")))))

(deftest generated-site-explains-typed-eval-without-authority-overclaim
  (let [html (slurp "site/dist/index.html")]
    (is (str/includes? html "id=\"typed-eval\""))
    (is (str/includes? html "DEFINITION CID"))
    (is (str/includes? html "ADMISSION CID"))
    (is (str/includes? html "VALUE CID"))
    (is (str/includes? html "Its hash cannot retroactively authorize an effect"))))
