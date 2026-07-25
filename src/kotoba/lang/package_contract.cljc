(ns kotoba.lang.package-contract
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [kotoba.lang.code-identity :as identity]
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

(def semver-pattern
  #"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$")

(defn semver? [value]
  (boolean (and (string? value)
                (re-matches semver-pattern value))))

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

(declare invalid)

(defn component-cid-of
  "Extracts an optional component CID from either a lock entry or manifest.
  A component package must carry this pin; other package kinds may carry it as
  reproducible-build evidence."
  [m]
  (or (:dep/component-cid m)
      (:kotoba.package/component-cid m)
      (get-in m [:dep/build :component-cid])
      (get-in m [:kotoba.package/build :component-cid])))

(defn component-cid-error
  "Validates a component CID and, when bytes are supplied by the admission
  layer, recomputes the raw CID.  This keeps content verification at the
  package/runtime boundary rather than trusting a declaration-shaped string."
  ([declared] (component-cid-error declared nil))
  ([declared component-bytes]
   (cond
     (not (cid? declared)) (invalid "component cid required" {:value declared})
     (nil? component-bytes) nil
     :else (let [computed (mf/cidv1-raw component-bytes)]
             (when (not= declared computed)
               (invalid "component cid does not match component content"
                        {:declared declared :computed computed}))))))

(defn- lock-dep-component-error [dep component-bytes]
  (let [declared (component-cid-of dep)]
    (cond
      (and (= :component (:dep/kind dep)) (nil? declared))
      (invalid "component cid required" {:dependency (:dep/name dep)})

      (some? declared) (component-cid-error declared component-bytes)
      :else nil)))

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

(defn- signed-bytes [^String s]
  #?(:clj (.getBytes s "UTF-8")
     :cljs (.encode (js/TextEncoder.) s)))

(defn- ed25519-signature-error
  "Fail closed when SIG does not verify under DID for SIGNED bytes. The CLJS
  contract has no approved verifier yet, so it rejects instead of accepting
  shape-only signatures."
  [sig did signed]
  #?(:clj
     (try
       (let [sig-bytes (.decode (Base64/getDecoder) ^String sig)]
         (when-not (ed25519/verify-did did signed sig-bytes)
           (invalid "signature verification failed" {:did did})))
       (catch Exception e
         (invalid "signature verification failed"
                  {:did did :error (.getMessage e)})))
     :cljs
     (invalid "signature verification not supported in this runtime"
              {:did did})))

(defn signatures-error
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
                (ed25519-signature-error
                 (:sig sig) (:did sig) (signed-bytes manifest-cid))))
          sigs)))

(defn package-manifest-error
  [m]
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
     (when-not (semver? (:kotoba.package/version m))
       (invalid "package semver required"
                {:value (:kotoba.package/version m)}))
     (missing-key source [:git-commit :tree-cid :manifest-cid] "missing required source field")
     (when-not (cid? (:tree-cid source))
       (invalid "tree cid required" {:source source}))
     (when-not (cid? (:manifest-cid source))
       (invalid "manifest cid required" {:source source}))
     (when-not (vector? (:kotoba.package/capabilities m))
       (invalid "capabilities vector required" {:value (:kotoba.package/capabilities m)}))
     (signatures-error (:kotoba.package/signatures m)
                       (:manifest-cid source)))))

(defn lockfile-error
  "The optional third argument carries component bytes indexed by dependency
  name and/or resolved definition records. It is the admission-layer bridge
  used by `kotoba wasm safe-build`; the two-arity form remains pure EDN
  validation for conformance tooling."
  ([m tc] (lockfile-error m tc nil))
  ([m tc {:keys [component-bytes-by-dep resolved-definitions]}]
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
                 (when-not (semver? (:dep/version dep))
                   (invalid "dependency semver required"
                            {:dependency (:dep/name dep)
                             :value (:dep/version dep)}))
                 (some (fn [k]
                         (when-not (cid? (get dep k))
                           (invalid "cid required" {:field k :value (get dep k)})))
                       [:dep/repo-rid :dep/tree-cid :dep/manifest-cid])
                 (when (and (contains? dep :dep/definition-cids)
                            (or (not (vector? (:dep/definition-cids dep)))
                                (not (every? identity/cid? (:dep/definition-cids dep)))
                                (not (= (count (:dep/definition-cids dep))
                                        (count (set (:dep/definition-cids dep)))))))
                   (invalid "definition cids must be a unique CID vector"
                            {:dependency (:dep/name dep)
                             :value (:dep/definition-cids dep)}))
                 (lock-dep-component-error
                  dep (get component-bytes-by-dep (:dep/name dep)))
                 (when-not (seq (:dep/signers dep))
                   (invalid "signer required" {:dep dep}))
                 (when-let [bad (seq (set/intersection (set (:dep/signers dep)) blocked))]
                   (invalid "signer not currently trusted"
                            {:signers (vec bad)
                             :dependency (:dep/name dep)}))
                 (when-not (set/subset? (set (:dep/capabilities dep)) declared)
                   (invalid "capability grant exceeds package declaration"
                            {:grant (:dep/capabilities dep)
                             :declared (:declared-capabilities tc)})))
           (:deps m))
     (when-let [resolved (or resolved-definitions (:resolved-definitions tc))]
       (let [result (identity/verify-locked-definitions m resolved)]
         (when-not (:ok? result)
           (invalid "definition identity lock verification failed" result))))))))

(defn validate-case
  [tc data]
  (let [result (case (:type tc)
                 :package-manifest (package-manifest-error data)
                 :lockfile (lockfile-error data tc)
                 (invalid "unknown case type" tc))]
    (if result result {:valid? true})))
