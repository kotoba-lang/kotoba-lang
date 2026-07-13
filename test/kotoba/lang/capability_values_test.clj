(ns kotoba.lang.capability-values-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kotoba.lang.capability-cacao :as cacao]
            [kotoba.lang.capability-host :as host]
            [kotoba.lang.capability-values :as caps]))

(def manifest-path "lang/capability-conformance/manifest.edn")

(defn read-edn
  [path]
  (edn/read-string (slurp (io/file path))))

;; lang/capability-conformance/manifest.edn is stored as Datomic/Datascript
;; tx-data (see schema.edn). See scripts/check-capability-values.bb for the
;; same reconstitution -- the manifest's plain :cases key was renamed to
;; :kotoba.lang.capability.conformance/cases and blob-stringified, while the
;; already-namespaced :version key was left untouched.
(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-capability-conformance-manifest [tx-data]
  (let [e (dissoc (first tx-data) :db/id)]
    {:kotoba.lang.capability.conformance/version
     (:kotoba.lang.capability.conformance/version e)
     :cases (unblob (:kotoba.lang.capability.conformance/cases e))}))

(def graph-a "bafygrapha11111111111111111111111111111111111111111111111")
(def graph-b "bafygraphb22222222222222222222222222222222222222222222222")
(def graph-c "bafygraphc33333333333333333333333333333333333333333333333")
(def now "2026-07-02")

(defn grant
  [kind resources expires id]
  {:grant/kind kind
   :grant/resources resources
   :grant/expires expires
   :grant/id id})

