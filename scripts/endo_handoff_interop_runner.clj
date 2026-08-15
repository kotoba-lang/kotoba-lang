(ns endo-handoff-interop-runner
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- executable []
  (if (str/starts-with? (str/lower-case (System/getProperty "os.name"))
                        "windows")
    "npx.cmd"
    "npx"))

(defn -main [& _]
  (let [command [(executable) "--no-install" "nbb"
                 "--classpath" (System/getProperty "java.class.path")
                 "scripts/endo_handoff_interop.cljs"]
        process (-> (ProcessBuilder. command)
                    (.directory (io/file "."))
                    (.inheritIO)
                    (.start))
        exit (.waitFor process)]
    (when-not (zero? exit)
      (throw (ex-info "Endo handoff interoperability failed"
                      {:problem :captp/endo-interop-failed :exit exit})))))
