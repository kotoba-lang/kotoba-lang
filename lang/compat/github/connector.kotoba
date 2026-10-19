(ns github.connector
  "GitHub as a connector.

  This lives here rather than in a repository of its own because the origin
  plane already gave GitHub to this one: two repositories for one subject in
  one plane is not allowed (ADR-2608040100), and creating `com-github-rest`
  would have been both a rule violation and a re-invention of code sitting
  beside it.

  Boundary with the rest of this repository:

    github.main       the clean-room actor — GitHub's API implemented HERE
    github.workflow   a client that normalizes one repository into a snapshot,
                      driven by a caller-supplied request-fn
    github.connector  a client exposed as TOOLS, with an OAuth profile and
                      per-tool scopes, so an agent can be granted part of it

  `workflow` and `connector` are both clients and deliberately not layered on
  each other. `workflow` answers one question — 'what is the state of this
  repository' — by fetching a fixed plan; a connector answers whatever the tool
  was called with. Making one call the other would force the plan-shaped API
  onto ad-hoc calls, or the ad-hoc API onto a plan. They share the origin
  (`api.github.com`) and nothing else.

  GitHub's OAuth Apps do not verify PKCE, and the descriptor says `:pkce? false`
  rather than sending a challenge nobody checks — recording `true` would state a
  protection that is not in force.

  Nothing here can obtain a credential; `connector.invoke` attaches it."
  (:require [connector.model :as m]
            [connector.provider :as p]
            [connector.uri :as uri]))

(def base-url "https://api.github.com")

(def repo-scope "repo")
(def read-org-scope "read:org")
(def read-user-scope "read:user")

(def auth
  (m/oauth2
   {:authorization-endpoint "https://github.com/login/oauth/authorize"
    :token-endpoint "https://github.com/login/oauth/access_token"
    :profile-endpoint "https://api.github.com/user"
    :client-id-env "GITHUB_CLIENT_ID"
    :client-secret-env "GITHUB_CLIENT_SECRET"
    ;; GitHub's OAuth Apps ignore code_challenge. See the namespace docstring.
    :pkce? false}))

(def descriptor
  (-> (m/connector
       "com.github" "GitHub"
       {:summary "Repositories, issues and pull requests on GitHub."
        :origin-domain "github.com"
        :base-url base-url
        :docs-url "https://docs.github.com/rest"
        :auth auth})

      (m/add-tool
       "github_get_authenticated_user"
       {:description "The account this grant belongs to."
        :effect :read
        :scopes [read-user-scope]
        :input-schema {:type "object" :properties {}}})

      (m/add-tool
       "github_list_repositories"
       {:description "Repositories this grant can see, most recently pushed first."
        :effect :read
        :scopes [repo-scope]
        :input-schema {:type "object"
                       :properties {"per_page" {:type "integer" :description "1-100, default 30"}
                                    "page" {:type "integer"}
                                    "affiliation" {:type "string"
                                                   :description "owner, collaborator, organization_member"}}}})

      (m/add-tool
       "github_get_repository"
       {:description "One repository's metadata."
        :effect :read
        :scopes [repo-scope]
        :input-schema {:type "object"
                       :properties {"owner" {:type "string"}
                                    "repo" {:type "string"}}
                       :required ["owner" "repo"]}})

      (m/add-tool
       "github_list_issues"
       {:description "Issues on a repository. Pull requests are excluded — GitHub returns them from this endpoint too, and a caller asking for issues does not mean PRs."
        :effect :read
        :scopes [repo-scope]
        :input-schema {:type "object"
                       :properties {"owner" {:type "string"}
                                    "repo" {:type "string"}
                                    "state" {:type "string" :description "open (default), closed, all"}
                                    "labels" {:type "string" :description "Comma-separated"}
                                    "per_page" {:type "integer"}}
                       :required ["owner" "repo"]}})

      (m/add-tool
       "github_list_pull_requests"
       {:description "Pull requests on a repository."
        :effect :read
        :scopes [repo-scope]
        :input-schema {:type "object"
                       :properties {"owner" {:type "string"}
                                    "repo" {:type "string"}
                                    "state" {:type "string" :description "open (default), closed, all"}
                                    "per_page" {:type "integer"}}
                       :required ["owner" "repo"]}})

      (m/add-tool
       "github_list_organizations"
       {:description "Organizations this grant can see."
        :effect :read
        :scopes [read-org-scope]
        :input-schema {:type "object" :properties {}}})

      (m/add-tool
       "github_create_issue"
       {:description "Open an issue on a repository."
        :effect :write
        :scopes [repo-scope]
        :input-schema {:type "object"
                       :properties {"owner" {:type "string"}
                                    "repo" {:type "string"}
                                    "title" {:type "string"}
                                    "body" {:type "string"}
                                    "labels" {:type "array" :items {:type "string"}}}
                       :required ["owner" "repo" "title"]}})))

