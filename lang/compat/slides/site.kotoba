(ns slides.site
  "Per 90-docs/adr/2607022800-kotoba-lang-default-uiux-appkit-uikit-interface-fundamentals:
  the docs/ Pages shell is rendered via kotoba-ui.core/->page — the single
  paved-road SSR entry (ADR-2607122200). The library emits the whole head
  chrome (charset, viewport with viewport-fit=cover, per-scheme theme-color
  metas) and inlines the complete layered theme CSS bundle (shitsuke.hig
  tokens + liquid-glass material + shell rules — dvh/safe-area/tap-target/
  focus-visible coverage included), so none of it is hand-written here."
  (:require [clojure.java.io :as io]
            [kotoba-ui.core :as ui]
            [slides.web.ssr :as ssr]
            [slides.web.styles :as styles]))

;; Shell-level class. The page body is rendered from the same CLJC view tree
;; consumed by browser hosts. The page ships BOTH renders of the dual-render
;; contract: the SSR HTML (readable without JS) plus the browser hydration
;; bundle docs/js/main.js (slides.web.client via shadow-cljs), which mounts
;; the SAME views over reagent + re-frame from the SAME initial db and flips
;; data-kotoba-render "ssr" → "live". (This deliberately reverses the earlier
;; SSR-only/no-JS design, per the owner-approved ADR-2607122200 follow-up.)
(def page-class "slides-page")

(defn index-html []
  (str (ui/->page {:title "kotoba-lang/slides"
                   :lang "en"
                   :theme styles/theme
                   :class page-class
                   ;; The editor's UNLAYERED app-chrome rules stay an external
                   ;; stylesheet (docs/main.css) — unlayered rules win over the
                   ;; inlined layered theme bundle regardless of order. The
                   ;; hydration bundle loads deferred.
                   :head (list [:link {:rel "stylesheet" :href "./main.css"}]
                               [:script {:src "./js/main.js" :defer true}])}
                  [:div#app {:data-kotoba-render "ssr"}
                   [:hiccup/raw (ssr/root-html)]])
       "\n"))

(defn write! []
  (let [out (io/file "docs" "index.html")]
    (io/make-parents out)
    (spit out (index-html))
    out))
