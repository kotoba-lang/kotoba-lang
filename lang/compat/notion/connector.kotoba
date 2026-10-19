(ns notion.connector
  "Notion as a connector.

  Lives here rather than in a new repository: the origin plane already gave
  notion.com to this one (ADR-2608040100). `notion.main` is the clean-room
  actor — Notion's API implemented *here*; this namespace is a client of the
  real one.

  **Notion has no scopes.** Its authorization endpoint takes no `scope`
  parameter at all: what an integration may touch is fixed when the integration
  is created and by which pages a person shares with it. So the descriptor
  declares `:scopes? false` and every tool declares none — and
  `connector.validate` rejects a scope declared here, because a consent screen
  printing scopes Notion will ignore is worse than one printing nothing.

  What that costs is worth stating plainly rather than hiding: **a Notion grant
  cannot be narrowed by this plane.** Enabling `notion_search` and enabling
  `notion_create_page` request exactly the same access. The narrowing that does
  exist lives in Notion — page-by-page sharing and the integration's
  capabilities — and an operator has to do it there.

  Notion also accepts only `client_secret_basic` at its token endpoint, so the
  profile says `:client-auth :basic`.

  Nothing here can obtain a credential; `connector.invoke` attaches it."
  (:require [connector.model :as m]
            [connector.provider :as p]
            [connector.uri :as uri]))

(def base-url "https://api.notion.com/v1")

(def api-version
  "Pinned. Notion dates its API and changes response shapes between versions;
  omitting the header makes the server pick, which is how a normalizer silently
  starts reading the wrong shape."
  "2022-06-28")

(def auth
  (m/oauth2
   {:authorization-endpoint "https://api.notion.com/v1/oauth/authorize"
    :token-endpoint "https://api.notion.com/v1/oauth/token"
    :client-id-env "NOTION_CLIENT_ID"
    :client-secret-env "NOTION_CLIENT_SECRET"
    :pkce? false
    :scopes? false
    :client-auth :basic
    :extra {"owner" "user"}}))

(def descriptor
  (-> (m/connector
       "com.notion" "Notion"
       {:summary "Search a workspace, read pages and databases, create a page."
        :origin-domain "notion.com"
        :base-url base-url
        :docs-url "https://developers.notion.com/reference/intro"
        :auth auth})

      (m/add-tool
       "notion_search"
       {:description "Search pages and databases the integration has been shared with."
        :effect :read
        :input-schema {:type "object"
                       :properties {"query" {:type "string"}
                                    "filter_object" {:type "string"
                                                     :description "page or database"}
                                    "page_size" {:type "integer" :description "1-100"}
                                    "start_cursor" {:type "string"}}}})

      (m/add-tool
       "notion_get_page"
       {:description "One page's properties. Block content is a separate call — see notion_get_block_children."
        :effect :read
        :input-schema {:type "object"
                       :properties {"page_id" {:type "string"}}
                       :required ["page_id"]}})

      (m/add-tool
       "notion_get_block_children"
       {:description "The blocks inside a page or block — this is where the text lives."
        :effect :read
        :input-schema {:type "object"
                       :properties {"block_id" {:type "string"}
                                    "page_size" {:type "integer"}
                                    "start_cursor" {:type "string"}}
                       :required ["block_id"]}})

      (m/add-tool
       "notion_query_database"
       {:description "Rows of a database, with optional filter and sort."
        :effect :read
        :input-schema {:type "object"
                       :properties {"database_id" {:type "string"}
                                    "filter" {:type "object"}
                                    "sorts" {:type "array" :items {:type "object"}}
                                    "page_size" {:type "integer"}
                                    "start_cursor" {:type "string"}}
                       :required ["database_id"]}})

      (m/add-tool
       "notion_create_page"
       {:description "Create a page under a parent page or database."
        :effect :write
        :input-schema {:type "object"
                       :properties {"parent" {:type "object"
                                              :description "{\"page_id\": …} or {\"database_id\": …}"}
                                    "properties" {:type "object"}
                                    "children" {:type "array" :items {:type "object"}}}
                       :required ["parent" "properties"]}})))

