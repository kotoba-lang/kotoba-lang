(ns kotoba.compiler.coverage
  (:require [clojure.java.io :as io]
            [clojure.set :as set])
  (:import [java.nio.file Files LinkOption Paths]
           [java.security MessageDigest]))

(def ^:private manifest-keys
  #{:format :dataset :population :period :observations :support :threshold-bps})
(def ^:private dataset-keys #{:name :url :retrieved-at :sha256})
(def ^:private period-keys #{:from :to})
(def ^:private observation-keys #{:platform :share-bps})
(def ^:private support-keys #{:platform :status :paths :evidence})
(def ^:private statuses #{:unsupported :experimental :preview :release})
(def ^:private paths #{:wasm :native})
(def ^:private max-dataset-bytes (* 8 1024 1024))

(defn- reject! [message data]
  (throw (ex-info message (merge {:phase :coverage} data))))

(defn- exact-keys? [value expected]
  (and (map? value) (= expected (set (keys value)))))

(defn- sha256? [value]
  (and (string? value) (boolean (re-matches #"[0-9a-f]{64}" value))))

(defn- month? [value]
  (and (string? value) (boolean (re-matches #"[0-9]{4}-(0[1-9]|1[0-2])" value))))

(defn validate! [manifest]
  (when-not (exact-keys? manifest manifest-keys)
    (reject! "coverage manifest schema rejected" {}))
  (let [{:keys [dataset period observations support threshold-bps]} manifest]
    (when-not (= :kotoba.coverage-manifest/v1 (:format manifest))
      (reject! "coverage manifest format rejected" {}))
    (when-not (and (exact-keys? dataset dataset-keys)
                   (string? (:name dataset)) (<= 1 (count (:name dataset)) 256)
                   (string? (:url dataset)) (<= 1 (count (:url dataset)) 2048)
                   (integer? (:retrieved-at dataset)) (pos? (:retrieved-at dataset))
                   (sha256? (:sha256 dataset)))
      (reject! "coverage dataset identity rejected" {}))
    (when-not (= :interactive-web (:population manifest))
      (reject! "coverage population rejected" {}))
    (when-not (and (exact-keys? period period-keys)
                   (month? (:from period)) (month? (:to period))
                   (<= (compare (:from period) (:to period)) 0))
      (reject! "coverage period rejected" {}))
    (when-not (and (integer? threshold-bps) (<= 1 threshold-bps 10000))
      (reject! "coverage threshold rejected" {}))
    (when-not (and (vector? observations) (<= 1 (count observations) 256)
                   (every? #(and (exact-keys? % observation-keys)
                                 (keyword? (:platform %))
                                 (integer? (:share-bps %))
                                 (<= 0 (:share-bps %) 10000))
                           observations)
                   (= (count observations) (count (set (map :platform observations))))
                   (= 10000 (reduce + (map :share-bps observations)))
                   (= 1 (count (filter #(= :unknown (:platform %)) observations))))
      (reject! "coverage observations rejected" {}))
    (when-not (and (vector? support) (<= (count support) 256)
                   (every? #(and (exact-keys? % support-keys)
                                 (keyword? (:platform %))
                                 (contains? statuses (:status %))
                                 (set? (:paths %)) (set/subset? (:paths %) paths)
                                 (vector? (:evidence %))
                                 (every? sha256? (:evidence %))
                                 (if (= :release (:status %))
                                   (and (seq (:paths %)) (seq (:evidence %)))
                                   (empty? (:evidence %))))
                           support)
                   (= (count support) (count (set (map :platform support))))
                   (set/subset? (set (map :platform support))
                                (set (map :platform observations))))
      (reject! "coverage support evidence rejected" {})))
  manifest)

(defn report
  ([manifest] (report manifest []))
  ([manifest verified-evidence]
   (validate! manifest)
   (when-not (and (vector? verified-evidence)
                  (every? #(and (map? %) (sha256? (:digest %))
                                (keyword? (:platform %)) (set? (:paths %)))
                          verified-evidence))
     (reject! "verified coverage evidence rejected" {}))
   (let [evidence-by-digest (into {} (map (juxt :digest identity) verified-evidence))
         shares (into {} (map (juxt :platform :share-bps) (:observations manifest)))
        unknown (get shares :unknown)
        identifiable (- 10000 unknown)
        released (filter #(= :release (:status %)) (:support manifest))
        _ (doseq [{:keys [platform paths evidence]} released
                  digest evidence]
            (let [verified (get evidence-by-digest digest)]
              (when-not (and verified (= platform (:platform verified))
                             (set/subset? paths (:paths verified)))
                (reject! "release coverage evidence is missing or mismatched"
                         {:platform platform}))))
        supported (reduce + (map #(get shares (:platform %)) released))
        coverage-bps (if (zero? identifiable) 0 (quot (* supported 10000) identifiable))]
    {:format :kotoba.coverage-report/v1
     :population (:population manifest)
     :period (:period manifest)
     :dataset-sha256 (get-in manifest [:dataset :sha256])
     :unknown-bps unknown
     :identifiable-bps identifiable
     :supported-bps supported
     :coverage-bps coverage-bps
     :threshold-bps (:threshold-bps manifest)
     :goal-met? (>= coverage-bps (:threshold-bps manifest))
     :release-platforms (set (map :platform released))})))

(defn verify-dataset! [manifest path]
  (validate! manifest)
  (when-not (and (string? path) (seq path))
    (reject! "coverage dataset path is required" {}))
  (let [file (Paths/get path (make-array String 0))]
    (when-not (Files/isRegularFile file (make-array LinkOption 0))
      (reject! "coverage dataset must be a regular file" {}))
    (let [digest (MessageDigest/getInstance "SHA-256")]
      (with-open [input (io/input-stream path)]
        (let [buffer (byte-array 8192)]
          (loop [total 0]
            (let [read (.read input buffer)]
              (when-not (neg? read)
                (let [next-total (+ total read)]
                  (when (> next-total max-dataset-bytes)
                    (reject! "coverage dataset exceeds byte limit"
                             {:limit max-dataset-bytes}))
                  (.update digest buffer 0 read)
                  (recur next-total)))))))
      (let [actual (apply str (map #(format "%02x" (bit-and (int %) 0xff))
                                   (.digest digest)))]
        (when-not (MessageDigest/isEqual (.getBytes ^String actual "US-ASCII")
                                         (.getBytes ^String (get-in manifest [:dataset :sha256])
                                                    "US-ASCII"))
          (reject! "coverage dataset digest mismatch" {}))))
  manifest))
