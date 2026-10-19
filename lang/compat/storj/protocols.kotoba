(ns storj.protocols
  "The transport seam.

  Crypto used to live here too; it moved to `sigv4.protocols` along with the
  signer itself, because every consumer of SigV4 in this workspace needs the
  same `ICrypto` and none of them agree on a transport. What is left is the one
  seam that is genuinely this library's: how a signed request reaches the wire.

  **Return-value contract.** `-request` may return either a plain value or a
  thenable. `storj.core` composes it through its own `then`, which is
  identity-application on the JVM and `.then` on JS.")

(defprotocol IHttp
  "Raw byte transport. The library performs zero network I/O itself; it hands
  you a fully signed request and you put it on the wire."
  (-request [this req]
    "`req` is `{:method :url :headers :body}` with `:headers` a string→string
    map. Returns `{:status :headers :body}` (or a thenable of one). Implementors
    must not follow redirects silently — a redirect invalidates the signature."))
