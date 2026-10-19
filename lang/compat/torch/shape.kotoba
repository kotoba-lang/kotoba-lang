(ns torch.shape
  "Pure shape & parameter semantics for built-in layer types — no tensor math,
  no I/O. Shapes are batch-agnostic feature shapes (a vector of dims, e.g.
  `[784]` for a flat feature, `[3 32 32]` for a CHW image). Each built-in is a
  pure function from (args, in-shape) to either `[:ok out-shape]` or
  `[:error msg]`; nothing throws on bad user data.

  Hosts extend the layer vocabulary through `torch.ports/ILayer`; this namespace
  is the default vocabulary that `default-ports` delegates to. `layer-shape` and
  `layer-params` are multimethods so a host *may* also `defmethod` new built-ins
  in-process, but the supported, portable path is the protocol."
  (:require [clojure.string :as str]))

(defn- prod [xs] (reduce * 1 xs))

(defn- nth-arg
  "Positional arg `i` from `args` (a vector), or `default`."
  [args i default]
  (if (and (vector? args) (> (count args) i)) (nth args i) default))

(defn- pair-value [value]
  (let [pair (if (sequential? value) (vec value) [value value])]
    (when (and (= 2 (count pair)) (every? integer? pair)) pair)))

(defn- conv-out
  "PyTorch floor output size, including dilation, or nil if it underflows."
  [dim k stride pad dilation]
  (let [effective-k (inc (* dilation (dec k)))
        n (+ (quot (- (+ dim (* 2 pad)) effective-k) stride) 1)]
    (when (pos? n) n)))

;; ---------------------------------------------------------------------------
;; layer-shape : (ltype args in-shape) -> [:ok out] | [:error msg]
;; ---------------------------------------------------------------------------

(defmulti layer-shape
  "Output shape for a built-in layer. Returns `[:ok out-shape]` or
  `[:error msg]`."
  (fn [ltype _args _in] ltype))

(defmethod layer-shape :default [ltype _ _]
  [:error (str "unknown layer type: " ltype)])

;; --- elementwise / activation (shape-preserving) ---------------------------

(doseq [t [:relu :silu :gelu :sigmoid :tanh :softmax :dropout :identity]]
  (defmethod layer-shape t [_ _ in] [:ok in]))

