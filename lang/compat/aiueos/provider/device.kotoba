(ns aiueos.provider.device
  "The device half of enrolment, as mechanism rather than decision.

  `aiueos.enroll` decides whether a claim may bind a device to an owner, and it
  refuses to decide without a verified `:possession-proof-valid?`. This
  namespace is what produces and checks that answer: it generates the
  operational key on the device, signs an enrolment challenge with it, and
  verifies such a signature against an expected challenge.

  JVM-only, like `aiueos.key-lifecycle` and `aiueos.entropy` — key custody is a
  host concern and there is no portable answer to it. No new dependency: the
  Ed25519 primitives are the JDK's, reached through `aiueos.key-lifecycle`.

  ## The private key is generated here and never leaves

  `generate-operational-keypair!` is the only place a device key comes into
  existence. What crosses the wire afterwards is the *public* half plus a
  signature — never the private key, and never a seed it could be derived from.
  That is what makes \"per-device keys\" a real property rather than a naming
  convention over one operator secret.

  ## Naming is deliberately not done here

  A `did:key` is a public key in a particular multibase/multicodec envelope, and
  the library that owns that envelope is `kotoba-lang/org-w3-did`. aiueos is
  dependency-minimal by invariant (its `deps.edn` carries the shared security
  package and Chicory, nothing else), so it exports the raw public key and lets
  the fleet plane name it. **The device owns the key; the fleet owns the name.**

  ## What a proof has to bind, and why each binding is there

  A signature over a bare nonce proves possession of *a* key at *some* point,
  which is not the question. `challenge` binds four things and verification
  checks all of them:

  - the **public key**, so a proof made by one device cannot be replayed for
    another;
  - the **nonce**, so a proof cannot be replayed at all;
  - the **endpoint**, so a proof harvested by one enrolment service cannot be
    presented to a different one;
  - the **purpose**, so an enrolment proof cannot be lifted into a different
    protocol that happens to sign the same shape."
  (:require [aiueos.key-lifecycle :as kl]
            [aiueos.signing :as signing])
  (:import [java.security KeyPair PrivateKey]
           [java.util Base64]))

(def challenge-version 1)
(def signature-key :aiueos.device/signature)

(defn generate-operational-keypair!
  "A fresh Ed25519 keypair for this device. The private half exists only in this
  process's memory until a storage provider seals it; nothing here writes it
  anywhere."
  ^KeyPair []
  (kl/generate-key-pair))

(defn public-key-hex
  "The raw 32-byte Ed25519 public key as hex — the form the fleet plane turns
  into a `did:key`."
  [^KeyPair kp]
  (kl/raw-public-hex kp))

(defn public-key-base64
  "The X.509 SubjectPublicKeyInfo form, which is what
  `aiueos.key-lifecycle/document-signature-valid?` verifies against."
  [^KeyPair kp]
  (kl/public-key-base64 kp))

(defn challenge
  "The document a device signs to prove it holds the key behind `public-key-b64`.

  Deterministic by construction: `aiueos.key-lifecycle/document-bytes` is the
  one canonicaliser in this repository, and its own docstring says a second one
  would be a signature-confusion hazard. This function therefore builds a map
  and signs *that*, rather than concatenating a string of its own."
  [{:keys [public-key-b64 nonce endpoint issued-ms]}]
  {:aiueos.device/version challenge-version
   :aiueos.device/purpose :enrollment
   :aiueos.device/public-key public-key-b64
   :aiueos.device/nonce nonce
   :aiueos.device/endpoint endpoint
   :aiueos.device/issued-ms issued-ms})

(defn sign-challenge
  "Sign `chal` with the device's private key. Returns the challenge with the
  signature attached."
  [chal ^PrivateKey private-key]
  (kl/sign-document chal signature-key private-key))

(def mismatch-reasons
  "Every way a proof can fail to be about the thing the verifier asked. Reported
  individually because \"the proof did not check out\" is not actionable and
  these are."
  #{:missing-signature :version-mismatch :purpose-mismatch :public-key-mismatch
    :nonce-mismatch :endpoint-mismatch :bad-signature})

(defn- mismatch [reason] {:aiueos.device/valid? false :aiueos.device/reason reason})

(defn verify-possession
  "Check `signed` against what the verifier actually issued.

  `expected` — `{:public-key-b64 :nonce :endpoint}`. Every field is compared;
  none is optional. A verifier that does not know what it issued cannot verify
  anything, so a missing expectation fails rather than being skipped.

  Returns `{:aiueos.device/valid? true}` or a map naming the mismatch. The
  signature is checked **last**, so a replayed-but-validly-signed proof is
  reported as the replay it is instead of as a signature success."
  [signed {:keys [public-key-b64 nonce endpoint]}]
  (cond
    (nil? (get signed signature-key)) (mismatch :missing-signature)
    (not= challenge-version (:aiueos.device/version signed)) (mismatch :version-mismatch)
    (not= :enrollment (:aiueos.device/purpose signed)) (mismatch :purpose-mismatch)
    (or (nil? public-key-b64)
        (not= public-key-b64 (:aiueos.device/public-key signed)))
    (mismatch :public-key-mismatch)
    (or (nil? nonce) (not= nonce (:aiueos.device/nonce signed))) (mismatch :nonce-mismatch)
    (or (nil? endpoint) (not= endpoint (:aiueos.device/endpoint signed)))
    (mismatch :endpoint-mismatch)
    (not (kl/document-signature-valid? signed signature-key public-key-b64))
    (mismatch :bad-signature)
    :else {:aiueos.device/valid? true
           :aiueos.device/public-key public-key-b64}))

(defn possession-proof-valid?
  "The single boolean `aiueos.enroll/claim` consumes. Kept as its own function
  so the decision layer never has to know the shape of a verification result,
  and so a caller cannot accidentally pass a truthy *map* where a boolean was
  meant — every mismatch map is truthy."
  [signed expected]
  (true? (:aiueos.device/valid? (verify-possession signed expected))))

(defn fingerprint
  "A short, stable, human-comparable digest of a public key, for a console to
  show next to a device. Not an identifier — the fleet plane's `did:key` is."
  [public-key-b64]
  (when public-key-b64
    (subs (signing/sha256-hex (.decode (Base64/getDecoder) ^String public-key-b64)) 0 16)))
