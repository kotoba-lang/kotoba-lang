(ns kotoba.signed-module
  "L6 / S5: signed guest module envelopes (ADR-2607180900).

  A closed guest component (wasm bytes or sealed graph payload) is content-
  addressed by CIDv1-raw and attested by an Ed25519 signature over a
  canonical statement. This is not ambient package require — it is a
  supply-chain pin for an already-admitted component artifact.

  Signing uses `ed25519.core` (same algorithm as package-manifest signatures
  and actor:host gen-keypair/sign). Trust is a simple DID allow/deny set
  compatible with package-admission key-register folding."
  (:require [ed25519.core :as ed]
            [kotoba.package-admission :as package-admission])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]
           [java.util Base64]
           [java.time Instant]))

(def encoder (Base64/getEncoder))
(def decoder (Base64/getDecoder))

(defn- b64 ^String [^bytes b] (.encodeToString encoder b))
(defn- unb64 ^bytes [^String s] (.decode decoder s))

(defn- utf8 ^bytes [^String s] (.getBytes s StandardCharsets/UTF_8))

(defn sha256-hex
  [^bytes bs]
  (let [d (.digest (MessageDigest/getInstance "SHA-256") bs)]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) d))))

(defn component-cid
  "CIDv1-raw of COMPONENT-BYTES."
  [component-bytes]
  (package-admission/compute-component-cid component-bytes))

(defn module-record
  "Build the unsigned module body pinned by component content."
  [{:keys [component-bytes exports capabilities module-graph-digest name version]
    :or {exports [] capabilities []}}]
  (let [cid (component-cid component-bytes)]
    (cond-> {:format :kotoba.module/v1
             :name (or name "anonymous-guest")
             :version (or version "0.0.0")
             :component-cid cid
             :component-sha256 (sha256-hex component-bytes)
             :exports (vec exports)
             :capabilities (vec capabilities)}
      (some? module-graph-digest)
      (assoc :module-graph-digest module-graph-digest))))

(defn- statement-bytes
  "Canonical UTF-8 bytes signed by the publisher. Order is fixed so hosts
  recompute identically."
  [{:keys [component-cid component-sha256 signer not-before expires name version]}]
  (utf8
   (str "kotoba.signed-module/v1\n"
        "name:" name "\n"
        "version:" version "\n"
        "component-cid:" component-cid "\n"
        "component-sha256:" component-sha256 "\n"
        "signer:" signer "\n"
        "not-before:" not-before "\n"
        "expires:" expires "\n")))

(defn sign
  "Sign COMPONENT-BYTES with a 32-byte Ed25519 SEED.

  OPTS: :exports :capabilities :module-graph-digest :name :version
        :not-before :expires (ISO-8601 date strings, default now / +365d)
        :seed (byte-array 32) required.

  Returns a signed-module envelope map."
  [component-bytes {:keys [seed not-before expires] :as opts}]
  (when-not (and (bytes? seed) (= 32 (count seed)))
    (throw (ex-info "signed-module requires a 32-byte Ed25519 seed"
                    {:phase :signed-module})))
  (let [module (module-record (assoc opts :component-bytes component-bytes))
        did (ed/did-key-from-seed seed)
        now (or not-before (subs (str (Instant/now)) 0 10))
        exp (or expires "2099-01-01")
        statement {:format :kotoba.module-statement/v1
                   :name (:name module)
                   :version (:version module)
                   :component-cid (:component-cid module)
                   :component-sha256 (:component-sha256 module)
                   :signer did
                   :not-before now
                   :expires exp}
        sig (b64 (ed/sign seed (statement-bytes statement)))]
    {:format :kotoba.signed-module/v1
     :module module
     :statement statement
     :signature sig}))

(defn- date<=?
  [a b]
  (not (pos? (compare a b))))

(defn verify
  "Verify a signed-module envelope.

  TRUST: {:trusted-signers #{did...} :revoked-signers #{did...}}
  OPTS:  {:now \"YYYY-MM-DD\" :component-bytes <bytes optional integrity>}

  Returns {:ok? true :module ... :signer ...} or {:ok? false :problems [...]}."
  ([envelope trust] (verify envelope trust nil))
  ([envelope trust {:keys [now component-bytes]}]
   (let [now (or now (subs (str (Instant/now)) 0 10))
         problems
         (cond-> []
           (not (map? envelope))
           (conj {:problem :signed-module/not-a-map})

           (not= :kotoba.signed-module/v1 (:format envelope))
           (conj {:problem :signed-module/format})

           (not (map? (:module envelope)))
           (conj {:problem :signed-module/module})

           (not (map? (:statement envelope)))
           (conj {:problem :signed-module/statement})

           (not (string? (:signature envelope)))
           (conj {:problem :signed-module/signature-missing}))]
     (if (seq problems)
       {:ok? false :problems problems}
       (let [module (:module envelope)
             statement (:statement envelope)
             signer (:signer statement)
             trusted (set (or (:trusted-signers trust) #{}))
             revoked (set (or (:revoked-signers trust) #{}))
             sig-ok?
             (try
               (ed/verify-did signer
                              (statement-bytes statement)
                              (unb64 (:signature envelope)))
               (catch Exception _ false))
             integrity-problems
             (cond-> []
               (not= (:component-cid module) (:component-cid statement))
               (conj {:problem :signed-module/cid-mismatch})

               (not= (:component-sha256 module) (:component-sha256 statement))
               (conj {:problem :signed-module/sha-mismatch})

               (and (some? component-bytes)
                    (not= (component-cid component-bytes)
                          (:component-cid module)))
               (conj {:problem :signed-module/content-cid-mismatch})

               (and (some? component-bytes)
                    (not= (sha256-hex component-bytes)
                          (:component-sha256 module)))
               (conj {:problem :signed-module/content-sha-mismatch})

               (and (seq trusted) (not (contains? trusted signer)))
               (conj {:problem :signed-module/signer-not-trusted :signer signer})

               (contains? revoked signer)
               (conj {:problem :signed-module/signer-revoked :signer signer})

               (and (string? (:not-before statement))
                    (neg? (compare now (:not-before statement))))
               (conj {:problem :signed-module/not-yet-valid})

               (and (string? (:expires statement))
                    (not (date<=? now (:expires statement))))
               (conj {:problem :signed-module/expired})

               (not sig-ok?)
               (conj {:problem :signed-module/signature-invalid}))]
         (if (seq integrity-problems)
           {:ok? false :problems integrity-problems}
           {:ok? true
            :module module
            :signer signer
            :statement statement}))))))
