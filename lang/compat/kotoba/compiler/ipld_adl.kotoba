(ns kotoba.compiler.ipld-adl
  "Synchronous io-ipld ADL capability backed by the bounded Wasmtime C engine."
  (:require [json.data-json :as json]
            [clojure.java.shell :as shell]
            [ipld.core :as ipld]
            [ipld.schema :as schema]
            [kotoba.artifact.core :as artifact]
            [kotoba.verifier.signing :as signing])
  (:import (java.nio.file Files Path Paths)
           (java.nio.file.attribute FileAttribute)))

(def engine-id "kotoba.ipld-adl-wasmtime/v1")
(def receipt-format "kotoba.ipld-adl-wasmtime-receipt/v1")
(def execution-receipt-format :kotoba.ipld-adl-execution-receipt/v1)

(def ^:private operation-code
  {:validate-representation 0 :decode 1 :encode 2 :validate-logical 3})

(defn- canonical-node-bytes!
  "Require the guest's output to be exactly what canonical DAG-CBOR would have
   written for the value it denotes.

   The source grammar is closed over byte strings, so a transform can be
   lowered perfectly faithfully and still hand back something the codec never
   produced. Decoding alone does not separate those cases: it rejects
   truncation and trailing bytes, but a non-canonical encoding decodes cleanly
   to the value it denotes -- `18 05` reads as the integer 5 -- so only
   re-encoding shows that DAG-CBOR would have written `05`.

   `ipld.schema` already refuses such an output on its own way in, so this is
   not the difference between accepting and rejecting *there*. It is the
   difference at two other places, and both were open:

   - the receipt. Signing happens here, so without this check the executor
     attests that the module produced these bytes and only afterwards does a
     consumer decide they are not a node. A receipt should not vouch for bytes
     the codec refuses.
   - every consumer that is not `ipld.schema`. The capability is a public
     entry point returning raw output bytes, and the engine path beneath it has
     no codec check at all.

   Being raised here also names the ADL as the source, which a reader throwing
   on its own cannot: that says some CBOR was bad, not whose."
  [operation output]
  (let [value (try (ipld/decode output)
                   (catch Exception error
                     (throw (ex-info "ADL output is not DAG-CBOR"
                                     {:phase :ipld-adl
                                      :code :adl-output-not-a-node
                                      :operation operation
                                      :reason (.getMessage error)}))))
        re-encoded (ipld/encode value)]
    (when-not (java.util.Arrays/equals ^bytes output ^bytes re-encoded)
      (throw (ex-info "ADL output is not canonical DAG-CBOR"
                      {:phase :ipld-adl
                       :code :adl-output-not-canonical
                       :operation operation
                       :output-bytes (alength ^bytes output)
                       :canonical-bytes (alength ^bytes re-encoded)})))
    output))

(defn- byte-array-value [value]
  (cond
    (instance? (Class/forName "[B") value) value
    (sequential? value) (byte-array (map byte value))
    :else (throw (ex-info "ADL engine requires byte input"
                          {:phase :ipld-adl :code :invalid-bytes}))))

(defn- delete-tree! [^Path directory]
  (when directory
    (doseq [name ["output.cbor" "input.cbor" "module.wasm" "runner"]]
      (Files/deleteIfExists (.resolve directory name)))
    (Files/deleteIfExists directory)))

(defn- raw-sha256 [bytes]
  (let [digest (.digest (java.security.MessageDigest/getInstance "SHA-256") bytes)]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) digest))))

(defn runner-sha256
  "Measure the exact runner artifact bytes admitted by `wasmtime-capability`."
  [runner]
  (when-not (string? runner)
    (throw (ex-info "ADL runner path required"
                    {:phase :ipld-adl :code :runner-unavailable})))
  (raw-sha256 (Files/readAllBytes (Paths/get runner (make-array String 0)))))

(def ^:private execution-receipt-fields
  #{:format :abi :engine-id :engine-version :runner-sha256 :module-cid
    :operation :input-cid :output-cid :fuel-limit :fuel-used
    :max-output-bytes :output-bytes :max-memory-pages :memory-pages
    :timeout-ms :receipt-sha256 :executor})

(defn- receipt-hash [receipt]
  (artifact/sha256 (dissoc receipt :receipt-sha256 :executor)))

