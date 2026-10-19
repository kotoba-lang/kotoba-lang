(ns aiueos.kagi-policy
  "aiueos decides whether a component may request a kagi reference. It never
  resolves, decrypts, logs, or stores the referenced secret value.")

(defn kagi-ref? [x]
  (and (string? x) (boolean (re-matches #"^kagi://[^/\s]+/.+$" x))))

(def operation->capability {:reveal :kagi/reveal :sign :kagi/sign})

(defn decide
  [{:keys [component secret-ref purpose operation]
    :or {operation :reveal}} {:keys [grants]}]
  (let [allowed (get grants component #{})
        capability (get operation->capability operation)
        request [capability purpose]]
    (cond
      (not (kagi-ref? secret-ref)) {:decision :deny :reason :invalid-secret-ref}
      (nil? purpose) {:decision :deny :reason :purpose-required}
      (nil? capability) {:decision :deny :reason :unsupported-operation}
      (not (contains? allowed request)) {:decision :deny :reason :not-granted}
      :else {:decision :grant :secret-ref secret-ref :purpose purpose
             :capability capability})))

(defn decide-all
  "Authorize every reference request as one indivisible broker decision. Empty
  requests are valid; malformed or unauthorized entries fail the component."
  [component requests policy]
  (let [decisions (mapv #(decide (assoc % :component component) policy) requests)]
    (if-let [denied (first (filter #(= :deny (:decision %)) decisions))]
      {:decision :deny :reason (:reason denied)
       :request-index (.indexOf decisions denied)}
      {:decision :grant :requests decisions})))

(defn audit-record [decision]
  (select-keys decision [:decision :reason :purpose :capability]))