;; --- requests ---

(def ^:private api-headers
  ;; The version header is pinned rather than left to GitHub's default, which
  ;; moves. A response shape changing under a normalizer that was written
  ;; against the old one fails as wrong data, not as an error.
  {"accept" "application/vnd.github+json"
   "x-github-api-version" "2022-11-28"})

(defn- repo-url [args & segments]
  (apply str base-url "/repos/" (uri/encode (get args "owner"))
         "/" (uri/encode (get args "repo")) segments))

(defn request
  [tool-name args]
  (let [arg #(get args %)]
    (case tool-name
      "github_get_authenticated_user"
      {:connector.http/method :get
       :connector.http/url (str base-url "/user")
       :connector.http/headers api-headers}

      "github_list_repositories"
      {:connector.http/method :get
       :connector.http/url (str base-url "/user/repos")
       :connector.http/headers api-headers
       :connector.http/query (cond-> {"sort" "pushed"}
                               (arg "per_page") (assoc "per_page" (arg "per_page"))
                               (arg "page") (assoc "page" (arg "page"))
                               (arg "affiliation") (assoc "affiliation" (arg "affiliation")))}

      "github_get_repository"
      {:connector.http/method :get
       :connector.http/url (repo-url args)
       :connector.http/headers api-headers}

      "github_list_issues"
      {:connector.http/method :get
       :connector.http/url (repo-url args "/issues")
       :connector.http/headers api-headers
       :connector.http/query (cond-> {}
                               (arg "state") (assoc "state" (arg "state"))
                               (arg "labels") (assoc "labels" (arg "labels"))
                               (arg "per_page") (assoc "per_page" (arg "per_page")))}

      "github_list_pull_requests"
      {:connector.http/method :get
       :connector.http/url (repo-url args "/pulls")
       :connector.http/headers api-headers
       :connector.http/query (cond-> {}
                               (arg "state") (assoc "state" (arg "state"))
                               (arg "per_page") (assoc "per_page" (arg "per_page")))}

      "github_list_organizations"
      {:connector.http/method :get
       :connector.http/url (str base-url "/user/orgs")
       :connector.http/headers api-headers}

      "github_create_issue"
      {:connector.http/method :post
       :connector.http/url (repo-url args "/issues")
       :connector.http/headers (assoc api-headers "content-type" "application/json")
       :connector.http/body (into {} (remove (comp nil? val))
                                  {"title" (arg "title")
                                   "body" (arg "body")
                                   "labels" (arg "labels")})})))

;; --- responses ---

(defn- repo-row [r]
  {:full-name (get r "full_name")
   :private? (true? (get r "private"))
   :description (get r "description")
   :default-branch (get r "default_branch")
   :pushed-at (get r "pushed_at")
   :archived? (true? (get r "archived"))
   :open-issues (get r "open_issues_count")
   :html-url (get r "html_url")})

(defn- issue-row [i]
  {:number (get i "number")
   :title (get i "title")
   :state (get i "state")
   :author (get-in i ["user" "login"])
   :labels (mapv #(get % "name") (get i "labels" []))
   :comments (get i "comments")
   :created-at (get i "created_at")
   :updated-at (get i "updated_at")
   :html-url (get i "html_url")})

(defn normalize
  [tool-name response]
  (let [body (:connector.http/body response)]
    (case tool-name
      "github_get_authenticated_user"
      {:login (get body "login")
       :name (get body "name")
       :type (get body "type")
       :html-url (get body "html_url")}

      "github_list_repositories" {:repositories (mapv repo-row body)}

      "github_get_repository" (repo-row body)

      ;; GitHub returns pull requests from /issues as well, distinguished only
      ;; by a "pull_request" key. A caller that asked for issues and counted the
      ;; result would be counting PRs too, and nothing in the response says so.
      "github_list_issues"
      {:issues (mapv issue-row (remove #(contains? % "pull_request") body))}

      "github_list_pull_requests"
      {:pull-requests (mapv (fn [pr]
                              (assoc (issue-row pr)
                                     :draft? (true? (get pr "draft"))
                                     :base (get-in pr ["base" "ref"])
                                     :head (get-in pr ["head" "ref"])))
                            body)}

      "github_list_organizations"
      {:organizations (mapv (fn [o] {:login (get o "login")
                                     :description (get o "description")})
                            body)}

      "github_create_issue" (issue-row body))))

(def provider
  (p/provider descriptor {:request request :normalize normalize}))
