(ns cloud-kotoba-dds.chat
  "Portable Hiccup conversation components. IDs, labels, models and actions belong to the host.
   No authentication, persistence, network requests, billing or consent defaults.")

(defn part
  "Add a stable CSS hook without reserving a global DOM ID."
  [name attrs] (assoc attrs :data-ck-chat name))

(defn- container [tag class attrs children]
  (into [tag (assoc attrs :class (str class (when (:class attrs) (str " " (:class attrs)))))] children))

(defn workspace [attrs & children] (container :main "chat-workspace" attrs children))
(defn sidebar [attrs & children] (container :aside "chat-sidebar" attrs children))
(defn panel [attrs & children] (container :section "go-shell" attrs children))
(defn toolbar [attrs & children] (container :header "go-toolbar" attrs children))
(defn conversation [attrs & children] (container :div "go-conversation" attrs children))
(defn welcome [attrs & children] (container :div "go-welcome" attrs children))
(defn composer [attrs & children] (container :form "go-composer" attrs children))
(defn composer-actions [attrs & children] (container :div "go-compose-actions" attrs children))
(defn model-control [attrs & children] (container :div "go-model-row" attrs children))
(defn message-list [attrs]
  [:div (merge {:role "region" :tabindex 0 :data-ck-chat "messages"} attrs)])
(defn prompt [attrs]
  [:textarea (merge {:class "dads-textarea" :rows 2 :data-ck-chat "prompt"} attrs)])
(defn model-select [attrs options]
  (into [:select (merge {:class "dads-select" :data-ck-chat "model"} attrs)]
        (map (fn [{:keys [id label disabled?]}]
               [:option (cond-> {:value id} disabled? (assoc :disabled true)) label]) options)))
