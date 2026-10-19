(ns kura.node.async
  "The async shard-store contract, and the conformance suite for it.

  ## Why this file exists

  `kura.node.store/IShardStore` is synchronous. Every host a storage node
  actually runs on — Node.js, a Cloudflare Worker — has asynchronous I/O.
  Those two facts are incompatible, and the incompatibility was **silent**:
  `kura.node.s3/send!` returns whatever the injected transport returns, `ok?`
  reads `:status` off it, and a promise has no `:status`, so it is falsy. Every
  read reported the shard absent. Every write reported success.

  The unit suite did not catch it because `FakeHttp` in the tests is
  synchronous — the test and the code were self-consistent and wrong together,
  which is the same failure mode as hex-encoding inside the SigV4 ladder. A
  test that supplies its own world will agree with itself.

  So: a separate async protocol rather than a promise-shaped patch on the sync
  one. `kotobase.storage` split the same way (`async_contract.cljs`) for the
  same reason — a contract that is sometimes synchronous is a contract nobody
  can implement correctly.

  **The sync protocol is not deprecated.** It is right for in-memory and for a
  host with genuinely blocking I/O, and it is what the portable `.cljc` tests
  exercise. What is wrong is pretending an async transport satisfies it.

  Everything here returns a promise. `check` in the suite is
  `(fn [truthy label])` as in `kura.node.contract`, so a caller supplies its
  own assertion — `clojure.test/is`, a CI reporter, or a Worker returning JSON."
  (:require [kura.node.gf :as gf]
            [kura.node.store :as store]))

(defprotocol IAsyncShardStore
  (-put-shard!> [store shard-id bytes] "Promise of `{:shard-id :bytes-written}`.")
  (-get-shard> [store shard-id] "Promise of bytes, or nil when absent.")
  (-get-range> [store shard-id offset length] "Promise of bytes, or nil.")
  (-delete-shard!> [store shard-id] "Promise of true when something was removed.")
  (-list-shards> [store prefix] "Promise of sorted shard ids under the prefix.")
  (-shard-size> [store shard-id] "Promise of byte length, or nil."))

(defn async-store? [x]
  (and (satisfies? IAsyncShardStore x) (satisfies? store/INodeIdentity x)))

;; --- the suite -------------------------------------------------------------

(defn- p [x] (js/Promise.resolve x))

(defn- bytes-of [& xs] (gf/->bytes (vec xs)))

(defn- refuses?>
  "Whether `f` rejects or throws. A backend may refuse either way and the
  contract only requires that it refuses."
  [f]
  (try
    (-> (p (f))
        (.then (fn [_] false))
        (.catch (fn [_] true)))
    (catch :default _ (p true))))

