(ns kotoba.codebase.value-runtime
  "Run-local handles for immutable, CID-addressed Kotoba values.

  ValueCID is the global logical identity, a handle is a bounded local slot,
  and neither is authority. The table deliberately stores canonical bytes,
  not caller-owned byte arrays or mutable host objects. Resolving decodes a
  fresh value, so mutating a returned byte array cannot mutate the value later
  observed through the same CID or handle.

  Runtime release never deletes a persistent block. CAS retention and runtime
  lifetime are separate collectors with separate roots."
  (:refer-clojure :exclude [resolve])
  (:require [ipld.value :as value]
            [kotoba.codebase.store :as store]
            [multiformats.core :as mf]))

(def default-max-handles 4096)

(defn- fail! [problem data]
  (throw (ex-info (str "value runtime: " (name problem))
                  (assoc data :problem problem))))

(defrecord ValueRuntime [root max-handles state])

(defn- value-cid [x]
  ;; The current immutable io-ipld pin predates the language-facing facade but
  ;; owns the same normative kotoba.value.v1 bytes. A future pin advance can
  ;; replace this with kotoba.value.codec/value-cid without moving identity.
  (mf/cidv1-dag-cbor (value/encode-value x)))

(defn create
  "Create an empty run-local table over an initialized codebase store."
  ([root] (create root default-max-handles))
  ([root max-handles]
   (when-not (and (integer? max-handles) (pos? max-handles)
                  (<= max-handles default-max-handles))
     (fail! :value-runtime/invalid-capacity
            {:max-handles max-handles :limit default-max-handles}))
   ;; Exercise the store boundary now, rather than failing after a handle has
   ;; already escaped to a caller.
   (store/block-cids root)
   (->ValueRuntime root max-handles
                   (atom {:closed? false :next-handle 1
                          :by-cid {} :by-handle {}}))))

(defn- install!
  [{:keys [max-handles state]} cid canonical-bytes]
  (let [[_ after]
        (swap-vals!
         state
         (fn [{:keys [closed? by-cid by-handle next-handle] :as current}]
           (when closed?
             (fail! :value-runtime/closed {}))
           (if (contains? by-cid cid)
             current
             (do
               (when (>= (count by-handle) max-handles)
                 (fail! :value-runtime/exhausted {:capacity max-handles}))
               (-> current
                   (assoc :next-handle (inc next-handle))
                   (assoc-in [:by-cid cid] next-handle)
                   (assoc-in [:by-handle next-handle]
                             {:cid cid :bytes canonical-bytes}))))))]
    (get-in after [:by-cid cid])))

(defn intern!
  "Canonicalize VALUE, persist it under its ValueCID, and return a local handle.

  Re-interning equal content in one table returns the same handle. Another run
  may choose another handle; only the CID is portable."
  [{:keys [root] :as runtime} value]
  (let [bytes (value/encode-value value)
        cid (value-cid value)
        ;; Decode before retaining anything so mutable caller-owned byte arrays
        ;; are severed from the runtime, then persist the canonical form through
        ;; the codebase's ordinary CID verifier.
        canonical-value (value/decode-value bytes)
        canonical-bytes (value/encode-value canonical-value)]
    (store/put-block! root cid (value/value->form canonical-value))
    (install! runtime cid canonical-bytes)))

(defn hydrate!
  "Resolve CID from the verified CAS once and return its run-local handle."
  [{:keys [root] :as runtime} cid]
  (if-let [handle (get-in @(:state runtime) [:by-cid cid])]
    handle
    (let [form (store/get-block root cid)
          decoded (value/form->value form)
          bytes (value/encode-value decoded)]
      (when-not (= cid (value-cid decoded))
        (fail! :value-runtime/value-cid-mismatch {:cid cid}))
      (install! runtime cid bytes))))

(defn resolve
  "Return a fresh decoded value for HANDLE; forged and released handles fail."
  [{:keys [state]} handle]
  (when-not (and (integer? handle) (pos? handle))
    (fail! :value-runtime/invalid-handle {:handle handle}))
  (let [{:keys [closed? by-handle]} @state]
    (when closed? (fail! :value-runtime/closed {}))
    (if-let [{:keys [bytes]} (get by-handle handle)]
      (value/decode-value bytes)
      (fail! :value-runtime/unknown-handle {:handle handle}))))

(defn cid-of
  "Return the portable ValueCID behind HANDLE."
  [{:keys [state] :as runtime} handle]
  ;; Reuse the complete handle/closed validation in resolve.
  (resolve runtime handle)
  (get-in @state [:by-handle handle :cid]))

(defn release!
  "Invalidate HANDLE without reusing its slot. Persistent CAS bytes remain."
  [{:keys [state] :as runtime} handle]
  (let [cid (cid-of runtime handle)]
    (swap! state (fn [current]
                   (-> current
                       (update :by-handle dissoc handle)
                       (update :by-cid dissoc cid))))
    {:released? true :handle handle :cid cid}))

(defn close!
  "Invalidate every handle in this run. Idempotent; does not collect CAS."
  [{:keys [state]}]
  (swap! state assoc :closed? true :by-cid {} :by-handle {})
  {:closed? true})

(defn stats
  "Non-authoritative runtime accounting; contains no values or capabilities."
  [{:keys [max-handles state]}]
  (let [{:keys [closed? next-handle by-handle]} @state]
    {:closed? closed? :capacity max-handles :used (count by-handle)
     :next-handle next-handle}))
