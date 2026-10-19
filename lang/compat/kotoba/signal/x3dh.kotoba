;; kotoba.signal.x3dh — X3DH (Extended Triple Diffie-Hellman) key agreement,
;; per the Signal spec (Marlinspike & Perrin, "The X3DH Key Agreement Protocol",
;; 2016), for ClojureScript. CLJS sibling of kotoba.signal.x3dh (JVM) — same
;; 6-function shape, built on kotoba.signal.x25519 (DH) + kotoba.signal.hkdf
;; (KDF, Web Crypto, hence Promise-based here — unlike the JVM version's
;; synchronous javax.crypto.Mac) + @noble/curves ed25519 (identity-binding
;; signature, same import pattern kotoba.signal.group already uses).
;;
;; Bundle shape (published by the receiving party, e.g. Bob, so an initiator,
;; e.g. Alice, can X3DH against it without Bob being online):
;;   {:ik ^Uint8Array(32)        ; Bob's long-term identity key, X25519 pub
;;    :sign-pub ^Uint8Array(32) ; Bob's Ed25519 identity pubkey, signs :spk
;;    :spk ^Uint8Array(32)      ; Bob's signed pre-key, X25519 pub (medium-term, rotated)
;;    :spk-sig ^Uint8Array(64)  ; Ed25519 signature over :spk under :sign-pub
;;    :opk ^Uint8Array(32)|nil  ; Bob's one-time pre-key, X25519 pub (consumed once)
;;    :opk-id int|nil}
;;
;; SCOPE NOTE — no XEdDSA, and :sign-pub is RAW bytes not a did:key string:
;; same two deliberate simplifications as the JVM version's "no XEdDSA" choice
;; (see its ns docstring) PLUS kotoba.signal.group's already-established cljs
;; precedent of leaving identity-string encoding (did:key or otherwise) to the
;; caller rather than baking one in here — a consumer that wants did:key
;; framing (e.g. app-aozora, which already has kotobase.cid for that) layers
;; it on top of these raw Ed25519 bytes.
;;
;;   (require '[kotoba.signal.x3dh :as x3dh])
;;   (def bob (x3dh/generate-identity))
;;   (def bundle (first (x3dh/publish-bundle bob)))        ; what Bob publishes
;;   (x3dh/verify-bundle bundle)                           ;=> true (Alice checks this FIRST)
;;   (def alice (x3dh/generate-identity))
;;   (-> (x3dh/x3dh-initiate alice bundle)                 ;=> Promise<{:shared-secret :ek-pub :opk-id}>
;;       (.then (fn [init] ...)))
;;   ;; Alice sends {:ik (:pub (:ik alice)) :ek-pub (:ek-pub init) :opk-id (:opk-id init)}
;;   ;; to Bob (as the first Double Ratchet message's associated data), who calls:
;;   (x3dh/x3dh-respond bob (:pub (:ik alice)) (:ek-pub init) (:opk-id init))
;;   ;; => Promise<Uint8Array(32)>, same bytes as (:shared-secret init)
(ns kotoba.signal.x3dh
  (:require [kotoba.signal.x25519 :as dh]
            [kotoba.signal.hkdf :as hkdf]
            ["@noble/curves/ed25519.js" :refer [ed25519]]))

(defn- rand-bytes [n]
  (doto (js/Uint8Array. n) (js/crypto.getRandomValues)))

(defn- concat-bytes [arrs]
  (let [total (reduce + (map #(.-length ^js %) arrs))
        out (js/Uint8Array. total)]
    (reduce (fn [off ^js a] (.set out a off) (+ off (.-length a))) 0 arrs)
    out))

;; RFC-style discriminant prefix (32 0xFF bytes) prepended to the DH-output
;; concatenation before KDF, per the X3DH spec §2.2 — same spec-fidelity
;; rationale as the JVM version's F constant (see its docstring).
(def ^:private F (js/Uint8Array. (into-array (repeat 32 0xff))))
(def ^:private info (.encode (js/TextEncoder.) "kotoba-signal X3DH v1"))

(defn- kdf
  "HKDF(salt=0, ikm = F || DH1 || DH2 || DH3 || [DH4], info, 32) -> Promise<shared secret>."
  [dh-outputs]
  (let [ikm (concat-bytes (into [F] dh-outputs))]
    (hkdf/hkdf (js/Uint8Array. 32) ikm info 32)))

(defn generate-identity
  "One-time setup for a party. Returns the FULL (private) identity:
     {:ik {:priv :pub}                  ; long-term X25519 identity key
      :sign-seed Uint8Array(32)         ; Ed25519 seed, signs SPKs (identity binding)
      :sign-pub Uint8Array(32)
      :spk {:priv :pub}                 ; signed pre-key (rotate periodically in real use)
      :spk-sig Uint8Array(64)           ; Ed25519 sig over (:pub spk) under sign-seed
      :opks [{:priv :pub :id int} …]}   ; one-time pre-keys, consumed one at a time
   Never publish this map as-is — see `publish-bundle`."
  ([] (generate-identity 10))
  ([n-opks]
   (let [ik (dh/generate-keypair)
         sign-seed (rand-bytes 32)
         spk (dh/generate-keypair)
         spk-sig (.sign ed25519 (:pub spk) sign-seed)
         opks (mapv #(assoc (dh/generate-keypair) :id %) (range n-opks))]
     {:ik ik
      :sign-seed sign-seed
      :sign-pub (.getPublicKey ed25519 sign-seed)
      :spk spk
      :spk-sig spk-sig
      :opks opks})))

(defn publish-bundle
  "The PUBLIC prekey bundle a party publishes for others to X3DH against.
   Pops (consumes) ONE one-time prekey from `identity`'s pool if present — a
   real directory server does this per-fetch so an OPK is never handed out
   twice; here it's returned as [bundle remaining-identity] so callers can
   thread the updated (consumed) identity back into their own storage."
  [identity]
  (let [opk (first (:opks identity))
        bundle {:ik (:pub (:ik identity))
                :sign-pub (:sign-pub identity)
                :spk (:pub (:spk identity))
                :spk-sig (:spk-sig identity)
                :opk (:pub opk)
                :opk-id (:id opk)}]
    [bundle (update identity :opks (comp vec rest))]))

(defn verify-bundle
  "Verify the SPK signature in a published bundle under its own :sign-pub.
   Callers MUST call this (and refuse to proceed on false) before
   `x3dh-initiate` against an untrusted/fetched bundle — this is the identity
   binding that keeps a man-in-the-middle from swapping in their own :spk."
  [{:keys [sign-pub spk spk-sig]}]
  (boolean (.verify ed25519 spk-sig spk sign-pub)))

(defn x3dh-initiate
  "Initiator's (Alice's) side. `my-identity` = Alice's full identity (from
   `generate-identity`). `their-bundle` = the other party's (Bob's) published
   bundle — caller must have already run `verify-bundle` on it. Generates a
   fresh ephemeral key (EKa) and computes:
     DH1 = DH(IKa, SPKb)   DH2 = DH(EKa, IKb)
     DH3 = DH(EKa, SPKb)   DH4 = DH(EKa, OPKb)   [only if their-bundle has an :opk]
   Returns Promise<{:shared-secret Uint8Array(32) :ek-pub Uint8Array(32) :opk-id (or nil)}>.
   The caller MUST send :ek-pub and :opk-id (plus Alice's own IK pub) to Bob
   alongside the first ciphertext so `x3dh-respond` can reconstruct the
   identical secret."
  [my-identity their-bundle]
  (let [ek (dh/generate-keypair)
        dh1 (dh/dh (:priv (:ik my-identity)) (:spk their-bundle))
        dh2 (dh/dh (:priv ek) (:ik their-bundle))
        dh3 (dh/dh (:priv ek) (:spk their-bundle))
        dh4 (when (:opk their-bundle) (dh/dh (:priv ek) (:opk their-bundle)))]
    (-> (kdf (remove nil? [dh1 dh2 dh3 dh4]))
        (.then (fn [sk] {:shared-secret sk :ek-pub (:pub ek) :opk-id (:opk-id their-bundle)})))))

(defn x3dh-respond
  "Receiver's (Bob's) side — reconstructs the SAME shared secret `x3dh-initiate`
   produced, from the values Alice sent alongside her first message.
   `my-identity` = Bob's full identity. `their-ik-pub` = Alice's identity X25519
   pubkey. `ek-pub` = Alice's ephemeral pubkey. `opk-id` = which OPK (if any)
   Alice consumed (nil ⇒ she had none / didn't use one).
   Computes the mirror image:
     DH1 = DH(SPKb, IKa)   DH2 = DH(IKb, EKa)
     DH3 = DH(SPKb, EKa)   DH4 = DH(OPKb, EKa)   [only if opk-id matches a held OPK]
   which equals the initiator's DH1..DH4 by DH commutativity: DH(a,B) = DH(b,A).
   Returns Promise<Uint8Array(32)>."
  [my-identity their-ik-pub ek-pub opk-id]
  (let [spk-priv (:priv (:spk my-identity))
        ik-priv (:priv (:ik my-identity))
        opk (when opk-id (first (filter #(= opk-id (:id %)) (:opks my-identity))))
        dh1 (dh/dh spk-priv their-ik-pub)
        dh2 (dh/dh ik-priv ek-pub)
        dh3 (dh/dh spk-priv ek-pub)
        dh4 (when opk (dh/dh (:priv opk) ek-pub))]
    (kdf (remove nil? [dh1 dh2 dh3 dh4]))))
