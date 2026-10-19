(ns num.autograd
  "Minimal reverse-mode automatic differentiation (ADR-2607131500 Phase 1,
  'training'). Deliberately NOT a general training framework — the honest
  minimal form of 'training capability': real, correct gradients (verified
  against numerical/finite-difference gradients, the standard autograd
  correctness check — see test/num/autograd_test.cljc) for exactly the ops a
  tiny linear/relu/linear/softmax(+mse-loss) network needs, matching
  torch-clj's own README example architecture. Most general tensor gradients
  remain host-materialized on GPU backends, but multi-head-attention has a fused
  `ITensorBackend` forward/backward path verified through async Deno WebGPU on
  Apple Metal; its Q/K/V tensors and gradients stay device-resident.

  Design: a small tape-based (Wengert-list) autograd. `with-tape` binds a
  fresh tape; every op below (`matmul*`/`add-bias*`/`relu*`/`softmax*`/
  `mse-loss*`) builds its forward result AND records itself + a
  `backward-fn` closure onto the tape. `backward!` seeds the output's
  gradient and replays the tape in REVERSE (creation) order, each node's
  backward-fn reading its own accumulated upstream gradient and pushing
  gradient contributions into its parents' `:grad` atoms. This is correct
  for acyclic tensor graphs, including residual/shared-subgraph branches whose
  contributions are summed by `accumulate!`. Dynamic control flow and higher-
  order differentiation remain outside this minimal engine."
  (:require [num.array :as arr]
            [num.core :as nm]
            [num.protocol :as p]
            [num.tensor :as t]))

(def ^:dynamic *tape* nil)

(defrecord Value [data grad backward-fn parents])

(defn- accumulate! [v contrib]
  (swap! (:grad v)
         (fn [g]
           (if g
             (do
               (when-let [temporaries (:gradient-temporaries (meta *tape*))]
                 ;; The summed gradient owns fresh storage. Neither displaced
                 ;; input is necessarily reachable from a tape node: residual
                 ;; branches commonly contribute a freshly produced VJP into
                 ;; an already accumulated gradient. Track both operands so a
                 ;; model-level lifecycle can release them after backward.
                 (swap! temporaries into [g contrib]))
               (t/add g contrib))
             contrib))))

(defn- record! [v]
  (when *tape* (swap! *tape* conj v))
  v)

(defn- node [data parents backward-fn]
  (record! (->Value data (atom nil) backward-fn parents)))

(defn value
  "Wrap `data` (an NDArray) as a graph leaf — a parameter (its `:grad` is
  read after `backward!`) or an input (its `:grad` is simply unused)."
  [data]
  (node data [] (fn [_])))

