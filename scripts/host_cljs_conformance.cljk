(ns host-cljs-conformance
  "Executes the authority conformance cases that require the `:host-cljs`
  backend, on a real ClojureScript host (nbb).

  `lang/conformance/manifest.edn` has declared `:host-cljs` since it was
  written -- \"ClojureScript host embed for :cljs expect keys / .cljs
  entries\" -- and six cases require it. Measured 2026-08-12, nothing executed
  it, so seven recorded `:cljs` expect values were compared against nothing.
  Its sibling `:host-clj` got a runner the same day; this closes the pair.

  Prints one EDN map per case on stdout. The comparison lives on the Clojure
  side (`kotoba.lang.host-cljs-conformance-test`) so a mismatch is reported as
  a normal test failure rather than as a non-zero exit from a subprocess."
  (:require ["fs" :as fs]
            ["os" :as os]
            ["path" :as path]
            [clojure.edn :as edn]
            [nbb.core :refer [load-file]]))

(def ^:private args (vec *command-line-args*))
(def ^:private flags (into {} (map vec) (partition 2 (rest args))))
(def conformance-root (or (first args) "lang/conformance"))

(defn- manifest []
  (edn/read-string (fs/readFileSync (path/join conformance-root "manifest.edn") "utf8")))

(defn- host-cljs-cases [m]
  (->> (:cases m)
       (filter #(contains? (set (:required-backends %)) :host-cljs))
       (filter #(contains? (:expect %) :cljs))))

(defn- case-by-id [id]
  (first (filter #(= (keyword id) (:id %)) (host-cljs-cases (manifest)))))

(defn- temp-dir []
  (fs/mkdtempSync (path/join (os/tmpdir) "kotoba-host-cljs-")))

(defn- entry-ns [text]
  (second (re-find #"\(ns\s+([a-zA-Z0-9_.\-]+)" text)))

(defn- stage!
  "Copy the entry next to whatever it requires, under the namespace-derived
  path a ClojureScript host loads it by.

  `namespace_priority/main.cljc` declares `(ns demo.main ...)` while living at
  `namespace_priority/main.cljc`, so no host could find it by namespace where
  it sits. Staging moves it, never rewrites it -- the extension, which is the
  whole subject of these cases, is preserved."
  [{:keys [entry source-paths] :as case}]
  (let [dir (temp-dir)
        text (fs/readFileSync (path/join conformance-root entry) "utf8")
        ext (path/extname entry)
        ns-name (entry-ns text)]
    (doseq [p (or source-paths [])]
      (fs/cpSync (path/join conformance-root p) dir #js {:recursive true}))
    (let [target (if ns-name
                   (let [rel (str (.replace (.replace ns-name (js/RegExp. "-" "g") "_")
                                            (js/RegExp. "\\." "g") "/")
                                  ext)
                         full (path/join dir rel)]
                     (fs/mkdirSync (path/dirname full) #js {:recursive true})
                     full)
                   (path/join dir (path/basename entry)))]
      (fs/writeFileSync target text)
      {:dir dir :file target :ns ns-name})))

(defn- call [ns-name function args]
  (let [sym (symbol (or ns-name "user") (or function "main"))
        f (resolve sym)]
    (if f {:value (apply @f args)} {:error :function-not-found :symbol (str sym)})))

(defn- run-staged [{:keys [id function args] :as case}]
  (let [{:keys [file ns]} (stage! case)]
    (-> (load-file file)
        (.then (fn [_] (merge {:id id} (call ns function args))))
        ;; Never swallow. A case this runner cannot execute must say so, or it
        ;; becomes indistinguishable from one that passed -- the exact shape of
        ;; the gap this runner exists to close.
        (.catch (fn [e] {:id id :error :threw :message (str (.-message e))})))))

(defn- run-required-ns
  "For a case whose entry requires another namespace: the staged directory has
  to be on nbb's classpath, which nbb only accepts at startup (there is no
  runtime add-classpath, checked 2026-08-12). The caller stages first, passes
  --classpath, and names the case here."
  [{:keys [id function args]} ns-name]
  (-> (js/Promise.resolve (require (symbol ns-name)))
      (.then (fn [_] (merge {:id id} (call ns-name function args))))
      (.catch (fn [e] {:id id :error :threw :message (str (.-message e))}))))

(defn- emit [results]
  (println (pr-str {:format :kotoba.host-cljs-conformance/v1 :results (vec results)})))

(defn- run-all []
  (reduce (fn [p case]
            (.then p (fn [acc] (.then (run-staged case) #(conj acc %)))))
          (js/Promise.resolve [])
          (remove :source-paths (host-cljs-cases (manifest)))))

(cond
  (get flags "--stage")
  (let [c (case-by-id (get flags "--stage"))]
    (println (pr-str (assoc (stage! c) :id (:id c) :function (:function c) :args (:args c)))))

  (get flags "--run-required")
  (let [c (case-by-id (get flags "--run-required"))]
    (.then (run-required-ns c (get flags "--ns")) #(emit [%])))

  :else (.then (run-all) emit))
