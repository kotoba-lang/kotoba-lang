(ns identity.directory
  "Portable organization directory: users, groups, roles and lifecycle."
  (:require [clojure.string :as str]))

(def user-statuses #{:active :suspended :deleted})
(def roles #{:super-admin :user-admin :groups-admin :billing-admin :member})

(defn directory [organization-id domain]
  {:directory/organization-id organization-id
   :directory/domain (str/lower-case domain)
   :directory/users {}
   :directory/groups {}})

(defn domain-email? [domain email]
  (and (string? email)
       (str/ends-with? (str/lower-case email) (str "@" (str/lower-case domain)))
       (> (count email) (inc (count domain)))))

(defn user [id email attrs]
  {:directory.user/id id
   :directory.user/email (some-> email str/trim str/lower-case)
   :directory.user/display-name (:display-name attrs)
   :directory.user/did (:did attrs)
   :directory.user/status (or (:status attrs) :active)
   :directory.user/roles (set (or (:roles attrs) #{:member}))})

(defn group [id email attrs]
  {:directory.group/id id
   :directory.group/email (some-> email str/trim str/lower-case)
   :directory.group/display-name (:display-name attrs)
   :directory.group/members (set (:members attrs))})

(defn valid-user? [d u]
  (and (string? (:directory.user/id u))
       (domain-email? (:directory/domain d) (:directory.user/email u))
       (contains? user-statuses (:directory.user/status u))
       (every? roles (:directory.user/roles u))))

(defn add-user [d u]
  (when-not (valid-user? d u)
    (throw (ex-info "invalid directory user" {:user u :domain (:directory/domain d)})))
  (assoc-in d [:directory/users (:directory.user/id u)] u))

(defn set-user-status [d user-id status]
  (when-not (contains? user-statuses status)
    (throw (ex-info "invalid directory user status" {:status status})))
  (if (get-in d [:directory/users user-id])
    (assoc-in d [:directory/users user-id :directory.user/status] status)
    (throw (ex-info "directory user not found" {:user-id user-id}))))

(defn assign-role [d user-id role]
  (when-not (contains? roles role)
    (throw (ex-info "invalid directory role" {:role role})))
  (if (get-in d [:directory/users user-id])
    (update-in d [:directory/users user-id :directory.user/roles] conj role)
    (throw (ex-info "directory user not found" {:user-id user-id}))))

(defn add-group [d g]
  (when-not (domain-email? (:directory/domain d) (:directory.group/email g))
    (throw (ex-info "invalid directory group" {:group g :domain (:directory/domain d)})))
  (assoc-in d [:directory/groups (:directory.group/id g)] g))

(defn add-group-member [d group-id user-id]
  (when-not (get-in d [:directory/users user-id])
    (throw (ex-info "directory user not found" {:user-id user-id})))
  (if (get-in d [:directory/groups group-id])
    (update-in d [:directory/groups group-id :directory.group/members] conj user-id)
    (throw (ex-info "directory group not found" {:group-id group-id}))))

(defn active-users [d]
  (->> (:directory/users d) vals (filter #(= :active (:directory.user/status %))) vec))

(defn license-seats [d]
  (count (active-users d)))

