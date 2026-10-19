(ns youtube.thumbnails
  "`thumbnails.set` -- requires the channel's custom-thumbnail feature to be
  enabled (phone-verified channel). Ported 1:1 from
  `kotoba-lang/youtube-upload`'s `client.py` `set_thumbnail`."
  (:require [youtube.client :as client]))

(def thumbnails-set-url (str client/upload-api "/thumbnails/set"))

(defn set-thumbnail!
  "youtube-video-id + png-bytes (JVM byte[]) -> nil on success. Throws
  ex-info on a non-2xx response."
  ([access-token youtube-video-id png-bytes] (set-thumbnail! access-token youtube-video-id png-bytes {}))
  ([access-token youtube-video-id png-bytes {:keys [http-fn] :or {http-fn (client/default-http-fn)}}]
   (let [resp (http-fn {:url (str thumbnails-set-url "?videoId=" youtube-video-id)
                        :method :post
                        :headers (merge (client/auth-header access-token) {"Content-Type" "image/png"})
                        :body png-bytes})]
     (when-not (#{200 201} (:status resp))
       (throw (ex-info "youtube thumbnails.set failed" {:stage :thumbnail :status (:status resp) :body (:body resp)})))
     nil)))