(defn verify-execution-receipt!
  "Verify a signed ADL engine execution receipt against an explicit trust set."
  [receipt trust {:keys [trusted-runner-sha256] :as policy}]
  (signing/validate-trust! trust)
  (let [executor (:executor receipt)
        signer (:signer executor)
        statement {:format :kotoba.ipld-adl-execution-attestation/v1
                   :receipt-sha256 (:receipt-sha256 receipt)
                   :executor signer}]
    (when-not (and (= #{:trusted-runner-sha256} (set (keys policy)))
                   (set? trusted-runner-sha256) (seq trusted-runner-sha256)
                   (every? #(and (string? %) (re-matches #"[0-9a-f]{64}" %))
                           trusted-runner-sha256)
                   (map? receipt)
                   (= execution-receipt-fields (set (keys receipt)))
                   (= execution-receipt-format (:format receipt))
                   (= "ipld-adl-wasm-v1" (:abi receipt))
                   (= engine-id (:engine-id receipt))
                   (string? (:engine-version receipt)) (seq (:engine-version receipt))
                   (string? (:runner-sha256 receipt))
                   (re-matches #"[0-9a-f]{64}" (:runner-sha256 receipt))
                   (contains? trusted-runner-sha256 (:runner-sha256 receipt))
                   (contains? (set (keys operation-code)) (:operation receipt))
                   (every? #(and (integer? %) (<= 0 %))
                           ((juxt :fuel-limit :fuel-used :max-output-bytes :output-bytes
                                  :max-memory-pages :memory-pages :timeout-ms) receipt))
                   (<= (:fuel-used receipt) (:fuel-limit receipt))
                   (<= (:output-bytes receipt) (:max-output-bytes receipt))
                   (<= (:memory-pages receipt) (:max-memory-pages receipt))
                   (every? #(and (string? %) (.startsWith ^String % "b"))
                           ((juxt :module-cid :input-cid :output-cid) receipt))
                   (= (:receipt-sha256 receipt) (receipt-hash receipt))
                   (map? executor)
                   (= #{:signer :public-key :signature} (set (keys executor)))
                   (= signer (signing/signer-id (:public-key executor)))
                   (contains? (:trusted-signers trust) signer)
                   (not (contains? (:revoked-signers trust) signer))
                   (signing/verify-value (:public-key executor) statement
                                         (:signature executor)))
      (throw (ex-info "ADL execution receipt rejected"
                      {:phase :ipld-adl :code :invalid-execution-receipt})))
    {:verified? true :receipt-sha256 (:receipt-sha256 receipt)
     :signer signer :runner-sha256 (:runner-sha256 receipt)}))

(defn- signed-execution-receipt
  [executor-key request response runner-sha timeout-ms]
  (let [body {:format execution-receipt-format :abi "ipld-adl-wasm-v1"
              :engine-id engine-id :engine-version (:engine-version response)
              :runner-sha256 runner-sha :module-cid (:module-cid request)
              :operation (:operation request)
              :input-cid (ipld/cid (:input-bytes request))
              :output-cid (ipld/cid (:output-bytes response))
              :fuel-limit (:fuel-limit request) :fuel-used (:fuel-used response)
              :max-output-bytes (:max-output-bytes request)
              :output-bytes (alength ^bytes (:output-bytes response))
              :max-memory-pages (:max-memory-pages request)
              :memory-pages (:memory-pages response) :timeout-ms timeout-ms}
        digest (artifact/sha256 body)
        statement {:format :kotoba.ipld-adl-execution-attestation/v1
                   :receipt-sha256 digest :executor (:signer executor-key)}]
    (assoc body :receipt-sha256 digest
           :executor {:signer (:signer executor-key)
                      :public-key (:public-key executor-key)
                      :signature (signing/sign-value executor-key statement)})))

(defn- invoke-engine
  [runner-bytes runner-sha timeout-ms module-size executor-key receipt-sink
   {:keys [module-bytes module-cid operation input-bytes fuel-limit
           max-output-bytes max-memory-pages] :as request}]
  (let [directory (Files/createTempDirectory
                   "kotoba-ipld-adl-" (make-array FileAttribute 0))
        runner-path (.resolve directory "runner")
        module-path (.resolve directory "module.wasm")
        input-path (.resolve directory "input.cbor")
        output-path (.resolve directory "output.cbor")]
    (try
      (Files/write runner-path runner-bytes (make-array java.nio.file.OpenOption 0))
      (when-not (.setExecutable (.toFile runner-path) true true)
        (throw (ex-info "could not make ADL runner snapshot executable"
                        {:phase :ipld-adl :code :runner-unavailable})))
      (Files/write module-path (byte-array-value module-bytes) (make-array java.nio.file.OpenOption 0))
      (Files/write input-path (byte-array-value input-bytes) (make-array java.nio.file.OpenOption 0))
      (let [result (shell/sh (str runner-path)
                             (str module-path) (str input-path) (str output-path)
                             (str (operation-code operation)) (str fuel-limit)
                             (str max-output-bytes) (str max-memory-pages)
                             (str timeout-ms) (str module-size))
            receipt (try
                      (json/read-str (:out result) {:key-fn keyword})
                      (catch Exception _ nil))]
        (when-not (and (zero? (:exit result))
                       (= #{:format :status :engineId :engineVersion :fuelUsed
                            :memoryPages :outputBytes}
                          (set (keys receipt)))
                       (= receipt-format (:format receipt))
                       (= "ok" (:status receipt))
                       (= engine-id (:engineId receipt))
                       (and (string? (:engineVersion receipt))
                            (seq (:engineVersion receipt)))
                       (and (integer? (:fuelUsed receipt))
                            (pos? (:fuelUsed receipt))
                            (<= (:fuelUsed receipt) fuel-limit))
                       (and (integer? (:memoryPages receipt))
                            (<= 0 (:memoryPages receipt) max-memory-pages))
                       (and (integer? (:outputBytes receipt))
                            (<= 0 (:outputBytes receipt) max-output-bytes)))
          (throw (ex-info "Wasmtime ADL execution failed"
                          {:phase :ipld-adl
                           :code (keyword (or (:code receipt) "engine-failed"))})))
        (let [output (Files/readAllBytes output-path)]
          (when-not (= (alength output) (:outputBytes receipt))
            (throw (ex-info "Wasmtime ADL output receipt mismatch"
                            {:phase :ipld-adl :code :output-receipt-mismatch})))
          ;; Checked before the receipt is signed: a receipt attests that this
          ;; module produced these bytes, and it should not attest to bytes the
          ;; codec would refuse.
          (canonical-node-bytes! operation output)
          (let [response {:status :ok :engine-id engine-id :module-cid module-cid
                          :output-bytes output :fuel-used (:fuelUsed receipt)
                          :memory-pages (:memoryPages receipt)
                          :engine-version (:engineVersion receipt)}]
            (when executor-key
              (receipt-sink (signed-execution-receipt
                             executor-key request response runner-sha timeout-ms)))
            (dissoc response :engine-version))))
      (finally (delete-tree! directory)))))

(defn wasmtime-capability
  "Create an `ipld-adl-wasm-v1` capability backed by RUNNER.

  RUNNER is the binary built by `scripts/build-ipld-adl-wasmtime.cljs`. Every
  invocation uses a fresh Wasmtime Store, admits no guest imports, enforces the
  request fuel/output/memory limits in the engine, and interrupts at TIMEOUT-MS.
  MODULE-CID remains independently re-derived by io-ipld before this adapter is
  called."
  [{:keys [runner module-bytes module-cid operations timeout-ms
           executor-key receipt-sink] :as options}]
  (when-not (and (every? #{:runner :module-bytes :module-cid :operations :timeout-ms
                           :executor-key :receipt-sink}
                         (keys options))
                 (every? #(contains? options %)
                         [:runner :module-bytes :module-cid :operations :timeout-ms]))
    (throw (ex-info "invalid Wasmtime ADL capability options"
                    {:phase :ipld-adl :code :invalid-options})))
  (when-not (and (string? runner) (.canExecute (java.io.File. runner)))
    (throw (ex-info "Wasmtime ADL runner is not executable"
                    {:phase :ipld-adl :code :runner-unavailable})))
  (when-not (and (integer? timeout-ms) (pos? timeout-ms) (<= timeout-ms 60000))
    (throw (ex-info "invalid Wasmtime ADL timeout"
                    {:phase :ipld-adl :code :invalid-timeout})))
  (when-not (= (some? executor-key) (some? receipt-sink))
    (throw (ex-info "ADL receipt signing requires both executor-key and receipt-sink"
                    {:phase :ipld-adl :code :invalid-receipt-options})))
  (when (and executor-key
             (or (not (signing/valid-key? executor-key)) (not (ifn? receipt-sink))))
    (throw (ex-info "invalid ADL receipt signer or sink"
                    {:phase :ipld-adl :code :invalid-receipt-options})))
  (let [module (byte-array-value module-bytes)
        runner-bytes (Files/readAllBytes (Paths/get runner (make-array String 0)))
        runner-sha (raw-sha256 runner-bytes)
        invoke #(invoke-engine runner-bytes runner-sha timeout-ms (alength module)
                               executor-key receipt-sink %)]
    (schema/wasm-adl-capability
     {:engine-id engine-id :module-bytes module :module-cid module-cid
      :operations operations :invoke invoke})))
