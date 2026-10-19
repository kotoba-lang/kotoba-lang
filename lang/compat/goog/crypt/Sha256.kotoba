;; nbb-only runtime shim -- NOT part of kotoba.net's own API surface, and
;; NOT a modification of kotoba.net.gossip/kotoba.net.bitswap (those two
;; namespaces are untouched; see git history/README -- this repo's
;; ADR-kotoba-net-p2p-semantics still owns their pure semantics).
;;
;; WHY THIS FILE EXISTS: kotoba.net.gossip/content-hash's :cljs branch
;; calls (goog.crypt.Sha256.) -- a real Google Closure Library class.
;; Under a full Closure-Compiler-based build (shadow-cljs, the environment
;; gossip.cljc's own top-of-file comment documents it was written/tested
;; against), the real goog.crypt.Sha256 module is available for free. nbb
;; (the Node-hosted ClojureScript runtime kotoba.wire.tcp -- and therefore
;; this repo's kotoba.net.transport.tcp real-socket adapter -- requires)
;; only bundles a small hand-picked subset of the Closure Library
;; (confirmed by inspecting nbb's own install: nbb_goog_string.js and
;; nbb_goog_crypt.js exist, but no Sha256 module) -- so
;; `(:require [goog.crypt.Sha256])` fails under plain nbb with "Could not
;; find namespace: goog.crypt.Sha256", even though the exact same source
;; compiles and runs fine under shadow-cljs. This is a genuine
;; environment gap between gossip.cljc's original cljs target and nbb,
;; not a bug in gossip.cljc's own logic -- see this repo's
;; kotoba.net.transport.tcp namespace docstring and README for the full
;; writeup, and clojure -M:test / a shadow-cljs build are both completely
;; unaffected (this file is never on either of those classpaths).
;;
;; FIX, WITHOUT TOUCHING gossip.cljc: nbb resolves an unrecognized
;; `goog.*` require by falling through to ordinary classpath-based
;; namespace resolution (confirmed empirically -- verified against a
;; real nbb 1.4.210 install before writing this file for real, not
;; assumed) exactly like any other `.cljs` namespace. Placing a file at
;; `goog/crypt/Sha256.cljs` on kotoba-net's own `src` classpath root (this
;; file) that DEFINES the missing `goog.crypt.Sha256` global the same way
;; kotoba-lang/dtn's kotoba.dtn.auth already solves the analogous
;; "no goog Closure hash module under nbb" problem for its own
;; HMAC-SHA256 -- Node's built-in `node:crypto` module, no npm dependency
;; -- satisfies gossip.cljc's require with zero edits to gossip.cljc
;; itself. Any consumer of kotoba-net (this repo's own tests/demo, or a
;; future caller) that puts `src` on its nbb --classpath automatically
;; picks this up, the same way it already picks up
;; kotoba.net.gossip/kotoba.net.bitswap from that same `src` root.
;;
;; SCOPE: implements only the two methods kotoba.net.gossip/content-hash
;; actually calls on a goog.crypt.Sha256 instance (`.update` and
;; `.digest`) -- not a general-purpose goog.crypt.Sha256 polyfill.
;; `.digest` returns a plain JS Array of byte values (0..255), matching
;; the real Closure Library class's own return shape closely enough for
;; gossip.cljc's `bytes->hex` (which just maps over it) to consume
;; unchanged. Verified byte-for-byte against known SHA-256 test vectors
;; (sha256("hello") / sha256("hi")) before being wired in for real.
(ns goog.crypt.Sha256
  (:require [goog.crypt]
            ["node:crypto" :as node-crypto]))

(defn- make-sha256 []
  (let [chunks (atom [])]
    #js {:update (fn [bytes]
                   (swap! chunks conj (js/Buffer.from (into-array bytes)))
                   nil)
         :digest (fn []
                   (let [h (node-crypto/createHash "sha256")]
                     (doseq [c @chunks] (.update h c))
                     (js/Array.from (.digest h))))}))

(set! (.-Sha256 goog.crypt) make-sha256)
