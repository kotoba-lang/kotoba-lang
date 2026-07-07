(ns kotoba.lang.package-contract
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [multiformats.core :as mf]))

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

(defn signatures-error
  [sigs]
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
                  (invalid "signature bytes required" {:signature sig}))))
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
     (missing-key source [:git-commit :tree-cid :manifest-cid] "missing required source field")
     (when-not (cid? (:tree-cid source))
       (invalid "tree cid required" {:source source}))
     (when-not (cid? (:manifest-cid source))
       (invalid "manifest cid required" {:source source}))
     (when-not (vector? (:kotoba.package/capabilities m))
       (invalid "capabilities vector required" {:value (:kotoba.package/capabilities m)}))
     (signatures-error (:kotoba.package/signatures m)))))

(defn lockfile-error
  [m tc]
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
           (:deps m)))))

(defn validate-case
  [tc data]
  (let [result (case (:type tc)
                 :package-manifest (package-manifest-error data)
                 :lockfile (lockfile-error data tc)
                 (invalid "unknown case type" tc))]
    (if result result {:valid? true})))