(defmacro with-tape
  "Run `body` with a fresh tape bound. Returns `[result tape-atom]`."
  [& body]
  `(binding [*tape* (atom [] :meta {:gradient-temporaries (atom [])})]
     [(do ~@body) *tape*]))

(defn backward!
  "Seed `v`'s gradient with `seed` (an NDArray matching `v`'s shape — for a
  scalar loss node, a 1-element NDArray) and propagate through `tape` (the
  second element `with-tape` returned) in reverse creation order."
  [v seed tape]
  (binding [*tape* tape]
    (accumulate! v seed)
    (doseq [nd (reverse @tape)]
      ((:backward-fn nd) nd))))

;; --- ops -------------------------------------------------------------------

(defn cast*
  "Differentiable physical dtype conversion. Forward materializes `dtype`;
  backward converts the upstream gradient to the input's storage dtype before
  accumulation, preserving graph connectivity across autocast boundaries."
  [x dtype]
  (let [input-dtype (or (:dtype (:data x)) :f32)]
    (if (= input-dtype dtype)
      x
      (node (arr/cast (:data x) dtype)
            [x]
            (fn [self]
              (when-let [g @(:grad self)]
                (accumulate! x (arr/cast g input-dtype))))))))

(defn- swap-last-two
  "Transpose the matrix dimensions while preserving any batch dimensions."
  [a]
  (let [rank (count (:shape a))
        perm (vec (concat (range (- rank 2)) [(dec rank) (- rank 2)]))]
    (t/transpose a perm)))

(defn matmul*
  "z = x @ W, including batched matrix multiplication. Gradients transpose
  only the final two (matrix) axes and preserve all leading batch axes."
  [x W]
  (node (t/matmul (:data x) (:data W))
          [x W]
          (fn [self]
            (when-let [g @(:grad self)]
            (when-not (= (or (:dtype g) :f32)
                         (or (:dtype (:data W)) :f32))
              (throw (ex-info "matmul VJP activation/weight dtype mismatch"
                              {:gradient-dtype (:dtype g)
                               :gradient-shape (:shape g)
                               :weight-dtype (:dtype (:data W))
                               :weight-shape (:shape (:data W))})))
            (when-not (= (or (:dtype (:data x)) :f32)
                         (or (:dtype g) :f32))
              (throw (ex-info "matmul VJP input/gradient dtype mismatch"
                              {:input-dtype (:dtype (:data x))
                               :input-shape (:shape (:data x))
                               :gradient-dtype (:dtype g)
                               :gradient-shape (:shape g)})))
            (let [weight-t (swap-last-two (:data W))
                  input-t (swap-last-two (:data x))
                  input-gradient (t/matmul g weight-t)
                  weight-gradient (t/matmul input-t g)]
              (arr/release-all! [weight-t input-t])
              (accumulate! x input-gradient)
              (accumulate! W weight-gradient))))))

(defn add-bias*
  "z = x + b, b broadcast over x's leading (batch) axis. dL/dx = dL/dz;
  dL/db = sum of dL/dz over the batch axis (every batch row's gradient
  contributes to the one shared bias vector)."
  [x b]
  (node (t/add (:data x) (:data b))
        [x b]
        (fn [self]
          (when-let [g @(:grad self)]
            (accumulate! x g)
            (let [gradient-dtype (or (:dtype g) :f32)]
              (if (= gradient-dtype :f32)
                (accumulate! b (t/sum g 0))
                (let [stable-gradient (arr/cast g :f32)
                      bias-gradient (t/sum stable-gradient 0)]
                  (arr/release! stable-gradient)
                  (accumulate! b bias-gradient))))))))

(defn add*
  "Elementwise addition of equal-shaped Values, including residual branches."
  [x y]
  (node (nm/add (:data x) (:data y)) [x y]
        (fn [self]
          (when-let [g @(:grad self)]
            (accumulate! x g)
            (accumulate! y g)))))

(defn mul*
  "Elementwise multiplication of equal-shaped Values."
  [x y]
  (node (nm/mul (:data x) (:data y)) [x y]
        (fn [self]
          (when-let [g @(:grad self)]
            (accumulate! x (nm/mul g (:data y)))
            (accumulate! y (nm/mul g (:data x)))))))

(defn- relu-grad
  "Elementwise derivative of relu at `x-data`: 1.0 where x>0, else 0.0 (the
  standard, if technically-debatable-at-exactly-0, convention). A small host
  round-trip — the same documented tradeoff every num.tensor op already
  makes, not a new one."
  [x-data]
  (arr/from-vec (:backend x-data)
                (mapv #(if (pos? %) 1.0 0.0) (arr/->vec x-data))
                (:shape x-data)))

(defn relu*
  [x]
  (node (nm/relu (:data x))
        [x]
        (fn [self]
          (when-let [g @(:grad self)]
            (accumulate! x (nm/mul g (relu-grad (:data x))))))))

(defn sigmoid*
  "Differentiable sigmoid. Its derivative is evaluated from the saved output,
  so both the forward and backward remain backend-native."
  [x]
  (let [y (nm/sigmoid (:data x))]
    (node y [x]
          (fn [self]
            (when-let [g @(:grad self)]
              (accumulate! x (nm/mul g (nm/sigmoid-gradient y))))))))

(defn tanh*
  "Differentiable hyperbolic tangent with a backend-native `1 - y^2`
  derivative evaluated from the saved output."
  [x]
  (let [y (nm/tanh (:data x))]
    (node y [x]
          (fn [self]
            (when-let [g @(:grad self)]
              (accumulate! x (nm/mul g (nm/tanh-gradient y))))))))

(defn gelu*
  "Differentiable PyTorch-default GELU. The derivative uses the saved input
  and remains backend-native on GPU backends."
  [x]
  (node (nm/gelu (:data x)) [x]
        (fn [self]
          (when-let [g @(:grad self)]
            (accumulate! x
                         (nm/mul g (nm/gelu-gradient (:data x))))))))

(defn softmax*
  "y = softmax(x) along the last axis. Standard softmax-Jacobian-vector
  product: dL/dx_i = y_i * (dL/dy_i - sum_j(dL/dy_j * y_j)) (per row)."
  [x]
  (let [y (t/softmax (:data x))]
    (node y
          [x]
          (fn [self]
            (when-let [g @(:grad self)]
              (let [gy (nm/mul g y)
                    s (t/sum gy (dec (count (:shape y))) {:keepdims? true})
                    dx (nm/mul y (t/sub g s))]
                (accumulate! x dx)))))))

(defn mse-loss*
  "L = mean((pred - target)^2) over every element, a scalar (shape `[]`).
  `target` is a plain NDArray, not a Value — nothing needs its gradient.
  dL/dpred = 2*(pred - target) / N."
  [pred target]
  (let [prediction (:data pred)
        backend (:backend prediction)
        shape (:shape prediction)
        count (arr/nelems shape)]
    (when-not (= shape (:shape target))
      (throw (ex-info "num.autograd/mse-loss*: target shape must match prediction"
                      {:prediction shape :target (:shape target)})))
    (if (and (= backend (:backend target))
             (= :f32 (:dtype prediction :f32) (:dtype target :f32))
             (satisfies? p/ITensorBackend backend))
      (node (assoc (arr/->NDArray
                    backend
                    (p/-mse-loss backend (:handle prediction) (:handle target)
                                 {:count count})
                    []) :dtype :f32)
            [pred]
            (fn [self]
              (when-let [g @(:grad self)]
                (accumulate!
                 pred
                 (assoc (arr/->NDArray
                         backend
                         (p/-mse-gradient backend (:handle prediction)
                                          (:handle target) (:handle g)
                                          {:count count})
                         shape) :dtype :f32)))))
      (let [diff (t/sub prediction target)
            n (double count)
            loss-val (if (= :f32 (or (:dtype diff) :f32))
                       (/ (nm/sum (nm/mul diff diff)) n)
                       (/ (reduce + (map #(* % %) (arr/->vec diff))) n))]
        (node (arr/from-vec backend [loss-val] [])
              [pred]
              (fn [self]
                (when-let [g @(:grad self)]
                  (let [scale (* 2.0 (arr/->scalar g) (/ 1.0 n))
                        diff-copy (arr/from-vec backend (arr/->vec diff) shape
                                                (or (:dtype diff) :f32))]
                    (accumulate! pred (t/scale diff-copy scale))))))))))

(defn cross-entropy-loss*
  "Mean stable cross entropy over the final logits axis.

  `labels` has the logits shape without its final class dimension and stores
  integer class IDs as ordinary num values. `ignore-index` rows contribute
  neither loss nor gradient."
  ([logits labels] (cross-entropy-loss* logits labels {}))
  ([logits labels {:keys [ignore-index] :or {ignore-index -100}}]
   (let [logits-data (:data logits)
         backend (:backend logits-data)
         shape (:shape logits-data)
         classes (long (peek shape))
         label-shape (vec (butlast shape))
         rows (arr/nelems label-shape)]
     (when-not (and (seq shape) (pos? classes) (= label-shape (:shape labels)))
       (throw (ex-info "cross entropy labels must match logits without class axis"
                       {:logits shape :labels (:shape labels)})))
     (if (and (= :f16 (:dtype logits-data))
              (= backend (:backend labels))
              (satisfies? p/IDTypeTensorOps backend))
       (let [{:keys [loss stats]}
             (p/-cross-entropy-forward-dtype
              backend (:handle logits-data) (:handle labels)
              {:rows rows :classes classes :ignore-index ignore-index} :f16)
             loss-data (assoc (arr/->NDArray backend loss []) :dtype :f32)]
         (node loss-data [logits]
               (fn [self]
                 (when-let [g @(:grad self)]
                   (accumulate!
                    logits
                    (assoc
                     (arr/->NDArray
                      backend
                      (p/-cross-entropy-backward-dtype
                       backend (:handle logits-data) (:handle labels) stats
                       (:handle g)
                       {:rows rows :classes classes :ignore-index ignore-index
                        :loss-h loss}
                       :f16)
                      shape)
                     :dtype :f16))))))
       (let [values (vec (arr/->vec logits-data))
             targets (mapv long (arr/->vec labels))
             valid (keep-indexed (fn [row label]
                                   (when (not= label ignore-index) row))
                                 targets)
             valid-count (count valid)
             row-stats (mapv
                        (fn [row]
                          (let [base (* row classes)
                                row-values (subvec values base (+ base classes))
                                maximum (reduce max row-values)
                                denominator (reduce + (map #(Math/exp (- % maximum))
                                                           row-values))]
                            [maximum denominator]))
                        (range rows))
             loss-value (if (zero? valid-count) 0.0
                          (/ (reduce +
                                     (map (fn [row]
                                            (let [[maximum denominator]
                                                  (nth row-stats row)
                                                  label (nth targets row)]
                                              (- (+ maximum (Math/log denominator))
                                                 (nth values (+ (* row classes)
                                                                label)))))
                                          valid))
                             valid-count))]
         (node (arr/from-vec backend [loss-value] []) [logits]
               (fn [self]
                 (when-let [g @(:grad self)]
                   (let [upstream (arr/->scalar g)
                         scale (if (zero? valid-count) 0.0
                                   (/ upstream valid-count))
                         gradient
                         (mapv
                          (fn [index]
                            (let [row (quot index classes)
                                  c (mod index classes)
                                  label (nth targets row)]
                              (if (= label ignore-index) 0.0
                                  (let [[maximum denominator] (nth row-stats row)
                                        probability (/ (Math/exp
                                                        (- (nth values index) maximum))
                                                       denominator)]
                                    (* scale (- probability
                                                (if (= c label) 1.0 0.0)))))))
                          (range (* rows classes)))]
                     (accumulate! logits
                                  (arr/from-vec backend gradient shape
                                                (or (:dtype logits-data) :f32))))))))))))

;; --- conv2d (2026-07-13 "raise the maturity" loop) --------------------------
;; num.tensor/conv2d bundles im2col + matmul + reshape into one opaque
;; function with no seam to hook a backward onto. conv2d* below decomposes
;; the SAME shape convention (x: [H W], kernel: [kh kw], out: [oh ow]) back
;; into that exact im2col+matmul+reshape pipeline, wrapping each step as a
;; differentiable node — matmul* and reshape*'s gradients are already real;
;; im2col*/col2im (below) is the one genuinely new backward formula.

(defn reshape*
  "z = reshape(x, new-shape) (zero-copy, like num.tensor/reshape). dL/dx =
  reshape(dL/dz, x's original shape) — reshape's backward is itself, since
  it never moves data, only relabels shape."
  [x new-shape]
  (let [old-shape (:shape (:data x))]
    (node (t/reshape (:data x) new-shape)
          [x]
          (fn [self]
            (when-let [g @(:grad self)]
              (accumulate! x (t/reshape g old-shape)))))))

(defn- im2col-nd
  "[oh*ow kh*kw] patches NDArray from a `[H W]` NDArray `a` — the exact same
  patch layout `num.tensor/conv2d` uses internally (mirrored here rather
  than reused, since that function has no seam to extract just this step)."
  [a kh kw]
  (let [[H W] (:shape a) kh (long kh) kw (long kw)
        oh (inc (- (long H) kh)) ow (inc (- (long W) kw))
        xs (vec (arr/->vec a))
        W (long W)
        patches (double-array (* oh ow kh kw))]
    (dotimes [oi oh]
      (dotimes [oj ow]
        (dotimes [ki kh]
          (dotimes [kj kw]
            (aset patches (+ (* (+ (* oi ow) oj) kh kw) (* ki kw) kj)
                  (nth xs (+ (* (+ oi ki) W) (+ oj kj))))))))
    (arr/from-vec (:backend a) (vec patches) [(* oh ow) (* kh kw)])))

(defn- col2im-nd
  "Adjoint of `im2col-nd`: scatter-ADD `patches-grad` (`[oh*ow kh*kw]`) back
  to an `in-shape`-shaped (`[H W]`) gradient. A pixel touched by more than
  one sliding window (any interior pixel once `kh`/`kw` > 1) accumulates the
  SUM of every window's contribution — the standard im2col-conv adjoint."
  [patches-grad in-shape kh kw]
  (let [[H W] in-shape kh (long kh) kw (long kw)
        oh (inc (- (long H) kh)) ow (inc (- (long W) kw))
        pg (vec (arr/->vec patches-grad))
        W (long W)
        out (double-array (* (long H) W))]
    (dotimes [oi oh]
      (dotimes [oj ow]
        (dotimes [ki kh]
          (dotimes [kj kw]
            (let [idx (+ (* (+ oi ki) W) (+ oj kj))
                  pidx (+ (* (+ (* oi ow) oj) kh kw) (* ki kw) kj)]
              (aset out idx (+ (aget out idx) (double (nth pg pidx)))))))))
    (arr/from-vec (:backend patches-grad) (vec out) in-shape)))

(defn im2col*
  [x kh kw]
  (node (im2col-nd (:data x) kh kw)
        [x]
        (fn [self]
          (when-let [g @(:grad self)]
            (accumulate! x (col2im-nd g (:shape (:data x)) kh kw))))))

(defn conv2d*
  "Single-channel 2-D 'valid' convolution with real gradients — same shape
  convention as `num.tensor/conv2d` (`x`: `[H W]`, `kernel`: `[kh kw]`,
  output `[oh ow]`), built from `im2col*` (the one new backward formula) +
  the already-real `matmul*`/`reshape*`."
  [x kernel]
  (let [[kh kw] (:shape (:data kernel))
        kh (long kh) kw (long kw)
        [H W] (:shape (:data x))
        oh (inc (- (long H) kh)) ow (inc (- (long W) kw))
        P (im2col* x kh kw)
        kflat (reshape* kernel [(* kh kw) 1])
        out-flat (matmul* P kflat)]
    (reshape* out-flat [oh ow])))

;; --- attention (2026-07-13 "raise the maturity" loop) -----------------------

(defn transpose*
  "Permute tensor axes with a differentiable transpose. With no permutation,
  reverses all axes like `num.tensor/transpose`. Backward applies the inverse
  permutation, so arbitrary-rank head splitting/merging remains differentiable."
  ([x]
   (transpose* x (vec (reverse (range (count (:shape (:data x))))))))
  ([x perm]
   (let [perm (mapv long perm)
         inverse (reduce-kv (fn [out destination source]
                              (assoc out source destination))
                            (vec (repeat (count perm) 0)) perm)]
     (node (t/transpose (:data x) perm)
           [x]
           (fn [self]
             (when-let [g @(:grad self)]
               (accumulate! x (t/transpose g inverse))))))))

(defn- copy-nd [a] (arr/from-vec (:backend a) (arr/->vec a) (:shape a)))

(defn scale*
  "z = alpha * x (alpha a host scalar constant, not a Value — nothing needs
  its gradient). dL/dx = alpha * dL/dz. Copies before calling num.core/scal!
  (BLAS in-place convention) since `x` — e.g. self-attention's Q=K=V=the
  SAME Value — may still be needed unscaled elsewhere in the graph."
  [alpha x]
  (node (nm/scal! alpha (copy-nd (:data x)))
        [x]
        (fn [self]
          (when-let [g @(:grad self)]
            (accumulate! x (nm/scal! alpha (copy-nd g)))))))

(defn attention*
  "Single-head scaled dot-product attention with real gradients — same shape
  convention as `num.tensor/attention` (`Q`: `[seqQ d]`, `K`/`V`:
  `[seqK d]`; self-attention passes the SAME Value as all three, which
  `with-tape`/`backward!`'s gradient-accumulation already handles correctly
  — each of Q/K/V's usage sites contributes additively to the one shared
  `:grad` atom). Built from `transpose*`/`scale*` (new) + the already-real
  `matmul*`/`softmax*` — no formula here is independently re-derived, this
  is the identical composition `num.tensor/attention` itself uses."
  [Q K V]
  (let [d (long (last (:shape (:data Q))))
        scores (matmul* Q (transpose* K))
        scaled (scale* (/ 1.0 (Math/sqrt d)) scores)
        weights (softmax* scaled)]
    (matmul* weights V)))

(defn- add-constant* [x constant]
  (node (t/add (:data x) constant)
        [x]
        (fn [self]
          (when-let [g @(:grad self)] (accumulate! x g)))))

(defn- attention-mask-nd
  [backend batch seq-q seq-k causal? key-padding-mask]
  (when (or causal? key-padding-mask)
    (let [padding (when key-padding-mask (vec (arr/->vec key-padding-mask)))]
      (arr/from-vec
       backend
       (mapv (fn [index]
               (let [k (mod index seq-k)
                     q (mod (quot index seq-k) seq-q)
                     b (quot index (* seq-q seq-k))]
                 (if (or (and causal? (> k q))
                         (and padding
                              (not (zero? (nth padding (+ (* b seq-k) k))))))
                   -1.0e30 0.0)))
             (range (* batch seq-q seq-k)))
       [batch 1 seq-q seq-k]))))

(declare cat* slice-axis*)

(defn multi-head-attention*
  "Differentiable rank-2/rank-3 multi-head attention, including causal and
  key-padding masks. Options match `num.tensor/multi-head-attention`."
  ([Q K V num-heads] (multi-head-attention* Q K V num-heads {}))
  ([Q K V num-heads {:keys [causal? key-padding-mask kv-heads] :as opts
                      :or {causal? false}}]
   (let [q-data (:data Q) k-data (:data K) v-data (:data V)
         q-shape (:shape q-data) k-shape (:shape k-data)
         v-shape (:shape v-data) rank (count q-shape)
         [batch seq-q d-model] (if (= rank 2) (into [1] q-shape) q-shape)
         [k-batch seq-k k-d-model] (if (= rank 2) (into [1] k-shape) k-shape)
         expected-mask (if (= rank 2) [seq-k] [batch seq-k])
         heads (long num-heads)
         kv-heads (long (or kv-heads heads))
         d-head (when (and (pos? heads) (zero? (mod (long d-model) heads)))
                  (quot (long d-model) heads))
         expected-k-model (when d-head (* kv-heads d-head))]
     (when-not (and (#{2 3} rank) (= rank (count k-shape) (count v-shape))
                    (= k-shape v-shape) (= batch k-batch)
                    (= k-d-model expected-k-model) (pos? heads) (pos? kv-heads)
                    (zero? (mod heads kv-heads))
                    (zero? (mod (long d-model) heads))
                    (or (nil? key-padding-mask)
                        (= (:shape key-padding-mask) expected-mask)
                        (and (= rank 2)
                             (= (:shape key-padding-mask) [1 seq-k]))))
       (throw (ex-info "num.autograd/multi-head-attention*: incompatible dimensions or mask"
                       {:query q-shape :key k-shape :value v-shape
                        :key-padding-mask (:shape key-padding-mask)
                        :num-heads heads :kv-heads kv-heads})))
     (let [backend (:backend q-data)
           output-shape (if (= rank 2) [seq-q d-model] [batch seq-q d-model])
           fused? (and (= backend (:backend k-data) (:backend v-data))
                       (or (nil? key-padding-mask)
                           (= backend (:backend key-padding-mask)))
                       (= :f32 (:dtype q-data :f32)
                          (:dtype k-data :f32) (:dtype v-data :f32))
                       (satisfies? p/ITensorBackend backend))
           typed-fused? (and (= backend (:backend k-data) (:backend v-data))
                             (nil? key-padding-mask)
                             (= :f16 (:dtype q-data) (:dtype k-data) (:dtype v-data))
                             (satisfies? p/IDTypeTensorOps backend))]
       (cond
         fused?
         (node
          (t/multi-head-attention q-data k-data v-data heads opts)
          [Q K V]
          (fn [self]
            (when-let [g @(:grad self)]
              (let [params {:batch batch :seq-q seq-q :seq-k seq-k
                            :d-model d-model :kv-d-model k-d-model
                            :heads heads :kv-heads kv-heads :head-dim d-head
                            :causal? causal?
                            :has-key-padding-mask? (boolean key-padding-mask)}
                    gradients (p/-multi-head-attention-backward
                               backend (:handle q-data) (:handle k-data)
                               (:handle v-data)
                               (when key-padding-mask (:handle key-padding-mask))
                               (:handle g) params)
                    ndarray (fn [handle shape]
                              (assoc (arr/->NDArray backend handle shape)
                                     :dtype :f32))]
                (accumulate! Q (ndarray (:query gradients) q-shape))
                (accumulate! K (ndarray (:key gradients) k-shape))
                (accumulate! V (ndarray (:value gradients) (:shape v-data)))))))

         typed-fused?
         (node
          (t/multi-head-attention q-data k-data v-data heads opts)
          [Q K V]
          (fn [self]
            (when-let [g @(:grad self)]
              (let [params {:batch batch :seq-q seq-q :seq-k seq-k
                            :d-model d-model :kv-d-model k-d-model
                            :heads heads :kv-heads kv-heads :head-dim d-head
                            :causal? causal?}
                    gradients (p/-multi-head-attention-backward-dtype
                               backend (:handle q-data) (:handle k-data)
                               (:handle v-data) (:handle g) params :f16)
                    typed (fn [handle shape]
                            (let [f32 (assoc (arr/->NDArray backend handle shape)
                                             :dtype :f32)
                                  f16 (arr/cast f32 :f16)]
                              (arr/release! f32)
                              f16))]
                (accumulate! Q (typed (:query gradients) q-shape))
                (accumulate! K (typed (:key gradients) k-shape))
                (accumulate! V (typed (:value gradients) (:shape v-data)))))))

         :else
         (let [q3 (if (= rank 2) (reshape* Q [1 seq-q d-model]) Q)
               k3 (if (= rank 2) (reshape* K [1 seq-k k-d-model]) K)
               v3 (if (= rank 2) (reshape* V [1 seq-k k-d-model]) V)
               split-query (fn [x]
                             (transpose* (reshape* x [batch seq-q heads d-head])
                                         [0 2 1 3]))
               repeat-kv (fn [x]
                           (let [split (reshape* x [batch seq-k kv-heads d-head])
                                 group-size (quot heads kv-heads)
                                 pieces (mapcat (fn [head]
                                                  (repeat group-size
                                                          (slice-axis* split 2 head (inc head))))
                                                (range kv-heads))]
                             (transpose* (cat* pieces 2) [0 2 1 3])))
               qh (split-query q3)
               kh (repeat-kv k3)
               vh (repeat-kv v3)
               scores (scale* (/ 1.0 (Math/sqrt d-head))
                              (matmul* qh (transpose* kh [0 1 3 2])))
               mask (attention-mask-nd backend batch seq-q seq-k
                                       causal? key-padding-mask)
               masked (if mask (add-constant* scores mask) scores)
               weights (softmax* masked)
               heads-out (matmul* weights vh)
               merged (reshape* (transpose* heads-out [0 2 1 3])
                                [batch seq-q d-model])]
           (if (= rank 2) (reshape* merged output-shape) merged)))))))
(defn- pair-option [value]
  (mapv long (if (sequential? value) value [value value])))

(defn conv2d-nchw*
  "Differentiable NCHW convolution matching `num.tensor/conv2d-nchw`.

  `x`, `weight`, and optional `bias` are autograd Values. Backward computes
  gradients for all three and supports the forward op's stride, padding,
  dilation, and groups semantics, including depthwise convolution."
  ([x weight] (conv2d-nchw* x weight nil {}))
  ([x weight bias] (conv2d-nchw* x weight bias {}))
  ([x weight bias {:keys [stride padding dilation groups] :as opts
                   :or {stride 1 padding 0 dilation 1 groups 1}}]
   (let [input-data (:data x)
         weight-data (:data weight)
         bias-data (when bias (:data bias))
         output (t/conv2d-nchw input-data weight-data bias-data opts)
         [N Cin H W] (mapv long (:shape input-data))
         [Cout Cin-per-group kh kw] (mapv long (:shape weight-data))
         [_ _ oh ow] (mapv long (:shape output))
         [sh sw] (pair-option stride)
         [ph pw] (pair-option padding)
         [dh dw] (pair-option dilation)
         groups (long groups)
         outputs-per-group (quot Cout groups)]
     (node output
           (cond-> [x weight] bias (conj bias))
           (fn [self]
             (when-let [g @(:grad self)]
               (let [xs (double-array (arr/->vec input-data))
                     ws (double-array (arr/->vec weight-data))
                     gs (double-array (arr/->vec g))
                     dx (double-array (* N Cin H W))
                     dw-gradient (double-array (* Cout Cin-per-group kh kw))
                     db-gradient (when bias (double-array Cout))]
                 (dotimes [n N]
                   (dotimes [oc Cout]
                     (let [group (quot oc outputs-per-group)
                           input-channel-base (* group Cin-per-group)]
                       (dotimes [oi oh]
                         (dotimes [oj ow]
                           (let [g-index (+ (* n Cout oh ow) (* oc oh ow) (* oi ow) oj)
                                 gradient (aget gs g-index)]
                             (when db-gradient
                               (aset db-gradient oc (+ (aget db-gradient oc) gradient)))
                             (dotimes [icg Cin-per-group]
                               (let [ic (+ input-channel-base icg)]
                                 (dotimes [ki kh]
                                   (let [ih (+ (- (* oi sh) ph) (* ki dh))]
                                     (when (and (<= 0 ih) (< ih H))
                                       (dotimes [kj kw]
                                         (let [iw (+ (- (* oj sw) pw) (* kj dw))]
                                           (when (and (<= 0 iw) (< iw W))
                                             (let [x-index (+ (* n Cin H W) (* ic H W) (* ih W) iw)
                                                   w-index (+ (* oc Cin-per-group kh kw)
                                                              (* icg kh kw) (* ki kw) kj)]
                                               (aset dx x-index
                                                     (+ (aget dx x-index)
                                                        (* gradient (aget ws w-index))))
                                               (aset dw-gradient w-index
                                                     (+ (aget dw-gradient w-index)
                                                        (* gradient (aget xs x-index)))))))))))))))))))
                 (accumulate! x (arr/from-vec (:backend input-data) (vec dx) (:shape input-data)))
                 (accumulate! weight
                              (arr/from-vec (:backend weight-data) (vec dw-gradient)
                                            (:shape weight-data)))
                 (when bias
                   (accumulate! bias
                                (arr/from-vec (:backend bias-data) (vec db-gradient)
                                              (:shape bias-data)))))))))))

;; --- UNet activations / normalization -----------------------------------------

(defn silu*
  "Differentiable SiLU. `d/dx silu(x) = sigmoid(x) *
  (1 + x * (1 - sigmoid(x)))`."
  [x]
  (let [input-data (:data x)]
    (node (t/silu input-data)
          [x]
          (fn [self]
            (when-let [g @(:grad self)]
              (if (and (= :f16 (:dtype input-data))
                       (= (:backend input-data) (:backend g))
                       (satisfies? p/IDTypeOps (:backend input-data)))
                (let [sigmoid (nm/sigmoid input-data)
                      sigmoid-gradient (nm/sigmoid-gradient sigmoid)
                      weighted-gradient (nm/mul input-data sigmoid-gradient)
                      derivative (nm/add sigmoid weighted-gradient)
                      dx (nm/mul g derivative)]
                  (arr/release-all! [sigmoid sigmoid-gradient weighted-gradient
                                     derivative])
                  (accumulate! x dx))
                (let [xs (arr/->vec input-data)
                      gs (arr/->vec g)
                      dx (mapv (fn [value upstream]
                                 (let [sigmoid (/ 1.0 (+ 1.0 (Math/exp (- (double value)))))
                                       derivative (* sigmoid
                                                     (+ 1.0 (* value (- 1.0 sigmoid))))]
                                   (* upstream derivative)))
                               xs gs)]
                  (accumulate! x (arr/from-vec (:backend input-data) dx
                                               (:shape input-data))))))))))

(defn group-norm-nchw*
  "Differentiable PyTorch-style GroupNorm. Optional affine `weight`/`bias`
  are Values with shape `[C]`; gradients are accumulated for input and every
  supplied affine parameter."
  ([x num-groups] (group-norm-nchw* x num-groups nil nil 1.0e-5))
  ([x num-groups weight bias eps]
   (let [input-data (:data x)
         weight-data (when weight (:data weight))
         bias-data (when bias (:data bias))
         output (t/group-norm-nchw input-data num-groups weight-data bias-data eps)
         [N C H W] (mapv long (:shape input-data))
         groups (long num-groups)
         channels-per-group (quot C groups)
         group-size (* channels-per-group H W)]
     (node output
           (cond-> [x] weight (conj weight) bias (conj bias))
           (fn [self]
             (when-let [g @(:grad self)]
               (let [xs (double-array (arr/->vec input-data))
                     gs (double-array (arr/->vec g))
                     ws (when weight (double-array (arr/->vec weight-data)))
                     dx (double-array (* N C H W))
                     dw (when weight (double-array C))
                     dbias (when bias (double-array C))]
                 (dotimes [n N]
                   (dotimes [group groups]
                     (let [base (+ (* n C H W) (* group group-size))
                           mean (/ (loop [i 0 sum 0.0]
                                     (if (< i group-size)
                                       (recur (inc i) (+ sum (aget xs (+ base i))))
                                       sum))
                                   group-size)
                           variance (/ (loop [i 0 sum 0.0]
                                         (if (< i group-size)
                                           (let [d (- (aget xs (+ base i)) mean)]
                                             (recur (inc i) (+ sum (* d d))))
                                           sum))
                                       group-size)
                           inv-std (/ 1.0 (Math/sqrt (+ variance eps)))
                           sum-dy (loop [i 0 sum 0.0]
                                    (if (< i group-size)
                                      (let [channel (+ (* group channels-per-group)
                                                       (quot i (* H W)))
                                            gamma (if ws (aget ws channel) 1.0)]
                                        (recur (inc i) (+ sum (* (aget gs (+ base i)) gamma))))
                                      sum))
                           sum-dy-xhat
                           (loop [i 0 sum 0.0]
                             (if (< i group-size)
                               (let [channel (+ (* group channels-per-group)
                                                (quot i (* H W)))
                                     gamma (if ws (aget ws channel) 1.0)
                                     xhat (* (- (aget xs (+ base i)) mean) inv-std)]
                                 (recur (inc i)
                                        (+ sum (* (aget gs (+ base i)) gamma xhat))))
                               sum))]
                       (dotimes [i group-size]
                         (let [index (+ base i)
                               channel (+ (* group channels-per-group)
                                          (quot i (* H W)))
                               upstream (aget gs index)
                               gamma (if ws (aget ws channel) 1.0)
                               xhat (* (- (aget xs index) mean) inv-std)
                               dx-value (* (/ inv-std group-size)
                                           (- (* group-size upstream gamma)
                                              sum-dy
                                              (* xhat sum-dy-xhat)))]
                           (aset dx index dx-value)
                           (when dw
                             (aset dw channel (+ (aget dw channel) (* upstream xhat))))
                           (when dbias
                             (aset dbias channel (+ (aget dbias channel) upstream))))))))
                 (accumulate! x (arr/from-vec (:backend input-data) (vec dx)
                                              (:shape input-data)))
                 (when weight
                   (accumulate! weight (arr/from-vec (:backend weight-data) (vec dw)
                                                     (:shape weight-data))))
                 (when bias
                   (accumulate! bias (arr/from-vec (:backend bias-data) (vec dbias)
                                                   (:shape bias-data)))))))))))

(defn layer-norm-last*
  "Differentiable LayerNorm over the final dimension, composed from metadata
  reshapes and the verified one-group GroupNorm VJP."
  ([x] (layer-norm-last* x nil nil 1.0e-5))
  ([x weight bias eps]
   (let [shape (:shape (:data x))]
     (when (empty? shape)
       (throw (ex-info "num.autograd/layer-norm-last* requires rank >= 1"
                       {:shape shape})))
     (let [features (long (peek shape))
           rows (quot (arr/nelems shape) features)]
       (reshape*
        (group-norm-nchw* (reshape* x [rows features 1 1])
                          1 weight bias eps)
        shape)))))

(defn embedding*
  "Differentiable embedding lookup. Token IDs are constants; gradients are
  accumulated into weight rows, including repeated-token scatter-adds."
  [indices weight]
  (let [weight-data (:data weight)
        output (t/embedding indices weight-data)
        backend (:backend weight-data)
        [rows dim] (:shape weight-data)]
    (node output [weight]
          (fn [self]
            (when-let [g @(:grad self)]
              (if (and (= :f16 (:dtype weight-data) (:dtype g))
                       (= backend (:backend indices) (:backend g))
                       (satisfies? p/IDTypeTensorOps backend))
                (accumulate!
                 weight
                 (assoc
                  (arr/->NDArray
                   backend
                   (p/-embedding-backward-dtype
                    backend (:handle indices) (:handle g)
                    {:tokens (arr/nelems (:shape indices)) :rows rows :dim dim}
                    :f16)
                   (:shape weight-data))
                  :dtype :f32))
                (let [ids (mapv long (arr/->vec indices))
                      gs (vec (arr/->vec g))
                      dw (double-array (* rows dim))]
                  (doseq [[token-pos row] (map-indexed vector ids)
                          feature (range dim)]
                    (let [weight-index (+ (* row dim) feature)
                          gradient-index (+ (* token-pos dim) feature)]
                      (aset dw weight-index
                            (+ (aget dw weight-index) (nth gs gradient-index)))))
                  (accumulate! weight
                               (arr/from-vec backend (vec dw)
                                             (:shape weight-data)
                                             (or (:dtype weight-data) :f32))))))))))

(defn rms-norm-last*
  "Differentiable RMSNorm over the final dimension."
  ([x weight] (rms-norm-last* x weight 1.0e-5))
  ([x weight eps]
   (let [input-data (:data x) weight-data (:data weight)
         shape (:shape input-data) dim (long (peek shape))
         rows (quot (arr/nelems shape) dim)
         output (t/rms-norm-last input-data weight-data eps)]
     (node output [x weight]
           (fn [self]
             (when-let [g @(:grad self)]
               (if (and (= :f16 (:dtype input-data) (:dtype weight-data))
                        (= (:backend input-data) (:backend weight-data) (:backend g))
                        (satisfies? p/IDTypeTensorOps (:backend input-data)))
                 (let [backend (:backend input-data)
                       gradients (p/-rms-norm-backward-dtype
                                  backend (:handle input-data) (:handle weight-data)
                                  (:handle g) {:rows rows :dim dim :eps eps} :f16)]
                   (accumulate! x (assoc (arr/->NDArray backend (:input gradients) shape)
                                         :dtype :f16))
                   (accumulate! weight
                                (assoc (arr/->NDArray backend (:weight gradients)
                                                     (:shape weight-data))
                                       :dtype :f32)))
                 (let [xs (vec (arr/->vec input-data))
                     ws (vec (arr/->vec weight-data))
                     gs (vec (arr/->vec g))
                     dx (double-array (* rows dim))
                     dw (double-array dim)]
                 (dotimes [row rows]
                   (let [base (* row dim)
                         square-sum (reduce + (map (fn [f]
                                                    (let [v (nth xs (+ base f))]
                                                      (* v v)))
                                                  (range dim)))
                         inv-rms (/ 1.0 (Math/sqrt (+ (/ square-sum dim) eps)))
                         dot-zx (reduce + (map (fn [f]
                                                (* (nth gs (+ base f)) (nth ws f)
                                                   (nth xs (+ base f))))
                                              (range dim)))
                         correction (/ (* dot-zx inv-rms inv-rms inv-rms) dim)]
                     (dotimes [f dim]
                       (let [i (+ base f) upstream (nth gs i) value (nth xs i)]
                         (aset dx i (- (* upstream (nth ws f) inv-rms)
                                      (* value correction)))
                         (aset dw f (+ (aget dw f) (* upstream value inv-rms)))))))
                 (accumulate! x (arr/from-vec (:backend input-data) (vec dx) shape))
                 (accumulate! weight
                              (arr/from-vec (:backend weight-data) (vec dw)
                                            (:shape weight-data)))))))))))

(defn rotary-embedding*
  "Differentiable RoPE. Since each pair is an orthogonal rotation, its VJP is
  the same kernel with the angle sign reversed."
  ([x num-heads] (rotary-embedding* x num-heads {}))
  ([x num-heads opts]
   (node (t/rotary-embedding (:data x) num-heads opts) [x]
         (fn [self]
           (when-let [g @(:grad self)]
             (accumulate! x
                          (t/rotary-embedding g num-heads
                                              (assoc opts :inverse? true))))))))

(defn cat*
  "Differentiable `num.tensor/cat`. Backward slices the upstream gradient
  along the concatenation axis and accumulates one contiguous gradient per
  input Value."
  [values axis]
  (let [values (vec values)
        data (mapv :data values)
        output (t/cat data axis)
        rank (count (:shape output))
        axis (long (if (neg? axis) (+ rank axis) axis))
        first-shape (:shape (first data))
        inner (long (arr/nelems (subvec first-shape (inc axis))))
        outer (long (arr/nelems (subvec first-shape 0 axis)))
        output-axis (long (nth (:shape output) axis))
        axis-sizes (mapv #(long (nth (:shape %) axis)) data)]
    (node output values
          (fn [self]
            (when-let [g @(:grad self)]
              (let [gs (double-array (arr/->vec g))]
                (loop [value-index 0 axis-offset 0]
                  (when (< value-index (count values))
                    (let [value (nth values value-index)
                          shape (:shape (:data value))
                          axis-size (nth axis-sizes value-index)
                          block (* axis-size inner)
                          sliced (double-array (arr/nelems shape))]
                      (dotimes [outer-index outer]
                        (let [source-base (+ (* outer-index output-axis inner)
                                             (* axis-offset inner))
                              destination-base (* outer-index block)]
                          (dotimes [i block]
                            (aset sliced (+ destination-base i)
                                  (aget gs (+ source-base i))))))
                      (accumulate! value
                                   (arr/from-vec (:backend (:data value))
                                                 (vec sliced) shape))
                      (recur (inc value-index) (+ axis-offset axis-size)))))))))))

(defn slice-axis*
  "Differentiable contiguous slice along one axis. Backward scatters the
  upstream slice into a zero gradient with the input shape."
  [value axis start end]
  (let [input (:data value) shape (:shape input) rank (count shape)
        axis (long (if (neg? axis) (+ rank axis) axis))
        start (long start) end (long end)
        inner (long (arr/nelems (subvec shape (inc axis))))
        input-axis (long (nth shape axis))
        output-axis (- end start)
        outer (long (arr/nelems (subvec shape 0 axis)))]
    (node (t/slice-axis input axis start end) [value]
          (fn [self]
            (when-let [g @(:grad self)]
              (let [source (vec (arr/->vec g))
                    scattered (double-array (arr/nelems shape))]
                (dotimes [outer-index outer]
                  (let [source-base (* outer-index output-axis inner)
                        destination-base (+ (* outer-index input-axis inner)
                                            (* start inner))]
                    (dotimes [i (* output-axis inner)]
                      (aset scattered (+ destination-base i)
                            (nth source (+ source-base i))))))
                (accumulate! value
                             (arr/from-vec (:backend input) (vec scattered)
                                           shape (:dtype input :f32)))))))))

(defn upsample-nearest2d*
  "Differentiable nearest-neighbor NCHW upsampling. Backward sums every
  repeated output cell into its source input cell."
  [x scale-factor]
  (let [input-data (:data x)
        [N C H W] (mapv long (:shape input-data))
        [scale-h scale-w] (pair-option scale-factor)
        output (t/upsample-nearest2d input-data scale-factor)
        [_ _ oh ow] (mapv long (:shape output))]
    (node output [x]
          (fn [self]
            (when-let [g @(:grad self)]
              (let [gs (double-array (arr/->vec g))
                    dx (double-array (* N C H W))]
                (dotimes [n N]
                  (dotimes [c C]
                    (dotimes [oi oh]
                      (dotimes [oj ow]
                        (let [input-index (+ (* n C H W) (* c H W)
                                             (* (quot oi scale-h) W)
                                             (quot oj scale-w))
                              output-index (+ (* n C oh ow) (* c oh ow)
                                              (* oi ow) oj)]
                          (aset dx input-index
                                (+ (aget dx input-index) (aget gs output-index))))))))
                (accumulate! x (arr/from-vec (:backend input-data) (vec dx)
                                             (:shape input-data)))))))))
