(ns kotoba.reservation
  "Reservable inventory, holds, rate plans and quotes — pure data contracts.

  A kotoba-lang capability library for the reservation-bearing
  cloud-itonami open businesses: passenger air transport
  (`cloud-itonami-isic-5110`), accommodation (`-5510`/`-5590`), travel
  agency (`-7911`), tour operator (`-7912`) and other reservation
  services (`-7990`). No network, no I/O, no ambient clock. Models the
  two things every one of those businesses needs and that no prior
  kotoba-lang capability library supplied: **how many are left to sell**
  and **what this one costs**.

  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM.

  ## Determinism, and why there are no floats here

  Every amount is an integer in the currency's MINOR UNIT (JPY: 1 = ¥1;
  USD: 1 = 1 cent). Every rate modifier is an integer in BASIS POINTS
  (`bp-scale` = 10000 bp = 100%), applied with truncating integer
  division. There is no floating-point arithmetic anywhere in this
  namespace, so a quote recomputed on a different runtime is bit-identical
  to the one recomputed here — which is the whole point of `quote-total`
  being independently re-runnable by a governor (see
  `quote-matches-claim?`).

  Truncation is a real decision, not an oversight: `apply-bp` rounds
  toward zero, so a modifier can only ever move a fare DOWN by a
  sub-unit, never up. An operator whose filed fare rules require a
  different rounding must apply it in its own filing and pass the
  resulting `:rate/base-amount` — this library will not silently pick a
  rounding convention on their behalf.

  ## No clock; calendar only when you ask for it

  `expired?` takes `now` as an argument, and nothing in this namespace
  ever reads a clock. That one matters for reproducibility: a governor
  recomputing a proposal minutes later must get the same answer, so
  wall-clock time is always an argument, never an ambient read.

  `quote-for` likewise takes each date's weekday as data rather than
  deriving it. The reason is NOT reproducibility — deriving the weekday
  of \"2026-08-01\" is deterministic and would give the same answer
  forever. (An earlier version of this docstring claimed otherwise; it
  was wrong, and the wrong rationale is corrected here rather than left
  for the next reader to copy.) The actual reasons are that the core
  pricing path should not bake in one calendar system, and that callers
  may key modifiers on something other than a proleptic-Gregorian
  weekday — a fiscal week, a local holiday table, a season.

  For callers who DO want proleptic-Gregorian arithmetic, `weekday-of`
  and `nights-between` provide it explicitly, as opt-in pure functions.
  They are how an accommodation caller turns a stay's own check-in and
  check-out into the nights to price, so that a governor recomputes the
  total from the booking's own dates rather than from a night list the
  advisor supplied and could have shortened."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Calendar — opt-in, proleptic Gregorian, pure integer arithmetic
;; ---------------------------------------------------------------------------
;; Nothing above or below calls into this section. It exists so a caller
;; that wants Gregorian day arithmetic can ask for it explicitly instead
;; of pulling in a date library or hand-rolling the algorithm again.

