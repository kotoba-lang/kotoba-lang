(ns kotoba.lang.cli-adapter-matrix-portable-test
  "The genuinely portable slice of kotoba.lang.cli-adapter-matrix-test:
  `validate` takes contract/matrix data as explicit arguments (2-arity) and
  only the 0-arity form reads `lang/cli.edn` / `lang/cli-adapter-matrix.edn`
  off disk via `load-edn`, whose `:cljs` branch is a deliberate throw. The
  0-arity, real-file form (`matrix-matches-public-contract`) stays in
  kotoba.lang.cli-adapter-matrix-test (.clj); this file exercises the same
  validation logic against a small synthetic contract/matrix pair."
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer [deftest is testing] :include-macros true])
            [kotoba.lang.cli-adapter-matrix :as m]))

(def ^:private contract
  {:kotoba.cli.contract/commands [{:id :check} {:id :run}]})

(def ^:private valid-matrix
  {:kotoba.cli.adapter-matrix/version 1
   :hosts #{:compiler-cli :node-cli}
   :commands
   {:check {:adapters [{:host :compiler-cli :status :implemented}]}
    :run {:adapters [{:host :node-cli :status :partial}]}}})

(deftest a-matching-matrix-is-admitted
  (let [r (m/validate contract valid-matrix)]
    (is (true? (:ok? r)) (pr-str (:problems r)))
    (is (= 2 (:command-count r)))
    (is (true? (:implemented-check? r))
        "check has a :compiler-cli :implemented adapter")))

(deftest a-mismatched-command-set-is-refused
  (let [r (m/validate contract (update valid-matrix :commands dissoc :run))]
    (is (false? (:ok? r)))
    (is (some #(= :command-id-mismatch (:type %)) (:problems r)))))

(deftest a-command-with-no-adapters-is-refused
  (let [r (m/validate contract (assoc-in valid-matrix [:commands :run :adapters] []))]
    (is (false? (:ok? r)))
    (is (some #(and (= :no-adapters (:type %)) (= :run (:id %))) (:problems r)))))

(deftest an-adapter-on-an-unlisted-host-is-refused
  (let [r (m/validate contract (assoc-in valid-matrix [:commands :run :adapters]
                                        [{:host :not-a-host :status :implemented}]))]
    (is (false? (:ok? r)))
    (is (some #(and (= :unknown-host (:type %)) (= :not-a-host (:host %))) (:problems r)))))

(deftest an-adapter-with-an-unrecognised-status-is-refused
  (let [r (m/validate contract (assoc-in valid-matrix [:commands :run :adapters]
                                        [{:host :node-cli :status :maybe}]))]
    (is (false? (:ok? r)))
    (is (some #(and (= :bad-status (:type %)) (= :maybe (:status %))) (:problems r)))))

(deftest a-stale-matrix-version-is-refused
  (let [r (m/validate contract (assoc valid-matrix :kotoba.cli.adapter-matrix/version 0))]
    (is (false? (:ok? r)))
    (is (some #(= :matrix-version (:type %)) (:problems r)))))

(deftest implemented-check-requires-compiler-cli-implemented-on-check
  (is (false? (:implemented-check?
               (m/validate contract
                           (assoc-in valid-matrix [:commands :check :adapters]
                                     [{:host :compiler-cli :status :partial}]))))
      "a :partial check adapter must not read as :implemented-check?"))
