;; measure-host-port-order.cljs — in what order can the host dependency go?
;;
;; `amu` builds a native artifact without a JVM and cannot sign, verify,
;; measure or run one without a JVM. Knowing that is not a work order: a
;; namespace cannot be ported before the namespaces it requires are, so the
;; question "what first" is a topological sort over the require graph
;; restricted to the JVM-bound nodes.
;;
;; JVM-bound here means: this namespace exists as `.clj` and has no `.cljc` or
;; `.cljs` twin on the classpath. That is a property of the tree, not a guess.
;;
;;   nbb --classpath <cp> jvm-port-order.cljs --roots ns,ns --output order.edn

(ns jvm-port-order
  (:require ["node:fs" :as fs]
            ["node:path" :as path]
            [clojure.string :as str]
            [cljs.pprint]))

(def ^:private argv (vec (js->clj js/process.argv)))
(defn- opt [flag]
  (let [i (.indexOf (into-array argv) flag)]
    (when (nat-int? i) (nth argv (inc i) nil))))

(def ^:private roots (->> (str/split (or (opt "--roots") "") #",")
                          (remove str/blank?) (map symbol) set))
(def ^:private output (or (opt "--output") "jvm-port-order.edn"))
(def ^:private dirs (->> (str/split (or (opt "--dirs") "") #":")
                         (remove str/blank?) vec))

(defn- files-under [dir]
  (if (fs/existsSync dir)
    (mapcat (fn [e]
              (let [p (path/join dir e)]
                (if (.isDirectory (fs/statSync p))
                  (files-under p)
                  (when (re-find #"\.clj[cs]?$" e) [p]))))
            (fs/readdirSync dir))
    []))

(defn- ns-name [source]
  (when-let [m (re-find #"\(ns\s+(?:\^\{[^}]*\}\s+)?([a-zA-Z][a-zA-Z0-9_.\-!?*+<>=]*)" source)]
    (symbol (second m))))

(defn- requires [source]
  ;; Every symbol that looks like a namespace inside the ns form's :require.
  ;; Deliberately loose: an over-count adds an edge that is already true of
  ;; some sibling, and a missed edge would silently shorten the order.
  (let [head (subs source 0 (min (count source) 8000))]
    (->> (re-seq #"\[([a-zA-Z][a-zA-Z0-9_.\-]*\.[a-zA-Z0-9_.\-]+)\s+:as" head)
         (map (comp symbol second)) set)))

(let [files (mapcat files-under dirs)
      entries (keep (fn [f]
                      (let [src (fs/readFileSync f "utf8")]
                        (when-let [n (ns-name src)]
                          {:ns n :file f :ext (last (str/split f #"\."))
                           :requires (requires src)})))
                    files)
      by-ns (reduce (fn [m e] (update m (:ns e) (fnil conj []) e)) {} entries)
      exts (into {} (map (fn [[n es]] [n (set (map :ext es))]) by-ns))
      escapes (fn [n]
                (let [es (get by-ns n)
                      srcs (map (fn [e] (fs/readFileSync (:file e) "utf8")) es)
                      ext (get exts n)
                      any (fn [re] (some (fn [src] (boolean (re-find re src))) srcs))]
                  (cond-> #{}
                    (and (contains? ext "clj")
                         (not (contains? ext "cljc"))
                         (not (contains? ext "cljs"))) (conj :clj-only)
                    (any #"\(:import|java\.[a-z]+\.[A-Z]") (conj :java-interop)
                    (any #"\[\"node:") (conj :node-require)
                    (any #"js/[A-Za-z]") (conj :js-interop))))
      escape-map (into {} (map (fn [n] [n (escapes n)]) (keys by-ns)))
      ;; TWO questions, two predicates. A .cljc file with `js/` inside its
      ;; :cljs branch is portable BETWEEN the hosts -- kotoba.hir and
      ;; kotoba.gmir are exactly that, and the JVM-free nbb route lowers
      ;; through both today. Counting them as JVM blockers, which an earlier
      ;; version of this script did, put seven namespaces in the first wave
      ;; that do not block the JVM at all.
      jvm-blocker? (fn [n] (contains? (get escape-map n) :clj-only))
      kotoba-blocker? (fn [n] (seq (get escape-map n)))
      deps (into {} (map (fn [[n es]] [n (reduce into #{} (map :requires es))]) by-ns))
      ;; reachable JVM-bound closure from the roots
      reach (loop [seen #{} queue (vec roots)]
              (if-let [n (first queue)]
                (if (contains? seen n)
                  (recur seen (vec (rest queue)))
                  (recur (conj seen n) (into (vec (rest queue)) (get deps n #{}))))
                seen))
      sort-waves
      (fn [nodes]
        (let [edges (into {} (map (fn [n] [n (set (filter nodes (get deps n #{})))]) nodes))]
          (loop [remaining nodes done [] wave 0 waves []]
            (if (empty? remaining)
              waves
              (let [ready (set (filter (fn [n] (empty? (remove (set done) (get edges n)))) remaining))]
                (if (empty? ready)
                  (conj waves {:wave :cycle :namespaces (vec (sort remaining))})
                  (recur (remove ready remaining) (into done ready) (inc wave)
                         (conj waves {:wave wave :namespaces (vec (sort ready))}))))))))
      jvm-nodes (set (filter jvm-blocker? reach))
      kotoba-nodes (set (filter kotoba-blocker? reach))
      nodes kotoba-nodes
      order (sort-waves kotoba-nodes)
      jvm-order (sort-waves jvm-nodes)
      result {:kotoba.lang.host-port-order/version 1
              :measured-at (subs (.toISOString (js/Date.)) 0 10)
              :question "In what order can the JVM and Node dependencies be removed from the native flow?"
              :host-escape-rule
              {:clj-only "exists as .clj with no .cljc/.cljs twin"
               :java-interop "(:import ...) or a java.x.Y symbol"
               :node-require "requires a [\"node:...\"] module"
               :js-interop "names a js/ symbol"}
              :escapes (into (sorted-map)
                             (map (fn [n] [n (vec (sort (get escape-map n)))])
                                  (sort nodes)))
              :roots (vec (sort roots))
              :reachable-namespaces (count reach)
              :jvm-removal {:blocker-rule ":clj-only -- a .clj with no .cljc/.cljs twin, so it runs on no other host"
                            :count (count jvm-nodes)
                            :waves jvm-order}
              :kotoba-only {:blocker-rule "any host escape at all, including a :cljs branch that names js/"
                            :count (count kotoba-nodes)
                            :waves order}
              :host-bound-count (count kotoba-nodes)
              :waves order
              :not-established
              ["Kotoba-only is the target, and neither .cljc nor a portable host shim is
                that. A namespace with no escape here is portable between the two
                hosts; it is not yet written in the language."
               "An edge is a require, not a proof that porting the dependency is
                sufficient. A namespace can also be JVM-bound by a java. import
                inside a .cljc file, which this does not see."
               "Wave 1 is what can be started, not what is easy."]}]
  (fs/writeFileSync output (with-out-str (cljs.pprint/pprint result)))
  (println "reachable:" (count reach))
  (println "-- to stop depending on the JVM --  blockers:" (count jvm-nodes) " waves:" (count jvm-order))
  (doseq [w jvm-order]
    (println (str "  wave " (:wave w) ": " (str/join ", " (map str (:namespaces w))))))
  (println "-- to be Kotoba only --  blockers:" (count kotoba-nodes) " waves:" (count order))
  (doseq [w order]
    (println (str "  wave " (:wave w) ": " (str/join ", " (map str (:namespaces w)))))))
