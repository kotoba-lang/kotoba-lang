(ns kotoba.lang.package-contract-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kotoba.lang.package-contract :as contract]))

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

(deftest profile-and-package-contract-are-machine-readable
  (let [profile (read-edn "lang/profile.edn")
        package (read-edn "lang/package.edn")]
    (is (= 2 (:kotoba.lang/profile-version profile)))
    (is (= :kotoba (:kotoba.lang/default-reader-target profile)))
    (is (= 1 (:kotoba.lang.package/version package)))
    (is (contains? (set (get-in package [:manifest :package-kinds :allow-kinds]))
                   :schema-contract))))
