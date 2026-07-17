(ns kotoba.lang.guest-grammar
  "Loader for lang/guest-grammar.edn — the shared .kotoba guest form catalog
  (ADR-2607180900). Portable .cljc: reads EDN from the classpath resource
  path used when this library is on the deps path, or from an explicit file."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(def ^:private resource-path "guest-grammar.edn")

(def ^:private catalog*
  (delay
    #?(:clj
       (let [candidates
             [(io/resource "kotoba/lang/guest-grammar.edn")
              (io/resource "lang/guest-grammar.edn")
              (io/file "lang/guest-grammar.edn")
              (io/file "orgs/kotoba-lang/kotoba-lang/lang/guest-grammar.edn")]]
         (or (some (fn [c]
                     (when c
                       (try
                         (with-open [r (io/reader c)]
                           (edn/read (java.io.PushbackReader. r)))
                         (catch Exception _ nil))))
                   candidates)
             {:kotoba.lang.guest-grammar/version 0
              :kotoba.lang.guest-grammar/status :missing
              :forbidden-heads #{}
              :diagnostic-hints {}
              :string-head-host-ops #{}}))
       :cljs
       {:kotoba.lang.guest-grammar/version 0
        :kotoba.lang.guest-grammar/status :cljs-host-loads-via-clj-or-embed
        :forbidden-heads #{}
        :diagnostic-hints {}
        :string-head-host-ops #{}})))

(defn catalog
  "Return the guest-grammar map (memoized)."
  []
  @catalog*)

(defn forbidden-heads
  "Set of symbol heads never admitted in guest source."
  []
  (set (map symbol (map name (:forbidden-heads (catalog) #{})))))

(defn string-head-host-ops
  "Ops whose first source arg may be a bare string literal (lowered to ptr/len)."
  []
  (set (map symbol (map name (:string-head-host-ops (catalog) #{})))))

(defn diagnostic-hint
  "Human-readable hint for a rejected head (string or symbol), or nil."
  [head]
  (let [k (cond (string? head) head
                (symbol? head) (name head)
                :else (str head))]
    (get (:diagnostic-hints (catalog) {}) k)))

(defn problem-with-hint
  "Attach :kotoba.lang/hint when the catalog knows HEAD."
  [problem head]
  (if-let [hint (diagnostic-hint head)]
    (assoc problem :kotoba.lang/hint hint)
    problem))
