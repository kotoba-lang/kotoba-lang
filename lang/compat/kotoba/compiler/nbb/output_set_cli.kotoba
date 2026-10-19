(ns kotoba.compiler.nbb.output-set-cli
  (:require [kotoba.artifact.core :as artifact]
            [kotoba.compiler.nbb.cli-support :as support]
            [kotoba.compiler.nbb.io :as io]
            [kotoba.compiler.nbb.output-admission :as admission]
            [kotoba.compiler.nbb.output-attestation :as attestation]
            [kotoba.compiler.nbb.output-set :as output-set]))

(def ^:private max-artifact-bytes (* 64 1024 1024))
(def ^:private max-metadata-bytes (* 1024 1024))

(defn- decode-utf8! [path bytes]
  (try
    (.decode (js/TextDecoder. "utf-8" #js {:fatal true}) bytes)
    (catch :default error
      (throw (ex-info "output-set member is not valid UTF-8"
                      {:phase :verify :path path} error)))))

(defn- read-metadata! [path]
  (support/parse-policy-material
   {:present? true
    :text (decode-utf8! path
                        (io/read-bounded-bytes-file path max-metadata-bytes))}))

(defn- options! [args allowed]
  (loop [remaining (drop 2 args) result {}]
    (if (empty? remaining)
      result
      (let [[flag value & tail] remaining]
        (when-not (and (contains? allowed flag) value
                       (not (.startsWith value "--"))
                       (not (contains? result flag)))
          (support/usage-error! "error: output-set options are malformed"))
        (recur tail (assoc result flag value))))))

(defn- load-output-set! [output]
  (let [provenance-output (str output ".provenance.edn")
        publication-output (str output ".publication.edn")
        artifact-bytes (io/read-bounded-bytes-file output max-artifact-bytes)
        provenance-text (decode-utf8!
                         provenance-output
                         (io/read-bounded-bytes-file provenance-output
                                                     max-metadata-bytes))
        marker (support/parse-policy-material
                {:present? true
                 :text (decode-utf8!
                        publication-output
                        (io/read-bounded-bytes-file publication-output
                                                    max-metadata-bytes))})]
    (output-set/verify! output artifact-bytes provenance-text marker)
    (let [provenance (support/parse-policy-material
                      {:present? true :text provenance-text})]
      {:output output :artifact-bytes artifact-bytes :provenance provenance
       :marker marker :provenance-output provenance-output
       :publication-output publication-output
       :admission (admission/admit! artifact-bytes provenance)})))

(defn- verify-output-set! [args]
  (let [output (second args)
        _ (when-not output
            (support/usage-error!
             "error: verify-output-set requires an artifact path"))
        _ (when (and (= 3 (count args))
                     (not (.startsWith (nth args 2) "--")))
            (support/usage-error!
             "error: verify-output-set requires exactly one artifact path"))
        options (options! args #{"--attestation" "--trust" "--now"})
        signed? (some? (get options "--attestation"))
        _ (when-not (= signed?
                       (and (some? (get options "--trust"))
                            (some? (get options "--now"))))
            (support/usage-error!
             "error: --attestation, --trust, and --now must be supplied together"))
        {:keys [marker provenance admission provenance-output publication-output]}
        (load-output-set! output)
        publisher (if signed?
                    (attestation/verify!
                     (read-metadata! (get options "--attestation"))
                     (read-metadata! (get options "--trust"))
                     marker provenance
                     (attestation/parse-epoch! (get options "--now")))
                    {:publisher-authenticated false})]
    (merge {:ok true :output-set-format output-set/format :output output
            :provenance-output provenance-output
            :publication-output publication-output}
           admission publisher)))

(defn- sign-output-set! [args]
  (let [output (second args)
        _ (when-not output
            (support/usage-error!
             "error: sign-output-set requires an artifact path"))
        options (options! args #{"--key" "--not-before" "--expires" "--output"})
        key-path (get options "--key")
        not-before (get options "--not-before")
        expires (get options "--expires")
        _ (when-not (and key-path not-before expires)
            (support/usage-error!
             "error: sign-output-set requires --key, --not-before, and --expires"))
        {:keys [marker provenance]} (load-output-set! output)
        key (read-metadata! key-path)
        envelope (attestation/sign marker provenance key
                                   (attestation/parse-epoch! not-before)
                                   (attestation/parse-epoch! expires))
        destination (or (get options "--output")
                        (str output ".attestation.edn"))]
    (io/write-text! destination (pr-str (artifact/edn-safe envelope)))
    {:ok true :format attestation/format :output destination
     :signer (get-in envelope [:statement :signer])
     :output-set-sha256 (get-in envelope [:statement :output-set-sha256])}))

(support/execute!
 #(case (first %)
    "verify-output-set" (verify-output-set! %)
    "sign-output-set" (sign-output-set! %)
    (support/usage-error! "error: output-set command is unknown")))
