(ns png.bytes
  "Portable read cursor over a sequence of unsigned byte values (0-255).
   Pure cljc. Self-contained copy of the primitive kasane.bytes provides —
   duplicated deliberately so this repo has zero kotoba-lang dependencies
   beyond org-ietf-deflate.")

(defn cursor [data]
  (let [v (vec data)]
    {:data v :len (count v) :pos (atom 0)}))

(defn pos  [c] @(:pos c))
(defn eof? [c] (>= @(:pos c) (:len c)))
(defn seek! [c p] (reset! (:pos c) p) c)
(defn skip! [c n] (swap! (:pos c) + n) c)

(defn u8! [c]
  (let [p @(:pos c)]
    (when (>= p (:len c)) (throw (ex-info "png.bytes EOF" {:pos p :len (:len c)})))
    (reset! (:pos c) (inc p))
    (nth (:data c) p)))

(defn read-bytes! [c n]
  (let [p @(:pos c) end (+ p n)]
    (when (> end (:len c)) (throw (ex-info "png.bytes EOF read-bytes" {:pos p :n n :len (:len c)})))
    (reset! (:pos c) end)
    (subvec (:data c) p end)))

(defn uint! [c n big?]
  (let [bs (read-bytes! c n)
        bs (if big? bs (reverse bs))]
    (reduce (fn [acc b] (+ (* acc 256) b)) 0 bs)))

(defn sint! [c n big?]
  (let [u    (uint! c n big?)
        bits (* 8 n)
        half (bit-shift-left 1 (dec bits))]
    (if (>= u half) (- u (bit-shift-left 1 bits)) u)))

(defn bytes->ascii [bs]
  (apply str (map char bs)))
