(ns erasure.matrix
  "The parity matrix and the linear solve behind global repair.

  **Cauchy, not Vandermonde.** The global parities are rows of a Cauchy
  matrix `C[i][j] = 1 / (x_i + y_j)` over disjoint element sets
  `x_i = k + i` and `y_j = j`. Every square submatrix of a Cauchy matrix is
  nonsingular *by construction*, which is what makes the decode below total:

    Let S be the surviving data indices (contributing identity rows) and T
    the surviving global-parity rows, with |S| + |T| = k. The identity rows
    select out the coordinates in S, so the determinant of the k x k system
    reduces to that of the |T| x |T| submatrix of C on rows T and the columns
    *missing* from S — a square Cauchy submatrix, hence nonsingular.

  So `[I_k ; C]` is MDS: ANY k of its k+g rows invert. A Vandermonde matrix
  put into systematic form loses that guarantee for some parameter sets, and
  the failure is silent — a decode that works on every pattern you tested and
  not on the one you get. Cauchy costs one extra inversion per entry at setup
  and nothing at all afterwards.

  Element sets stay disjoint because `x_i = k + i >= k > j = y_j`, so
  `x_i + y_j` (xor) is never zero. The construction is valid while
  `k + g <= 256`; `erasure.lrc/layout` enforces that."
  (:require [erasure.gf :as gf]))

(defn cauchy-entry
  "Coefficient applied to data shard `j` when forming global parity `i`."
  [k i j]
  (gf/inv (bit-xor (+ k i) j)))

(defn cauchy-rows
  "The `g` x `k` global-parity coefficient matrix."
  [k g]
  (mapv (fn [i] (mapv (fn [j] (cauchy-entry k i j)) (range k)))
        (range g)))

(defn identity-row
  "The row of `I_k` that reproduces data shard `j`."
  [k j]
  (mapv (fn [c] (if (= c j) 1 0)) (range k)))

(defn invert
  "Gauss-Jordan inverse of a square matrix over GF(2^8).

  Returns `nil` for a singular matrix rather than throwing. A caller that
  built its rows from `[I_k ; C]` can treat `nil` as impossible; a caller
  feeding in arbitrary rows gets a value it can branch on."
  [m]
  (let [n (count m)]
    (loop [a (vec (map-indexed
                   (fn [i row]
                     (into (vec row) (assoc (vec (repeat n 0)) i 1)))
                   m))
           col 0]
      (if (= col n)
        (mapv #(subvec % n) a)
        (if-let [piv (first (filter #(not (zero? (get-in a [% col])))
                                    (range col n)))]
          (let [a (if (= piv col) a (assoc a col (nth a piv) piv (nth a col)))
                scale (gf/inv (get-in a [col col]))
                a (assoc a col (mapv #(gf/mul scale %) (nth a col)))
                pivot-row (nth a col)
                a (vec (map-indexed
                        (fn [r row]
                          (let [f (nth row col)]
                            (if (or (= r col) (zero? f))
                              row
                              (mapv (fn [x y] (bit-xor x (gf/mul f y)))
                                    row pivot-row))))
                        a))]
            (recur a (inc col)))
          nil)))))

(defn apply-row
  "Combine `shards` under one row of coefficients into a single shard.

  `shards` and `coeffs` are positionally aligned. This is where every
  multiply in an encode or a global decode happens, and it delegates each one
  to `gf/scale-add` — the single function a host provider is allowed to
  replace."
  [coeffs shards len]
  (reduce (fn [acc [c s]] (gf/scale-add acc c s))
          (gf/zero-shard len)
          (map vector coeffs shards)))

;; --- rank-based survivor selection ----------------------------------------
;;
;; EVERY shard is a linear function of the data, local parities included: the
;; local parity of group q is the sum of that group's identity rows. Leaving
;; them out of a global solve throws away real equations. Measured on
;; k=16/r=4/g=6: excluding them makes the code fail at 7 erasures; including
;; them it survives patterns that a data-and-global-only solve calls dead.
;;
;; The price is that "at least k survivors" stops being sufficient. Identity
;; and Cauchy rows alone are independent by the Cauchy argument above; throw
;; local-parity rows into the pool and a k-subset can be dependent (a group's
;; local parity is redundant once all of its data survives). So the selection
;; below does not count, it ranks: rows are folded into a reduced basis in
;; ascending index order and kept only when they raise the rank.

(defn- leading-index
  "Column of the first non-zero coefficient, or nil for a zero row."
  [row]
  (first (keep-indexed (fn [i v] (when-not (zero? v) i)) row)))

(defn independent-rows
  "Pick shard indices from `candidates` (ascending) whose generator rows are
  linearly independent, until `k` of them are held.

  `row-of` maps a shard index to its generator row. Returns the chosen
  indices, or `nil` when the candidates do not span the data space — which is
  the exact, not approximate, test for whether an erasure pattern is
  recoverable."
  [row-of k candidates]
  (loop [cands (seq candidates)
         basis {}
         picked []]
    (cond
      (= (count picked) k) picked
      (nil? cands) nil
      :else
      (let [s (first cands)
            reduced (loop [r (row-of s)]
                      (let [piv (leading-index r)]
                        (if (and piv (contains? basis piv))
                          (let [f (nth r piv)]
                            (recur (mapv (fn [x y] (bit-xor x (gf/mul f y)))
                                         r (get basis piv))))
                          r)))
            piv (leading-index reduced)]
        (if piv
          (let [scale (gf/inv (nth reduced piv))]
            (recur (next cands)
                   (assoc basis piv (mapv #(gf/mul scale %) reduced))
                   (conj picked s)))
          (recur (next cands) basis picked))))))
