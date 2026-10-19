(ns kagi.sync
  "Cloud persistence for the kagi vault, iCloud-Keychain / 1Password style
  (ADR-2607170500). The vault snapshot is ALREADY end-to-end encrypted on disk
  (ciphertext items + wrapped VMK + ledger — `kagi.persist/->edn` emits no
  plaintext, no raw VMK), so pushing that exact blob to an untrusted server is
  safe: kotobase.net only ever holds ciphertext, and the master passphrase /
  OS-keychain VMK unlock never leaves the device. That is precisely the
  iCloud-Keychain trust model — the server is a sync relay, not a trust root.

  Transport: the encrypted snapshot is stored as a single upserted datom in the
  actor's own tenant graph `kotobase/db/<did>/kagi-vault` on kotobase.net,
  authorized by a depth-1 self-minted CACAO. The actor owns the graph by
  construction (graph CID = hash of its own DID + db-name), so no handed token
  and no coordination-server auth-key are needed.

    kagi push   local vault -> cloud (upsert snapshot under this actor's graph)
    kagi pull   cloud -> local vault (with a local backup first)
    kagi sync   pull-if-newer then push (last-writer-wins by :kagi.vault/seq)"
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [kagi.cacao :as cacao]
            [kagi.persist :as persist])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]
           [java.time Instant]
           [java.time.temporal ChronoUnit]
           [java.util UUID]))

(def default-pod "https://kotobase.net")
(def default-db-name "kagi-vault")

;; ── schema (idempotent; installed via :db/add VECTOR datoms every push) ──────
;; The live kotobase-server needs :db/add vector forms, NOT map-entity forms,
;; and MAP-shaped queries (confirmed live by cloud-murakumo/queue_kotoba
;; 2026-07-12 — a vector query silently returns zero rows). :kagi.vault/id is
;; unique-identity so pushes UPSERT the single vault entity.
(def ^:private vault-attrs
  [[:kagi.vault/id       :db.type/string :db.cardinality/one :db.unique/identity]
   [:kagi.vault/snapshot :db.type/string :db.cardinality/one nil]
   [:kagi.vault/seq      :db.type/long   :db.cardinality/one nil]
   [:kagi.vault/at       :db.type/string :db.cardinality/one nil]])

(defn- schema-eid [ident] (str "schema:" (subs (str ident) 1)))

(def ^:private vault-schema-tx
  (vec (mapcat (fn [[ident vt card uniq]]
                 (let [e (schema-eid ident)]
                   (cond-> [[:db/add e :db/ident ident]
                            [:db/add e :db/valueType vt]
                            [:db/add e :db/cardinality card]]
                     uniq (conj [:db/add e :db/unique uniq]))))
               vault-attrs)))

(defn jvm-http-fn
  "host-caps :http-fn over the JDK HTTP client (no dependency)."
  [{:keys [url method headers body]}]
  (let [b (HttpRequest/newBuilder (URI/create url))]
    (doseq [[k v] headers] (.header b k v))
    (let [req  (-> b (.method (str/upper-case (name (or method :post)))
                            (if body
                              (HttpRequest$BodyPublishers/ofString body)
                              (HttpRequest$BodyPublishers/noBody)))
                   (.build))
          resp (.send (HttpClient/newHttpClient) req (HttpResponse$BodyHandlers/ofString))]
      {:status (.statusCode resp) :body (.body resp)})))

