(ns aiueos.sbom
  "Release supply-chain attestations: an SBOM and SLSA-style provenance for a
  built artifact.

  `aiueos.deployment-profile` has required `:sbom-digest` and
  `:provenance-digest` from `:regulated` evidence since it was written, and
  `kotoba.security.supply-chain/evaluate-attestations` has been able to judge
  those documents for just as long. Nothing produced them: the only values that
  ever reached the checks were the strings `\"sha256:sbom\"` and
  `\"sha256:provenance\"` in a test. A control whose evidence is a placeholder
  is not a control.

  The components come from `qualification/tcb-inventory.edn` rather than from a
  second scan. That inventory is already content-addressed in all three of its
  halves — in-repository sources, declared dependencies, and the loaded
  classpath closure — which is exactly what an SBOM component list is. Deriving
  the SBOM from it means the two cannot disagree, and the inventory's own
  fail-closed drift check is what keeps the SBOM honest.

  Signing is Ed25519 over the canonical document form owned by
  `aiueos.key-lifecycle`, so an attestation and a lifecycle bundle sign the
  same way. The production key stays offline; `-main` emits unsigned documents
  and the caller signs them, which is the same split the release-receipt
  signing already uses."
  (:require [aiueos.key-lifecycle :as key-lifecycle]
            [aiueos.tcb :as tcb]
            [clojure.java.io :as io])
  (:import [java.security MessageDigest]))

(def sbom-version 1)
(def provenance-version 1)
(def generator-path "src/aiueos/sbom.clj")

(defn- sha256-ref
  "`sha256:<hex>` — the reference form every consumer of these documents
  expects (`supply-chain/digest?`, `deployment-profile/sha256-ref?`)."
  [hex]
  (str "sha256:" hex))

(defn file-digest [path]
  (sha256-ref (tcb/sha256-file (io/file path))))

(defn document-digest [document signature-key]
  (sha256-ref (key-lifecycle/document-digest document signature-key)))

(defn- text-digest [^String text]
  (sha256-ref
   (apply str
          (map #(format "%02x" (bit-and (int %) 0xff))
               (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes text "UTF-8"))))))

(defn components
  "The SBOM component list, derived from the TCB inventory's three halves.

  Every entry is already content-addressed there; nothing is re-hashed and no
  second discovery pass exists to drift from the first. A component the
  inventory does not know about cannot appear here, which is the point."
  ([] (components (tcb/read-inventory)))
  ([inventory]
   (into []
         (concat
          (for [{:keys [path role sha256]} (:tcb/files inventory)]
            {:component/kind :source :component/name path
             :component/role role :component/digest (sha256-ref sha256)})
          (for [{:keys [coordinate version git-sha sha256 role source assurance-gap]}
                (:tcb/external inventory)]
            (cond-> {:component/kind :dependency :component/name coordinate
                     :component/role role :component/source source}
              version (assoc :component/version version)
              sha256 (assoc :component/digest (sha256-ref sha256))
              git-sha (assoc :component/commit git-sha)
              ;; A dependency with no content address must say so here too:
              ;; an SBOM that silently omits the caveat is worse than one that
              ;; omits the component.
              assurance-gap (assoc :component/assurance-gap assurance-gap)))
          (for [{:keys [jar role scope sha256]} (:tcb/classpath inventory)]
            {:component/kind :classpath :component/name jar
             :component/role role :component/scope scope
             :component/digest (sha256-ref sha256)})))))

(defn sbom
  "An SBOM binding ARTIFACT-DIGEST and SOURCE-COMMIT to the inventory's
  components. Unsigned; `sign` adds `:sbom/signature`.

  `:sbom/generator-digest` is the digest of this file — the SBOM records what
  produced it, so a changed generator cannot quietly produce the same claim."
  [{:keys [artifact-digest source-commit inventory]}]
  (let [document {:sbom/version sbom-version
                  :sbom/artifact-digest artifact-digest
                  :sbom/subject {:commit source-commit
                                 :repository "kotoba-lang/aiueos"}
                  :sbom/generator-digest (file-digest generator-path)
                  :sbom/components (components (or inventory (tcb/read-inventory)))}]
    (assoc document :sbom/digest (document-digest document :sbom/signature))))

