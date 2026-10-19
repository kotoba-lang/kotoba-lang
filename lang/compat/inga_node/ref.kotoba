(ns inga-node.ref
  "`inga.ref` composed over a witness's surface: one `IRefStore` that reads a
  head, proves it was committed, and compare-and-sets by submitting.

  ADR-2608198200 left three parts measured and never assembled — the
  `head-of!` seam, the `inga.commitment` verifier, and a witness that serves
  `{block, qc}` with the head. This is the assembly.

  ## What it is not: an async store

  `kotobase.storage.core/-compare-and-set-ref!` is **synchronous** and a
  commit is not, so every seam here has to block. On Node nothing can, which
  is why this namespace is `.cljc` with the transport injected rather than a
  `.cljs` HTTP client: a JVM consumer supplies `java.net.http` and a real
  sleep, and a Node caller supplies a transport that is already local. The
  socket is the caller's, and so is the decision about whether it can wait.

  This is `inga.ref`'s own reading — its docstring says the waiting lives in
  the host — made concrete rather than restated.

  ## Seams

    committed  (fn [ref-name] -> {:head map :proof edn-string} | nil)
    submit!    (fn [record] -> anything)   the caller's POST /submit
    sleep!     (fn [ms] -> anything)       blocking, or the poll never yields
    context    the `inga.commitment` verification context
    tries      how many polls before a proposal is reported unresolved
    poll-ms    how long between them"
  (:require [inga.commitment :as cm]
            [inga.head :as head]
            [inga.ref :as iref]
            [inga-node.proof :as proof]))

(defn verified-head
  "The head of `ref-name`, proved committed, or nil.

  Absent rather than error on every failure — no answer, no proof, a proof
  that does not verify — because that is the reading `inga.head/verify-head`
  and `inga.commitment/verify-head` both give, and a caller already knows how
  to handle an empty ref. A host that serves something unverifiable is
  indistinguishable from one that has nothing, which is the point of not
  trusting it."
  [{:keys [committed context]} ref-name]
  (when-let [{:keys [head proof]} (committed ref-name)]
    (when-let [p (proof/decode proof)]
      (cm/verify-head head ref-name p context))))

(defn- outcome-for
  "Did `record` win its sequence, given the head that is now committed?

  Three cases and only the first is a win:

  - the head IS this sequence and holds this CID — won
  - the head is this sequence and holds another CID — lost, and the caller is
    told which, so it retries against the right base rather than the one it
    already knows is stale
  - the head is PAST this sequence — someone else's record occupies it and the
    plane has moved on. Reported as a loss against the current head rather
    than guessed at: this store cannot see the record at a sequence the head
    is no longer at, and answering `won` because it cannot see the winner is
    exactly the failure `inga.ref/project`'s first-wins rule exists to stop."
  [record head]
  (let [want (get record "seq")
        got (get head "seq")]
    (cond
      (nil? head) {:certified? false :current nil}
      (and (= want got) (= (get record "cid") (get head "cid")))
      {:certified? true :cert nil}
      :else {:certified? false :current (get head "cid")})))

(defn- propose!
  [{:keys [submit! sleep! tries poll-ms] :or {tries 80 poll-ms 500} :as config} record]
  (submit! record)
  (loop [n tries]
    (let [head (verified-head config (get record "ref"))]
      (cond
        ;; A head at or past this sequence is an answer either way.
        (and head (>= (get head "seq") (get record "seq")))
        (outcome-for record head)

        (zero? n)
        ;; Not `certified? false` with no head: a caller told it lost would
        ;; retry against a base it never saw, and a timeout is not a loss.
        {:certified? false :current (get head "cid") :unresolved? true}

        :else (do (sleep! poll-ms) (recur (dec n)))))))

(defn ref-store
  "The store. `write-head!` is a no-op and says so: a commitment plane
  publishes a head by committing it, and `propose!` above has already waited
  for that — `inga.ref` still reads back afterwards, which is the difference
  between the quorum decided and it is readable."
  [{:keys [committed submit! sleep! context] :as config}]
  (doseq [[k v] {:committed committed :submit! submit! :sleep! sleep!}]
    (when-not (ifn? v)
      (throw (ex-info "inga-node.ref: missing or non-callable seam"
                      {:type :inga-node.ref/invalid-seam :seam k}))))
  (when-not (map? context)
    (throw (ex-info "inga-node.ref: a verification context is required"
                    {:type :inga-node.ref/invalid-context})))
  (iref/ref-store
   {:head-of! (fn [ref-name] (verified-head config ref-name))
    :propose! (fn [record] (propose! config record))
    :write-head! (fn [_ _] nil)}))

(defn head-record
  "Re-exported so a caller building records for `submit!` uses the same
  constructor the verifier does. Two spellings of one record is how the
  producer and the verifier drift."
  [m]
  (head/head-record m))
