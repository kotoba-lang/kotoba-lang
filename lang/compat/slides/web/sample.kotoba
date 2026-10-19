(ns slides.web.sample
  "Pure-data sample deck for the web editor (portable .cljc)."
  (:require [slides.design :as design]
            [slides.model :as model]))

(def sample-deck
  (-> (model/deck "web-deck"
                  {:slides/title "Web generated deck"
                   :slides/width 10
                   :slides/height 5.625
                   :slides/design design/default-design})
      (model/add-slide
       (-> (model/slide "slide-1" {:slides/title "EDN to PPTX"})
           (model/add-shape {:slides/id "accent" :slides/component :accent-bar})
           (model/add-shape {:slides/id "eyebrow" :slides/component :eyebrow
                             :slides/text "KOTOBA SLIDES"})
           (model/add-shape
            {:slides/id "title" :slides/component :title
             :slides/text "EDN to editable PPTX"})
           (model/add-shape
            {:slides/id "subtitle" :slides/component :subtitle
             :slides/text "Design tokens, slide master, guides, and components are EDN."})
           (model/add-shape {:slides/id "panel" :slides/component :panel})
           (model/add-shape {:slides/id "body" :slides/component :body
                             :slides/text "Use :slides/component for reusable layout parts and :slides/text-style for typography."})))
      (model/add-slide
       (-> (model/slide "slide-2" {:slides/title "Pure data"})
           (model/add-shape
            {:slides/id "title" :slides/component :title
             :slides/text "Component-first authoring"})
           (model/add-shape
            {:slides/id "body" :slides/component :body
             :slides/text "Decks, slides, design tokens, and shapes stay readable as EDN maps."})))))
