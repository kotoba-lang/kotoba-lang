;; CI6 — cross-implementation conformance for definition identity.
;;
;; CI1's admission rule is "byte-for-byte deterministic identity". Inside one
;; implementation that is only a regression test; the claim that matters is
;; that a SECOND implementation computes the same bytes. This script recomputes
;; every frozen vector under ClojureScript (nbb) from the same .cljc source and
;; fails if any canonical block or CID differs from the Clojure-generated table.
;;
;; This is the check that would have caught the version 1 encoding: it hashed
;; `pr-str` output, which is not byte-identical across the two platforms.
;;
;; Run (from the repository root, with the sibling libraries on the classpath):
;;
;;   nbb --classpath "src:../io-multiformats/src:../dag-cbor/src" \
;;       scripts/verify_code_identity_cljs.cljs

(require '[kotoba.lang.code-identity :as ci]
         '[cljs.reader :as reader]
         '["fs" :as fs])

(def table (reader/read-string (fs/readFileSync "lang/code-identity-vectors.edn" "utf8")))

(when-not (= ci/payload-version (:payload-version table))
  (println "FAIL payload-version:" (:payload-version table) "in table," ci/payload-version "in source")
  (js/process.exit 1))

(def failures
  (reduce
   (fn [acc {:keys [id definition canonical-hex definition-cid]}]
     (let [hex (ci/canonical-hex definition)
           cid (ci/definition-cid definition)]
       (cond-> acc
         (not= canonical-hex hex)
         (conj {:id id :field :canonical-hex :expected canonical-hex :actual hex})
         (not= definition-cid cid)
         (conj {:id id :field :definition-cid :expected definition-cid :actual cid}))))
   []
   (:vectors table)))

;; The effect-row bridge under ClojureScript: nbb carries capability ids as
;; BigInt, and a BigInt is not `=` to the plain number a catalog keys on. The
;; bridge normalises, so the same compiler report bridges to the same keyword
;; row -- and therefore the same CID -- on both implementations.
(def bridge-failures
  (let [catalog {5 :clock/now 8 :state/transact}
        expected #{:state/transact :clock/now}
        row (try (ci/effect-row-from-hir {:effects #{[:cap/call (js/BigInt 8)] [:cap/call (js/BigInt 5)]}
                                          :named-operations #{:state/transact :clock/now}}
                                         {:id->name catalog})
                 (catch :default e {:error (.-message e)}))
        refused (try (ci/effect-row-from-hir {:effects #{[:cap/call (js/BigInt 200)]}}
                                             {:id->name catalog})
                     nil
                     (catch :default e (.-message e)))]
    (cond-> []
      (not= expected row)
      (conj {:id :bridge-bigint-row :field :effect-row :expected expected :actual row})
      (not= "effect row wire id has no catalog name: [:cap/call 200]" refused)
      (conj {:id :bridge-unnamed-id :field :refusal
             :expected "effect row wire id has no catalog name: [:cap/call 200]" :actual refused}))))

(def failures (into failures bridge-failures))

(doseq [f failures]
  (println "FAIL" (:id f) (:field f))
  (println "  clojure   :" (:expected f))
  (println "  cljs      :" (:actual f)))

(if (seq failures)
  (do (println (count failures) "cross-implementation mismatch(es) across"
               (count (:vectors table)) "vectors")
      (js/process.exit 1))
  (println "ok:" (count (:vectors table))
           "vectors produce identical canonical bytes and CIDs under ClojureScript;"
           "effect-row bridge agrees on BigInt ids and on the unnamed-id refusal"))
