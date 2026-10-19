(ns nekko.delegate
  "Delegate authorization derived from a signed identity journal. The owner
   (identity genesis' did) is always authorized; delegate-add/delegate-remove
   entries must themselves be signed by an already-authorized did:key, so
   the delegate set can only ever grow/shrink by consent of an existing
   authority -- there is no 'grant me admin' bootstrap other than owning
   the genesis did's private key.

   NOTE: ed25519.core is JVM-only today (no :cljs branch upstream), so this
   namespace, despite the .cljc extension (matching sibling convention), is
   effectively Clojure/JVM-only until that changes."
  (:require [nekko.journal :as journal]
            [cbor.core :as cbor]
            [ed25519.core :as ed]))

(defn- delegate-payload-bytes [kind did ts]
  (cbor/encode {"kind" kind "did" did "ts" ts}))

(defn entry-valid?
  "Does entry's :sig verify against its own :signer for its (kind,did,ts)?"
  [entry]
  (boolean
   (when-let [sig (get entry "sig")]
     (ed/verify-did (get entry "signer")
                     (delegate-payload-bytes (get entry "kind") (get entry "did") (get entry "ts"))
                     (ed/unhex sig)))))

(defn delegates
  "Fold the journal into the currently-authorized delegate set (did:key
   strings). Unsigned or invalid entries are ignored (see entry-valid?)."
  [get-fn head-cid owner-did]
  (reduce (fn [ds entry]
            (if (entry-valid? entry)
              (case (get entry "kind")
                "delegate-add" (conj ds (get entry "did"))
                "delegate-remove" (disj ds (get entry "did"))
                ds)
              ds))
          #{owner-did}
          (journal/entries get-fn head-cid)))

(defn authorized?
  [get-fn head-cid owner-did did]
  (contains? (delegates get-fn head-cid owner-did) did))

(defn- signed-entry [signer-seed kind did ts]
  (let [signer (ed/did-key-from-seed signer-seed)]
    {"kind" kind "did" did "ts" ts "signer" signer
     "sig" (ed/hexify (ed/sign signer-seed (delegate-payload-bytes kind did ts)))}))

(defn add-delegate!
  "signer-seed's did:key must already be authorized (owner or an existing
   delegate). Throws if not. Returns the journal's new head-cid."
  [put! get-fn head-cid owner-did signer-seed added-did ts]
  (let [signer-did (ed/did-key-from-seed signer-seed)]
    (when-not (authorized? get-fn head-cid owner-did signer-did)
      (throw (ex-info "signer is not an authorized delegate" {:signer signer-did})))
    (journal/append! put! get-fn head-cid (signed-entry signer-seed "delegate-add" added-did ts))))

(defn remove-delegate!
  [put! get-fn head-cid owner-did signer-seed removed-did ts]
  (let [signer-did (ed/did-key-from-seed signer-seed)]
    (when-not (authorized? get-fn head-cid owner-did signer-did)
      (throw (ex-info "signer is not an authorized delegate" {:signer signer-did})))
    (journal/append! put! get-fn head-cid (signed-entry signer-seed "delegate-remove" removed-did ts))))

(defn verify-journal
  "Every entry must be both hash-chain-valid (journal/verify) AND carry a
   signature that verifies against its own claimed :signer."
  [get-fn head-cid]
  (and (journal/verify get-fn head-cid)
       (every? entry-valid? (journal/entries get-fn head-cid))))
