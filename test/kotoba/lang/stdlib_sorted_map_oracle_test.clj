(ns kotoba.lang.stdlib-sorted-map-oracle-test
  "`lang/stdlib/sorted_map.kotoba`, checked against clojure.core/sorted-map.

  The oracle is the real thing. A table of expected trees would only record
  what someone believed an AVL tree does with a particular insertion order;
  replaying the same operations into a `sorted-map` says what an ordered map
  IS, and leaves the balancing as the only difference between the two sides.

  Operations cross the boundary as NUMBERS, the way `stdlib_core_oracle_test`
  passes lists: a leading 1 followed by one decimal digit per item. A run is
  two of those -- `kinds`, where 0 is assoc and 1 is dissoc, and `keys`, one
  digit per operation -- plus how many of them to replay. The i-th assoc
  stores `1 + (7i mod 9)`, which cycles through all nine non-zero digits, so
  re-assoc'ing a key that is already there changes its value and a lost
  replacement shows up. Both sides compute that value the same way and the
  Kotoba side cannot see the encoding.

  Twenty-four sequences, from a fixed seed, replayed at EVERY prefix length:
  count, keys, vals, get for every key, contains? for every key, min, max and
  two folds are compared after each operation.

  Balance is checked separately, because it does not show in any answer: a
  rotation preserves the in-order sequence, so an unbalanced tree returns the
  same keys, values and count as a balanced one and only its HEIGHT is wrong.
  `avl-height-bound-holds` is the test that a broken `balance` fails."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private module (slurp "lang/stdlib/sorted_map.kotoba"))

;; The module is a library: no `main`. Calling it needs an entry, and this
;; repository has no project linker to call, so the harness joins the same
;; compilation unit -- which is also why the private helpers (`node-height`)
;; are reachable from here.
(def ^:private harness
  "(defn decode-into [n acc]
     (if (= n 1) acc (decode-into (quot n 10) (pair (- n (* 10 (quot n 10))) acc))))
   (defn decode [n] (decode-into n 0))
   (defn encode-into [items acc]
     (if (= items 0) acc (encode-into (pair-second items) (+ (* acc 10) (pair-first items)))))
   (defn encode [items] (encode-into items 1))
   (defn value-at [i] (+ 1 (- (* 7 i) (* 9 (quot (* 7 i) 9)))))
   (defn build-from [tree kinds keys i n]
     (if (= i n)
       tree
       (build-from (if (= (pair-first kinds) 0)
                     (sm-assoc tree (pair-first keys) (value-at i))
                     (sm-dissoc tree (pair-first keys)))
                   (pair-second kinds) (pair-second keys) (+ i 1) n)))
   (defn build [kinds keys n] (build-from 0 (decode kinds) (decode keys) 0 n))
   (defn t-count [kinds keys n] (sm-count (build kinds keys n)))
   (defn t-keys [kinds keys n] (encode (sm-keys (build kinds keys n))))
   (defn t-vals [kinds keys n] (encode (sm-vals (build kinds keys n))))
   (defn t-height [kinds keys n] (node-height (build kinds keys n)))
   (defn t-get [kinds keys n k] (sm-get (build kinds keys n) k (- 0 1)))
   (defn t-contains [kinds keys n k] (sm-contains? (build kinds keys n) k))
   (defn t-min [kinds keys n] (sm-min-key (build kinds keys n)))
   (defn t-max [kinds keys n] (sm-max-key (build kinds keys n)))
   (defn t-reduce-keys [kinds keys n]
     (sm-reduce (fn [acc e] (+ (* 10 acc) (pair-first e))) 1 (build kinds keys n)))
   (defn t-reduce-sum [kinds keys n]
     (sm-reduce (fn [acc e] (+ acc (* (+ 1 (pair-first e)) (pair-second e)))) 0 (build kinds keys n)))
   (defn t-empty [] (sm-empty))
   (defn asc-from [tree i n] (if (> i n) tree (asc-from (sm-assoc tree i i) (+ i 1) n)))
   (defn t-asc-height [n] (node-height (asc-from 0 1 n)))
   (defn t-asc-count [n] (sm-count (asc-from 0 1 n)))
   (defn main [] 0)")

(def ^:private lowered
  (delay
    (let [body (->> (sema/read-forms module)
                    (remove #(and (seq? %) (= 'ns (first %))))
                    (map pr-str)
                    (str/join "\n"))]
      (kir/lower (sema/analyze (str body "\n" harness))))))

;; The comparisons are about ANSWERS, so they run with a budget that is not
;; the question. What the DEFAULT budget buys is measured on its own, in
;; `what-the-default-fuel-buys`, and recorded in the manifest's :costs.
(defn- run [function & args]
  (long (kir/execute @lowered function (vec args) {:fuel 100000000})))

(defn- run-with-default-fuel [function & args]
  (long (kir/execute @lowered function (vec args))))

;; The oracle side of the encoding.
(defn- encode [items]
  (reduce (fn [acc item] (+ (* acc 10) item)) 1 items))

(defn- value-at [i] (inc (mod (* 7 i) 9)))

;; The oracle side of the replay: an ordinary clojure.core/sorted-map.
(defn- replay [kinds key-digits n]
  (reduce (fn [m i]
            (if (zero? (nth kinds i))
              (assoc m (nth key-digits i) (value-at i))
              (dissoc m (nth key-digits i))))
          (sorted-map)
          (range n)))

;; Twenty-four fixed-seed sequences. Seeded rather than generated once and
;; pasted in, so the inputs are reproducible and a reader can widen them.
(def ^:private sequences
  (let [rng (java.util.Random. 20260902)]
    (vec (for [_ (range 24)
               :let [length (+ 1 (.nextInt rng 17))]]
           {:kinds (vec (for [_ (range length)]
                          ;; roughly one dissoc in three, so keys accumulate
                          (if (zero? (.nextInt rng 3)) 1 0)))
            :key-digits (vec (for [_ (range length)] (.nextInt rng 10)))}))))

;; Two hand-written sequences that the random ones are not guaranteed to hit:
;; the two-child removal (which is the only place `min-node` is reached) and
;; the ascending run that forces the most rotations.
(def ^:private hand-written
  [{:kinds [0 0 0 0 0 1] :key-digits [5 2 8 1 3 2]}
   {:kinds [0 0 0 0 0 0 0 1] :key-digits [4 2 6 1 3 5 7 4]}
   {:kinds [0 0 0 0 0 0 0 0 0] :key-digits [1 2 3 4 5 6 7 8 9]}
   {:kinds [0 0 0 0 0 0 0 0 0] :key-digits [9 8 7 6 5 4 3 2 1]}
   {:kinds [1 1 1] :key-digits [1 2 3]}
   {:kinds [0 0 0] :key-digits [7 7 7]}])

(def ^:private all-sequences (into hand-written sequences))

(defn- disagreements
  "Every prefix of every sequence, compared on one question."
  [label kotoba oracle]
  (for [{:keys [kinds key-digits]} all-sequences
        n (range 0 (inc (count kinds)))
        :let [got (kotoba kinds key-digits n)
              want (oracle (replay kinds key-digits n))]
        :when (not= got want)]
    {:question label :kinds kinds :key-digits key-digits :after n :kotoba got :clojure want}))

(deftest matches-clojure-sorted-map
  (doseq [[label kotoba oracle]
          [["count"
            (fn [ks kk n] (run 't-count (encode ks) (encode kk) n))
            count]
           ["keys ascending"
            (fn [ks kk n] (run 't-keys (encode ks) (encode kk) n))
            #(encode (keys %))]
           ["vals in key order"
            (fn [ks kk n] (run 't-vals (encode ks) (encode kk) n))
            #(encode (vals %))]
           ["reduce over entries, in order"
            (fn [ks kk n] (run 't-reduce-keys (encode ks) (encode kk) n))
            #(reduce (fn [acc [k _]] (+ (* 10 acc) k)) 1 %)]
           ["reduce sees the values"
            (fn [ks kk n] (run 't-reduce-sum (encode ks) (encode kk) n))
            #(reduce (fn [acc [k v]] (+ acc (* (inc k) v))) 0 %)]]]
    (testing label
      (let [bad (disagreements label kotoba oracle)]
        (is (empty? bad) (pr-str (vec (take 5 bad))))))))

(deftest get-and-contains-match-clojure-sorted-map
  ;; Every key, present or not, after every operation -- the absent ones are
  ;; half the point, since `sm-get` answers with a caller-supplied sentinel
  ;; rather than an option and a wrong sentinel would look like a value.
  (let [bad (for [{:keys [kinds key-digits]} all-sequences
                  n (range 0 (inc (count kinds)))
                  k (range 0 10)
                  :let [m (replay kinds key-digits n)
                        got-get (run 't-get (encode kinds) (encode key-digits) n k)
                        want-get (get m k -1)
                        got-has (run 't-contains (encode kinds) (encode key-digits) n k)
                        want-has (if (contains? m k) 1 0)]
                  :when (or (not= got-get want-get) (not= got-has want-has))]
              {:kinds kinds :key-digits key-digits :after n :key k
               :kotoba [got-get got-has] :clojure [want-get want-has]})]
    (is (empty? bad) (pr-str (vec (take 5 bad))))))

(deftest min-and-max-match-clojure-sorted-map
  (let [bad (for [{:keys [kinds key-digits]} all-sequences
                  n (range 0 (inc (count kinds)))
                  :let [m (replay kinds key-digits n)]
                  :when (seq m)
                  :let [got [(run 't-min (encode kinds) (encode key-digits) n)
                             (run 't-max (encode kinds) (encode key-digits) n)]
                        want [(first (keys m)) (last (keys m))]]
                  :when (not= got want)]
              {:kinds kinds :key-digits key-digits :after n :kotoba got :clojure want})]
    (is (empty? bad) (pr-str (vec (take 5 bad))))))

(deftest zero-from-min-key-is-ambiguous-and-that-is-pinned
  ;; The empty map answers 0, and 0 is a key. Asserted rather than avoided,
  ;; so the docstring in sorted_map.kotoba cannot quietly stop being true.
  (is (= 0 (run 't-min (encode [0]) (encode [0]) 0)) "empty map")
  (is (= 0 (run 't-min (encode [0]) (encode [0]) 1)) "a map whose only key is 0")
  (is (= 0 (run 't-count (encode [0]) (encode [0]) 0)))
  (is (= 1 (run 't-count (encode [0]) (encode [0]) 1))
      "sm-count is what tells the two apart")
  (is (= 0 (run 't-empty)) "and sm-empty is the same 0"))

;; ---------------------------------------------------------------------------
;; Balance. Nothing above can see it: a rotation preserves the in-order
;; sequence, so an unbalanced BST returns exactly the same keys, vals, count,
;; get and folds. Height is the only observable, which is why it gets its own
;; test -- and why deleting the rotations from `balance` fails HERE and
;; nowhere else.

(defn- avl-bound
  "The classical AVL height bound: a tree of n keys is at most
  1.4405*log2(n+2) - 0.3277 levels deep."
  [n]
  (if (zero? n) 0 (long (Math/floor (- (* 1.4405 (/ (Math/log (+ n 2)) (Math/log 2))) 0.3277)))))

(deftest avl-height-bound-holds
  (let [bad (for [{:keys [kinds key-digits]} all-sequences
                  n (range 0 (inc (count kinds)))
                  :let [size (count (replay kinds key-digits n))
                        height (run 't-height (encode kinds) (encode key-digits) n)
                        bound (avl-bound size)]
                  :when (> height bound)]
              {:kinds kinds :key-digits key-digits :after n :size size :height height :bound bound})]
    (is (empty? bad) (pr-str (vec (take 5 bad)))))
  (testing "and on ascending runs, which are the worst case for an unbalanced tree"
    (doseq [n [15 31 63]]
      (let [height (run 't-asc-height n)]
        (is (= n (run 't-asc-count n)) (str n " ascending inserts must all be there"))
        (is (<= height (avl-bound n))
            (str n " ascending keys: height " height " exceeds the AVL bound " (avl-bound n)))
        (is (< height n)
            (str "a list would be " n " deep; this is " height))))))

(deftest what-ascending-inserts-cost
  ;; Not a comparison: the measurement the manifest's :costs records. Printed
  ;; so the numbers there can be checked against a run, with a floor so that a
  ;; regression is a failure rather than a smaller number nobody reads.
  (let [heights (into {} (for [n [15 31 63]] [n (run 't-asc-height n)]))
        ceiling (last (take-while
                       (fn [n]
                         (try (run-with-default-fuel 't-asc-height n)
                              true
                              (catch clojure.lang.ExceptionInfo e
                                (if (= :fuel-exhausted (:trap (ex-data e))) false (throw e)))))
                       (range 1 64)))
        get-ceiling (last (take-while
                           (fn [n]
                             (try (run-with-default-fuel 't-get (encode [0]) (encode [0]) 0 n)
                                  true
                                  (catch clojure.lang.ExceptionInfo e
                                    (if (= :fuel-exhausted (:trap (ex-data e))) false (throw e)))))
                           (range 1 4)))]
    (println (str "AVL-ASCENDING-HEIGHT\t" (pr-str heights)
                  "\tDEFAULT-FUEL-512-ASCENDING-INSERTS\t" ceiling
                  "\t(bounds " (pr-str (into {} (for [n [15 31 63]] [n (avl-bound n)]))) ")"))
    (is (= {15 4 31 5 63 6} heights)
        "the AVL heights for 15/31/63 ascending inserts are what the manifest records")
    (is (>= ceiling 8) "the default budget must buy at least eight ascending inserts")
    (is (pos? get-ceiling) "a lookup on the empty map fits in the default budget")))

;; ---------------------------------------------------------------------------

(deftest the-module-provides-exactly-what-it-claims
  (let [manifest (edn/read-string (slurp "lang/conformance/stdlib/manifest.edn"))
        entry (first (filter #(= :sorted-map (:id %)) (:modules manifest)))
        public (set (map (comp symbol second)
                         (re-seq #"(?m)^\s*\(defn\s+([^\s\[]+)" module)))
        private (set (map (comp symbol second)
                          (re-seq #"(?m)^\s*\(defn-\s+([^\s\[]+)" module)))]
    (is (some? entry) "the manifest must carry a :sorted-map module")
    (is (= (:public-names entry) public)
        (str "extra=" (pr-str (set/difference public (:public-names entry)))
             " missing=" (pr-str (set/difference (:public-names entry) public))))
    (is (= (set (mapcat val (:private-helpers entry))) private)
        (str "extra=" (pr-str (set/difference private (set (mapcat val (:private-helpers entry)))))))
    (is (empty? (set/intersection public private)))))