;; Direct XRPC (not langchain.kotoba-db's api map): the live worker returns
;; query rows under `:rows` (kdb's :q reads the stale `:rows_edn` name → always
;; empty here), writes require `:db_name` (the edge derives the tenant graph),
;; reads require the canonical `:graph` CID. Auth is a fresh mint-kotobase CACAO
;; in the Authorization header AND the body cacao_b64 (the tenant-cap write gate
;; wants both).

(defn- fresh-cacao
  "A CACAO for exactly one apex call.

  `iat`/`exp` must be ISO-8601 SECONDS precision — exactly
  `YYYY-MM-DDTHH:MM:SSZ`. The apex's `parse-utc-seconds` matches that regex
  and NOTHING else: it accepts neither epoch seconds nor a fractional part,
  and a value it cannot parse becomes `invalid CACAO iat` -> 401.

  This is why `kagi push` was failing. `(str (Instant/now))` renders
  nanoseconds whenever they are non-zero (`2026-07-27T10:15:30.123456789Z`),
  so most mints did not match, and the ones that happened to land on a whole
  second did. `truncatedTo SECONDS` makes it unconditional.

  A fresh nonce per call is mandatory — the edge records nonces for replay
  protection, so reusing one across a retry 401s."
  [id]
  (let [now (.truncatedTo (Instant/now) ChronoUnit/SECONDS)]
    (cacao/mint-kotobase id {:nonce (str (UUID/randomUUID))
                             :issued-at (str now)
                             :expiry (str (.plusSeconds now 300))})))

(defn- xrpc! [url nsid id body]
  (let [c    (fresh-cacao id)
        resp (jvm-http-fn {:url (str url "/xrpc/ai.gftd.apps.kotobase." nsid)
                           :method :post
                           :headers {"content-type" "application/json"
                                     "authorization" (str "CACAO " c)
                                     "x-kotoba-did" (:did id)}
                           :body (json/write-str (assoc body :cacao_b64 c))})
        data (json/read-str (:body resp) :key-fn keyword)]
    (when-not (and (#{200 201} (:status resp)) (:ok data))
      (throw (ex-info (str "kotobase " nsid " failed: " (:error data))
                      {:status (:status resp) :body (:body resp)})))
    data))

(defn- q! [url id db-name query]
  (let [graph (cacao/canonical-graph (:did id) db-name)]
    (:rows (xrpc! url "datomic.q" id {:graph graph :query_edn (pr-str query)}))))

(defn- transact! [url id db-name tx]
  (xrpc! url "datomic.transact" id {:db_name db-name :tx_edn (pr-str (vec tx))}))

;; MAP-shaped queries only — a vector query silently hits the triple-pattern
;; engine and returns zero rows (cloud-murakumo/queue_kotoba, 2026-07-12).
(def ^:private seq-query
  '{:find [?s] :where [[?e :kagi.vault/id "vault"] [?e :kagi.vault/seq ?s]]})
(def ^:private snapshot-query
  '{:find [?snap ?s]
    :where [[?e :kagi.vault/id "vault"]
            [?e :kagi.vault/snapshot ?snap]
            [?e :kagi.vault/seq ?s]]})

(defn- ->long [x] (if (number? x) (long x) (Long/parseLong (str x))))

(defn- read-remote-seq
  "Current max :kagi.vault/seq on the server (0 if none). Transport and auth
  failures propagate: treating an unreadable remote as empty would enable overwrite."
  [url id db-name]
  (reduce max 0 (map (comp ->long first) (q! url id db-name seq-query))))

(defn assert-expected-seq!
  "Optimistic concurrency gate. Prevents a device from overwriting a snapshot
  changed after its pull. nil retains explicit legacy/force-push semantics."
  [expected actual]
  (when (and (some? expected) (not= (long expected) (long actual)))
    (throw (ex-info "cloud vault changed since pull"
                    {:reason :sync-conflict :expected expected :actual actual})))
  true)

(defn push!
  "Upsert the local encrypted vault snapshot into the actor's cloud graph.
  Returns {:seq :bytes :graph :pod}."
  [{:keys [id vault-path pod db-name expected-seq]}]
  (let [url  (or pod default-pod)
        db   (or db-name default-db-name)
        snap (slurp vault-path)                 ; already ciphertext-only
        remote-seq (read-remote-seq url id db)
        _ (assert-expected-seq! expected-seq remote-seq)
        seq  (inc remote-seq)
        eid  "kagi.vault:vault"
        tx   (into vault-schema-tx
                   [[:db/add eid :kagi.vault/id "vault"]
                    [:db/add eid :kagi.vault/snapshot snap]
                    [:db/add eid :kagi.vault/seq seq]
                    [:db/add eid :kagi.vault/at (str (Instant/now))]])]
    (transact! url id db tx)
    {:seq seq :previous-seq remote-seq :bytes (count snap)
     :graph (cacao/canonical-graph (:did id) db) :pod url}))

(defn pull!
  "Fetch the latest cloud vault snapshot and write it to vault-path (after
  backing up the current local file to <vault-path>.bak). Returns {:seq :bytes}
  or {:seq nil} if the cloud has no snapshot."
  [{:keys [id vault-path pod db-name]}]
  (let [url  (or pod default-pod)
        db   (or db-name default-db-name)
        rows (q! url id db snapshot-query)]
    (if (empty? rows)
      {:seq nil}
      (let [[snap seq] (apply max-key (comp ->long second) rows)]
        ;; sanity: it must parse as a kagi vault snapshot before we overwrite.
        (persist/<-edn snap)
        (let [f (java.io.File. ^String vault-path)]
          (when (.exists f)
            (java.nio.file.Files/copy (.toPath f)
                                      (.toPath (java.io.File. (str vault-path ".bak")))
                                      (into-array java.nio.file.CopyOption
                                                  [java.nio.file.StandardCopyOption/REPLACE_EXISTING]))))
        (spit vault-path snap)
        {:seq (->long seq) :bytes (count snap)}))))
