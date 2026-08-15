(ns kotoba.lang.incidence-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [kotoba.lang.incidence :as incidence]))

(def alice (incidence/typed-ref :did "did:key:z6Mkalice"))
(def bob (incidence/typed-ref :did "did:key:z6Mkbob"))
(def service (incidence/typed-ref :uri "https://example.test/service"))

(def constitution
  (incidence/constitute :organization #{alice}
                        {:facts {:organization/name "Example"}}))

(def org (incidence/typed-ref :cid (incidence/incidence-cid constitution)))

(deftest machine-readable-contract-and-reference-implementation-agree
  (let [contract (edn/read-string (slurp "lang/incidence.edn"))]
    (is (= incidence/payload-version
           (:kotoba.lang.incidence/version contract)))
    (is (= incidence/required-fields (get-in contract [:block :required])))
    (is (true? (get-in contract [:identity :integrity-is-not-authority])))
    (is (= incidence/organization-kinds
           (get-in contract [:organization :kinds])))
    (is (= :opaque-verified-organization-binding
           (get-in contract [:organization :external-identity :admission])))
    (is (= :kotoba.lang.interop-verifiers/did-vc-organization-binding-verifier
           (get-in contract [:adapters :w3c-did :adapter])))
    (is (= :kotoba.lang.interop-verifiers/ucan-delegation-verifier
           (get-in contract [:adapters :ucan :adapter])))
    (is (= :assertionMethod
           (get-in contract [:adapters :w3c-vc :proof-purpose])))
    (is (= incidence/signed-readback-kind
           (get-in contract [:coordination :publication :transport :ocapn
                             :delivery-modes :signed-readback
                             :statement-kind])))
    (is (= :verified-cid-set-union
           (get-in contract [:coordination :replication :model])))
    (is (= :explicit-branch-conflict
           (get-in contract [:organization :governance :concurrency
                             :same-conflict-key])))))

(deftest frozen-canonical-vector-holds
  (let [vectors (edn/read-string (slurp "lang/incidence-vectors.edn"))]
    (is (= incidence/payload-version (:payload-version vectors)))
    (doseq [{:keys [id block canonical-hex incidence-cid]} (:vectors vectors)]
      (is (= canonical-hex (incidence/canonical-hex block)) (str id " bytes"))
      (is (= incidence-cid (incidence/incidence-cid block)) (str id " CID")))))

(deftest typed-references-do-not-confuse-names-with-principals
  (is (incidence/ref? alice))
  (is (incidence/ref? service))
  (is (incidence/ref? org))
  (is (false? (incidence/ref? "did:key:z6Mkalice")))
  (is (false? (incidence/ref? {:ref/type :did :ref/value "alice"})))
  (is (false? (incidence/ref? {:ref/type :cid :ref/value "bafy-not-a-cid"}))))

