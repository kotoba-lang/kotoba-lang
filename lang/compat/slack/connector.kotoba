(ns slack.connector
  "Slack as a connector.

  Lives here rather than in a new repository: the origin plane already gave
  slack.com to this one, and two repositories for one subject in one plane is
  not allowed (ADR-2608040100). `slack.main` is the clean-room actor — Slack's
  API implemented *here*; this namespace is a client of the real one, exposed
  as tools an agent can be granted scope by scope.

  **The Slack behaviour that matters most is not in any tool: Slack answers
  HTTP 200 with `{\"ok\": false, \"error\": \"...\"}`.** A connector that only
  checked the status code would report `missing_scope`, `channel_not_found` and
  `not_in_channel` as successes, and the caller would see an empty channel list
  rather than a permission problem. `normalize` turns `ok:false` into an error
  value, so the failure arrives as a failure.

  Slack's OAuth v2 does not verify PKCE, and the descriptor says so rather than
  sending a challenge nobody checks.

  Nothing here can obtain a credential; `connector.invoke` attaches it."
  (:require [connector.model :as m]
            [connector.provider :as p]))

(def base-url "https://slack.com/api")

(def channels-read-scope "channels:read")
(def channels-history-scope "channels:history")
(def chat-write-scope "chat:write")
(def users-read-scope "users:read")

(def auth
  (m/oauth2
   {:authorization-endpoint "https://slack.com/oauth/v2/authorize"
    :token-endpoint "https://slack.com/api/oauth.v2.access"
    :client-id-env "SLACK_CLIENT_ID"
    :client-secret-env "SLACK_CLIENT_SECRET"
    :pkce? false}))

(def descriptor
  (-> (m/connector
       "com.slack" "Slack"
       {:summary "Read channels and history, post messages, look up users."
        :origin-domain "slack.com"
        :base-url base-url
        :docs-url "https://api.slack.com/methods"
        :auth auth})

      (m/add-tool
       "slack_list_channels"
       {:description "Public channels in the workspace."
        :effect :read
        :scopes [channels-read-scope]
        :input-schema {:type "object"
                       :properties {"limit" {:type "integer" :description "1-1000, default 100"}
                                    "cursor" {:type "string"}
                                    "exclude_archived" {:type "boolean"}}}})

      (m/add-tool
       "slack_channel_history"
       {:description "Recent messages in a channel."
        :effect :read
        :scopes [channels-history-scope]
        :input-schema {:type "object"
                       :properties {"channel" {:type "string" :description "Channel id, e.g. C0123ABC"}
                                    "limit" {:type "integer"}
                                    "oldest" {:type "string" :description "Unix ts, inclusive lower bound"}
                                    "cursor" {:type "string"}}
                       :required ["channel"]}})

      (m/add-tool
       "slack_list_users"
       {:description "Members of the workspace."
        :effect :read
        :scopes [users-read-scope]
        :input-schema {:type "object"
                       :properties {"limit" {:type "integer"}
                                    "cursor" {:type "string"}}}})

      (m/add-tool
       "slack_post_message"
       {:description "Post a message to a channel."
        :effect :write
        :scopes [chat-write-scope]
        :input-schema {:type "object"
                       :properties {"channel" {:type "string"}
                                    "text" {:type "string"}
                                    "thread_ts" {:type "string"
                                                 :description "Set to reply in a thread"}}
                       :required ["channel" "text"]}})))

;; --- requests ---

(defn request
  [tool-name args]
  (let [arg #(get args %)]
    (case tool-name
      "slack_list_channels"
      {:connector.http/method :get
       :connector.http/url (str base-url "/conversations.list")
       :connector.http/query (cond-> {"exclude_archived" (str (not (false? (arg "exclude_archived"))))
                                      "types" "public_channel"}
                               (arg "limit") (assoc "limit" (arg "limit"))
                               (arg "cursor") (assoc "cursor" (arg "cursor")))}

      "slack_channel_history"
      {:connector.http/method :get
       :connector.http/url (str base-url "/conversations.history")
       :connector.http/query (cond-> {"channel" (arg "channel")}
                               (arg "limit") (assoc "limit" (arg "limit"))
                               (arg "oldest") (assoc "oldest" (arg "oldest"))
                               (arg "cursor") (assoc "cursor" (arg "cursor")))}

      "slack_list_users"
      {:connector.http/method :get
       :connector.http/url (str base-url "/users.list")
       :connector.http/query (cond-> {}
                               (arg "limit") (assoc "limit" (arg "limit"))
                               (arg "cursor") (assoc "cursor" (arg "cursor")))}

      "slack_post_message"
      {:connector.http/method :post
       :connector.http/url (str base-url "/chat.postMessage")
       :connector.http/headers {"content-type" "application/json; charset=utf-8"}
       :connector.http/body (into {} (remove (comp nil? val))
                                  {"channel" (arg "channel")
                                   "text" (arg "text")
                                   "thread_ts" (arg "thread_ts")})})))

;; --- responses ---

(defn slack-error
  "Slack's failure shape, as an error value.

  `ok:false` arrives with HTTP 200, so `connector.invoke`'s status check cannot
  see it. Without this, `missing_scope` would be normalized into an empty list
  and read as 'the workspace has no channels'."
  [tool-name body]
  {:connector/error true
   :connector/code :slack/not-ok
   :connector/message (str "slack " tool-name " returned ok=false: "
                           (get body "error" "unknown"))
   :connector/tool tool-name
   :slack/error (get body "error")
   :slack/needed (get body "needed")
   :slack/provided (get body "provided")})

(defn- channel-row [c]
  {:id (get c "id")
   :name (get c "name")
   :private? (true? (get c "is_private"))
   :archived? (true? (get c "is_archived"))
   :member? (true? (get c "is_member"))
   :topic (get-in c ["topic" "value"])
   :members (get c "num_members")})

(defn- message-row [m]
  {:ts (get m "ts")
   :user (get m "user")
   :bot-id (get m "bot_id")
   :text (get m "text")
   :thread-ts (get m "thread_ts")
   :reply-count (get m "reply_count")})

(defn normalize
  [tool-name response]
  (let [body (:connector.http/body response)]
    (if-not (true? (get body "ok"))
      (slack-error tool-name body)
      (case tool-name
        "slack_list_channels"
        {:channels (mapv channel-row (get body "channels" []))
         :next-cursor (get-in body ["response_metadata" "next_cursor"])}

        "slack_channel_history"
        {:messages (mapv message-row (get body "messages" []))
         :has-more? (true? (get body "has_more"))
         :next-cursor (get-in body ["response_metadata" "next_cursor"])}

        "slack_list_users"
        {:users (mapv (fn [u] {:id (get u "id")
                               :name (get u "name")
                               :real-name (get u "real_name")
                               :bot? (true? (get u "is_bot"))
                               :deleted? (true? (get u "deleted"))
                               :email (get-in u ["profile" "email"])})
                      (get body "members" []))
         :next-cursor (get-in body ["response_metadata" "next_cursor"])}

        "slack_post_message"
        {:ts (get body "ts")
         :channel (get body "channel")}))))

(def provider
  (p/provider descriptor {:request request :normalize normalize}))
