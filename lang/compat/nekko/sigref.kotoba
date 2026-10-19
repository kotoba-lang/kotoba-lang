(ns nekko.sigref
  "Signed refs: a did:key-holding delegate attests \"ref-name currently
   points at commit-cid, for this RID\" -- the Radicle rad/sigrefs
   equivalent. kotoba-rad only ever deals in CID strings here, not
   kotoba-git objects directly, so the two repos stay decoupled.

   NOTE: ed25519.core is JVM-only today -- see nekko.delegate."
  (:require [cbor.core :as cbor]
            [ed25519.core :as ed]))

(defn- payload-bytes [rid ref-name commit-cid ts]
  (cbor/encode {"rid" rid "ref" ref-name "commit" commit-cid "ts" ts}))

(defn sign
  "Sign an attestation that ref-name -> commit-cid under rid, as signer-seed's
   did:key. Returns the sigref map (ready to hand to push-gate/authorize-push?)."
  [signer-seed rid ref-name commit-cid ts]
  {"rid" rid "ref" ref-name "commit" commit-cid "ts" ts
   "signer" (ed/did-key-from-seed signer-seed)
   "sig" (ed/hexify (ed/sign signer-seed (payload-bytes rid ref-name commit-cid ts)))})

(defn valid?
  "Does sigref's :sig verify against its own :signer for its (rid,ref,commit,ts)?"
  [sigref]
  (boolean
   (when-let [sig (get sigref "sig")]
     (ed/verify-did (get sigref "signer")
                     (payload-bytes (get sigref "rid") (get sigref "ref")
                                     (get sigref "commit") (get sigref "ts"))
                     (ed/unhex sig)))))
