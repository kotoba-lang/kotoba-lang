(ns youtube.channels
  "`channels.list?mine=true` and the guard that every publishing tool needs
  before it writes anything.

  Extracted 2026-08-11 from four copies of the same `verify_channel` function
  in `gftdcojp/ai-gftd-ghosthacker-shiropico/tools/` (publish_short,
  publish_episodes, attach_captions, set_privacy). Each had its own copy of
  the expected channel id and its own way of failing, which is the shape a
  safety check should never have: the one that matters is the one on the tool
  someone wrote in a hurry, and that is exactly the copy that gets it wrong.

  The check is deliberately strict — exactly one channel, and its id equals
  the expected one. An OAuth token can carry more than one channel (a brand
  account, a second channel on the same Google account), and `mine=true`
  returns whichever YouTube considers default. Uploading to the wrong channel
  is not recoverable by deleting: the video has already been distributed."
  (:require [youtube.client :as client]))

(def channels-list-url (str client/data-api "/channels?part=id,snippet&mine=true"))

(defn list-mine!
  "Channels this token can act for. Returns the raw `items` vector."
  ([access-token] (list-mine! access-token {}))
  ([access-token {:keys [http-fn] :or {http-fn (client/default-http-fn)}}]
   (let [resp (http-fn {:url channels-list-url
                        :method :get
                        :headers (client/auth-header access-token)})]
     (when-not (#{200} (:status resp))
       (throw (ex-info "youtube channels.list failed"
                       {:stage :channels :status (:status resp) :body (:body resp)})))
     (vec (:items (client/read-json (:body resp)))))))

(defn assert-channel!
  "Refuse to go further unless the token acts for exactly `expected-id`.
  Returns {:id :title} on success; throws ex-info otherwise. Call this before
  the first write in any publishing tool."
  ([access-token expected-id] (assert-channel! access-token expected-id {}))
  ([access-token expected-id opts]
   (let [items (list-mine! access-token opts)
         found (mapv (fn [i] {:id (:id i) :title (get-in i [:snippet :title])}) items)]
     (when-not (and (= 1 (count items)) (= expected-id (:id (first items))))
       (throw (ex-info "youtube channel mismatch — refusing to publish"
                       {:stage :channels :expected expected-id :found found})))
     (first found))))
