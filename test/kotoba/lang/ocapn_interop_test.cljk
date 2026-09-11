(ns kotoba.lang.ocapn-interop-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.lang.captp-runtime :as captp]))

(defn- hex [bytes]
  (apply str (map #(format "%02x" (bit-and 0xff %)) bytes)))

;; Independently emitted by @endo/ocapn 1.1.1 encodeSyrup on Node 26.3.0.
;; Source snapshot: endojs/endo cafd9fc0c4b49becf1311d18e157ce7f19a6bb58.
(def endo-shared-vectors
  [[0 "302b"]
   [72 "37322b"]
   [-5 "352d"]
   [true "74"]
   [false "66"]
   ["björn" "3622626ac3b6726e"]
   ['update "3627757064617465"]
   [[1 "two" false] "5b312b332274776f665d"]
   [{"name" "Alice" "age" 30}
    "7b332261676533302b34226e616d653522416c6963657d"]
   [{"b" 20 "a" 10} "7b31226131302b31226232302b7d"]])

(deftest frozen-endo-syrup-vectors-match-kotoba-byte-for-byte
  (doseq [[value expected] endo-shared-vectors]
    (is (= expected (hex (captp/syrup-encode value))) (pr-str value))))
