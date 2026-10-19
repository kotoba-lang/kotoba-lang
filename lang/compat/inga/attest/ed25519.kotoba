(ns inga.attest.ed25519
  "Ed25519 through WebCrypto — the only place in inga that knows what a curve
  is.

  Named under `inga.attest` rather than beside engi's `engi.crypto`
  (kotobase's client-side key handling, which stayed in that repo) because what it implements is
  the verifier `inga.attest` takes as a parameter, and a name that says so is
  worth more than a name that groups by subject matter.

  Everything above takes verification as a parameter, for the reason that
  decision keeps earning: `kotoba-lang/ed25519` is JVM-only, and a browser
  that cannot re-verify a certificate is not a verifier. This exists so there
  is ONE implementation of the platform call rather than one per node.

  ## The witness id IS the public key

  A witness is named by its base64 raw public key. That removes the registry a
  name-to-key mapping would need, and with it the question of who may edit
  that registry — the same conclusion `torihiki.auth` reached from the other
  direction, where the key is the identity and the id is a handle for it.

  It also makes a certificate self-describing: everything needed to check it
  is inside it."
  (:require [inga.attest :as att]))

(defn- b64->bytes [s]
  (let [bin (js/atob s) n (.-length bin) out (js/Uint8Array. n)]
    (dotimes [i n] (aset out i (.charCodeAt bin i)))
    out))

(defn- utf8 [s] (.encode (js/TextEncoder.) s))

(defn verify-one
  "A promise of true/false. Never rejects: a key that will not import and a
  signature that will not verify are the same answer to the caller, and a
  rejected promise here would surface as an unhandled rejection in whatever
  event loop happens to be running."
  [pubkey-b64 payload sig-b64]
  (-> (js/crypto.subtle.importKey "raw" (b64->bytes pubkey-b64)
                                  #js {:name "Ed25519"} false #js ["verify"])
      (.then (fn [k]
               (js/crypto.subtle.verify #js {:name "Ed25519"} k
                                        (b64->bytes sig-b64) (utf8 payload))))
      (.then (fn [ok] (true? ok)))
      (.catch (fn [_] false))))

(defn resolve-certificate
  "Verify every signature a certificate needs, and return a promise of a
  `verify-fn` for `inga.attest/verify-certificate`.

  This is the whole bridge between an asynchronous platform primitive and a
  synchronous rule. The rule does not become async; the caller resolves first."
  [qc chain-id]
  (let [checks (att/pending-checks qc chain-id)]
    (-> (js/Promise.all
         (clj->js (map (fn [[w payload sig]]
                         (.then (verify-one w payload sig)
                                (fn [ok] #js [w payload sig ok])))
                       checks)))
        (.then (fn [results]
                 (att/lookup-verifier
                  (into {} (map (fn [r] [[(aget r 0) (aget r 1) (aget r 2)]
                                         (aget r 3)]))
                        (array-seq results))))))))

(defn verify-certificate!
  "A promise of `inga.attest/verify-certificate`'s answer: nil, or a reason."
  [qc chain-id quorum admitted?]
  (-> (resolve-certificate qc chain-id)
      (.then (fn [vf] (att/verify-certificate qc chain-id quorum vf admitted?)))))
