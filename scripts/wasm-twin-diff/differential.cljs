;; Differential driver: the guest twin of kotoba.wasm.core (lang/compat/kotoba/wasm/core.kotoba, run on the
;; KIR interpreter after a project link) vs the host emitter (kotoba.wasm.core/emit), byte for byte.
;; env: KROOTS (source roots incl. lang/compat), CORPUS (dir of .edn KIR maps), RANDOM (n generated pure
;;      programs), SEED, TARGET (default :wasm32-kotoba-v1), FUEL (default 512 -> host {} opts)
(ns wdiff
  (:require ["node:fs" :as fs]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [kotoba.sema :as sema]
            [kotoba.kir :as kir]
            [kotoba.compiler.project :as project]
            [kotoba.compiler.project-files :as pf]
            [kotoba.wasm.core :as host]))

(def env (fn [k d] (or (aget js/process.env k) d)))
(def roots (vec (str/split (env "KROOTS" "") #":")))
(def corpus (env "CORPUS" "/private/tmp/wasm-corpus"))
(def nrandom (js/parseInt (env "NRANDOM" "0")))
(def fuel (js/parseInt (env "FUEL" "512")))
(def probe-root "/private/tmp/scratch/wd_probe.cljk")

(def probe-src
  (str "(ns probe.wd\n  {:kotoba/export [main emit-512 emit-fuel]}\n"
       "  (:require [kotoba.wasm.core :as core] [kotoba.form :as form])\n"
       "  (:schemas {:form/r [:record :form/r [[:tag :i64] [:s :string] [:n :i64] [:k :keyword] [:kids [:list [:ref :form/r]]] [:span :i64] [:data :bytes]]]}))\n"
       "(defn emit-512 [t :string] :bytes (core/emit (form/edn-form t) :wasm32-kotoba-v1))\n"
       "(defn emit-fuel [t :string f :i64] :bytes (core/emit-with-fuel (form/edn-form t) :wasm32-kotoba-v1 f))\n"
       "(defn main [] :string \"ok\")\n"))
(.writeFileSync fs probe-root probe-src)
(def kir-mod
  (let [graph (pf/load-closed-graph probe-root roots)
        linked (project/link-source (:sources graph) (:root graph))
        hir (sema/analyze (:source linked) {:admit-linked-synthetics? true})]
    (kir/lower hir)))
(println "probe compiled")

(defn ->hex [b] (str/join (map #(.padStart (.toString (bit-and % 255) 16) 2 "0") (vec (js/Array.from b)))))
(defn twin [text]
  (try (let [r (kir/execute kir-mod (if (= fuel 512) 'emit-512 'emit-fuel) (if (= fuel 512) [text] [text fuel])
                            {:fuel 3000000000 :frames 400000 :cells 2000000000 :bytes 2000000000})]
         (if (or (instance? js/Uint8Array r) (.-buffer r)) (->hex r) (str "ODD:" (pr-str r))))
       (catch :default e (str "TRAP:" (ex-message e)))))
(defn host-emit [k]
  (try (->hex (host/emit k :wasm32-kotoba-v1 (if (= fuel 512) {} {:fuel fuel})))
       (catch :default e (str "HOSTERR:" (ex-message e)))))

;; ---- random pure programs ---------------------------------------------------------------------
(def state (atom (js/parseInt (env "SEED" "1"))))
(defn rnd [n] (swap! state (fn [s] (bit-and (+ (* s 1103515245) 12345) 0x7fffffff))) (mod (quot @state 7) n))
(def binops '[+ - * quot bit-and bit-or bit-xor])
(def cmps '[= < > <= >=])
(def lits [0 1 -1 2 7 63 64 127 128 255 -64 -65 8191 8192 1000000 -1000000 2147483647 -2147483648 4294967296 9007199254740991 -9007199254740991])
(defn gen [depth vars fns]
  (let [r (rnd (if (<= depth 0) 3 14))]
    (case r
      0 (nth lits (rnd (count lits)))
      1 (if (seq vars) (nth vars (rnd (count vars))) (nth lits (rnd (count lits))))
      2 (nth [true false] (rnd 2))
      3 (list (nth binops (rnd 7)) (gen (dec depth) vars fns) (gen (dec depth) vars fns))
      4 (list (nth cmps (rnd 5)) (gen (dec depth) vars fns) (gen (dec depth) vars fns))
      5 (list 'if (gen (dec depth) vars fns) (gen (dec depth) vars fns) (gen (dec depth) vars fns))
      6 (let [v (symbol (str "v" (count vars)))]
          (list 'let [v (gen (dec depth) vars fns)] (gen (dec depth) (conj vars v) fns)))
      7 (list 'do (gen (dec depth) vars fns) (gen (dec depth) vars fns))
      8 (list (nth '[min max] (rnd 2)) (gen (dec depth) vars fns) (gen (dec depth) vars fns))
      9 (list (nth '[i32-wrap u32-wrap bit-not -] (rnd 4)) (gen (dec depth) vars fns))
      10 (list (nth '[i32-wrapping-add i32-wrapping-mul i32-xor i64-shift-left i64-shift-right u64-shift-right
                     i32-shift-left i32-shift-right u32-shift-right] (rnd 9))
               (gen (dec depth) vars fns) (gen (dec depth) vars fns))
      11 (if (seq fns)
           (let [[f n] (nth fns (rnd (count fns)))] (apply list f (repeatedly n #(gen (dec depth) vars fns))))
           0)
      12 (list 'let (vec (mapcat (fn [i] [(symbol (str "w" i "_" depth)) (gen (dec depth) vars fns)]) (range (inc (rnd 3)))))
               (gen (dec depth) vars fns))
      13 (list (nth '[cap-call typed-cap-call pair pair-first pair-second] (rnd 5)) 3 (gen (dec depth) vars fns)))))
(defn fix-heap [x]
  ;; give the special forms their real shapes
  (cond
    (and (seq? x) (= 'cap-call (first x))) (list 'cap-call (nth lits (rnd 5)) (fix-heap (nth x 2 0)))
    (and (seq? x) (= 'typed-cap-call (first x))) (list 'typed-cap-call 3 :i64 :i64 (fix-heap (nth x 2 0)))
    (and (seq? x) (= 'pair (first x))) (list 'pair (fix-heap (nth x 2 0)) 1)
    (and (seq? x) (contains? '#{pair-first pair-second} (first x))) (list (first x) (fix-heap (nth x 2 0)))
    (seq? x) (apply list (map fix-heap x))
    (vector? x) (vec (map fix-heap x))
    :else x))
(defn gen-program []
  (let [nf (inc (rnd 4))
        names (mapv #(symbol (str "f" %)) (range nf))
        sigs (mapv (fn [i] [(nth names i) (rnd 4)]) (range nf))
        funs (vec (for [i (range nf)]
                    (let [[nm n] (nth sigs i) params (mapv #(symbol (str "p" %)) (range n))
                          callable (subvec sigs 0 i)]
                      {:name nm :params params :result :i64 :effects #{}
                       :body (if (zero? (rnd 6)) (gen 0 params callable) (fix-heap (gen (+ 1 (rnd 4)) params callable)))})))
        loop? (zero? (rnd 3))
        funs (if loop?
               (let [ln (symbol (str "__kotoba_loop_" (rnd 50)))]
                 (conj funs {:name ln :params '[i acc n] :result :i64 :effects #{}
                             :body (list 'if '(= i n) 'acc
                                         (list ln '(+ i 1) (list (nth binops (rnd 7)) 'acc (gen 1 '[i acc n] [])) 'n))}
                       {:name 'driver :params '[n] :result :i64 :effects #{}
                        :body (list ln 0 (nth lits (rnd 5)) 'n)}))
               funs)]
    {:format (nth [:kotoba.kir/v2 :kotoba.kir/v3] (rnd 2))
     :entry (:name (peek funs))
     :exports (if (zero? (rnd 3)) (vec (take (inc (rnd (count funs))) (map :name funs))) (mapv :name funs))
     :effects #{}
     :functions funs}))

(def tally (atom {}))
(defn tally! [k] (swap! tally update k (fnil inc 0)))
(def shown (atom 0))
(defn compare! [label k]
  (let [text (binding [*print-namespace-maps* false] (pr-str k))
        want (host-emit k)
        got (twin text)
        c (cond (str/starts-with? want "HOSTERR:") (if (str/starts-with? got "TRAP:") :agree-both-refuse :host-error-twin-emitted)
                (= got want) :AGREE-BYTES
                (str/starts-with? got "TRAP:") :twin-refused-host-emitted
                :else :DISAGREE-bytes)]
    (tally! c)
    (when (and (#{:DISAGREE-bytes :twin-refused-host-emitted :host-error-twin-emitted} c) (< @shown 12))
      (swap! shown inc)
      (let [d (first (filter #(not= (get want %) (get got %)) (range (max (count want) (count got)))))
            w (max 0 (- (or d 0) 40))]
        (println (name c) label "\n  kir :" (subs text 0 (min 400 (count text)))
                 "\n  first diff at hex" d "of" (count want) (count got)
                 "\n  host:" (subs want w (min (count want) (+ w 100))) "\n  twin:" (subs got w (min (count got) (+ w 100))))))))

(doseq [f (sort (js->clj (.readdirSync fs corpus)))]
  (when (str/ends-with? f ".edn")
    (compare! f (edn/read-string (.readFileSync fs (str corpus "/" f) "utf8")))))
(dotimes [i nrandom] (compare! (str "random-" i) (gen-program)))
(println "TALLY" (pr-str @tally))
