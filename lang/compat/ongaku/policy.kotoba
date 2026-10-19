(ns ongaku.policy)

(def allowed-contexts
  #{:youtube-background :episode-background :internal-preview})

(defn allowed-for-context?
  [asset usage-context]
  (and (contains? allowed-contexts usage-context)
       (:policy/render-only? asset)
       (false? (:policy/raw-public-access? asset))
       (false? (:policy/ai-training? asset))
       (false? (:policy/content-id-registration? asset))))

(defn allowed-for-channel?
  [asset channel-id]
  (contains? (:music/channels asset) channel-id))

(defn policy-errors
  [asset {:keys [channel-id usage-context expose-raw? ai-training? content-id?]}]
  (cond-> []
    (not (allowed-for-context? asset usage-context))
    (conj {:policy/error :usage-context
           :usage/context usage-context})
    (and channel-id (not (allowed-for-channel? asset channel-id)))
    (conj {:policy/error :channel
           :channel/id channel-id})
    expose-raw?
    (conj {:policy/error :raw-public-access})
    ai-training?
    (conj {:policy/error :ai-training})
    content-id?
    (conj {:policy/error :content-id-registration})))

(defn assert-allowed!
  [asset request]
  (let [errors (policy-errors asset request)]
    (when (seq errors)
      (throw (ex-info "ongaku policy rejected asset"
                      {:asset/id (:asset/id asset)
                       :policy/errors errors})))
    asset))
