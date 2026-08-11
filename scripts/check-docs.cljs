#!/usr/bin/env nbb
(ns check-docs
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            ["child_process" :as child]
            ["fs" :as fs]
            ["path" :as path]))

(defn arg-value [flag]
  (let [args (vec (drop 2 (js->clj (.-argv js/process))))
        i (.indexOf args flag)]
    (when (and (<= 0 i) (< (inc i) (count args)))
      (nth args (inc i)))))

(def root (path/resolve (or (arg-value "--root") ".")))
(def map-path (path/join root "docs" "authority-map.edn"))

(defn exists? [p] (fs/existsSync p))
(defn read-text [p] (fs/readFileSync p "utf8"))
(defn read-edn [p] (reader/read-string (read-text p)))
(defn local-path [p] (path/resolve root p))

(defn markdown-targets [text]
  (let [re #"\[[^\]]*\]\(([^)]+)\)"]
    (map second (re-seq re text))))

(defn relative-target [source target]
  (let [target (-> target
                   (str/replace #"^<|>$" "")
                   (str/split #"#" 2)
                   first)]
    (when (and (seq target)
               (not (str/starts-with? target "#"))
               (not (re-find #"^[a-zA-Z][a-zA-Z0-9+.-]*:" target))
               (not (str/starts-with? target "/")))
      (path/resolve (path/dirname source) target))))

(defn profile-errors []
  (let [grammar-path (local-path "lang/guest-grammar.edn")
        surface-path (local-path "lang/surface-status.edn")
        pipeline-path (local-path "lang/elaboration-pipeline.edn")]
    (if-not (every? exists? [grammar-path surface-path pipeline-path])
      []
      (let [grammar (read-edn grammar-path)
            surface (read-edn surface-path)
            pipeline (read-edn pipeline-path)
            versions {:grammar (:kotoba.lang.guest-grammar/profile-version grammar)
                      :surface (:kotoba.lang.surface-status/profile-version surface)
                      :pipeline (get-in pipeline [:contract-versions :language-profile])}]
        (when-not (apply = (vals versions))
          [{:code :docs/profile-version-drift :versions versions}])))))

(defn generated-errors []
  (let [generator (local-path "scripts/generate-docs-reference.cljs")]
    (if-not (exists? generator)
      []
      (let [result (child/spawnSync "nbb"
                                    #js ["scripts/generate-docs-reference.cljs" "--check"]
                                    #js {:cwd root :encoding "utf8"})]
        (when-not (zero? (.-status result))
          [{:code :docs/generated-drift
            :detail (str/trim (str (.-stdout result) (.-stderr result)))}])))))

(defn release-errors []
  (let [binding-path (local-path "lang/docs-release.edn")
        policy-path (local-path "lang/version-policy.edn")
        surface-path (local-path "lang/surface-status.edn")
        trust-path (local-path "lang/release-trust.edn")]
    (if-not (every? exists? [binding-path policy-path surface-path trust-path])
      []
      (let [binding (read-edn binding-path)
            policy (read-edn policy-path)
            surface (read-edn surface-path)
            trust (read-edn trust-path)
            contract-profile (get-in binding [:contract :language-profile])
            release-profile (get-in binding [:language-release :language-profile])
            language-version (get-in binding [:language-release :version])
            implementation (:implementation-release binding)
            impl-binding (:language-profile-binding implementation)
            public-status (get-in binding [:public-default :status])
            envelope (:signed-envelope implementation)
            signer (get-in trust [:signers (:signer envelope)])
            conformance (:conformance-result implementation)
            required-binds #{:binary-sha256 :runtime :source-extensions
                             :structured-rejection :commit :tree :platform
                             :language-profile :package-contract
                             :artifact-digests :conformance-result}
            released-valid?
            (and (= :verified impl-binding)
                 (= contract-profile (:language-profile implementation))
                 (= (:release/package-contract policy)
                    (:package-contract implementation))
                 (= (str "v" language-version) (:tag implementation))
                 (re-matches #"[0-9a-f]{40}" (:commit implementation))
                 (re-matches #"[0-9a-f]{40}" (:tree implementation))
                 (= (:platforms implementation)
                    (set (keys (:artifact-digests implementation))))
                 (seq (:platforms implementation))
                 (every? #(re-matches #"sha256:[0-9a-f]{64}" %)
                         (vals (:artifact-digests implementation)))
                 (re-matches #"[0-9a-f]{64}" (:binary-sha256 implementation))
                 (= :passed (:status conformance))
                 (pos? (:tests conformance))
                 (pos? (:assertions conformance))
                 (zero? (:failures conformance))
                 (zero? (:errors conformance))
                 (every? (:evidence-binds implementation) required-binds)
                 (= :verified (:signature-status envelope))
                 (re-matches #"[0-9a-f]{64}" (:sha256 envelope))
                 (= :active (:status signer))
                 (contains? (:purpose signer) :implementation-release))]
        (cond-> []
          (not= contract-profile (:kotoba.lang.surface-status/profile-version surface))
          (conj {:code :docs/release-contract-drift})
          (not= release-profile (:release/language-profile policy))
          (conj {:code :docs/release-policy-drift})
          (and (or (not= contract-profile release-profile)
                   (= :absent impl-binding))
               (not= :blocked public-status))
          (conj {:code :docs/release-overclaim
                 :contract-profile contract-profile
                 :release-profile release-profile
                 :implementation-binding impl-binding})
          (and (= :released public-status) (not released-valid?))
          (conj {:code :docs/released-binding-incomplete
                 :implementation-release implementation}))))))

(defn diagnostic-errors []
  (let [p (local-path "lang/diagnostics.edn")]
    (if-not (exists? p)
      []
      (let [registry (read-edn p)
            entries (:diagnostics registry)
            codes (map :code entries)]
        (cond-> []
          (not= (count codes) (count (set codes)))
          (conj {:code :docs/diagnostic-code-duplicate})
          (some #(or (not (keyword? (:code %)))
                     (not (keyword? (:phase %)))
                     (str/blank? (:summary %))
                     (str/blank? (:action %))
                     (not (exists? (local-path (:source %))))) entries)
          (conj {:code :docs/diagnostic-entry-invalid}))))))

(defn validation-errors []
  (let [p (local-path "docs/user-validation.edn")]
    (if-not (exists? p)
      []
      (let [doc (read-edn p)
            observations (:observations doc)
            invalid (filter #(or (nil? (:participant/class %))
                                 (nil? (:task %))
                                 (nil? (:outcome %))
                                 (str/blank? (:evidence %))
                                 (str/blank? (:observed-at %))) observations)
            gate (get-in doc [:external-gate :status])
            external (filter #(= :external-user (:participant/class %)) observations)
            required (get-in doc [:protocol :external-completion-requires :required-tasks])
            covered (set (map :task (filter #(= :pass (:outcome %)) external)))
            minimum (get-in doc [:protocol :external-completion-requires :minimum-participants])]
        (cond-> []
          (seq invalid)
          (conj {:code :docs/validation-result-invalid :count (count invalid)})
          (and (= :complete gate)
               (or (< (count (set (map :participant/id external))) minimum)
                   (not (every? covered required))))
          (conj {:code :docs/external-validation-overclaim}))))))

(defn main []
  (let [missing-map (when-not (exists? map-path)
                      [{:code :docs/authority-map-missing
                        :path (path/relative root map-path)}])
        authority-map (when-not missing-map (read-edn map-path))
        checked (:checked-documents authority-map [])
        missing-docs (for [doc checked :when (not (exists? (local-path doc)))]
                       {:code :docs/document-missing :path doc})
        broken-links
        (for [doc checked
              :let [source (local-path doc)]
              :when (exists? source)
              target (markdown-targets (read-text source))
              :let [resolved (relative-target source target)]
              :when (and resolved (not (exists? resolved)))]
          {:code :docs/link-missing
           :source doc
           :target target
           :resolved (path/relative root resolved)})
        route-errors
        (for [[audience {:keys [start next]}] (:audiences authority-map {})
              p (cons start next)
              :when (not (exists? (local-path p)))]
          {:code :docs/route-missing :audience audience :path p})
        authority-errors
        (for [[axis {:keys [owner path kind]}] (:authorities authority-map {})
              :when (or (not (re-matches #"[^/]+/[^/]+" (or owner "")))
                        (str/blank? path)
                        (nil? kind))]
          {:code :docs/authority-invalid :axis axis})
        errors (vec (concat missing-map missing-docs broken-links route-errors
                            authority-errors (profile-errors) (generated-errors)
                            (release-errors) (diagnostic-errors)
                            (validation-errors)))]
    (if (seq errors)
      (do (println "DOCS FAIL" (pr-str errors)) (js/process.exit 1))
      (do (println "DOCS PASS"
                   (pr-str {:documents (count checked)
                            :routes (count (:audiences authority-map))
                            :authorities (count (:authorities authority-map))}))
          (js/process.exit 0)))))

(main)
