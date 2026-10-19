(ns kami.json
  "A tiny, dependency-free pretty JSON emitter — the shared serializer under the JSON-shaped kami.*
   formats (kami.otio, kami.gltf). `.cljc`, so the same code runs on bb/JVM and shadow-cljs without
   pulling cheshire (JVM-only). Maps → objects (keyword keys become strings), vectors/seqs → arrays,
   keywords → strings; 2-space indented for deterministic golden output and clean diffs."
  (:require [clojure.string :as str]))

(defn- esc [s]
  ;; JSON requires the C0 control chars to be escaped; \b \f \n \r \t cover the ones that occur in
  ;; practice. Backslash first so later replacements don't double-escape. cljc-portable (no \uXXXX).
  (-> (str s)
      (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")
      (str/replace "\b" "\\b")  (str/replace "\f" "\\f")
      (str/replace "\n" "\\n")  (str/replace "\r" "\\r") (str/replace "\t" "\\t")))

(defn- kstr [k] (if (keyword? k) (name k) (str k)))

(declare emit)
(defn- emit [x ind]
  (let [pad  (apply str (repeat ind "  "))
        pad+ (str pad "  ")]
    (cond
      (nil? x)                      "null"
      (boolean? x)                  (str x)
      (number? x)                   (str x)
      (or (string? x) (keyword? x)) (str \" (esc (kstr x)) \")
      (map? x)        (if (empty? x) "{}"
                          (str "{\n" (str/join ",\n" (for [[k v] x]
                                                       (str pad+ \" (esc (kstr k)) "\": " (emit v (inc ind)))))
                               "\n" pad "}"))
      (sequential? x) (if (empty? x) "[]"
                          (str "[\n" (str/join ",\n" (for [v x] (str pad+ (emit v (inc ind)))))
                               "\n" pad "]"))
      :else           (str \" (esc (str x)) \"))))

(defn json
  "Serialize an EDN value to a 2-space pretty JSON string."
  [x] (emit x 0))
