(ns tayori.policy
  "Pure checks over the published tayori facts (contact consent, redaction
  requirements, document tenancy) — no I/O, no store: shared by
  ComplianceGovernor and the mock reply-LLM so both reason over the same facts
  without coupling the censor to the proposer (the tayori analog of
  kekkai.acl).")

(def protected-tags #{:health :legal :financial})

(defn protected-tag? [cite] (contains? protected-tags cite))

(defn missing-redactions
  "Protected cites the proposal did not list in :redactions."
  [cites redactions]
  (vec (remove (set redactions) (filter protected-tag? cites))))

(defn consent-blocked? [contact] (= :blocked (:consent contact)))

(defn first-contact? [contact] (boolean (:first-contact? contact)))

(defn target-mismatch?
  "Does `target` diverge from the document's own registered path? No implicit
  allow — publishing anywhere other than the document's own registered target
  is a hijack of a different document's slot."
  [document target]
  (not= target (:path document)))
