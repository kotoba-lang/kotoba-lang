(ns captp-cljs-smoke
  (:require [kotoba.lang.captp-runtime :as captp]))

(def expected
  "3c3130276f703a64656c697665723c313127646573633a6578706f7274352b3e5b342770696e675d66663e")

(def frame
  (captp/syrup-record
   'op:deliver
   [(captp/descriptor 'desc:export 5) ['ping] false false]))

(def encoded (captp/syrup-encode frame))
(def actual (.toString (js/Buffer.from encoded) "hex"))

(when-not (= expected actual)
  (throw (ex-info "CapTP CLJS canonical bytes drifted"
                  {:expected expected :actual actual})))

(when-not (= frame (captp/syrup-decode encoded))
  (throw (ex-info "CapTP CLJS Syrup did not round-trip" {})))

(println "CapTP CLJS smoke: canonical encode/decode passed")
