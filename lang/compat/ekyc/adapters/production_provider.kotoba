(ns ekyc.adapters.production-provider
  (:require [ekyc.adapters.provider :as provider]))

(defn provider-client [transport endpoints]
  (reify provider/IEkycProviderClient
    (create-session! [_ payload opts]
      (:body (transport (assoc opts :method :post :url (:sessions endpoints) :body payload))))
    (upload-evidence! [_ payload opts]
      (:body (transport (assoc opts :method :post :url (:evidence endpoints) :body payload))))
    (retrieve-result! [_ payload opts]
      (:body (transport (assoc opts :method :get :url (:result endpoints) :query payload))))))

(defn static-transport [responses]
  (let [calls (atom [])]
    {:calls calls
     :transport (fn [request]
                  (swap! calls conj request)
                  (get responses [(:method request) (:url request)] {:body {}}))}))
