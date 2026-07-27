(ns kotoba.lang.version-policy
  "Machine-enforced release, support, deprecation, and signed-tag policy."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [ed25519.core :as ed])
  (:import [java.time LocalDate]
           [java.time.temporal ChronoUnit]
           [java.util Base64]))

(def policy-path "lang/version-policy.edn")

(defn read-policy []
  (edn/read-string (slurp policy-path)))

(def semver-pattern
  #"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$")

(defn parse-semver [value]
  (when-let [[_ major minor patch pre build]
             (and (string? value) (re-matches semver-pattern value))]
    {:major (Long/parseLong major) :minor (Long/parseLong minor)
     :patch (Long/parseLong patch) :pre-release pre :build build
     :value value}))

(defn validate-policy
  ([] (validate-policy (read-policy)))
  ([policy]
   (let [minimum (get-in policy [:deprecation :minimum-days])
         errors
         (cond-> []
           (not= 1 (:kotoba.lang.version-policy/version policy))
           (conj {:code :policy/version})
           (nil? (parse-semver (:release/current policy)))
           (conj {:code :release/invalid-semver})
           (not (and (integer? minimum) (>= minimum 180)))
           (conj {:code :deprecation/window-too-short})
           (not= :ed25519 (get-in policy [:release-tags :algorithm]))
           (conj {:code :release/unsupported-signature})
           (not= #{:version :commit :tree :source-root :issued-at-ms}
                 (get-in policy [:release-tags :binds]))
           (conj {:code :release/incomplete-binding}))
         deprecation-errors
         (for [[axis versions] (:supported policy)
               [version entry] versions
               :when (= :deprecated (:status entry))
               :let [start (try (LocalDate/parse (:deprecated-on entry))
                                (catch Exception _ nil))
                     end (try (LocalDate/parse (:removal-not-before entry))
                              (catch Exception _ nil))]
               :when (or (nil? start) (nil? end)
                         (< (if (and start end)
                              (.between ChronoUnit/DAYS start end)
                              0)
                            minimum))]
           {:code :deprecation/invalid-window :axis axis :version version})]
     {:valid? (and (empty? errors) (empty? deprecation-errors))
      :errors (vec (concat errors deprecation-errors))})))

(defn support-decision
  [policy axis version on-date]
  (let [entry (get-in policy [:supported axis version])
        removal (some-> (:removal-not-before entry) LocalDate/parse)
        expired? (and removal (not (.isBefore on-date removal)))
        code (cond
               (nil? entry) :version/unsupported
               (= :removed (:status entry)) :version/removed
               expired? :version/deprecation-expired
               :else nil)]
    {:allowed? (nil? code) :code code :axis axis :version version
     :status (:status entry)
     :removal-not-before (:removal-not-before entry)}))

(defn compatibility-report
  "Deterministic compatibility result for a tool/artifact request."
  [policy {:keys [language-profile package-contract release-version on-date]}]
  (let [on-date (or on-date (LocalDate/now))
        checks [(support-decision policy :language-profile language-profile on-date)
                (support-decision policy :package-contract package-contract on-date)]
        semver (parse-semver release-version)
        release-check {:allowed? (some? semver)
                       :code (when-not semver :release/invalid-semver)
                       :axis :release :version release-version}
        checks (conj checks release-check)]
    {:format-version (get-in policy [:compatibility-report :format-version])
     :compatible? (every? :allowed? checks)
     :checks (vec (sort-by (comp str :axis) checks))}))

(defn canonical-tag-body [envelope]
  (pr-str (into (sorted-map) (dissoc envelope :signature))))

(defn verify-release-tag
  "Verify tag shape, v<semver>, complete content binding, signer trust/status,
   and Ed25519 signature. TRUST maps DID to {:status :active}."
  [policy trust envelope]
  (let [version (:version envelope)
        signer (:signer envelope)
        tag (:tag envelope)
        required (get-in policy [:release-tags :binds])
        body (canonical-tag-body envelope)
        signature (try (.decode (Base64/getDecoder)
                                ^String (:signature envelope))
                       (catch Exception _ nil))
        missing (remove #(contains? envelope %) required)
        code (cond
               (nil? (parse-semver version)) :tag/invalid-version
               (not= tag (str (get-in policy [:release-tags :prefix])
                              version)) :tag/name-mismatch
               (seq missing) :tag/incomplete-binding
               (not= :active (get-in trust [signer :status]))
               :tag/signer-untrusted
               (nil? signature) :tag/invalid-signature
               (not (try
                      (ed/verify-did signer (.getBytes body "UTF-8")
                                     signature)
                      (catch Exception _ false)))
               :tag/invalid-signature
               :else nil)]
    {:valid? (nil? code) :code code :tag tag :version version
     :signer signer}))

(defn -main [& [profile package release]]
  (let [policy (read-policy)
        policy-result (validate-policy policy)
        report (when (and profile package release)
                 (compatibility-report
                  policy {:language-profile (parse-long profile)
                          :package-contract (parse-long package)
                          :release-version release}))]
    (prn {:policy policy-result :compatibility report})
    (when-not (and (:valid? policy-result)
                   (or (nil? report) (:compatible? report)))
      (System/exit 1))))
