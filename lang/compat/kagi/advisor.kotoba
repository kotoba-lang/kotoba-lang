(ns kagi.advisor
  "知能ノード(risk/anomaly advisor)。**proposal のみ**返す — 書込/開示/鍵操作は一切しない。
  mock は決定的、実装は langchain.model(LLM) や異常検知に差し替え可能(注入境界)。"
  )

(defprotocol Advisor
  (-advise [a request context] "request+context → proposal map(confidence/category/anomaly?)"))

(defn- infer [{:keys [op category]} {:keys [device-trusted? hour]}]
  ;; 単純な決定的リスク推定(デモ): 高価値カテゴリ・深夜・未信頼デバイスで confidence を下げ
  ;; anomaly を立てる。実運用では振る舞い/位置/速度などで置換。
  (let [base 0.95
        night? (and hour (or (< hour 6) (> hour 23)))
        penalty (cond-> 0.0
                  (= false device-trusted?) (+ 0.4)
                  night?                    (+ 0.2))]
    {:category   category
     :confidence (max 0.0 (- base penalty))
     :anomaly?   (or (= false device-trusted?) (boolean night?))
     :summary    (str "risk-assessed " op)}))

(defn mock-advisor []
  (reify Advisor (-advise [_ req ctx] (infer req ctx))))

(defn trace [request proposal]
  {:t :advised :op (:op request) :confidence (:confidence proposal)
   :anomaly? (:anomaly? proposal)})
