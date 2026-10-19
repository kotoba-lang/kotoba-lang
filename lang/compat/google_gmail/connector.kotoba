(ns google-gmail.connector
  "Gmail as a connector.

  Three scopes, not one. `cloud-itonami-app` currently holds `gmail.modify` for
  everything because one provider entry carries one scope list, and `modify`
  covers reading — so a deployment that only wanted to search the mailbox also
  held permission to file and relabel it. Here reading asks for
  `gmail.readonly`, filing asks for `gmail.modify`, and sending asks for
  `gmail.send`, so what is granted follows what is enabled.

  No tool deletes anything, and none asks for `https://mail.google.com/` (full
  account access, including permanent delete). Trash is reversible and nothing
  here needs more than that.

  Nothing here can obtain a credential; `connector.invoke` attaches it."
  (:require [clojure.string :as str]
            [connector.model :as m]
            [connector.provider :as p]
            [connector.uri :as uri]))

(def base-url "https://gmail.googleapis.com")

(def read-scope "https://www.googleapis.com/auth/gmail.readonly")
(def modify-scope "https://www.googleapis.com/auth/gmail.modify")
(def send-scope "https://www.googleapis.com/auth/gmail.send")

(def auth
  (m/oauth2
   {:authorization-endpoint "https://accounts.google.com/o/oauth2/v2/auth"
    :token-endpoint "https://oauth2.googleapis.com/token"
    :profile-endpoint "https://openidconnect.googleapis.com/v1/userinfo"
    :client-id-env "GOOGLE_CLIENT_ID"
    :client-secret-env "GOOGLE_CLIENT_SECRET"
    :pkce? true
    :base-scopes ["openid" "email"]
    :extra {"access_type" "offline"
            "prompt" "consent"
            "include_granted_scopes" "true"}}))

(def descriptor
  (-> (m/connector
       "com.google.gmail" "Gmail"
       {:summary "Search a mailbox, read a message, file it under labels, send mail."
        :origin-domain "google.com"
        :base-url base-url
        :docs-url "https://developers.google.com/gmail/api/reference/rest"
        :auth auth})

      (m/add-tool
       "gmail_search_messages"
       {:description "Search the mailbox with Gmail query syntax, e.g. from:jun@example.com is:unread newer_than:7d."
        :effect :read
        :scopes [read-scope]
        :input-schema {:type "object"
                       :properties {"q" {:type "string" :description "Gmail search query"}
                                    "maxResults" {:type "integer" :description "1-500, default 100"}
                                    "pageToken" {:type "string"}
                                    "labelIds" {:type "array" :items {:type "string"}}}}})

      (m/add-tool
       "gmail_get_message"
       {:description "One message: headers always, body when format is \"full\"."
        :effect :read
        :scopes [read-scope]
        :input-schema {:type "object"
                       :properties {"id" {:type "string"}
                                    "format" {:type "string"
                                              :description "metadata (default) or full"}}
                       :required ["id"]}})

      (m/add-tool
       "gmail_list_labels"
       {:description "The mailbox's labels, with their ids."
        :effect :read
        :scopes [read-scope]
        :input-schema {:type "object" :properties {}}})

      (m/add-tool
       "gmail_modify_labels"
       {:description "File a message: add and/or remove labels. Moving to TRASH is reversible; nothing here deletes permanently."
        :effect :write
        :scopes [modify-scope]
        :input-schema {:type "object"
                       :properties {"id" {:type "string"}
                                    "addLabelIds" {:type "array" :items {:type "string"}}
                                    "removeLabelIds" {:type "array" :items {:type "string"}}}
                       :required ["id"]}})

      (m/add-tool
       "gmail_send_message"
       {:description "Send a message. `raw` is the RFC 5322 message, base64url-encoded, as the Gmail API requires."
        :effect :write
        :scopes [send-scope]
        :input-schema {:type "object"
                       :properties {"raw" {:type "string"
                                           :description "base64url-encoded RFC 5322 message"}
                                    "threadId" {:type "string"
                                                :description "Set to reply within an existing thread"}}
                       :required ["raw"]}})))

