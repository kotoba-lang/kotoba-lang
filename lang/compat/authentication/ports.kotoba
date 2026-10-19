(ns authentication.ports)

(defprotocol IFactorVerifier
  (verify-factor! [port factor-request response]))

(defprotocol IAsyncFactorVerifier
  "A factor verifier whose underlying ceremony is asynchronous -- the shape
  every WebCrypto-backed edge verifier has, since `crypto.subtle` returns
  Promises. Implementations return a Promise of the same factor map
  `IFactorVerifier/verify-factor!` returns synchronously.

  Kept as a separate protocol rather than making `IFactorVerifier` async:
  the JVM/Node adapters (local, static, OAuth/OIDC/SAML result shapes) are
  genuinely synchronous, and forcing every one of them to hand back a
  Promise would push js interop into `.cljc` files that currently compile
  on both hosts. `authentication.async/authenticate!` accepts a verifier
  map holding either kind, so a host mixes them freely."
  (verify-factor-async! [port factor-request response]))

(defn verifier-map
  "Build a factor-type -> IFactorVerifier map."
  [& kvs]
  (apply hash-map kvs))
