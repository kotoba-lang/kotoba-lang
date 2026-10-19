(ns kotoba.release-evidence
  "L6 / F-007: safe-release evidence gate (ADR-2607180900).

  A safe release may proceed only when an evidence packet is complete:
  - package-verification receipt (verified)
  - signed guest module (S5) verified against trust
  - key-register snapshot without blocked publisher
  - optional SBOM / provenance (required unless exception-register covers them)

  Missing packet items fail closed. Exception-register entries require
  :owner and :expires (ISO date); expired exceptions do not waive."
  (:require [kotoba.package-admission :as package-admission]
            [kotoba.signed-module :as signed-module])
  (:import [java.time Instant]))

(def required-evidence-keys
  #{:package-receipt :signed-module :trust :key-register})

(def optional-with-exception
  #{:sbom :provenance})

(defn- today []
  (subs (str (Instant/now)) 0 10))

(defn- date-expired?
  [expires now]
  (and (string? expires) (string? now) (neg? (compare expires now))))

(defn exception-covers?
  "True when EXCEPTION-REGISTER has a live entry for KIND with owner+expiry."
  [exception-register kind now]
  (let [entries (or (:exceptions exception-register)
                    (when (vector? exception-register) exception-register)
                    [])]
    (boolean
     (some (fn [e]
             (and (= kind (:kind e))
                  (string? (:owner e))
                  (seq (:owner e))
                  (string? (:expires e))
                  (not (date-expired? (:expires e) now))))
           entries))))

(defn- key-register-blocks-signer?
  [key-register signer]
  (contains? (package-admission/key-register-blocked-signers
              (or key-register {}))
             signer))

(defn evaluate
  "Evaluate a release evidence packet.

  PACKET:
  {:package-receipt <receipt from package-admission>
   :signed-module <envelope from signed-module/sign>
   :trust {:trusted-signers #{did...} :revoked-signers #{...}}
   :key-register {:keys [...]}
   :sbom <map or path-identity optional>
   :provenance <map optional>
   :exception-register {:exceptions [{:kind :sbom|:provenance :owner :expires}]}
   :component-bytes <optional integrity>
   :now \"YYYY-MM-DD\"
   :require-component-cid? true}

  Returns {:ok? bool :problems [...] :evidence {...}}."
  [packet]
  (let [now (or (:now packet) (today))
        problems (atom [])
        note! (fn [p] (swap! problems conj p))
        receipt (:package-receipt packet)
        release (package-admission/safe-release-ready?
                 receipt
                 {:require-component-cid?
                  (boolean (:require-component-cid? packet true))})
        _ (when-not (:ok? release)
            (doseq [p (:problems release)]
              (note! (assoc p :evidence :package-receipt))))
        mod-result (when (:signed-module packet)
                     (signed-module/verify
                      (:signed-module packet)
                      (or (:trust packet) {})
                      {:now now
                       :component-bytes (:component-bytes packet)}))
        _ (cond
            (nil? (:signed-module packet))
            (note! {:problem :release/missing-signed-module
                    :message "signed guest module required for safe release"})

            (not (:ok? mod-result))
            (doseq [p (:problems mod-result)]
              (note! (assoc p :evidence :signed-module))))
        signer (or (:signer mod-result)
                   (get-in packet [:signed-module :statement :signer]))
        _ (when (and signer
                     (key-register-blocks-signer? (:key-register packet) signer))
            (note! {:problem :release/signer-blocked
                    :signer signer
                    :message "module signer is not active in key-register"}))
        _ (when (nil? (:key-register packet))
            (note! {:problem :release/missing-key-register
                    :message "key-register snapshot required for safe release"}))
        _ (when (nil? (:trust packet))
            (note! {:problem :release/missing-trust
                    :message "trust context required for safe release"}))
        _ (doseq [kind optional-with-exception]
            (when (nil? (get packet kind))
              (if (exception-covers? (:exception-register packet) kind now)
                nil
                (note! {:problem :release/missing-evidence
                        :kind kind
                        :message (str (name kind)
                                      " required unless exception-register waives it")}))))
        _ (when-let [ex (:exception-register packet)]
            (doseq [e (or (:exceptions ex) (when (vector? ex) ex) [])]
              (when-not (and (string? (:owner e)) (seq (:owner e))
                             (string? (:expires e)))
                (note! {:problem :release/invalid-exception
                        :entry e
                        :message "exception requires :owner and :expires"}))))]
    {:ok? (empty? @problems)
     :problems @problems
     :evidence {:package-verified? (true? (:ok? release))
                :module-verified? (true? (:ok? mod-result))
                :signer signer
                :checked-at now}}))

(defn safe-release-ready?
  "F-007 gate entry point. True only when evaluate returns :ok?"
  [packet]
  (let [r (evaluate packet)]
    {:ok? (:ok? r)
     :problems (:problems r)
     :evidence (:evidence r)}))
