(ns arrangement.range-index
  "The order-revealing half of a blinded index, and the budget it spends.

  ## Why there has to be a second index at all

  `arrangement.core/index-root` builds leaf keys as
  `(pr-str [(blind-fn a) (blind-fn b) (blind-fn c)])` — a keyed, deterministic
  MAC. Its own docstring is precise about what that buys and what it does not:
  the key is *queryable by prefix for a caller who already knows the plaintext
  component*, and it is **not order-preserving**. Equality, yes. Order, no.

  Measured consequence (superproject ADR-2608170400 P4-4, 2026-08-17): on a
  corpus where one attribute holds 1,000 quads of 21,000, a value range
  returning ten results and one returning a thousand cost **byte for byte the
  same** — 11 blocks, 248,177 bytes. The attribute cut is worth 8x the blocks
  and 17x the bytes against a full hydrate; the value cut is worth exactly
  nothing. That is not a defect in the MAC. It is what a MAC is.

  So a range that prunes needs a *different key*, in a *different index*, and
  that index reveals something the equality index does not. This namespace is
  the mechanism for that key, and the accounting for what it costs.

  ## The budget, stated

  A range index here partitions an attribute's value domain into buckets at
  **declared** boundaries, and puts the bucket ordinal in the leaf key **in
  clear**. Everything else in the key stays blinded.

  What an observer holding the blocks learns, that they did not learn before:

  - **The bucket of every value of that attribute.** With `b` buckets that is
    at most `log2(b)` bits per value, and — because they can count leaves —
    the exact histogram of values over buckets.
  - **Order between buckets.** Two values in different buckets are ordered,
    and the observer knows which way.

  What they still do not learn:

  - **The value.** The bucket is a range, not a number, and the value slot
    remains AEAD ciphertext.
  - **Order within a bucket.** Values sharing a bucket are unordered to them.
  - **Anything at all about attributes without a declared partition.** There
    is no default: an attribute nobody declared has no range index and no
    range leakage.

  Equality leakage is unchanged, because it was already there: the MAC is
  deterministic, and `blind`'s own README states the matching cost for the
  value slot (\"deterministic encryption leaks equality of plaintexts\").

  ## The budget is a checked argument, not a comment

  `budget-bits` is **required** in a partition and is verified against the
  boundaries. Declaring 3 bits and passing 100 boundaries is rejected. This is
  the whole reason the field exists: a leakage budget that lives only in prose
  is one nobody can be wrong about, and being wrong about it is the failure
  mode that matters.

  ## What is deliberately absent

  **There is no function here that derives boundaries from data.** Choosing
  cut points from the corpus leaks the corpus distribution into a parameter
  that is then published in clear, which is a strictly larger disclosure than
  the one budgeted above — and it would happen silently, because the result
  looks like a well-tuned index. Boundaries are a property of the attribute's
  *declared domain* (an age is 0..130, a score is 0..100, a timestamp is a
  calendar), and they come from the caller. Enforcement is by omission: the
  function does not exist, so it cannot be reached by accident."
  (:require [kotoba.lang.text :as str]))

;; ── partitions ──────────────────────────────────────────────────────────────

(defn- pow2 [n] (reduce * 1 (repeat n 2)))

(defn bits-for
  "Bits needed to name one of `n` buckets — ceil(log2 n), and 0 for n<=1.

  A single bucket names nothing and leaks nothing, which is why it is 0 rather
  than 1: the observer learns \"this value is somewhere in the domain\", which
  they knew."
  [n]
  (if (<= n 1)
    0
    (loop [bits 1]
      (if (>= (pow2 bits) n) bits (recur (inc bits))))))

(defn validate-partition!
  "Check a partition and return it, or throw.

  ```clojure
  {:attr \"score\" :boundaries [25 50 75] :budget-bits 2}
  ```

  `boundaries` are the cut points, strictly increasing, in the attribute's own
  value order; `n` of them make `n+1` buckets. `budget-bits` is what the
  caller claims this partition discloses per value, and it must equal
  `bits-for` the bucket count — the point of the field is to be checkable."
  [{:keys [attr boundaries budget-bits] :as partition}]
  (when (nil? attr)
    (throw (ex-info "range-index: a partition needs the attribute it is for"
                    {:type ::no-attr :partition partition})))
  (when-not (and (sequential? boundaries) (seq boundaries))
    (throw (ex-info "range-index: a partition needs at least one boundary"
                    {:type ::no-boundaries :partition partition})))
  (when-not (apply distinct? boundaries)
    (throw (ex-info "range-index: boundaries must be strictly increasing"
                    {:type ::unsorted-boundaries :boundaries boundaries})))
  (when-not (= (vec boundaries) (vec (sort boundaries)))
    (throw (ex-info "range-index: boundaries must be strictly increasing"
                    {:type ::unsorted-boundaries :boundaries boundaries})))
  (let [n (inc (count boundaries))
        need (bits-for n)]
    (when (nil? budget-bits)
      (throw (ex-info (str "range-index: state the leakage budget. This "
                           "partition discloses " need " bits per value.")
                      {:type ::no-budget :buckets n :required-bits need})))
    (when-not (= budget-bits need)
      (throw (ex-info (str "range-index: declared budget " budget-bits
                           " bits, but " n " buckets disclose " need)
                      {:type ::budget-mismatch :declared budget-bits
                       :buckets n :required-bits need}))))
  partition)

;; ── buckets ─────────────────────────────────────────────────────────────────

(defn bucket-of
  "Which bucket `v` falls in: the count of boundaries it is greater than or
  equal to. Bucket `i` therefore holds `[boundary(i-1), boundary(i))`,
  half-open the same way every range in this stack is."
  [boundaries v]
  (count (take-while #(not (neg? (compare v %))) boundaries)))

(def ^:private token-width
  "Fixed width so that STRING order is BUCKET order. The leaf key is a
  `pr-str`ed vector of strings and the tree is ordered by that string, so a
  token whose length varied would sort \"10\" before \"9\" and a range scan
  would silently return the wrong leaves."
  6)

(defn bucket-token
  "The clear-text, order-preserving token for bucket `i`.

  This is the disclosure. Everything else in the key is a MAC."
  [i]
  (let [s (str i)]
    (when (> (count s) token-width)
      (throw (ex-info "range-index: more buckets than the token can name"
                      {:type ::token-overflow :bucket i :width token-width})))
    (str (str/join (repeat (- token-width (count s)) "0")) s)))

(defn buckets-for-range
  "The inclusive bucket ordinals a query for `[lo, hi)` must read.

  Returns `{:from i :to j}`, or **nil** for an empty interval. `nil` bounds
  are open.

  Empty and inverted are not the same thing and are not treated the same way.
  `lo = hi` is a legitimate degenerate query — half-open, so it selects
  nothing — and nil says so. `lo > hi` is a caller bug, and returning nothing
  for it would be this ADR's own recurring failure: a call that could not mean
  anything answering exactly like one that meant nothing. It throws.

  Conservative on purpose in the other direction, like `prolly-tree.diff`: a
  bucket that turns out to hold nothing in range costs a read, whereas
  skipping one that holds a match loses a row silently."
  [boundaries lo hi]
  (when (and (some? lo) (some? hi) (pos? (compare lo hi)))
    (throw (ex-info "range-index: inverted interval — lo is above hi"
                    {:type ::inverted-interval :lo lo :hi hi})))
  (when-not (and (some? lo) (some? hi) (zero? (compare lo hi)))
    (let [last-bucket (count boundaries)
          from (if (nil? lo) 0 (bucket-of boundaries lo))
          ;; `hi` is exclusive, but a value equal to a boundary opens the NEXT
          ;; bucket, so taking the bucket of `hi` itself is the safe side: it
          ;; may read one bucket that holds nothing in range, and it cannot
          ;; skip one that holds a match.
          to (if (nil? hi) last-bucket (bucket-of boundaries hi))]
      {:from (max 0 from) :to (min last-bucket to)})))

;; ── keys ────────────────────────────────────────────────────────────────────

(defn leaf-key
  "The range-index leaf key for one quad.

  `[blinded-attr, bucket-token, blinded-value, blinded-subject]` — position 2
  is the only clear component, and it is the budgeted one. Four components
  rather than three so the key stays unique when two subjects share a value."
  [blinded-attr bucket blinded-value blinded-subject]
  (pr-str [blinded-attr (bucket-token bucket) blinded-value blinded-subject]))

(defn key-bounds
  "String bounds `[lo-key hi-key)` for a scan of buckets `[from, to]` under
  one blinded attribute.

  The upper bound names bucket `to + 1`, because the tree's range scan is
  half-open and bucket `to` must be included whole."
  [blinded-attr {:keys [from to]}]
  [(str "[" (pr-str blinded-attr) " " (pr-str (bucket-token from)))
   (str "[" (pr-str blinded-attr) " " (pr-str (bucket-token (inc to))))])