(defn parse-date
  "Parse an ISO `YYYY-MM-DD` date into `[y m d]` integers, or nil when the
  string is not that shape. Does not validate that the date exists —
  `2026-02-31` parses; `days-from-civil` will simply place it where the
  arithmetic puts it."
  [s]
  (when (string? s)
    (when-let [[_ y m d] (re-matches #"(\d{4})-(\d{2})-(\d{2})" s)]
      [(parse-long y) (parse-long m) (parse-long d)])))

(defn days-from-civil
  "Days since 1970-01-01 for a proleptic-Gregorian `[y m d]`. Howard
  Hinnant's civil-from-days inverse: exact integer arithmetic, no
  floating point, no locale, no clock."
  [[y m d]]
  (let [y (if (<= m 2) (dec y) y)
        era (quot (if (>= y 0) y (- y 399)) 400)
        yoe (- y (* era 400))                                  ; [0, 399]
        doy (+ (quot (+ (* 153 (+ m (if (> m 2) -3 9))) 2) 5) (dec d))
        doe (+ (* yoe 365) (quot yoe 4) (- (quot yoe 100)) doy)]
    (+ (* era 146097) doe -719468)))

(defn weekday-of
  "Proleptic-Gregorian weekday for an ISO date string: 0=Sunday .. 6=Saturday,
  matching the keys `rate-plan`'s `:weekday-bp` uses. nil for an
  unparseable date.

  Opt-in on purpose — see the ns docstring. `quote-for` will never call
  this for you."
  [s]
  (when-let [ymd (parse-date s)]
    (mod (+ 4 (days-from-civil ymd)) 7)))                      ; 1970-01-01 was a Thursday

(defn civil-from-days
  "Inverse of `days-from-civil`: days since 1970-01-01 -> `[y m d]`."
  [z]
  (let [z (+ z 719468)
        era (quot (if (>= z 0) z (- z 146096)) 146097)
        doe (- z (* era 146097))                               ; [0, 146096]
        yoe (quot (+ (- doe (quot doe 1460)) (quot doe 36524) (- (quot doe 146096))) 365)
        y (+ yoe (* era 400))
        doy (- doe (+ (* 365 yoe) (quot yoe 4) (- (quot yoe 100))))
        mp (quot (+ (* 5 doy) 2) 153)                          ; [0, 11]
        d (+ (- doy (quot (+ (* 153 mp) 2) 5)) 1)              ; [1, 31]
        m (+ mp (if (< mp 10) 3 -9))]                          ; [1, 12]
    [(if (<= m 2) (inc y) y) m d]))

(defn- pad2 [n] (if (< n 10) (str "0" n) (str n)))

(defn format-date
  "`[y m d]` -> ISO `YYYY-MM-DD`."
  [[y m d]]
  (str y "-" (pad2 m) "-" (pad2 d)))

(defn nights-between
  "The nights of a stay from `check-in` to `check-out`, as
  `[{:date d :weekday w} ..]` ready to hand to `quote-for` as `:dates`.

  Half-open, the way accommodation actually bills: a stay of
  2026-08-01 -> 2026-08-05 is FOUR nights (the 1st, 2nd, 3rd and 4th),
  not five. Returns [] when check-out is on or before check-in, and nil
  when either date is unparseable — a caller that gets nil must treat
  the stay as un-priceable rather than as a free one."
  [check-in check-out]
  (let [a (parse-date check-in), b (parse-date check-out)]
    (when (and a b)
      (let [from (days-from-civil a), to (days-from-civil b)]
        (if (<= to from)
          []
          (mapv (fn [n]
                  (let [ymd (civil-from-days n)]
                    {:date (format-date ymd)
                     :weekday (mod (+ 4 n) 7)}))
                (range from to)))))))

;; ---------------------------------------------------------------------------
;; Money — integer minor units, integer basis points
;; ---------------------------------------------------------------------------

(def bp-scale
  "Basis-point scale: 10000 bp = 100% = unchanged."
  10000)

(defn apply-bp
  "Apply an integer basis-point modifier to an integer minor-unit amount,
  truncating toward zero. `(apply-bp 10000 12000)` => 12000 (120%)."
  [amount bp]
  (quot (* amount (or bp bp-scale)) bp-scale))

(defn- non-neg-int? [n]
  (and (integer? n) (not (neg? n))))

;; ---------------------------------------------------------------------------
;; Inventory bucket — the "how many are left" contract
;; ---------------------------------------------------------------------------

(defn bucket
  "Construct an inventory bucket: `capacity` sellable units of `class` on
  `resource` for `date`. A bucket is the atom of reservable inventory —
  one fare class on one flight-date, or one room type on one stay-date.

  `:overbook` is the operator's DELIBERATE authorized overbooking
  allowance, in units, and it defaults to 0. It is a separate field from
  capacity on purpose: `available` may legitimately sell into it, but
  `oversold?` still reports the truth about physical capacity, so an
  operator can never lose track of how far past the aircraft/property
  they have sold.

  Returns nil for a structurally invalid bucket rather than a bucket
  that lies about its own numbers."
  [resource date class capacity & {:keys [sold held overbook]}]
  (let [sold (or sold 0), held (or held 0), overbook (or overbook 0)]
    (when (and resource date class
               (non-neg-int? capacity) (non-neg-int? sold)
               (non-neg-int? held) (non-neg-int? overbook))
      {:inv/resource resource
       :inv/date     date
       :inv/class    class
       :inv/capacity capacity
       :inv/sold     sold
       :inv/held     held
       :inv/overbook overbook})))

(defn available
  "Units still sellable: capacity + authorized overbooking − sold − held.
  May be negative when a bucket is already oversold; callers should test
  `sellable?` rather than assume this is a count they can sell."
  [b]
  (- (+ (:inv/capacity b 0) (:inv/overbook b 0))
     (:inv/sold b 0)
     (:inv/held b 0)))

(defn oversold?
  "True when sold + held has passed PHYSICAL capacity — independent of
  whether the operator's authorized overbooking allowance still covers
  it. Selling into an authorized allowance is a business decision;
  pretending it is not oversold is not."
  [b]
  (> (+ (:inv/sold b 0) (:inv/held b 0)) (:inv/capacity b 0)))

(defn sellable?
  "Can `qty` more units be sold from this bucket without exceeding
  capacity + authorized overbooking?"
  [b qty]
  (and (non-neg-int? qty) (>= (available b) qty)))

;; ---------------------------------------------------------------------------
;; Holds — inventory reserved but not yet sold
;; ---------------------------------------------------------------------------

(defn- before?
  "Comparable ordering for the two time representations this library
  accepts: integers (epoch millis) and ISO-8601 UTC strings (which sort
  lexicographically). Mixed types are NOT comparable and never compare
  true — a caller mixing them gets a hold that never expires rather
  than one that expires at a garbage time."
  [a b]
  (cond
    (and (number? a) (number? b)) (< a b)
    (and (string? a) (string? b)) (neg? (compare a b))
    :else false))

(defn hold
  "Construct a hold of `qty` units against `b`, expiring at `expires-at`
  (epoch millis or an ISO-8601 UTC string). A hold is a claim on
  inventory, not a sale: it suppresses `available` until it is confirmed
  (becomes sold), released, or expires."
  [id b qty expires-at]
  (when (and id b (non-neg-int? qty) (pos? qty))
    {:hold/id         id
     :hold/resource   (:inv/resource b)
     :hold/date       (:inv/date b)
     :hold/class      (:inv/class b)
     :hold/qty        qty
     :hold/expires-at expires-at
     :hold/status     :held}))

(defn expired?
  "Has `h` expired as of `now`? Never reads a clock — see the ns
  docstring."
  [h now]
  (boolean (and (:hold/expires-at h) (before? (:hold/expires-at h) now))))

(defn covers?
  "Does hold `h` refer to the same resource/date/class as bucket `b`?"
  [h b]
  (and (= (:hold/resource h) (:inv/resource b))
       (= (:hold/date h) (:inv/date b))
       (= (:hold/class h) (:inv/class b))))

(defn place-hold
  "Place `h` against `b`. Returns
  `{:reservation/ok? bool :reservation/error kw :reservation/bucket b'}`.
  Refuses (`:insufficient-inventory`) rather than overselling — this is
  the check a governor re-runs independently before letting a booking
  proposal commit."
  [b h]
  (cond
    (not (covers? h b))          {:reservation/ok? false :reservation/error :hold-bucket-mismatch}
    (not (sellable? b (:hold/qty h))) {:reservation/ok? false :reservation/error :insufficient-inventory}
    :else {:reservation/ok? true
           :reservation/bucket (update b :inv/held + (:hold/qty h))}))

(defn release-hold
  "Release `h` back to `b` (expired, cancelled, or abandoned). Held units
  never go negative."
  [b h]
  (if-not (covers? h b)
    {:reservation/ok? false :reservation/error :hold-bucket-mismatch}
    {:reservation/ok? true
     :reservation/bucket (update b :inv/held #(max 0 (- % (:hold/qty h))))}))

(defn confirm-hold
  "Convert `h` from held to sold on `b` — the only transition that
  increments `:inv/sold`. Refuses if the hold does not belong to the
  bucket or if the bucket does not actually carry that many held units
  (a double-confirm would otherwise silently manufacture sold seats)."
  [b h]
  (cond
    (not (covers? h b))                     {:reservation/ok? false :reservation/error :hold-bucket-mismatch}
    (< (:inv/held b 0) (:hold/qty h))       {:reservation/ok? false :reservation/error :hold-not-on-bucket}
    :else {:reservation/ok? true
           :reservation/bucket (-> b
                                   (update :inv/held - (:hold/qty h))
                                   (update :inv/sold + (:hold/qty h)))
           :reservation/hold (assoc h :hold/status :confirmed)}))

(defn purge-expired
  "Release every hold in `holds` that has expired as of `now`. Returns
  `{:reservation/bucket b' :reservation/released [hold ..]}`."
  [b holds now]
  (let [dead (filterv #(and (covers? % b) (= :held (:hold/status %)) (expired? % now)) holds)]
    {:reservation/bucket (reduce (fn [acc h] (:reservation/bucket (release-hold acc h))) b dead)
     :reservation/released dead}))

;; ---------------------------------------------------------------------------
;; Rate plans — the "what this one costs" contract
;; ---------------------------------------------------------------------------

(defn rate-plan
  "Construct a rate plan: `base-amount` minor units per unit per date, in
  `currency`, for `class`.

  Optional restrictions and modifiers:
    :refundable?   -- bool, carried for disclosure, never priced here
    :min-units     -- minimum units per booking
    :advance-days  -- minimum days between booking and first dated unit
    :weekday-bp    -- {weekday-int -> bp} (0=Sunday .. 6=Saturday)
    :date-bp       -- {date -> bp}, season/event overrides; a date entry
                      WINS over a weekday entry for the same date
    :fees          -- [{:label str :amount minor-units} ..] per booking
    :tax-bp        -- bp applied to the line subtotal"
  [id class base-amount currency
   & {:keys [refundable? min-units advance-days weekday-bp date-bp fees tax-bp]}]
  (when (and id class (non-neg-int? base-amount) currency)
    {:rate/id           id
     :rate/class        class
     :rate/base-amount  base-amount
     :rate/currency     currency
     :rate/refundable?  (boolean refundable?)
     :rate/min-units    (or min-units 1)
     :rate/advance-days (or advance-days 0)
     :rate/weekday-bp   (or weekday-bp {})
     :rate/date-bp      (or date-bp {})
     :rate/fees         (vec (or fees []))
     :rate/tax-bp       (or tax-bp 0)}))

(defn modifier-bp
  "The basis-point modifier that applies to `date`/`weekday` under `plan`.
  A `:date-bp` entry wins over a `:weekday-bp` entry; absent both, 100%."
  [plan date weekday]
  (or (get (:rate/date-bp plan) date)
      (get (:rate/weekday-bp plan) weekday)
      bp-scale))

(defn restrictions-satisfied?
  "Check a booking request against `plan`'s restrictions. Returns
  `{:reservation/ok? bool :reservation/violations [kw ..]}` — a vector,
  because a request can fail several restrictions at once and an
  operator deserves to see all of them, not just the first."
  [plan {:keys [units advance-days class]}]
  (let [v (cond-> []
            (and (:rate/min-units plan) units (< units (:rate/min-units plan)))
            (conj :below-min-units)

            (and (:rate/advance-days plan) (some? advance-days)
                 (< advance-days (:rate/advance-days plan)))
            (conj :inside-advance-purchase-window)

            (and class (not= class (:rate/class plan)))
            (conj :class-mismatch))]
    {:reservation/ok? (empty? v) :reservation/violations v}))

;; ---------------------------------------------------------------------------
;; Quote — deterministic, independently recomputable
;; ---------------------------------------------------------------------------

(defn- normalize-date
  "Accept either a bare date or `{:date d :weekday w}`. Weekday is DATA,
  never derived — see the ns docstring."
  [d]
  (if (map? d) [(:date d) (:weekday d)] [d nil]))

(defn quote-for
  "Compute a quote for `plan` over `:dates` at `:qty` units each.

  `:dates` is a coll of dates, each either a bare date value or
  `{:date d :weekday w}`. Every date becomes one `:unit` line priced at
  `base-amount × qty × modifier`. Plan fees become `:fee` lines; the
  plan's `:tax-bp` becomes one `:tax` line on the line subtotal.

  Returns
  `{:quote/lines [..] :quote/subtotal n :quote/tax n :quote/total n
    :quote/currency c :quote/qty n}` — all integers in minor units.
  Given the same plan and request, this returns the same total on every
  runtime, forever; that is what makes `quote-matches-claim?` a real
  check rather than a restatement of the claim.

  Returns nil rather than throwing when it cannot price the request: no
  rate plan, a plan with no numeric base amount, or a non-numeric `qty`.
  A caller handed junk gets an un-priceable quote, not a
  ClassCastException out of its own governor -- this library is called
  BY the checks that are supposed to catch bad input, so it must not be
  the thing that crashes on it. `quote-total` reads nil as 0 for
  display, and `quote-matches-claim?` never matches a nil quote."
  [plan {:keys [dates qty fees]}]
  (when (and (map? plan) (number? (:rate/base-amount plan))
             (or (nil? qty) (number? qty)))
  (let [qty (or qty 1)
        unit-lines (for [d dates
                         :let [[date weekday] (normalize-date d)
                               bp (modifier-bp plan date weekday)]]
                     {:line/kind   :unit
                      :line/date   date
                      :line/qty    qty
                      :line/bp     bp
                      :line/amount (apply-bp (* (:rate/base-amount plan) qty) bp)})
        fee-lines (for [f (concat (:rate/fees plan) (or fees []))]
                    {:line/kind :fee
                     :line/label (:label f)
                     :line/amount (:amount f)})
        subtotal (reduce + 0 (map :line/amount (concat unit-lines fee-lines)))
        tax (apply-bp subtotal (:rate/tax-bp plan 0))
        tax-lines (when (pos? tax)
                    [{:line/kind :tax :line/label "tax" :line/bp (:rate/tax-bp plan) :line/amount tax}])]
    {:quote/lines    (vec (concat unit-lines fee-lines tax-lines))
     :quote/qty      qty
     :quote/subtotal subtotal
     :quote/tax      tax
     :quote/total    (+ subtotal tax)
     :quote/currency (:rate/currency plan)})))

(defn quote-total
  "The total of a quote map, in minor units."
  [q]
  (:quote/total q 0))

;; ---------------------------------------------------------------------------
;; Independent verification — the governor seam
;; ---------------------------------------------------------------------------

(defn quote-matches-claim?
  "Recompute the quote from `plan` + `req` and compare it to
  `claimed-total`.

  This exists so a governor can verify a priced proposal WITHOUT
  trusting the advisor that produced it: an LLM can draft a fare and
  state a total, but it cannot be trusted to have actually done the
  arithmetic. This is a pure ground-truth recompute against the plan's
  own filed fields — not a judgment about whether the fare is a good
  one, and not an authority to file a fare."
  [plan req claimed-total]
  ;; An UN-PRICEABLE request must not match a claim of zero. `quote-total`
  ;; reads a nil quote as 0 for display purposes, so comparing through it
  ;; would let a junk request pass by claiming nothing was owed.
  (let [q (quote-for plan req)]
    (boolean (and q (number? claimed-total)
                  (= (quote-total q) claimed-total)))))

(defn availability-supports?
  "Can every `[bucket qty]` pair in `requests` be satisfied without
  exceeding capacity + authorized overbooking? The check a governor
  re-runs independently before a booking proposal may commit."
  [requests]
  (every? (fn [[b qty]] (sellable? b qty)) requests))

;; ---------------------------------------------------------------------------
;; Booking
;; ---------------------------------------------------------------------------

(defn booking
  "Construct a booking record binding confirmed holds to a quote. Status
  is one of :held/:confirmed/:cancelled."
  [id holds q & {:keys [status]}]
  (let [st (or status :held)]
    (when (contains? #{:held :confirmed :cancelled} st)
      {:booking/id       id
       :booking/holds    (mapv :hold/id holds)
       :booking/units    (reduce + 0 (map :hold/qty holds))
       :booking/total    (quote-total q)
       :booking/currency (:quote/currency q)
       :booking/status   st})))

(defn confirmed? [bk] (= :confirmed (:booking/status bk)))

;; ---------------------------------------------------------------------------
;; Validation
;; ---------------------------------------------------------------------------

(defn validate-booking
  "Validate a booking request end-to-end: restrictions, then inventory,
  then the claimed total. Returns
  `{:reservation/valid? bool :reservation/errors [kw ..]}`.

  Reports EVERY failing check, not the first — an operator fixing a
  rejected booking should not have to resubmit three times to discover
  three problems."
  [plan req requests claimed-total]
  (let [{:keys [reservation/violations]} (restrictions-satisfied? plan req)
        errs (cond-> (vec violations)
               (not (availability-supports? requests)) (conj :insufficient-inventory)
               (and (some? claimed-total)
                    (not (quote-matches-claim? plan req claimed-total)))
               (conj :quote-mismatch))]
    {:reservation/valid? (empty? errs)
     :reservation/errors errs}))

(defn describe-error
  "Human-readable label for a validation error keyword — for operator
  consoles and audit records, never for control flow."
  [k]
  (case k
    :below-min-units                 "below the plan's minimum units"
    :inside-advance-purchase-window  "inside the advance-purchase window"
    :class-mismatch                  "requested class is not this plan's class"
    :insufficient-inventory          "not enough inventory to satisfy this request"
    :quote-mismatch                  "claimed total does not match the recomputed quote"
    :hold-bucket-mismatch            "hold does not refer to this bucket"
    :hold-not-on-bucket              "bucket does not carry that many held units"
    (str/replace (name k) "-" " ")))
