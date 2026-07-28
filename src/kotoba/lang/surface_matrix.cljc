(ns kotoba.lang.surface-matrix
  "T2.2: generate docs/lang/surface-matrix.md from lang/surface-status.edn.

  Pure loaders + markdown render + --check (byte-identical regenerated body)."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

(def surface-status-path "lang/surface-status.edn")
(def surface-matrix-path "docs/lang/surface-matrix.md")

(def ^:private section-order
  [:invariants :collections :other-gaps])

(def ^:private section-titles
  {:invariants "Security / language invariants"
   :collections "Collections"
   :other-gaps "Other surface (gaps & partials)"})

(defn load-surface-status
  "Parse surface-status.edn from classpath, cwd path, or raw text."
  ([]
   #?(:clj
      (cond
        (.exists (io/file surface-status-path))
        (load-surface-status (slurp surface-status-path))
        (io/resource surface-status-path)
        (load-surface-status (slurp (io/resource surface-status-path)))
        :else
        (throw (ex-info "surface-status.edn missing" {:path surface-status-path})))
      :cljs
      (throw (ex-info "surface-matrix/load-surface-status requires text inject on cljs"
                      {:path surface-status-path}))))
  ([edn-text]
   (edn/read-string edn-text)))

(defn- disposition-of [entry]
  (or (:disposition entry)
      (when (keyword? entry) entry)
      :unknown))

(defn- note-of [entry]
  (or (:note entry)
      (:reason entry)
      (:performance-choice entry)
      (:semantic-choice entry)
      ""))

(defn- backends-of [entry]
  (let [impl (:implementation entry)]
    (cond
      (set? impl) (str/join ", " (map name (sort impl)))
      (sequential? impl) (str/join ", " (map name impl))
      :else "")))

(defn- md-escape [s]
  (-> (str s)
      (str/replace "|" "\\|")
      (str/replace "\n" " ")))

(defn- row [id entry]
  (let [disp (disposition-of entry)
        note (note-of entry)
        backends (backends-of entry)]
    (str "| `" (name id) "` | `" (name disp) "` | "
         (md-escape backends) " | "
         (md-escape (if (string? note) note (pr-str note)))
         " |")))

(defn- section-table [title entries]
  (str "## " title "\n\n"
       "| Surface | Disposition | Backends | Note / reason |\n"
       "|---|---|---|---|\n"
       (str/join "\n"
                 (for [[id entry] (sort-by (comp name first) entries)]
                   (row id (if (map? entry) entry {:disposition entry}))))
       "\n"))

(defn render-markdown
  "Render surface-matrix markdown body from a surface-status map."
  [status]
  (let [ver (:kotoba.lang.surface-status/version status)
        pver (:kotoba.lang.surface-status/profile-version status)
        as-of (:kotoba.lang.surface-status/as-of status)
        adr (:kotoba.lang.surface-status/adr status)
        header (str "# Kotoba language surface matrix\n\n"
                    "**Generated** from `lang/surface-status.edn` — do not hand-edit.\n"
                    "Regenerate: `clojure -M -m kotoba.lang.surface-matrix`\n"
                    "Check: `clojure -M -m kotoba.lang.surface-matrix --check`\n\n"
                    "| Field | Value |\n|---|---|\n"
                    "| surface-status version | " ver " |\n"
                    "| profile version | " pver " |\n"
                    "| as-of | " as-of " |\n"
                    "| authority ADR | `" adr "` |\n\n"
                    "WBS: **T2.2**. Disposition meanings live under "
                    "`:dispositions` in the EDN source.\n\n")
        disp-section
        (str "## Dispositions\n\n"
             "| Keyword | Meaning |\n|---|---|\n"
             (str/join "\n"
                       (for [[k v] (sort-by (comp name first) (:dispositions status))]
                         (str "| `" (name k) "` | "
                              (md-escape (or (:meaning v) "")) " |")))
             "\n\n")
        body (str/join "\n"
                       (for [sec section-order
                             :let [m (get status sec)]
                             :when (map? m)]
                         (section-table (section-titles sec) m)))]
    (str header disp-section body
         "\n## Classification rule\n\n"
         "See `:classification-rule` in `lang/surface-status.edn` "
         "(not expanded here).\n")))

(defn validate-status
  "Structural check that surface-status has expected sections."
  [status]
  (let [problems (transient [])]
    (when-not (number? (:kotoba.lang.surface-status/version status))
      (conj! problems {:type :missing-version}))
    (doseq [sec section-order]
      (when-not (map? (get status sec))
        (conj! problems {:type :missing-section :section sec})))
    (when-not (map? (:dispositions status))
      (conj! problems {:type :missing-dispositions}))
    (let [ps (persistent! problems)]
      {:ok? (empty? ps) :problems ps})))

#?(:clj
   (defn write-matrix!
     "Write docs/lang/surface-matrix.md. Returns path."
     ([] (write-matrix! (load-surface-status)))
     ([status]
      (let [md (render-markdown status)
            f (io/file surface-matrix-path)]
        (io/make-parents f)
        (spit f md)
        (.getPath f)))))

#?(:clj
   (defn check-matrix!
     "Return {:ok? bool :problems ...} comparing on-disk md to regenerated."
     ([] (check-matrix! (load-surface-status)))
     ([status]
      (let [expected (render-markdown status)
            f (io/file surface-matrix-path)]
        (if-not (.exists f)
          {:ok? false :problems [{:type :missing-file :path surface-matrix-path}]}
          (let [actual (slurp f)]
            (if (= expected actual)
              {:ok? true :problems []}
              {:ok? false
               :problems [{:type :stale
                           :path surface-matrix-path
                           :hint "run: clojure -M -m kotoba.lang.surface-matrix"}]})))))))

#?(:clj
   (defn -main
     [& args]
     (let [status (load-surface-status)
           v (validate-status status)]
       (when-not (:ok? v)
         (binding [*out* *err*]
           (println "surface-status invalid:" (pr-str (:problems v))))
         (System/exit 2))
       (if (some #{"--check"} args)
         (let [r (check-matrix! status)]
           (if (:ok? r)
             (do (println "surface-matrix.md up to date")
                 (System/exit 0))
             (do (binding [*out* *err*]
                   (println "STALE:" (pr-str (:problems r))))
                 (System/exit 1))))
         (let [path (write-matrix! status)]
           (println "wrote" path)
           (System/exit 0))))))
