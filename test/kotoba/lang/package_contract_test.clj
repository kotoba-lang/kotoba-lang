(ns kotoba.lang.package-contract-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.code-identity :as identity]
            [kotoba.lang.package-contract :as contract]
            [multiformats.core :as mf]))

(def manifest-path "lang/package-conformance/manifest.edn")

(defn read-edn
  [path]
  (edn/read-string (slurp (io/file path))))

(deftest package-conformance-fixtures-match-contract
  (let [manifest (read-edn manifest-path)]
    (is (= 1 (:kotoba.lang.package.conformance/version manifest)))
    (doseq [tc (:cases manifest)
            :let [data (read-edn (str "lang/package-conformance/" (:file tc)))
                  result (contract/validate-case tc data)]]
      (case (:kind tc)
        :accept
        (is (:valid? result) (:id tc))

        :expect-error
        (do
          (is (false? (:valid? result)) (:id tc))
          (is (str/includes? (:message result) (:error-contains tc)) (:id tc)))))))

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

(deftest package-and-lock-versions-use-strict-semver
  (doseq [valid ["0.1.0" "1.2.3" "2.0.0-rc.1" "2.0.0+build.9"]]
    (is (contract/semver? valid)))
  (doseq [invalid [nil "" "v1.2.3" "1" "1.2" "01.2.3" "latest"]]
    (is (false? (contract/semver? invalid)))))

(def pure-definition
  {:definition/profile-version 1
   :definition/kir {:op :fn
                    :params [:x]
                    :body [:+ :x 1]}
   :definition/interface {:params [:i64] :result :i64 :effects #{}}
   :definition/dependencies []})

(defn real-cid [text]
  (mf/cidv1-dag-cbor (.getBytes text "UTF-8")))

(defn definition-lock [definition-cids]
  {:kotoba.lock/version 1
   :deps [{:dep/name "kotoba-lang/math"
           :dep/version "0.1.0"
           :dep/repo-rid (real-cid "repo")
           :dep/commit "0123456789abcdef0123456789abcdef01234567"
           :dep/tree-cid (real-cid "tree")
           :dep/manifest-cid (real-cid "manifest")
           :dep/signers ["did:key:z6Mkmath"]
           :dep/capabilities []
           :dep/definition-cids definition-cids}]})

(deftest definition-cid-is-canonical-and-name-independent
  (let [cid (identity/definition-cid pure-definition)
        reordered (assoc pure-definition
                         :definition/kir {:body [:+ :x 1]
                                          :params [:x]
                                          :op :fn})]
    (is (= cid (identity/definition-cid reordered))
        "map/source key order cannot change semantic identity")
    (is (= cid (identity/definition-cid (assoc pure-definition :definition/name 'math/inc)))
        "author-facing aliases cannot change semantic identity")
    (is (not= cid (identity/definition-cid
                   (assoc-in pure-definition [:definition/kir :body] [:+ :x 2])))
        "typed KIR changes must change identity")
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"dependencies vector required"
                          (identity/definition-cid
                           (assoc pure-definition :definition/dependencies #{}))))))

(deftest definition-identity-must-match-and-be-listed-in-lock
  (let [cid (identity/definition-cid pure-definition)
        lock (definition-lock [cid])
        resolved [{:dep/name "kotoba-lang/math"
                   :definition pure-definition
                   :definition-cid cid}]]
    (is (= {:ok? true} (identity/verify-locked-definitions lock resolved)))
    (is (= :definition/hash-mismatch
           (:reason (identity/verify-locked-definitions
                     lock
                     [(assoc (first resolved) :definition-cid (real-cid "attacker"))]))))
    (is (= :definition/not-locked
           (:reason (identity/verify-locked-definitions
                     (definition-lock []) resolved))))
    (is (= :definition/unknown-dependency
           (:reason (identity/verify-locked-definitions
                     lock [(assoc (first resolved) :dep/name "attacker/math")]))))
    (is (:valid?
         (contract/validate-case
          {:type :lockfile :declared-capabilities [] :resolved-definitions resolved}
          lock))
        "the package contract invokes identity verification when safe-build supplies resolutions")
    (is (= "definition identity lock verification failed"
           (:message
            (contract/lockfile-error
             lock
             {:declared-capabilities []
              :resolved-definitions [(assoc (first resolved) :definition-cid (real-cid "attacker"))]}))))))

(deftest component-package-lock-binds-the-actual-component-bytes
  (let [bytes (.getBytes "wasm-component" "UTF-8")
        dep (assoc (first (:deps (definition-lock [])))
                   :dep/kind :component
                   :dep/component-cid (mf/cidv1-raw bytes))
        lock {:kotoba.lock/version 1 :deps [dep]}
        accepted (contract/lockfile-error
                  lock {:declared-capabilities []}
                  {:component-bytes-by-dep {(:dep/name dep) bytes}})
        rejected (contract/lockfile-error
                  lock {:declared-capabilities []}
                  {:component-bytes-by-dep {(:dep/name dep) (.getBytes "tampered" "UTF-8")}})]
    (is (nil? accepted))
    (is (= "component cid does not match component content" (:message rejected)))))
