(ns google-drive.connector
  "Google Drive as a connector.

  Metadata and text, not bytes. There is deliberately no `download` tool:
  `connector.ports/IHttp` hands back an already-parsed body, which is the right
  contract for JSON and the wrong one for a 200 MB video, and this workspace
  keeps bytes on the object plane rather than in the query plane
  (`kotobase.storage`'s large-object profile). A connector that returned a
  base64 blob through a tool result would put a file in a model's context
  window, which is neither cheap nor reversible.

  `google_drive_export_document` is the exception that shows the line: it asks
  Drive to render a Google Doc as `text/plain`, so what comes back is text a
  caller can actually read. It is also the only tool needing `drive.readonly`
  (content) rather than `drive.metadata.readonly`, and it says so — a
  deployment that only lists files never asks for permission to read them.

  Nothing here can obtain a credential; `connector.invoke` attaches it."
  (:require [connector.model :as m]
            [connector.provider :as p]
            [connector.uri :as uri]))

(def base-url "https://www.googleapis.com")

(def metadata-scope "https://www.googleapis.com/auth/drive.metadata.readonly")
(def content-scope "https://www.googleapis.com/auth/drive.readonly")

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

(def ^:private file-fields
  "id,name,mimeType,modifiedTime,size,owners(emailAddress),webViewLink,parents,trashed")

(def descriptor
  (-> (m/connector
       "com.google.drive" "Google Drive"
       {:summary "Search Drive, read file metadata and permissions, export a document as text."
        :origin-domain "google.com"
        :base-url base-url
        :docs-url "https://developers.google.com/drive/api/v3/reference"
        :auth auth})

      (m/add-tool
       "google_drive_search_files"
       {:description "Search files by Drive query syntax, e.g. name contains 'budget' and mimeType != 'application/vnd.google-apps.folder'."
        :effect :read
        :scopes [metadata-scope]
        :input-schema {:type "object"
                       :properties
                       {"q" {:type "string" :description "Drive query string"}
                        "pageSize" {:type "integer" :description "1-1000, default 100"}
                        "pageToken" {:type "string"}
                        "orderBy" {:type "string"
                                   :description "e.g. modifiedTime desc"}}}})

      (m/add-tool
       "google_drive_get_file"
       {:description "Metadata for one file."
        :effect :read
        :scopes [metadata-scope]
        :input-schema {:type "object"
                       :properties {"fileId" {:type "string"}}
                       :required ["fileId"]}})

      (m/add-tool
       "google_drive_list_permissions"
       {:description "Who can see a file. Answers 'is this shared outside the org' without reading the contents."
        :effect :read
        :scopes [metadata-scope]
        :input-schema {:type "object"
                       :properties {"fileId" {:type "string"}}
                       :required ["fileId"]}})

      (m/add-tool
       "google_drive_export_document"
       {:description "Export a Google Docs/Sheets/Slides file as text. Not for binary files — see this connector's README."
        :effect :read
        :scopes [content-scope]
        :input-schema {:type "object"
                       :properties
                       {"fileId" {:type "string"}
                        "mimeType" {:type "string"
                                    :description "text/plain (default), text/csv, text/markdown"}}
                       :required ["fileId"]}})))

;; --- requests ---

(defn- file-url [file-id & segments]
  (apply str base-url "/drive/v3/files/" (uri/encode file-id) segments))

(defn request
  [tool-name args]
  (let [arg #(get args %)]
    (case tool-name
      "google_drive_search_files"
      {:connector.http/method :get
       :connector.http/url (str base-url "/drive/v3/files")
       ;; `fields` is not an optimisation. Drive's default projection is
       ;; id/name/mimeType only, so a caller asking "when was this last
       ;; touched" gets nothing back and no error saying why.
       :connector.http/query (cond-> {"fields" (str "nextPageToken,files(" file-fields ")")}
                               (arg "q") (assoc "q" (arg "q"))
                               (arg "pageSize") (assoc "pageSize" (arg "pageSize"))
                               (arg "pageToken") (assoc "pageToken" (arg "pageToken"))
                               (arg "orderBy") (assoc "orderBy" (arg "orderBy")))}

      "google_drive_get_file"
      {:connector.http/method :get
       :connector.http/url (file-url (arg "fileId"))
       :connector.http/query {"fields" file-fields}}

      "google_drive_list_permissions"
      {:connector.http/method :get
       :connector.http/url (file-url (arg "fileId") "/permissions")
       :connector.http/query {"fields" "permissions(id,type,role,emailAddress,domain,allowFileDiscovery)"}}

      "google_drive_export_document"
      {:connector.http/method :get
       :connector.http/url (file-url (arg "fileId") "/export")
       :connector.http/query {"mimeType" (or (arg "mimeType") "text/plain")}})))

;; --- responses ---

(defn- file-row [f]
  {:id (get f "id")
   :name (get f "name")
   :mime-type (get f "mimeType")
   :modified-time (get f "modifiedTime")
   :size (get f "size")
   :owners (mapv #(get % "emailAddress") (get f "owners" []))
   :web-view-link (get f "webViewLink")
   :trashed? (true? (get f "trashed"))})

(defn normalize
  [tool-name response]
  (let [body (:connector.http/body response)]
    (case tool-name
      "google_drive_search_files"
      {:files (mapv file-row (get body "files" []))
       :next-page-token (get body "nextPageToken")}

      "google_drive_get_file" (file-row body)

      "google_drive_list_permissions"
      {:permissions (mapv (fn [p] {:id (get p "id")
                                   :type (get p "type")
                                   :role (get p "role")
                                   :email (get p "emailAddress")
                                   :domain (get p "domain")
                                   :discoverable? (true? (get p "allowFileDiscovery"))})
                          (get body "permissions" []))}

      ;; An export is text, and the transport hands it over as the body. It is
      ;; returned as-is rather than wrapped: a caller asked for the document.
      "google_drive_export_document" {:text (if (string? body) body (str body))})))

(def provider
  (p/provider descriptor {:request request :normalize normalize}))
