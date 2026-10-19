(ns slides.routes)

(def apps
  {:slides {:slides/host "kotoba-lang.github.io/slides"
            :slides/app :slides
            :slides/title "GFTD Slides"
            :slides/capabilities #{:deck/edit :deck/publish :notes/edit}}
   :docs   {:slides/host "kotoba-lang.github.io/docs"
            :slides/app :docs
            :slides/title "GFTD Docs"
            :slides/capabilities #{:doc/edit :outline/edit :decision/log}}
   :drive  {:slides/host "kotoba-lang.github.io/drive"
            :slides/app :drive
            :slides/title "GFTD Drive"
            :slides/capabilities #{:file/store :folder/share :object/ref}}
   :sheets {:slides/host "kotoba-lang.github.io/sheets"
            :slides/app :sheets
            :slides/title "GFTD Sheets"
            :slides/capabilities #{:sheet/edit :range/query :formula/eval}}})

(def hosts
  (into {} (map (fn [[k v]] [(:slides/host v) (assoc v :slides/key k)]) apps)))

(defn resolve-host [host]
  (get hosts host))

(defn app-url [app]
  (str "https://" (get-in apps [app :slides/host]) "/"))

(defn nav []
  (->> [:slides :docs :drive :sheets]
       (mapv (fn [app]
               (let [cfg (apps app)]
                 {:href (app-url app)
                  :label (:slides/title cfg)
                  :app app})))))
