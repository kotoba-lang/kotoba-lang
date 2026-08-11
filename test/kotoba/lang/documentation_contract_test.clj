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

(deftest release-binding-fails-closed-on-the-observed-gap
  (let [binding (read-edn (File. ".") "lang/docs-release.edn")
        policy (read-edn (File. ".") "lang/version-policy.edn")
        surface (read-edn (File. ".") "lang/surface-status.edn")]
    (is (= (:kotoba.lang.surface-status/profile-version surface)
           (get-in binding [:contract :language-profile])))
    (is (= (:release/language-profile policy)
           (get-in binding [:language-release :language-profile])))
    (is (= 6 (get-in binding [:language-release :language-profile])))
    (is (= "0.7.0" (get-in binding [:language-release :version])))
    (is (= :absent
           (get-in binding [:implementation-release :language-profile-binding])))
    (is (= :blocked (get-in binding [:public-default :status])))
    (is (= :docs/no-release-bound-profile
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
    (is (= 47 (count entries)))
    (is (every? #(and (string? (:title %))
                      (string? (:body %))
                      (string? (:url %))) entries))))

(deftest generated-site-embeds-private-local-search-and-release-block
  (let [html (slurp "site/dist/index.html")]
    (is (str/includes? html "id=\"kot-doc-search\""))
    (is (str/includes? html "aria-label=\"Search Kotoba documentation reference\""))
    (is (str/includes? html "No query leaves the browser."))
    (is (str/includes? html "docs/no-release-bound-profile"))
    (is (str/includes? html "profile binding: absent"))
    (is (= 47 (count (re-seq #"class=\"kot-search-item\"" html))))
    (is (str/includes? html "input.addEventListener('input',apply)"))))
