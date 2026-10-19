(ns kotobase.storage.object-async-contract
  "Promise-based conformance suite for large-object providers.

  `kotobase.storage.object-contract` is synchronous, and that is not a
  stylistic difference -- it makes the presigned profile untestable on the
  runtime that profile exists for. Presigning is HMAC-SHA-256 over a
  canonical request, and on a Worker the only SHA-256 available is
  `crypto.subtle`, which is async. A provider that presigns there returns a
  Promise, `valid-grant?` on a Promise is false, and the sync suite reports a
  conformance failure for a store that is in fact correct. So the only
  provider the sync suite could ever accept as `:presigned-transfer` is one
  that does not need to sign anything -- a fixture.

  Same asymmetry as the sync suite: a proxied store is driven end to end,
  while a presigned store is checked for the properties that have no second
  chance. Completing a presigned upload means talking to the provider's
  network, and that belongs in the provider's own live test.

  Two checks here have no counterpart in the sync suite, both about the same
  hazard -- a grant that constrains nothing:

  - a PUT grant must bind `content-length` **in its signature**;
  - asking for a PUT grant *without* a size must be refused, rather than
    quietly producing an unbound one.

  The second is what closes the door. A provider can satisfy the first by
  binding a size when it is given one, and still hand out a blank cheque to
  every caller that omits it -- which is the caller most likely to be
  streaming an object of unknown length, i.e. exactly the large ones."
  (:require [kotobase.storage.object :as object]))

(def ^:private cid-a
  "bafkreiadsbmmn4waznesyuz3bjgrj33xzqhxrk6mz3ksq7meugrachh3qe")
(def ^:private cid-b
  "bafkreibpugzxpp3hgcpwlzphxsozeq2fzjsi33comandtcu4wsl5zorxmu")
(def ^:private not-an-object-cid "SHA256E-s3--abc.wav")

(defn- bytes-of [& values] (js/Uint8Array. (clj->js (vec values))))

(defn- failure [message data]
  (ex-info (str "object contract: " message)
           (assoc data :type :kotobase.storage/contract-violation)))

(defn- checker []
  (let [n (atom 0)]
    {:tally #(deref n)
     :check (fn [ok? message data]
              (swap! n inc)
              (when-not ok? (throw (failure message data)))
              ok?)}))

(defn- rejects?
  "True when `f` throws, or returns a Promise that rejects.

  Both forms count: a provider may validate the CID before it ever awaits
  (a synchronous throw) or inside the chain. The contract cares that the
  caller is told, not where the telling happens."
  [f]
  (try
    (let [result (f)]
      (if (and result (fn? (.-then result)))
        (.then result (fn [_] false) (fn [_] true))
        (js/Promise.resolve false)))
    (catch :default _ (js/Promise.resolve true))))

;; ── proxied ─────────────────────────────────────────────────────────────────

(defn- verify-range-read
  "The half that only runs for a store implementing `IRangeRead`.

  It exists in BOTH suites deliberately. The sync suite runs on the JVM and
  the async one is what every Worker provider actually runs, so a range
  check that lived only in the first would be a check the deployed path
  never took -- and the boundary it guards (HTTP Range is inclusive, this
  protocol is half-open) is one a provider gets wrong exactly there, in the
  header it writes."
  [store check]
  (-> (js/Promise.resolve (object/-put-object! store cid-a (bytes-of 1 2 3)))
      (.then (fn [_] (object/-get-object-range store cid-a 0 3)))
      (.then (fn [b]
               (check (= [1 2 3] (vec b))
                      "a full-width range equals the whole object" {:got (vec b)})
               (object/-get-object-range store cid-a 0 1)))
      (.then (fn [b]
               (check (= [1] (vec b))
                      "the range is HALF-OPEN: [0,1) is one byte, not two"
                      {:got (vec b)})
               (object/-get-object-range store cid-a 1 3)))
      (.then (fn [b]
               (check (= [2 3] (vec b))
                      "an interior range starts where it says" {:got (vec b)})
               (object/-get-object-range store cid-a 0 99)))
      (.then (fn [b]
               (check (= [1 2 3] (vec b))
                      "a range past the end returns the overlap, as HTTP 206 does"
                      {:got (vec b)})
               (object/-get-object-range store cid-b 0 1)))
      (.then (fn [b]
               (check (nil? b)
                      "a range of an object nobody stored is nil" {:got b})
               (object/-delete-object! store cid-a)))))

