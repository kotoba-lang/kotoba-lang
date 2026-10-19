(ns connector.ports
  "Host-injected ports. This library defines the protocols; the host supplies
  the implementations.

  There is exactly one required port, `IHttp`, and it deliberately trades in
  maps rather than a client object. The reason is that the interesting tests
  for a connector are 'did it build the right request' and 'does it read that
  response correctly', and both are ordinary value comparisons once the
  request is a map — no network, no recorded cassettes, no fake server.

  Request:
    {:connector.http/method  :get|:post|:patch|:put|:delete
     :connector.http/url     \"https://…\"          ; without query
     :connector.http/query   {\"q\" \"…\"}            ; optional, string keys
     :connector.http/headers {\"accept\" \"…\"}       ; optional, lower-case keys
     :connector.http/body    <data or string>}     ; optional

  Response:
    {:connector.http/status  200
     :connector.http/headers {…}
     :connector.http/body    <already-parsed data>}

  The body arrives parsed. No JSON text is parsed inside this library — the
  same rule `mcp.json` follows, and the reason both stay dependency-free.")

(defprotocol IHttp
  (-request [this request] "request map → response map"))

(defprotocol ITokens
  "Where a host keeps the access token for a connector. Separate from IHttp
  because the token is the part with a lifetime: a host that refreshes on
  expiry implements this and leaves the transport alone."
  (-token [this connector-id]
    "connector id → access token string, or nil when not connected"))

(defn http-fn
  "Adapt a plain function to `IHttp`. Convenience for tests and for hosts whose
  transport is already a function."
  [f]
  (reify IHttp (-request [_ request] (f request))))

(defn token-fn
  "Adapt a plain function to `ITokens`."
  [f]
  (reify ITokens (-token [_ connector-id] (f connector-id))))

(defn static-tokens
  "An `ITokens` backed by a map of connector id → token. Test seam."
  [m]
  (reify ITokens (-token [_ connector-id] (get m connector-id))))
