#!/usr/bin/env bb
(ns apply-q9-page-limit-pilots
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(def workspace (-> "../../.." fs/canonicalize str))

(def pilots
  [{:repo "com-aave" :dir "aave" :oracle-ns "aave.main"}
   {:repo "com-abb-robotics" :dir "abb_robotics" :oracle-ns "abb_robotics.main"}
   {:repo "com-accela" :dir "accela" :oracle-ns "accela.main"}
   {:repo "com-accelbyte" :dir "accelbyte" :oracle-ns "accelbyte.main"}
   {:repo "com-ach-network" :dir "ach_network" :oracle-ns "ach_network.main"}
   {:repo "com-acorrd-standards" :dir "acorrd_standards" :oracle-ns "acorrd-standards.main"}
   {:repo "com-acquia" :dir "acquia_compat" :oracle-ns "acquia-compat.main"}
   {:repo "com-adobe" :dir "adobe_compat" :oracle-ns "adobe-compat.main"}
   {:repo "com-aftership" :dir "aftership" :oracle-ns "aftership.main"}])

(def coerce-snippet
  "(defn coerce-field [kind v]\n  (case kind :int (as-int v) :float (as-float v) :bool (as-bool v) v))")

(def oracle-snippet
  (str coerce-snippet
       "\n\n(defn page-limit\n"
       "  \"Bound a requested page size. Retained CLJC oracle for page_limit.kotoba.\"\n"
       "  [requested]\n"
       "  (if (pos? requested)\n"
       "    (min requested max-limit)\n"
       "    default-limit))"))

(def old-limit
  "(min (max (or (let [l (as-int (get params :limit))] (when (pos? l) l)) default-limit) 1) max-limit)")
(def new-limit "(page-limit (as-int (get params :limit)))")

(def old-test-dep
  "io.github.cognitect-labs/test-runner\n                      {:git/tag \"v0.5.1\" :git/sha \"dfb30dd\"}")
(def new-test-dep
  (str old-test-dep
       "\n                      io.github.kotoba-lang/kotoba\n"
       "                      {:local/root \"../kotoba\"}\n"
       "                      io.github.kotoba-lang/compiler\n"
       "                      {:local/root \"../compiler\"}\n"
       "                      io.github.kotoba-lang/kotoba-lang\n"
       "                      {:local/root \"../kotoba-lang\"}"))

(def guest-source
  "(ns q9.page-limit)\n\n(defn page-limit [requested]\n  (if (> requested 0)\n    (if (> requested 100) 100 requested)\n    20))\n\n(defn main []\n  (page-limit 250))\n")

(defn replace-once [text old new context]
  (let [n (count (re-seq (re-pattern (java.util.regex.Pattern/quote old)) text))]
    (when-not (= 1 n)
      (throw (ex-info "Q9 pilot source shape drift" {:context context :matches n})))
    (str/replace text old new)))

(defn qualification [dir oracle-ns]
  (pr-str {:kotoba.q9.pilot/version 1
           :source (str "src/" dir "/page_limit.kotoba")
           :oracle {:namespace (symbol oracle-ns) :function 'page-limit
                    :cases [-1 0 1 20 250] :expected [20 20 1 20 100]}
           :effects #{} :capabilities #{}
           :consumer-cutover false
           :rollback {:oracle-retained true :action :remove-unused-pilot-source}}))

(defn qualification-test [dir oracle-ns]
  (let [test-ns (str/replace oracle-ns ".main" ".kotoba-qualification-test")]
    (str "(ns " test-ns "\n"
         "  (:require [" oracle-ns " :as oracle]\n"
         "            [clojure.test :refer [deftest is]]\n"
         "            [kotoba.compiler.core :as compiler]\n"
         "            [kotoba.compiler.ir :as compiler-ir]\n"
         "            [kotoba.runtime :as runtime]\n"
         "            [kotoba.wasm-exec :as wasm-exec]))\n\n"
         "(deftest q9-page-limit-oracle-and-backends-agree\n"
         "  (let [source (slurp \"src/" dir "/page_limit.kotoba\")\n"
         "        forms (runtime/read-forms source :kotoba)\n"
         "        reference (runtime/wasm-binary forms)\n"
         "        compiled (compiler/compile-source source :wasm32-kotoba-v1 {:allow #{}})]\n"
         "    (is (:kotoba.wasm/ok? reference))\n"
         "    (is (= 100 (oracle/page-limit 250)\n"
         "           (wasm-exec/run-main (:kotoba.wasm/binary reference) [])\n"
         "           (compiler-ir/execute (:kir compiled) 'main [])))\n"
         "    (is (= #{} (get-in compiled [:hir :effects])))\n"
         "    (is (= [20 20 1 20 100] (mapv oracle/page-limit [-1 0 1 20 250])))))\n")))

(doseq [{:keys [repo dir oracle-ns]} pilots]
  (let [root (str workspace "/orgs/kotoba-lang/" repo)
        main (str root "/src/" dir "/main.cljc")
        deps (str root "/deps.edn")
        guest (str root "/src/" dir "/page_limit.kotoba")
        test-dir (str root "/test/" dir)
        test-file (str test-dir "/kotoba_qualification_test.clj")]
    (when (or (fs/exists? guest) (fs/exists? test-file))
      (throw (ex-info "Q9 pilot target already exists" {:repo repo})))
    (spit main (-> (slurp main)
                   (replace-once coerce-snippet oracle-snippet [repo :oracle])
                   (replace-once old-limit new-limit [repo :paginate])))
    (spit deps (replace-once (slurp deps) old-test-dep new-test-dep [repo :deps]))
    (spit guest guest-source)
    (fs/create-dirs test-dir)
    (spit test-file (qualification-test dir oracle-ns))
    (spit (str root "/kotoba-qualification.edn")
          (str (qualification dir oracle-ns) "\n"))
    (println "Q9 PILOT APPLIED" repo)))
