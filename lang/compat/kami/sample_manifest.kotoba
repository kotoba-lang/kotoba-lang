(ns kami.sample-manifest
  "Pure data helpers for authoring and validating the public sample catalog."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [kami.benchmark :as benchmark]))

(def schema-version 2)
(def statuses #{:concept :prototype :playable :showcase :released :archived})
(def url-keys [:sample/source-url :sample/fork-url :sample/play-url
               :sample/benchmark-url])
(def required-v2-keys
  #{:sample/schema-version :sample/capabilities :sample/status :sample/license
    :sample/source-url :sample/fork-url :sample/play-url :sample/benchmark-url})

(defn- fail [message data]
  (throw (ex-info (str "sample manifest: " message) data)))

(defn public-url?
  "True for an absolute HTTP(S) URL. Syntax only; no request is performed."
  [value]
  (boolean (and (string? value)
                (re-matches #"https?://[^\s/$.?#].[^\s]*" value))))

(defn scaffold
  "Create a latest-version manifest from identity data, merging overrides last."
  ([identity] (scaffold identity {}))
  ([identity overrides]
   (merge {:sample/schema-version schema-version
           :sample/capabilities []
           :sample/status :concept
           :sample/license "Apache-2.0"
           :sample/source-url "https://isekai.network/source"
           :sample/fork-url "https://isekai.network/fork"
           :sample/play-url "https://isekai.network/play"
           :sample/benchmark-url "https://isekai.network/benchmarks"
           :sample/metrics [:frame/fps :frame/cpu-ms :frame/gpu-ms]
           :sample/tiers
           {:playable {:target-fps 30 :budget {}}
            :showcase {:target-fps 60 :budget {}}
            :meltdown {:target-fps 60 :budget {}
                       :load {:unit :entities :initial 1 :step 1 :maximum 1}}}}
          identity overrides)))

(defn migrate-manifest
  "Upgrade a manifest to the current schema using explicit caller defaults.
  An unversioned manifest is v1. Publication URLs are never invented here."
  ([manifest] (migrate-manifest manifest {}))
  ([manifest defaults]
   (let [version (get manifest :sample/schema-version 1)]
     (cond
       (= version schema-version) manifest
       (= version 1) (merge {:sample/schema-version schema-version
                             :sample/capabilities []
                             :sample/status :prototype
                             :sample/license "UNLICENSED"}
                            defaults manifest
                            {:sample/schema-version schema-version})
       :else (fail "unsupported schema version"
                   {:version version :supported schema-version})))))

(defn valid?
  "Validate latest publication metadata and the benchmark contract."
  [manifest]
  (when-not (= schema-version (:sample/schema-version manifest))
    (fail "manifest must use the latest schema version"
          {:expected schema-version :actual (:sample/schema-version manifest)}))
  (let [missing (set/difference required-v2-keys (set (keys manifest)))]
    (when (seq missing)
      (fail "manifest is missing publication metadata" {:missing missing})))
  (when-not (and (vector? (:sample/capabilities manifest))
                 (every? keyword? (:sample/capabilities manifest))
                 (= (count (:sample/capabilities manifest))
                    (count (distinct (:sample/capabilities manifest)))))
    (fail ":sample/capabilities must be a vector of unique keywords"
          {:value (:sample/capabilities manifest)}))
  (when-not (statuses (:sample/status manifest))
    (fail "unknown :sample/status"
          {:value (:sample/status manifest) :known statuses}))
  (when-not (and (string? (:sample/license manifest))
                 (not (str/blank? (:sample/license manifest))))
    (fail ":sample/license must be a non-empty license identifier"
          {:value (:sample/license manifest)}))
  (doseq [key url-keys]
    (when-not (public-url? (get manifest key))
      (fail "publication URL must be absolute HTTP(S)"
            {:key key :value (get manifest key)})))
  (benchmark/valid-manifest? manifest)
  true)

(defn valid-matrix?
  "Validate unique fixtures and require both 2D and 3D genre coverage."
  [manifests]
  (when-not (sequential? manifests)
    (fail "matrix must be sequential" {:value manifests}))
  (doseq [manifest manifests] (valid? manifest))
  (let [ids (map :sample/id manifests)
        dimensions (group-by :sample/dimension manifests)]
    (when-not (= (count ids) (count (distinct ids)))
      (fail "matrix sample ids must be unique" {:ids ids}))
    (doseq [dimension benchmark/dimensions]
      (when-not (seq (get dimensions dimension))
        (fail "matrix must cover every dimension" {:missing dimension})))
    true))
