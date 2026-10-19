(ns engi.store
  "kotobase.net I/O for ENGI (ADR-2607101100) — the ONLY namespace in this
  repo that touches the network. Built directly on `kotobase-client`'s
  `kotobase.client`/`kotobase.cid` (NOT a hand-rolled mint/post like
  yoro-ui.studio.genko-store — that ns predates kotobase-client's extraction;
  engi is new, so it depends on the canonical library instead of
  re-implementing it).

  Graph naming: `kotobase/db/<did:key>/engi` (`db-name` below), exactly the
  ADR's convention. Every write lands in the CALLING client's own graph only
  (kotobase.net apex constraint — self-graph-only writes, ADR context #1);
  there is no cross-agent write path, by construction.

  ── reads of ANOTHER agent's graph (validate-proposal / audit / bilateral
  confirmation all need this) ──
  `reader-client` builds a `:did`-only, `:public-reads?` client (no secret
  key) — the ONLY way to read a graph you don't own. Whether kotobase.net's
  apex actually serves an unauthenticated read of someone else's engi graph
  is exactly what this repo's live integration test
  (`test/engi/live_test.cljs`) checks against production and reports
  verbatim; see that ns and the README for the observed result."
  (:require [clojure.string :as str]
            [kotobase.client :as client]
            [kotobase.cid :as cid]
            [kotobase.cacao :as cacao]))

(def db-name "engi")
(def default-endpoint "https://kotobase.net")
(def default-operator-did "did:web:kotobase.net")

;; ── clients ──────────────────────────────────────────────────────────────

(defn owner-client
  "A client that can WRITE (and read) its own `kotobase/db/<did>/engi` graph.
  `secret-key` is the agent's own Ed25519 seed (32 bytes)."
  ([secret-key] (owner-client secret-key {}))
  ([secret-key {:keys [endpoint operator-did fetch-fn]}]
   (client/make-client {:endpoint (or endpoint default-endpoint)
                         :operator-did (or operator-did default-operator-did)
                         :secret-key secret-key
                         :fetch-fn fetch-fn})))

(defn reader-client
  "A read-only client for READING a possibly-foreign `did`'s engi graph — no
  secret key, `:public-reads? true` so no CACAO is minted (see ns docstring
  re: whether the server actually permits this for a non-owner)."
  ([did] (reader-client did {}))
  ([did {:keys [endpoint operator-did fetch-fn]}]
   (client/make-client {:endpoint (or endpoint default-endpoint)
                         :operator-did (or operator-did default-operator-did)
                         :did did
                         :public-reads? true
                         :fetch-fn fetch-fn})))

;; ── EDN scalar <-> tx_edn encoding (memo is base64'd — see below) ─────────

(defn- str->b64 [^string s] (cacao/bytes->base64 (cid/text->bytes s)))
(defn- b64->str [^string b] (.decode (js/TextDecoder.) (cacao/base64->bytes b)))

(defn- edn-quoted-str
  "EDN string literal for a scalar (JSON.stringify escaping — same encoder
  `yoro-ui.studio.genko-store/edn-str` and `aozora.pds.encode/edn-str` use)."
  [v] (js/JSON.stringify (str v)))

(defn- attr-edn-value
  "One attr's tx_edn value literal. Numbers/bools/nil are written bare (so
  they decode back via `kotobase.client/decode-edn-scalar` as numbers/bools/
  nil, not strings) — engi entities carry real int fields (:engi/seq
  :engi/amount :engi/credit-limit :engi/ts) that MUST round-trip as numbers
  for `engi.core`'s arithmetic. Everything else is a quoted EDN string."
  [v]
  (cond
    (nil? v) "nil"
    (number? v) (str v)
    (boolean? v) (str v)
    :else (edn-quoted-str v)))

(defn- entity->tx-edn
  "One entity map -> a `[{...}]` tx_edn string. `:engi/memo` (free text —
  the ONLY field a caller could ever put a literal `{`/`}`/`\"` into) is
  base64-encoded on the wire (mirrors `:gh.genko/docB64` — the deployed
  tx_edn parser lineage splits entity maps at literal braces/quotes inside a
  value; every other engi attr is a controlled-alphabet id/sig/CID/number
  with no such risk, so only memo needs this)."
  [entity]
  (let [entity (cond-> entity
                 (some? (:engi/memo entity)) (update :engi/memo str->b64))]
    (str "[{"
         (str/join " "
                   (keep (fn [[k v]]
                           (when (some? v) (str k " " (attr-edn-value v))))
                         entity))
         "}]")))

