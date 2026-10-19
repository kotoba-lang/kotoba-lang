(ns nameserver.domain-verification
  "Portable DNS TXT ownership-challenge contract for SaaS tenant domains.
  Network lookup and token hashing remain host capabilities."
  (:require [clojure.string :as str]))

(def challenge-label "_itonami-verification")
(def value-prefix "itonami-domain-verification=")

(defn challenge-name [domain]
  (str challenge-label "." (str/replace domain #"\.$" "") "."))

(defn challenge-value [token]
  (str value-prefix token))

(defn- unquote-txt [value]
  (-> (or value "")
      (str/replace #"^\"|\"$" "")
      (str/replace #"\"\s+\"" "")))

(defn dns-json-txt-values
  "Extract TXT strings from a DNS-over-HTTPS JSON response represented as a
  Clojure map. Accepts keyword or string keys and ignores non-TXT answers."
  [response]
  (let [answers (or (:Answer response) (get response "Answer") [])]
    (->> answers
         (filter #(= 16 (or (:type %) (get % "type"))))
         (map #(unquote-txt (or (:data %) (get % "data"))))
         vec)))

(defn verified? [response token]
  (contains? (set (dns-json-txt-values response)) (challenge-value token)))

(defn doh-url [base-url domain]
  (str (str/replace base-url #"/$" "")
       "/dns-query?name=" (challenge-name domain) "&type=TXT"))

