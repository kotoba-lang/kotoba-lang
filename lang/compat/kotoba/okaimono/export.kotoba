(ns kotoba.okaimono.export
  "Operator-facing export for a courier actor's order book.

  Renders orders and line items to CSV and JSON for settlement audit and
  downstream reporting. Pure data → text: no network."
  (:require [clojure.string :as str]
            [kotoba.okaimono :as ok]))

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

(defn orders->csv [orders]
  (str/join "\n"
    (cons (csv-row ["order_id" "store" "items" "total" "currency" "cod" "dispatchable" "status"])
          (for [o orders]
            (csv-row [(:okaimono/id o)
                      (:okaimono/store o)
                      (ok/item-count o)
                      (ok/total o)
                      (:okaimono/currency o)
                      (if (:okaimono/cod? o) "yes" "no")
                      (if (ok/dispatchable? o) "yes" "no")
                      (name (:okaimono/status o))])))))

(defn lines->csv [order]
  (str/join "\n"
    (cons (csv-row ["order_id" "sku" "name" "qty" "unit_price" "line_total"])
          (for [l (:okaimono/lines order)]
            (csv-row [(:okaimono/id order)
                      (:line/sku l)
                      (:line/name l)
                      (:line/qty l)
                      (:line/unit-price l)
                      (ok/line-total l)])))))

(defn orders->json [orders]
  (str "["
       (str/join ","
                 (for [o orders]
                   (str "{\"order_id\":\"" (json-str (:okaimono/id o)) "\","
                        "\"store\":\"" (json-str (:okaimono/store o)) "\","
                        "\"total\":" (ok/total o) ","
                        "\"currency\":\"" (json-str (:okaimono/currency o)) "\","
                        "\"cod\":" (if (:okaimono/cod? o) "true" "false") ","
                        "\"dispatchable\":" (if (ok/dispatchable? o) "true" "false") ","
                        "\"status\":\"" (name (:okaimono/status o)) "\"}")))
       "]"))
