(ns ao.identity
  "Durable AO identity and dormancy.

  `1 repository = 1 artificial organism` (tamaki ADR-0001). The relationship
  is bijective and the name is *derived*, never assigned — an AO cannot be
  renamed without moving the repository, which is what makes the identity
  durable rather than a label someone maintains.

  The AO persists as repository identity and history even when no process is
  running. Archiving projects it to **dormant**; it never deletes it, and a
  dormant AO remains a member of its family. That distinction matters
  operationally: 'no process running' and 'no longer exists' need different
  answers from anything reconciling a fleet."
  (:require [clojure.string :as str]))

(def id-re #"^ao:github:([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+)$")

(defn ao-id
  "`ao:github:<org>/<repo>` — derived from the repository slug."
  [org repo]
  (when (or (str/blank? (str org)) (str/blank? (str repo)))
    (throw (ex-info "AO id requires org and repo" {:org org :repo repo})))
  (str "ao:github:" org "/" repo))

(defn parse-id [s]
  (when-let [[_ org repo] (re-matches id-re (str s))]
    {:ao/org org :ao/repo repo}))

(defn valid-id? [s]
  (boolean (parse-id s)))

(def states
  "`:active` and `:dormant` are the only states. There is no `:deleted` —
  an archived repository still has history someone may need to answer for."
  #{:active :dormant})

(defn organism
  [{:keys [org repo family archived? objective]}]
  (let [id (ao-id org repo)]
    {:ao/version 1
     :ao/id id
     :ao/org org
     :ao/repo repo
     :ao/family (or family org)
     :ao/state (if archived? :dormant :active)
     :ao/objective objective}))

(defn dormant? [ao] (= :dormant (:ao/state ao)))

(defn family-members
  "Membership is a rule applied to an inventory, not a curated list — so it
  cannot drift from reality by someone forgetting to update it."
  [repos {:keys [org]}]
  (->> repos
       (filter #(= org (:org %)))
       (mapv #(organism (assoc % :family org)))))
