(ns google-chat.client
  "Google Chat -- send-only via an Incoming Webhook URL. Portable `.cljc`,
  I/O injected (`:http-fn :json-write :json-read`, same DI shape as every
  other client in this workspace).

  Scope decision: Google Chat's INBOUND path (an app receiving messages)
  needs either a Workspace Marketplace Chat app registration verified via
  a Google-issued RS256 JWT (a NEW crypto capability -- signature
  *verification* against Google's JWKS, structurally like
  `teams.jwt-verify`) or Pub/Sub, and its full REST API for sending
  (`spaces.messages.create`) needs a service-account JWT *assertion*,
  signed with a private key via RS256 -- signing, not just verifying, is a
  capability this workspace's other channels have never needed. Both are
  disproportionate for a send-only notification channel, so this library
  deliberately implements only the Incoming Webhook path: create a
  webhook-enabled space in Google Chat, copy its generated URL (already
  carries `key`/`token` query params as its own credential -- no separate
  auth header needed), configure that as `:webhook-url`. There is no
  polling/reading counterpart -- `manimani`'s google-chat channel (if
  wired) is necessarily egress-only, mirroring `call-log`'s ingress-only
  asymmetry in the opposite direction.")

(defn send-message!
  "POST `text` to the configured Incoming Webhook. Returns the parsed JSON
  body on HTTP 200, or `{:ok false :status <code> :error <body>}` on any
  other status (this workspace's universal failure shape -- never a bare
  `nil`)."
  [{:keys [http-fn json-write json-read webhook-url]} text]
  (let [resp (http-fn {:url webhook-url :method :post
                        :headers {"Content-Type" "application/json"}
                        :body (json-write {:text text})})]
    (if (= 200 (:status resp))
      (json-read (:body resp))
      {:ok false :status (:status resp) :error (:body resp)})))
