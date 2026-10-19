(ns kotoba.ekyc.export
  "Operator-facing export for an identity-verification actor.

  Renders verification records and the recognized method catalog to CSV and
  JSON for compliance/audit export. Pure data -> text: no network, no I/O.
  Never exports evidence payloads themselves (those are opaque refs held by
  a real vendor/custody adapter) -- only the structural disposition."
  (:require [clojure.string :as str]
            [kotoba.ekyc :as ekyc]))

(defn- csv-cell [v]
  (let [s (str (if (nil? v) "" v))]
    (if (re-find #"[\",\n]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- csv-row [vals] (str/join "," (map csv-cell vals)))

(defn- json-str [v]
  (-> (str (if (nil? v) "" v))
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")))

(defn- reasons-str [r] (str/join ";" (map name (:ekyc.result/reasons r))))

(defn verifications->csv
  "Export verification records to CSV. Header row + one row per record."
  [verifications]
  (str/join "\n"
    (cons (csv-row ["id" "method" "citation" "disposition" "assurance_level" "reasons"])
          (for [v verifications]
            (let [r (ekyc/validate v)
                  m (ekyc/method (:ekyc.verification/method v))]
              (csv-row [(:ekyc.verification/id v)
                        (name (:ekyc.verification/method v))
                        (or (:ekyc.method/citation m) "")
                        (name (:ekyc.result/disposition r))
                        (name (or (:ekyc.result/assurance-level r) :na))
                        (reasons-str r)]))))))

(defn methods->csv
  "Export the recognized method catalog to CSV."
  [methods]
  (str/join "\n"
    (cons (csv-row ["id" "subject" "label" "citation" "ial_approx"])
          (for [m methods]
            (csv-row [(name (:ekyc.method/id m))
                      (name (:ekyc.method/subject m))
                      (:ekyc.method/label m)
                      (:ekyc.method/citation m)
                      (name (or (:ekyc.method/ial-approx m) :na))])))))

(defn verifications->json
  "Export verification records (with computed disposition) to a JSON string."
  [verifications]
  (str "["
       (str/join ","
         (for [v verifications]
           (let [r (ekyc/validate v)]
             (str "{\"id\":\"" (json-str (:ekyc.verification/id v)) "\","
                  "\"method\":\"" (json-str (name (:ekyc.verification/method v))) "\","
                  "\"disposition\":\"" (name (:ekyc.result/disposition r)) "\","
                  "\"assurance_level\":\"" (json-str (name (or (:ekyc.result/assurance-level r) :na))) "\","
                  "\"reasons\":\"" (json-str (reasons-str r)) "\"}"))))
       "]"))

(defn methods->json
  "Export the recognized method catalog to a JSON string."
  [methods]
  (str "["
       (str/join ","
         (for [m methods]
           (str "{\"id\":\"" (json-str (name (:ekyc.method/id m))) "\","
                "\"subject\":\"" (json-str (name (:ekyc.method/subject m))) "\","
                "\"label\":\"" (json-str (:ekyc.method/label m)) "\","
                "\"citation\":\"" (json-str (:ekyc.method/citation m)) "\","
                "\"ial_approx\":\"" (json-str (name (or (:ekyc.method/ial-approx m) :na))) "\"}")))
       "]"))
