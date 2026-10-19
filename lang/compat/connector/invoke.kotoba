(ns connector.invoke
  "Run one tool call: build the request, attach the token, send it through the
  host's transport, normalize the response.

  The attaching is here rather than in each connector repository, and that is
  the load-bearing part of this namespace. A connector's `request` function is
  handed a tool name and arguments and returns a request map — it is never
  given a token, a keychain, an environment or a way to reach any of them. So
  a connector repository cannot leak a credential it has no way to obtain, and
  reviewing one does not require checking whether it did."
  (:require [connector.auth :as auth]
            [connector.ports :as ports]
            [connector.provider :as p]
            [connector.registry :as reg]))

(defn- error [code msg data]
  (merge {:connector/error true :connector/code code :connector/message msg} data))

(defn- authorize
  "Attach the credential for `descriptor`, or return an error map.

  Where it goes depends on the profile. For `:oauth2`/`:bearer` it is an
  Authorization header. For `:url-credential` — an incoming webhook, whose URL
  carries its own key — there is no header to use and the credential IS the
  endpoint, so it becomes the URL. Either way the connector's own `request`
  never saw it."
  [request descriptor token]
  (let [kind (get-in descriptor [:connector/auth :connector.auth/kind])
        missing (error :connector/not-connected
                       (str (:connector/id descriptor) " has no access token")
                       {:connector/id (:connector/id descriptor)})]
    (case kind
      :none request

      (:oauth2 :bearer)
      (if (seq (str token))
        (assoc-in request [:connector.http/headers "authorization"]
                  (auth/authorization-header token))
        missing)

      :url-credential
      (cond
        (not (:connector.http/url-from-credential request)) request
        (seq (str token)) (-> request
                              (assoc :connector.http/url (str token))
                              (dissoc :connector.http/url-from-credential))
        :else missing)

      request)))

(defn request-for
  "The fully-formed request a tool call would send, WITHOUT the credential.

  Exposed because it is the thing worth asserting on in a connector's tests,
  and because a host that wants to show an operator what a tool is about to do
  should be able to see it without holding a token to do so."
  [registry tool-name args]
  (if-let [pr (reg/owner registry tool-name)]
    ((:connector/request pr) tool-name args)
    (error :connector/unknown-tool (str "no connector offers tool " tool-name)
           {:connector/tool tool-name})))

(defn call
  "Invoke `tool-name`.

  ctx: {:http <IHttp> :tokens <ITokens>}

  Returns the normalized result, or an error map with `:connector/error true`.
  Errors are values rather than exceptions because every caller so far — an
  MCP dispatcher, an agent loop, a tool-result frame — has to turn a failure
  back into data anyway, and throwing only to catch is a longer way round.

  An HTTP status of 400 or above is not normalized. A connector's `normalize`
  is written against a success body; handing it an error payload produces a
  result that reads like an answer."
  [registry tool-name args {:keys [http tokens]}]
  (if-let [pr (reg/owner registry tool-name)]
    (let [descriptor (p/descriptor pr)
          request ((:connector/request pr) tool-name args)]
      (if (:connector/error request)
        request
        (let [token (when tokens (ports/-token tokens (:connector/id descriptor)))
              authorized (authorize request descriptor token)]
          (if (:connector/error authorized)
            authorized
            (let [response (ports/-request http authorized)
                  status (:connector.http/status response)]
              (if (and (integer? status) (>= status 400))
                (error :connector/http-error
                       (str (:connector/id descriptor) " " tool-name
                            " failed with HTTP " status)
                       {:connector/id (:connector/id descriptor)
                        :connector/tool tool-name
                        :connector.http/status status
                        :connector.http/body (:connector.http/body response)})
                ((:connector/normalize pr) tool-name response)))))))
    (error :connector/unknown-tool (str "no connector offers tool " tool-name)
           {:connector/tool tool-name})))
