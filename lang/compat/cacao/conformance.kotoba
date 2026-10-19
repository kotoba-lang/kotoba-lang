(ns cacao.conformance
  "An executable contract for what a kotobase deployment accepts.

  ## Why this exists

  Every CACAO rejection the kotobase apex produces looks the same from the
  outside: `{\"ok\":false,\"error\":\"Unauthorized\"}`. The edge computes a
  precise reason — `invalid CACAO iat`, `CACAO graph scope does not include
  issuer DID`, `CACAO nonce replay` — and then discards it
  (`proxy/resolve-viewer`'s `(.catch (fn [_] nil))`). So a client that drifts
  from the contract learns about it in production, as an outage, with one bit
  of information.

  On 2026-07-27/28 that cost two days across FIVE separate failures with
  identical symptoms:

    - `kagi push` had been dead for months because `(str (Instant/now))`
      renders nanoseconds and the apex's `parse-utc-seconds` accepts only
      `YYYY-MM-DDTHH:MM:SSZ`;
    - the edge began verifying CACAOs it had previously trusted, so every
      client minting the old shape broke at once;
    - `datomic.*` began bridging to kotobase-storage-d1, which names the same
      permissions differently (`graph:query` vs `datom:read`), and every
      marketplace read 401'd;
    - refs moved from content-addressed CIDs to literal
      `kotobase/db/<did>/<name>` strings, so existing data became invisible
      (`UnknownGraphCid`) rather than missing;
    - the apex path signed base64url where the verifier decodes base64, so a
      CORRECT signature failed verification and seven live actors 401'd for a
      day with every payload field identical to a working token.

  That last one hid because this suite had no case for it. It does now
  (`:signature-base64url` / `:signature-base64`), which is the only honest
  response to a contract term discovered by outage.

  Not one of those is exotic. Each is a contract this namespace can state and
  a probe can check in seconds.

  ## What this is

  A set of NAMED CASES. Each case mints a CACAO that differs from a valid one
  in exactly one way and says what should happen. `run` takes a `probe` — a
  function that sends one CACAO at a target and reports what came back — so
  the same suite runs against a live deployment, a local worker, or a stub.

  It reports what a deployment ACTUALLY enforces, which is a different
  question from what its source says it enforces. Point it at a target before
  and after a migration and the diff is the answer.

  ## What this deliberately does NOT do

  It does not assert that a rejection carries a reason, because today none of
  them do. `reason-visible?` in the report is the measurement of that gap —
  when the edge starts returning reason codes, this suite is where that
  becomes checkable."
  (:require [kotoba.lang.text :as str]
            [cacao.core :as cacao]))

;; ── the vocabulary, in one place ─────────────────────────────────────────────
;;
;; The XRPC edge and kotobase-storage-d1 name the same permissions
;; differently, and nothing mapped between them. A client that carried only
;; one vocabulary worked until the day the other side started answering.

(def capability-vocabularies
  "The same permission, under each backend's own name.

  Carrying BOTH is what a client should do while more than one backend can
  answer: extra capabilities ride along harmlessly on the side that does not
  check them, and guessing which side will answer is how an outage happens."
  {:read    {:xrpc "datom:read"     :d1 "graph:query"}
   :write   {:xrpc "datom:transact" :d1 "datom:transact"}
   :tx      {:xrpc "tx:create"      :d1 nil}})

(defn capabilities-for
  "Every name a given permission goes by, deduplicated."
  [permission]
  (->> (get capability-vocabularies permission) vals (remove nil?) distinct vec))

(defn read-capabilities [] (capabilities-for :read))
(defn write-capabilities []
  (vec (distinct (concat (capabilities-for :write) (capabilities-for :tx)))))

;; ── cases ────────────────────────────────────────────────────────────────────

(def ^:private base-iat "2026-07-27T10:15:30Z")

(defn- valid-spec [seed nonce now-iso exp-iso]
  {:seed seed :nonce nonce :iat now-iso :exp exp-iso
   :op-caps (read-capabilities)})

(defn cases
  "Every case, as data. `seed` signs them; `now-iso`/`exp-iso` come from the
  caller so a run is reproducible.

  Each case is one deviation from a valid CACAO. Bundling two deviations into
  one case would make a rejection uninformative — the whole point is that a
  failure names which rule it broke."
  [{:keys [seed now-iso exp-iso]}]
  (let [iss (cacao/mint-kotobase-apex (valid-spec seed "probe-valid" now-iso exp-iso))
        did (:iss iss)]
    [{:case/id :valid
      :case/expect :accept
      :case/why "a correctly shaped apex CACAO"
      :case/cacao (:cacao-b64 iss)}

     {:case/id :missing-pin-capability
      :case/expect :reject
      :case/reason "CACAO missing kotobase:pin capability"
      :case/why "the apex requires kotoba://can/kotobase:pin explicitly"
      :case/cacao (:cacao-b64
                   (cacao/mint {:seed seed :aud cacao/kotobase-apex-aud
                                :domain cacao/kotobase-apex-domain
                                :iat now-iso :exp exp-iso :nonce "probe-nopin"
                                :header-type "caip122"
                                :resources [(str "kotoba://graph/" did)]}))}

     {:case/id :graph-scope-is-a-cid
      :case/expect :reject
      :case/reason "CACAO graph scope does not include issuer DID"
      :case/why "the scope must be the ISSUER DID; a graph CID is refused even
                 though the request body names that same CID"
      :case/cacao (:cacao-b64
                   (cacao/mint {:seed seed :aud cacao/kotobase-apex-aud
                                :domain cacao/kotobase-apex-domain
                                :iat now-iso :exp exp-iso :nonce "probe-cidscope"
                                :header-type "caip122"
                                :resources [cacao/kotobase-pin-capability
                                            "kotoba://graph/bafyreiaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]}))}

     {:case/id :iat-with-fractional-seconds
      :case/expect :reject
      :case/reason "invalid CACAO iat"
      :case/why "THE failure that hid for months. (str (Instant/now)) renders
                 nanoseconds when they are non-zero, so most mints were
                 rejected and the ones landing on a whole second passed —
                 an intermittent auth failure reads as a flaky network"
      :case/cacao (:cacao-b64
                   (cacao/mint {:seed seed :aud cacao/kotobase-apex-aud
                                :domain cacao/kotobase-apex-domain
                                :iat "2026-07-27T10:15:30.123456789Z" :exp exp-iso
                                ;; deliberately non-conforming: this vector's
                                ;; whole job is to be refused, so it opts out
                                ;; of mint's instant normalization.
                                :instants :raw
                                :nonce "probe-fracsec" :header-type "caip122"
                                :resources (cacao/kotobase-apex-resources did)}))}

     {:case/id :iat-as-epoch-seconds
      :case/expect :reject
      :case/reason "invalid CACAO iat"
      :case/why "epoch seconds are the other obvious encoding and are also
                 refused; parse-utc-seconds matches one regex and nothing else"
      :case/cacao (:cacao-b64
                   (cacao/mint {:seed seed :aud cacao/kotobase-apex-aud
                                :domain cacao/kotobase-apex-domain
                                :iat "1785140130" :exp exp-iso
                                :instants :raw
                                :nonce "probe-epoch" :header-type "caip122"
                                :resources (cacao/kotobase-apex-resources did)}))}

     {:case/id :expired
      :case/expect :reject
      :case/reason "expired CACAO"
      :case/why "temporal window enforcement"
      :case/cacao (:cacao-b64
                   (cacao/mint-kotobase-apex
                    {:seed seed :nonce "probe-expired"
                     :iat "2020-01-01T00:00:00Z" :exp "2020-01-01T00:05:00Z"
                     :op-caps (read-capabilities)}))}

     {:case/id :eip4361-header
      :case/expect :unknown
      :case/why "the apex's own docs and one drifted copy disagreed about
                 whether `h` matters; this measures it rather than asserting it"
      :case/cacao (:cacao-b64
                   (cacao/mint {:seed seed :aud cacao/kotobase-apex-aud
                                :domain cacao/kotobase-apex-domain
                                :iat now-iso :exp exp-iso :nonce "probe-eip"
                                :header-type "eip4361"
                                :resources (cacao/kotobase-apex-resources did)}))}

     {:case/id :xrpc-vocabulary-only
      :case/expect :unknown
      :case/why "carries datom:read but NOT graph:query. Accepted while the
                 XRPC edge answers; refused the moment datomic.* bridges to
                 kotobase-storage-d1. This single case is the whole of the
                 2026-07-27 marketplace outage"
      :case/cacao (:cacao-b64
                   (cacao/mint-kotobase-apex
                    {:seed seed :nonce "probe-xrpconly" :iat now-iso :exp exp-iso
                     :op-caps ["datom:read"]}))}

     {:case/id :d1-vocabulary-only
      :case/expect :unknown
      :case/why "the mirror image: graph:query but not datom:read. Together
                 with the previous case this reports WHICH backend is
                 answering, which no other observation makes visible"
      :case/cacao (:cacao-b64
                   (cacao/mint-kotobase-apex
                    {:seed seed :nonce "probe-d1only" :iat now-iso :exp exp-iso
                     :op-caps ["graph:query"]}))}

     {:case/id :both-vocabularies
      :case/expect :accept
      :case/why "what a client should send while more than one backend can
                 answer"
      :case/cacao (:cacao-b64
                   (cacao/mint-kotobase-apex
                    {:seed seed :nonce "probe-both" :iat now-iso :exp exp-iso
                     :op-caps (read-capabilities)}))}

     {:case/id :signature-base64url
      :case/expect :unknown
      :case/why "THE case whose absence let the fifth cause of the day hide.
                 A correct Ed25519 signature over the correct message, encoded
                 base64url instead of base64. The verifier decodes one way; the
                 other decodes to different bytes and fails — on a signature
                 that is not wrong. kotobase-client's apex path defaulted to
                 base64url while its direct-v1 path had already been fixed, and
                 seven live actors 401'd for a day with every payload field
                 identical to a working token. Measured, not asserted: which
                 encoding a deployment takes is its choice to state"
      :case/cacao (:cacao-b64
                   (cacao/mint {:seed seed :aud cacao/kotobase-apex-aud
                                :domain cacao/kotobase-apex-domain
                                :iat now-iso :exp exp-iso :nonce "probe-sigurl"
                                :header-type "caip122"
                                :sig-encoding :base64url
                                ;; the READ capabilities too. Without them this
                                ;; case differs in two ways and its rejection
                                ;; proves nothing about the encoding — the
                                ;; suite caught exactly that on its first live
                                ;; run, which is the discipline working on its
                                ;; author.
                                :resources (cacao/kotobase-apex-resources
                                            did (read-capabilities))}))}

     {:case/id :signature-base64
      :case/expect :accept
      :case/why "the mirror image. Together with the previous case this states
                 the encoding a deployment takes, which nothing in a 401 does"
      :case/cacao (:cacao-b64
                   (cacao/mint {:seed seed :aud cacao/kotobase-apex-aud
                                :domain cacao/kotobase-apex-domain
                                :iat now-iso :exp exp-iso :nonce "probe-sigb64"
                                :header-type "caip122"
                                :sig-encoding :base64
                                :resources (cacao/kotobase-apex-resources
                                            did (read-capabilities))}))}

     {:case/id :tampered-signature
      :case/expect :reject
      :case/reason "signature"
      :case/why "a deployment that accepts this is not verifying at all —
                 which was true of the apex until 2026-07-27, and is exactly
                 the cross-tenant hole its own docstring now describes"
      :case/cacao (let [{:keys [cacao-b64]} (cacao/mint-kotobase-apex
                                             (valid-spec seed "probe-tamper" now-iso exp-iso))]
                    ;; flip one character of the base64 envelope
                    (str (subs cacao-b64 0 (- (count cacao-b64) 2))
                         (if (= \A (nth cacao-b64 (- (count cacao-b64) 2))) "B" "A")
                         (subs cacao-b64 (dec (count cacao-b64)))))}]))


;; ── request shape ────────────────────────────────────────────────────────────
;;
;; Not every contract term is about the CACAO. Two of the five failures that
;; took the marketplace down were about the BODY: a read that named the graph
;; only by content-addressed CID (404 UnknownGraphCid, which the client then
;; turned into an empty result), and a query pattern with a bound subject
;; (400 InvalidDatomicRequest). Same structure as the CACAO terms — changed
;; silently, answered opaquely, absorbed by a caller into something that
;; looked normal — so they belong in the same suite.

(defn request-cases
  "Cases that vary the REQUEST BODY rather than the CACAO.

  Each carries a VALID CACAO of its own: the deviation is in the body, so
  the auth must be beyond question or a rejection says nothing about the
  shape. A first version omitted them and every case rejected — the suite
  reported DISAGREE on three terms I had measured working by hand an hour
  earlier, which is the mechanism doing its job on its author for the second
  time today.

  `:case/request` is merged into whatever body the probe would otherwise
  send."
  [{:keys [seed now-iso exp-iso did]}]
  (let [cid-graph "bafyreicuqbxou4isy5z4d5lucus326qtuwpslhpslkb4pazp7deaatsedq"
        literal (str "kotobase/db/" did "/conformance-probe")
        wildcard "[nil \":mp.probe/doc\" nil]"
        ok (fn [nonce] (:cacao-b64 (cacao/mint-kotobase-apex
                                    {:seed seed :nonce nonce :iat now-iso :exp exp-iso
                                     :op-caps (read-capabilities)})))]
    [{:case/id :ref-literal
      :case/cacao (ok "rq-ref-literal")
      :case/expect :accept
      :case/why "the form kotobase-storage-d1 addresses refs by. A client that
                 can name this needs nothing else"
      :case/request {:graph literal :query_edn wildcard}}

     {:case/id :ref-cid-with-db-name
      :case/cacao (ok "rq-ref-cid-with-db-name")
      :case/expect :accept
      :case/why "the form this client naturally has (a content-addressed graph)
                 PLUS the name the bridge needs to resolve it. `transact`
                 always sent db_name; reads did not, which is why writes
                 probed clean while every read came back empty"
      :case/request {:graph cid-graph :db_name "conformance-probe"
                     :query_edn wildcard}}

     {:case/id :ref-cid-only
      :case/cacao (ok "rq-ref-cid-only")
      :case/expect :unknown
      :case/why "MEASURED, not asserted: whether a CID alone resolves is the
                 backend's choice. What is not negotiable is that a caller can
                 tell 'unresolvable' from 'empty' — this answered 404
                 UnknownGraphCid and kotobase-client turned it into an empty
                 result, which is indistinguishable from an empty database and
                 is why data looked lost"
      :case/request {:graph cid-graph :query_edn wildcard}}

     {:case/id :pattern-wildcard-subject
      :case/cacao (ok "rq-pattern-wildcard-subject")
      :case/expect :accept
      :case/why "scanning one attribute across a kind. Every bounded read in
                 marketplace.edge rests on this"
      :case/request {:graph literal :query_edn wildcard}}

     {:case/id :pattern-bound-subject
      :case/cacao (ok "rq-pattern-bound-subject")
      :case/expect :unknown
      :case/why "MEASURED: reading ONE document by its subject. This is what a
                 read of one document should be, and the bridge answers 400
                 InvalidDatomicRequest, so `marketplace.edge/load-one` reads a
                 whole kind and filters. A regression here is invisible except
                 as read volume, which is the kind of thing nobody notices
                 until a bill or a timeout"
      :case/request {:graph literal
                     :query_edn "[\"mp.probe/x\" \":mp.probe/doc\" nil]"}}

     {:case/id :pattern-bound-subject-only
      :case/cacao (ok "rq-pattern-bound-subject-only")
      :case/expect :unknown
      :case/why "the same question without an attribute, in case the refusal
                 is about the pair rather than the subject"
      :case/request {:graph literal :query_edn "[\"mp.probe/x\" nil nil]"}}]))

;; ── running ──────────────────────────────────────────────────────────────────

(defn- classify
  "What a probe result means, without knowing the target's error vocabulary."
  [{:keys [status body]}]
  (cond
    (and status (<= 200 status 299)) :accept
    (and status (#{401 403} status)) :reject
    (and status (= 410 status))      :gone
    (nil? status)                    :unreachable
    :else                            :other))

(defn- reason-of
  "A reason from the response body, when the target bothers to give one.

  Today none do — the edge computes a precise message and drops it. This
  reads whichever field a future deployment might use rather than assuming
  one, because measuring the absence is the point."
  [{:keys [body]}]
  (let [b (str body)]
    (some (fn [k] (when-let [m (re-find (re-pattern (str "\"" k "\"\\s*:\\s*\"([^\"]+)\"")) b)]
                    (second m)))
          ["reason" "detail" "details" "cacao_error" "error_reason"])))

(defn run
  "Run every case — CACAO shape and request shape — against `probe`.

  `probe` is `(fn [cacao-b64 request-overrides] -> {:status int :body
  string})`. `request-overrides` is nil for a CACAO case (send your usual
  body) and a map to merge for a request-shape case. Keeping the transport
  out of this namespace is what lets the same suite run against
  kotobase.net, a `wrangler dev` worker, or a stub in a unit test.

  `opts` needs `:did` for the request-shape cases to build a literal ref;
  omit it and only the CACAO cases run.

  Returns `{:results [...] :summary {...}}`. A case whose `:case/expect` is
  `:unknown` is never a failure — it is a MEASUREMENT, and the report is
  where you read what a deployment currently does."
  [probe {:keys [did] :as opts}]
  (let [cs (into (cases opts) (when did (request-cases opts)))
        results (mapv (fn [c]
                        (let [r (probe (:case/cacao c) (:case/request c))
                              got (classify r)]
                          (-> c
                              (dissoc :case/cacao)
                              (assoc :result/outcome got
                                     :result/status (:status r)
                                     :result/reason (reason-of r)
                                     :result/agrees?
                                     (case (:case/expect c)
                                       :unknown :measured
                                       (= got (:case/expect c)))))))
                      cs)
        checked (remove #(= :measured (:result/agrees? %)) results)]
    {:results results
     :summary {:total (count results)
               :checked (count checked)
               :agree (count (filter :result/agrees? checked))
               :disagree (count (remove :result/agrees? checked))
               :measured (- (count results) (count checked))
               ;; The gap this suite exists to make visible.
               :reason-visible? (boolean (some :result/reason results))}}))

(defn- pad
  "Right-pad to `n`. `format` is JVM-only and this namespace is .cljc — the
  report has to render the same on a Node runner as on a JVM one, because
  the whole point is that anyone can point it at a deployment."
  [s n]
  (let [s (str s)]
    (if (>= (count s) n) s (str s (apply str (repeat (- n (count s)) " "))))))

(defn report
  "The run, as lines a human reads in a terminal."
  [{:keys [results summary]}]
  (str/join
   "\n"
   (concat
    (for [r results]
      (str (pad (name (:case/id r)) 28) " "
           (pad (name (or (:result/outcome r) :?)) 9) " "
           (pad (case (:result/agrees? r)
                  :measured "measured"
                  true      "ok"
                  "DISAGREE") 8) " "
           (or (:result/reason r) "")))
    [""
     (str (:checked summary) " checked, " (:agree summary) " agree, "
          (:disagree summary) " disagree, " (:measured summary) " measured")
     (if (:reason-visible? summary)
       "rejections carry a reason"
       "rejections carry NO reason — every cause looks identical from outside")])))