(defn verify>
  "Run the async backend contract against `s`. Returns a promise of nil.

  Same assertions as `kura.node.contract/verify`, chained. Kept as a separate
  function rather than generated from the sync one because the interesting
  failures are in the chaining — a backend that resolves its promises out of
  order passes an assertion-by-assertion translation and fails this."
  [s check]
  (let [a "obj-async/0/0"
        b "obj-async/0/1"
        body (bytes-of 1 2 3 4 5 6 7 8)]
    (check (async-store? s) "implements IAsyncShardStore and INodeIdentity")
    (let [d (store/-descriptor s)]
      (check (and (string? (:node-id d)) (seq (:node-id d))) "declares a node id")
      (check (contains? store/independence-profiles (:independence d))
             "declares exactly one independence profile"))
    (-> (p nil)
        ;; Clear the fixtures BEFORE asserting they are absent, not only after.
        ;;
        ;; The suite already deleted them at the end, which is enough when it
        ;; finishes — and useless when it does not. A run that throws partway
        ;; leaves `a` and `b` on the backend, and the NEXT run then fails
        ;; "absent shard reads as nil" and "absent shard has no size": two
        ;; false failures that say nothing about the defect and point away from
        ;; it. That is the worst possible output for a self-check, because the
        ;; operator runs it precisely when something is already wrong.
        ;;
        ;; Observed: a node on Node 18 threw at case 14 of 19, and the re-run
        ;; reported 17/19 failing the two absent checks instead. The real bug
        ;; was neither of them.
        (.then #(-> (-delete-shard!> s a) (.catch (fn [_] false))))
        (.then #(-> (-delete-shard!> s b) (.catch (fn [_] false))))
        ;; absent
        (.then #(-get-shard> s a))
        (.then (fn [r] (check (nil? r) "absent shard reads as nil")))
        (.then #(-shard-size> s a))
        (.then (fn [r] (check (nil? r) "absent shard has no size")))
        ;; put / get
        (.then #(-put-shard!> s a body))
        (.then (fn [r]
                 (check (= a (:shard-id r)) "put echoes the shard id")
                 (check (= 8 (:bytes-written r)) "put reports what it wrote")))
        (.then #(-get-shard> s a))
        (.then (fn [r] (check (= [1 2 3 4 5 6 7 8] (gf/->vec r))
                              "shard bytes round-trip")))
        (.then #(-shard-size> s a))
        (.then (fn [r] (check (= 8 r) "size without reading the body")))
        ;; idempotent
        (.then #(-put-shard!> s a body))
        (.then #(-get-shard> s a))
        (.then (fn [r] (check (= [1 2 3 4 5 6 7 8] (gf/->vec r))
                              "putting the same shard twice is idempotent")))
        ;; ranges — the claim "S3-compatible" makes and does not guarantee
        (.then #(-get-range> s a 1 3))
        (.then (fn [r] (check (= [2 3 4] (gf/->vec r))
                              "range read returns exactly the range")))
        (.then #(-get-range> s a 0 100))
        (.then (fn [r] (check (= [1 2 3 4 5 6 7 8] (gf/->vec r))
                              "a range past the end clips rather than failing")))
        (.then #(-get-range> s "obj-async/9/9" 0 1))
        (.then (fn [r] (check (nil? r) "a range on an absent shard is nil")))
        ;; listing
        (.then #(-put-shard!> s b (bytes-of 250 251 252)))
        (.then #(-list-shards> s "obj-async/"))
        (.then (fn [r] (check (= [a b] (vec r))
                              "list returns sorted ids under the prefix")))
        (.then #(-list-shards> s "no-such-object/"))
        (.then (fn [r] (check (empty? r) "list of an unknown prefix is empty")))
        ;; deletion
        (.then #(-delete-shard!> s b))
        (.then (fn [r] (check (true? (boolean r)) "delete reports removal")))
        (.then #(-get-shard> s b))
        (.then (fn [r] (check (nil? r) "deleted shard is gone")))
        (.then #(-delete-shard!> s b))
        (.then (fn [r] (check (false? (boolean r))
                              "deleting twice reports nothing removed")))
        ;; rejection
        (.then #(refuses?> (fn [] (-put-shard!> s "not-a-shard-id" body))))
        (.then (fn [r] (check r "a malformed shard id is refused")))
        ;; cleanup
        (.then #(-delete-shard!> s a))
        (.then (fn [_] nil)))))

(defn run>
  "Run `verify>` and collect results rather than asserting. Returns a promise
  of `{:passed :failed :failures}` — the shape a Worker can return as JSON so
  a conformance run against a live bucket is a URL anyone can fetch."
  [s]
  (let [results (atom [])]
    (-> (verify> s (fn [ok? label] (swap! results conj {:ok (boolean ok?) :label label})))
        (.then (fn [_]
                 (let [rs @results]
                   {:passed (count (filter :ok rs))
                    :failed (count (remove :ok rs))
                    :failures (mapv :label (remove :ok rs))
                    :total (count rs)})))
        (.catch (fn [e]
                  (let [rs @results]
                    {:passed (count (filter :ok rs))
                     :failed (inc (count (remove :ok rs)))
                     :failures (conj (mapv :label (remove :ok rs))
                                     (str "threw: " (.-message e)))
                     :total (inc (count rs))}))))))
