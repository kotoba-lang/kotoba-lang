(ns sigv4.protocols
  "The one host seam this library needs: crypto.

  It lives in its own namespace so `sigv4.crypto` (a default implementation)
  and `sigv4.core`'s consumers can both depend on it without a cycle, and so a
  host can satisfy it without pulling in either.

  Transport is deliberately absent. A signer produces a signed request; putting
  it on the wire belongs to whoever owns the connection, and every consumer here
  already has its own (a Worker's `fetch`, a peer's object store, a JVM client).

  **Return-value contract.** Every method may return either a plain value or a
  thenable. Consumers compose them through their own `then`, which is
  identity-application on the JVM and `.then` on JS — so a synchronous
  `javax.crypto` implementation and an asynchronous `crypto.subtle` one drive
  exactly the same code path.")

(defprotocol ICrypto
  "SHA-256 and HMAC-SHA-256. Deliberately tiny: SigV4 needs nothing else, and a
  small surface is one a host can implement in a few lines on any runtime."
  (-sha256-hex [this data]
    "Hex-encoded (lowercase) SHA-256 of `data` — a string or a byte container.")
  (-hmac [this key data]
    "HMAC-SHA-256 of the string `data` under `key` (a string, or the raw byte
    output of a previous `-hmac` — the key-derivation ladder chains them).
    Returns raw bytes.")
  (-hex [this bytes]
    "Lowercase hex encoding of raw bytes, for the final signature."))
