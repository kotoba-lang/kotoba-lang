(ns kotoba.lang.ocapn-handoff-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.captp-runtime :as captp]
            [kotoba.lang.ocapn-handoff :as handoff])
  (:import [java.io ByteArrayOutputStream]
           [java.security MessageDigest]))

(defn- fixed-bytes [n value] (byte-array (repeat n value)))

(defn- digest [key payload]
  (let [out (ByteArrayOutputStream.)]
    (.write out ^bytes key)
    (.write out ^bytes payload)
    (.digest (MessageDigest/getInstance "SHA-256") (.toByteArray out))))

(defn- signer [key] #(digest key %))
(defn- verify [key payload signature]
  (MessageDigest/isEqual (digest key payload) signature))

(deftest signed-third-party-gift-is-session-bound-and-single-use
  (let [gifter-key (fixed-bytes 32 1)
        receiver-key (fixed-bytes 32 2)
        gifter-session (fixed-bytes 32 3)
        gifter-side (fixed-bytes 32 4)
        receiver-session (fixed-bytes 32 5)
        receiver-side (fixed-bytes 32 6)
        gift-id (fixed-bytes 32 7)
        location (captp/syrup-record 'ocapn-peer ['tcp "exporter" false])
        give (handoff/handoff-give!
              (signer gifter-key)
              {:receiver-key receiver-key
               :exporter-location location
               :session-id gifter-session
               :gifter-side gifter-side
               :gift-id gift-id})
        receive (handoff/handoff-receive!
                 (signer receiver-key)
                 {:receiving-session receiver-session
                  :receiving-side receiver-side
                  :handoff-count 0
                  :signed-give give})
        store (handoff/gift-store)
        target (Object.)]
    (is (:handoff/deposited?
         (handoff/deposit-gift!
          store verify gifter-key
          {:session-id gifter-session :gifter-side gifter-side}
          give target)))
    (let [gift (handoff/withdraw-gift!
                store verify
                (fn [session side]
                  (is (= (seq gifter-session) (seq session)))
                  (is (= (seq gifter-side) (seq side)))
                  gifter-key)
                {:receiving-session receiver-session
                 :receiving-side receiver-side}
                receive)]
      (is (handoff/admitted-gift? gift))
      (is (identical? target (handoff/gift-target gift)))
      (is (= 0 (:handoff/count (handoff/gift-description gift)))))
    (is (= :handoff/gift-unavailable
           (:problem
            (ex-data
             (try
               (handoff/withdraw-gift!
                store verify (fn [_ _] gifter-key)
                {:receiving-session receiver-session
                 :receiving-side receiver-side}
                receive)
               (catch clojure.lang.ExceptionInfo e e))))))))

(deftest forged-and-cross-session-handoffs-fail-closed
  (let [gifter-key (fixed-bytes 32 11)
        receiver-key (fixed-bytes 32 12)
        session (fixed-bytes 32 13)
        side (fixed-bytes 32 14)
        gift-id (fixed-bytes 32 15)
        give (handoff/handoff-give!
              (signer gifter-key)
              {:receiver-key receiver-key
               :exporter-location
               (captp/syrup-record 'ocapn-peer ['tcp "exporter" false])
               :session-id session :gifter-side side :gift-id gift-id})
        store (handoff/gift-store)]
    (is (= :handoff/give-signature-invalid
           (:problem
            (ex-data
             (try (handoff/deposit-gift!
                   store verify (fixed-bytes 32 99)
                   {:session-id session :gifter-side side} give (Object.))
                  (catch clojure.lang.ExceptionInfo e e))))))
    (is (= :handoff/gifter-session-mismatch
           (:problem
            (ex-data
             (try (handoff/deposit-gift!
                   store verify gifter-key
                   {:session-id (fixed-bytes 32 0) :gifter-side side} give (Object.))
                  (catch clojure.lang.ExceptionInfo e e))))))))