;; --- parameter-free, self-attention over [sequence embedding] --------------
;; :attention [num-heads?] — num-heads defaults to 1 (single-head, the
;; original shape this method supported); `args` may be `{}` (the zero-arg
;; `torch.model/attention` constructor's stored form) or `[num-heads]`.

(defmethod layer-shape :attention [_ args in]
  (let [num-heads (nth-arg args 0 1)]
    (cond
      (not= 2 (count in))
      [:error (str "attention expects a [sequence embedding] input, got " in)]

      (not (every? pos-int? in))
      [:error "attention expects positive sequence and embedding dimensions"]

      (not (pos-int? num-heads))
      [:error "attention num-heads must be a positive int"]

      (not (zero? (mod (long (second in)) (long num-heads))))
      [:error (str "attention num-heads " num-heads " must evenly divide embedding "
                   (second in))]

      :else [:ok in])))

(defmethod layer-shape :multihead-attention [_ args in]
  (let [[embed-dim num-heads] args]
    (cond
      (not (#{2 3} (count in)))
      [:error (str "multihead-attention expects [sequence embedding] or "
                   "[batch sequence embedding], got " in)]
      (not (and (pos-int? embed-dim) (pos-int? num-heads)))
      [:error "multihead-attention expects positive embed-dim and num-heads"]
      (not= embed-dim (last in))
      [:error (str "multihead-attention embed-dim " embed-dim
                   " differs from input " (last in))]
      (not (zero? (mod embed-dim num-heads)))
      [:error (str "multihead-attention num-heads " num-heads
                   " must divide embed-dim " embed-dim)]
      :else [:ok in])))

(defmethod layer-shape :llama-block [_ args in]
  (let [[embed heads hidden opts] args
        kv-heads (or (:kv-heads opts) heads)]
    (cond
      (not (#{2 3} (count in)))
      [:error "llama-block expects [sequence embed] or [batch sequence embed]"]
      (not (every? pos-int? [embed heads hidden kv-heads]))
      [:error "llama-block dimensions must be positive integers"]
      (not= embed (last in)) [:error "llama-block embed dimension mismatch"]
      (not (zero? (mod embed heads))) [:error "llama-block heads must divide embed"]
      (not (zero? (mod heads kv-heads))) [:error "llama-block kv-heads must divide heads"]
      (odd? (quot embed heads)) [:error "llama-block head dimension must be even"]
      :else [:ok in])))

(defmethod layer-shape :lm-head [_ args in]
  (let [[embed vocab] args]
    (cond
      (not (and (pos-int? embed) (pos-int? vocab)))
      [:error "lm-head expects positive embed and vocabulary dimensions"]
      (empty? in) [:error "lm-head expects rank >= 1"]
      (not= embed (last in)) [:error "lm-head embed dimension mismatch"]
      :else [:ok (conj (vec (butlast in)) vocab)])))

;; --- linear ----------------------------------------------------------------

(defmethod layer-shape :linear [_ args in]
  (let [in-f  (nth-arg args 0 nil)
        out-f (nth-arg args 1 nil)]
    (cond
      (not (and (pos-int? in-f) (pos-int? out-f)))
      [:error "linear expects [in out] positive ints"]

      (empty? in)
      [:error "linear got a rank-0 (empty) input shape"]

      (not= (last in) in-f)
      [:error (str "linear in-features " in-f " ≠ input last dim " (last in))]

      :else
      [:ok (conj (vec (butlast in)) out-f)])))

;; --- flatten ---------------------------------------------------------------

(defmethod layer-shape :flatten [_ _ in]
  (if (empty? in)
    [:error "flatten got an empty input shape"]
    [:ok [(prod in)]]))

;; --- embedding : [num-embeddings dim], appends dim to a token-id shape ------

(defmethod layer-shape :embedding [_ args in]
  (let [dim (nth-arg args 1 nil)]
    (if (pos-int? dim)
      [:ok (conj (vec in) dim)]
      [:error "embedding expects [num-embeddings dim] positive ints"])))

;; --- norms (shape-preserving, but check feature dim) ------------------------

(defmethod layer-shape :batchnorm [_ _ in] [:ok in])

(defmethod layer-shape :layernorm [_ args in]
  (let [features (nth-arg args 0 nil)]
    (cond
      (not (pos-int? features)) [:error "layernorm expects a positive feature count"]
      (empty? in) [:error "layernorm expects rank >= 1"]
      (not= features (last in))
      [:error (str "layernorm features " features " differs from input " (last in))]
      :else [:ok in])))

(defmethod layer-shape :rmsnorm [_ args in]
  (let [features (nth-arg args 0 nil)]
    (cond
      (not (pos-int? features)) [:error "rmsnorm expects a positive feature count"]
      (empty? in) [:error "rmsnorm expects rank >= 1"]
      (not= features (last in))
      [:error (str "rmsnorm features " features " differs from input " (last in))]
      :else [:ok in])))

(defmethod layer-shape :groupnorm [_ args in]
  (let [groups (nth-arg args 0 nil)
        channels (nth-arg args 1 nil)]
    (cond
      (not= 3 (count in))
      [:error (str "groupnorm expects a [C H W] input, got " in)]
      (not (and (pos-int? groups) (pos-int? channels)))
      [:error "groupnorm expects [num-groups num-channels] positive ints"]
      (not= channels (first in))
      [:error (str "groupnorm channels " channels " ≠ input channels " (first in))]
      (not (zero? (mod channels groups)))
      [:error "groupnorm num-channels must be divisible by num-groups"]
      :else [:ok in])))

;; --- conv2d : [in-ch out-ch k stride? pad?] over a [C H W] shape ------------

(defmethod layer-shape :conv2d [_ args in]
  (let [in-ch  (nth-arg args 0 nil)
        out-ch (nth-arg args 1 nil)
        kernel (pair-value (nth-arg args 2 nil))
        stride (pair-value (nth-arg args 3 1))
        pad (pair-value (nth-arg args 4 0))
        dilation (pair-value (nth-arg args 5 1))
        groups (nth-arg args 6 1)]
    (cond
      (not= 3 (count in))
      [:error (str "conv2d expects a [C H W] input, got " in)]
      (not (and (pos-int? in-ch) (pos-int? out-ch)
                kernel (every? pos-int? kernel)))
      [:error "conv2d expects [in out k] positive ints"]
      (not (and stride (every? pos-int? stride)
                pad (every? #(and (integer? %) (not (neg? %))) pad)
                dilation (every? pos-int? dilation)))
      [:error "conv2d stride/dilation must be positive and padding non-negative"]
      (not (and (pos-int? groups) (zero? (mod in-ch groups))
                (zero? (mod out-ch groups))))
      [:error "conv2d groups must divide input and output channels"]
      (not= (first in) in-ch)
      [:error (str "conv2d in-channels " in-ch " ≠ input channels " (first in))]
      :else
      (let [[_ h w] in [kh kw] kernel [sh sw] stride [ph pw] pad [dh dw] dilation
            h' (conv-out h kh sh ph dh)
            w' (conv-out w kw sw pw dw)]
        (if (and h' w')
          [:ok [out-ch h' w']]
          [:error (str "conv2d kernel " kernel " too large for " [h w])])))))

;; --- pooling : [k stride? pad?] over [C H W] --------------------------------

(defn- pool-shape [args in]
  (let [k      (nth-arg args 0 nil)
        stride (nth-arg args 1 k)
        pad    (nth-arg args 2 0)]
    (cond
      (not= 3 (count in)) [:error (str "pool expects a [C H W] input, got " in)]
      (not (pos-int? k))  [:error "pool expects [k …] positive int"]
      :else
      (let [[c h w] in
            h' (conv-out h k stride pad 1)
            w' (conv-out w k stride pad 1)]
        (if (and h' w')
          [:ok [c h' w']]
          [:error (str "pool kernel " k " too large for " [h w])])))))

(defmethod layer-shape :maxpool2d [_ args in] (pool-shape args in))
(defmethod layer-shape :avgpool2d [_ args in] (pool-shape args in))

;; ---------------------------------------------------------------------------
;; layer-params : (ltype args) -> non-negative int (learnable parameter count)
;; ---------------------------------------------------------------------------

(defmulti layer-params (fn [ltype _args] ltype))
(defmethod layer-params :default [_ _] 0)

(defmethod layer-params :linear [_ args]
  (let [in (nth-arg args 0 0) out (nth-arg args 1 0)]
    (+ (* in out) out)))                       ; weight + bias

(defmethod layer-params :conv2d [_ args]
  (let [in (nth-arg args 0 0) out (nth-arg args 1 0)
        [kh kw] (or (pair-value (nth-arg args 2 0)) [0 0])
        groups (nth-arg args 6 1)]
    (+ (* out (quot in groups) kh kw) out)))

(defmethod layer-params :embedding [_ args]
  (* (nth-arg args 0 0) (nth-arg args 1 0)))

(defmethod layer-params :batchnorm [_ args] (* 2 (nth-arg args 0 0)))
(defmethod layer-params :layernorm [_ args] (* 2 (nth-arg args 0 0)))
(defmethod layer-params :rmsnorm [_ args] (nth-arg args 0 0))
(defmethod layer-params :groupnorm [_ args] (* 2 (nth-arg args 1 0)))
(defmethod layer-params :multihead-attention [_ args]
  (let [embed (nth-arg args 0 0)] (* 4 (+ (* embed embed) embed))))
(defmethod layer-params :llama-block [_ args]
  (let [embed (nth-arg args 0 0) heads (nth-arg args 1 1)
        hidden (nth-arg args 2 0) opts (nth-arg args 3 {})
        kv-heads (or (:kv-heads opts) heads)
        kv-embed (* kv-heads (quot embed heads))]
    (+ (* 2 embed) (* 2 embed embed) (* 2 embed kv-embed)
       (* 3 embed hidden))))
(defmethod layer-params :lm-head [_ args]
  (* (nth-arg args 0 0) (nth-arg args 1 0)))

(def built-in-types
  "The set of layer types this namespace understands."
  #{:linear :conv2d :maxpool2d :avgpool2d :embedding :batchnorm :layernorm :rmsnorm
    :groupnorm :dropout :flatten :relu :silu :gelu :sigmoid :tanh :softmax
    :attention :multihead-attention :llama-block :lm-head :identity})

(defn known?
  "True if `t` is a built-in layer type."
  [t]
  (contains? built-in-types t))

(defn shape-str
  "Pretty `[3, 32, 32]`-style rendering of a shape vector."
  [shape]
  (str "[" (str/join ", " shape) "]"))
