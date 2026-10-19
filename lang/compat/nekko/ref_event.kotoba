(ns nekko.ref-event
  "Signed, immutable peer-ref events and their Datom projection.

   Unlike a compact sigref, a ref event carries the causal information needed
   to replay a peer namespace without arrival-order authority: old/new target,
   previous event CID, per-peer sequence, and the manifest proving the Git
   object closure. The event CID identifies the complete signed event.

   This namespace deliberately accepts `closure-valid?` as a capability. Git
   object fidelity belongs to bonsai/kotoba-git; nekko owns identity and ref
   authorization, and arrangement owns the Datom projection."
  (:require [arrangement.core :as arr]
            [cbor.core :as cbor]
            [ed25519.core :as ed]
            [ipld.core :as ipld]))

(def ^:private version 1)

(defn- payload
  [{:keys [rid ref old new manifest prev seq]}]
  {"v" version
   "rid" rid
   "ref" ref
   "old" old
   "new" new
   "manifest" manifest
   "prev" prev
   "seq" seq})

(defn sign
  "Sign one causal peer-ref update. `old` and `prev` are nil for the first
   event in a peer/ref namespace. Returns a string-keyed wire map."
  [signer-seed event]
  (let [p (payload event)]
    (assoc p
           "signer" (ed/did-key-from-seed signer-seed)
           "sig" (ed/hexify (ed/sign signer-seed (cbor/encode p))))))

(defn valid?
  "Verify the event shape and its Ed25519 signature."
  [event]
  (boolean
   (and (= version (get event "v"))
        (string? (get event "rid"))
        (string? (get event "ref"))
        (string? (get event "new"))
        (string? (get event "manifest"))
        (or (nil? (get event "old")) (string? (get event "old")))
        (or (nil? (get event "prev")) (string? (get event "prev")))
        (pos-int? (get event "seq"))
        (string? (get event "signer"))
        (string? (get event "sig"))
        (ed/verify-did (get event "signer")
                       (cbor/encode (select-keys event
                                                ["v" "rid" "ref" "old" "new"
                                                 "manifest" "prev" "seq"]))
                       (ed/unhex (get event "sig"))))))

(defn event-cid
  "CID of the complete signed event."
  [event]
  (ipld/cid (ipld/encode event)))

(defn- peer-subject [rid signer ref-name]
  ;; pr-str of a vector is unambiguous even when identifiers contain '/'.
  (str "rad.peer-ref/" (pr-str [rid signer ref-name])))

(defn- one [db subject pred]
  (first (get (arr/entity-attrs db subject) pred)))

(defn current-event-cid
  "Current immutable event CID for one peer/ref namespace, or nil."
  [db rid signer ref-name]
  (one db (peer-subject rid signer ref-name) "rad.peer-ref/event"))

(defn event
  "Read a projected immutable event by CID."
  [db cid]
  (one db (str cid) "rad.event/body"))

(defn current-event
  "Read the current event for one peer/ref namespace."
  [db rid signer ref-name]
  (some->> (current-event-cid db rid signer ref-name) (event db)))

(defn- replace-one [db subject pred value]
  (let [db (if-let [old (one db subject pred)]
             (arr/retract-quad db {:s subject :p pred :o old})
             db)]
    (if (nil? value)
      db
      (arr/assert-quad db {:s subject :p pred :o value}))))

(defn admit
  "Verify and project one signed event, returning [db' event-cid].

   `delegates` is the authorized DID set at the event's parent identity
   revision. `closure-valid?` receives the event and must verify its new Git
   target, manifest, and complete Git OID/CID closure. It is called only after
   signature and causal checks. Retries of the exact current event are
   idempotent; stale, skipped, or divergent events fail closed."
  [db delegates closure-valid? event]
  (let [signer (get event "signer")
        rid (get event "rid")
        ref-name (get event "ref")
        cid (str (event-cid event))
        current-cid (current-event-cid db rid signer ref-name)
        current (when current-cid (nekko.ref-event/event db current-cid))]
    (cond
      (= cid current-cid) [db cid]
      (not (valid? event))
      (throw (ex-info "ref event signature or shape is invalid"
                      {:reason :invalid-event}))
      (not (contains? delegates signer))
      (throw (ex-info "ref event signer is not a delegate"
                      {:reason :unauthorized :signer signer}))
      (not= (get event "prev") current-cid)
      (throw (ex-info "ref event does not extend the peer namespace"
                      {:reason :prev-mismatch :expected current-cid
                       :actual (get event "prev")}))
      (not= (get event "seq") (if current (inc (get current "seq")) 1))
      (throw (ex-info "ref event sequence is not contiguous"
                      {:reason :seq-break :expected (if current (inc (get current "seq")) 1)
                       :actual (get event "seq")}))
      (not= (get event "old") (when current (get current "new")))
      (throw (ex-info "ref event old target does not match current peer ref"
                      {:reason :old-mismatch :expected (when current (get current "new"))
                       :actual (get event "old")}))
      (not (closure-valid? event))
      (throw (ex-info "ref event Git object closure is invalid"
                      {:reason :invalid-closure :manifest (get event "manifest")}))
      :else
      (let [event-subject cid
            peer (peer-subject rid signer ref-name)
            db' (-> db
                    (arr/assert-quad {:s event-subject :p "rad.event/body" :o event})
                    (arr/assert-quad {:s event-subject :p "rad.event/rid" :o rid})
                    (arr/assert-quad {:s event-subject :p "rad.event/ref" :o ref-name})
                    (arr/assert-quad {:s event-subject :p "rad.event/signer" :o signer})
                    (arr/assert-quad {:s event-subject :p "rad.event/new" :o (get event "new")})
                    (arr/assert-quad {:s event-subject :p "rad.event/manifest" :o (get event "manifest")})
                    (arr/assert-quad {:s event-subject :p "rad.event/seq" :o (get event "seq")})
                    (replace-one peer "rad.peer-ref/event" cid))]
        [db' cid]))))

(defn peer-events
  "Current valid event for each configured delegate, sorted by signer DID."
  [db rid ref-name delegates]
  (->> delegates
       sort
       (keep #(current-event db rid % ref-name))
       vec))

