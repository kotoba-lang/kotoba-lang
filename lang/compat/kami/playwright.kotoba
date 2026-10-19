(ns kami.playwright
  "playwright-clj — drive a headless WebGL2 Chromium from CLJ/bb. (eval-page js) runs JS in a real
   browser page and returns the result as EDN, so browser tests (WebGL2 shader compilation, canvas,
   DOM, fetch) run reproducibly in the CLJ toolchain — no live extension needed. Resolves Playwright
   from the npx cache and auto-detects the chromium binary; shells to scripts/pw_eval.cjs."
  (:require [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- detect-node-path []
  (or (System/getenv "NODE_PATH")
      (let [npx (io/file (System/getProperty "user.home") ".npm/_npx")]
        (when (.isDirectory npx)
          (some (fn [d] (when (.exists (io/file d "node_modules/playwright"))
                          (.getPath (io/file d "node_modules"))))
                (.listFiles npx))))
      "node_modules"))

(def ^:private bridge "scripts/pw_eval.cjs")

(defn eval-page
  "Evaluate JS (a statement block; `return` yields the result) in a headless WebGL2 Chromium page;
   returns the parsed result as EDN. opts {:url}. Throws on browser/bridge error."
  [js & {:keys [url] :or {url "about:blank"}}]
  (let [f  (java.io.File/createTempFile "pwclj" ".js")
        np (detect-node-path)]
    (spit f js)
    (try
      (let [{:keys [out err exit]} (p/sh {:extra-env {"NODE_PATH" np}} "node" bridge (.getPath f) url)
            parsed (try (json/parse-string (str/trim out) true) (catch Exception _ nil))]
        (cond
          (and parsed (:ok parsed)) (:result parsed)
          parsed (throw (ex-info (str "browser eval error: " (:error parsed)) {}))
          :else  (throw (ex-info (str "playwright bridge failed (exit " exit ")") {:err err :out out}))))
      (finally (.delete f)))))

(defn available?
  "Is the headless-browser harness usable here (Playwright + a chromium binary present)?"
  []
  (try (boolean (:webgl2 (eval-page "return {webgl2: !!document.createElement('canvas').getContext('webgl2')};")))
       (catch Exception _ false)))
