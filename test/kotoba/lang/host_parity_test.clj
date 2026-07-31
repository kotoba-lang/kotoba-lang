(ns kotoba.lang.host-parity-test
  "ADR-2607180900 L5: host parity matrix + cross-host conformance."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.host-parity :as hp]))

(deftest browser-linkability-meets-threshold
  (let [s (hp/score)]
    (is (pos? (:total s)))
    (is (true? (:ok? s)) (str s))))

(deftest availability-maps-no-to-capability-absent
  (is (= :available (hp/availability :sha256-hex :jvm)))
  (is (= :available (hp/availability :sha256-hex :browser)))
  (is (= :capability-absent (hp/availability :llm-infer :browser)))
  (is (= :available (hp/availability :llm-infer :jvm)))
  (is (= :available (hp/availability :llm-infer :node))
      "inject counts as available for linkability")
  (is (= :available (hp/availability :kagi-sign :node))
      "Node kagi-sign inject is real actor-host surface")
  (is (= :capability-absent (hp/availability :scram-sha256 :node))
      "Node actor-host has no scram-sha256 — honesty vs overclaimed inject")
  (is (= :capability-absent (hp/availability :transport-connect :node))
      "Node actor-host has no transport inject")
  (is (= :capability-absent (hp/availability :pg-open :node))
      "Node actor-host has no pg wire inject")
  (is (= :available (hp/availability :scram-sha256 :jvm))
      "JVM tender has purpose-bound scram-sha256")
  (is (= :unknown-import (hp/availability :not-a-real-import :jvm)))
  (is (= :unknown-host (hp/availability :sha256-hex :fleet))))

(deftest guard-host-import-denies-absent
  (let [denied (hp/guard-host-import :llm-infer :browser)
        ok (hp/guard-host-import :sha256-hex :browser)]
    (is (false? (:kotoba.host/ok? denied)))
    (is (= :host-absent (:kotoba.host/denied denied)))
    (is (true? (:kotoba.host/ok? ok)))))

(deftest conformance-suite-passes
  (let [r (hp/run-conformance)]
    (is (pos? (:total r)))
    (is (>= (:total r) 30)
        "T8.4 expanded critical-import fixtures (multi-host cases expand)")
    (is (true? (:ok? r))
        (str "failed cases: " (pr-str (:failed r))))
    (is (= (:total r) (:passed r)))))

(deftest t84-critical-import-fixtures-present
  "T8.4: crypto/http/kagi/transport/llm honest-gap fixtures must remain."
  (let [ids (set (map :id (hp/conformance-cases)))]
    (doseq [id [:sign-all-available :verify-all-available
                :kagi-sign-browser-absent :http-get-jvm-component-link-absent
                :transport-connect-browser-absent :llm-infer-browser-absent
                :scram-sha256-node-absent :transport-connect-node-absent
                :pg-open-node-absent]]
      (is (contains? ids id) (str id)))))

(deftest report-is-l5
  (let [r (hp/report)]
    (is (= :l5 (:level r)))
    (is (= :meets-threshold (:status r)))
    (is (true? (get-in r [:conformance :ok?])))))
