#!/usr/bin/env bb
(ns pin-q9-pilot-deps
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(def workspace (-> "../../.." fs/canonicalize str))
(def repositories
  ["com-aadhaar" "com-aave" "com-abb-robotics" "com-accela"
   "com-accelbyte" "com-ach-network" "com-acorrd-standards" "com-acquia"
   "com-adobe" "com-aftership"])

(def local-block
  (str "io.github.kotoba-lang/kotoba\n"
       "                      {:local/root \"../kotoba\"}\n"
       "                      io.github.kotoba-lang/compiler\n"
       "                      {:local/root \"../compiler\"}\n"
       "                      io.github.kotoba-lang/kotoba-lang\n"
       "                      {:local/root \"../kotoba-lang\"}"))

(def pinned-block
  (str "io.github.kotoba-lang/kotoba\n"
       "                      {:git/url \"https://github.com/kotoba-lang/kotoba.git\"\n"
       "                       :git/sha \"47a83883ce0ca91d6cda978c8fd03f38192c3887\"}\n"
       "                      io.github.kotoba-lang/compiler\n"
       "                      {:git/url \"https://github.com/kotoba-lang/compiler.git\"\n"
       "                       :git/sha \"269846bbd9921517ec9451fbf575e047d61c7e19\"}\n"
       "                      io.github.kotoba-lang/kotoba-lang\n"
       "                      {:git/url \"https://github.com/kotoba-lang/kotoba-lang.git\"\n"
       "                       :git/sha \"94443462fba84e28bff5df6e3adf6752bf9b8e2e\"}"))

(def local-head-block
  (-> pinned-block
      (str/replace "47a83883ce0ca91d6cda978c8fd03f38192c3887"
                   "0c96a034898c8f4b6c3562c9c88e1950f3dfff56")
      (str/replace "269846bbd9921517ec9451fbf575e047d61c7e19"
                   "8c070cb3dcb2190790f3f299f2370bddb440ad82")
      (str/replace "94443462fba84e28bff5df6e3adf6752bf9b8e2e"
                   "300017d7fb2341ff9deffa4029776d9f4064c122")))

(doseq [repository repositories]
  (let [path (str workspace "/orgs/kotoba-lang/" repository "/deps.edn")
        text (slurp path)
        source (cond
                 (str/includes? text local-block) local-block
                 (str/includes? text local-head-block) local-head-block
                 (str/includes? text pinned-block) nil
                 :else ::missing)]
    (when (= ::missing source)
      (throw (ex-info "Q9 dependency block drift" {:repository repository})))
    (when source
      (spit path (str/replace text source pinned-block)))
    (println "Q9 PILOT DEPS PINNED" repository
             (if source "updated" "already-current"))))