;; --- requests ---

(def ^:private messages-url (str base-url "/gmail/v1/users/me/messages"))

(defn request
  [tool-name args]
  (let [arg #(get args %)]
    (case tool-name
      "gmail_search_messages"
      {:connector.http/method :get
       :connector.http/url messages-url
       :connector.http/query (cond-> {}
                               (arg "q") (assoc "q" (arg "q"))
                               (arg "maxResults") (assoc "maxResults" (arg "maxResults"))
                               (arg "pageToken") (assoc "pageToken" (arg "pageToken"))
                               ;; Gmail takes labelIds as a repeated parameter.
                               ;; The query map is single-valued, so a single
                               ;; label works and several do not -- stated here
                               ;; rather than silently sending the first.
                               (= 1 (count (arg "labelIds")))
                               (assoc "labelIds" (first (arg "labelIds"))))}

      "gmail_get_message"
      {:connector.http/method :get
       :connector.http/url (str messages-url "/" (uri/encode (arg "id")))
       :connector.http/query {"format" (or (arg "format") "metadata")}}

      "gmail_list_labels"
      {:connector.http/method :get
       :connector.http/url (str base-url "/gmail/v1/users/me/labels")}

      "gmail_modify_labels"
      {:connector.http/method :post
       :connector.http/url (str messages-url "/" (uri/encode (arg "id")) "/modify")
       :connector.http/headers {"content-type" "application/json"}
       :connector.http/body (into {} (remove (comp nil? val))
                                  {"addLabelIds" (arg "addLabelIds")
                                   "removeLabelIds" (arg "removeLabelIds")})}

      "gmail_send_message"
      {:connector.http/method :post
       :connector.http/url (str messages-url "/send")
       :connector.http/headers {"content-type" "application/json"}
       :connector.http/body (into {} (remove (comp nil? val))
                                  {"raw" (arg "raw")
                                   "threadId" (arg "threadId")})})))

;; --- responses ---

(defn- header-map
  "Gmail returns headers as [{name, value}], which is a list a caller has to
  search every time. Lower-cased keys because header names are case-insensitive
  and Gmail does not normalize them."
  [message]
  (into {} (map (fn [h] [(some-> (get h "name") str/lower-case) (get h "value")]))
        (get-in message ["payload" "headers"] [])))

(defn- message-row [message]
  (let [headers (header-map message)]
    {:id (get message "id")
     :thread-id (get message "threadId")
     :snippet (get message "snippet")
     :label-ids (vec (get message "labelIds" []))
     :from (get headers "from")
     :to (get headers "to")
     :subject (get headers "subject")
     :date (get headers "date")}))

(defn normalize
  [tool-name response]
  (let [body (:connector.http/body response)]
    (case tool-name
      ;; messages.list returns ids only -- no headers, no snippet. Saying so is
      ;; the honest normalization: inventing empty :subject keys here would
      ;; read as "this message has no subject".
      "gmail_search_messages"
      {:message-ids (mapv #(get % "id") (get body "messages" []))
       :result-size-estimate (get body "resultSizeEstimate")
       :next-page-token (get body "nextPageToken")
       :note "ids only; call gmail_get_message for headers"}

      "gmail_get_message"
      (assoc (message-row body)
             :payload (get body "payload"))

      "gmail_list_labels"
      {:labels (mapv (fn [l] {:id (get l "id")
                              :name (get l "name")
                              :type (get l "type")})
                     (get body "labels" []))}

      "gmail_modify_labels" (message-row body)

      "gmail_send_message" {:id (get body "id")
                            :thread-id (get body "threadId")
                            :label-ids (vec (get body "labelIds" []))})))

(def provider
  (p/provider descriptor {:request request :normalize normalize}))
