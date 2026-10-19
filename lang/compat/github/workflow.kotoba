(ns github.workflow
  "Portable GitHub operating-workflow contract.

  Keeps GitHub HTTP/auth outside the business application. Callers inject a
  `(request-fn request-map) -> response-map` transport; fixtures, `gh api`, a
  GitHub App, Workers, and native hosts can therefore share the same model.")

(def schema "kotoba.github.workflow.v0")

(def read-capabilities
  #{:org/read :repo/read :project/read :issue/read :pull/read
    :check/read :workflow/read})

(def mutation-capabilities
  #{:issue/write :pull/write :project/write :workflow/dispatch})

(defn scope [owner repo]
  {:owner owner :repo repo :graph (str owner "/" repo)})

(defn organization-profile
  "Validate the public business identity attached to an organization."
  [{:keys [id name domain operator-email] :as profile}]
  (when-not (and (every? #(and (string? %) (not (empty? %)))
                          [id name domain operator-email])
                 (re-matches #"[^@\s]+@[^@\s]+\.[^@\s]+" operator-email)
                 (re-matches #"[A-Za-z0-9.-]+\.[A-Za-z]{2,}" domain))
    (throw (ex-info "Invalid organization profile" {:profile profile})))
  profile)

(defn with-organization-profile [snapshot profile]
  (assoc snapshot :business-organization (organization-profile profile)))

(defn request
  ([method path] (request method path nil))
  ([method path body]
   (cond-> {:method method :path path
            :headers {"accept" "application/vnd.github+json"
                      "x-github-api-version" "2022-11-28"}}
     body (assoc :body body))))

(defn rest-plan
  "Read plan for one repository. Projects v2 is GraphQL-only; everything
  else deliberately uses stable REST paths."
  [{:keys [owner repo]}]
  [{:key :organization :request (request :get (str "/orgs/" owner))}
   {:key :repository   :request (request :get (str "/repos/" owner "/" repo))}
   {:key :issues       :request (request :get (str "/repos/" owner "/" repo "/issues?state=all&per_page=100"))}
   {:key :pulls        :request (request :get (str "/repos/" owner "/" repo "/pulls?state=all&per_page=100"))}
   {:key :workflows    :request (request :get (str "/repos/" owner "/" repo "/actions/workflows?per_page=100"))}
   {:key :workflow-runs :request (request :get (str "/repos/" owner "/" repo "/actions/runs?per_page=100"))}
   {:key :check-runs   :request (request :get (str "/repos/" owner "/" repo "/commits/HEAD/check-runs?per_page=100"))}])

(def projects-v2-query
  "query($owner:String!,$repo:String!){organization(login:$owner){projectsV2(first:100){nodes{id number title closed url}}}repository(owner:$owner,name:$repo){projectsV2(first:100){nodes{id number title closed url}}}}")

(defn projects-request [{:keys [owner repo]}]
  (request :post "/graphql" {:query projects-v2-query
                              :variables {:owner owner :repo repo}}))

(defn- rows [v]
  (cond (vector? v) v (sequential? v) (vec v) (nil? v) [] :else [v]))

(defn- pick [m & ks]
  (some #(when (contains? m %) (get m %)) ks))

(defn normalize-issue [graph x]
  {:kind :github/issue :graph graph :id (pick x :id "id")
   :number (pick x :number "number") :title (pick x :title "title")
   :state (pick x :state "state") :url (pick x :html_url "html_url" :url "url")})

(defn normalize-pull [graph x]
  (assoc (normalize-issue graph x) :kind :github/pull-request))

(defn normalize-run [graph x]
  {:kind :github/workflow-run :graph graph :id (pick x :id "id")
   :name (pick x :name "name") :status (pick x :status "status")
   :conclusion (pick x :conclusion "conclusion")
   :branch (pick x :head_branch "head_branch")
   :sha (pick x :head_sha "head_sha")
   :url (pick x :html_url "html_url")})

(defn normalize-check [graph x]
  {:kind :github/check-run :graph graph :id (pick x :id "id")
   :name (pick x :name "name") :status (pick x :status "status")
   :conclusion (pick x :conclusion "conclusion")
   :url (pick x :html_url "html_url")})

(defn normalize-project [graph x]
  {:kind :github/project :graph graph :id (pick x :id "id")
   :number (pick x :number "number") :title (pick x :title "title")
   :closed? (boolean (pick x :closed "closed")) :url (pick x :url "url")})

(defn- body [responses k] (get-in responses [k :body]))

(defn snapshot
  "Build the common read model from keyed transport responses."
  [{:keys [owner repo]} responses projects-body]
  (let [graph (:graph (scope owner repo))
        issues (remove #(contains? % :pull_request) (rows (body responses :issues)))
        pulls (rows (body responses :pulls))
        runs (rows (or (:workflow_runs (body responses :workflow-runs))
                       (get (body responses :workflow-runs) "workflow_runs")))
        checks (rows (or (:check_runs (body responses :check-runs))
                         (get (body responses :check-runs) "check_runs")))
        project-nodes (concat (get-in projects-body [:data :organization :projectsV2 :nodes] [])
                              (get-in projects-body [:data :repository :projectsV2 :nodes] []))]
    {:schema schema :scope (scope owner repo)
     :organization (body responses :organization)
     :repository (body responses :repository)
     :projects (mapv #(normalize-project graph %) project-nodes)
     :issues (mapv #(normalize-issue graph %) issues)
     :pull-requests (mapv #(normalize-pull graph %) pulls)
     :workflows (rows (or (:workflows (body responses :workflows))
                          (get (body responses :workflows) "workflows")))
     :workflow-runs (mapv #(normalize-run graph %) runs)
     :check-runs (mapv #(normalize-check graph %) checks)}))

(defn execute-plan
  "Execute a repository read through an injected transport. Throws on any
  non-2xx response so consumers cannot render an incomplete snapshot as OK."
  [request-fn repo-scope]
  (let [responses (into {}
                        (map (fn [{:keys [key request]}]
                               (let [response (request-fn request)
                                     status (:status response)]
                                 (when-not (<= 200 status 299)
                                   (throw (ex-info "GitHub read failed" {:key key :response response})))
                                 [key response])))
                        (rest-plan repo-scope))
        projects (request-fn (projects-request repo-scope))]
    (when-not (<= 200 (:status projects) 299)
      (throw (ex-info "GitHub Projects read failed" {:response projects})))
    (snapshot repo-scope responses (:body projects))))

(defn health [snapshot]
  (let [failed (filter #(contains? #{"failure" "cancelled" "timed_out"} (:conclusion %))
                       (concat (:workflow-runs snapshot) (:check-runs snapshot)))
        open-issues (filter #(= "open" (:state %)) (:issues snapshot))
        open-pulls (filter #(= "open" (:state %)) (:pull-requests snapshot))]
    {:schema "kotoba.github.health.v0"
     :graph (get-in snapshot [:scope :graph])
     :counts {:projects (count (:projects snapshot))
              :open-issues (count open-issues) :open-pulls (count open-pulls)
              :failed-checks (count failed)}
     :ready? (empty? failed)}))

(defn fixture-transport
  "Deterministic harness transport. `routes` maps [method path] to a body or
  complete {:status :body} response."
  [routes]
  (fn [{:keys [method path]}]
    (let [v (get routes [method path] ::missing)]
      (cond (= ::missing v) {:status 404 :body {:message "fixture route missing"}}
            (and (map? v) (contains? v :status)) v
            :else {:status 200 :body v}))))
