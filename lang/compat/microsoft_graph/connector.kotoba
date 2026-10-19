(ns microsoft-graph.connector
  "Microsoft Graph as a connector — mail and calendar over one Microsoft 365
  grant.

  Graph is one API over many services, so this connector is named for the API
  rather than for a product: `graph.microsoft.com` is the authority, and
  `Mail.Read` and `Calendars.Read` are two scopes on one client rather than two
  connectors. That is the opposite decision from Google, where Drive, Gmail and
  Calendar are three APIs under one OAuth client — there they are three
  connectors that `connector.consent` groups back into one grant.

  `offline_access` is in the base scopes because without it Microsoft issues no
  refresh token at all, and a connector that has to be reauthorized by hand
  every hour is one nobody leaves enabled.

  Nothing here can obtain a credential; `connector.invoke` attaches it."
  (:require [connector.model :as m]
            [connector.provider :as p]
            [connector.uri :as uri]))

(def base-url "https://graph.microsoft.com/v1.0")

(def user-scope "User.Read")
(def mail-read-scope "Mail.Read")
(def mail-send-scope "Mail.Send")
(def calendar-scope "Calendars.Read")

(def auth
  (m/oauth2
   {:authorization-endpoint "https://login.microsoftonline.com/organizations/oauth2/v2.0/authorize"
    :token-endpoint "https://login.microsoftonline.com/organizations/oauth2/v2.0/token"
    :profile-endpoint "https://graph.microsoft.com/v1.0/me"
    :client-id-env "M365_CLIENT_ID"
    :client-secret-env "M365_CLIENT_SECRET"
    :pkce? true
    :base-scopes ["openid" "email" "profile" "offline_access"]}))

(def ^:private message-select
  "id,subject,from,toRecipients,receivedDateTime,isRead,hasAttachments,webLink,bodyPreview")

(def ^:private event-select
  "id,subject,start,end,location,organizer,attendees,isAllDay,webLink,showAs")

(def descriptor
  (-> (m/connector
       "com.microsoft.graph" "Microsoft 365"
       {:summary "Mail and calendar on Microsoft 365, through Microsoft Graph."
        :origin-domain "microsoft.com"
        :base-url base-url
        :docs-url "https://learn.microsoft.com/graph/api/overview"
        :auth auth})

      (m/add-tool
       "microsoft_graph_get_profile"
       {:description "The signed-in account: display name, mail address, time zone."
        :effect :read
        :scopes [user-scope]
        :input-schema {:type "object" :properties {}}})

      (m/add-tool
       "microsoft_graph_list_messages"
       {:description "Messages in the mailbox, newest first. `search` uses Graph's $search."
        :effect :read
        :scopes [mail-read-scope]
        :input-schema {:type "object"
                       :properties {"top" {:type "integer" :description "1-999, default 25"}
                                    "filter" {:type "string" :description "OData $filter"}
                                    "search" {:type "string" :description "OData $search"}}}})

      (m/add-tool
       "microsoft_graph_get_message"
       {:description "One message, with its body."
        :effect :read
        :scopes [mail-read-scope]
        :input-schema {:type "object"
                       :properties {"id" {:type "string"}}
                       :required ["id"]}})

      (m/add-tool
       "microsoft_graph_send_mail"
       {:description "Send a message as the signed-in account."
        :effect :write
        :scopes [mail-send-scope]
        :input-schema {:type "object"
                       :properties {"subject" {:type "string"}
                                    "body" {:type "string"}
                                    "contentType" {:type "string" :description "Text (default) or HTML"}
                                    "to" {:type "array" :items {:type "string"}
                                          :description "Recipient addresses"}
                                    "saveToSentItems" {:type "boolean"}}
                       :required ["to" "body"]}})

      (m/add-tool
       "microsoft_graph_list_events"
       {:description "Calendar events in a time window."
        :effect :read
        :scopes [calendar-scope]
        :input-schema {:type "object"
                       :properties {"startDateTime" {:type "string" :description "ISO 8601"}
                                    "endDateTime" {:type "string" :description "ISO 8601"}
                                    "top" {:type "integer"}}}})

      (m/add-tool
       "microsoft_graph_get_schedule"
       {:description "Free/busy for one or more addresses. Returns availability only — no titles or attendees."
        :effect :read
        :scopes [calendar-scope]
        :input-schema {:type "object"
                       :properties
                       {"schedules" {:type "array" :items {:type "string"}
                                     :description "Addresses to query"}
                        "startTime" {:type "string" :description "ISO 8601"}
                        "endTime" {:type "string" :description "ISO 8601"}
                        "availabilityViewInterval" {:type "integer"
                                                    :description "Minutes per slot, default 30"}
                        "timeZone" {:type "string" :description "Default UTC"}}
                       :required ["schedules" "startTime" "endTime"]}})))

;; --- requests ---

(defn- time-zone-map [value time-zone]
  {"dateTime" value "timeZone" (or time-zone "UTC")})

