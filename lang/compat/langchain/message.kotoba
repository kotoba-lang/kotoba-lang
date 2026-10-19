(ns langchain.message
  "Chat message data model. Plain maps, Anthropic-Messages-API-shaped:

    {:role :user|:assistant|:system|:tool
     :content \"…\"                     ; string or content blocks
     :tool-calls [{:id .. :name .. :input ..}]   ; assistant only
     :tool-call-id \"…\"}                         ; tool results only")

(defn system [content] {:role :system :content content})
(defn user [content] {:role :user :content content})

(defn ai
  ([content] {:role :assistant :content content})
  ([content {:keys [tool-calls usage stop-reason]}]
   (cond-> {:role :assistant :content content}
     tool-calls (assoc :tool-calls tool-calls)
     usage (assoc :usage usage)
     stop-reason (assoc :stop-reason stop-reason))))

(defn tool-result
  "Result of executing a tool call, fed back to the model."
  [tool-call-id content & [{:keys [error?]}]]
  (cond-> {:role :tool :tool-call-id tool-call-id :content content}
    error? (assoc :error? true)))

(defn tool-calls
  "Tool calls requested by an assistant message (nil when none)."
  [msg]
  (seq (:tool-calls msg)))

(defn last-message [messages] (peek (vec messages)))

(defn text
  "Extracts plain text from a message's content (string or blocks)."
  [msg]
  (let [c (:content msg)]
    (if (string? c)
      c
      (apply str (keep #(when (= "text" (or (:type %) (get % "type")))
                          (or (:text %) (get % "text")))
                       c)))))
