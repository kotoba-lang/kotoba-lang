(ns authentication.adapters.local-factor
  (:require [authentication.adapters.factor :as factor]))

(defn static-client [accepted]
  (reify factor/IFactorClient
    (verify! [_ payload opts]
      (let [expected (get accepted (:request-id payload))]
        (if (= expected (:response payload))
          {:evidence-ref (or (:evidence-ref opts)
                             (str "local://authn/factor/" (:request-id payload)))
           :assurance (:assurance opts)
           :verified-at (:verified-at opts)}
          {:error :factor-mismatch
           :verified-at (:verified-at opts)})))))
