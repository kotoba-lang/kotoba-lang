;; Host side of the cst twin differential: amu's src/kotoba/compiler/refactor/cst.cljk (run by nbb/kbb) prints
;; the same lines probe/probe/cst_dump.kotoba prints for the twin. Offsets are converted from UTF-16 units (the
;; host's) to UTF-8 bytes (the twin's). Usage: kbb --classpath <amu>/src host_dump.cljs <file>...
(ns host-dump
  (:require ["node:fs" :as fs]
            [clojure.string :as str]
            [kotoba.compiler.refactor.cst :as cst]))

(defn byte-offsets
  "Vector v with (v u) = the UTF-8 byte offset of UTF-16 unit u (one past the end included)."
  [s]
  (let [n (count s)]
    (loop [u 0 b 0 acc (transient [])]
      (if (>= u n)
        (persistent! (conj! acc b))
        (let [c (.charCodeAt s u)]
          (cond
            (and (>= c 0xD800) (<= c 0xDBFF) (< (inc u) n))
            (recur (+ u 2) (+ b 4) (-> acc (conj! b) (conj! (+ b 2))))
            (< c 0x80) (recur (inc u) (+ b 1) (conj! acc b))
            (< c 0x800) (recur (inc u) (+ b 2) (conj! acc b))
            :else (recur (inc u) (+ b 3) (conj! acc b))))))))

(defn kw [k] (if k (name k) "nil"))
(defn orq [s] (if (or (nil? s) (= s "")) "-" s))

(defn dump [path]
  (let [src (.toString (fs/readFileSync path) "utf8")
        bo (byte-offsets src)
        B (fn [u] (if (nil? u) -1 (nth bo u)))
        out (transient [])
        parsed (try {:nodes (cst/parse src)} (catch :default e {:err (ex-message e) :at (:at (ex-data e))}))]
    (conj! out (str "E " (orq (:err parsed)) " " (if (:err parsed) (B (:at parsed)) -1)))
    (when-not (:err parsed)
      (let [ids (atom {}) k (atom 0)]
        (cst/walk
         (fn [n {:keys [region path index]}]
           (let [me @k
                 ;; under an rc node the path ends [.. rc feature-key]: the key is an atom, a real parent never is
                 lastp (last path)
                 key (when (and lastp (contains? #{:sym :kw :num :str :char :regex} (:type lastp))) lastp)
                 parent (if key (nth path (- (count path) 2)) lastp)
                 pv (if parent (get @ids parent -1) -1)
                 fv (when (cst/call? n "defn") (cst/->fn-view n))]
             (swap! ids assoc n me) (swap! k inc)
             (conj! out (str/join " " ["V" me pv (kw region) index (kw (:type n)) (B (:s n)) (B (:e n))
                                       (orq (:prefix n)) (if (:splice? n) "1" "0")
                                       (orq (cst/head-text n)) (orq (cst/call-head-text n))
                                       (kw (:type (cst/strip-meta n)))
                                       (if key (:text key) "-")
                                       (count (cst/args n)) (count (cst/rc-branches n))
                                       (if fv (count (:kids fv)) "-")]))
             nil))
         (:nodes parsed))))
    (persistent! out)))

(doseq [p *command-line-args*]
  (println (str/join "\n" (dump p))))
