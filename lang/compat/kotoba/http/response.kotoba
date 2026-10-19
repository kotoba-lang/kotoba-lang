(ns kotoba.http.response
  "response -- addressed on its own.

  Split out of kotoba.lang.http on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  )

(defn response
  ([status] (response status nil nil))
  ([status headers body]
   {:http/status  (int status)
    :http/headers (or headers {})
    :http/body    body}))
