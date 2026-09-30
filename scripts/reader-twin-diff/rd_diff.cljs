;; Differential check: the Kotoba reader twin (lang/compat/kotoba/compiler/
;; kotoba_reader.kotoba, run through the KIR interpreter) against the host
;; reader (kotoba-sema's kotoba_reader.cljk, run by nbb).
;;
;; Each source is cut into whole-line chunks at top-level boundaries. The twin
;; (probe/rd_lib.cljk) hashes every top-level form it reads bottom-up: tag,
;; span (line * 2^32 + column, for symbol/list/vector/set/map/f64 nodes),
;; payload and children; map entries and set members combine commutatively
;; because the host sorts them and the twin keeps source order. This file
;; computes the same hash over the host forms (`:line`/`:column` metadata,
;; `(f64-from-bits N)` with the f64-literal marker as tag 10, `::x` as the
;; twin's `(kotoba.reader/autokw "x")`) and compares per top-level form.
;;
;; Run:  scripts/reader-twin-diff/par.sh CHUNK JOBSFILE   (JOBSFILE: "file P" lines;
;;       file is cut into chunks, P processes take every P-th chunk)
;; One file: SEL=0 P=1 ENTRY=_x run_rd.sh rd_diff.cljs FILE.cljk
;; Needs WALL_CP-style classpath in /private/tmp/wall-cp-5.txt and a stack
;; ulimit of 65520 KB (the interpreter recurses on the host stack).
(ns rd-diff
  (:require ["node:fs" :as fs]
            [clojure.string :as str]
            [kotoba.sema :as sema]
            [kotoba.kir :as kir]
            [kotoba.compiler.kotoba-reader :as hr]
            [kotoba.compiler.project :as project]
            [kotoba.compiler.project-files :as pf]))

(def roots (vec (str/split (.-KROOTS js/process.env) #":")))
(def rd-dir (.-RD_DIR js/process.env))
(def entry (str rd-dir "/entry" (or (.-ENTRY js/process.env) "") ".cljk"))
(def chunk-bytes (js/parseInt (or (.-CHUNK js/process.env) "20000")))
(def budget {:fuel 100000000000 :frames 100000 :cells 2000000000 :bytes 2000000000})

(defn run-batch [fn-name srcs]
  (.writeFileSync fs entry
    (str "(ns probe.rd-entry\n  {:kotoba/export [main]}\n  (:require [probe.rd-lib :as l]))\n"
         "(defn main [] :string (str "
         (str/join " \"\\n\" " (map #(str "(l/" fn-name " " (pr-str %) ")") srcs))
         "))\n"))
  (let [graph (pf/load-closed-graph entry roots)
        linked (project/link-source (:sources graph) (:root graph))
        hir (sema/analyze (:source linked) {:admit-linked-synthetics? true})]
    (str/split (kir/execute (kir/lower hir) 'main [] budget) #"\n" -1)))

(def M (js/BigInt "1099511628211"))
(def B7 (js/BigInt 7))
(def B0 (js/BigInt 0))
(def B32 (js/BigInt "4294967296"))
(def SEED (js/BigInt 1469598103))
(defn m [h x] (.asIntN js/BigInt 64 (+ (* h M) (js/BigInt x) B7)))
(defn str-hash [s h]
  (reduce (fn [acc ch] (m acc (.codePointAt ch 0))) h (.from js/Array s)))
(def line-off (atom 0))
(defn span-of [x]
  (let [mt (meta x)]
    (if (and mt (:line mt))
      (+ (* (js/BigInt (- (:line mt) @line-off)) B32) (js/BigInt (:column mt)))
      B0)))
(declare hh)
(def typeof-fn (js/Function. "x" "return typeof x"))
(defn hash-kids [h xs] (reduce (fn [acc k] (m acc (hh k))) h xs))
(defn hh [x]
  (let [h0 (fn [t] (m SEED t))
        sp (fn [t] (m (h0 t) (span-of x)))]
    (cond
      (nil? x) (h0 0)
      (boolean? x) (m (h0 1) (if x 1 0))
      (or (integer? x) (= "bigint" (typeof-fn x))) (m (h0 2) (.asIntN js/BigInt 64 (js/BigInt x)))
      (string? x) (str-hash x (h0 3))
      (and (keyword? x) (str/starts-with? (str x) "::"))
      (hh (list 'kotoba.reader/autokw (subs (str x) 2)))
      (keyword? x) (str-hash (subs (str x) 1) (h0 4))
      (symbol? x) (str-hash (str x) (sp 5))
      (and (seq? x) (:kotoba.reader/f64-literal (meta x)))
      (m (sp 10) (.asIntN js/BigInt 64 (js/BigInt (second x))))
      (seq? x) (m (hash-kids (sp 6) x) (count x))
      (vector? x) (m (hash-kids (sp 7) x) (count x))
      (set? x) (m (m (sp 8) (.asIntN js/BigInt 64 (reduce (fn [a k] (+ a (hh k))) B0 x))) (count x))
      (map? x) (m (m (sp 9) (.asIntN js/BigInt 64 (reduce (fn [a [k v]] (+ a (m (hh k) (hh v)))) B0 x))) (* 2 (count x)))
      :else (throw (ex-info "hh" {:x (pr-str x)})))))

(defn tags [x]
  (cons (cond (nil? x) :nil (boolean? x) :bool (or (integer? x) (= "bigint" (typeof-fn x))) :int
              (string? x) :string (and (keyword? x) (str/starts-with? (str x) "::")) :list (keyword? x) :keyword (symbol? x) :symbol
              (and (seq? x) (:kotoba.reader/f64-literal (meta x))) :f64
              (seq? x) :list (vector? x) :vector (set? x) :set (map? x) :map :else :other)
        (cond (and (seq? x) (:kotoba.reader/f64-literal (meta x))) nil
              (or (seq? x) (vector? x) (set? x)) (mapcat tags x)
              (map? x) (mapcat (fn [[k v]] (concat (tags k) (tags v))) x)
              :else nil)))

(defn chunks
  "Returns [{:text \"...\" :forms [...]}]: whole-line segments starting at a
  top-level boundary line, padded with newlines so lines stay absolute."
  [src forms]
  (let [lines (vec (str/split src #"\n" -1))
        form-lines (set (map (comp :line meta) forms))
        boundary? (fn [ln] ;; ln is 1-based
                    (let [l (nth lines (dec ln))]
                      (and (seq l) (not (contains? #{" " ";" "\t"} (subs l 0 1)))
                           (or (contains? form-lines ln)
                               (str/starts-with? l "#?") (str/starts-with? l "#_") (str/starts-with? l "^")))))
        bs (vec (concat (filter boundary? (range 1 (inc (count lines)))) [(inc (count lines))]))
        ;; group boundaries into chunks by size
        segs (partition 2 1 bs)
        seg-size (fn [[a b]] (reduce + (map #(inc (count (nth lines (dec %)))) (range a b))))
        groups (loop [ss segs cur nil sz 0 out []]
                 (if (empty? ss)
                   (if cur (conj out cur) out)
                   (let [[a b] (first ss) z (seg-size [a b])]
                     (if (and cur (> (+ sz z) chunk-bytes))
                       (recur ss nil 0 (conj out cur))
                       (recur (rest ss) (if cur [(first cur) b] [a b]) (+ sz z) out)))))]
    (mapv (fn [[a b]]
            {:text (str (str/join "\n" (subvec lines (dec a) (dec b))) "\n")
             :line-off (dec a)
             :forms (filterv #(let [l (:line (meta %))] (and (>= l a) (< l b))) forms)})
          groups)))

(defn group-by-size [srcs]
  (loop [xs srcs cur [] sz 0 out []]
    (if (empty? xs)
      (if (seq cur) (conj out cur) out)
      (let [z (count (pr-str (first xs)))]
        (if (and (seq cur) (> (+ sz z) 40000))
          (recur xs [] 0 (conj out cur))
          (recur (rest xs) (conj cur (first xs)) (+ sz z) out))))))

(defn check-file [path]
  (let [src (str (.readFileSync fs path "utf8"))
        forms (hr/read-forms src)
        p (js/parseInt (or (.-P js/process.env) "1"))
        sel (js/parseInt (or (.-SEL js/process.env) "0"))
        cs0 (vec (keep-indexed (fn [i c] (when (= sel (mod i p)) c)) (chunks src forms)))
        srcs (mapv :text cs0)
        offs (mapv (constantly 0) cs0)
        cs (mapv #(vec (hr/read-forms %)) srcs)
        t0 (js/Date.now)
        outs (vec (mapcat #(run-batch "hash-of" %) (group-by-size srcs)))
        results (map (fn [c out off txt]
                       (let [[hs err at] (str/split out #"\|" 3)
                             got (vec (remove str/blank? (str/split hs #",")))
                             want (do (reset! line-off off) (mapv #(str (hh %)) c))]
                         {:n (count want) :off (subs txt 0 (min 70 (count txt))) :ok (= got want) :err err
                          :bad (vec (keep-indexed (fn [i [g w]] (when (not= g w) i)) (map vector got want)))
                          :ngot (count got)
                          :first (first c)}))
                     cs outs offs srcs)]
    {:file path :sel sel :forms (reduce + (map count cs)) :chunks (count cs)
     :nodes (frequencies (mapcat (fn [r c] (when (:ok r) (mapcat tags c))) results cs))
     :agree (reduce + (map #(- (:n %) (count (:bad %))) (filter #(= (:n %) (:ngot %)) results)))
     :bad (vec (mapcat (fn [r] (when-not (:ok r) [(select-keys r [:n :off :ngot :bad :err])])) results))
     :ms (- (js/Date.now) t0)}))

(defn dbg2 [p idx]
  (let [src (str (.readFileSync fs p "utf8")) forms (hr/read-forms src) c (nth (chunks src forms) idx)]
    (println "line-off" (:line-off c) "hostforms" (count (:forms c)) "bytes" (count (:text c)))
    (println (pr-str (map (fn [f] [(:line (meta f)) (:column (meta f)) (str (first f)) (str (second f))]) (:forms c))))
    (.writeFileSync fs "/tmp/chunk.txt" (:text c))))
(when (= "dbg2" (first *command-line-args*)) (dbg2 (second *command-line-args*) (js/parseInt (nth *command-line-args* 2))) (js/process.exit 0))
(defn dbg [p] (let [src (str (.readFileSync fs p "utf8")) forms (hr/read-forms src)] (println :a) (println (pr-str (map hh forms))) (println :b) (let [cs (chunks src forms)] (println (count cs)) (println (pr-str (mapv :text cs))))))
(when (= "dbg" (first *command-line-args*)) (dbg (second *command-line-args*)) (js/process.exit 0))
(doseq [p *command-line-args*]
  (println (pr-str (try (check-file p) (catch :default e (js/console.error (.-stack e)) {:file p :error (str e)})))))
