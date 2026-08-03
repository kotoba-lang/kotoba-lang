(ns kotoba.lang.version-policy
  "Machine-enforced language support, deprecation, and compatibility policy."
  (:require [clojure.edn :as edn])
  (:import [java.time LocalDate]
           [java.time.temporal ChronoUnit]))

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
           (not= #{:version :commit :tree :source-root :issued-at-ms :language-profile}
                 (get-in policy [:release-tags :binds]))
           (conj {:code :release/incomplete-binding})
           (let [lp (:release/language-profile policy)
                 st (get-in policy [:supported :language-profile lp :status])]
             (not= :active st))
           (conj {:code :release/language-profile-not-active
                  :language-profile (:release/language-profile policy)})
           (let [pc (:release/package-contract policy)
                 st (get-in policy [:supported :package-contract pc :status])]
             (not= :active st))
           (conj {:code :release/package-contract-not-active
                  :package-contract (:release/package-contract policy)}))
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

(defn current-profile-ids
  "Active language-profile and package-contract bound to :release/current (T10.1)."
  ([] (current-profile-ids (read-policy)))
  ([policy]
   {:language-profile (:release/language-profile policy)
    :package-contract (:release/package-contract policy)
    :release-version (:release/current policy)}))

(defn default-compatibility-request
  "Request map for CI gate against the checked-in current release (T10.2)."
  ([] (default-compatibility-request (read-policy)))
  ([policy]
   (merge (current-profile-ids policy)
          {:on-date (LocalDate/now)})))

(defn release-tag-envelope-template
  "Unsigned envelope skeleton for a release tag (T10.1). Caller signs."
  [policy {:keys [commit tree source-root issued-at-ms signer]}]
  (let [version (:release/current policy)
        prefix (get-in policy [:release-tags :prefix])]
    {:tag (str prefix version)
     :version version
     :language-profile (:release/language-profile policy)
     :commit commit
     :tree tree
     :source-root source-root
     :issued-at-ms issued-at-ms
     :signer signer}))

(defn -main
  "CLI (T10.2):
   clojure -M:compatibility                 ; validate policy + current release report
   clojure -M:compatibility 4 1 0.4.0       ; explicit profile package release"
  [& [profile package release]]
  (let [policy (read-policy)
        policy-result (validate-policy policy)
        report (if (and profile package release)
                 (compatibility-report
                  policy {:language-profile (parse-long profile)
                          :package-contract (parse-long package)
                          :release-version release})
                 (compatibility-report policy (default-compatibility-request policy)))]
    (prn {:policy policy-result
          :compatibility report
          :current (current-profile-ids policy)})
    (when-not (and (:valid? policy-result) (:compatible? report))
      (System/exit 1))))
