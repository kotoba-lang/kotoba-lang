(ns kotoba.cad.app
  (:require [reagent.dom.client :as rdom]
            [re-frame.core :as rf]
            [kotoba.cad.core :as core]
            [kotoba.cad.ui :as ui]))

(def starter-scene
  [{:id :house :label "Casa Amani" :kind :building :x 48 :y 42 :color "#e7b77a"}
   {:id :pool :label "Reflecting pool" :kind :water :x 67 :y 65 :color "#5fd4e6"}
   {:id :oak :label "Oak grove" :kind :vegetation :x 27 :y 43 :color "#77b96b"}
   {:id :path :label "Arrival path" :kind :landscape :x 44 :y 72 :color "#d4bd95"}])

(defn hash-cid [s]
  (str "bafy" (subs (str (hash s)) 1)))

(defn install-css! []
  (let [node (.createElement js/document "style")]
    (set! (.-textContent node) (ui/css-text))
    (.appendChild (.-head js/document) node)))

(defn download! [filename text]
  (let [blob (js/Blob. #js [text] #js {:type "application/edn"})
        url (js/URL.createObjectURL blob)
        a (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.click a)
    (js/URL.revokeObjectURL url)))

(rf/reg-event-db
 :init
 (fn [_ _]
   {:stage 0
    :artifacts []
    :runner-results []
    :runner-plan nil
    :active-panel :scene
    :selected :house
    :camera :perspective
    :time 16
    :weather :golden
    :scene starter-scene
    :presentation? false}))

(rf/reg-event-db
 :select-node
 (fn [db [_ id]] (assoc db :selected id)))

(rf/reg-event-db
 :select-camera
 (fn [db [_ camera]] (assoc db :camera camera)))

(rf/reg-event-db
 :set-time
 (fn [db [_ time]] (assoc db :time (js/parseInt time 10))))

(rf/reg-event-db
 :set-weather
 (fn [db [_ weather]] (assoc db :weather weather)))

(rf/reg-event-db
 :toggle-presentation
 (fn [db _] (update db :presentation? not)))

(rf/reg-event-db
 :add-asset
 (fn [db [_ kind]]
   (let [n (inc (count (:scene db)))
         id (keyword (str (name kind) "-" n))]
     (-> db
         (update :scene conj {:id id :label (str (name kind) " " n) :kind kind
                              :x (+ 24 (* 9 (mod n 6))) :y (+ 36 (* 7 (mod n 5)))
                              :color (case kind :vegetation "#72bb75" :light "#f9d46c" "#c5b9ff")})
         (assoc :selected id)))))

(rf/reg-event-db
 :advance
 (fn [db _]
   (update db :stage #(min (dec (count core/stages)) (inc %)))))

(rf/reg-event-db
 :audit
 (fn [db _]
   (update db :runner-results conj {:run/status :dry-run})))

(rf/reg-event-db
 :files
 (fn [db [_ files]]
   (update db :artifacts into
           (map (fn [file]
                  (let [name (.-name file)
                        artifact (core/classify-artifact name)]
                    (assoc artifact :artifact/cid (hash-cid (str name (:artifact/id artifact))))))
                files))))

(rf/reg-event-db
 :build-plan
 (fn [db _]
   (assoc db :runner-plan (core/runner-plan (:artifacts db)))))

(rf/reg-fx
 :download
 (fn [{:keys [filename body]}]
   (download! filename body)))

(rf/reg-event-fx
 :download-plan
 (fn [{:keys [db]} _]
   (let [plan (or (:runner-plan db) (core/runner-plan (:artifacts db)))]
     {:db (assoc db :runner-plan plan)
      :download {:filename "kotoba-cad-runner-plan.edn" :body (str (pr-str plan) "\n")}})))

(rf/reg-event-fx
 :download-state
 (fn [{:keys [db]} _]
   {:download {:filename "kotoba-cad-state.edn"
               :body (str (pr-str (select-keys db [:scene :selected :camera :time :weather])) "\n")}}))

(rf/reg-sub :db identity)
(rf/reg-sub :review (fn [db _] (core/co-sientist-review db (:runner-results db))))
(rf/reg-sub :coverage (fn [db _] (core/coverage-assessment db (:runner-results db))))

(defn files-event [event]
  (let [target (.-target event)]
    (rf/dispatch [:files (vec (array-seq (.-files target)))])
    (set! (.-value target) "")))

(defn handlers []
  {:advance #(rf/dispatch [:advance])
   :audit #(rf/dispatch [:audit])
   :build-plan #(rf/dispatch [:build-plan])
   :download-plan #(rf/dispatch [:download-plan])
   :download-state #(rf/dispatch [:download-state])
   :select-node #(rf/dispatch [:select-node %])
   :select-camera #(rf/dispatch [:select-camera %])
   :set-time #(rf/dispatch [:set-time (.. % -target -value)])
   :set-weather #(rf/dispatch [:set-weather %])
   :toggle-presentation #(rf/dispatch [:toggle-presentation])
   :add-asset #(rf/dispatch [:add-asset %])
   :files files-event})

(defn app-root []
  (let [db @(rf/subscribe [:db])
        review @(rf/subscribe [:review])
        coverage @(rf/subscribe [:coverage])]
    [ui/shell (assoc db :review review :coverage coverage :handlers (handlers))]))

(defonce root (atom nil))

(defn ^:dev/after-load render! []
  (when @root
    (rdom/render @root [app-root])))

(defn ^:export init []
  (install-css!)
  (rf/dispatch-sync [:init])
  (reset! root (rdom/create-root (.getElementById js/document "root")))
  (render!)
  (set! (.-__kotobaIndustrial js/window)
        #js {:dispatch rf/dispatch
             :state #(clj->js @(rf/subscribe [:db]))}))
