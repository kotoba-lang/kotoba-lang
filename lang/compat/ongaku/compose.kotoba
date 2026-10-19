(ns ongaku.compose
  (:require [ongaku.catalog :as catalog]
            [ongaku.policy :as policy]))

(defn- mood-score
  [asset requested-mood]
  (if (contains? (:music/moods asset) requested-mood) 10 0))

(defn- duration-score
  [asset target-sec]
  (let [duration (:audio/duration-sec asset 0)]
    (cond
      (nil? target-sec) 0
      (>= duration target-sec) 3
      :else (- 0 (/ (- target-sec duration) 60.0)))))

(defn candidates
  ([request] (candidates (catalog/load-catalog) request))
  ([cat request]
   (let [{:keys [channel-id usage-context mood]} request]
     (->> (catalog/assets cat)
          (filter #(empty? (policy/policy-errors %
                                                 {:channel-id channel-id
                                                  :usage-context usage-context})))
          (sort-by (fn [asset]
                     (- (+ (mood-score asset mood)
                           (duration-score asset (:duration/sec request))))))))))

(defn compose
  "Select a background-music asset for a render plan.

  This function returns a plan, not a public URL. Callers must mux the local
  file into a video and carry the returned credit into the published
  description/credits."
  ([request] (compose (catalog/load-catalog) request))
  ([cat request]
   (let [request (merge {:usage-context :youtube-background
                         :mood :calm}
                        request)
         asset (first (candidates cat request))]
     (when-not asset
       (throw (ex-info "no usable BGM asset"
                       {:ongaku/request request})))
     (policy/assert-allowed! asset {:channel-id (:channel-id request)
                                    :usage-context (:usage-context request)})
     {:ongaku/plan :plan/background-music
      :asset/id (:asset/id asset)
      :asset/title (:asset/title asset)
      :asset/path (:asset/local-path asset)
      :asset/sha256 (:asset/sha256 asset)
      :audio/duration-sec (:audio/duration-sec asset)
      :credit/text (:credit/text asset)
      :policy/render-only? true
      :policy/raw-public-access? false
      :license/url (:asset/license-url asset)
      :source/url (:asset/source-url asset)})))
