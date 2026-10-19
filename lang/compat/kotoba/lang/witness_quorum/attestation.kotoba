(ns kotoba.lang.witness-quorum.attestation
  "nbb/ClojureScript port of attestation.clj -- see that file for full
  docs (cell-side attestation production: validate + sign + format).
  Same public API; `canonical-attestation-bytes` must produce bytes that
  hash/sign identically to the JVM version for the same logical inputs,
  since that's the whole point of a cross-platform witness fleet."
  (:require [clojure.string :as str]
            [kotoba.lang.witness-quorum.selector :as selector]))

(defn- accept-schema [_record _rule] {:layer :schema :verdict :accept})
(defn- accept-policy [_record _rule] {:layer :policy :verdict :accept})
(defn- accept-deterministic [_record _rule] {:layer :deterministic :verdict :accept})

(defn minimal-schema-validator
  "Built-in basic schema validator -- checks that `record` is a map with
  a positive integer `:v` field."
  [record _rule]
  (cond
    (not (map? record))
    {:layer :schema :verdict :reject
     :reason (if (sequential? record)
               "record must be an object, not an array"
               "record is not an object")}

    (let [v (:v record)] (not (and (integer? v) (>= v 1))))
    {:layer :schema :verdict :reject
     :reason "record.v must be a positive integer (lexicon format version)"}

    :else {:layer :schema :verdict :accept}))

(defn validate-against-membrane
  "Validate a record against a membrane rule, returning the composite
  verdict. Pure function -- no I/O."
  ([record rule] (validate-against-membrane record rule {}))
  ([record rule validators]
   (let [schema-check (:schema validators accept-schema)
         policy-check (:policy validators accept-policy)
         det-check (:deterministic validators accept-deterministic)
         s (schema-check record rule)]
     (if (not= (:verdict s) :accept)
       {:verdict (:verdict s) :layers [s] :reason (:reason s)}
       (let [p (policy-check record rule)]
         (if (not= (:verdict p) :accept)
           {:verdict (:verdict p) :layers [s p] :reason (:reason p)}
           (let [d (det-check record rule)]
             (if (not= (:verdict d) :accept)
               {:verdict (:verdict d) :layers [s p d] :reason (:reason d)}
               {:verdict :accept :layers [s p d]}))))))))

(defn membrane-version-for
  "Build the membraneVersion string per the lexicon spec:
  `lex:{semver}/rego:{semver}/cell:{git-sha-7}`."
  [rule]
  (let [ref-v (fn [{:keys [version content-hash]}] (or version (subs content-hash 0 7)))]
    (str "lex:" (ref-v (:schema-ref rule))
         "/rego:" (ref-v (:policy-ref rule))
         "/cell:" (ref-v (:cell-ref rule)))))

(defn canonical-attestation-bytes
  "Canonicalize the attestation prefix for signing:
  `{record-cid}\\n{cell-id}\\n{verdict}\\n{reason}\\n{membrane-version}\\n{attested-at}`.
  `:verdict`'s bare name (e.g. \"accept\") is what goes on the wire -- must
  byte-match the JVM/TS/Python sibling implementations' string enum
  values for cross-language signature verification to hold."
  [{:keys [record-cid cell-id verdict reason membrane-version attested-at]}]
  (js/Buffer.from (str/join "\n" [record-cid cell-id (name verdict) reason membrane-version attested-at])
                  "utf8"))

(defn produce-attestation
  "Cell-side: validate + sign + format an attestation map."
  [{:keys [record-uri record-cid record cell rule validators signer attested-at]}]
  (let [verdict-result (validate-against-membrane record rule validators)
        attested-at (or attested-at (.toISOString (js/Date.)))
        membrane-version (membrane-version-for rule)
        reason (or (:reason verdict-result) "")
        canonical (canonical-attestation-bytes
                   {:record-cid record-cid
                    :cell-id (:cell-id cell)
                    :verdict (:verdict verdict-result)
                    :reason reason
                    :membrane-version membrane-version
                    :attested-at attested-at})
        signature (signer canonical)]
    (cond-> {:v 1
             :record-uri record-uri
             :record-cid record-cid
             :cell-id (:cell-id cell)
             :cell-node (:node cell)
             :verdict (:verdict verdict-result)
             :reason (when (seq reason) reason)
             :membrane-version membrane-version
             :attested-at attested-at
             :signature signature
             :quorum-group (selector/quorum-group record-cid)}
      (= (:verdict verdict-result) :escalate) (assoc :escalation-target :council))))
