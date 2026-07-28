(ns kotoba.lang.stdlib-manifest-test
  "T4.1: frozen stdlib public module list."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import (java.security MessageDigest)))

(def manifest-path "lang/conformance/stdlib/manifest.edn")
(def core-path "lang/stdlib/core.kotoba")
(def conf-core-path "lang/conformance/stdlib/core.kotoba")

(defn- sha256-hex [bytes]
  (let [md (MessageDigest/getInstance "SHA-256")
        d (.digest md bytes)]
    (apply str (map #(format "%02x" %) d))))

(defn- load-manifest []
  (edn/read-string (slurp manifest-path)))

(defn- defn-names [source]
  (->> (re-seq #"(?m)^\s*\(defn\s+([^\s\[]+)" source)
       (map (comp symbol second))
       set))

(deftest stdlib-manifest-frozen-shape
  (let [m (load-manifest)
        core (first (:modules m))]
    (is (= 1 (:kotoba.lang.stdlib.manifest/version m)))
    (is (= :frozen (:kotoba.lang.stdlib.manifest/status m)))
    (is (= "T4.1" (:kotoba.lang.stdlib.manifest/wbs m)))
    (is (= :core (:id core)))
    (is (= :frozen (:status core)))
    (is (set? (:public-names core)))
    (is (contains? (:public-names core) 'option-value))
    (is (contains? (:public-names core) 'unwrap-ok))
    (is (not (contains? (:public-names core) 'string-from-i64))
        "string ops are language builtins, not prelude names")
    (is (contains? (get-in m [:language-builtins :string-ops]) 'string-from-i64))))

(deftest core-source-matches-public-names-and-sha
  (let [m (load-manifest)
        core (first (:modules m))
        src (slurp core-path)
        conf (slurp conf-core-path)
        names (defn-names src)
        expected (:public-names core)]
    (is (= src conf) "conformance mirror must match package SSoT")
    (is (= (:sha256 core) (sha256-hex (.getBytes src "UTF-8"))))
    (is (= expected names)
        (str "extra=" (pr-str (clojure.set/difference names expected))
             " missing=" (pr-str (clojure.set/difference expected names))))))

(deftest package-contract-lists-core-module
  (let [pkg (edn/read-string (slurp "lang/stdlib.edn"))
        m (load-manifest)
        core (first (:modules m))]
    (is (= 1 (:kotoba.stdlib/version pkg)))
    (is (= :core (:id (first (:kotoba.stdlib/artifacts pkg)))))
    (is (= (:sha256 core) (:sha256 (first (:kotoba.stdlib/artifacts pkg)))))))
