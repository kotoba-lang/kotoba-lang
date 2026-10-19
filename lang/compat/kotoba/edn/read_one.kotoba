(ns kotoba.edn.read-one
  "read-one -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.read-form :refer [read-form]]
            [kotoba.edn.reject :refer [reject!]]
            [kotoba.edn.skip-blanks :refer [skip-blanks]]))

(defn read-one
  "The single top-level form in `text`. `preflight!` has already refused empty
  input and trailing forms, so anything left over here is this parser
  disagreeing with that lexer -- which is a defect, not bad input, and says so."
  [text opts]
  (let [i        (skip-blanks text 0)
        [v i']   (read-form text i opts)
        leftover (skip-blanks text i')]
    (when (< leftover (count text))
      (reject! "EDN reader and preflight disagree about where the form ends"
               {:kotoba.lang.edn/reason :reader/desync :index leftover}))
    v))
