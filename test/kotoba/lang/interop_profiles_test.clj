(ns kotoba.lang.interop-profiles-test
  (:require [clojure.test :refer [deftest is]]
            [data-integrity.core :as di]
            [data-integrity.eddsa :as eddsa]
            [ed25519.core :as ed]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.interop-profiles :as profiles]
            [kotoba.lang.trusted-admission :as trusted]
            [status-list.core :as status-list]
            [ucan.core :as ucan]))

(defn seed [offset]
  (byte-array (map unchecked-byte (range offset (+ offset 32)))))

(def org-seed (seed 0))
(def peer-seed (seed 32))
(def holder-seed (seed 64))
(def org-did (ed/did-key-from-seed org-seed))
(def peer-did (ed/did-key-from-seed peer-seed))
(def holder-did (ed/did-key-from-seed holder-seed))
(def org-method (str org-did "#" (subs org-did (count "did:key:"))))
(def peer (incidence/typed-ref :did peer-did))
(def constitution
  (incidence/addressed
   (incidence/constitute :organization
                         #{(incidence/typed-ref :did org-did)}
                         {:facts {:organization/name "Example"}})))

(defn issue [seed did document]
  (di/issue-credential
   document
   {:suite eddsa/suite
    :seed seed
    :verification-method (str did "#" (subs did (count "did:key:")))
    :created "2026-08-15T00:00:00Z"}))

(defn org-credential
  ([] (org-credential nil))
  ([credential-status]
   (cond-> {"@context" ["https://www.w3.org/ns/credentials/v2"
                         "https://w3id.org/security/data-integrity/v2"]
            "type" ["VerifiableCredential" "KotobaOrganizationBinding"]
            "issuer" org-did
            "credentialSubject"
            {"id" org-did
             "kotobaConstitution" (:incidence/cid constitution)}}
     credential-status (assoc "credentialStatus" credential-status))))

(defn evidence [secured]
  {:binding/did org-did
   :binding/verification-method org-method
   :binding/authorized-peers #{peer}
   :binding/evidence-cid (:incidence/cid constitution)
   :vc/secured-document secured})

(defn problem [f]
  (try (f) nil
       (catch clojure.lang.ExceptionInfo e (:problem (ex-data e)))))

(deftest real-data-integrity-proof-mints-only-an-opaque-binding
  (let [secured (issue org-seed org-did (org-credential))
        verifier (profiles/did-key-vc-organization-binding-verifier {})
        binding (trusted/verify-organization-binding!
                 verifier constitution (evidence secured))]
    (is (trusted/verified-organization-binding? binding))
    (is (= org-did
           (:binding/did
            (trusted/organization-binding-description binding))))
    (let [tampered (assoc secured "issuer" peer-did)]
      (is (some? (problem #(trusted/verify-organization-binding!
                           verifier constitution (evidence tampered))))))))

(deftest signed-bitstring-status-list-is-mandatory-when-status-is-present
  (let [url "https://example.test/status/1"
        entry (status-list/entry
               {:id (str url "#7") :index 7
                :status-list-credential url})
        list-doc (status-list/status-list-credential
                  {:id url :issuer org-did
                   :encoded-list (status-list/generate {7 1})})
        list-secured (issue org-seed org-did list-doc)
        secured (issue org-seed org-did (org-credential entry))]
    (is (= :interop/status-verifier-required
           (problem #(profiles/did-key-vc-verify!
                      secured
                      {:expectedProofPurpose "assertionMethod"
                       :verificationMethod org-method}))))
    (is (= :interop/credential-not-active
           (problem #(profiles/did-key-vc-verify!
                      secured
                      {:expectedProofPurpose "assertionMethod"
                       :verificationMethod org-method}
                      {:resolve-status-list! (constantly list-secured)}))))))

(def resource "kotoba://cap/host/ledger-append/dataspace:rooms/a")
(defn delegation-payload [iss aud command nonce]
  {"iss" iss "aud" aud "sub" org-did "cmd" command
   "pol" [["==" ".resource" resource]]
   "nonce" (byte-array (repeat 12 (unchecked-byte nonce)))
   "exp" 2000})

(deftest real-ucan-chain-mints-only-attenuated-kotoba-grants
  (let [chain [(ucan/sign-delegation
                org-seed (delegation-payload org-did peer-did "/kotoba" 1))
               (ucan/sign-delegation
                peer-seed (delegation-payload peer-did holder-did
                                              "/kotoba/ledger" 2))]
        verifier (profiles/did-key-ucan-delegation-verifier
                  {:holder holder-did
                   :now! (constantly 1900)
                   :revoked? (constantly false)})
        delegation (trusted/verify-delegation!
                    verifier {:ucan/chain chain})]
    (is (trusted/verified-delegation? delegation))
    (is (= :host/ledger-append
           (:grant/kind (first (trusted/delegation-grants delegation)))))
    (is (= #{"dataspace:rooms/a"}
           (:grant/resources
            (first (trusted/delegation-grants delegation)))))))
