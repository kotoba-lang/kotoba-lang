(ns nameserver.names
  "Domain-name label helpers shared by nameserver.wire and nameserver.store.
  Names are FQDN strings with a trailing dot (\"www.example.com.\"); the root
  is \".\". Zero third-party deps, portable .cljc."
  (:require [clojure.string :as str]))

(defn split-labels
  "\"www.example.com.\" -> [\"www\" \"example\" \"com\"]; \".\" or \"\" -> []."
  [name]
  (let [trimmed (if (str/ends-with? name ".") (subs name 0 (dec (count name))) name)]
    (if (str/blank? trimmed) [] (vec (str/split trimmed #"\.")))))

(defn join-labels
  "Inverse of `split-labels`; [] -> \".\"."
  [labels]
  (if (empty? labels) "." (str (str/join "." labels) ".")))

(defn parent
  "The immediate parent domain of `name` (drop the leftmost label)."
  [name]
  (join-labels (rest (split-labels name))))

(defn subdomain-of?
  "True if `name` equals `origin` or is a subdomain of it (both FQDN)."
  [name origin]
  (let [n (split-labels name) o (split-labels origin)]
    (and (>= (count n) (count o)) (= o (vec (take-last (count o) n))))))

(defn wildcard-name
  "The wildcard owner name (\"*.<parent>\") that would cover `name`."
  [name]
  (join-labels (cons "*" (rest (split-labels name)))))