(deftest incidence-cid-seals-semantics-not-source-order
  (let [a (incidence/incidence
           :economic/transfer
           (array-map :provider #{alice} :receiver #{bob} :resource #{service})
           {:facts {:quantity 1 :unit :item}})
        b (incidence/incidence
           :economic/transfer
           (array-map :resource #{service} :receiver #{bob} :provider #{alice})
           {:facts (array-map :unit :item :quantity 1)})]
    (is (= (incidence/incidence-cid a) (incidence/incidence-cid b)))
    (is (not= (incidence/incidence-cid a)
              (incidence/incidence-cid
               (incidence/incidence
                :economic/transfer
                (:incidence/roles a)
                {:facts {:quantity 2 :unit :item}}))))))

(deftest malformed-or-partial-blocks-fail-closed
  (let [valid (incidence/incidence :relation/example {:subject #{alice}} {})]
    (is (nil? (incidence/incidence-error valid)))
    (is (= :incidence/fields
           (:problem (incidence/incidence-error
                      (assoc valid :incidence/authorized? true)))))
    (is (= :incidence/roles
           (:problem (incidence/incidence-error
                      (assoc valid :incidence/roles {:subject #{"did:key:z"}})))))
    (is (= :incidence/cid-set
           (:problem (incidence/incidence-error
                      (assoc valid :incidence/policies #{"policy-name"})))))
    (let [bad-remove (incidence/incidence
                      :organization/member-removed
                      {:organization #{org} :member #{bob}}
                      {:facts {:membership/removes
                               #{(incidence/incidence-cid constitution)}}})]
      (is (= :organization/member-removed
             (:problem (incidence/incidence-error bad-remove)))))))

(deftest append-requires-known-parents-and-addressed-projection-verifies-hash
  (let [root-entry (incidence/addressed constitution)
        root-cid (:incidence/cid root-entry)
        child (incidence/incidence :relation/child {:subject #{org}}
                                   {:parents #{root-cid}})
        admitted (incidence/admit-append #{root-cid} child)]
    (is (:ok? admitted))
    (is (= :incidence/unknown-parent
           (:reason (incidence/admit-append #{} child))))
    (is (= :incidence/incomplete-history
           (:reason (incidence/project [(:entry admitted)]))))
    (let [projection (incidence/project [root-entry (:entry admitted)])]
      (is (:ok? projection))
      (is (= #{root-cid}
             (get-in projection [:by-kind :organization/constitution])))
      (is (= #{(:incidence/cid (:entry admitted))}
             (get-in projection [:by-role :subject org]))))
    (is (= :incidence/hash-mismatch
           (:reason (incidence/verify-addressed
                     (assoc root-entry :incidence/block
                            (assoc-in constitution
                                      [:incidence/facts :organization/name]
                                      "Substituted"))))))))

(deftest person-agent-organization-and-system-share-one-constitution-model
  (doseq [kind [:person :agent :organization :system]]
    (let [block (incidence/constitute kind #{alice} {})]
      (is (= :organization/constitution (:incidence/kind block)))
      (is (= kind (get-in block [:incidence/facts :organization/kind])))
      (is (string? (incidence/incidence-cid block)))))
  (is (= :organization/kind
         (:problem (ex-data (try (incidence/constitute :company #{alice} {})
                                 (catch Exception e e)))))))

(deftest membership-is-an-order-independent-observed-remove-projection
  (let [add-a (incidence/member-added org bob #{:maintainer} {})
        add-b (incidence/member-added org bob #{:reviewer}
                                      {:facts {:source :vc}})
        add-a-entry (incidence/addressed add-a)
        add-b-entry (incidence/addressed add-b)
        remove-a (incidence/member-removed
                  org bob #{(:incidence/cid add-a-entry)} {})
        entries [add-a-entry add-b-entry (incidence/addressed remove-a)]
        forward (incidence/project-membership entries org)
        reverse-order (incidence/project-membership (reverse entries) org)]
    (is (= forward reverse-order))
    (is (= #{bob} (:members forward)))
    (is (= #{(:incidence/cid add-b-entry)}
           (set (keys (:active-adds forward)))))))

(deftest syndicate-style-assert-retract-observe-is-pure-and-order-independent
  (let [presence (incidence/incidence
                  :presence/online
                  {:room #{service} :participant #{alice}}
                  {:facts {:status :available}})
        assertion (incidence/assertion presence)
        tombstone (incidence/assertion
                   (incidence/retract bob #{(:incidence/cid assertion)} {}))
        live (incidence/active-projection [assertion])
        gone-a (incidence/active-projection [assertion tombstone])
        gone-b (incidence/active-projection [tombstone assertion])]
    (is (= #{(:incidence/cid assertion)}
           (set (keys (:matches
                       (incidence/observe
                        live {:kind :presence/online
                              :roles {:room service}}))))))
    (is (= gone-a gone-b))
    (is (= #{(:incidence/cid assertion)} (:retracted gone-a)))
    (is (empty? (:matches
                 (incidence/observe gone-a {:roles {:participant alice}}))))
    (is (= :dataspace/query-invalid
           (:reason (incidence/observe live {:roles {:room "a name"}}))))))

(deftest facet-stop-automatically-retracts-every-owned-assertion
  (let [presence (incidence/incidence
                  :presence/online
                  {:room #{service} :participant #{alice}}
                  {})
        status (incidence/incidence
                :presence/status
                {:participant #{alice}}
                {:facts {:status :available}})
        opened (incidence/facet alice)
        first-assert (incidence/facet-assert opened presence)
        second-assert (incidence/facet-assert (:facet first-assert) status)
        duplicate (incidence/facet-assert (:facet second-assert) presence)
        stopped (incidence/facet-stop (:facet duplicate))
        assertion-entries (into [] (concat (:emit first-assert)
                                           (:emit second-assert)))
        all-entries (into assertion-entries (:emit stopped))
        projection (incidence/active-projection all-entries)
        targets (set (map :incidence/cid assertion-entries))]
    (is (:ok? first-assert))
    (is (:ok? second-assert))
    (is (empty? (:emit duplicate)))
    (is (= :stopped (get-in stopped [:facet :facet/status])))
    (is (= targets
           (get-in stopped [:facet :facet/withdrawal :incidence/block
                            :incidence/facts :dataspace/retracts])))
    (is (= targets (:retracted projection)))
    (is (= #{:dataspace/retracted}
           (set (map :incidence/kind (vals (:active-blocks projection))))))
    (is (empty? (:matches
                 (incidence/observe projection {:kind :presence/online}))))
    (is (empty? (:emit (incidence/facet-stop (:facet stopped)))))
    (is (= :facet/stopped
           (:reason (incidence/facet-assert (:facet stopped) presence))))))

(deftest facet-stop-is-canonical-across-assertion-order
  (let [a (incidence/incidence :fact/a {:subject #{alice}} {})
        b (incidence/incidence :fact/b {:subject #{bob}} {})
        build (fn [blocks]
                (reduce (fn [state block]
                          (:facet (incidence/facet-assert state block)))
                        (incidence/facet alice)
                        blocks))
        stopped-a (incidence/facet-stop (build [a b]))
        stopped-b (incidence/facet-stop (build [b a]))]
    (is (= (get-in stopped-a [:emit 0 :incidence/cid])
           (get-in stopped-b [:emit 0 :incidence/cid])))
    (is (nil? (incidence/facet-error (:facet stopped-a))))))

(deftest facet-state-is-inert-and-fails-closed
  (let [empty-stop (incidence/facet-stop (incidence/facet alice))
        asserted (incidence/facet-assert
                  (incidence/facet alice)
                  (incidence/incidence :fact/a {:subject #{alice}} {}))
        stopped (:facet (incidence/facet-stop (:facet asserted)))]
    (is (:ok? empty-stop))
    (is (empty? (:emit empty-stop)))
    (is (nil? (get-in empty-stop [:facet :facet/withdrawal])))
    (is (nil? (:capability empty-stop)))
    (is (= :facet/withdrawal
           (:problem (incidence/facet-error (assoc stopped :facet/owner bob)))))
    (is (= :facet/state-invalid
           (:reason
            (incidence/facet-assert
             (assoc (incidence/facet alice) :facet/publish-authority true)
             (incidence/incidence :fact/a {:subject #{alice}} {})))))
    (is (= :facet/retraction-not-owned
           (:reason
            (incidence/facet-assert
             (incidence/facet alice)
             (incidence/retract alice #{(incidence/incidence-cid constitution)}
                                {})))))))

(deftest cid-and-delegation-shaped-data-never-mint-authority
  (let [delegation (incidence/incidence
                    :capability/delegation
                    {:grantor #{alice} :grantee #{bob} :resource #{service}}
                    {:facts {:actions #{:read}}})
        entry (incidence/addressed delegation)
        projection (incidence/project [entry])]
    (is (:ok? projection))
    (is (= #{(:incidence/cid entry)}
           (get-in projection [:by-kind :capability/delegation])))
    (is (nil? (:capability projection))
        "integrity/indexing does not mint a runtime capability")
    (is (false? (incidence/ref? {:cap/kind :host/http
                                 :cap/resources #{"https://example.test"}}))
        "a serialized capability-shaped map is not a participant reference")))
