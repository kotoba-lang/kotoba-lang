(ns num.quantized
  "Packed GGML inference matrices that remain quantized through matmul."
  (:require [num.array :as arr]
            [num.protocol :as p]))

(defrecord QuantizedMatrix [backend handle shape quant-type byte-count block-size])
(defrecord QuantizedTable [backend handle shape quant-type byte-count block-size])

(def ^:private layouts
  {:q5-0 {:block-size 32 :bytes-per-block 22}
   :q4-k {:block-size 256 :bytes-per-block 144}
   :q6-k {:block-size 256 :bytes-per-block 210}
   :q8-0 {:block-size 32 :bytes-per-block 34}})

(defn- validate-packed! [backend bytes [rows cols :as shape] quant-type]
  (let [{:keys [block-size bytes-per-block]} (layouts quant-type)]
    (when-not (and (= 2 (count shape)) block-size bytes-per-block
                   (pos-int? rows) (pos-int? cols)
                   (if (= quant-type :q5-0)
                     (zero? (mod (* rows cols) block-size))
                     (zero? (mod cols block-size)))
                   (= (count bytes) (* (quot (* rows cols) block-size)
                                       bytes-per-block))
                   (satisfies? p/IQuantizedOps backend))
      (throw (ex-info "invalid or unsupported packed quantized tensor"
                      {:shape shape :quant-type quant-type :bytes (count bytes)})))
    block-size))

(defn matrix
  "Upload GGML row-quantized bytes. `source-shape` is GGML's logical `[out,in]`;
  the returned matrix presents `[in,out]` so it composes with `num.core/matmul`."
  [backend bytes source-shape quant-type]
  (let [[out in] source-shape
        block-size (validate-packed! backend bytes source-shape quant-type)]
    (->QuantizedMatrix backend
                       (p/-quantized-from-host backend bytes
                                                {:quant-type quant-type
                                                 :rows out :cols in})
                       [in out] quant-type (count bytes) block-size)))

(defn table
  "Upload a row-quantized `[rows,features]` lookup table without decoding it."
  [backend bytes shape quant-type]
  (let [block-size (validate-packed! backend bytes shape quant-type)]
    (->QuantizedTable backend
                      (p/-quantized-from-host backend bytes
                                               {:quant-type quant-type
                                                :rows (first shape)
                                                :cols (second shape)})
                      shape quant-type (count bytes) block-size)))

(defn matrix? [value] (instance? QuantizedMatrix value))
(defn table? [value] (instance? QuantizedTable value))

(defn as-matrix
  "Zero-copy tied-weight view of `[rows,features]` table as `[features,rows]`."
  [table]
  (when-not (table? table)
    (throw (ex-info "as-matrix requires a quantized table" {})))
  (let [[rows features] (:shape table)]
    (->QuantizedMatrix (:backend table) (:handle table) [features rows]
                       (:quant-type table) (:byte-count table) (:block-size table))))

(defn matmul
  "Multiply dense f32 `input` by packed `weight`, returning dense f32 output."
  [input weight]
  (let [[m k] (:shape input) [wk n] (:shape weight)]
    (when-not (and (matrix? weight) (= k wk)
                   (= (:backend input) (:backend weight))
                   (= :f32 (:dtype input :f32)))
      (throw (ex-info "quantized matmul dimensions/backend are incompatible"
                      {:input (:shape input) :weight (:shape weight)})))
    (assoc (arr/->NDArray
            (:backend input)
            (p/-quantized-matmul (:backend input) (:handle input) (:handle weight)
                                 {:quant-type (:quant-type weight)
                                  :m m :k k :n n
                                  :blocks-per-row (quot k (:block-size weight))})
            [m n])
           :dtype :f32)))

(defn embedding
  "Gather arbitrary-shaped f32 token indices from a packed table."
  [indices table]
  (when-not (and (table? table) (= (:backend indices) (:backend table))
                 (= :f32 (:dtype indices :f32)))
    (throw (ex-info "quantized embedding backend/dtype are incompatible"
                    {:indices (:shape indices) :table (:shape table)})))
  (let [[rows dim] (:shape table) count (arr/nelems (:shape indices))
        output-shape (conj (vec (:shape indices)) dim)]
    (assoc (arr/->NDArray
            (:backend indices)
            (p/-quantized-embedding
             (:backend indices) (:handle indices) (:handle table)
             {:quant-type (:quant-type table) :rows rows :dim dim :count count
              :total (* count dim) :blocks-per-row (quot dim (:block-size table))})
            output-shape) :dtype :f32)))

(defn release! [weight]
  (p/-free (:backend weight) (:handle weight))
  nil)
