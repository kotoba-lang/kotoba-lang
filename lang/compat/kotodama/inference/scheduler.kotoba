(ns kotodama.inference.scheduler
  "Host-side mirror of `kotoba/scheduler_core.kotoba` — the paged-KV,
  continuous-batching and prefix/speculation decisions an inference server
  makes every step.

  ## Which of the two is the authority

  The Kotoba core is. It compiles to `aarch64-kotoba-v1`, `wasm32-kotoba-v1`
  and the JVM KIR oracle, and `amu test` runs its 17 `test-*` exports on all
  three. This namespace exists because the HOST owns the aggregates — the
  block table, the sequence list, the radix trie — and a host that had to
  cross a compiler boundary to ask `how many blocks does 100 tokens need`
  would call it once per sequence per step.

  So this is a mirror, and the mirror's job is to be checked. Both files carry
  the SAME case table, in the same order, and `scheduler_parity_test` asserts
  this side of it. When they disagree, the Kotoba core is right.

  ## Why the split exists at all

  A native export cannot take a handle (`native-function-boundary-type?`
  refuses `:vector-i64` at an export) and `:document` crosses but cannot be
  read on native (only `document-edn-read`/`-print` are admitted). So the
  decisions are scalars in and scalars out, and the structures stay here. That
  is the same split `kernel_math_core.kotoba` makes for bytes, and it is not a
  workaround: the aggregates are the server's state and the decisions are its
  policy. Only the policy needs one authority."
  (:refer-clojure :exclude [max min]))

(defn- i-max [a b] (if (> a b) a b))
(defn- i-min [a b] (if (< a b) a b))

;; ── P3: paged KV cache ──────────────────────────────────────────────────────

(defn kv-blocks-needed
  "ceil(tokens / block-size). Zero tokens is zero blocks, not one."
  [tokens block-size]
  (if (< block-size 1)
    0
    (if (< tokens 1) 0 (quot (+ tokens (dec block-size)) block-size))))

(defn kv-blocks-to-append
  "The INCREMENT for appending `new-tokens` to a sequence already holding
  `held`. Appending inside a partially filled block costs nothing, which is
  the whole reason a paged cache is cheaper than a contiguous one."
  [held new-tokens block-size]
  (if (< block-size 1)
    0
    (if (< new-tokens 1)
      0
      (- (kv-blocks-needed (+ held new-tokens) block-size)
         (kv-blocks-needed held block-size)))))

(defn kv-can-allocate?
  "vLLM's watermark. An allocation that would leave fewer than `watermark`
  blocks free is refused even though it fits: without it a prefill consumes
  every block and every running sequence then deadlocks on its next append."
  [free-blocks need watermark]
  (and (>= need 0) (>= (- free-blocks need) (i-max watermark 0))))

(defn kv-free-after-alloc [free-blocks need]
  (i-max 0 (- free-blocks (i-max need 0))))

(defn kv-slot-index
  "Flat slot in the KV pool, or -1. A negative is the refusal rather than a
  throw: a bad index must not take the server down."
  [block-id offset block-size]
  (if (or (< block-size 1) (< block-id 0) (< offset 0) (>= offset block-size))
    -1
    (+ (* block-id block-size) offset)))

(defn kv-append-offset
  "Where the next token goes in the last block, or -1 when a new one is due."
  [filled block-size]
  (if (or (< block-size 1) (< filled 0) (>= filled block-size)) -1 filled))

;; ── P4: continuous batching and chunked prefill ─────────────────────────────

(defn batch-prefill-chunk
  "How much of a prompt this step takes: the smallest of what is left, what
  the budget affords, and the cap. Chunked prefill is what stops a long prompt
  from stalling every decode behind it."
  [remaining budget max-chunk]
  (if (< remaining 1)
    0
    (i-max 0 (i-min remaining (i-min (i-max budget 0) (i-max max-chunk 0))))))

(defn batch-budget-after [budget scheduled]
  (i-max 0 (- budget (i-max scheduled 0))))

(defn batch-admit?
  "Concurrency first, then memory. A server that admits past its batch width
  is a server whose latency target is a wish."
  [free-blocks need watermark running max-running]
  (and (< running (i-max max-running 0))
       (kv-can-allocate? free-blocks need watermark)))

(defn batch-decode-slots
  "One token per running sequence, bounded by the budget. Whatever prefill
  took this step, decode cannot have."
  [running budget]
  (i-max 0 (i-min (i-max running 0) (i-max budget 0))))

(defn preempt-rank
  "Higher is preempted first: newest-first, ties broken by blocks held.

  Preempting the oldest would let a stream of arrivals starve it forever, and
  the newest has the least work to lose on recompute. A rank rather than a
  choice — the caller holds the sequence list and sorts by this."
  [arrival blocks-held]
  (+ (* (i-max arrival 0) 1024) (i-min (i-max blocks-held 0) 1023)))

;; ── P5: prefix caching and speculative decoding ─────────────────────────────

(defn prefix-cached-blocks
  "Only FULL blocks are reusable. A partially filled block belongs to the
  sequence still writing into it, so sharing it would let one sequence read
  another's future tokens — floor, and here rounding down is the correctness
  condition rather than a convenience."
  [matched-tokens block-size]
  (if (or (< block-size 1) (< matched-tokens 1)) 0 (quot matched-tokens block-size)))

(defn prefix-tokens-to-compute
  "What prefill still has to do after a cache hit. Never negative and never
  more than the prompt, so a stale entry claiming more blocks than the prompt
  has cannot make the scheduler compute a negative number of tokens."
  [prompt-tokens cached-blocks block-size]
  (if (< block-size 1)
    (i-max 0 prompt-tokens)
    (i-max 0 (- (i-max 0 prompt-tokens) (* (i-max 0 cached-blocks) block-size)))))

(defn cache-hit-bp
  "Basis points. A rate reported as a truncated percent hides the difference
  between 0.4% and 0%."
  [hit-tokens total-tokens]
  (if (< total-tokens 1)
    0
    (i-min 10000 (quot (* (i-max 0 hit-tokens) 10000) total-tokens))))

(defn spec-accept-count
  "The accepted prefix of a draft, capped by its length. The cap is what stops
  a miscounted match from emitting tokens the model never produced."
  [matched draft-len]
  (i-max 0 (i-min (i-max 0 matched) (i-max 0 draft-len))))

(defn spec-emitted-tokens
  "Accepted tokens plus the bonus token. The verifier's own next-token
  prediction is always emitted — that is why speculative decoding is never
  slower than one token per step."
  [accepted draft-len]
  (if (< draft-len 0) 1 (inc (i-max 0 (i-min accepted draft-len)))))

(defn spec-worth-it?
  "Speculation pays when the accepted fraction beats what the draft cost.
  Equality is NOT worth it: a wash that adds a draft model is a loss in
  complexity, and a scheduler that speculates on a tie keeps doing it forever
  without ever measuring a gain."
  [accept-bp draft-cost-bp]
  (> (i-max 0 accept-bp) (i-max 0 draft-cost-bp)))
