(ns kotoba.lang.package-contract
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [multiformats.core :as mf]
            #?(:clj [ed25519.core :as ed25519]))
  #?(:clj (:import (java.util Base64))))

(def manifest-required
  [:kotoba.package/name
   :kotoba.package/version
   :kotoba.package/repo-rid
   :kotoba.package/source
   :kotoba.package/capabilities
   :kotoba.package/signatures])

(def lock-required
  [:dep/name
   :dep/version
   :dep/repo-rid
   :dep/commit
   :dep/tree-cid
   :dep/manifest-cid
   :dep/signers
   :dep/capabilities])

(def allowed-package-kinds
  #{:library :adapter :schema-contract :tool :component})

(defn non-empty-string?
  [x]
  (and (string? x) (not (str/blank? x))))

(defn- read-varint
  "Unsigned LEB128 varint at OFFSET in BS (any indexable byte sequence --
  `nth`/`count` work uniformly on a JVM byte-array and a ClojureScript
  vector, so this needs no platform-specific reader-conditional branch).
  Returns [value next-offset]."
  [bs offset]
  (loop [offset offset value 0 shift 0]
    (let [b (bit-and (nth bs offset) 0xff)]
      (if (< b 0x80)
        [(bit-or value (bit-shift-left b shift)) (inc offset)]
        (recur (inc offset) (bit-or value (bit-shift-left (bit-and b 0x7f) shift)) (+ shift 7))))))

(defn cid?
  "A genuine CIDv1 structural check (`multiformats.core/cid->bytes` decode,
  then parse [version-varint][codec-varint][multihash: fn-varint
  len-varint digest]) -- not the previous `(str/starts-with? x \"bafy\")`
  prefix sniff, which never actually decoded anything and happily accepted
  strings containing characters (`0`/`1`/`8`/`9`, e.g. this repo's own
  former test fixtures like \"bafyrepojson111...\") that fall OUTSIDE the
  base32 'b'-multibase alphabet and could never have decoded as a real
  CID. Requires version 1 (the only version `multiformats.core/cidv1`
  emits) and the multihash's declared length to actually match the
  decoded digest's byte count -- a truncated or padded string fails here
  even if the multibase alphabet and varint framing otherwise parse."
  [x]
  (and (non-empty-string? x)
       (str/starts-with? x "b")
       (try
         (let [bs (mf/cid->bytes x)
               [version off1] (read-varint bs 0)
               [codec off2] (read-varint bs off1)
               [hash-fn off3] (read-varint bs off2)
               [hash-len off4] (read-varint bs off3)
               digest-len (- (count bs) off4)]
           (and (= 1 version)
                (pos? codec)
                (pos? hash-fn)
                (pos? hash-len)
                (= hash-len digest-len)))
         (catch #?(:clj Exception :cljs :default) _ false))))

(defn invalid
  [message data]
  {:valid? false :message message :data data})

(defn contract-keyword?
  [x]
  (and (keyword? x)
       (let [s (subs (str x) 1)]
         (or (str/starts-with? s "app.kotoba.")
             (str/starts-with? s "wire.kotoba.")))))

(defn missing-key
  [m keys message]
  (some (fn [k] (when-not (contains? m k) (invalid message {:missing k}))) keys))

(defn contract-vector-error
  [value field]
  (cond
    (and (some? value) (not (vector? value)))
    (invalid "contract surface vector required" {:field field :value value})

    :else
    (some (fn [contract]
            (when-not (contract-keyword? contract)
              (invalid "contract surface keyword required"
                       {:field field :value contract})))
          value)))

(defn contract-surfaces-error
  [m prefix]
  (or (contract-vector-error (get m (keyword prefix "provides"))
                             (keyword prefix "provides"))
      (contract-vector-error (get m (keyword prefix "consumes"))
                             (keyword prefix "consumes"))))

(defn- signed-bytes
  "UTF-8 bytes of S. Used to turn the canonical string a package signature
  attests to (its manifest's own declared :manifest-cid -- see
  `signatures-error`) into the byte message Ed25519 verify operates on.
  Portable (same #?(:cljs (.encode (js/TextEncoder.) s)) construction
  `multiformats.core/kotoba-cid` already uses in this dependency)."
  [^String s]
  #?(:clj (.getBytes s "UTF-8")
     :cljs (.encode (js/TextEncoder.) s)))

(defn- ed25519-signature-error
  "Real Ed25519 verification of SIG (a base64-encoded signature, the same
  encoding `cacao.core`/`kotoba-lang/org-chainagnostic-cacao` uses) against
  SIGNED bytes, under the public key encoded in DID (a did:key:z... string,
  `ed25519.core/did-key->pubkey`). nil when the signature verifies; a
  package-contract-shaped error otherwise. Never throws: bad base64, a
  malformed did:key, or a genuine signature mismatch are all reported as
  the same closed rejection, not an uncaught exception.

  :clj only. `ed25519.core` (kotoba-lang/ed25519) is a JVM
  (java.security-based) Ed25519 implementation; no portable/:cljs Ed25519
  verifier exists yet anywhere in this dependency graph, unlike
  `multiformats.core` (fully .cljc). Rather than silently treat an
  unverifiable signature as valid under :cljs, this fails CLOSED there --
  every signature is rejected until a portable verifier exists. That is a
  real, deliberate behavior change for any future :cljs consumer of this
  namespace (there is none today -- see the file/ns docs), not an
  oversight: accepting what cannot actually be verified would reintroduce
  the exact vulnerability this function exists to close."
  [sig did signed]
  #?(:clj
     (try
       (let [sig-bytes (.decode (Base64/getDecoder) ^String sig)]
         (when-not (ed25519/verify-did did signed sig-bytes)
           (invalid "signature verification failed" {:did did})))
       (catch Exception e
         (invalid "signature verification failed" {:did did :error (.getMessage e)})))
     :cljs
     (invalid "signature verification not supported in this runtime" {:did did})))

(defn signatures-error
  "Validates SIGS (a package manifest's :kotoba.package/signatures vector)
  for shape AND for real Ed25519 cryptographic authenticity: each
  signature's :sig must actually verify, under the Ed25519 public key
  encoded in its own :did, against MANIFEST-CID (the manifest's own
  declared :kotoba.package/source :manifest-cid -- the content-identifier
  every signer and verifier can derive from the manifest without agreeing
  on a byte-for-byte re-encoding here; `kotoba.package-admission/
  manifest-integrity-error` in the sibling kotoba-lang/kotoba repo
  separately verifies that CID actually matches the manifest's real
  content, so the two checks compose into signer-attests-to-CID +
  CID-matches-content).

  Before this fix, this function only checked shape (non-blank :did/:sig
  strings, :alg = :ed25519) and never called any verification -- ANY :sig
  string under ANY :did passed (docs/issues/
  security-package-contract-conformance-gap.md, F-001). A forged or
  malformed signature -- wrong key, tampered bytes, garbage :sig, a :did
  that never actually signed this manifest -- now fails closed (rejected),
  not silently accepted."
  [sigs manifest-cid]
  (cond
    (empty? sigs) (invalid "signature required" {})
    :else
    (some (fn [sig]
            (or (missing-key sig [:did :alg :sig] "signature missing required field")
                (when-not (non-empty-string? (:did sig))
                  (invalid "signature did required" {:signature sig}))
                (when-not (= :ed25519 (:alg sig))
                  (invalid "signature alg unsupported" {:signature sig}))
                (when-not (non-empty-string? (:sig sig))
                  (invalid "signature bytes required" {:signature sig}))
                (ed25519-signature-error (:sig sig) (:did sig) (signed-bytes manifest-cid))))
          sigs)))

(defn tree-cid-error
  "Real content-integrity check for a source tree -- the :tree-cid analogue
  of `kotoba.package-admission/manifest-integrity-error` (kotoba-lang/kotoba,
  which recomputes and compares :manifest-cid from a manifest's own EDN
  content). Computes `multiformats.core/cidv1-raw` of TREE-BYTES -- the
  caller-supplied REAL byte content of the source tree DECLARED is supposed
  to pin -- and compares. nil when they match; a package-contract-shaped
  error otherwise (fails closed on mismatch).

  This .cljc kernel is deliberately I/O-free (`package-manifest-error` /
  `lockfile-error` validate only already-parsed EDN, never touch a
  filesystem or a git object store). Unlike :manifest-cid, whose subject
  (the manifest's own EDN data) IS already an in-memory argument to
  `package-manifest-error`, :tree-cid's subject -- an actual source tree /
  git working tree -- is NOT part of the manifest/lock data this kernel
  receives at all. TREE-BYTES must therefore come from a caller that
  genuinely has filesystem/git access (this mirrors why
  manifest-integrity-error itself lives outside this kernel, in a separate
  file-I/O-capable layer, one repo over). `package-manifest-error` /
  `lockfile-error` accept optional tree content (`:tree-bytes` /
  `:tree-bytes-by-dep`) and call this when it is supplied; when it is not
  supplied (every existing caller and fixture, unchanged) they still only
  run the shape-only `cid?` check they always ran -- this function never
  silently no-ops a check by treating absent content as a pass."
  [declared tree-bytes]
  (cond
    (not (cid? declared)) (invalid "tree cid required" {:value declared})
    (nil? tree-bytes) (invalid "tree content required to verify tree cid" {:declared declared})
    :else
    (let [computed (mf/cidv1-raw tree-bytes)]
      (when (not= declared computed)
        (invalid "tree cid does not match tree content"
                 {:declared declared :computed computed})))))

(defn package-manifest-error
  "2-arity OPTS may carry :tree-bytes -- see `tree-cid-error` -- to also
  recompute-and-compare :tree-cid against real content; omitted (the
  1-arity, every existing caller) keeps the original shape-only :tree-cid
  behavior unchanged."
  ([m] (package-manifest-error m nil))
  ([m {:keys [tree-bytes]}]
   (let [source (:kotoba.package/source m)]
     (or
      (missing-key m manifest-required "missing required package field")
      (when (and (:kotoba.package/kind m)
                 (not (contains? allowed-package-kinds (:kotoba.package/kind m))))
        (invalid "unknown package kind"
                 {:package (:kotoba.package/name m)
                  :allowed allowed-package-kinds}))
      (contract-surfaces-error m "kotoba.package")
      (when (and (= :adapter (:kotoba.package/kind m))
                 (empty? (:kotoba.package/consumes m)))
        (invalid "adapter consumes required" {:package (:kotoba.package/name m)}))
      (when (and (= :schema-contract (:kotoba.package/kind m))
                 (empty? (:kotoba.package/provides m)))
        (invalid "schema-contract provides required" {:package (:kotoba.package/name m)}))
      (when-not (cid? (:kotoba.package/repo-rid m))
        (invalid "repo-rid cid required" {:value (:kotoba.package/repo-rid m)}))
      (missing-key source [:git-commit :tree-cid :manifest-cid] "missing required source field")
      (when-not (cid? (:tree-cid source))
        (invalid "tree cid required" {:source source}))
      (when tree-bytes (tree-cid-error (:tree-cid source) tree-bytes))
      (when-not (cid? (:manifest-cid source))
        (invalid "manifest cid required" {:source source}))
      (when-not (vector? (:kotoba.package/capabilities m))
        (invalid "capabilities vector required" {:value (:kotoba.package/capabilities m)}))
      (signatures-error (:kotoba.package/signatures m) (:manifest-cid source))))))

(defn lockfile-error
  "3-arity OPTS may carry :tree-bytes-by-dep -- {dep-name tree-bytes ...} --
  to also recompute-and-compare each dep's :dep/tree-cid against real
  content (see `tree-cid-error`); omitted (the 2-arity, every existing
  caller) keeps the original shape-only :dep/tree-cid behavior unchanged."
  ([m tc] (lockfile-error m tc nil))
  ([m tc {:keys [tree-bytes-by-dep]}]
   (let [declared (set (:declared-capabilities tc))
         blocked (set/union (set (:revoked-signers tc))
                            (set (:expired-signers tc))
                            (set (:compromised-signers tc)))]
     (or
      (when-not (= 1 (:kotoba.lock/version m))
        (invalid "lock version 1 required" {:value (:kotoba.lock/version m)}))
      (when-not (vector? (:deps m))
        (invalid "lock deps vector required" {:value (:deps m)}))
      (some (fn [dep]
              (or (missing-key dep lock-required "missing required lock field")
                  (when (and (:dep/kind dep)
                             (not (contains? allowed-package-kinds (:dep/kind dep))))
                    (invalid "unknown package kind" {:dependency (:dep/name dep)}))
                  (contract-surfaces-error dep "dep")
                  (some (fn [k]
                          (when-not (cid? (get dep k))
                            (invalid "cid required" {:field k :value (get dep k)})))
                        [:dep/repo-rid :dep/tree-cid :dep/manifest-cid])
                  (when-let [tree-bytes (get tree-bytes-by-dep (:dep/name dep))]
                    (tree-cid-error (:dep/tree-cid dep) tree-bytes))
                  (when-not (seq (:dep/signers dep))
                    (invalid "signer required" {:dep dep}))
                  (when-let [bad (seq (set/intersection (set (:dep/signers dep)) blocked))]
                    (invalid "signer not currently trusted"
                             {:signers (vec bad)
                              :dependency (:dep/name dep)}))
                  (when-not (set/subset? (set (:dep/capabilities dep)) declared)
                    (invalid "capability grant exceeds package declaration"
                             {:grant (:dep/capabilities dep)
                              :declared (:declared-capabilities tc)}))))
            (:deps m))))))

(defn validate-case
  [tc data]
  (let [result (case (:type tc)
                 :package-manifest (package-manifest-error data)
                 :lockfile (lockfile-error data tc)
                 (invalid "unknown case type" tc))]
    (if result result {:valid? true})))
