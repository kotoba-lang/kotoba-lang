(ns youtube.captions
  "`captions.insert` -- multipart/related upload of a single SRT track.
  Ported 1:1 from `kotoba-lang/youtube-upload`'s `client.py` `upload_caption`
  (boundary format, part ordering, and content-types are unchanged)."
  (:require [clojure.string :as str]
            [youtube.client :as client]))

(def captions-insert-url (str client/upload-api "/captions?uploadType=multipart&part=snippet"))

(defn caption-snippet [{:keys [youtube-video-id lang name]}]
  {:snippet {:videoId youtube-video-id :language lang :name name :isDraft false}})

(defn multipart-body
  "snippet-map + srt-bytes (JVM byte[]) + boundary -> the multipart/related
  request body as a byte[] (JSON part, then the raw SRT bytes, then the
  closing boundary -- srt-bytes is opaque so it can't be built as one
  String without risking encoding corruption)."
  [snippet-map srt-bytes boundary]
  (let [head (str "--" boundary "\r\n"
                  "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                  (client/write-json snippet-map) "\r\n"
                  "--" boundary "\r\n"
                  "Content-Type: application/x-subrip\r\n\r\n")
        tail (str "\r\n--" boundary "--\r\n")
        head-bytes (client/utf8-bytes head)
        tail-bytes (client/utf8-bytes tail)]
    (client/concat-bytes [head-bytes srt-bytes tail-bytes])))

(defn insert-caption!
  "{:youtube-video-id :lang :name} + srt-bytes (JVM byte[]) -> nil on
  success. Throws ex-info on a non-2xx response."
  ([access-token opts srt-bytes] (insert-caption! access-token opts srt-bytes {}))
  ([access-token opts srt-bytes {:keys [http-fn boundary]
                                 :or {http-fn (client/default-http-fn)
                                      boundary (str "ytupload" (client/now-ms))}}]
   (let [body (multipart-body (caption-snippet opts) srt-bytes boundary)
         resp (http-fn {:url captions-insert-url
                        :method :post
                        :headers (merge (client/auth-header access-token)
                                       {"Content-Type" (str "multipart/related; boundary=" boundary)})
                        :body body})]
     (when-not (#{200 201} (:status resp))
       (throw (ex-info "youtube captions.insert failed" {:stage :captions :status (:status resp) :body (:body resp)})))
     nil)))
