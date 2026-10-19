(ns tayori.channel.slack
  "Slack Channel via the Slack Web API — `conversations.history` for thread
  context, `chat.postMessage` to reply. Inbound ingestion in production would
  come from the Events API (a webhook push, verify-webhook is a follow-up —
  the same charter gap gijiroku's Zoom webhook-verify covers for meetings);
  `list-new-messages` here polls `conversations.history` for the mock/offline
  path.

  I/O injected (`:http-fn` `:json-write` `:json-read` `:creds {:bot-token}`),
  same contract as `tayori.channel.email`. Real binding is untested (needs a
  Slack app + bot token with `chat:write`/`channels:history` scopes) — see
  `tayori.channel/mock-channel` for the runnable default."
  (:require [tayori.channel :as ch]))

(defn- slack-get [http-fn token url]
  (http-fn {:url url :method :get :headers {"Authorization" (str "Bearer " token)}}))

(defn- slack-post! [http-fn json-write token url payload]
  (http-fn {:url url :method :post
            :headers {"Authorization" (str "Bearer " token)
                      "Content-Type" "application/json; charset=utf-8"}
            :body (json-write payload)}))

(defn slack-channel [{:keys [http-fn json-write json-read creds]}]
  (let [token (:bot-token creds)]
    (reify ch/Channel
      (fetch-thread [_ thread-id]
        (some-> (slack-get http-fn token
                           (str "https://slack.com/api/conversations.info?channel=" thread-id))
                :body json-read))
      (list-new-messages [_ thread-id]
        (:messages (some-> (slack-post! http-fn json-write token
                             "https://slack.com/api/conversations.history"
                             {:channel thread-id})
                            :body json-read)))
      (send-reply! [_ thread body]
        (slack-post! http-fn json-write token "https://slack.com/api/chat.postMessage"
                     {:channel (:external-id thread) :text body})))))
