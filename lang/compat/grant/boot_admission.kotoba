(ns grant.boot-admission
  "May these artifacts be booted?

  Contract: `resources/aiueos/boot_admission_contract.edn`. Pure decisions;
  hashing files is provider mechanism, as everywhere else here.

  ## The hole this namespace closes

  ADR-0048 made PID 1 load the anchor set the image carries, and named what was
  left: **nothing verifies the image.** Every link from the release manifest
  down is checked — the manifest binds the anchors artifact's digest, the
  document's pins are validated, the peer's key is measured against them — and
  the thing the hosted machine actually boots was not checked at all. A chain
  whose first link is unattached is a chain lying about its length.

  ## What this is, and what it is not

  It is: **the artifacts this launcher is about to boot are the artifacts a
  signed release manifest names.** A substituted or corrupted initramfs is
  refused before QEMU starts.

  It is not measured boot. The check runs on the host, in the launcher, so it
  is worth exactly as much as the host is. A compromised host verifies whatever
  it likes. The bare-metal profile answers this with firmware; the hosted
  profile cannot, and saying otherwise would be the kind of claim this
  repository's evidence rules exist to prevent.

  ## Authenticity, not recency

  No `:installed-sequence` is passed, so the monotonic-sequence check does not
  run. **Booting an older release is allowed on purpose**: a device that cannot
  boot an earlier image cannot be recovered, and anti-rollback belongs in the
  update path, where there is a stored sequence and a machine that is already
  running. This decides whether the bytes are authentic, not whether they are
  current."
  (:require [grant.ota :as ota]
            [grant.publisher :as publisher]))

(def required-kinds
  "Both, always. A manifest that names the initramfs while the launcher takes
  its kernel from anywhere has verified the smaller half of what executes."
  #{:kernel :initramfs})

(def deny-reasons
  "Reasons this namespace produces. `grant.publisher` reasons pass through
  unchanged."
  #{:no-release :kind-not-named :artifact-unmeasured :artifact-digest-mismatch})

(defn- deny [reason extra]
  (merge {:aiueos/decision :deny :aiueos.boot/reason reason} extra))

(defn- admit [extra]
  (merge {:aiueos/decision :grant} extra))

(defn admitted? [verdict] (= :grant (:aiueos/decision verdict)))

(defn admit-boot
  "Decide whether OBSERVED artifacts may be booted under RELEASE.

  `release`  — the signed manifest: `{:manifest-id :sequence :signatures
                :timestamp-ms :artifacts}`
  `observed` — `{kind digest}`, what the launcher actually hashed
  `state`    — `grant.publisher`'s state (root, revocation bits, now-ms)

  Unmeasured and mismatched are separate refusals. Both deny, so neither is a
  silent pass, but they are different operator problems — one is a launcher
  that did not hash something, the other is bytes that are not the bytes."
  ([release observed state] (admit-boot release observed state publisher/default-policy))
  ([release observed state policy]
   (let [named (ota/artifact-index (:artifacts release))
         missing-kinds (remove #(contains? named %) required-kinds)
         unmeasured (remove #(contains? observed %) required-kinds)]
     (cond
       (or (nil? release) (empty? named))
       (deny :no-release {})

       (seq missing-kinds)
       (deny :kind-not-named {:aiueos.boot/kinds (vec (sort missing-kinds))
                              :aiueos.boot/release-id (:manifest-id release)})

       (seq unmeasured)
       (deny :artifact-unmeasured {:aiueos.boot/kinds (vec (sort unmeasured))})

       :else
       ;; Only the artifacts this launcher boots. A manifest names more than a
       ;; launcher touches -- the anchors artifact lives *inside* the initramfs,
       ;; so the initramfs digest already covers it, and re-hashing it here
       ;; would mean opening the image to check bytes the guest checks anyway,
       ;; while presenting one measurement as two.
       (let [booted (filter #(contains? required-kinds (:kind %)) (:artifacts release))
             check (ota/verify-artifacts booted observed)]
         (if-not (:aiueos.ota/ok? check)
           (deny :artifact-digest-mismatch
                 {:aiueos.boot/mismatched (:aiueos.ota/mismatched check)
                  :aiueos.boot/missing (:aiueos.ota/missing check)
                  :aiueos.boot/release-id (:manifest-id release)})
           ;; The digests are checked above, so the publisher is asked only
           ;; about authorship. No :installed-sequence: see the ns docstring.
           (let [v (publisher/admit-release
                    {:sequence (:sequence release)
                     :signatures (:signatures release)
                     :artifact-digests-match? true
                     :timestamp-ms (:timestamp-ms release)}
                    (dissoc state :installed-sequence)
                    policy)]
             (if-not (publisher/admitted? v)
               v
               (admit {:aiueos.boot/release-id (:manifest-id release)
                       :aiueos.boot/verified (into {} (map (juxt identity observed)) required-kinds)
                       :aiueos.publisher/live (:aiueos.publisher/live v)})))))))))
