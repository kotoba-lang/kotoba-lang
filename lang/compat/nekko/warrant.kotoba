(ns nekko.warrant
  "Signed inert evidence of a peer-detected invalid journal event
   (double-history / fork evidence). Implements landing path (a) of
   ADR-2809060410: a warrant is peer-observed evidence that event E,
   rejected under ref R of RID, was seen and signed by delegate X. It is
   NOT authority and does no canonicalization -- adjudication is out of
   scope. Reuses the Ed25519 did:key signature envelope from nekko.sigref
   so there is one signing seam, not two.

   `:warrant/kind` names the violation from ADR-2809060410's table
   (InvalidSignature, SeqBreak, PrevMismatch, CacaoInvalid,
   ProllyInconsistent, plus the fork-specific OrderInvalid and
   EntryOrderMismatch); :warrant/reason keeps the ref_event mechanism's
   native :reason keyword for traceability."
  (:require [cbor.core :as cbor]
            [ed25519.core :as ed]))

(defn- payload-bytes [kind rid ref event]
  (cbor/encode {"kind" (name kind) "rid" rid "ref" ref "event" event}))

(defn issue
  "Sign that event E (the rejected body) was received under `rid`/`ref` with
   violation `kind`. `signer-seed` is the observing delegate's Ed25519 seed.
   Returns the warrant envelope (an inert signed evidence record)."
  [signer-seed kind rid ref event]
  {:warrant/kind kind
   :warrant/rid rid
   :warrant/ref ref
   :warrant/event event
   :warrant/signer (ed/did-key-from-seed signer-seed)
   :warrant/sig (ed/hexify (ed/sign signer-seed (payload-bytes kind rid ref event)))})

(defn valid?
  "Does the warrant's :warrant/sig verify against its own :warrant/signer for
   the recorded kind/rid/ref/event? True for an untampered warrant (or when no
   sig is present -> false), false for a tampered one or a wrong signer."
  [w]
  (boolean
   (when-let [sig (get w :warrant/sig)]
     (ed/verify-did (get w :warrant/signer)
                    (payload-bytes (get w :warrant/kind)
                                   (get w :warrant/rid)
                                   (get w :warrant/ref)
                                   (get w :warrant/event))
                    (ed/unhex sig)))))
