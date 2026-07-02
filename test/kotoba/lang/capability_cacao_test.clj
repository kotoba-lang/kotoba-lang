(ns kotoba.lang.capability-cacao-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.capability-cacao :as cacao]
            [kotoba.lang.capability-values :as caps]))

(def root-iss "did:key:z6Mkroota")

(defn chain-result
  [resources & [{:keys [expires valid? problems]
                 :or {expires "2027-01-01T00:00:00Z" valid? true problems []}}]]
  {:chain/valid? valid?
   :chain/problems problems
   :chain/root-iss root-iss
   :chain/holder "did:key:z6Mkholder"
   :chain/resources (set resources)
   :chain/expires expires
   :chain/depth 2})

(deftest parse-cap-uri-slash-keyword-convention
  (testing "bare kind"
    (is (= {:kind :graph-read :resource "bafyg1"}
           (cacao/parse-cap-uri "kotoba://cap/graph-read/bafyg1"))))
  (testing "namespaced kind keeps its namespace as a path segment"
    (is (= {:kind :host/clipboard-read :resource "clipboard:system"}
           (cacao/parse-cap-uri "kotoba://cap/host/clipboard-read/clipboard:system"))))
  (testing "trailing * maps to the :any wildcard scope"
    (is (= {:kind :host/ledger-append :resource :any}
           (cacao/parse-cap-uri "kotoba://cap/host/ledger-append/*"))))
  (testing "resource may itself contain slashes"
    (is (= {:kind :host/fs-read :resource "tmp/notes/a.txt"}
           (cacao/parse-cap-uri "kotoba://cap/host/fs-read/tmp/notes/a.txt"))))
  (testing "unknown kinds and non-cap URIs are skip-noted, never guessed"
    (is (= {:skip :unknown-kind}
           (cacao/parse-cap-uri "kotoba://cap/teleport/pad:1")))
    (is (= {:skip :not-a-cap-uri}
           (cacao/parse-cap-uri "kotoba://graph/did:key:z6Mkroota")))
    (is (= {:skip :not-a-cap-uri} (cacao/parse-cap-uri "https://example.com")))
    (is (= {:skip :not-a-string} (cacao/parse-cap-uri 42)))
    (is (= {:skip :empty-resource} (cacao/parse-cap-uri "kotoba://cap/graph-read/")))))

(deftest grants-from-verified-chain
  (let [{:keys [grants skipped] :as result}
        (cacao/grants-from-chain
         (chain-result ["kotoba://cap/host/ledger-append/ledger:main"
                        "kotoba://cap/graph-read/*"
                        "kotoba://cap/teleport/pad:1"
                        "kotoba://graph/did:key:z6Mkroota"]))]
    (is (not (contains? result :problems)))
    (testing "one grant per registered cap URI, deterministic sorted order"
      (is (= [{:grant/kind :graph-read
               :grant/resources #{:any}
               :grant/expires "2027-01-01"
               :grant/id "cacao:did:key:z6Mkroota:0"}
              {:grant/kind :host/ledger-append
               :grant/resources #{"ledger:main"}
               :grant/expires "2027-01-01"
               :grant/id "cacao:did:key:z6Mkroota:1"}]
             grants)))
    (testing "unknown kinds are skipped with a note, never granted"
      (is (= [{:resource "kotoba://cap/teleport/pad:1" :note :unknown-kind}
              {:resource "kotoba://graph/did:key:z6Mkroota" :note :not-a-cap-uri}]
             skipped)))
    (testing "grants feed intersect-grants directly"
      (let [concrete (caps/intersect-grants
                      {:requested (caps/make-cap :host/ledger-append :any)
                       :cacao-grants grants
                       :local-policy {:policy/allow {:host/ledger-append #{:any}}}
                       :now "2026-07-02"})]
        (is (not (caps/denied? concrete)))
        (is (= "ledger:main" (:cap/resource concrete)))
        (is (= "2027-01-01" (:cap/expires concrete)))
        (is (= ["cacao:did:key:z6Mkroota:1"] (:cap/provenance concrete)))))))

(deftest unverified-chain-never-yields-grants
  (testing ":chain/valid? false is rejected with the chain problems echoed"
    (let [result (cacao/grants-from-chain
                  (chain-result ["kotoba://cap/host/ledger-append/ledger:main"]
                                {:valid? false
                                 :problems [{:problem :chain/invalid-signature
                                             :index 1}]}))]
      (is (= [] (:grants result)))
      (is (= [{:problem :chain/not-verified}
              {:problem :chain/invalid-signature :index 1}]
             (:problems result)))))
  (testing "a missing or non-true :chain/valid? also fails closed"
    (doseq [bad [nil 42 "chain" [] {:chain/resources #{"x"}}
                 (assoc (chain-result []) :chain/valid? :yes)]]
      (let [result (cacao/grants-from-chain bad)]
        (is (= [] (:grants result)) (pr-str bad))
        (is (seq (:problems result)) (pr-str bad))))))

(deftest chain-expires-maps-to-grant-expires
  (testing "instant is truncated to its date part"
    (is (= "2026-07-10"
           (-> (cacao/grants-from-chain
                (chain-result ["kotoba://cap/graph-read/g1"]
                              {:expires "2026-07-10T12:34:56Z"}))
               :grants first :grant/expires))))
  (testing "nil expiry stays nil (chain freshness already checked by verify-chain)"
    (is (nil? (-> (cacao/grants-from-chain
                   (chain-result ["kotoba://cap/graph-read/g1"] {:expires nil}))
                  :grants first :grant/expires))))
  (testing "an unintelligible expiry fails closed instead of widening to never-expires"
    (let [result (cacao/grants-from-chain
                  (chain-result ["kotoba://cap/graph-read/g1"]
                                {:expires "soon"}))]
      (is (= [] (:grants result)))
      (is (= :chain/expires-invalid (-> result :problems first :problem))))))

(deftest missing-root-iss-fails-closed
  (let [result (cacao/grants-from-chain
                (assoc (chain-result ["kotoba://cap/graph-read/g1"])
                       :chain/root-iss nil))]
    (is (= [] (:grants result)))
    (is (= :chain/root-iss-missing (-> result :problems first :problem)))))

(deftest all-resources-unknown-yields-empty-grants-not-problems
  (let [result (cacao/grants-from-chain
                (chain-result ["kotoba://cap/teleport/pad:1"]))]
    (is (= [] (:grants result)))
    (is (not (contains? result :problems)))
    (is (= [:unknown-kind] (mapv :note (:skipped result))))))
