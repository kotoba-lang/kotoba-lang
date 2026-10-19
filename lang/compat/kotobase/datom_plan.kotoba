(ns kotobase.datom-plan
  "Shared pure plan from an `[s p o]` pattern to a Datomic index read.

  The contract belongs beside `datom.source/IPatternSource`, not in any one
  HTTP client. Keeping it here lets in-process and remote implementations use
  the same pushdown decision without forcing a particular transport."
  (:require [clojure.set :as set]))

(defn plan
  "`[s p o]` -> a map with index, components, and post-filter keys.
  `:post-filter` names positions the selected index could not bind. Object-only
  patterns correctly fall back to an EAVT scan; VAET indexes refs only."
  [[s p o]]
  (cond
    (and s p) {:index "eavt" :components [s p] :post-filter [nil nil o]}
    s {:index "eavt" :components [s] :post-filter [nil p o]}
    (and p o) {:index "avet" :components [p o] :post-filter [nil nil nil]}
    p {:index "aevt" :components [p] :post-filter [nil nil o]}
    :else {:index "eavt" :components [] :post-filter [nil nil o]}))

(defn reads
  "Return distinct index reads required by PATTERNS."
  [patterns]
  (vec (distinct (map #(select-keys (plan %) [:index :components]) patterns))))

(defn rows->quads
  "Convert asserted Datomic wire rows to stored-value quads via ROW-FN."
  [row-fn rows]
  (into #{}
        (comp (map row-fn)
              (filter :added)
              (map (fn [{:keys [e a v_edn]}] {:s e :p a :o v_edn})))
        rows))

(defn union-quads
  "Union overlapping read results without changing algebraic cardinality."
  [quad-sets]
  (reduce set/union #{} quad-sets))
