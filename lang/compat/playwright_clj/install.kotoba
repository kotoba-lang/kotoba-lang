(ns playwright-clj.install
  "Install Playwright browser binaries: `clojure -M:install [chromium|firefox|webkit]`.
   Delegates to Playwright's bundled CLI (downloads the browsers it manages)."
  (:import [com.microsoft.playwright CLI]))

(defn -main [& args]
  (let [args (if (seq args) (vec args) ["install" "chromium"])
        ;; allow `:install chromium` (prepend "install") or full `:install install chromium`
        argv (if (= "install" (first args)) args (cons "install" args))]
    (println "playwright-clj: " (pr-str argv))
    (CLI/main (into-array String argv))))
