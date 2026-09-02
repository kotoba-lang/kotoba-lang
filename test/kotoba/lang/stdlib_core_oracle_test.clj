(ns kotoba.lang.stdlib-core-oracle-test
  "The version-3 names in `lang/stdlib/core.kotoba`, checked against
  clojure.core itself.

  The stdlib's collections are pair chains of i64 ending in 0, and the
  interpreter hands those back as heap handles, not as values. So a list
  crosses the boundary as a NUMBER: a leading 1 followed by one decimal digit
  per item -- 1345 is (3 4 5), 101 is (0 1), 1 is (). The harness decodes on
  the way in and encodes on the way out, the oracle side does the same with
  ordinary Clojure, and the two encodings are compared. Every item is a digit,
  which is enough to sort, deduplicate, partition and interpose, and the
  functions being tested cannot see the encoding.

  `keep` is compared against clojure.core/keep with 0 read as nil, because
  that is the module's stated convention (first-match returns 0 for nothing)
  -- and then, in `keep-differs-from-clojure-on-zero`, the divergence that
  convention creates is asserted rather than avoided."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private module (slurp "lang/stdlib/core.kotoba"))

(def ^:private harness
  "(defn decode-into [n acc]
     (if (= n 1) acc (decode-into (quot n 10) (pair (- n (* 10 (quot n 10))) acc))))
   (defn decode [n] (decode-into n 0))
   (defn encode-into [items acc]
     (if (= items 0) acc (encode-into (pair-second items) (+ (* acc 10) (pair-first items)))))
   (defn encode [items] (encode-into items 1))
   (defn chain-count [items] (if (= items 0) 0 (+ 1 (chain-count (pair-second items)))))
   (defn chain-nth [items i] (if (= items 0) 0 (if (= i 0) (pair-first items) (chain-nth (pair-second items) (- i 1)))))
   (defn t-keep [code] (encode (keep (fn [x] (if (> x 4) (- x 4) 0)) (decode code))))
   (defn t-keep-identity [code] (encode (keep (fn [x] x) (decode code))))
   (defn t-remove [code] (encode (remove (fn [x] (> x 4)) (decode code))))
   (defn t-mapv [code] (encode (mapv (fn [x] (- 9 x)) (decode code))))
   (defn t-take-while [code] (encode (take-while (fn [x] (< x 5)) (decode code))))
   (defn t-drop-while [code] (encode (drop-while (fn [x] (< x 5)) (decode code))))
   (defn t-sort [code] (encode (sort (decode code))))
   (defn t-sort-by [code] (encode (sort-by (fn [x] (quot x 3)) (decode code))))
   (defn t-partition-count [n code] (chain-count (partition n (decode code))))
   (defn t-partition [n code i] (encode (chain-nth (partition n (decode code)) i)))
   (defn t-distinct [code] (encode (distinct (decode code))))
   (defn t-interpose [sep code] (encode (interpose sep (decode code))))
   (defn t-juxt2 [x] (encode (invoke (juxt2 (fn [x] (+ x 1)) (fn [x] (quot x 2))) x)))
   (defn main [] 0)")

(def ^:private lowered
  (delay
    (let [body (->> (sema/read-forms module)
                    (remove #(and (seq? %) (= 'ns (first %))))
                    (map pr-str)
                    (str/join "\n"))]
      (kir/lower (sema/analyze (str body "\n" harness))))))

;; The interpreter's default fuel is 512 calls, the same budget the wasm host
;; gives an instance. The comparisons below are about ANSWERS, so they run with
;; a large budget; what the default budget buys is measured separately in
;; `what-the-default-fuel-buys`, and recorded in the manifest.
(defn- run [function & args]
  (long (kir/execute @lowered function (vec args) {:fuel 1000000})))

(defn- run-with-default-fuel [function & args]
  (long (kir/execute @lowered function (vec args))))

;; The oracle side of the encoding.
(defn- encode [items]
  (reduce (fn [acc item] (+ (* acc 10) item)) 1 items))

(def ^:private lists
  [[] [5] [1 2 3] [9 8 7 6 5 4 3 2 1] [3 1 4 1 5 9 2 6 5 3 5] [2 2 2] [7 7 1 7]
   [5 5 5 5 5 5 5 5 5 5 5 5] [4 5 4 5 6] [1] [9 1] [6 3 6 3 9 0] [0 1]
   [8 2 5 2 8 1 1 9 3] [1 2 3 4 5 6 7 8 9 9 8 7 6 5 4 3 2]])

;; Seventeen digits is the most the i64 encoding holds, so a function that
;; grows its input (interpose) is compared on the inputs whose OUTPUT fits.
(defn- disagreements [label kotoba oracle]
  (for [items lists
        :when (or (not (str/starts-with? label "interpose")) (<= (count items) 8))
        :let [got (kotoba items) want (oracle items)]
        :when (not= got want)]
    {:function label :input items :kotoba got :clojure want}))

(deftest matches-clojure-core
  (doseq [[label kotoba oracle]
          [["keep" #(run 't-keep (encode %))
            #(encode (keep (fn [x] (let [r (if (> x 4) (- x 4) 0)] (when-not (zero? r) r))) %))]
           ["remove" #(run 't-remove (encode %)) #(encode (remove (fn [x] (> x 4)) %))]
           ["mapv" #(run 't-mapv (encode %)) #(encode (mapv (fn [x] (- 9 x)) %))]
           ["take-while" #(run 't-take-while (encode %)) #(encode (take-while (fn [x] (< x 5)) %))]
           ["drop-while" #(run 't-drop-while (encode %)) #(encode (drop-while (fn [x] (< x 5)) %))]
           ["sort" #(run 't-sort (encode %)) #(encode (sort %))]
           ["sort-by" #(run 't-sort-by (encode %)) #(encode (sort-by (fn [x] (quot x 3)) %))]
           ["distinct" #(run 't-distinct (encode %)) #(encode (distinct %))]
           ["interpose 0" #(run 't-interpose 0 (encode %)) #(encode (interpose 0 %))]
           ["interpose 7" #(run 't-interpose 7 (encode %)) #(encode (interpose 7 %))]]]
    (testing label
      (let [bad (disagreements label kotoba oracle)]
        (is (empty? bad) (pr-str (vec bad)))))))

(deftest sort-by-is-stable
  ;; quot-by-3 buckets 3 4 5 together and 6 7 8 together; Clojure keeps their
  ;; arrival order inside a bucket, and so must this.
  (let [items [8 5 3 7 4 6 5 3]]
    (is (= (encode (sort-by #(quot % 3) items))
           (run 't-sort-by (encode items))))
    (is (= [5 3 4 5 3 8 7 6] (sort-by #(quot % 3) items))
        "the oracle really does order by bucket and keep arrival order within it")))

(deftest partition-matches-clojure-core
  (doseq [items lists
          n [1 2 3 4]
          :let [want (vec (partition n items))]]
    (testing (pr-str [n items])
      (is (= (count want) (run 't-partition-count n (encode items))))
      (doseq [i (range (count want))]
        (is (= (encode (nth want i)) (run 't-partition n (encode items) i))
            (str "group " i " of (partition " n " " (pr-str items) ")"))))))

(deftest juxt2-matches-clojure-core
  (doseq [x (range 0 9)]
    (is (= (encode ((juxt inc #(quot % 2)) x)) (run 't-juxt2 x)))))

(deftest keep-differs-from-clojure-on-zero
  ;; The one divergence, pinned: 0 is nil in this module, and clojure.core
  ;; keeps a 0. If this ever passed with equality the docstring in
  ;; core.kotoba would be wrong.
  (is (= (encode [1]) (run 't-keep-identity (encode [0 1]))))
  (is (= (encode [0 1]) (encode (keep identity [0 1]))))
  (is (not= (encode (keep identity [0 1])) (run 't-keep-identity (encode [0 1])))))

(deftest the-encoding-round-trips
  ;; If decode/encode were wrong every test above would be comparing noise.
  (doseq [items lists]
    (is (= (encode items) (run 't-mapv (run 't-mapv (encode items))))
        "mapv (- 9 x) twice is the identity, so the encoding survives the trip")))

(defn- fuel-ceiling
  "The largest n for which `function` on an ASCENDING list of n digits runs
  under the default fuel. Ascending is the worst case for an insertion sort
  that inserts in front of the first larger item: every insert walks the
  whole sorted chain. (Descending is its best case, and measuring that first
  said 17 for everything -- a ceiling that was not a ceiling.)"
  [function]
  (last (take-while
         (fn [n]
           (try (run-with-default-fuel function (encode (take n (cycle [1 2 3 4 5 6 7 8 9]))))
                true
                (catch clojure.lang.ExceptionInfo e
                  (if (= :fuel-exhausted (:trap (ex-data e))) false (throw e)))))
         (range 1 18))))

(deftest what-the-default-fuel-buys
  ;; Not a comparison: a measurement, printed so the number in the manifest
  ;; can be checked against it, with a floor so a regression is a failure and
  ;; not a smaller number nobody reads.
  (let [ceilings (into {} (for [[label function] [["sort" 't-sort] ["sort-by" 't-sort-by]
                                                   ["distinct" 't-distinct] ["mapv" 't-mapv]]]
                            [label (fuel-ceiling function)]))]
    (println (str "DEFAULT-FUEL-512-CEILING\t" (pr-str ceilings)
                  "\t(17 means: not reached within the 17 digits the encoding holds)"))
    (is (>= (get ceilings "sort") 4) "sort must handle at least four items under the default budget")
    (is (>= (get ceilings "sort-by") 4))
    (is (>= (get ceilings "mapv") 8))))
