(ns num.sparse
  "CSR (compressed sparse row) matrices — the format the Krylov solvers in
  nagare-clj / kudaki-clj want for `A·x`. Backend-agnostic HOST data: `row-ptr`
  (length n-rows+1), `col-idx` and `vals` (length nnz). The CPU backend reads
  these arrays directly; a GPU backend uploads them to device buffers on first
  `spmv` and caches. The vector `x` is the only operand that is a device handle —
  so one CSR can feed any backend."
  )

(defn csr
  "Build a CSR matrix with `n-cols` columns from `rows`, a seq (length n-rows) of
  per-row `[[col val] …]` entries (ascending col within a row recommended).
  Returns {:n-rows :n-cols :nnz :row-ptr :col-idx :vals}."
  [n-cols rows]
  (let [rows (vec rows)
        nrows (count rows)
        nnz (reduce + (map count rows))
        row-ptr (int-array (inc nrows))
        col-idx (int-array nnz)
        vals (double-array nnz)]
    (loop [i 0 p 0]
      (if (< i nrows)
        (do (aset row-ptr i p)
            (recur (inc i)
                   (reduce (fn [pp [c v]]
                             (aset col-idx pp (int c))
                             (aset vals pp (double v))
                             (inc pp))
                           p (nth rows i))))
        (aset row-ptr nrows nnz)))
    {:n-rows nrows :n-cols n-cols :nnz nnz
     :row-ptr row-ptr :col-idx col-idx :vals vals}))

(defn dense->csr
  "Convenience: build CSR from a row-major dense `m×n` seq of doubles, dropping
  zeros. Useful for tests and small problems."
  [m n flat]
  (let [v (vec flat)]
    (csr n (for [i (range m)]
             (for [j (range n)
                   :let [x (double (nth v (+ (* i n) j)))]
                   :when (not (zero? x))]
               [j x])))))
