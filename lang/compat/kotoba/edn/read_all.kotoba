(ns kotoba.edn.read-all
  "read-all -- addressed on its own.

  Split out of kotoba.lang.edn on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.edn.check-opts :refer [check-opts!]]
            [kotoba.edn.form-spans :refer [form-spans]]
            [kotoba.edn.max-edn-bytes :refer [max-edn-bytes]]
            [kotoba.edn.read-string :refer [read-string]]
            [kotoba.edn.reject :refer [reject!]]
            [kotoba.edn.utf8-size :refer [utf8-size]]))

(defn read-all
  "Read EVERY top-level form in `text`, returning a vector in source order.

  The bounded, capability-free replacement for a `clojure.edn/read` loop over
  a `PushbackReader` with an `:eof` sentinel: obtain the text through a granted
  filesystem handle, then call this. End of input is the end of the vector, so
  there is no sentinel value to choose or to leak into the data.

  Empty input (blank, or only comments) reads as `[]` -- NOT an error, because
  `[]` is the honest answer for a file that holds no forms, and the caller can
  tell it apart from a file it failed to read (that throws). Each form is
  bounded and shape-checked exactly as `read-string` bounds a single one.

  Reader evaluation and tagged literals stay forbidden."
  ([text] (read-all {} text))
  ([opts text]
   (check-opts! opts)
   (when-not (string? text)
     (reject! "EDN input must be text" {}))
   (when (> (utf8-size text) max-edn-bytes)
     (reject! "EDN input exceeds byte limit" {:limit max-edn-bytes}))
   ;; :eof has no meaning here -- end of input is the end of the vector, which
   ;; is the whole point of read-all -- so it is refused rather than ignored.
   (when (contains? opts :eof)
     (reject! "EDN :eof has no meaning for read-all; the empty vector is the answer" {}))
   (mapv (fn [[start end]] (read-string opts (subs text start end)))
         (form-spans text (boolean (or (:readers opts) (:default opts)))))))
