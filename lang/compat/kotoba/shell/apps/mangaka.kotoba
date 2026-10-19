(ns kotoba.shell.apps.mangaka
  "Kotoba-native local control surface for the Mangaka LangGraph/Genko loop."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.data.json :as json]))

(defn- workspace-root []
  (or (System/getenv "MANGAKA_WORKSPACE")
      (-> (io/file "../../..") .getCanonicalPath)))

(defn- artifact [relative]
  (.getCanonicalPath (io/file (workspace-root) relative)))

(def score-path
  "orgs/gftdcojp/mangaka-data/ghosthacker/resources/gpt-image-base-scenes/gh-arc0-1-p00/visual-score.edn")

(def page-path
  "orgs/gftdcojp/mangaka-data/ghosthacker/resources/gpt-image-base-scenes/gh-arc0-1-p00/page.png")

(defn- load-score []
  (try (edn/read-string (slurp (artifact score-path)))
       (catch Exception _ {:score/total 0 :score/accepted false
                           :score/critical-failures [{:axis :score :evidence "score unavailable"}]})))

(defn- node-ops [id tag attrs text children]
  (concat [[:dom/create-element id tag]]
          (map (fn [[k v]] [:dom/set-attr id k v]) attrs)
          (when text
            [[:dom/create-text (+ 10000 id) text]
             [:dom/append-child id (+ 10000 id)]])
          (map (fn [child] [:dom/append-child id child]) children)))

(defn start []
  (let [score (load-score)
        total (double (:score/total score))
        accepted? (:score/accepted score)
        critical (first (:score/critical-failures score))
        api (or (System/getenv "MANGAKA_API_BASE") "http://127.0.0.1:8088")
        run-body (fn [assistant input]
                   (json/write-str {:assistant_id assistant :input input
                                    :thread_id "kotoba-mangaka-local"}))]
    {:kotoba.app/surface-ops
     (vec
      (concat
       (node-ops 1 :main {:class "mangaka-studio"} nil [2 3 4])
       (node-ops 2 :header {} nil [20 21])
       (node-ops 20 :h1 {} "Mangaka Local Studio" [])
       (node-ops 21 :p {} "EDN → panel model → layered Genko → deterministic compose → visual score" [])
       (node-ops 3 :summary {} nil [30 31 32])
       (node-ops 30 :article {:class "liquid-glass__panel"} nil [300 301 302 303])
       (node-ops 300 :h3 {} (format "p00  %.1f / 100" total) [])
       (node-ops 301 :p {} (if accepted? "ACCEPTED" "REPAIR REQUIRED") [])
       (node-ops 302 :p {} (str "Critical: " (name (or (:axis critical) :none))) [])
       (node-ops 303 :p {} (or (:evidence critical) "No critical failure") [])
       (node-ops 31 :article {:class "liquid-glass__panel"} nil [310 311])
       (node-ops 310 :h2 {} "Canonical document" [])
       (node-ops 311 :input {:id "doc-id" :value "gh-arc0-1-p00"
                             :placeholder "document id"} nil [])
       (node-ops 32 :article {:class "liquid-glass__panel"} nil [320 321 322 323 324 325])
       (node-ops 320 :h2 {} "Quality loop" [])
       (node-ops 321 :button {:class "liquid-glass__button"
                              :data-action "mangaka/generate-page"
                              :data-input-id "doc-id"
                              :data-endpoint (str api "/runs")
                              :data-body (run-body "ai.gftd.mangaka.mangakaGeneratePage"
                                                   {:doc-id "$value"})}
                 "Compose page" [])
       (node-ops 322 :button {:class "liquid-glass__button"
                              :data-action "mangaka/review-page" :data-input-id "doc-id"
                              :data-endpoint (str api "/runs")
                              :data-body (run-body "ai.gftd.mangaka.reviewPage"
                                                   {:doc-id "$value" :threshold 75})}
                 "Visual review" [])
       (node-ops 323 :button {:class "liquid-glass__button"
                              :data-action "mangaka/export-genko" :data-input-id "doc-id"
                              :data-endpoint (str api "/runs")
                              :data-body (run-body "ai.gftd.mangaka.exportGenko"
                                                   {:doc-id "$value"})}
                 "Export layered Genko" [])
       (node-ops 324 :button {:class "liquid-glass__button"
                              :data-action "mangaka/generate-panel" :data-input-id "doc-id"
                              :data-endpoint (str api "/runs")
                              :data-body (run-body "ai.gftd.mangaka.mangakaGeneratePanelQuality"
                                                   {:doc-id "$value" :panel-index 0})}
                 "Generate panel 1 candidates" [])
       (node-ops 325 :p {} "Axes below threshold route panel, layout, or lettering independently." [])
       (node-ops 4 :section {:class "liquid-glass__panel"} nil [40 41])
       (node-ops 40 :h2 {} "Latest composed page" [])
       (node-ops 41 :img {:src (artifact page-path)
                          :alt "Ghost Hacker p00 composed manga page"}
                 nil [])
       [[:dom/set-root 1]]))}))
