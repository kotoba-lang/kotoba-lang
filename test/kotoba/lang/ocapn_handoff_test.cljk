(ns kotoba.lang.ocapn-handoff-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.captp-runtime :as captp]
            [kotoba.lang.ocapn-handoff :as handoff]
            [sha2.core :as sha2]))

;; Ported from ocapn_handoff_test.clj 2026-09-08. The signing/verification
;; helpers here were built on `java.security.MessageDigest` and
;; `java.io.ByteArrayOutputStream`, not on `kotoba.lang.ocapn-handoff` itself
;; (already dual-host: `byte-array-value?` branches on `[B` vs
;; `js/Uint8Array`). `sha2.core` (portable `.cljc` SHA-256, verified elsewhere
;; in this port against the standard SHA-256("abc") vector) replaces
;; `MessageDigest` identically on both hosts.
;;
;; `handoff/*` requires signatures to satisfy `signature-value?`, which in
;; turn requires `byte-array-value?` (a real `[B]` or `js/Uint8Array`, not a
;; plain vector) -- `sha2.core/sha256` returns a plain persistent vector of
;; unsigned ints, so `digest` below converts back to the host byte type after
;; hashing. `unsigned-byte-vec` exists because a JVM `byte[]` iterates as
;; SIGNED bytes (-128..127) while a `js/Uint8Array` iterates unsigned
;; (0..255); `(bit-and % 0xff)` normalises either to the unsigned domain
;; `sha2.core/sha256` documents itself as expecting.

(defn- fixed-bytes [n value]
  #?(:clj (byte-array (repeat n value))
     :cljs (js/Uint8Array. (clj->js (repeat n value)))))

(defn- opaque-target []
  #?(:clj (Object.) :cljs (js-obj)))

(defn- unsigned-byte-vec [xs]
  (vec (map #(bit-and % 0xff) xs)))

(defn- to-bytes [ints]
  #?(:clj (byte-array (map unchecked-byte ints))
     :cljs (js/Uint8Array. (clj->js ints))))

(defn- digest [key payload]
  (to-bytes (sha2/sha256 (into (unsigned-byte-vec key) (unsigned-byte-vec payload)))))

(defn- signer [key] #(digest key %))
(defn- verify [key payload signature]
  (= (vec (digest key payload)) (vec signature)))

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
        target (opaque-target)]
    (is (:handoff/deposited?
         (handoff/deposit-gift!
          store verify gifter-key
          {:session-id gifter-session :gifter-side gifter-side}
          give target)))
    (let [gift (handoff/withdraw-gift!
                store verify
                (fn [session side]
                  (is (= (vec gifter-session) (vec session)))
                  (is (= (vec gifter-side) (vec side)))
                  gifter-key)
                {:receiving-session receiver-session
                 :receiving-side receiver-side}
                receive)]
      (is (handoff/admitted-gift? gift))
      (is (identical? target (handoff/gift-target gift)))
      (is (= 0 (:handoff/count (handoff/gift-description gift)))))
    (is (= :handoff/count-replay
           (:problem
            (ex-data
             (try
               (handoff/withdraw-gift!
                store verify (fn [_ _] gifter-key)
                {:receiving-session receiver-session
                 :receiving-side receiver-side}
                receive)
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                      e e))))))))

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
                   {:session-id session :gifter-side side} give (opaque-target))
                  (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                         e e))))))
    (is (= :handoff/gifter-session-mismatch
           (:problem
            (ex-data
             (try (handoff/deposit-gift!
                   store verify gifter-key
                   {:session-id (fixed-bytes 32 0) :gifter-side side} give
                   (opaque-target))
                  (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                         e e))))))))

(deftest deferred-withdrawal-settles-after-deposit-and-keeps-tombstones
  (let [gifter-key (fixed-bytes 32 21)
        receiver-key (fixed-bytes 32 22)
        gifter-session (fixed-bytes 32 23)
        gifter-side (fixed-bytes 32 24)
        receiver-session (fixed-bytes 32 25)
        receiver-side (fixed-bytes 32 26)
        ;; Endo's randomGiftId is 16 bytes; OCapN does not require 32.
        gift-id (fixed-bytes 16 27)
        give (handoff/handoff-give!
              (signer gifter-key)
              {:receiver-key receiver-key
               :exporter-location
               (captp/syrup-record 'ocapn-peer ['tcp-testing-only
                                                 "exporter" {}])
               :session-id gifter-session
               :gifter-side gifter-side
               :gift-id gift-id})
        receive (handoff/handoff-receive!
                 (signer receiver-key)
                 {:receiving-session receiver-session
                  :receiving-side receiver-side
                  :handoff-count 3
                  :signed-give give})
        store (handoff/gift-store)
        target :authority/target
        delivered (atom nil)
        future (handoff/withdraw-gift-deferred!
                store verify (fn [_ _] gifter-key)
                {:receiving-session receiver-session
                 :receiving-side receiver-side}
                receive)]
    (is (handoff/future-gift? future))
    (is (= {:handoff/status :pending}
           (handoff/future-gift-settlement future)))
    (handoff/listen-future-gift! future #(reset! delivered %))
    (is (:handoff/delivered-to-waiter?
         (handoff/deposit-gift!
          store verify gifter-key
          {:session-id gifter-session :gifter-side gifter-side}
          give target)))
    (is (handoff/admitted-gift? @delivered))
    (is (= target (handoff/gift-target @delivered)))
    (is (= :fulfilled
           (:handoff/status (handoff/future-gift-settlement future))))
    (is (= :handoff/count-replay
           (:problem
            (ex-data
             (try
               (handoff/withdraw-gift-deferred!
                store verify (fn [_ _] gifter-key)
                {:receiving-session receiver-session
                 :receiving-side receiver-side}
                receive)
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                      e e))))))
    (is (= :handoff/gift-replay
           (:problem
            (ex-data
             (try
               (handoff/deposit-gift!
                store verify gifter-key
                {:session-id gifter-session :gifter-side gifter-side}
                give target)
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                      e e))))))))

(deftest pending-gift-allows-one-waiter-only
  (let [gifter-key (fixed-bytes 32 31)
        receiver-key (fixed-bytes 32 32)
        gifter-session (fixed-bytes 32 33)
        gifter-side (fixed-bytes 32 34)
        receiver-session (fixed-bytes 32 35)
        receiver-side (fixed-bytes 32 36)
        gift-id (fixed-bytes 16 37)
        give (handoff/handoff-give!
              (signer gifter-key)
              {:receiver-key receiver-key
               :exporter-location
               (captp/syrup-record 'ocapn-peer ['tcp-testing-only "x" {}])
               :session-id gifter-session :gifter-side gifter-side
               :gift-id gift-id})
        receive! (fn [count]
                   (handoff/handoff-receive!
                    (signer receiver-key)
                    {:receiving-session receiver-session
                     :receiving-side receiver-side
                     :handoff-count count
                     :signed-give give}))
        store (handoff/gift-store)]
    (is (handoff/future-gift?
         (handoff/withdraw-gift-deferred!
          store verify (fn [_ _] gifter-key)
          {:receiving-session receiver-session :receiving-side receiver-side}
          (receive! 0))))
    (is (= :handoff/gift-reserved
           (:problem
            (ex-data
             (try
               (handoff/withdraw-gift-deferred!
                store verify (fn [_ _] gifter-key)
                {:receiving-session receiver-session
                 :receiving-side receiver-side}
                (receive! 1))
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                      e e))))))))
