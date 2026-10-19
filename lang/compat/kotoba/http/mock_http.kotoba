(ns kotoba.http.mock-http
  "mock-http -- addressed on its own.

  Split out of kotoba.lang.http on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.http.http :refer [Http send]])
)

(defn mock-http
  "An Http whose `send` routes each request through `handler` (fn of req →
  response). For tests / OSS standalone."
  [handler]
  (reify Http
    (send [_ req] (handler req))))
