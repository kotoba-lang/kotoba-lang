(ns connector.provider
  "A provider pairs a descriptor with the two functions that cannot live inside
  it: how a tool call becomes an HTTP request, and how the response becomes a
  result.

  They are kept out of the descriptor because a descriptor has to be printable,
  diffable and storable — it goes in a catalog, a manifest and a grant screen —
  and a map holding closures is none of those things.

  A connector repository's whole public surface is one of these:

    (def provider
      (connector.provider/provider descriptor
        {:request   (fn [tool-name args] …)     ; → request map, no auth headers
         :normalize (fn [tool-name response] …)}))

  `request` must NOT attach credentials. `connector.invoke` adds the
  Authorization header from the host's token, which is what keeps every
  connector repository free of any way to obtain one."
  (:require [connector.model :as m]
            [connector.validate :as v]))

(defn provider
  "Bundle a descriptor with its request/normalize functions.

  Throws on a descriptor with :error-level problems. A connector repository
  calls this at load time on purpose: a descriptor that cannot pass validation
  should fail where it is written, not in the host that imported it."
  [descriptor {:keys [request normalize]}]
  (let [errs (v/errors descriptor)]
    (when (seq errs)
      (throw (ex-info (str "invalid connector descriptor: " (:connector/id descriptor))
                      {:type :connector/invalid
                       :connector/id (:connector/id descriptor)
                       :connector/problems errs}))))
  (when-not (fn? request)
    (throw (ex-info "provider needs a :request function"
                    {:type :connector/no-request
                     :connector/id (:connector/id descriptor)})))
  {:connector/descriptor descriptor
   :connector/request request
   :connector/normalize (or normalize (fn [_ response] (:connector.http/body response)))})

(defn descriptor [p] (:connector/descriptor p))
(defn id [p] (get-in p [:connector/descriptor :connector/id]))
(defn tool-names [p] (m/tool-names (descriptor p)))
