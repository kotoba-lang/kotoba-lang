(ns kotoba.compiler.nbb.compile-cache
  "Bounded in-memory content-addressed cache for one target-locked worker.

  Only fully admitted/emitted (and for native, independently verified)
  artifacts are inserted. Values never cross worker processes."
  (:require ["node:crypto" :as crypto]))

(defn- configured-integer [name default maximum]
  (let [text (aget js/process.env name)
        value (when text (js/Number text))]
    (if (and value (js/Number.isSafeInteger value) (<= 1 value maximum))
      value
      default)))

(defn create
  ([] (create "KOTOBA_WORKER_CACHE" 128 (* 64 1024 1024)))
  ([environment-prefix default-entries default-bytes]
   {:entries (volatile! {})
    :order (volatile! [])
    :bytes (volatile! 0)
    :max-entries (configured-integer (str environment-prefix "_ENTRIES")
                                     default-entries 4096)
    :max-bytes (configured-integer (str environment-prefix "_BYTES")
                                   default-bytes (* 1024 1024 1024))}))

(defn create-context []
  {:artifacts (create)
   :stages (create "KOTOBA_WORKER_STAGE_CACHE" 128 (* 64 1024 1024))})

(defn sha256 [value]
  (-> (.createHash crypto "sha256")
      (.update value)
      (.digest "hex")))

(defn key-for
  ;; `linked?` is in the key because it changes what the frontend ADMITS, not
  ;; how it is spelled: a linked project carries :admit-linked-synthetics?.
  ;; The rest of the frontend options are a function of the policy text, which
  ;; is already here. Version tag raised to v3, so entries written under the
  ;; old key miss rather than being read under the new meaning.
  ([target source policy-material]
   (key-for target source policy-material {} false))
  ([target source policy-material emit-metadata]
   (key-for target source policy-material emit-metadata false))
  ([target source policy-material emit-metadata linked?]
   (key-for target source policy-material emit-metadata linked? nil))
  ;; `artifact-kind` selects WHICH packaged form `--output` receives for the
  ;; aiueos targets, so two invocations differing only in `--artifact` produce
  ;; different bytes from identical source, policy and metadata. It is not part
  ;; of provenance -- packaging is not a compile input, and the JVM does not
  ;; seal it either -- but it must be part of the cache key, or the second
  ;; invocation is served the first one's answer. Version tag raised to v4 so
  ;; entries written under v3 miss rather than being read under the new meaning.
  ([target source policy-material emit-metadata linked? artifact-kind]
   (sha256
    (.stringify js/JSON
                (clj->js ["kotoba.compile-cache/v4"
                          (name target)
                          source
                          (boolean (:present? policy-material))
                          (:text policy-material)
                          (pr-str emit-metadata)
                          (boolean linked?)
                          artifact-kind])))))

(defn stage-key-for [stage material]
  (sha256
   (.stringify js/JSON
               (clj->js ["kotoba.stage-cache/v1" (name stage) material]))))

(defn- without [items value]
  (into [] (remove #(= value %)) items))

(defn lookup! [cache key]
  (when-let [entry (get @(:entries cache) key)]
    (vreset! (:order cache) (conj (without @(:order cache) key) key))
    (:value entry)))

(defn remove! [cache key]
  (when-let [entry (get @(:entries cache) key)]
    (vswap! (:entries cache) dissoc key)
    (vreset! (:order cache) (without @(:order cache) key))
    (vswap! (:bytes cache) - (:size entry)))
  nil)

(defn- evict! [cache]
  (loop []
    (when (or (> (count @(:entries cache)) (:max-entries cache))
              (> @(:bytes cache) (:max-bytes cache)))
      (when-let [oldest (first @(:order cache))]
        (remove! cache oldest)
        (recur)))))

(defn put! [cache key value size]
  (when (and (nat-int? size) (<= size (:max-bytes cache)))
    (remove! cache key)
    (vswap! (:entries cache) assoc key {:value value :size size})
    (vswap! (:order cache) conj key)
    (vswap! (:bytes cache) + size)
    (evict! cache))
  value)

(defn lookup-stage! [cache key]
  (when-let [entry (lookup! cache key)]
    (let [serialized (pr-str (:value entry))]
      (if (= (:sha256 entry) (sha256 serialized))
        (:value entry)
        (do
          (remove! cache key)
          (throw (ex-info "compiler stage cache integrity mismatch"
                          {:cache-key key})))))))

(defn put-stage! [cache key value]
  (let [serialized (pr-str value)]
    (put! cache key
          {:value value :sha256 (sha256 serialized)}
          (.byteLength js/Buffer serialized "utf8")))
  value)

(defn resolve-stage! [cache stage material compute]
  (if-not cache
    {:value (compute) :cache nil}
    (let [key (stage-key-for stage material)]
      (if-let [value (lookup-stage! cache key)]
        {:value value :cache :hit :cache-key key}
        (let [value (compute)]
          (put-stage! cache key value)
          {:value value :cache :miss :cache-key key})))))

(defn stats [cache]
  {:entries (count @(:entries cache))
   :bytes @(:bytes cache)
   :max-entries (:max-entries cache)
   :max-bytes (:max-bytes cache)})
