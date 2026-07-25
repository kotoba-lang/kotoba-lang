(ns kotoba.lang.extension-audit
  "Machine enforcement for non-canonical files using the .kotoba extension."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.security MessageDigest]))

(def audit-path "lang/q9-kotoba-extension-audit.edn")
(def workspace-root "../../..")
(def collision-classes
  #{:legacy-schema-dsl-extension-collision
    :legacy-language-extension-collision})
(def exception-classes
  (conj collision-classes
        :canonical-candidate-unverified
        :canonical-rejected))
(def required-exception-keys
  #{:exception/type :artifact-kind :owner :canonical-admission
    :replacement-extension :migration-track})

(defn sha256-file [file]
  (with-open [input (io/input-stream file)]
    (let [digest (MessageDigest/getInstance "SHA-256")
          buffer (byte-array 16384)]
      (loop []
        (let [n (.read input buffer)]
          (when (pos? n)
            (.update digest buffer 0 n)
            (recur))))
      (apply str
             (map #(format "%02x" (bit-and (int %) 0xff))
                  (.digest digest))))))

(defn read-audit []
  (edn/read-string (slurp audit-path)))

(defn validate
  ([] (validate (read-audit)))
  ([audit]
   (let [entries (:entries audit)
         paths (mapv :path entries)
         errors
         (into []
               (concat
                (when-not (= 3 (:kotoba.lang.q9.extension-audit/version audit))
                  [{:kind :unsupported-version}])
                (when-not (= (:path-count audit) (count entries))
                  [{:kind :path-count-drift}])
                (when-not (= (count paths) (count (set paths)))
                  [{:kind :duplicate-path}])
                (mapcat
                 (fn [{:keys [path sha256 classification typed-exception]}]
                   (let [file (io/file workspace-root path)
                         exception? (contains? exception-classes classification)]
                     (cond-> []
                       (not (.isFile file))
                       (conj {:kind :missing-file :path path})
                       (and (.isFile file)
                            (not= sha256 (sha256-file file)))
                       (conj {:kind :digest-drift :path path})
                       (and exception?
                            (not= required-exception-keys
                                  (set (keys typed-exception))))
                       (conj {:kind :incomplete-typed-exception :path path})
                       (and exception?
                            (not (keyword? (:exception/type typed-exception))))
                       (conj {:kind :invalid-exception-type :path path})
                       (and exception?
                            (not= :deny
                                  (:canonical-admission typed-exception)))
                       (conj {:kind :canonical-admission-not-denied
                              :path path})
                       (and exception?
                            (not (string? (:owner typed-exception))))
                       (conj {:kind :missing-exception-owner :path path})
                       (and exception?
                            (string? (:owner typed-exception))
                            (not (.startsWith path
                                              (:owner typed-exception))))
                       (conj {:kind :owner-scope-mismatch :path path})
                       (and (not exception?) (some? typed-exception))
                       (conj {:kind :exception-on-non-collision
                              :path path}))))
                 entries)))]
     {:valid? (empty? errors)
      :paths (count entries)
      :collisions (count (filter #(contains? collision-classes
                                             (:classification %))
                                 entries))
      :typed-exceptions (count (filter #(contains? exception-classes
                                                (:classification %))
                                       entries))
      :errors errors})))

(defn canonical-path-decision
  "Return the normative path-level admission decision. Collision exceptions
   are always deny; they are not waivers that make legacy content executable."
  [audit path]
  (if-let [entry (some #(when (= path (:path %)) %) (:entries audit))]
    {:allowed? (contains? #{:canonical-verified
                           :canonical-fixture-verified}
                         (:classification entry))
     :classification (:classification entry)
     :typed-exception (:typed-exception entry)}
    {:allowed? false :classification :unregistered-kotoba-path}))

(defn -main [& _]
  (let [result (validate)]
    (prn result)
    (when-not (:valid? result)
      (System/exit 1))))
