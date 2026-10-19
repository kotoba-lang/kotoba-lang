(ns noise.core
  "Single entry point for the Noise Protocol Framework (rev 34) in portable
  `.cljc` — the mutually-authenticating handshake an identity-addressed overlay
  data plane needs, with the two primitives that must be constant-time injected
  rather than hand-rolled (see `noise.suite`).

      (require '[noise.core :as noise]
               '[noise.provider.jvm :as jvm])      ; or noise.provider.noble under cljs

      (def st (noise/suite (jvm/ports)))
      (def alice (noise/keypair st))
      (def bob   (noise/keypair st))

      ;; IK: Alice already knows Bob's static public key (from the netmap)
      (def i (noise/initiator {:suite st :s alice :rs (:pub bob)
                               :prologue (noise/prologue \"kekkai/1 netmap:42\")}))
      (def r (noise/responder {:suite st :s bob}))

      (let [[i msg1] (noise/write-message i [])            ; -> the wire
            [r _]    (noise/read-message r msg1)
            [r msg2] (noise/write-message r [])
            [i _]    (noise/read-message i msg2)]
        ;; both sides are now :done? with matching handshake hashes
        (def sa (noise/session i {:now 0 :peer-id \"bob\"}))
        (def sb (noise/session r {:now 0 :peer-id \"alice\"})))

      (let [[sa frame] (noise/encrypt sa (b/utf8-encode \"hello\"))]
        (second (noise/decrypt sb frame)))                 ;=> \"hello\" bytes"
  (:require [kotoba.bytes :as b]
            [noise.handshake-state :as handshake]
            [noise.patterns :as patterns]
            [noise.session :as session]
            [noise.suite :as suite]))

(def suite suite/suite)
(def protocol-name suite/protocol-name)
(def known-patterns (set (keys patterns/patterns)))

(defn keypair
  "A fresh static or ephemeral keypair from the suite's injected generator."
  [st]
  ((:dh-generate st)))

(defn prologue
  "Prologue bytes from a string. Both sides must supply an identical prologue;
   bind it to whatever context the session must not be transplantable out of —
   the tailnet id and netmap version, for instance, so a captured handshake
   cannot be replayed against a later netmap."
  [s]
  (b/utf8-encode s))

(defn initiator
  [{:keys [suite s rs prologue pattern e]
    :or {pattern :IK}}]
  (handshake/initialize {:suite suite :pattern pattern :initiator? true
                         :s s :rs rs :e e :prologue (or prologue [])}))

(defn responder
  [{:keys [suite s rs prologue pattern e]
    :or {pattern :IK}}]
  (handshake/initialize {:suite suite :pattern pattern :initiator? false
                         :s s :rs rs :e e :prologue (or prologue [])}))

(def write-message handshake/write-message)
(def read-message handshake/read-message)

(defn done? [hs] (boolean (:done? hs)))
(defn handshake-hash
  "The final transcript hash — identical on both sides, and only if both sides
   saw byte-identical transcripts. Safe to use as a channel binding."
  [hs]
  (:handshake-hash hs))
(defn remote-static
  "The peer's static public key. After an IK handshake completes this is
   *authenticated*: the peer proved possession of the matching private key."
  [hs]
  (:rs hs))

(def session session/session)
(def encrypt session/encrypt)
(def decrypt session/decrypt)
(def advice session/advice)
(def expired? session/expired?)
(def needs-rekey? session/needs-rekey?)
(def needs-keepalive? session/needs-keepalive?)
(def handshake-plan session/handshake-plan)