;; --- requests ---

(def ^:private headers
  {"notion-version" api-version
   "accept" "application/json"})

(def ^:private json-headers
  (assoc headers "content-type" "application/json"))

(defn request
  [tool-name args]
  (let [arg #(get args %)]
    (case tool-name
      "notion_search"
      {:connector.http/method :post
       :connector.http/url (str base-url "/search")
       :connector.http/headers json-headers
       :connector.http/body (into {} (remove (comp nil? val))
                                  {"query" (arg "query")
                                   "page_size" (arg "page_size")
                                   "start_cursor" (arg "start_cursor")
                                   "filter" (when-let [o (arg "filter_object")]
                                              {"property" "object" "value" o})})}

      "notion_get_page"
      {:connector.http/method :get
       :connector.http/url (str base-url "/pages/" (uri/encode (arg "page_id")))
       :connector.http/headers headers}

      "notion_get_block_children"
      {:connector.http/method :get
       :connector.http/url (str base-url "/blocks/" (uri/encode (arg "block_id")) "/children")
       :connector.http/headers headers
       :connector.http/query (cond-> {}
                               (arg "page_size") (assoc "page_size" (arg "page_size"))
                               (arg "start_cursor") (assoc "start_cursor" (arg "start_cursor")))}

      "notion_query_database"
      {:connector.http/method :post
       :connector.http/url (str base-url "/databases/" (uri/encode (arg "database_id")) "/query")
       :connector.http/headers json-headers
       :connector.http/body (into {} (remove (comp nil? val))
                                  {"filter" (arg "filter")
                                   "sorts" (arg "sorts")
                                   "page_size" (arg "page_size")
                                   "start_cursor" (arg "start_cursor")})}

      "notion_create_page"
      {:connector.http/method :post
       :connector.http/url (str base-url "/pages")
       :connector.http/headers json-headers
       :connector.http/body (into {} (remove (comp nil? val))
                                  {"parent" (arg "parent")
                                   "properties" (arg "properties")
                                   "children" (arg "children")})})))

;; --- responses ---

(defn- rich-text
  "Notion returns text as a list of annotated runs. Joining the plain_text is
  what a caller means by 'the title' — the annotations are formatting."
  [runs]
  (apply str (map #(get % "plain_text") runs)))

(defn- title-of
  "A page's title lives under whichever property has type \"title\", and that
  property's NAME is chosen by whoever built the database. Looking for
  \"Name\" works until somebody renames it."
  [page]
  (some (fn [[_ prop]]
          (when (= "title" (get prop "type"))
            (rich-text (get prop "title"))))
        (get page "properties" {})))

(defn- object-row [o]
  (cond-> {:id (get o "id")
           :object (get o "object")
           :url (get o "url")
           :created-time (get o "created_time")
           :last-edited-time (get o "last_edited_time")
           :archived? (true? (get o "archived"))}
    (= "page" (get o "object")) (assoc :title (title-of o))
    (= "database" (get o "object")) (assoc :title (rich-text (get o "title" [])))))

(defn- block-row [b]
  (let [t (get b "type")]
    {:id (get b "id")
     :type t
     :has-children? (true? (get b "has_children"))
     :text (rich-text (get-in b [t "rich_text"] []))}))

(defn normalize
  [tool-name response]
  (let [body (:connector.http/body response)]
    (case tool-name
      "notion_search"
      {:results (mapv object-row (get body "results" []))
       :next-cursor (get body "next_cursor")
       :has-more? (true? (get body "has_more"))}

      "notion_get_page" (assoc (object-row body) :properties (get body "properties"))

      "notion_get_block_children"
      {:blocks (mapv block-row (get body "results" []))
       :next-cursor (get body "next_cursor")
       :has-more? (true? (get body "has_more"))}

      "notion_query_database"
      {:rows (mapv (fn [p] (assoc (object-row p) :properties (get p "properties")))
                   (get body "results" []))
       :next-cursor (get body "next_cursor")
       :has-more? (true? (get body "has_more"))}

      "notion_create_page" (object-row body))))

(def provider
  (p/provider descriptor {:request request :normalize normalize}))
