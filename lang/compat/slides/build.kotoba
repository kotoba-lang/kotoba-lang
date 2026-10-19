(ns slides.build
  (:require [clojure.java.io :as io]
            [slides.site :as site]
            [slides.web.styles :as styles]))

(defn css-release!
  "docs/main.css = the editor's UNLAYERED app-chrome rules from
  slides.web.styles (css.core EDN data) only. The kotoba-ui theme bundle
  (layer-order declaration + HIG tokens/base + liquid-glass material + shell
  structural rules, all inside `@layer kotoba.hig, kotoba.glass`) is no
  longer duplicated here — kotoba-ui.core/->page inlines it into
  docs/index.html's <head> (slides.site). Unlayered app rules always win
  over the layered library CSS regardless of order. One `:pages` entrypoint
  still emits both docs/index.html and docs/main.css (ADR-2607122200)."
  []
  (let [out (io/file "docs" "main.css")
        app (styles/static-css)]
    (io/make-parents out)
    (spit out app)
    out))

(defn pages [& _]
  (site/write!)
  (css-release!)
  nil)

(defn -main [& args]
  (apply pages args))
