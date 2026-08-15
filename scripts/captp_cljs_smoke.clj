(ns captp-cljs-smoke
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- executable []
  (if (str/starts-with? (str/lower-case (System/getProperty "os.name")) "windows")
    "npx.cmd"
    "npx"))

(defn -main [& _]
  (let [classpath (System/getProperty "java.class.path")
        command [(executable) "--no-install" "nbb" "--classpath" classpath
                 "scripts/captp_cljs_smoke.cljs"]
        process (-> (ProcessBuilder. command)
                    (.directory (io/file "."))
                    (.inheritIO)
                    (.start))
        exit (.waitFor process)]
    (when-not (zero? exit)
      (throw (ex-info "CapTP ClojureScript smoke failed"
                      {:problem :captp/cljs-smoke-failed :exit exit})))))
