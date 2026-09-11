(ns kotoba.lang.capability-catalog-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.capability-catalog :as catalog]))

(deftest an-entry-with-no-classification-is-refused-by-the-authority
  ;; Root ADR-2607280100 D5. The count assertions in the test below say the
  ;; catalog IS complete today; this says `validate!` is what would notice if
  ;; it stopped being -- a check that has only ever seen a passing input has
  ;; not been shown to discriminate.
  (let [authority (catalog/read-authority)
        entry (get-in authority [:capabilities :hash/sha256])]
    (is (keyword? (:kotoba.security/classification entry)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (catalog/validate!
                  (update-in authority [:capabilities :hash/sha256]
                             dissoc :kotoba.security/classification)))
        "a capability with no declaration must not leave the authority")
    (is (thrown? clojure.lang.ExceptionInfo
                 (catalog/validate!
                  (assoc-in authority
                            [:capabilities :hash/sha256
                             :kotoba.security/classification]
                            "public")))
        "a declaration that is a string reading like a keyword is not one")
    (is (map? (catalog/validate! authority))
        "and the unmodified authority still passes, so the two refusals above
         are the classification and not the fixture")))

(deftest semantic-capability-authority-is-closed
  ;; The count is a deliberate tripwire: adding a capability must be a reviewed
  ;; act, not something that slips in. It went stale when 68e5fb5 ("record W5
  ;; family-3 HTTP ingress dual-runtime") added :http/accept and :http/reply
  ;; without updating it, and CI was red on main from 2026-07-27 09:57 until
  ;; this commit. The wire-id assertion below is what makes the tripwire useful
  ;; rather than merely annoying -- a bump to the count alone cannot hide a
  ;; duplicated or skipped wire identity.
  (let [authority (catalog/validate! (catalog/read-authority))
        entries (:capabilities authority)
        wire-ids (sort (map :compiler-wire-id (vals entries)))]
    (is (= 40 (count entries)))
    ;; Root ADR-2607280100 D5. Counts, not a boolean: `false` alone cannot
    ;; tell one capability added without a classification from a catalog that
    ;; failed to load. WHICH labels are legal is not asserted here -- that is
    ;; `kotoba.security.information-flow/ranks`, one lattice, and amu's
    ;; `kotoba.compiler.effect-classification` is what ranks a declaration.
    (is (= 0 (count (remove :kotoba.security/classification (vals entries))))
        "every capability declares :kotoba.security/classification")
    (is (= (count entries)
           (count (filter (comp keyword? :kotoba.security/classification)
                          (vals entries))))
        "a declaration is a keyword, not a string that reads like one")
    (is (= (range 1 (inc (count entries))) wire-ids)
        "wire ids stay contiguous from 1 with no duplicates or gaps")
    (is (= [4 11 12]
           (mapv #(get-in entries [% :compiler-wire-id])
                 [:http/post :llm/generate :storage/transact])))
    (is (= ['http/post 'llm/generate 'storage/transact]
           (mapv #(get-in entries [% :source-operation])
                 [:http/post :llm/generate :storage/transact])))
    ;; T8.3 ops kits — wire ids match provider kit :capability :id (ADR-t83-ops-catalog-19-23)
    (is (= [19 20 21 22 23 24]
           (mapv #(get-in entries [% :compiler-wire-id])
                 [:fs/transact :process/spawn :secret/get :git/run :entropy/draw
                  :dataspace/transact])))
    (is (= ['fs/transact 'process/spawn 'secret/get 'git/run 'entropy/draw
            'dataspace/transact]
           (mapv #(get-in entries [% :source-operation])
                 [:fs/transact :process/spawn :secret/get :git/run :entropy/draw
                  :dataspace/transact])))
    (is (= [25 26]
           (mapv #(get-in entries [% :compiler-wire-id])
                 [:stream/accept :stream/send])))
    (is (= [27 28 29 30]
           (mapv #(get-in entries [% :compiler-wire-id])
                 [:net/datagram :link/frame :can/frame :code/eval])))
    (is (= 'code/eval (get-in entries [:code/eval :source-operation])))
    (is (= 'eval (get-in entries [:code/eval :source-alias])))
    ;; ADR-2609031100. Promoted from kotoba-sema's vendored copy, where a
    ;; language-surface change had been made in a consumer repository; the ids
    ;; are named here so a resync cannot quietly drop or renumber them.
    (is (= [31 32]
           (mapv #(get-in entries [% :compiler-wire-id])
                 [:screen/observe :screen/act])))
    (is (= ['screen/observe 'screen/act]
           (mapv #(get-in entries [% :source-operation])
                 [:screen/observe :screen/act])))
    ;; kbb ADR-2607181900 slice 3 (merge 8d5a7b1d): compiler wire ids 33/34
    ;; for env/read and fs/browse. Named so a resync cannot drop them.
    (is (= [33 34]
           (mapv #(get-in entries [% :compiler-wire-id])
                 [:env/read :fs/browse])))
    (is (= ['env/read 'fs/browse]
           (mapv #(get-in entries [% :source-operation])
                 [:env/read :fs/browse])))
    ;; ADR-2609051100 slice 4: fs/app-data (runtime id 202) gets compiler
    ;; wire id 35 so native guests can lower fs-read/fs-write.
    (is (= 35 (get-in entries [:fs/app-data :compiler-wire-id])))
    (is (= 'fs/app-data (get-in entries [:fs/app-data :source-operation])))
    ;; find-lib slice: fs/browse-dir (runtime id 261) gets compiler wire
    ;; 36. 85fcca93 wrote 261 here; validate! refuses ids outside 1..255
    ;; and the tripwire requires contiguous wires 1..n.
    (is (= 36 (get-in entries [:fs/browse-dir :compiler-wire-id])))
    (is (= 'fs/browse-dir (get-in entries [:fs/browse-dir :source-operation])))
    ;; io/write gets compiler wire 37: the bytes a COMMAND writes to its
    ;; standard output. It is the capability that makes a Kotoba guest a
    ;; command rather than a function -- before it there was no entry in this
    ;; catalog for stdout or for argv, so a guest could compute an answer and
    ;; had no way to say one.
    ;;
    ;; The effect is deliberately its OWN and not :host/log-append. A
    ;; command's stdout is its answer; an audit record is a different thing,
    ;; and one grant must not carry both.
    (is (= 37 (get-in entries [:io/write :compiler-wire-id])))
    (is (= 'io/write (get-in entries [:io/write :source-operation])))
    (is (= :host/io-write (get-in entries [:io/write :effect])))
    ;; cli/args gets compiler wire 38: the arguments a command was invoked
    ;; with. With :io/write it completes the pair a command cannot be built
    ;; without -- one to see what it was asked, one to answer.
    ;;
    ;; Its own effect, not shared with :host/io-write: seeing how a process
    ;; was invoked and producing output are different authorities.
    (is (= 38 (get-in entries [:cli/args :compiler-wire-id])))
    (is (= 'cli/args (get-in entries [:cli/args :source-operation])))
    (is (= :host/cli-args (get-in entries [:cli/args :effect])))
    ;; io/write-error gets compiler wire 39: a command's DIAGNOSTIC output.
    ;; Its own effect, asserted here, because sharing :host/io-write would let
    ;; a grant meant for complaining put bytes into the answer.
    (is (= 39 (get-in entries [:io/write-error :compiler-wire-id])))
    (is (= 'io/write-error (get-in entries [:io/write-error :source-operation])))
    (is (= :host/io-write-error (get-in entries [:io/write-error :effect])))
    (is (not= (get-in entries [:io/write :effect])
              (get-in entries [:io/write-error :effect]))
        "stdout and stderr must not share an effect")
    ;; sys/cwd gets compiler wire 40: where the process was started.
    ;;
    ;; Its own effect, asserted here for the same reason stderr has one.
    ;; `pwd` is often described as reading $PWD, but /bin/pwd -L VALIDATES
    ;; $PWD against the real directory and prints the physical path when they
    ;; disagree -- measured 2026-09-10, PWD=/tmp still printed the physical
    ;; path. The environment variable is a hint that gets checked, not the
    ;; answer, so a grant to read environment variables must not supply it.
    (is (= 40 (get-in entries [:sys/cwd :compiler-wire-id])))
    (is (= 'sys/cwd (get-in entries [:sys/cwd :source-operation])))
    (is (= :host/sys-cwd (get-in entries [:sys/cwd :effect])))
    (is (not= (get-in entries [:env/read :effect])
              (get-in entries [:sys/cwd :effect]))
        "reading the environment must not carry the working directory")))

(deftest duplicate-wire-id-fails-closed
  (let [authority (catalog/read-authority)]
    (testing "a semantic alias cannot acquire an existing wire identity"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"wire IDs must be unique"
           (catalog/validate!
            (assoc-in authority
                      [:capabilities :storage/transact :compiler-wire-id]
                      11)))))))
