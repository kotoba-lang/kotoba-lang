(ns kotoba.lang.package-registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.package-registry :as registry]))

(def base-record
  {:registry/name "safe"
   :registry/version "1"
   :registry/repo-rid "bafyreiamqmkpfhh5noifwsn2jgnavzwpgmefv7orc6frbobfmhh2cear6y"
   :registry/commit "0123456789abcdef0123456789abcdef01234567"
   :registry/tree-cid "bafyreigyygk6epd5ge2akwuafpdjmztjc27v5pr7wpqnq37re7xhjwxsl4"
   :registry/manifest-cid "bafyreia4kejp3eqgyv3fbb6yrxkzemeqehopntqntvgkcq7maqltxrh57q"
   :registry/signers ["did:key:test"]
   :registry/capabilities [:graph-read :host/http]})

(deftest registry-coordinate-key-cannot-collide-on-at-signs
  (let [record (assoc base-record :registry/name "a@b" :registry/version "c")]
    (is (= {:ok? false
            :problems [{:problem :registry/not-found :name "a" :version "b@c"}]}
           (registry/resolve-record [record] "a" "b@c")))
    (is (:ok? (registry/resolve-record [record] "a@b" "c")))))

(deftest lock-capabilities-are-explicit-declared-subsets
  (testing "dependencies receive no capability by default"
    (is (= []
           (get-in (registry/lock-from-requests
                    [base-record] [{:name "safe" :version "1"}])
                   [:deps 0 :dep/capabilities]))))
  (testing "an explicitly requested subset is retained"
    (is (= [:graph-read]
           (get-in (registry/lock-from-requests
                    [base-record]
                    [{:name "safe" :version "1" :capabilities [:graph-read]}])
                   [:deps 0 :dep/capabilities]))))
  (testing "a request cannot introduce undeclared authority"
    (let [result (registry/lock-from-requests
                  [base-record]
                  [{:name "safe" :version "1" :capabilities [:host/fs-write]}])]
      (is (false? (:ok? result)))
      (is (= :registry/capability-grant-not-subset
             (-> result :problems first :problem))))))
