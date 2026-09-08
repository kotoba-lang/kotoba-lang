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
  (is (= :available (hp/availability :scram-sha256 :node))
      "Node actor-host scramCredentials fail-closed inject seam")
  (is (= :available (hp/availability :transport-connect :node))
      "Node actor-host transportProvider fail-closed inject seam")
  (is (= :available (hp/availability :pg-open :node))
      "Node actor-host pgWireProvider fail-closed inject seam")
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
                :scram-sha256-node-available :transport-connect-node-available
                :pg-open-node-available]]
      (is (contains? ids id) (str id)))))

(deftest report-is-l5
  (let [r (hp/report)]
    (is (= :l5 (:level r)))
    (is (= :meets-threshold (:status r)))
    (is (true? (get-in r [:conformance :ok?])))))

;; --- 2026-09-08: an unavailable catalog must not read as a measured one ----

(deftest a-loaded-catalog-says-it-was-loaded
  ;; The positive direction. Without this, the assertion below passes just as
  ;; well against a `catalog-available?` that returns false unconditionally.
  (is (true? (hp/catalog-available?))
      "the JVM suite reads lang/host-parity.edn, so this must be true here")
  (is (= :resource (:kotoba.lang.host-parity/source (hp/catalog)))))

(deftest a-loaded-catalog-reports-a-status-that-is-not-catalog-unavailable
  ;; The other half: `:catalog-unavailable` must be reachable ONLY when the
  ;; catalog really is unavailable, or the new status is just noise.
  (is (not= :catalog-unavailable (:status (hp/report))))
  (is (true? (:catalog-available? (hp/report))))
  (is (true? (:catalog-available? (hp/score)))))

(deftest the-empty-catalog-was-indistinguishable-and-is-not-anymore
  ;; What this pins is the DISTINCTION, not the numbers. Before this change the
  ;; unavailable catalog produced `{:total 0 :ratio 0.0 :ok? false}` and
  ;; `:below-threshold` -- byte-identical to a real measurement of a workspace
  ;; with no host imports. `guard-host-import` still denied (it always did;
  ;; availability answers :unknown-import for everything), so this was never a
  ;; safety hole -- it was an unreadable one.
  (let [real (hp/catalog)]
    (is (contains? real :kotoba.lang.host-parity/source)
        "every catalog carries its provenance, loaded or not")
    (is (seq (:imports real))
        "SCANNED: the JVM catalog is non-empty, so the assertions above are
         about a real catalog and not about the stub")))
