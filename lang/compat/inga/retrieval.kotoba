(ns inga.retrieval
  "F3's last piece: crediting `:storage` power by asking a witness to produce
  blocks it claims to hold.

  ## Why this is cheap here and expensive in Filecoin

  Filecoin needs PoRep and PoSt because a storage proof there has to survive
  an adversary who can fetch the data from anywhere and who is paid to appear
  to store it. Those proofs are enormous machinery.

  A datom plane needs much less for a much weaker claim, because **the data is
  content-addressed**. Asking \"return the bytes for this CID\" has an answer
  that verifies itself: hash what came back and compare. There is no trusted
  party, no setup, no sector, and no proving time. The whole verifier is one
  hash and one comparison.

  ## What a passing sample proves, and the three things it does not

  It proves: **at sample time, this witness could produce these bytes.**

  It does NOT prove
  1. **durable storage.** A witness that fetched the block from a peer the
     moment it was asked passes. Nothing here distinguishes holding from
     fetching.
  2. **unique storage.** Ten witnesses can pass on one physical copy.
  3. **future availability.** The sample is about the instant it ran.

  The economic argument against (1) is that fetching on demand costs more than
  storing when samples are frequent and unpredictable enough — and that is an
  ARGUMENT, not a measurement, so no deployment using this may claim
  Filecoin-equivalent storage guarantees. Superproject ADR-2608038000 F3 says
  the same in one line; this says it where someone is about to rely on it.

  ## Unpredictable, and verifiable that it was unpredictable

  A witness that can predict its challenge stores only what will be asked for.
  So the challenge is derived from a value fixed AFTER the witness's storage
  claim and by nobody in particular — a committed block hash — through an
  injected `hash-fn`. Deriving it from wall-clock time or a caller's choice
  would let the caller pick a witness's fate, which is the same hole from the
  other side.

  Pure: no I/O, no crypto, no clock. `hash-fn` and `cid-of` are injected, the
  same seam every other namespace here uses."
  (:require [kotoba.lang.text :as str]))

(def ^:private hex-tail 8)

(defn- index-from
  "A deterministic index in `[0, n)` from a hash string.

  Reads the LAST `hex-tail` characters rather than the first: some hash
  encodings carry a fixed prefix (a multihash header, a version byte), and an
  index taken from the front of one of those is the same number for every
  input — which looks like a working selector and samples one block forever."
  [h n]
  (if (or (nil? h) (zero? n))
    0
    (let [s (str h)
          tail (subs s (max 0 (- (count s) hex-tail)))
          v (reduce (fn [acc c] (+ (* acc 31) (int c))) 0 tail)]
      (mod (Math/abs v) n))))

(defn challenge
  "Which CIDs `witness` must produce this round.

  `seed` is a value fixed after the storage claim and by nobody in
  particular — a committed block hash. `held` is the CIDs the witness claims,
  in any order; it is sorted here so two verifiers derive the same challenge
  from the same claim.

  Returns `{:witness :seed :cids}`. Fewer than `n` cids when the witness
  claims fewer, and an empty challenge when it claims none — a witness with
  nothing to prove is not thereby proven."
  [{:keys [seed witness held n hash-fn]}]
  (when-not (ifn? hash-fn)
    (throw (ex-info "inga.retrieval: hash-fn is required"
                    {:type :inga.retrieval/invalid-seam})))
  (let [claimed (vec (sort (distinct held)))
        want (min (or n 1) (count claimed))]
    {:witness witness
     :seed seed
     :cids (loop [i 0 picked [] pool claimed]
             (if (or (>= i want) (empty? pool))
               picked
               (let [h (hash-fn (str seed "|" witness "|" i))
                     idx (index-from h (count pool))
                     cid (nth pool idx)]
                 (recur (inc i) (conj picked cid)
                        (vec (concat (subvec pool 0 idx) (subvec pool (inc idx))))))))}))

(defn judge
  "Did the witness answer its challenge?

  `responses` is `{cid bytes}`. `cid-of` is `(fn [bytes] -> cid-string)` —
  the caller's content addressing, injected, because a verifier that computed
  CIDs its own way would be checking a different question than the one the
  store answers.

  Returns `{:witness :passed :failed :verdict}`. A response whose bytes hash
  to a DIFFERENT cid counts as failed rather than missing, and the difference
  matters: missing is 'I do not have it', wrong is 'I gave you something
  else', and only the second is evidence of anything but absence."
  [{:keys [witness cids]} responses cid-of]
  (let [{:keys [passed failed]}
        (reduce (fn [acc cid]
                  (let [bytes (get responses cid)]
                    (cond
                      (nil? bytes) (update acc :failed conj {:cid cid :why :missing})
                      (= cid (cid-of bytes)) (update acc :passed conj cid)
                      :else (update acc :failed conj {:cid cid :why :wrong-bytes}))))
                {:passed [] :failed []}
                cids)]
    {:witness witness
     :passed passed
     :failed failed
     ;; An empty challenge is `:unproven`, not `:pass`. A witness that claims
     ;; nothing would otherwise sail through every round it is asked about,
     ;; which is the cheapest possible way to look like a storage provider.
     :verdict (cond
                (empty? cids) :unproven
                (seq failed) :fail
                :else :pass)}))

(defn ->power-event
  "The `inga.power` event a verdict earns, or nil when it earns none.

  Only `:pass` produces one, and it grants the `:storage` role rather than an
  amount: what a sample establishes is eligibility for a duty, and how much
  collateral that duty requires is `inga.stake/required-bond`'s call, not
  this namespace's. Keeping those separate is why `:storage` is a role on the
  existing bond market instead of a second economy."
  [{:keys [witness verdict]}]
  (when (= :pass verdict)
    {:event :set-roles :witness witness :roles [:storage]}))

(defn summarize
  "One line per witness, for an operator watching a round. Sorted, so two
  runs of the same round print the same thing."
  [verdicts]
  (->> verdicts
       (sort-by :witness)
       (map (fn [{:keys [witness verdict passed failed]}]
              (str witness " " (name verdict)
                   " " (count passed) "/" (+ (count passed) (count failed))
                   (when (seq failed)
                     (str " (" (str/join "," (map #(name (:why %)) failed)) ")")))))
       vec))
