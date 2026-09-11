(ns kotoba.lang.captp-runtime-natural-ceiling-test
  "Host-parity contract for `parse-natural`'s digit-string bound.

  `clojure.core/parse-long` silently returns nil for a digit string
  representing a value above `Number.MAX_SAFE_INTEGER` (2^53 - 1) on
  ClojureScript, while the identical digit string keeps parsing on the
  JVM out to a 64-bit long (measured 2026-09-08 under nbb 1.4.208:
  `(parse-long \"9007199254740992\")` => nil; under the JVM => the exact
  long). Before the accompanying fix, `parse-natural` still wrapped that
  nil in a truthy `[nil i]` pair, so the caller's
  `(or (parse-natural ...) (throw ...))` guard never fired -- the decoded
  value silently became `nil` in place of the true integer, and the only
  visible symptom was `syrup-decode`'s unrelated-looking canonicity
  self-check throwing `:captp/syrup-value-unsupported` for `nil`, not a
  message naming the length parse at all.

  This test pins the boundary itself, at 2^53 - 1 and 2^53 exactly,
  against literal canonical wire bytes (host-independent ASCII digits)
  so both hosts are asserted to admit or refuse identically -- it does
  not go through `syrup-encode`, whose own ceiling (if any) is a
  separate question this test does not touch."
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.captp-runtime :as captp]))

(defn- bytes-of
  [ints]
  #?(:clj (byte-array ints)
     :cljs (js/Uint8Array. (clj->js ints))))

;; Canonical Syrup encoding of 9007199254740991 (2^53 - 1): the last
;; integer both a JVM long and a ClojureScript float64 Number can
;; represent losslessly. Digit ASCII followed by the "+" (positive)
;; terminator.
(def at-ceiling-bytes
  (bytes-of [57 48 48 55 49 57 57 50 53 52 55 52 48 57 57 49 43]))

;; The same encoding for 9007199254740992 (2^53) -- one past the ceiling.
(def past-ceiling-bytes
  (bytes-of [57 48 48 55 49 57 57 50 53 52 55 52 48 57 57 50 43]))

(deftest a-natural-at-the-portable-ceiling-decodes-exactly
  (is (= 9007199254740991 (captp/syrup-decode at-ceiling-bytes))))

(deftest a-natural-one-past-the-portable-ceiling-is-refused-not-corrupted
  (let [caught (try
                 (captp/syrup-decode past-ceiling-bytes)
                 :did-not-throw
                 (catch #?(:clj Throwable :cljs :default) e e))]
    (is (not= :did-not-throw caught)
        "decoding one past the ceiling must refuse, not silently succeed")
    (is (= :captp/syrup-number (:problem (ex-data caught)))
        "the refusal must name the length parse itself, not fail later as an unrelated corrupted-value error")))
