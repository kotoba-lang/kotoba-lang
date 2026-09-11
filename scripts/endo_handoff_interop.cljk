(ns endo-handoff-interop
  (:require [kotoba.lang.captp-runtime :as captp]
            [kotoba.lang.ocapn-handoff :as handoff]))

(defonce ^:private immutable-bytes->u8 (atom nil))

(defn- u8 [bytes]
  (if (instance? js/Uint8Array bytes)
    bytes
    (if-let [convert @immutable-bytes->u8]
      (convert bytes)
      (js/Uint8Array. bytes))))

(defn- immutable [bytes]
  (let [copy (js/Uint8Array. (.-length bytes))]
    (.set copy bytes)
    (.-buffer copy)))

(defn- same-bytes? [left right]
  (let [a (u8 left) b (u8 right)]
    (and (= (.-length a) (.-length b))
         (every? #(= (aget a %) (aget b %)) (range (.-length a))))))

(defn- public-key-component [public-key]
  ['public-key
   ['ecc
    ['curve 'Ed25519]
    ['flags 'eddsa]
    ['q (u8 (.-bytes public-key))]]])

(defn- signature-component [signature]
  ['sig-val
   ['eddsa
    ['r (u8 (.-r signature))]
    ['s (u8 (.-s signature))]]])

(defn- signature-object [component]
  (let [[_ [_ [_ r] [_ s]]] component]
    #js {:type "sig-val" :scheme "eddsa"
         :r (immutable r) :s (immutable s)}))

(defn- location-record [location]
  (captp/syrup-record
   'ocapn-peer
   [(symbol (.-transport location))
    (.-designator location)
    false]))

(defn- give-record [signed-give]
  (let [give (.-object signed-give)]
    (captp/syrup-record
     'desc:handoff-give
     [(public-key-component
       #js {:bytes (.. give -receiverKey -q)})
      (location-record (.-exporterLocation give))
      (u8 (.-exporterSessionId give))
      (u8 (.-gifterSideId give))
      (u8 (.-giftId give))])))

(defn- signed-give-record [signed-give]
  (captp/syrup-record
   'desc:sig-envelope
   [(give-record signed-give)
    (signature-component (.-signature signed-give))]))

(defn- assert! [condition message data]
  (when-not condition
    (throw (ex-info message data))))

(defn -main []
  (-> (js/import "@endo/init")
      (.then
       (fn [_]
         (js/Promise.all
          #js [(js/import "../node_modules/@endo/ocapn/src/cryptography.js")
               (js/import "../node_modules/@endo/ocapn/src/codecs/descriptors.js")
               (js/import "@endo/bytes/from-immutable.js")])))
      (.then
       (fn [modules]
         (reset! immutable-bytes->u8
                 (.-bytesFromImmutable (aget modules 2)))
         (let [crypto (aget modules 0)
               descriptors (aget modules 1)
               gifter-keypair
               (.makeOcapnKeyPairFromPrivateKey
                crypto (js/Uint8Array. (clj->js (repeat 32 7))))
               receiver-keypair
               (.makeOcapnKeyPairFromPrivateKey
                crypto (js/Uint8Array. (clj->js (repeat 32 9))))
               gifter-public (.-publicKey gifter-keypair)
               receiver-public (.-publicKey receiver-keypair)
               location #js {:type "ocapn-peer"
                             :transport "tcp-testing-only"
                             :designator "127.0.0.1:41001"
                             :hints false}
               session-id (.makeSessionId crypto
                                          (.-id gifter-public)
                                          (.-id receiver-public))
               gift-id (js/Uint8Array. (clj->js (range 16)))
               endo-give (.makeSignedHandoffGive
                          crypto receiver-public location session-id
                          (.-id gifter-public) (.-buffer gift-id)
                          gifter-keypair)
               give (signed-give-record endo-give)
               give-bytes (captp/syrup-encode (first (:syrup/fields give)))
               verify!
               (fn [public-key payload signature]
                 (try
                   (let [[_ [_ _ _ [_ q]]] public-key
                         key (.makeOcapnPublicKey crypto (.-buffer q))]
                     (.assertSignatureValid key (.-buffer payload)
                                            (signature-object signature))
                     true)
                   (catch :default _ false)))
               store (handoff/gift-store)
               receive-session (u8 session-id)
               receive-side (u8 (.-id receiver-public))
               receive
               (handoff/handoff-receive!
                (fn [payload]
                  (signature-component
                   (.sign receiver-keypair (.-buffer payload))))
                {:receiving-session receive-session
                 :receiving-side receive-side
                 :handoff-count 0
                 :signed-give give})
               [receive-record receive-signature] (:syrup/fields receive)
               endo-receive-object
               (.makeHandoffReceiveDescriptor
                descriptors endo-give (js/BigInt 0) session-id
                (.-id receiver-public))
               future
               (handoff/withdraw-gift-deferred!
                store verify!
                (fn [_ _] (public-key-component gifter-public))
                {:receiving-session receive-session
                 :receiving-side receive-side}
                receive)
               target (js-obj "authority" "interop")]
           (assert! (= 16 (.-length gift-id))
                    "Endo gift id is not the expected OCapN width" {})
           (assert! (same-bytes? give-bytes
                                (.serializeHandoffGive descriptors
                                                       (.-object endo-give)))
                    "Kotoba and Endo handoff-give Syrup differ" {})
           (assert! (same-bytes? (captp/syrup-encode receive-record)
                                (.serializeHandoffReceive descriptors
                                                          endo-receive-object))
                    "Kotoba and Endo handoff-receive Syrup differ" {})
           (.assertHandoffReceiveSignatureValid
            crypto endo-receive-object
            (signature-object receive-signature) receiver-public)
           (assert! (= :pending
                       (:handoff/status
                        (handoff/future-gift-settlement future)))
                    "future did not wait for deposit" {})
           (handoff/deposit-gift!
            store verify! (public-key-component gifter-public)
            {:session-id (u8 session-id)
             :gifter-side (u8 (.-id gifter-public))}
            give target)
           (let [gift (:handoff/gift
                       (handoff/future-gift-settlement future))]
             (assert! (and (handoff/admitted-gift? gift)
                           (identical? target (handoff/gift-target gift)))
                      "future deposit did not deliver opaque authority" {}))
           (println "Endo 1.1.1 <-> Kotoba handoff: bytes, signatures, and future deposit passed"))))
      (.catch (fn [error]
                (js/console.error error)
                (set! (.-exitCode js/process) 1)))))

(-main)
