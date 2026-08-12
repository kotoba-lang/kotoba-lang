#!/usr/bin/env bb
(ns collect-q9-soak
  (:require [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [clojure.string :as str])
  (:import [java.math BigInteger]
           [java.security KeyFactory MessageDigest Signature]
           [java.security.spec X509EncodedKeySpec]
           [java.time Duration Instant]
           [java.util Arrays Base64]))

(def path "lang/q9-wave1-tranche-1-soak.edn")
(def root-repository "com-junkawasaki/root")
(def evidence (edn/read-string (slurp path)))

(defn command! [args]
  (let [{:keys [exit out err]} @(process/process args {:out :string :err :string})]
    (when-not (zero? exit)
      (throw (ex-info "Q9 soak collection command failed"
                      {:args args :exit exit :err err})))
    (str/trim out)))

(defn gh-json [endpoint]
  (json/parse-string (command! ["gh" "api" endpoint]) true))

(defn github-file [repository revision file]
  (let [contents (gh-json (str "repos/" repository "/contents/" file "?ref=" revision))
        ;; Contents API omits :content once a file exceeds 1 MiB. The immutable
        ;; blob named by that response is the same bytes and remains available
        ;; through Git Data up to GitHub's blob limit.
        response (if (seq (:content contents))
                   contents
                   (gh-json (str "repos/" repository "/git/blobs/" (:sha contents))))]
    (when-not (and (= "base64" (:encoding response)) (seq (:content response)))
      (throw (ex-info "GitHub did not return decodable file content"
                      {:repository repository :revision revision :file file
                       :size (:size contents) :blob-sha (:sha contents)})))
    (String. (.decode (Base64/getMimeDecoder) ^String (:content response)) "UTF-8")))

(defn edn-lines [s]
  (->> (str/split-lines s)
       (remove str/blank?)
       (keep (fn [line]
               (try (edn/read-string line)
                    (catch Exception _ nil))))
       vec))

(defn artifact-paths [dir]
  [(str "src/" dir "/page_limit.kotoba")
   (str "test/" dir "/kotoba_qualification_test.clj")
   "kotoba-qualification.edn" "deps.edn"])

(defn remote-tree [github sha]
  (let [response (gh-json (str "repos/" github "/git/trees/" sha "?recursive=1"))]
    (into {} (map (juxt :path :sha) (:tree response)))))

(defn canonical-str
  [{:ci/keys [subject checks required passed outcome policy at parent]}]
  (pr-str ["fleet-ci/v1" subject
           (mapv (juxt :name :outcome) checks)
           (vec (sort required)) (vec (sort passed))
           outcome policy at parent]))

(defn sha256 [s]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes ^String s "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and 0xff %)) digest))))

(def base58-alphabet "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

(defn base58-decode [s]
  (let [n (reduce (fn [^BigInteger acc ch]
                    (let [digit (.indexOf base58-alphabet (int ch))]
                      (when (neg? digit)
                        (throw (ex-info "invalid base58btc signer" {:character ch})))
                      (.add (.multiply acc (BigInteger/valueOf 58))
                            (BigInteger/valueOf digit))))
                  BigInteger/ZERO s)
        encoded (.toByteArray n)
        magnitude (if (and (< 1 (alength encoded)) (zero? (aget encoded 0)))
                    (Arrays/copyOfRange encoded 1 (alength encoded))
                    encoded)
        leading-zeroes (count (take-while #(= \1 %) s))
        result (byte-array (+ leading-zeroes (alength magnitude)))]
    (System/arraycopy magnitude 0 result leading-zeroes (alength magnitude))
    result))

(defn hex-bytes [s]
  (when-not (and (string? s) (even? (count s)))
    (throw (ex-info "invalid hex" {:value s})))
  (byte-array
   (map (fn [i] (unchecked-byte (Integer/parseInt (subs s i (+ i 2)) 16)))
        (range 0 (count s) 2))))

(defn signer-public-key [did]
  (when-not (str/starts-with? did "did:key:z")
    (throw (ex-info "unsupported fleet signer DID" {:signer did})))
  (let [decoded (base58-decode (subs did (count "did:key:z")))]
    (when-not (and (= 34 (alength decoded))
                   (= 0xed (bit-and 0xff (aget decoded 0)))
                   (= 0x01 (bit-and 0xff (aget decoded 1))))
      (throw (ex-info "fleet signer is not an Ed25519 did:key" {:signer did})))
    (let [prefix (hex-bytes "302a300506032b6570032100")
          encoded (byte-array (+ (alength prefix) 32))]
      (System/arraycopy prefix 0 encoded 0 (alength prefix))
      (System/arraycopy decoded 2 encoded (alength prefix) 32)
      (.generatePublic (KeyFactory/getInstance "Ed25519")
                       (X509EncodedKeySpec. encoded)))))

(defn valid-signature? [signer payload signature]
  (try
    (let [verifier (Signature/getInstance "Ed25519")]
      (.initVerify verifier (signer-public-key signer))
      (.update verifier (.getBytes ^String payload "UTF-8"))
      (.verify verifier (hex-bytes signature)))
    (catch Exception _ false)))

(defn coherent-receipt? [{:keys [receipt cid signer signature]}]
  (let [passed (into #{} (comp (filter #(= :pass (:outcome %))) (map :name))
                     (:ci/checks receipt))
        outcome (if (set/subset? (:ci/required receipt) passed) :pass :fail)
        payload (canonical-str receipt)]
    (and (= cid (sha256 payload))
         (valid-signature? signer payload signature)
         (= passed (:ci/passed receipt))
         (= outcome (:ci/outcome receipt)))))

(defn matching-gate [repo sha checks]
  (let [prefix (str "test-" repo "-" (subs sha 0 7) "-murakumo-")]
    (first
     (filter (fn [{:keys [name outcome]}]
               (and (= "gate" (namespace name))
                    (str/starts-with? (clojure.core/name name) prefix)
                    (= :pass outcome)))
             checks))))

(defn reachable-from-main? [github sha]
  (let [comparison (gh-json (str "repos/" github "/compare/" sha "...main"))]
    (contains? #{"ahead" "identical"} (:status comparison))))

(defn soak-seconds [^Instant observed-at runs]
  (if-let [oldest (when (seq runs)
                    (apply min-key #(.toEpochMilli ^Instant %)
                           (map (comp #(Instant/parse %) :completed-at) runs)))]
    (.getSeconds (Duration/between oldest observed-at))
    0))

(defn collect-repository [root-sha receipts allowed-signers
                          {:keys [github dir] :as row}]
  (let [repo (last (str/split github #"/"))
        policy (get-in evidence [:requirements :policy])
        candidates
        (->> receipts
             (keep (fn [{:keys [receipt cid signer] :as envelope}]
                     (let [sha (get-in receipt [:ci/subject :tips repo])
                           gate (when (and (string? sha) (<= 7 (count sha)))
                                  (matching-gate repo sha (:ci/checks receipt)))]
                       (when (and (= policy (:ci/policy receipt))
                                  (= :pass (:ci/outcome receipt))
                                  (contains? allowed-signers signer)
                                  (coherent-receipt? envelope)
                                  gate)
                         {:receipt-cid cid
                          :root-evidence-sha root-sha
                          :signer signer
                          :signer-enrolled true
                          :signature-verified true
                          :head-sha sha
                          :policy policy
                          :outcome :pass
                          :completed-at (:ci/at receipt)
                          :gate (clojure.core/name (:name gate))}))))
             (sort-by :completed-at)
             (reduce (fn [runs run]
                       (if (some #(= (:receipt-cid %) (:receipt-cid run)) runs)
                         runs
                         (conj runs run))) []))
        runs
        (mapv (fn [{:keys [head-sha] :as run}]
                (when-not (reachable-from-main? github head-sha)
                  (throw (ex-info "fleet receipt tip is not reachable from repository main"
                                  {:repository github :head-sha head-sha})))
                (let [required (artifact-paths dir)
                      artifacts (select-keys (remote-tree github head-sha) required)]
                  (when-not (= (set required) (set (keys artifacts)))
                    (throw (ex-info "fleet receipt tip lacks Q9 qualification artifacts"
                                    {:repository github :head-sha head-sha
                                     :required required :actual (keys artifacts)})))
                  (assoc run :artifacts artifacts)))
              candidates)]
    (assoc row
           :qualified-revision (some-> runs last :head-sha)
           :qualification-artifacts (some-> runs last :artifacts)
           :runs runs)))

(let [root-sha (:sha (gh-json (str "repos/" root-repository "/commits/main")))
      receipts (edn-lines (github-file root-repository root-sha "manifest/fleet-ci.edn"))
      agents (edn-lines (github-file root-repository root-sha "manifest/fleet-agents.edn"))
      grant (get-in evidence [:requirements :signer-grant])
      allowed-signers (into #{} (comp (filter #(contains? (:grants %) grant))
                                      (map :did)) agents)
      _ (when (empty? allowed-signers)
          (throw (ex-info "no enrolled murakumo signer has the required grant"
                          {:grant grant :root-evidence-sha root-sha})))
      observed-at (Instant/now)
      repositories (mapv #(collect-repository root-sha receipts allowed-signers %)
                         (:repositories evidence))
      min-runs (apply min (map (comp count :runs) repositories))
      min-soak (apply min (map #(soak-seconds observed-at (:runs %)) repositories))
      updated (-> evidence
                  (assoc :as-of (str observed-at))
                  (assoc :repositories repositories)
                  (assoc :gate {:root-evidence-sha root-sha
                                :actual-green-fleet-receipts-per-repository min-runs
                                :soak-seconds min-soak
                                :ready false
                                :consumer-cutover-authorized false}))]
  (spit path (with-out-str (pprint/pprint updated)))
  (println "Q9 MURAKUMO SOAK EVIDENCE COLLECTED; authority gate remains closed"))
