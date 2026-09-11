(ns kotoba.cli-portable-test
  "The genuinely portable slice of kotoba.cli-test: `parse-argv` takes a
  literal argv vector and returns data -- it never touches `read-contract`,
  the top-level `(cli/read-contract)` call whose `:cljs` branch is a
  deliberate throw (kotoba.cli-test.clj's own docstring names this exactly).
  Every other assertion in kotoba.cli-test depends on `contract` or
  `adapters`, both loaded from disk at namespace load time, and stays there."
  (:require #?(:clj [clojure.test :refer [deftest is]]
               :cljs [cljs.test :refer [deftest is] :include-macros true])
            [kotoba.cli :as cli]))

(deftest argv-is-shaped-as-data
  (is (= {:positionals ["main.kotoba"]
          :options {:target "kotoba"
                    :arg ["1" "2"]
                    :json true}}
         (cli/parse-argv ["main.kotoba" "--target" "kotoba" "--arg" "1" "--arg" "2" "--json"]))))

(deftest argv-with-no-options-has-an-empty-options-map
  (is (= {:positionals ["run"] :options {}}
         (cli/parse-argv ["run"]))))
