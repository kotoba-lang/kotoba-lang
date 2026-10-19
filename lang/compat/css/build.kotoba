(ns css.build
  "Deterministic filesystem build boundary for css.core sheets."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [css.core :as css]
            [css.utility :as utility]
            [kotoba.css.shadow :as shadow]))

(defn validate-sheet [{:keys [rules media keyframes]}]
  (vec
   (concat
    (for [[selector decls] rules :when (or (str/blank? (str selector)) (not (map? decls)))]
      {:type :invalid-rule :selector selector})
    (for [[query nested] media :when (or (str/blank? (str query)) (not (map? nested)))]
      {:type :invalid-media :query query})
    (for [[name frames] keyframes :when (or (str/blank? (str name)) (not (sequential? frames)))]
      {:type :invalid-keyframes :name name}))))

(defn minify [text]
  (-> text
      (str/replace #"/\*.*?\*/" "")
      (str/replace #"\s+" " ")
      (str/replace #"\s*([{}:;,])\s*" "$1")
      str/trim))

(defn class-tokens [paths]
  (->> paths
       (mapcat (fn [path]
                 (mapcat (fn [[_ classes]] (str/split classes #"\s+"))
                         (re-seq #":class\s+\"([^\"]+)\"" (slurp path)))))
       (remove str/blank?) set sort vec))

(defn index-shadow-source
  "Index static `(css ...)` forms from one source file. Forms must carry reader
  line/column metadata, as normal Clojure/CLJS source does."
  [path]
  (with-open [reader (clojure.lang.LineNumberingPushbackReader. (io/reader path))]
    (let [forms (loop [out []]
                  (let [form (read {:eof ::eof} reader)]
                    (if (= ::eof form) out (recur (conj out form)))))
          ns-name (or (some (fn [form] (when (and (seq? form) (= 'ns (first form))) (second form))) forms)
                      'user)
          indexed (volatile! [])]
      (doseq [form forms]
        (walk/postwalk
         (fn [node]
           (when (and (seq? node) (#{'css 'kotoba.css.shadow/css} (first node)))
             (let [{:keys [line column]} (meta node)
                   class (shadow/class-name ns-name line column)]
               (vswap! indexed conj
                       {:class class
                        :sheet (shadow/compile-form (str "." class) (rest node))})))
           node)
         form))
      @indexed)))

(defn shadow-sheet [paths]
  (reduce (fn [acc sheet]
            (-> acc
                (update :rules merge (:rules sheet))
                (update :media #(merge-with merge (or % {}) (:media sheet)))))
          {:rules {} :media {}}
          (for [path (or paths [])
                indexed (index-shadow-source path)]
            (:sheet indexed))))

(defn- canonical-sheet [sheet]
  (cond-> sheet
    (:rules sheet) (update :rules #(into (sorted-map)
                                         (map (fn [[selector decls]]
                                                [selector (into (sorted-map) decls)])) %))
    (:media sheet) (update :media #(into (sorted-map)
                                         (map (fn [[query rules]]
                                                [query (into (sorted-map)
                                                             (map (fn [[selector decls]]
                                                                    [selector (into (sorted-map) decls)]))
                                                             rules)])) %))))

(defn compile-css
  [{:keys [sheet includes utility-classes shadow-sources minify? strict-utilities? banner]}]
  (let [warnings (validate-sheet (or sheet {}))
        unsupported (utility/unsupported utility-classes)
        warnings (into warnings (map #(hash-map :type :unsupported-utility :class %) unsupported))]
    (when (and strict-utilities? (seq unsupported))
      (throw (ex-info "unsupported CSS utilities" {:warnings warnings})))
    (when (seq (filter #(not= :unsupported-utility (:type %)) warnings))
      (throw (ex-info "invalid CSS sheet" {:warnings warnings})))
    (let [shadow-sheet (shadow-sheet shadow-sources)
          sheet (-> (or sheet {})
                    (update :rules merge (:rules shadow-sheet))
                    (update :media #(merge-with merge (or % {}) (:media shadow-sheet))))
          parts (concat (when banner [(str "/* " banner " */")])
                        includes
                        (when (or (seq (:rules sheet)) (seq (:media sheet)) (seq (:keyframes sheet)))
                          [(css/css (canonical-sheet sheet))])
                        (when (seq utility-classes)
                          [(css/css {:rules (utility/rules utility-classes)
                                     :media (utility/media-rules utility-classes)})]))
          output (str/join "\n" (remove str/blank? parts))]
      {:css (if minify? (minify output) output) :warnings warnings})))

(defn write!
  [{:keys [outputs] :as config}]
  (let [result (compile-css config)]
    (doseq [output outputs]
      (io/make-parents output)
      (spit output (str (:css result) "\n")))
    (assoc result :outputs (vec outputs) :bytes (count (:css result)))))
