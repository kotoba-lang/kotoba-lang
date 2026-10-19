(ns kotoba.lang.witness-quorum.signer
  "Production Ed25519 signer + verifier.

  Pairs with the Python implementation in
  `40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/kotoba-datomic.py`
  (`make_ed25519_signer` + `verify_ed25519_signature`) and the original TS
  implementation in this repo's `src/signer.ts` (which uses
  `@noble/curves/ed25519`). All three sides use the same canonical bytes
  (see `kotoba.lang.witness-quorum.attestation/canonical-attestation-bytes`),
  so a signature produced on any side verifies on the others.

  Built on `kotoba-lang/ed25519` (`ed25519.core`), NOT Bouncy Castle and NOT
  the JDK's `java.security` Ed25519 KeyPairGenerator/KeyFactory used
  free-hand. Two independent reasons ruled those out:
    - JDK's native Ed25519 key generation does not derive the same public
      key from a raw 32-byte seed that RFC 8032 / @noble/curves do, even
      driven through a fixed-output SecureRandom (verified empirically).
    - Bouncy Castle is not loadable under babashka -- its GraalVM native
      image has no `org.bouncycastle.*` classes baked in, so any BC import
      throws `ClassNotFoundException` at runtime under `bb` -- which
      matters here because this package's own docstrings describe an
      exclusively bb-based Murakumo-cell/orchestrator deployment.
  `ed25519.core` solves both: it derives the public key from a raw seed via
  pure RFC-8032 math (`java.security.MessageDigest` SHA-512 + `BigInteger`
  only), then signs/verifies via the JDK's own built-in `java.security`
  Ed25519 `Signature`/`KeyFactory` (core JDK, not a 3rd-party jar -- fine
  under `bb`) wrapped with the minimal PKCS8/X.509 envelopes those APIs
  require. Verified byte-for-byte identical to @noble/curves'
  getPublicKey/sign for a known seed (see this namespace's test suite for
  the pinned cross-language vector).

  CLJ port of this repo's original @etzhayyim/witness-quorum src/signer.ts.
  JVM-only (and, unlike the Bouncy-Castle-based first draft of this port,
  actually bb-compatible) -- see the repo README.

  Per kotoba-datomic SPEC S5 + ADR-2605231400."
  (:require [ed25519.core :as ed]))

(defn make-ed25519-cell-signer
  "Build a production CellSigner over an Ed25519 private key.

  `private-key` a raw 32-byte Ed25519 seed (byte[]). Throws if not 32 bytes.
  Returns a function `(fn [canonical-bytes] -> signature-bytes)` (64 bytes)."
  [^bytes private-key]
  (when (not= (alength private-key) 32)
    (throw (ex-info (str "Ed25519 private key must be 32 bytes, got " (alength private-key))
                     {:length (alength private-key)})))
  (fn [^bytes canonical-bytes]
    (ed/sign private-key canonical-bytes)))

(defn ed25519-public-key-bytes
  "Derive the raw 32-byte Ed25519 public key from a 32-byte seed."
  [^bytes private-key]
  (when (not= (alength private-key) 32)
    (throw (ex-info (str "Ed25519 private key must be 32 bytes, got " (alength private-key))
                     {:length (alength private-key)})))
  (ed/pubkey-from-seed private-key))

(defn verify-ed25519-signature
  "Third-party Ed25519 verifier -- given canonical bytes, a detached
  signature, and the cell's published public key, returns whether the
  signature is valid. This is what the orchestrator runs on each
  attestation it collects via the witness transport."
  [^bytes canonical ^bytes signature ^bytes public-key]
  (when (not= (alength public-key) 32)
    (throw (ex-info (str "Ed25519 public key must be 32 bytes, got " (alength public-key))
                     {:length (alength public-key)})))
  (if (not= (alength signature) 64)
    false
    (try
      (boolean (ed/verify public-key canonical signature))
      (catch Exception _ false))))
