(ns aiueos.entropy
  "Qualified host entropy boundary for the :random/bytes capability.

  Uses the JDK strong SecureRandom selection (normally an OS-backed DRBG or
  blocking native source), exposes provider identity for deployment evidence,
  and applies continuous duplicate, repetition-count and adaptive-proportion
  health checks before bytes reach guest memory."
  (:import [java.security MessageDigest SecureRandom Security]))

(def max-request-bytes 4096)
(def ^:private repetition-cutoff 32)
(def ^:private adaptive-window-bytes 256)
(def ^:private adaptive-symbol-cutoff 32)

(defonce ^:private strong-source
  (delay (SecureRandom/getInstanceStrong)))

(defonce ^:private health-state
  (atom {:last-digest nil :samples 0}))

(defn- hex [^bytes value]
  (apply str (map #(format "%02x" (bit-and (int %) 0xff)) value)))

(defn- digest [^bytes value]
  (hex (.digest (MessageDigest/getInstance "SHA-256") value)))

(defn health-violations
  "Deterministic online health checks over one sample. These detect gross
  source failure; they are not a statistical proof of randomness."
  [^bytes sample]
  (let [values (mapv #(bit-and (int %) 0xff) sample)
        longest-run
        (reduce (fn [[longest current previous] value]
                  (let [current (if (= value previous) (inc current) 1)]
                    [(max longest current) current value]))
                [0 0 nil]
                values)
        max-frequency (when (>= (count values) adaptive-window-bytes)
                        (apply max (vals (frequencies
                                         (take adaptive-window-bytes values)))))]
    (cond-> []
      (zero? (count values)) (conj :empty-sample)
      (and (>= (count values) repetition-cutoff)
           (= 1 (count (set values))))
      (conj :stuck-source)
      (> (first longest-run) repetition-cutoff)
      (conj :repetition-count)
      (and max-frequency (> max-frequency adaptive-symbol-cutoff))
      (conj :adaptive-proportion))))

(defn assess-sample!
  "Apply health checks and continuous full-block duplicate detection using
  caller-owned STATE. Returns SAMPLE or throws before release."
  [state ^bytes sample]
  (locking state
    (let [sample-digest (digest sample)
          duplicate? (and (>= (alength sample) 16)
                          (= sample-digest (:last-digest @state)))
          violations (cond-> (health-violations sample)
                       duplicate? (conj :continuous-duplicate))]
      (when (seq violations)
        (throw
         (ex-info "entropy provider health test failed"
                  {:aiueos.entropy/health-failure
                   {:violations violations
                    :sample-bytes (alength sample)}})))
      (swap! state assoc
             :last-digest sample-digest
             :samples (inc (:samples @state)))
      sample)))

(defn provider-attestation
  "Runtime identity of the strong provider selected by this JVM."
  []
  (let [^SecureRandom source @strong-source
        provider (.getProvider source)]
    {:source :os-strong
     :algorithm (.getAlgorithm source)
     :provider (.getName provider)
     :provider-version (str (.getVersionStr provider))
     :strong-algorithms (Security/getProperty "securerandom.strongAlgorithms")
     :health-tests #{:continuous-duplicate
                     :repetition-count
                     :adaptive-proportion}}))

(defn random-bytes
  "Return N health-checked strong random bytes. Bounds are validated before
  allocation and no failed sample is returned."
  [n]
  (when-not (and (integer? n) (pos? n) (<= n max-request-bytes))
    (throw (ex-info "secure entropy request out of bounds"
                    {:aiueos.execute/entropy-denied
                     {:requested n :minimum 1 :maximum max-request-bytes}})))
  (let [sample (byte-array n)]
    (.nextBytes ^SecureRandom @strong-source sample)
    (assess-sample! health-state sample)))
