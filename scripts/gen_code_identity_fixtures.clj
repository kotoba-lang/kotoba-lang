;; Regenerates every hash-bearing artefact of kotoba.lang.code-identity:
;;
;;   lang/code-identity-vectors.edn                    (CI1 frozen vectors)
;;   lang/code-identity-conformance/manifest.edn       (CI2 + CI3 cases)
;;   lang/code-identity-conformance/{positive,negative}/*.edn
;;
;; Every CID below is computed, never typed by hand. A fixture with an invented
;; hash only ever tests the rejection path, and a hand-edited hash turns a
;; regression into a green build.
;;
;;   clojure -M -e "(load-file \"scripts/gen_code_identity_fixtures.clj\")"

(require '[kotoba.lang.code-identity :as ci]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(def base
  {:definition/profile-version 4
   :definition/desugar-contract-version 1
   :definition/kir {:op :const :value 1}
   :definition/effect-row #{}
   :definition/interface {:arity 0 :result :i64}
   :definition/dependencies []})

(defn cid [d] (ci/definition-cid d))

(def dep-a (cid (assoc base :definition/kir {:op :const :value 10})))
(def dep-b (cid (assoc base :definition/kir {:op :const :value 20})))

;; ---------------------------------------------------------------------------
;; CI1 vectors
;; ---------------------------------------------------------------------------

(def vector-cases
  [[:pure-const
    "The minimal closed pure definition: no dependencies, no effects."
    base]
   [:effect-row-http
    "Same KIR as :pure-const but requiring http authority — MUST NOT share its identity."
    (assoc base :definition/effect-row #{:host/http})]
   [:effect-row-two
    "Effect row members are a set: source order is not semantic."
    (assoc base :definition/effect-row #{:host/http :host/clock-monotonic})]
   [:desugar-contract-2
    "Same KIR, later desugar contract — identity must move."
    (assoc base :definition/desugar-contract-version 2)]
   [:profile-5
    "Same KIR, later profile version — identity must move."
    (assoc base :definition/profile-version 5)]
   [:interface-arity-1
    "The declared interface participates in identity."
    (assoc base :definition/interface {:arity 1 :params [:i64] :result :i64})]
   [:dependencies-two
    "Direct definition dependencies participate; their order does not."
    (assoc base :definition/dependencies [dep-b dep-a])]
   [:kir-nested-collections
    "Exercises every branch of the canonical domain: nil, bool, big int, string, keyword, symbol, vector, list, set, map."
    (assoc base :definition/kir
           {:op :do
            :forms [{:op :const :value nil}
                    {:op :const :value true}
                    ;; beyond JavaScript's exact integer range on purpose: a
                    ;; plain literal here is rounded by the cljs reader before
                    ;; any encoder runs, so it must be carried explicitly
                    {:op :const :value (ci/i64 9007199254740993)}
                    {:op :const :value (ci/i64 -9007199254740993)}
                    {:op :const :value "text"}
                    {:op :const :value :a-keyword}
                    {:op :const :value 'a-symbol}
                    {:op :vec :items [1 2 3]}
                    {:op :list :items '(1 2 3)}
                    {:op :set :items #{:x :y}}
                    {:op :map :entries {:k "v" 1 :one}}]})]
   [:kir-f64
    "f64 literals travel as exact IEEE-754 bits, never as a platform float."
    (assoc base :definition/kir {:op :const :type :f64 :value (ci/f64 1.5)})]
   [:kir-keyword-vs-string
    "A keyword and the string of the same name are distinct identity inputs."
    (assoc base :definition/kir {:op :pair :a :name :b "name"})]])

(def vectors
  (mapv (fn [[id note definition]]
          {:id id :note note :definition definition
           :canonical-hex (ci/canonical-hex definition)
           :definition-cid (cid definition)})
        vector-cases))

(let [cids (map :definition-cid vectors)]
  (assert (= (count cids) (count (set cids)))
          (str "vectors collide: " (pr-str (frequencies cids)))))

;; ---------------------------------------------------------------------------
;; CI2 / CI3 conformance fixtures
;; ---------------------------------------------------------------------------

(def pure-cid (cid base))

(defn registry-record [overrides]
  (merge {:registry/name "acme/lib"
          :registry/version "1.0.0"
          :registry/repo-rid dep-a
          :registry/commit "8f14e45fceea167a5a36dedd4bea2543471f6a1f"
          :registry/tree-cid dep-a
          :registry/manifest-cid dep-b
          :registry/signers ["did:key:z6Mkacme"]
          :registry/capabilities []}
         overrides))

(defn lock-with [cids]
  {:kotoba.lock/version 1
   :deps [{:dep/name "acme/lib" :dep/definition-cids (vec (sort cids))}]})

(defn entry [definition claimed-cid]
  {:dep/name "acme/lib" :definition definition :definition-cid claimed-cid})

;; A definition that differs from `base` only in the named canonical input.
;; Each is presented under `base`'s locked identity, so the rejection proves
;; that input is genuinely sealed rather than merely documented.
(def substitutions
  {:profile      (assoc base :definition/profile-version 5)
   :interface    (assoc base :definition/interface {:arity 1 :params [:i64] :result :i64})
   :dependency   (assoc base :definition/dependencies [dep-a])
   :effect-row   (assoc base :definition/effect-row #{:host/http})
   :desugar      (assoc base :definition/desugar-contract-version 2)
   :body         (assoc base :definition/kir {:op :const :value 99})})

(def fixtures
  (concat
   ;; ---- CI2 positive ----
   [{:file "positive/identity_pure_const.edn"
     :case {:id :positive-identity-pure-const :kind :accept :type :identity
            :expected-cid pure-cid}
     :data base}

    {:file "positive/alias_resolves_to_definition_cids.edn"
     :case {:id :positive-alias-resolves :kind :accept :type :alias
            :expected-definition-cids (vec (sort [pure-cid dep-a]))}
     :data {:registry [(registry-record {:registry/definition-cids [dep-a pure-cid]})]
            :request {:dep/name "acme/lib" :dep/version "1.0.0"}}}

    {:file "positive/locked_definition_admitted.edn"
     :case {:id :positive-locked-definition-admitted :kind :accept :type :admission}
     :data {:lock (lock-with [pure-cid])
            :resolved [(entry base pure-cid)]}}]

   ;; ---- CI3 negative: one per sealed canonical input ----
   (for [[label definition] substitutions]
     {:file (str "negative/" (str/replace (name label) "-" "_") "_mismatch.edn")
      :case {:id (keyword (str "negative-" (name label) "-mismatch"))
             :kind :reject :type :admission
             :expected-reason :definition/hash-mismatch}
      :data {:lock (lock-with [pure-cid])
             :resolved [(entry definition pure-cid)]}})

   ;; ---- CI3 negative: the linking rules themselves ----
   [{:file "negative/unknown_dependency.edn"
     :case {:id :negative-unknown-dependency :kind :reject :type :admission
            :expected-reason :definition/unknown-dependency}
     :data {:lock {:kotoba.lock/version 1 :deps []}
            :resolved [(entry base pure-cid)]}}

    {:file "negative/not_locked.edn"
     :case {:id :negative-not-locked :kind :reject :type :admission
            :expected-reason :definition/not-locked}
     :data {:lock {:kotoba.lock/version 1
                   :deps [{:dep/name "acme/lib" :dep/definition-cids [dep-a]}]}
            :resolved [(entry base pure-cid)]}}

    {:file "negative/cid_invalid.edn"
     :case {:id :negative-cid-invalid :kind :reject :type :admission
            :expected-reason :definition/cid-invalid}
     :data {:lock (lock-with [pure-cid])
            :resolved [(entry base "acme/lib@1.0.0")]}}

    ;; The mutable-name fallback, reached by omission: the lock pins an
    ;; identity and the build simply never resolves it.
    {:file "negative/unresolved_dependency.edn"
     :case {:id :negative-unresolved-dependency :kind :reject :type :admission
            :expected-reason :definition/unresolved-dependency}
     :data {:lock (lock-with [pure-cid])
            :resolved []}}

    ;; Partial resolution: two definitions pinned, one presented.
    {:file "negative/unresolved_definition.edn"
     :case {:id :negative-unresolved-definition :kind :reject :type :admission
            :expected-reason :definition/unresolved-definition}
     :data {:lock (lock-with [pure-cid dep-a])
            :resolved [(entry base pure-cid)]}}]

   ;; ---- CI3 negative: identities that must not be computed at all ----
   [{:file "negative/missing_effect_row.edn"
     :case {:id :negative-missing-effect-row :kind :reject :type :identity}
     :data (dissoc base :definition/effect-row)}

    {:file "negative/raw_float_in_kir.edn"
     :case {:id :negative-raw-float :kind :reject :type :identity
            :expected-problem :definition/unencodable-float}
     :data (assoc base :definition/kir {:op :const :value 1.5})}

    {:file "negative/dependency_not_a_cid.edn"
     :case {:id :negative-dependency-not-a-cid :kind :reject :type :identity}
     :data (assoc base :definition/dependencies ["acme/lib@1.0.0"])}]

   ;; ---- CI3 negative: registry refuses an unenforceable pin ----
   [{:file "negative/registry_definition_cids_invalid.edn"
     :case {:id :negative-registry-definition-cids-invalid :kind :reject :type :alias
            :expected-problem :registry/definition-cids-invalid}
     :data {:registry [(registry-record {:registry/definition-cids ["not-a-cid"]})]
            :request {:dep/name "acme/lib" :dep/version "1.0.0"}}}]))

;; ---------------------------------------------------------------------------
;; emit
;; ---------------------------------------------------------------------------

(defn- write! [path content]
  (io/make-parents (io/file path))
  (spit path content))

(write! "lang/code-identity-vectors.edn"
        (str ";; kotoba.lang.code-identity — frozen definition-CID test vectors (CI1).\n"
             ";;\n"
             ";; CI1's admission rule is \"byte-for-byte deterministic identity\". These\n"
             ";; vectors are that rule made checkable: kotoba.lang.code-identity-test\n"
             ";; recomputes every :canonical-hex and :definition-cid below and fails if\n"
             ";; any one moves. They are also the cross-implementation reference for CI6.\n"
             ";;\n"
             ";; DO NOT hand-edit a hash to make a test pass. A changed identity means\n"
             ";; either a real semantic change (bump :payload-version and regenerate) or\n"
             ";; a bug in the canonical encoding.\n"
             ";;\n"
             ";; Regenerate:  clojure -M -e \"(load-file \\\"scripts/gen_code_identity_fixtures.clj\\\")\"\n\n"
             "{:kotoba.lang.code-identity-vectors/version 1\n"
             " :payload-version " ci/payload-version "\n"
             " :codec :dag-cbor\n"
             " :multihash :sha2-256\n"
             " :vectors\n"
             " [\n"
             (str/join "\n\n"
                       (map (fn [v]
                              (str "  {:id " (pr-str (:id v)) "\n"
                                   "   :note " (pr-str (:note v)) "\n"
                                   "   :definition " (pr-str (:definition v)) "\n"
                                   "   :canonical-hex " (pr-str (:canonical-hex v)) "\n"
                                   "   :definition-cid " (pr-str (:definition-cid v)) "}"))
                            vectors))
             "]}\n"))

(doseq [{:keys [file data]} fixtures]
  (write! (str "lang/code-identity-conformance/" file) (str (pr-str data) "\n")))

(write! "lang/code-identity-conformance/manifest.edn"
        (str ";; CI2 (positive) and CI3 (negative) conformance cases for definition\n"
             ";; identity. The manifest — not a test body — is the list of properties\n"
             ";; that must hold; kotoba.lang.code-identity-test walks every case.\n"
             ";;\n"
             ";; Generated by scripts/gen_code_identity_fixtures.clj. Every CID is\n"
             ";; computed from the definition beside it.\n\n"
             "{:kotoba.lang.code-identity.conformance/version 1\n"
             " :cases\n"
             " [\n"
             (str/join "\n"
                       (map (fn [{:keys [file case]}]
                              (str "  " (pr-str (assoc case :file file))))
                            fixtures))
             "]}\n"))

(println "wrote" (count vectors) "vectors and" (count fixtures) "conformance fixtures")
