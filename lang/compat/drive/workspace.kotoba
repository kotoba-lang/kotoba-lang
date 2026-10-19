(ns drive.workspace
  "Portable tenant Drive metadata: hierarchy, ACL, versions, trash and quota."
  (:require [clojure.string :as str]
            [drive.model :as model]))

(def permission-roles #{:owner :editor :commenter :viewer})

(defn workspace [id owner-id quota-bytes]
  {:drive.workspace/id id
   :drive.workspace/owner-id owner-id
   :drive.workspace/root-id "root"
   :drive.workspace/items
   {"root" (assoc (model/folder "root" {:drive/title "My Drive"})
                  :drive/parent-id nil :drive/trashed? false
                  :drive/permissions {owner-id :owner})}
   :drive.workspace/used-bytes 0
   :drive.workspace/share-links {}
   :drive.workspace/quota-bytes quota-bytes})

(defn item [ws id] (get-in ws [:drive.workspace/items id]))

(defn- ensure-parent [ws parent-id]
  (when-not (= :folder (:drive/kind (item ws parent-id)))
    (throw (ex-info "drive parent folder not found" {:parent-id parent-id}))))

(defn create-folder [ws id parent-id title actor-id]
  (ensure-parent ws parent-id)
  (-> ws
      (assoc-in [:drive.workspace/items id]
                (assoc (model/folder id {:drive/title title})
                       :drive/parent-id parent-id :drive/trashed? false
                       :drive/permissions {actor-id :owner}))
      (update-in [:drive.workspace/items parent-id :drive/children] conj id)))

(defn create-file [ws id parent-id attrs actor-id]
  (ensure-parent ws parent-id)
  (-> ws
      (assoc-in [:drive.workspace/items id]
                (assoc (model/file id attrs)
                       :drive/parent-id parent-id :drive/trashed? false
                       :drive/permissions {actor-id :owner}
                       :drive/versions []))
      (update-in [:drive.workspace/items parent-id :drive/children] conj id)))

(defn grant [ws item-id principal-id role]
  (when-not (contains? permission-roles role)
    (throw (ex-info "invalid drive permission role" {:role role})))
  (assoc-in ws [:drive.workspace/items item-id :drive/permissions principal-id] role))

(defn effective-role [ws item-id principal-id]
  (loop [id item-id seen #{}]
    (when (and id (not (contains? seen id)))
      (or (get-in ws [:drive.workspace/items id :drive/permissions principal-id])
          (recur (:drive/parent-id (item ws id)) (conj seen id))))))

(defn can-read? [ws item-id principal-id]
  (contains? permission-roles (effective-role ws item-id principal-id)))

(defn can-write? [ws item-id principal-id]
  (contains? #{:owner :editor} (effective-role ws item-id principal-id)))

(defn create-share-link [ws token item-id role expires-at]
  (when-not (contains? #{:viewer :commenter} role)
    (throw (ex-info "invalid public link role" {:role role})))
  (when-not (item ws item-id)
    (throw (ex-info "drive item not found" {:item-id item-id})))
  (assoc-in ws [:drive.workspace/share-links token]
            {:drive.share/token token :drive.share/item-id item-id
             :drive.share/role role :drive.share/expires-at expires-at}))

(defn revoke-share-link [ws token]
  (update ws :drive.workspace/share-links dissoc token))

(defn resolve-share-link [ws token now]
  (when-let [link (get-in ws [:drive.workspace/share-links token])]
    (when (or (nil? (:drive.share/expires-at link))
              (< now (:drive.share/expires-at link)))
      link)))

(defn add-version [ws item-id version]
  (let [size (:drive.version/size-bytes version 0)
        next-used (+ (:drive.workspace/used-bytes ws) size)]
    (when (> next-used (:drive.workspace/quota-bytes ws))
      (throw (ex-info "drive quota exceeded" {:used next-used
                                               :quota (:drive.workspace/quota-bytes ws)})))
    (-> ws
        (update-in [:drive.workspace/items item-id :drive/versions] conj version)
        (assoc-in [:drive.workspace/items item-id :drive/object-ref]
                  (:drive.version/object-ref version))
        (assoc :drive.workspace/used-bytes next-used))))

(defn trash [ws item-id]
  (assoc-in ws [:drive.workspace/items item-id :drive/trashed?] true))

(defn restore [ws item-id]
  (assoc-in ws [:drive.workspace/items item-id :drive/trashed?] false))

(defn visible-items [ws principal-id]
  (->> (:drive.workspace/items ws) vals
       (filter #(and (not (:drive/trashed? %))
                     (can-read? ws (:drive/id %) principal-id))) vec))

(defn search [ws principal-id query]
  (let [needle (str/lower-case (str (or query "")))]
    (->> (visible-items ws principal-id)
         (filter #(or (empty? needle)
                      (str/includes? (str/lower-case (str (:drive/title %))) needle)))
         (sort-by :drive/title) vec)))