(defn- verify-proxied [store check]
  (-> (js/Promise.resolve (object/-stat-object store cid-a))
      (.then (fn [stat]
               (check (nil? stat) "a missing object stats as nil" {:cid cid-a})
               (object/-put-object! store cid-a (bytes-of 1 2 3))))
      (.then (fn [_] (object/-stat-object store cid-a)))
      (.then (fn [stat]
               (check (= 3 (:size-bytes stat))
                      "a stored object reports its size" {:stat stat})
               (object/-get-object store cid-a)))
      (.then (fn [body]
               (check (= [1 2 3] (vec body))
                      "object bytes round-trip" {:body (vec body)})
               (object/-get-object store cid-b)))
      (.then (fn [missing]
               (check (nil? missing)
                      "an object nobody stored is not invented" {:cid cid-b})
               (object/-delete-object! store cid-a)))
      (.then (fn [{:keys [deleted? reason]}]
               (if (object/deletes? store)
                 (do (check (true? deleted?)
                            "a store claiming :object-delete deletes" {})
                     (-> (js/Promise.resolve (object/-stat-object store cid-a))
                         (.then (fn [stat]
                                  (check (nil? stat)
                                         "and the object is gone afterwards"
                                         {:stat stat})))))
                 (do (check (false? deleted?)
                            "a store without :object-delete reports failure
                             rather than a tombstone that says success" {})
                     (check (= :not-supported reason) "and says why"
                            {:reason reason})
                     (-> (js/Promise.resolve (object/-stat-object store cid-a))
                         (.then (fn [stat]
                                  (check (some? stat)
                                         "and does not claim the bytes went away"
                                         {}))))))))))

;; ── presigned ───────────────────────────────────────────────────────────────

(defn- verify-presigned [store check]
  (-> (js/Promise.all
       #js [(object/-presign-put store cid-a {:size-bytes 3})
            (object/-presign-get store cid-a {})])
      (.then
       (fn [[put get]]
         (check (object/valid-grant? put)
                "a put grant has href, method and expiry" {:grant put})
         (check (object/valid-grant? get)
                "a get grant has href, method and expiry" {:grant get})
         (check (object/bound-put-grant? put)
                "a put grant binds content-length in its SIGNATURE -- an
                 unbound presigned PUT is a blank cheque for arbitrary bytes"
                {:signed-headers (:signed-headers put)})
         (check (not= (:href put) (:href get))
                "the two grants are not the same URL by accident" {})
         (rejects? #(object/-presign-put store cid-a {}))))
      (.then (fn [refused?]
               (check (true? refused?)
                      "a PUT grant requested with no size is REFUSED rather
                       than issued unbound -- binding the size only when one
                       is offered leaves the blank cheque available to every
                       caller who omits it"
                      {})))))

;; ── entry ───────────────────────────────────────────────────────────────────

(defn verify
  "Run the shared large-object contract.

  -> Promise<{:checks n :profile p :deletes bool}>.

  `:profile` is echoed from the store's own declaration rather than
  inferred, so a caller reading `{:checks n}` alone cannot mistake a proxied
  store for one that was tested for grant binding."
  [store]
  ;; Every failure is a REJECTION, including the validation that runs before
  ;; the first await -- a caller that attached only `.catch` would otherwise
  ;; take a synchronous throw for exactly the malformed stores this reports on.
  (try
    (object/validate-object-store! store)
    (let [{:keys [check tally]} (checker)
          profile (object/transfer-profile store)]
      (-> (rejects? #(object/-stat-object store not-an-object-cid))
          (.then (fn [refused?]
                   (check (true? refused?)
                          "a store rejects an id that is not a
                           CIDv1(raw, sha2-256) -- an LFS oid or an annex key
                           passed straight through creates an object nobody
                           can resolve"
                          {:id not-an-object-cid})
                   (if (object/presigned? store)
                     (verify-presigned store check)
                     (verify-proxied store check))))
          (.then (fn [_]
                   (when (object/range-read? store)
                     (verify-range-read store check))))
          (.then (fn [_] {:checks (tally) :profile profile
                          :deletes (object/deletes? store)
                          ;; `:not-claimed` and `:verified` are different
                          ;; words on purpose: a passing run must not read
                          ;; as a guarantee the store never made.
                          :range-read (if (object/range-read? store)
                                        :verified :not-claimed)
                          :range-grant (if (object/range-grant? store)
                                         :declared :not-claimed)}))))
    (catch :default e (js/Promise.reject e))))

(defn verify!
  "`verify` as a process gate: prints the result and sets a non-zero exit code
  on a violation.

  Here rather than in each provider for the reason `async-contract/verify!`
  gives: a suite that reports success by printing and exits 0 either way is
  green by construction."
  [store label]
  (-> (verify store)
      (.then (fn [result]
               (println (str label " object contract: " (pr-str result)))
               result))
      (.catch (fn [error]
                (js/console.error (str label " object contract FAILED: "
                                       (.-message error)))
                (when-let [data (ex-data error)]
                  (js/console.error (str "  " (pr-str data))))
                (set! (.-exitCode js/process) 1)
                nil))))
