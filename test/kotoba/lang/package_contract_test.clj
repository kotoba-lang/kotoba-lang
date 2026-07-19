(ns kotoba.lang.package-contract-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.package-contract :as contract]
            [multiformats.core :as mf]))

(def manifest-path "lang/package-conformance/manifest.edn")

(defn read-edn
  [path]
  (edn/read-string (slurp (io/file path))))

(defn read-manifest
  [path]
  (let [value (read-edn path)]
    (if (vector? value)
      (let [entity (first value)]
        (cond-> entity
          (string? (:kotoba.lang.package.conformance/cases entity))
          (assoc :cases
                 (edn/read-string
                  (:kotoba.lang.package.conformance/cases entity)))))
      value)))

(deftest package-conformance-fixtures-match-contract
  (let [manifest (read-manifest manifest-path)]
    (is (= 1 (:kotoba.lang.package.conformance/version manifest)))
    (doseq [tc (or (:cases manifest)
                   (:kotoba.lang.package.conformance/cases manifest))
            :let [data (read-edn (str "lang/package-conformance/" (:file tc)))
                  result (contract/validate-case tc data)]]
      (case (:kind tc)
        :accept
        (is (:valid? result) (:id tc))

        :expect-error
        (do
          (is (false? (:valid? result)) (:id tc))
          (is (str/includes? (:message result) (:error-contains tc)) (:id tc)))))))

(deftest component-cid-required-for-component-kind
  (testing "L3: :component packages must pin a real component-cid"
    (let [lock (read-edn "lang/package-conformance/positive/component-lock.edn")
          missing (read-edn "lang/package-conformance/negative/component_missing_cid_lock.edn")
          bad (read-edn "lang/package-conformance/negative/component_bad_cid_lock.edn")
          tc {:declared-capabilities []}]
      (is (nil? (contract/lockfile-error lock tc)))
      (is (= "component cid required"
             (:message (contract/lockfile-error missing tc))))
      (is (= "component cid required"
             (:message (contract/lockfile-error bad tc))))))
  (testing "content integrity when component bytes are supplied"
    (let [lock (read-edn "lang/package-conformance/positive/component-lock.edn")
          tc {:declared-capabilities []}
          good (.getBytes "component-bytes-v1" "UTF-8")
          other (.getBytes "other-component" "UTF-8")]
      (is (nil? (contract/lockfile-error
                 lock tc {:component-bytes-by-dep {"kotoba-lang/guest-hello" good}})))
      (is (str/includes?
           (:message (contract/lockfile-error
                      lock tc {:component-bytes-by-dep {"kotoba-lang/guest-hello" other}}))
           "component cid does not match")))))

(deftest cid?-genuinely-decodes-and-structurally-validates-a-cidv1
  (testing "a real CIDv1 (multiformats.core/cidv1-dag-cbor, the same function this repo's
            conformance fixtures now use) passes"
    (is (contract/cid? (mf/cidv1-dag-cbor (.getBytes "hello" "UTF-8"))))
    (is (contract/cid? (mf/cidv1-raw (.getBytes "hello" "UTF-8")))))
  (testing "the OLD naive (str/starts-with? x \"bafy\") check would have accepted every one of
            these -- they must all fail the real structural check instead"
    (is (false? (contract/cid? "bafyrepojson111111111111111111111111111111111111111111111111"))
        "contains characters (0/1/8/9) outside the base32 'b'-multibase alphabet")
    (is (false? (contract/cid? "bafynotreallyacid")) "decodes but isn't valid CID framing")
    (is (false? (contract/cid? "b")) "empty payload after the multibase prefix")
    (is (false? (contract/cid? "notbase32atall"))))
  (testing "non-CID inputs are rejected outright, not just malformed CIDs"
    (is (false? (contract/cid? "")))
    (is (false? (contract/cid? nil)))
    (is (false? (contract/cid? 42))))
  (testing "a CID with a corrupted/truncated digest (multihash declares a length the
            actual decoded bytes don't have) is rejected, not silently accepted"
    (let [real (mf/cidv1-dag-cbor (.getBytes "hello" "UTF-8"))
          truncated (subs real 0 (dec (count real)))]
      (is (false? (contract/cid? truncated))))))

(deftest profile-and-package-contract-are-machine-readable
  (let [profile (read-edn "lang/profile.edn")
        package (read-edn "lang/package.edn")]
    (is (= 4 (:kotoba.lang/profile-version profile)))
    (is (= :kotoba (:kotoba.lang/default-reader-target profile)))
    (is (= 1 (:kotoba.lang.package/version package)))
    (is (contains? (set (get-in package [:manifest :package-kinds :allow-kinds]))
                   :schema-contract))))
