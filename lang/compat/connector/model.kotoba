(ns connector.model
  "A connector as EDN — a plain-data description of one external service this
  workspace may be granted access to, with a threading-friendly builder.
  No I/O, no third-party deps — portable .cljc (JVM, ClojureScript, SCI).

  A connector answers four questions and nothing else:

    what is it        :connector/id :connector/name :connector/summary
    whose is it       :connector/origin-domain  (the origin plane's recorded fact)
    how do we get in  :connector/auth
    what can it do    :connector/tools, each naming the scopes IT needs

  The last point is the one that makes this worth writing down. A provider is
  usually one OAuth client with a scope list, and a host that asks for the union
  of every scope any tool might want ends up holding `gmail.send` because
  somebody enabled a calendar reader. Scopes are declared per tool so consent
  can be computed from what is actually turned on — see `connector.consent`.

  The descriptor is data all the way down: no functions, no closures, no host
  objects. The functions that turn a tool call into an HTTP request live beside
  it in a provider map (`connector.provider`), because those cannot be printed,
  diffed or stored and the descriptor must be.

    {:connector/id            \"com.google.calendar\"
     :connector/name          \"Google Calendar\"
     :connector/origin-domain \"google.com\"
     :connector/base-url      \"https://www.googleapis.com\"
     :connector/auth          {:connector.auth/kind :oauth2 …}
     :connector/tools
     {\"google_calendar_freebusy\"
      {:connector/name         \"google_calendar_freebusy\"
       :connector/description  \"Free/busy windows for one or more calendars\"
       :connector/effect       :read
       :connector/scopes       [\"https://www.googleapis.com/auth/calendar.readonly\"]
       :connector/input-schema {:type \"object\" …}}}}")

;; --- builder (threadable) ---

(defn connector
  "A connector descriptor with no tools yet.

  opts: {:summary :origin-domain :base-url :auth :docs-url}

  `:origin-domain` is the registrable domain of the authority that owns the
  service, recorded rather than derived — it is the same fact
  manifest/origin-domains.edn keeps, and the repository name is checked against
  it (ADR-2608040100). google.com, not drive.google.com: the API is Google's
  even where the product has a domain of its own."
  ([id name] (connector id name nil))
  ([id name opts]
   (cond-> {:connector/id    id
            :connector/name  name
            :connector/tools {}}
     (:summary opts)       (assoc :connector/summary (:summary opts))
     (:origin-domain opts) (assoc :connector/origin-domain (:origin-domain opts))
     (:base-url opts)      (assoc :connector/base-url (:base-url opts))
     (:docs-url opts)      (assoc :connector/docs-url (:docs-url opts))
     (:auth opts)          (assoc :connector/auth (:auth opts)))))

(defn add-tool
  "Add a tool. opts: {:description :input-schema :scopes :effect}.

  `:input-schema` is JSON-Schema-as-EDN, the same shape `mcp.model/add-tool`
  takes, so `connector.mcp` can hand it straight to an MCP client.

  `:effect` is `:read` or `:write` and is not decoration: a host that offers a
  connector read-only needs to know which half of it that is, and asking each
  host to infer it from the HTTP verb pushes the same judgement into every
  consumer. `:write` is the default only in the sense that an unstated effect
  is a validation error — nothing here guesses."
  [descriptor name opts]
  (assoc-in descriptor [:connector/tools name]
            (cond-> {:connector/name name}
              (:description opts)  (assoc :connector/description (:description opts))
              (:input-schema opts) (assoc :connector/input-schema (:input-schema opts))
              (:effect opts)       (assoc :connector/effect (:effect opts))
              (seq (:scopes opts)) (assoc :connector/scopes (vec (:scopes opts))))))

;; --- auth profiles ---

(defn oauth2
  "An OAuth 2.0 authorization-code auth profile.

  opts: {:authorization-endpoint :token-endpoint :client-id-env
         :client-secret-env :pkce? :base-scopes :extra :profile-endpoint
         :scopes? :client-auth}

  `:scopes?` defaults to true and is set false by providers whose
  authorization endpoint has no `scope` parameter at all. Notion is the case
  that forced it: permissions there are fixed on the integration, so a
  descriptor that declared per-tool scopes would be describing a mechanism the
  provider does not have. Declaring it false is not a weaker connector — it is
  the connector saying that consent cannot be narrowed here, which is exactly
  what an operator needs to know before enabling it.

  `:client-auth` is `:post` (default, RFC 6749 §2.3.1 client_secret_post) or
  `:basic` (client_secret_basic, the HTTP Basic header). Notion accepts only
  the latter, and a token request sent the wrong way fails with an error that
  says nothing about which of the two is expected.

  Only the NAMES of the environment variables holding the client credentials
  are recorded. A descriptor that read them would hold ambient authority and
  could not be printed to a log or shipped in a catalog, which is most of what
  a descriptor is for.

  `:pkce?` is declared per provider rather than assumed, because it is not
  universal: Google and Microsoft verify `code_challenge`, GitHub's OAuth Apps
  ignore it. Sending it anyway is harmless, but recording `true` for a provider
  that never checks it states a security property this connector does not have."
  [opts]
  (cond-> {:connector.auth/kind :oauth2
           :connector.auth/authorization-endpoint (:authorization-endpoint opts)
           :connector.auth/token-endpoint (:token-endpoint opts)
           :connector.auth/pkce? (boolean (:pkce? opts))}
    ;; Recorded only when false. The default is the common case, and a key that
    ;; appears on every descriptor to say "normal" is noise in a grant screen.
    (false? (:scopes? opts)) (assoc :connector.auth/scopes? false)
    (= :basic (:client-auth opts)) (assoc :connector.auth/client-auth :basic)
    (:client-id-env opts)     (assoc :connector.auth/client-id-env (:client-id-env opts))
    (:client-secret-env opts) (assoc :connector.auth/client-secret-env (:client-secret-env opts))
    (:profile-endpoint opts)  (assoc :connector.auth/profile-endpoint (:profile-endpoint opts))
    (seq (:base-scopes opts)) (assoc :connector.auth/base-scopes (vec (:base-scopes opts)))
    (seq (:extra opts))       (assoc :connector.auth/extra (:extra opts))))

(defn bearer
  "A static-token auth profile: the host holds a token and this connector says
  which environment variable names it. No authorization flow."
  [token-env]
  {:connector.auth/kind :bearer
   :connector.auth/token-env token-env})

(defn url-credential
  "A profile where the credential IS the endpoint.

  Incoming webhooks work this way — Google Chat, Slack and Discord all hand out
  a URL carrying its own key in the query string, and there is no header to put
  anything in. Modelling it as `:bearer` would be wrong twice: the host would
  send an Authorization header the provider ignores, and the URL, which is the
  actual secret, would sit in the descriptor where a catalog can print it.

  `connector.invoke` fills the URL in from the host's token for any request
  carrying `:connector.http/url-from-credential`. So the connector still never
  holds the credential, and still cannot print it."
  [url-env]
  {:connector.auth/kind :url-credential
   :connector.auth/url-env url-env})

(def anonymous
  "No credential at all. A public API still gets a declared profile so that
  `:connector/auth` is never absent — absent would read as 'not yet written'."
  {:connector.auth/kind :none})

(defn scoped?
  "Whether this connector's provider has a scope mechanism at all."
  [descriptor]
  (and (= :oauth2 (get-in descriptor [:connector/auth :connector.auth/kind]))
       (not (false? (get-in descriptor [:connector/auth :connector.auth/scopes?])))))

;; --- queries ---

(defn tool [descriptor name] (get-in descriptor [:connector/tools name]))

(defn tools
  "All tool descriptors, sorted by name (deterministic)."
  [descriptor]
  (->> descriptor :connector/tools vals (sort-by :connector/name) vec))

(defn tool-names [descriptor]
  (->> descriptor :connector/tools keys sort vec))

(defn scopes-for
  "The scopes needed to run exactly `names`, plus the profile's base scopes.

  Sorted and deduplicated so that two calls with the same enabled set produce
  the same string — a consent URL that reorders itself between renders looks
  like a different grant to anybody reading a log."
  [descriptor names]
  (let [wanted (set names)]
    (->> (concat (get-in descriptor [:connector/auth :connector.auth/base-scopes])
                 (mapcat :connector/scopes
                         (filter #(wanted (:connector/name %)) (tools descriptor))))
         (remove nil?)
         distinct
         sort
         vec)))

(defn scopes
  "Every scope this connector could ever need — i.e. `scopes-for` with all
  tools enabled. Useful for a catalog entry; NOT what to request at consent
  time, which is `connector.consent/authorization` over the enabled set."
  [descriptor]
  (scopes-for descriptor (tool-names descriptor)))

(defn read-only
  "The same descriptor with every `:write` tool removed.

  A host offering a connector in read-only mode drops the tools and, because
  scopes are per tool, the write scopes fall away with them."
  [descriptor]
  (update descriptor :connector/tools
          #(into {} (remove (fn [[_ t]] (= :write (:connector/effect t)))) %)))
