(ns aml.adapters.etzhayyim
  (:require [aml.model :as m]
            [aml.ports :as p]))

(def yabai-get-risk "ai.gftd.apps.yabai.getRisk")
(def malak-query-risk-chain "ai.gftd.apps.malak.queryRiskChain")

(defprotocol IXrpcClient
  (invoke! [client nsid payload]))

(defn- subject-id [request]
  (let [subject (:aml/subject request)]
    (or (:subject/id subject)
        (:identity.subject/id subject)
        (:did subject)
        (:wallet/address subject)
        subject)))

(defn- severity->level [severity]
  (case (keyword severity)
    (:critical :deny) :deny
    (:high :challenge) :challenge
    (:medium :review) :review
    (:low :monitor) :monitor
    (:clear :none) :clear
    nil))

(defn- score->level [score]
  (cond
    (nil? score) nil
    (>= score 900) :deny
    (>= score 700) :challenge
    (>= score 400) :review
    (pos? score) :monitor
    :else :clear))

(defn- response-level
  "Map a screening response to an aml.model level. Falls back to :review
  (never :clear) when the response carries no recognizable severity/score
  signal -- this is the case for an upstream error map (e.g.
  {:error :http/status :status 503 :body ...}, what xrpc-http/retry return
  when the risk-scoring vendor is unreachable or every retry is exhausted),
  and :clear must never be the default for 'we don't actually know': an
  AML/sanctions clearance requires a positive observed signal, not merely
  the absence of one. Silently clearing a subject because the vendor API
  errored out was a confirmed fail-open bug this closes."
  [response]
  (or (severity->level (:severity response))
      (severity->level (:risk/severity response))
      (score->level (:score response))
      (score->level (:risk/score response))
      :review))

(defn- response-score [response]
  (or (:score response) (:risk/score response)))

(defn- response-categories [response]
  (set (concat (:categories response)
               (:flags response)
               (:riskSignals response)
               (:risk/signals response))))

(defn- evidence-ref [route response]
  (or (:evidence-ref response)
      (:uri response)
      (:cid response)
      (str "xrpc://" (name route) "/" (:request-id response))))

(defn- yabai-payload [request]
  {:entityId (str (subject-id request))
   :caseRef (:aml/case-ref request)
   :purpose (:aml/purpose request)})

(defn- malak-payload [request]
  {:address (:aml/subject request)
   :chain (get-in request [:aml/request-context :chain])
   :caseRef (:aml/case-ref request)
   :purpose (:aml/purpose request)})

(defn- call-route [client route request]
  (case route
    :yabai (invoke! client yabai-get-risk (yabai-payload request))
    :malak (invoke! client malak-query-risk-chain (malak-payload request))))

(defn screening-port [client]
  (reify p/IAmlScreening
    (screen! [_ request route]
      (let [response (call-route client route request)]
        (m/result request route (response-level response)
                  {:score (response-score response)
                   :categories (response-categories response)
                   :evidence-ref (evidence-ref route response)
                   :asserter (str "etzhayyim/" (name route))
                   :observed-at (:observed-at response)})))))
