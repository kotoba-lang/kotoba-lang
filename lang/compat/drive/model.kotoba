(ns drive.model)

(def item-kinds #{:folder :file})

(defn drive
  ([id] (drive id {}))
  ([id attrs]
   (merge {:drive/id id
           :drive/type :drive
           :drive/items {}}
          attrs)))

(defn folder [id attrs]
  (merge {:drive/id id :drive/kind :folder :drive/title id :drive/children []} attrs))

(defn file [id attrs]
  (merge {:drive/id id :drive/kind :file :drive/title id :drive/object-ref nil :drive/media-type "application/octet-stream"} attrs))

(defn add-item [d item]
  (assoc-in d [:drive/items (:drive/id item)] item))

(defn item-by-id [d id]
  (get-in d [:drive/items id]))

(defn add-child [d folder-id child-id]
  (update-in d [:drive/items folder-id :drive/children] (fnil conj []) child-id))

(defn children [d folder-id]
  (mapv #(item-by-id d %) (get-in d [:drive/items folder-id :drive/children])))

(defn seed-drive []
  (-> (drive "gftd-drive")
      (add-item (folder "root" {:drive/title "GFTD Drive"}))
      (add-item (file "deck" {:drive/title "Intro deck" :drive/object-ref "cid:slides:intro-deck"}))
      (add-child "root" "deck")))
