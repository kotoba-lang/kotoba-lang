(ns google-calendar.connector
  "Google Calendar as a connector.

  Read paths and one write path, each declaring the scope it needs so a
  deployment that only wants free/busy asks for `calendar.readonly` and nothing
  else. `google_calendar_create_event` is the only tool carrying
  `calendar.events`, so dropping it (`connector.model/read-only`) drops the
  write scope with it.

  Free/busy is here first on purpose. ADR-2608093000 records that
  `tenant_connection`'s capability vocabulary is entirely inward-facing —
  `tenant.read`, `workspace.write`, `actor.invoke` — with nothing that means
  'this app may read the owner's free time'. `google_calendar_freebusy` is that
  missing outward capability, in the plane where it can actually be granted and
  revoked.

  Nothing here can obtain a credential. `request` receives a tool name and
  arguments; `connector.invoke` attaches the Authorization header."
  (:require [connector.model :as m]
            [connector.provider :as p]
            [connector.uri :as uri]))

(def base-url "https://www.googleapis.com")

(def read-scope "https://www.googleapis.com/auth/calendar.readonly")
(def events-scope "https://www.googleapis.com/auth/calendar.events")

(def auth
  (m/oauth2
   {:authorization-endpoint "https://accounts.google.com/o/oauth2/v2/auth"
    :token-endpoint "https://oauth2.googleapis.com/token"
    :profile-endpoint "https://openidconnect.googleapis.com/v1/userinfo"
    :client-id-env "GOOGLE_CLIENT_ID"
    :client-secret-env "GOOGLE_CLIENT_SECRET"
    :pkce? true
    :base-scopes ["openid" "email"]
    ;; access_type=offline is what makes a refresh token appear at all, and
    ;; prompt=consent is what makes a reconnect actually re-ask rather than
    ;; silently reissuing the scopes granted the first time — which matters
    ;; precisely because scopes here change with what is enabled.
    :extra {"access_type" "offline"
            "prompt" "consent"
            "include_granted_scopes" "true"}}))

(def descriptor
  (-> (m/connector
       "com.google.calendar" "Google Calendar"
       {:summary "Calendars, events and free/busy windows."
        :origin-domain "google.com"
        :base-url base-url
        :docs-url "https://developers.google.com/calendar/api/v3/reference"
        :auth auth})

      (m/add-tool
       "google_calendar_list_calendars"
       {:description "List the calendars this account can see."
        :effect :read
        :scopes [read-scope]
        :input-schema {:type "object"
                       :properties {"maxResults" {:type "integer"
                                                  :description "1-250, default 100"}}}})

      (m/add-tool
       "google_calendar_list_events"
       {:description "List events on a calendar within a time window."
        :effect :read
        :scopes [read-scope]
        :input-schema {:type "object"
                       :properties
                       {"calendarId" {:type "string"
                                      :description "Calendar id, or \"primary\""}
                        "timeMin" {:type "string" :description "RFC 3339 lower bound"}
                        "timeMax" {:type "string" :description "RFC 3339 upper bound"}
                        "maxResults" {:type "integer"}
                        "q" {:type "string" :description "Free-text search"}}
                       :required ["calendarId"]}})

      (m/add-tool
       "google_calendar_get_event"
       {:description "One event, in full."
        :effect :read
        :scopes [read-scope]
        :input-schema {:type "object"
                       :properties {"calendarId" {:type "string"}
                                    "eventId" {:type "string"}}
                       :required ["calendarId" "eventId"]}})

      (m/add-tool
       "google_calendar_freebusy"
       {:description "Busy windows for one or more calendars. Returns times only — no titles, attendees or descriptions."
        :effect :read
        :scopes [read-scope]
        :input-schema {:type "object"
                       :properties
                       {"timeMin" {:type "string" :description "RFC 3339 lower bound"}
                        "timeMax" {:type "string" :description "RFC 3339 upper bound"}
                        "calendarIds" {:type "array"
                                       :items {:type "string"}
                                       :description "Defaults to [\"primary\"]"}}
                       :required ["timeMin" "timeMax"]}})

      (m/add-tool
       "google_calendar_create_event"
       {:description "Create an event on a calendar."
        :effect :write
        :scopes [events-scope]
        :input-schema {:type "object"
                       :properties
                       {"calendarId" {:type "string"}
                        "summary" {:type "string"}
                        "description" {:type "string"}
                        "start" {:type "object" :description "{\"dateTime\": RFC 3339} or {\"date\": YYYY-MM-DD}"}
                        "end" {:type "object"}
                        "attendees" {:type "array" :items {:type "object"}}}
                       :required ["calendarId" "start" "end"]}})))

