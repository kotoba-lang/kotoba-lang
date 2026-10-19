(ns kotoba.compiler.artifact
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]))

(defn- canonical [x]
  (cond
    (map? x) (into (sorted-map-by #(compare (pr-str %1) (pr-str %2)))
                   (map (fn [[k v]] [k (canonical v)])) x)
    (set? x) (vec (sort-by pr-str (map canonical x)))
    (vector? x) (mapv canonical x)
    (sequential? x) (mapv canonical x)
    :else x))

(defn sha256 [value]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes (pr-str (canonical value)) StandardCharsets/UTF_8))]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) digest))))

(defn canonical-bytes [value]
  (.getBytes (pr-str (canonical value)) StandardCharsets/UTF_8))

(defn seal [artifact]
  (let [payload (dissoc artifact :sha256)]
    (assoc payload :sha256 (sha256 payload))))

(defn valid-seal? [artifact]
  (and (string? (:sha256 artifact))
       (MessageDigest/isEqual
        (.getBytes ^String (:sha256 artifact) StandardCharsets/US_ASCII)
        (.getBytes ^String (sha256 (dissoc artifact :sha256)) StandardCharsets/US_ASCII))))
