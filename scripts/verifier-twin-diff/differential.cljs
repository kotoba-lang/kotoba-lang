;; Differential driver: the guest twin (KIR interpreter over the linked project) vs the host verifier.
;; env: KROOTS, CORPUS (dir), MUTANTS (n per base), SEED, ONLY (regex on file name), MODE (program|artifact|both)
(ns vdrive2
  (:require ["node:fs" :as fs]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [kotoba.sema :as sema]
            [kotoba.kir :as kir]
            [kotoba.compiler.project :as project]
            [kotoba.compiler.project-files :as pf]
            ["node:crypto" :as crypto]
            [kotoba.artifact.core :as artifact]
            [kotoba.verifier :as verifier]))

(def env (fn [k d] (or (aget js/process.env k) d)))
(def roots (vec (str/split (env "KROOTS" "") #":")))
(def corpus (env "CORPUS" "/private/tmp/corpus"))
(def mutants (js/parseInt (env "MUTANTS" "0")))
(def only (re-pattern (env "ONLY" ".")))
(def mode (env "MODE" "both"))
(def probe-root "/private/tmp/scratch/vd2_probe.cljk")

(def probe-src
  (str "(ns probe.vd2\n  {:kotoba/export [main program-msg artifact-msg structure-msg]}\n"
       "  (:require [kotoba.verifier.program :as vp] [kotoba.verifier :as v] [kotoba.form :as form])\n"
       "  (:schemas {:form/r [:record :form/r [[:tag :i64] [:s :string] [:n :i64] [:k :keyword] [:kids [:list [:ref :form/r]]] [:span :i64] [:data :bytes]]]}))\n"
       "(defn program-msg [t :string] :string (vp/verify-program-message (form/edn-form t)))\n"
       "(defn artifact-msg [t :string] :string (v/verify-artifact-message (form/edn-form t)))\n"
       "(defn structure-msg [t :string] :string (v/verify-artifact-structure-message (form/edn-form t)))\n"
       "(defn main [] :string \"ok\")\n"))

(.writeFileSync fs probe-root probe-src)
(def kir-mod
  (let [graph (pf/load-closed-graph probe-root roots)
        linked (project/link-source (:sources graph) (:root graph))
        hir (sema/analyze (:source linked) {:admit-linked-synthetics? true})]
    (kir/lower hir)))
(println "probe compiled")

(defn twin [fname text]
  (try (kir/execute kir-mod (symbol fname) [text]
                    {:fuel 300000000 :frames 200000 :cells 2000000000 :bytes 2000000000
                     :typed-cap-call (fn [cap-id _ _ request]
                                       (if (= 3 (js/Number cap-id))
                                         (-> (crypto/createHash "sha256") (.update request "utf8") (.digest "hex"))
                                         (throw (ex-info "no capability" {:cap cap-id}))))})
       (catch :default e (str "TRAP:" (ex-message e)))))

(defn host-program [c]
  (try ((deref (resolve 'kotoba.verifier/verify-program!)) c) ""
       (catch :default e (or (ex-message e) (str e)))))
(def code-region-messages #{"native instruction stream rejected" "native export table rejected" "runtime KIR cannot be safely lowered"})
(declare host-artifact)
(defn host-structure [c]
  (let [m (host-artifact c)]
    (if (contains? code-region-messages m) :code-region m)))
(defn host-artifact [c]
  (try (verifier/verify-artifact! c) ""
       (catch :default e (or (ex-message e) (str e)))))

;; ---- PRNG + mutation -------------------------------------------------------------------
(def state (atom (js/parseInt (env "SEED" "1"))))
(defn rnd [n]
  (swap! state (fn [s] (bit-and (+ (* s 1103515245) 12345) 0x7fffffff)))
  (mod (quot @state 7) n))

(defn paths [x]
  (let [go (fn go [x p]
             (cons p
                   (cond
                     (map? x) (mapcat (fn [[k v]] (go v (conj p [:v k]))) x)
                     (vector? x) (mapcat (fn [i] (go (nth x i) (conj p [:i i]))) (range (count x)))
                     (or (seq? x) (list? x)) (mapcat (fn [i] (go (nth x i) (conj p [:i i]))) (range (count x)))
                     (set? x) []
                     :else [])))]
    (go x [])))

(defn get-at [x p]
  (reduce (fn [x [t k]] (if (= t :v) (get x k) (nth x k))) x p))

(defn update-at [x p f]
  (if (empty? p) (f x)
      (let [[t k] (first p)]
        (if (= t :v)
          (assoc x k (update-at (get x k) (rest p) f))
          (let [c (update-at (nth x k) (rest p) f)]
            (cond (vector? x) (assoc x k c)
                  :else (apply list (map-indexed (fn [i e] (if (= i k) c e)) x))))))))

(def atoms [0 1 -1 2 255 256 9999 :i64 :bool :string :f64 :main true false nil 'x 'main '+ 'if "s" :kotoba.kir/v3 :kotoba.kir/v4 :state [:cap/call 3] []])

(defn mutate [x]
  (let [ps (vec (paths x))
        p (nth ps (rnd (count ps)))
        n (get-at x p)]
    (case (rnd 6)
      0 (update-at x p (fn [_] (nth atoms (rnd (count atoms)))))
      1 (if (empty? p) x
            (let [pp (vec (butlast p)) [t k] (last p)]
              (update-at x pp (fn [par]
                                (cond (map? par) (dissoc par k)
                                      (vector? par) (vec (concat (subvec par 0 k) (subvec par (inc k))))
                                      (seq? par) (apply list (concat (take k par) (drop (inc k) par)))
                                      :else par)))))
      2 (if (empty? p) x
            (let [pp (vec (butlast p)) [t k] (last p)]
              (update-at x pp (fn [par]
                                (cond (vector? par) (vec (concat (subvec par 0 (inc k)) [(nth par k)] (subvec par (inc k))))
                                      (seq? par) (apply list (concat (take (inc k) par) [(nth par k)] (drop (inc k) par)))
                                      :else par)))))
      3 (if (integer? n) (update-at x p (fn [v] (+ v (if (zero? (rnd 2)) 1 -1)))) x)
      4 (if (keyword? n) (update-at x p (fn [_] (nth [:i64 :bool :string :f64 :vector-i64 :bytes :slice] (rnd 7)))) x)
      5 (if (and (seq? n) (pos? (count n))) (update-at x p (fn [l] (apply list (concat l [(nth atoms (rnd (count atoms)))])))) x))))

;; ---- run ------------------------------------------------------------------------------------
(def shard (let [x (env "SHARD" "0/1")] (mapv js/parseInt (str/split x #"/"))))
(def files (->> (js->clj (.readdirSync fs corpus)) (sort-by (fn [f] (.-size (.statSync fs (str corpus "/" f))))) (filter #(re-find only %))
                (keep-indexed (fn [i f] (when (= (mod i (second shard)) (first shard)) f)))))
(def tally (atom {}))
(declare compare-1!)
(defn tally! [k] (swap! tally update k (fnil inc 0)))
(def shown (atom 0))

(def maxlen (js/parseInt (env "MAXLEN" "1000000")))
(defn compare! [label fname host-fn c]
  (if (> (count (binding [*print-namespace-maps* false] (pr-str c))) maxlen)
    (tally! :skipped-too-long)
    (compare-1! label fname host-fn c)))
(defn compare-1! [label fname host-fn c]
  (let [text (binding [*print-namespace-maps* false] (pr-str c))
        _ (when (env "TRACE" nil) (println "case" label (count text)))
        t0 (js/Date.now)
        want (host-fn c)
        t1 (js/Date.now)
        got (twin fname text)
        _ (when (env "TRACE" nil) (println "   host ms" (- t1 t0) "twin ms" (- (js/Date.now) t1)))
        k (cond (= want :code-region) :host-code-region-skipped
                (= got want) (if (= "" want) :agree-accept :agree-reject-same)
                (and (= "" want) (not= "" got)) :DISAGREE-twin-rejects
                (and (not= "" want) (= "" got)) :DISAGREE-twin-accepts
                :else :agree-reject-diff-msg)]
    (tally! k)
    (when (and (contains? #{:DISAGREE-twin-rejects :DISAGREE-twin-accepts :agree-reject-diff-msg} k) (< @shown 40))
      (swap! shown inc)
      (println (name k) label "\n   host:" (pr-str want) "\n   twin:" (pr-str got)))))

(doseq [f files]
  (let [c (edn/read-string (.readFileSync fs (str corpus "/" f) "utf8"))
        art? (and (map? c) (= :kotoba.kexe/v1 (:format c)))
        prog (if art? (:program c) c)]
    (when (and art? (#{"artifact" "both"} mode))
      ;; structure mode vs host: a host rejection at the code region or oracle is not structural
      (compare! (str f " (structure)") "structure-msg"
                host-structure
                c)
      (dotimes [i (min mutants (js/parseInt (env "ART_MUTANTS" "2")))]
        (let [m (mutate c)
              ;; re-seal (usually) so the mutation reaches the structural checks and not just the seal
              m (if (and (map? m) (map? (:program m)) (< (rnd 8) 7))
                  (artifact/seal (assoc m :kir-sha256 (artifact/sha256 (:program m))))
                  m)]
          (compare! (str f " artifact mutant " i) "structure-msg"
                    host-structure
                    m))))
    (when (#{"program" "both"} mode)
      (compare! (str f " (program)") "program-msg" host-program prog)
      (dotimes [i mutants]
        (compare! (str f " program mutant " i) "program-msg" host-program (mutate prog))))))
(println "TALLY" (pr-str @tally))
