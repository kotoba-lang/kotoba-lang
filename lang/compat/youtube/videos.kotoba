(ns youtube.videos
  "`videos.insert` (resumable, single-chunk PUT) and `videos.update`. Ported
  1:1 from `kotoba-lang/youtube-upload`'s `client.py` `upload_video` (the
  metadata shape, header names, and status-code checks are unchanged)."
  (:require [clojure.string :as str]
            [youtube.client :as client]))

(def videos-insert-url (str client/upload-api "/videos?uploadType=resumable&part=snippet,status"))
(def videos-update-url (str client/data-api "/videos?part=snippet,status"))
(def videos-status-url (str client/data-api "/videos?part=status"))

(defn list-status!
  "videos.list?part=status for up to 50 ids -> {video-id status-map}.
  Ids YouTube does not return (deleted, or not yours) are simply absent, so
  callers can tell 'not found' from 'found with this status'."
  ([access-token ids] (list-status! access-token ids {}))
  ([access-token ids {:keys [http-fn] :or {http-fn (client/default-http-fn)}}]
   (let [resp (http-fn {:url (str videos-status-url "&id=" (str/join "," ids))
                        :method :get
                        :headers (client/auth-header access-token)})]
     (when-not (#{200} (:status resp))
       (throw (ex-info "youtube videos.list failed"
                       {:stage :list :status (:status resp) :body (:body resp)})))
     (into {} (map (juxt :id :status)) (:items (client/read-json (:body resp)))))))

(defn privacy-body
  "The videos.update?part=status body that changes privacy **and nothing else**.

  videos.update REPLACES the whole `status` part: any field left out reverts
  to its API default. PUTting a bare {:privacyStatus \"public\"} therefore
  silently clears selfDeclaredMadeForKids / embeddable / license — on a
  children's channel that is a compliance change nobody asked for. So the
  current status is read first and carried through.

  `madeForKids` is read-only on the way out but has to be written back under
  the *different* name `selfDeclaredMadeForKids`; they are the same setting."
  [video-id current-status privacy]
  {:id video-id
   :status {:privacyStatus privacy
            :license (get current-status :license "youtube")
            :embeddable (get current-status :embeddable true)
            :publicStatsViewable (get current-status :publicStatsViewable true)
            :selfDeclaredMadeForKids (get current-status :madeForKids false)}})

(defn set-privacy!
  "Read-modify-write a single video's privacy, preserving the rest of `status`."
  ([access-token video-id current-status privacy]
   (set-privacy! access-token video-id current-status privacy {}))
  ([access-token video-id current-status privacy {:keys [http-fn] :or {http-fn (client/default-http-fn)}}]
   (let [resp (http-fn {:url videos-status-url
                        :method :put
                        :headers (merge (client/auth-header access-token)
                                        {"Content-Type" "application/json; charset=UTF-8"})
                        :body (client/write-json (privacy-body video-id current-status privacy))})]
     (when-not (#{200} (:status resp))
       (throw (ex-info "youtube videos.update (privacy) failed"
                       {:stage :privacy :video-id video-id
                        :status (:status resp) :body (:body resp)})))
     (client/read-json (:body resp)))))

(defn video-metadata
  "{:title :description :tags :category-id :default-language :privacy-status
    :made-for-kids? :embeddable?} -> the videos.insert/update request body.
  Title/description are clamped to the API's 100/5000-char limits."
  [{:keys [title description tags category-id default-language privacy-status
           made-for-kids? embeddable?]
    :or {tags [] made-for-kids? false embeddable? true}}]
  {:snippet {:title (subs (or title "") 0 (min 100 (count (or title ""))))
             :description (subs (or description "") 0 (min 5000 (count (or description ""))))
             :tags tags
             :categoryId category-id
             :defaultLanguage default-language
             :defaultAudioLanguage default-language}
   :status {:privacyStatus privacy-status
           :selfDeclaredMadeForKids made-for-kids?
           :embeddable embeddable?}})

(defn insert-video!
  "Resumable videos.insert. `video-bytes` is a JVM byte array (mp4).
  Returns the new YouTube video id. Throws ex-info on any transport/API
  failure (mirrors client.py's YouTubeUploadError stages: :upload-init /
  :upload-put)."
  ([access-token video-bytes metadata] (insert-video! access-token video-bytes metadata {}))
  ([access-token video-bytes metadata {:keys [http-fn] :or {http-fn (client/default-http-fn)}}]
   (let [init-resp (http-fn {:url videos-insert-url
                             :method :post
                             :headers (merge (client/auth-header access-token)
                                            {"Content-Type" "application/json; charset=UTF-8"
                                             "X-Upload-Content-Type" "video/mp4"
                                             "X-Upload-Content-Length" (str (client/byte-count video-bytes))})
                             :body (client/write-json metadata)})]
     (when-not (#{200 201} (:status init-resp))
       (throw (ex-info "youtube videos.insert init failed"
                       {:stage :upload-init :status (:status init-resp) :body (:body init-resp)})))
     (let [upload-url (get (:response-headers init-resp) "location")]
       (when-not upload-url
         (throw (ex-info "youtube videos.insert init: no Location header" {:stage :upload-init})))
       (let [put-resp (http-fn {:url upload-url
                                :method :put
                                :headers {"Content-Type" "video/mp4"
                                         "Content-Length" (str (client/byte-count video-bytes))}
                                :body video-bytes})]
         (when-not (#{200 201} (:status put-resp))
           (throw (ex-info "youtube videos.insert upload failed"
                           {:stage :upload-put :status (:status put-resp) :body (:body put-resp)})))
         (let [video-id (:id (client/read-json (:body put-resp)))]
           (when-not video-id
             (throw (ex-info "youtube videos.insert: no id in response" {:stage :upload-put :body (:body put-resp)})))
           video-id))))))

(defn update-video!
  "videos.update -- PUT a full snippet/status body for an existing video.
  `video-id` is merged into the body as :id (required by the API)."
  ([access-token video-id metadata] (update-video! access-token video-id metadata {}))
  ([access-token video-id metadata {:keys [http-fn] :or {http-fn (client/default-http-fn)}}]
   (let [resp (http-fn {:url videos-update-url
                        :method :put
                        :headers (merge (client/auth-header access-token)
                                       {"Content-Type" "application/json; charset=UTF-8"})
                        :body (client/write-json (assoc metadata :id video-id))})]
     (when-not (#{200} (:status resp))
       (throw (ex-info "youtube videos.update failed" {:stage :update :status (:status resp) :body (:body resp)})))
     (client/read-json (:body resp)))))