(defn provenance
  "SLSA-style provenance binding the artifact to its source commit, its SBOM,
  and the invocation that produced it.

  `isolated-builder?` is an assertion about the build environment that this
  namespace cannot verify, so it is required from the caller and never
  defaulted to true. Hardcoding it would make every local build claim an
  isolated builder, which is the failure mode this document exists to rule out."
  [{:keys [artifact-digest source-commit sbom-digest builder invocation
           isolated-builder?]}]
  (let [document {:provenance/version provenance-version
                  :provenance/artifact-digest artifact-digest
                  :provenance/source-commit source-commit
                  :provenance/sbom-digest sbom-digest
                  :provenance/builder builder
                  :provenance/invocation invocation
                  :provenance/invocation-digest (text-digest (pr-str invocation))
                  :provenance/isolated-builder? (true? isolated-builder?)}]
    (assoc document :provenance/digest
           (document-digest document :provenance/signature))))

(defn sign [document signature-key private-key]
  (key-lifecycle/sign-document document signature-key private-key))

(defn verify-fn
  "A `verify-sbom-signature-fn` / `verify-provenance-signature-fn` for
  `kotoba.security.supply-chain/evaluate-attestations`, bound to PUBLIC-KEY.

  `evaluate-attestations` hands the verifier the document with its signature
  key already removed, so re-signing that shape is what must match."
  [signature-key public-key]
  (fn [document signature]
    (key-lifecycle/document-signature-valid?
     (assoc document signature-key signature) signature-key public-key)))

(defn regulated-evidence
  "The supply-chain half of `aiueos.deployment-profile`'s `:regulated`
  evidence, computed rather than asserted.

  `:tcb-drift-check?` is the inventory check's real verdict, so evidence for a
  drifted tree reports false instead of claiming a check that did not pass.

  `:artifact-digest` is carried through from the SBOM so the profile can bind
  the reproduced artifact to the attested one. REPRODUCIBILITY, when given, is
  `aiueos.reproducibility/profile-evidence`; it is merged rather than computed
  here because judging two independent builds needs inputs — the second build's
  digest above all — that this namespace has no way to obtain."
  [{sbom-document :sbom provenance-document :provenance
    reproducibility :reproducibility}]
  (let [validation (tcb/validate)]
    (merge
     {:artifact-digest (:sbom/artifact-digest sbom-document)
      :sbom-digest (:sbom/digest sbom-document)
      :provenance-digest (:provenance/digest provenance-document)
      :tcb-inventory-digest (file-digest tcb/inventory-path)
      :tcb-drift-check? (:valid? validation)}
     reproducibility)))

(defn attestations
  "Both documents plus the evidence map, for a built artifact.

  REPRODUCIBILITY is optional and, when absent, the resulting evidence fails
  the regulated profile's reproducibility checks rather than omitting them —
  an unattested claim is refused, not skipped."
  [{:keys [artifact-digest source-commit builder invocation isolated-builder?
           reproducibility]}]
  (let [s (sbom {:artifact-digest artifact-digest :source-commit source-commit})
        p (provenance {:artifact-digest artifact-digest
                       :source-commit source-commit
                       :sbom-digest (:sbom/digest s)
                       :builder builder
                       :invocation invocation
                       :isolated-builder? isolated-builder?})]
    {:sbom s :provenance p
     :evidence (regulated-evidence {:sbom s :provenance p
                                    :reproducibility reproducibility})}))

(defn -main
  "Emit unsigned attestations for a built artifact.

      clojure -M:attest <artifact-digest> <source-commit> [builder] [--isolated]

  ARTIFACT-DIGEST is the release build receipt's `disk.sha256` as
  `sha256:<hex>`. It is passed in rather than parsed out of the receipt: this
  namespace is in the TCB inventory, and a hand-rolled JSON reader inside it
  would be new parsing surface for no benefit."
  [& [artifact-digest source-commit builder & flags]]
  (if-not (and artifact-digest source-commit)
    (do (println "usage: <artifact-digest> <source-commit> [builder] [--isolated]")
        (System/exit 2))
    (prn (attestations {:artifact-digest artifact-digest
                        :source-commit source-commit
                        :builder (or builder "local")
                        :invocation {:command "os/aiueos/scripts/build-release-image.sh"}
                        :isolated-builder? (boolean (some #{"--isolated"} flags))}))))