;; --- requests ---

(defn- calendar-url [calendar-id & segments]
  (apply str base-url "/calendar/v3/calendars/" (uri/encode calendar-id) segments))

(defn request
  "Tool call → HTTP request map. No credential: see the namespace docstring."
  [tool-name args]
  (let [arg #(get args %)]
    (case tool-name
      "google_calendar_list_calendars"
      {:connector.http/method :get
       :connector.http/url (str base-url "/calendar/v3/users/me/calendarList")
       :connector.http/query (cond-> {} (arg "maxResults") (assoc "maxResults" (arg "maxResults")))}

      "google_calendar_list_events"
      {:connector.http/method :get
       :connector.http/url (calendar-url (arg "calendarId") "/events")
       ;; singleEvents + startTime is the combination that expands recurring
       ;; events into occurrences. Without it a weekly stand-up is one row with
       ;; a recurrence rule, and every caller has to expand it themselves.
       :connector.http/query (cond-> {"singleEvents" "true" "orderBy" "startTime"}
                               (arg "timeMin") (assoc "timeMin" (arg "timeMin"))
                               (arg "timeMax") (assoc "timeMax" (arg "timeMax"))
                               (arg "maxResults") (assoc "maxResults" (arg "maxResults"))
                               (arg "q") (assoc "q" (arg "q")))}

      "google_calendar_get_event"
      {:connector.http/method :get
       :connector.http/url (calendar-url (arg "calendarId") "/events/" (uri/encode (arg "eventId")))}

      "google_calendar_freebusy"
      {:connector.http/method :post
       :connector.http/url (str base-url "/calendar/v3/freeBusy")
       :connector.http/headers {"content-type" "application/json"}
       :connector.http/body {"timeMin" (arg "timeMin")
                             "timeMax" (arg "timeMax")
                             "items" (mapv (fn [id] {"id" id})
                                           (or (seq (arg "calendarIds")) ["primary"]))}}

      "google_calendar_create_event"
      {:connector.http/method :post
       :connector.http/url (calendar-url (arg "calendarId") "/events")
       :connector.http/headers {"content-type" "application/json"}
       :connector.http/body (into {} (remove (comp nil? val))
                                  {"summary" (arg "summary")
                                   "description" (arg "description")
                                   "start" (arg "start")
                                   "end" (arg "end")
                                   "attendees" (arg "attendees")})})))

;; --- responses ---

(defn- event-row [e]
  {:id (get e "id")
   :summary (get e "summary")
   :start (get-in e ["start" "dateTime"] (get-in e ["start" "date"]))
   :end (get-in e ["end" "dateTime"] (get-in e ["end" "date"]))
   :status (get e "status")
   :html-link (get e "htmlLink")
   :organizer (get-in e ["organizer" "email"])
   :attendees (mapv #(get % "email") (get e "attendees" []))})

(defn normalize
  "Response → result. Trimmed to the fields a caller acts on: a Calendar event
  carries around forty keys and handing all of them to a model spends context
  on `iCalUID` and `etag`. `google_calendar_get_event` is the exception and
  returns the raw event, because 'in full' is what it is for."
  [tool-name response]
  (let [body (:connector.http/body response)]
    (case tool-name
      "google_calendar_list_calendars"
      {:calendars (mapv (fn [c] {:id (get c "id")
                                 :summary (get c "summary")
                                 :primary? (true? (get c "primary"))
                                 :access-role (get c "accessRole")
                                 :time-zone (get c "timeZone")})
                        (get body "items" []))}

      "google_calendar_list_events"
      {:events (mapv event-row (get body "items" []))
       :next-page-token (get body "nextPageToken")}

      "google_calendar_get_event" body

      "google_calendar_freebusy"
      {:busy (into {} (map (fn [[calendar-id v]]
                             [calendar-id (mapv (fn [w] {:start (get w "start")
                                                         :end (get w "end")})
                                                (get v "busy" []))]))
                   (get body "calendars" {}))
       :errors (into {} (keep (fn [[calendar-id v]]
                                (when-let [es (seq (get v "errors"))]
                                  [calendar-id es])))
                     (get body "calendars" {}))}

      "google_calendar_create_event" (event-row body))))

(def provider
  (p/provider descriptor {:request request :normalize normalize}))
