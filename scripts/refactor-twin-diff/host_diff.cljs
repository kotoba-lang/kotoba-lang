;; Host side of the diff twin differential: amu's src/kotoba/compiler/refactor/diff.cljk (nbb) prints the lines
;; probe/probe/diff_dump.kotoba prints for the twin. Usage: kbb --classpath <amu>/src host_diff.cljs <old> <new>
(ns host-diff
  (:require ["node:fs" :as fs]
            [clojure.string :as str]
            [kotoba.compiler.refactor.diff :as df]))

(defn run [a-path b-path]
  (let [old (.toString (fs/readFileSync a-path) "utf8")
        new (.toString (fs/readFileSync b-path) "utf8")
        la (df/split-lines old) lb (df/split-lines new)
        out (volatile! [])
        w (fn [s] (vswap! out conj s))]
    (w (str "S " (count la) " " (count lb)))
    (doseq [[as ae bs be] (df/regions la lb)] (w (str "R " as " " ae " " bs " " be)))
    (let [cl (df/changed-lines old new)] (w (str "CL " (:removed cl) " " (:added cl))))
    (doseq [[p ctx] [["/src/a.cljk" 3] ["x.cljk" 0] ["x.cljk" 1]]]
      (w (str "U " ctx "\n" (df/unified p old new ctx) "ENDU")))
    (println (str/join "\n" @out))))

(let [[a b] *command-line-args*] (run a b))
