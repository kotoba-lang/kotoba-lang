(ns kotoba.compiler.runtime-identity
  (:require [kotoba.compiler.artifact :as artifact]
            [kotoba.compiler.target :as target]))

(def loader-source-sha256
  "Pinned identity of the reviewed POSIX native loader source."
  "b3b3361712a26e69edbaa42fec47d1290a8ebb06a0baf83f54533b9a32173fdc")

(def windows-loader-source-sha256
  "Pinned identity of the reviewed Windows native loader source.
  Updated for the WFP-based network denial added under ADR-2607182420
  (install_network_denial(), network_outbound_probe_denied(),
  network_listen_probe_denied() in tools/kexe_loader_windows.c)."
  "ebad29ac8bac522159186713f89a5392bd089d8d2d6b7574ee0ac0437fdedcb1")

(defn loader-source-for-profile [profile]
  (case (:os profile)
    :windows windows-loader-source-sha256
    (:linux :macos) loader-source-sha256
    nil))

(def ^:private fields
  #{:format :target-profile :loader-source-sha256 :loader-binary-sha256
    :compiler-binary-sha256 :compiler-version-sha256
    :assembler-binary-sha256 :linker-binary-sha256
    :compiler-resource-sha256 :system-header-closure-sha256})

(defn- sha256? [value]
  (and (string? value) (boolean (re-matches #"[0-9a-f]{64}" value))))

(defn validate! [runtime]
  (when-not (and (map? runtime)
                 (= fields (set (keys runtime)))
                 (= :kotoba.native-runtime/v6 (:format runtime))
                 (contains? (set (vals target/profiles)) (:target-profile runtime))
                 (= :native (get-in runtime [:target-profile :execution]))
                 (contains? #{:linux :macos :windows} (get-in runtime [:target-profile :os]))
                 (= (loader-source-for-profile (:target-profile runtime))
                    (:loader-source-sha256 runtime))
                 (sha256? (:loader-binary-sha256 runtime))
                 (sha256? (:compiler-binary-sha256 runtime))
                 (sha256? (:compiler-version-sha256 runtime))
                 (sha256? (:assembler-binary-sha256 runtime))
                 (sha256? (:linker-binary-sha256 runtime))
                 (sha256? (:compiler-resource-sha256 runtime))
                 (sha256? (:system-header-closure-sha256 runtime)))
    (throw (ex-info "native runtime identity rejected"
                    {:phase :runtime-identity})))
  runtime)

(defn identity-sha256 [runtime]
  (artifact/sha256 (validate! runtime)))

(defn validate-measurement! [measurement]
  (when-not (and (map? measurement)
                 (= #{:format :runtime} (set (keys measurement)))
                 (= :kotoba.runtime-measurement/v1 (:format measurement)))
    (throw (ex-info "runtime measurement schema mismatch"
                    {:phase :runtime-identity})))
  (validate! (:runtime measurement))
  measurement)

(defn admit! [runtime trust]
  (let [identity (identity-sha256 runtime)
        trusted (:trusted-runtime-sha256 trust)
        revoked (:revoked-runtime-sha256 trust #{})]
    (when (contains? revoked identity)
      (throw (ex-info "native runtime identity is revoked"
                      {:phase :trust :runtime-sha256 identity})))
    ;; Native execution is never a trust-on-first-use operation. A measured
    ;; runtime must be reviewed and explicitly provisioned before guest entry.
    (when-not (and (set? trusted) (contains? trusted identity))
      (throw (ex-info "native runtime identity is not trusted"
                      {:phase :trust :runtime-sha256 identity})))
    {:runtime-sha256 identity :trusted? true}))