(deftest intersection-narrows-requested-any-to-concrete-cid
  (let [result (caps/intersect-grants
                {:requested (caps/graph-write-cap :any {:holder "did:key:z6Mkh"})
                 :cacao-grants [(grant :graph-write #{graph-a} "2027-01-01" "g1")]
                 :local-policy {:policy/allow {:graph-write #{graph-a graph-b}}}
                 :now now})]
    (is (not (caps/denied? result)))
    (is (= graph-a (:cap/resource result)))
    (is (= "2027-01-01" (:cap/expires result)))
    (is (= ["g1"] (:cap/provenance result)))
    (is (= "did:key:z6Mkh" (:cap/holder result)))
    (is (caps/capability? result))))

(deftest intersection-denies-on-empty-intersection
  (testing "requested resource outside every grant"
    (is (= {:denied :empty-intersection}
           (caps/intersect-grants
            {:requested (caps/graph-write-cap graph-c)
             :cacao-grants [(grant :graph-write #{graph-a} nil "g1")]
             :local-policy {:policy/allow {:graph-write #{graph-a}}}
             :now now}))))
  (testing "policy does not allow the kind at all"
    (is (= {:denied :empty-intersection}
           (caps/intersect-grants
            {:requested (caps/graph-write-cap graph-a)
             :cacao-grants [(grant :graph-write #{graph-a} nil "g1")]
             :local-policy {:policy/allow {:graph-read #{graph-a}}}
             :now now})))))

(deftest intersection-denies-when-every-covering-grant-is-expired
  (is (= {:denied :expired}
         (caps/intersect-grants
          {:requested (caps/graph-write-cap :any)
           :cacao-grants [(grant :graph-write #{graph-a} "2026-01-01" "g-stale")]
           :local-policy {:policy/allow {:graph-write #{graph-a}}}
           :now now}))))

(deftest intersection-denies-unsupported-kind
  (is (= {:denied :unsupported-kind}
         (caps/intersect-grants
          {:requested (caps/make-cap :teleport :any)
           :cacao-grants [(grant :teleport #{:any} nil "g1")]
           :local-policy {:policy/allow {:teleport #{:any}}}
           :now now}))))

(deftest aiueos-and-actor-host-kinds-are-registered-in-effect-for-kind
  (testing "every aiueos kernel-capability kind and kototama actor-host kind kotoba.runtime/op->kind
            declares is a member of effect-for-kind -- previously true only for the original
            issue #263 provider kinds and kgraph-*; these 15 were added to op->kind (aiueos,
            ADR-2607022700; actor-host, kotoba-core-contracts#3) without a matching entry here,
            so guard-call denied every one of them at RUN time with :unsupported-kind the moment
            a real caller supplied a policy and actually executed the guest, undetected because
            only the static compile-time capability gate (which never consults effect-for-kind)
            was ever exercised against them"
    (doseq [kind [:host/log-write :host/clock-monotonic :host/random-bytes
                  :host/topic-publish :host/topic-subscribe :host/pci-config
                  :host/dma-map :host/irq-subscribe :host/mmio-map
                  :host/identity-keypair :host/identity-sign :host/identity-verify
                  :host/hash-sha256 :host/http-post :host/log-read]]
      (is (contains? caps/effect-for-kind kind) kind)
      (is (not= {:denied :unsupported-kind}
                (caps/intersect-grants
                 {:requested (caps/make-cap kind :any)
                  :cacao-grants [(grant kind #{:any} nil "g1")]
                  :local-policy {:policy/allow {kind #{:any}}}
                  :now now}))
          (str kind " must not be denied as unsupported now that it's registered")))))

(deftest wildcard-result-requires-requested-grants-and-policy-all-any
  (testing "all three :any yields :any"
    (is (= :any
           (:cap/resource
            (caps/intersect-grants
             {:requested (caps/graph-read-cap :any)
              :cacao-grants [(grant :graph-read #{:any} nil "g-root")]
              :local-policy {:policy/allow {:graph-read #{:any}}}
              :now now})))))
  (testing "policy narrows an otherwise wildcard chain"
    (is (= graph-a
           (:cap/resource
            (caps/intersect-grants
             {:requested (caps/graph-read-cap :any)
              :cacao-grants [(grant :graph-read #{:any} nil "g-root")]
              :local-policy {:policy/allow {:graph-read #{graph-a}}}
              :now now})))))
  (testing "grants narrow an otherwise wildcard chain"
    (is (= graph-a
           (:cap/resource
            (caps/intersect-grants
             {:requested (caps/graph-read-cap :any)
              :cacao-grants [(grant :graph-read #{graph-a} nil "g1")]
              :local-policy {:policy/allow {:graph-read #{:any}}}
              :now now}))))))

(deftest vrm-domain-capabilities-are-runtime-supported
  (doseq [kind [:vrm/asset-read :vrm/compose :vrm/preview :vrm/export :vrm/publish]]
    (is (= kind (get caps/effect-for-kind kind)))
    (is (= graph-a
           (:cap/resource
            (caps/intersect-grants
             {:requested (caps/make-cap kind :any)
              :cacao-grants [(grant kind #{graph-a} nil "vrm-grant")]
              :local-policy {:policy/allow {kind #{graph-a}}}
              :now now})))
        (str kind " must pass canonical grant/policy intersection"))))

(deftest sensing-device-capability-kinds-are-registered-in-effect-for-kind
  (testing "ADR-2607140600 Phase 3a device-capability bridge (iPhone sensing for the indoor
            floorplan-lab): :host/motion-read, :host/audio-io, :host/ble-scan, :host/wifi-info
            must each be a member of effect-for-kind -- omitting one of these here is exactly
            the aiueos/actor:host :unsupported-kind runtime-denial gap documented in
            aiueos-and-actor-host-kinds-are-registered-in-effect-for-kind above (registered in
            kotoba.runtime/op->kind but not HERE means every real call is denied at RUN time
            even though the static compile-time capability gate never catches it)."
    (doseq [kind [:host/motion-read :host/audio-io :host/ble-scan :host/wifi-info]]
      (is (contains? caps/effect-for-kind kind) kind)
      (is (not= {:denied :unsupported-kind}
                (caps/intersect-grants
                 {:requested (caps/make-cap kind :any)
                  :cacao-grants [(grant kind #{:any} nil "g1")]
                  :local-policy {:policy/allow {kind #{:any}}}
                  :now now}))
          (str kind " must not be denied as unsupported now that it's registered")))))

(deftest effect-row-must-cover-capability-parameters
  (testing "consistent row"
    (is (= {:ok? true :missing #{}}
           (caps/effects-consistent? #{:graph-write}
                                     [(caps/graph-write-cap graph-a)]))))
  (testing "inconsistent row is detected"
    (is (= {:ok? false :missing #{:graph-write}}
           (caps/effects-consistent? #{:graph-read}
                                     [(caps/graph-write-cap graph-a)]))))
  (testing "infer capability requires :infer"
    (is (= {:ok? false :missing #{:infer}}
           (caps/effects-consistent? #{} [(caps/infer-cap "bafymodela1")]))))
  (testing "unknown kind fails closed instead of passing as pure data"
    (is (= {:ok? false :missing #{:teleport}}
           (caps/effects-consistent? #{:graph-read}
                                     [(caps/make-cap :teleport :any)])))))

(deftest receipt-embeds-concrete-capability-not-requested
  (let [requested (caps/graph-write-cap :any)
        concrete (caps/intersect-grants
                  {:requested requested
                   :cacao-grants [(grant :graph-write #{graph-a} nil "g1")]
                   :local-policy {:policy/allow {:graph-write #{graph-a graph-b}}}
                   :now now})
        r (caps/receipt concrete now 'kgraph-assert!)]
    (is (:ok? (caps/validate-receipt r)))
    (is (= concrete (:receipt/cap r)))
    (is (= graph-a (get-in r [:receipt/cap :cap/resource])))
    (is (not= (:cap/resource requested)
              (get-in r [:receipt/cap :cap/resource])))
    (is (= now (:receipt/at r)))
    (is (= 'kgraph-assert! (:receipt/call r)))))

(deftest malformed-cap-shapes-are-rejected
  (testing "a plain resource string is not a capability"
    (is (false? (caps/capability? graph-a)))
    (is (= :cap/not-a-map
           (-> (caps/validate-cap graph-a) :problems first :problem))))
  (testing "non-string resource"
    (let [result (caps/validate-cap {:cap/kind :graph-read
                                     :cap/resource 42
                                     :cap/provenance []})]
      (is (false? (:ok? result)))
      (is (some #(= :cap/resource-invalid (:problem %)) (:problems result)))))
  (testing "bad expiry and provenance"
    (let [result (caps/validate-cap {:cap/kind :graph-read
                                     :cap/resource graph-a
                                     :cap/expires "tomorrow"
                                     :cap/provenance "g1"})]
      (is (false? (:ok? result)))
      (is (= #{:cap/expires-invalid :cap/provenance-invalid}
             (set (map :problem (:problems result)))))))
  (testing "malformed requested capability fails intersection closed"
    (is (= {:denied :malformed-requested}
           (caps/intersect-grants
            {:requested graph-a
             :cacao-grants [(grant :graph-write #{graph-a} nil "g1")]
             :local-policy {:policy/allow {:graph-write #{graph-a}}}
             :now now}))))
  (testing "constructors produce well-formed capabilities"
    (is (caps/capability? (caps/graph-read-cap graph-a)))
    (is (caps/capability? (caps/graph-write-cap #{graph-a graph-b})))
    (is (caps/capability? (caps/infer-cap "bafymodela1"
                                          {:holder "did:key:z6Mkh"
                                           :expires "2026-12-31"
                                           :provenance ["g1"]})))))

(deftest capability-conformance-fixtures-match-contract
  (let [manifest (reconstitute-capability-conformance-manifest (read-edn manifest-path))]
    (is (= 1 (:kotoba.lang.capability.conformance/version manifest)))
    (doseq [tc (:cases manifest)
            :let [data (read-edn (str "lang/capability-conformance/" (:file tc)))
                  result (case (:type tc)
                           :host-dispatch (host/check-case tc data)
                           :cacao-grants (cacao/check-case tc data)
                           (caps/check-case tc data))]]
      (is (:ok? result) (str (:id tc) " -> " (pr-str (:actual result)))))))
