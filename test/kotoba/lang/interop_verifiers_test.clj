(ns kotoba.lang.interop-verifiers-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.interop-verifiers :as interop]
            [kotoba.lang.trusted-admission :as trusted]))

(def org-did "did:key:z6Mkorganization")
(def method (str org-did "#assertion-1"))
(def peer (incidence/typed-ref :did "did:key:z6Mkpeer"))
(def constitution
  (incidence/addressed
   (incidence/constitute :organization
                         #{(incidence/typed-ref :did org-did)}
                         {:facts {:organization/name "Example"}})))
(def evidence-cid (:incidence/cid constitution))
(def did-document
  {"id" org-did
   "verificationMethod"
   [{"id" method "type" "Multikey" "controller" org-did
     "publicKeyMultibase" "z6Mkexample"}]
   "assertionMethod" [method]})
(def secured-vc
  {"@context" ["https://www.w3.org/ns/credentials/v2"
                "https://w3id.org/security/data-integrity/v2"]
   "type" ["VerifiableCredential" "KotobaOrganizationBinding"]
   "issuer" org-did
   "credentialSubject" {"id" org-did
                         "kotobaConstitution" (:incidence/cid constitution)}
   "proof" {"type" "DataIntegrityProof"
             "cryptosuite" "eddsa-rdfc-2022"
             "verificationMethod" method
             "proofPurpose" "assertionMethod"
             "proofValue" "zproof"}})
(def binding-evidence
  {:binding/did org-did
   :binding/verification-method method
   :binding/authorized-peers #{peer}
   :binding/evidence-cid evidence-cid
   :vc/secured-document secured-vc})

(defn binding-verifier
  ([] (binding-verifier did-document secured-vc))
  ([document verified-document]
   (interop/did-vc-organization-binding-verifier
    {:resolve! (constantly document)
     :verify! (fn [_ options]
                (is (= "assertionMethod" (:expectedProofPurpose options)))
                {:verified true :verified-document verified-document})
     :allowed-cryptosuites #{"eddsa-rdfc-2022"}
     :max-methods 8 :max-subjects 4 :max-peers 8})))

(deftest did-vc-adapter-mints-only-the-existing-opaque-binding
  (let [binding (trusted/verify-organization-binding!
                 (binding-verifier) constitution binding-evidence)]
    (is (trusted/verified-organization-binding? binding))
    (is (false? (trusted/verified-organization-binding? binding-evidence)))
    (is (= org-did (:binding/did
                    (trusted/organization-binding-description binding))))
    (is (= :assertionMethod
           (:binding/verification-relationship
            (trusted/organization-binding-description binding))))))

(deftest did-vc-adapter-enforces-relationship-purpose-and-verified-document
  (doseq [[expected verifier evidence]
          [[:interop/did-assertion-method-missing
            (binding-verifier (assoc did-document "assertionMethod" []) secured-vc)
            binding-evidence]
           [:interop/proof-purpose-mismatch
            (binding-verifier)
            (assoc binding-evidence :vc/secured-document
                   (assoc-in secured-vc ["proof" "proofPurpose"] "authentication"))]
           [:interop/vc-verification-invalid
            (binding-verifier did-document (assoc secured-vc "issuer" "did:key:other"))
            binding-evidence]
           [:interop/did-verification-methods-unbounded
            (binding-verifier (assoc did-document "verificationMethod"
                                     (vec (repeat 9 (first (get did-document
                                                               "verificationMethod")))))
                              secured-vc)
            binding-evidence]]]
    (let [thrown (try
                   (verifier {:constitution constitution :evidence evidence})
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= expected (:problem (ex-data thrown)))))))

(deftest trusted-boundary-sanitizes-did-vc-adapter-failures
  (let [verifier (binding-verifier
                  (assoc did-document "assertionMethod" []) secured-vc)
        thrown (try
                 (trusted/verify-organization-binding!
                  verifier constitution binding-evidence)
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (= :trusted/organization-binding-verification-failed
           (:problem (ex-data thrown))))))

(def valid-ucan-result
  {:ucan/valid? true
   :ucan/problems []
   :ucan/root-iss "did:key:z6Mkroot"
   :ucan/audience "did:key:z6Mkholder"
   :ucan/resources #{"kotoba://cap/host/ledger-append/dataspace:rooms/a"}
   :ucan/expires nil
   :ucan/depth 2
   :ucan/attenuated? true})

(deftest ucan-adapter-mints-a-delegation-with-attenuated-grants
  (let [verifier (interop/ucan-delegation-verifier
                  {:verify! (constantly valid-ucan-result)
                   :holder "did:key:z6Mkholder"
                   :max-depth 4 :max-resources 8})
        delegation (trusted/verify-delegation! verifier {:ucan "inert-token"})]
    (is (trusted/verified-delegation? delegation))
    (is (= :host/ledger-append
           (:grant/kind (first (trusted/delegation-grants delegation)))))
    (is (= #{"dataspace:rooms/a"}
           (:grant/resources (first (trusted/delegation-grants delegation)))))))

(deftest ucan-adapter-rejects-audience-amplification-and-unbounded-chains
  (doseq [result [(assoc valid-ucan-result :ucan/audience "did:key:other")
                  (assoc valid-ucan-result :ucan/attenuated? false)
                  (assoc valid-ucan-result :ucan/depth 5)
                  (assoc valid-ucan-result :ucan/resources #{"https://example.test/admin"})
                  (assoc valid-ucan-result :ucan/resources #{:not-a-uri})]]
    (let [verifier (interop/ucan-delegation-verifier
                    {:verify! (constantly result)
                     :holder "did:key:z6Mkholder"
                     :max-depth 4 :max-resources 8})
          thrown (try
                   (trusted/verify-delegation! verifier :evidence)
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= :trusted/delegation-verification-failed
             (:problem (ex-data thrown)))))))
