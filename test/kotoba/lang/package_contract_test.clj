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

;; lang/package-conformance/manifest.edn, lang/package.edn, and
;; lang/profile.edn are stored as Datomic/Datascript tx-data (see
;; schema.edn / scripts/edn-datomize.bb `wrap-map-preserve-ns!`).
;; `unblob` reverses the pr-str blob-ification of non-scalar values.
(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

;; manifest.edn: :kotoba.lang.package.conformance/version was already
;; namespaced and kept as-is; the plain :cases key was prefixed to
;; :kotoba.lang.package.conformance/cases and blob-stringified.
(defn- reconstitute-package-conformance-manifest [tx-data]
  (let [e (dissoc (first tx-data) :db/id)]
    {:kotoba.lang.package.conformance/version
     (:kotoba.lang.package.conformance/version e)
     :cases (unblob (:kotoba.lang.package.conformance/cases e))}))

;; profile.edn: every top-level key was already namespaced
;; (:kotoba.lang/*), so no key was renamed -- just unblob every value.
(defn- reconstitute-entity [tx-data]
  (into {} (map (fn [[k v]] [k (unblob v)]))
        (dissoc (first tx-data) :db/id)))

;; package.edn: only :kotoba.lang.package/version was already namespaced
;; (kept as-is); every other originally-plain key (:status :summary
;; :identity :manifest :lock-entry :trust-rules :maturity) was prefixed to
;; :kotoba.lang.package/* by the same ns, so it must be stripped back to
;; plain explicitly (a blind namespace-strip would also strip :version).
(defn- reconstitute-package-edn [tx-data]
  (let [e (dissoc (first tx-data) :db/id)]
    (into {:kotoba.lang.package/version (:kotoba.lang.package/version e)}
          (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
          (dissoc e :kotoba.lang.package/version))))

(deftest package-conformance-fixtures-match-contract
  (let [manifest (reconstitute-package-conformance-manifest (read-edn manifest-path))]
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
  (let [profile (reconstitute-entity (read-edn "lang/profile.edn"))
        package (reconstitute-package-edn (read-edn "lang/package.edn"))]
    (is (= 3 (:kotoba.lang/profile-version profile)))
    (is (= :kotoba (:kotoba.lang/default-reader-target profile)))
    (is (= :m2 (get-in profile [:kotoba.lang/type-system :maturity])))
    (is (= :pending (get-in profile [:kotoba.lang/type-system :compiler-admission])))
    (is (= 1 (:kotoba.lang.package/version package)))
    (is (contains? (set (get-in package [:manifest :package-kinds :allow-kinds]))
                   :schema-contract))))