(defn request
  [tool-name args]
  (let [arg #(get args %)]
    (case tool-name
      "microsoft_graph_get_profile"
      {:connector.http/method :get
       :connector.http/url (str base-url "/me")}

      "microsoft_graph_list_messages"
      {:connector.http/method :get
       :connector.http/url (str base-url "/me/messages")
       ;; $orderby and $search cannot be combined -- Graph answers 400. The
       ;; ordering is dropped when a search is asked for, because a search
       ;; result is already relevance-ranked and the alternative is a request
       ;; that fails for a reason the caller cannot see.
       :connector.http/query (cond-> {"$select" message-select}
                               (not (arg "search")) (assoc "$orderby" "receivedDateTime desc")
                               (arg "top") (assoc "$top" (arg "top"))
                               (arg "filter") (assoc "$filter" (arg "filter"))
                               (arg "search") (assoc "$search" (str "\"" (arg "search") "\"")))}

      "microsoft_graph_get_message"
      {:connector.http/method :get
       :connector.http/url (str base-url "/me/messages/" (uri/encode (arg "id")))}

      "microsoft_graph_send_mail"
      {:connector.http/method :post
       :connector.http/url (str base-url "/me/sendMail")
       :connector.http/headers {"content-type" "application/json"}
       :connector.http/body
       {"message" {"subject" (arg "subject")
                   "body" {"contentType" (or (arg "contentType") "Text")
                           "content" (arg "body")}
                   "toRecipients" (mapv (fn [a] {"emailAddress" {"address" a}})
                                        (arg "to"))}
        ;; Defaults to true rather than being omitted: a message the sender
        ;; cannot find in Sent Items later is one they cannot prove they sent.
        "saveToSentItems" (if (contains? args "saveToSentItems")
                            (boolean (arg "saveToSentItems"))
                            true)}}

      "microsoft_graph_list_events"
      (if (and (arg "startDateTime") (arg "endDateTime"))
        ;; calendarView expands recurring events into occurrences; /me/events
        ;; returns the series master. With a window given, the occurrences are
        ;; what a caller means by "events this week".
        {:connector.http/method :get
         :connector.http/url (str base-url "/me/calendarView")
         :connector.http/query (cond-> {"$select" event-select
                                        "startDateTime" (arg "startDateTime")
                                        "endDateTime" (arg "endDateTime")
                                        "$orderby" "start/dateTime"}
                                 (arg "top") (assoc "$top" (arg "top")))}
        {:connector.http/method :get
         :connector.http/url (str base-url "/me/events")
         :connector.http/query (cond-> {"$select" event-select
                                        "$orderby" "start/dateTime"}
                                 (arg "top") (assoc "$top" (arg "top")))})

      "microsoft_graph_get_schedule"
      {:connector.http/method :post
       :connector.http/url (str base-url "/me/calendar/getSchedule")
       :connector.http/headers {"content-type" "application/json"}
       :connector.http/body {"schedules" (vec (arg "schedules"))
                             "startTime" (time-zone-map (arg "startTime") (arg "timeZone"))
                             "endTime" (time-zone-map (arg "endTime") (arg "timeZone"))
                             "availabilityViewInterval"
                             (or (arg "availabilityViewInterval") 30)}})))

;; --- responses ---

(defn- message-row [m]
  {:id (get m "id")
   :subject (get m "subject")
   :from (get-in m ["from" "emailAddress" "address"])
   :to (mapv #(get-in % ["emailAddress" "address"]) (get m "toRecipients" []))
   :received (get m "receivedDateTime")
   :read? (true? (get m "isRead"))
   :has-attachments? (true? (get m "hasAttachments"))
   :preview (get m "bodyPreview")
   :web-link (get m "webLink")})

(defn- event-row [e]
  {:id (get e "id")
   :subject (get e "subject")
   :start (get-in e ["start" "dateTime"])
   :end (get-in e ["end" "dateTime"])
   :time-zone (get-in e ["start" "timeZone"])
   :all-day? (true? (get e "isAllDay"))
   :location (get-in e ["location" "displayName"])
   :organizer (get-in e ["organizer" "emailAddress" "address"])
   :attendees (mapv #(get-in % ["emailAddress" "address"]) (get e "attendees" []))
   :show-as (get e "showAs")
   :web-link (get e "webLink")})

(defn normalize
  [tool-name response]
  (let [body (:connector.http/body response)]
    (case tool-name
      "microsoft_graph_get_profile"
      {:id (get body "id")
       :display-name (get body "displayName")
       :mail (or (get body "mail") (get body "userPrincipalName"))
       :time-zone (get body "preferredLanguage")}

      "microsoft_graph_list_messages"
      {:messages (mapv message-row (get body "value" []))
       :next-link (get body "@odata.nextLink")}

      "microsoft_graph_get_message"
      (assoc (message-row body) :body (get-in body ["body" "content"]))

      ;; sendMail answers 202 with no body. Saying "accepted" is the whole
      ;; result; returning nil would read as a failure that did not happen.
      "microsoft_graph_send_mail"
      {:accepted true :status (:connector.http/status response)}

      "microsoft_graph_list_events"
      {:events (mapv event-row (get body "value" []))
       :next-link (get body "@odata.nextLink")}

      "microsoft_graph_get_schedule"
      {:schedules (mapv (fn [s]
                          {:address (get s "scheduleId")
                           :availability-view (get s "availabilityView")
                           :busy (mapv (fn [i] {:start (get-in i ["start" "dateTime"])
                                                :end (get-in i ["end" "dateTime"])
                                                :status (get i "status")})
                                       (get s "scheduleItems" []))
                           :error (get-in s ["error" "message"])})
                        (get body "value" []))})))

(def provider
  (p/provider descriptor {:request request :normalize normalize}))
