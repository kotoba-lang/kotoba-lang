(ns identity.validate
  (:require [identity.model :as m]))

(defn problems [record]
  (cond-> []
    (and (contains? record :identity.subject/id)
         (nil? (:identity.subject/id record)))
    (conj {:identity.problem/code :subject/missing-id})

    (and (:identity.subject/type record)
         (not (contains? m/subject-types (:identity.subject/type record))))
    (conj {:identity.problem/code :subject/unknown-type})

    (and (contains? record :identity.evidence/id)
         (nil? (:identity.evidence/id record)))
    (conj {:identity.problem/code :evidence/missing-id})

    (and (:identity.evidence/kind record)
         (not (contains? m/evidence-kinds (:identity.evidence/kind record))))
    (conj {:identity.problem/code :evidence/unknown-kind})

    (and (contains? record :identity.attestation/id)
         (nil? (:identity.attestation/id record)))
    (conj {:identity.problem/code :attestation/missing-id})

    (and (contains? record :identity.attestation/subject)
         (nil? (:identity.attestation/subject record)))
    (conj {:identity.problem/code :attestation/missing-subject})

    (and (contains? record :identity.attestation/predicate)
         (nil? (:identity.attestation/predicate record)))
    (conj {:identity.problem/code :attestation/missing-predicate})

    (false? (:identity/non-adjudicating record))
    (conj {:identity.problem/code :adjudicating-record})))

(defn valid? [record]
  (empty? (problems record)))

(defn valid! [record]
  (when-let [ps (seq (problems record))]
    (throw (ex-info "invalid identity record" {:identity/problems ps})))
  record)
