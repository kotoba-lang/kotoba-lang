(ns kotoba.http.request
  "request -- addressed on its own.

  Split out of kotoba.lang.http on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.
"
  (:require [kotoba.lang.text :as str])
)

(defn request
  "Construct a request map. `method` may be a keyword or string; `url` is the
  full URL string. Options: `:headers` (map), `:body`, `:query-params`."
  ([method url] (request method url nil))
  ([method url opts]
   (cond-> {:http/method (keyword (str/lower (name method)))
            :http/url    url}
     (:headers opts)       (assoc :http/headers (:headers opts))
     (contains? opts :body) (assoc :http/body (:body opts))
     (:query-params opts)  (assoc :http/query-params (:query-params opts)))))
