(ns google-chat.connector
  "Google Chat as a connector — the Incoming Webhook, exposed as one tool.

  This is `google-chat.client` given a descriptor, not a second client. The
  scope decision recorded in that namespace still holds: the inbound path needs
  a Marketplace app and JWT verification, and the full REST send API needs
  service-account JWT *signing*, both disproportionate for a notification
  channel. So there is exactly one tool, it writes, and there is no read
  counterpart.

  **The webhook URL is the credential.** It arrives from Google already
  carrying `key` and `token` query parameters and there is no header to
  authenticate with. Modelling that as `:bearer` would be wrong twice: the host
  would send an Authorization header Google ignores, and the URL — the actual
  secret — would have to sit in the descriptor, where a connector catalog can
  print it. `:url-credential` keeps it out: `request` returns
  `:connector.http/url-from-credential`, and `connector.invoke` fills the URL in
  from the host's token store.

  So this connector, like every other, cannot obtain or print its credential.
  What is unusual is only where the credential goes."
  (:require [connector.model :as m]
            [connector.provider :as p]))

(def descriptor
  (-> (m/connector
       "com.google.chat" "Google Chat"
       {:summary "Post a message to a Google Chat space through an Incoming Webhook. Send-only."
        :origin-domain "google.com"
        ;; No :base-url on purpose — the endpoint IS the credential and does
        ;; not belong in a descriptor a catalog can print.
        :docs-url "https://developers.google.com/chat/how-tos/webhooks"
        :auth (m/url-credential "GOOGLE_CHAT_WEBHOOK_URL")})

      (m/add-tool
       "google_chat_post_message"
       {:description "Post a message to the space this webhook belongs to. The space is fixed by the webhook URL and cannot be chosen per call."
        :effect :write
        :input-schema {:type "object"
                       :properties
                       {"text" {:type "string"
                                :description "Message text. Google Chat renders a small subset of Markdown."}
                        "thread_key" {:type "string"
                                      :description "Group messages sharing this key into one thread."}}
                       :required ["text"]}})))

;; --- requests ---

(defn request
  [tool-name args]
  (case tool-name
    "google_chat_post_message"
    (cond-> {:connector.http/method :post
             ;; No URL. connector.invoke supplies it from the credential.
             :connector.http/url-from-credential true
             :connector.http/headers {"content-type" "application/json; charset=UTF-8"}
             :connector.http/body {"text" (get args "text")}}
      (get args "thread_key")
      (assoc :connector.http/query
             {"threadKey" (get args "thread_key")
              ;; Without this Google Chat treats threadKey as a hint and starts
              ;; a new thread anyway when it does not recognise the key.
              "messageReplyOption" "REPLY_MESSAGE_FALLBACK_TO_NEW_THREAD"}))))

;; --- responses ---

(defn normalize
  [tool-name response]
  (let [body (:connector.http/body response)]
    (case tool-name
      "google_chat_post_message"
      {:name (get body "name")
       :thread (get-in body ["thread" "name"])
       :create-time (get body "createTime")})))

(def provider
  (p/provider descriptor {:request request :normalize normalize}))
