(ns tayori.channel.email
  "Email Channel via the Gmail API (REST/HTTP) — matches local-manimani's
  existing Gmail integration (`server/src/gmail.ts`, `reply_llm` policy),
  generalized behind the Channel protocol so other tayori consumers
  (cloud-manimani, gijiroku) can reuse it without re-implementing Gmail auth.

  I/O is injected (`:http-fn` `:json-write` `:json-read` `:creds`), the same
  contract as `tayori.docport.git`/`kekkai.kotoba` — this ns stays dependency-
  free; a JVM host supplies a real `:http-fn` (e.g. `tayori.kotoba/jvm-http-fn`).

  `send-reply!` is called ONLY after human approval, by `tayori.operation`'s
  commit step — never by the reply-LLM advise path directly (no-actuation
  charter)."
  (:require [tayori.channel :as ch]))

(defn- gmail-get [http-fn creds thread-id]
  (http-fn {:url (str "https://gmail.googleapis.com/gmail/v1/users/me/threads/" thread-id)
            :method :get
            :headers {"Authorization" (str "Bearer " (:token creds))}}))

(defn email-channel
  "opts: :http-fn :json-write :json-read :creds ({:token <oauth-bearer>}).
  Gmail's `users.messages.send` wants a full base64url RFC 2822 `:raw`
  message (headers + MIME body); `:raw-fn` (thread body -> raw-string) does
  that encoding and is injected too — a JVM host can wire `jakarta.mail`,
  a cljs host a plain string-template + goog.crypt.base64. Not supplying
  `:raw-fn` is a configuration error, not a silent no-op: `send-reply!` throws
  rather than send a malformed message."
  [{:keys [http-fn json-write json-read creds raw-fn]}]
  (reify ch/Channel
    (fetch-thread [_ thread-id]
      (some-> (gmail-get http-fn creds thread-id) :body json-read))
    (list-new-messages [_ _thread-id]
      ;; Gmail's threads.get has no "since" filter — correctly filtering to
      ;; only-new messages needs users.history.list against a stored
      ;; historyId cursor tayori doesn't yet model. Returning the full thread
      ;; here would silently re-ingest every historical message on every
      ;; poll; honestly empty until that cursor exists (same known-gap shape
      ;; as tayori.channel.whatsapp — a real deployment ingests via Gmail's
      ;; push/watch notification, not by polling this port).
      [])
    (send-reply! [_ thread body]
      (when-not raw-fn
        (throw (ex-info "email-channel: :raw-fn (thread body -> base64url RFC2822) is required to send"
                        {:thread thread})))
      (http-fn {:url "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
                :method :post
                :headers {"Authorization" (str "Bearer " (:token creds))
                          "Content-Type" "application/json"}
                :body (json-write {:threadId (:external-id thread) :raw (raw-fn thread body)})}))))