;; ── wire attr keyword <-> string ───────────────────────────────────────────

(defn- wire-attr->kw [^string a]
  (let [s (subs a 1) ; drop leading ':'
        i (.indexOf s "/")]
    (if (>= i 0) (keyword (subs s 0 i) (subs s (inc i))) (keyword s))))

;; ── reads: whole-graph datoms scan -> folded entity maps ──────────────────

(defn- decode-memo [entity]
  (if-let [b64 (:engi/memo entity)]
    (assoc entity :engi/memo (b64->str b64))
    entity))

;; Live-probed against production kotobase.net (2026-07-09): the server does
;; NOT preserve "number-ness" through a bare (unquoted) tx_edn numeric
;; literal — it round-trips `:engi/credit-limit -1000` back as
;; `"v_edn":"\"-1000\""`, i.e. ALWAYS as a quoted string representation,
;; the same real quirk `yoro-ui.studio.genko-store` already worked around by
;; declaring `:gh.genko/rev`/`:gh.genko/updatedAt` "as str" and `js/parseInt`-
;; ing them back on read. `kotobase.client/decode-edn-scalar`'s quoted-string
;; branch only unwraps ONE level of quoting (`"\"-1000\""` -> `"-1000"`, a
;; still-a-string value) — its bare-number regex branch is unreachable
;; against this server (the quote branch always intercepts first), so every
;; numeric engi field needs an explicit second coercion pass here.
(def ^:private numeric-keys
  #{:engi/seq :engi/amount :engi/credit-limit :engi/ts :engi/created-at})

(defn- coerce-numeric [entity]
  (reduce (fn [e k]
            (let [v (get e k)]
              (if (string? v) (assoc e k (js/Number v)) e)))
          entity numeric-keys))

(defn- js-datom->map [^js d]
  {:e (.-e d) :a (.-a d) :v-edn (.-v_edn d)
   :added (if (some? (.-added d)) (.-added d) true)})

(defn fold-entities
  "Datom maps (log order) -> `[entity-map ...]`, one map per distinct `:e`,
  last-assertion-wins per attr within an entity (kotobase has no
  cardinality-one; this is the established convention —
  `yoro-ui.studio.genko-store/fold-datoms`, `aozora.appview.scan/group-by-
  entity`). Pure — exposed so tests can exercise it without a fake client."
  [datoms]
  (->> datoms
       (group-by :e)
       vals
       (map (fn [ds]
              (reduce (fn [acc {:keys [a v-edn added]}]
                        (let [k (wire-attr->kw a)]
                          (if added
                            (assoc acc k (client/decode-edn-scalar v-edn))
                            (dissoc acc k))))
                      {} ds)))
       (map decode-memo)
       (map coerce-numeric)
       vec))

(defn fetch-entities!
  "→ Promise<[entity-map ...]> — every entity (genesis + tx entries +
  warrants) currently in `client`'s target engi graph. `[]` for a
  never-written graph (kotobase.client's `empty-on-404`)."
  [client]
  (-> (client/datoms client db-name ":eavt" {:public? (:public-reads? client)})
      (.then (fn [^js resp]
               (fold-entities (map js-datom->map (array-seq (or (.-datoms resp) #js []))))))))

;; ── writes (always to the client's OWN graph) ─────────────────────────────

(defn write-entity!
  "Transact one entity map into `client`'s own engi graph. `:retry?` true —
  our writes are deterministic keyed re-asserts (fixed `:db/id` + fixed
  content per call), so a retried transient-5xx re-send is a harmless
  duplicate assertion, not a double-append (`kotobase.client/transact`
  docstring)."
  [client entity]
  (client/transact client db-name (entity->tx-edn entity) {:retry? true}))

(defn write-genesis! [client genesis-entity] (write-entity! client genesis-entity))
(defn write-entry! [client entry] (write-entity! client entry))

(defn write-warrant!
  "Writes a warrant entity to the DETECTOR's own engi graph (the client's own
  graph — kotobase.net has no cross-agent write, so a literal shared
  `kotobase/db/shared/...` registry graph is not writable by anyone in
  particular; ADR-2607101100 itself leaves this undecided — \"実装時に確定\").
  `validate-proposal`/`audit-agent` treat a set of known-validator DIDs as
  the (v1, gossip-less) warrant registry and pull each one's graph — see
  `engi.protocol`."
  [detector-client warrant-entity]
  (write-entity! detector-client warrant-entity))
