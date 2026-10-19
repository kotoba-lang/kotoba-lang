(ns kotoba.protocol.a2a
  "Pure A2A v1.0 wire model and validation.

  This namespace does not open HTTP, authenticate a peer, allocate authority or
  persist a task. A host supplies those effects and translates its durable run
  into these protocol objects. Both keyword and JSON string keys are accepted
  at the boundary; emitted objects use the v1.0 JSON field names."
  (:require [kotoba.lang.text :as str]))

(def protocol-version "1.0")
(def json-rpc-version "2.0")

(def task-states
  #{"TASK_STATE_UNSPECIFIED" "TASK_STATE_SUBMITTED" "TASK_STATE_WORKING"
    "TASK_STATE_COMPLETED" "TASK_STATE_FAILED" "TASK_STATE_CANCELED"
    "TASK_STATE_INPUT_REQUIRED" "TASK_STATE_REJECTED"
    "TASK_STATE_AUTH_REQUIRED"})

(def terminal-task-states
  #{"TASK_STATE_COMPLETED" "TASK_STATE_FAILED" "TASK_STATE_CANCELED"
    "TASK_STATE_REJECTED"})

(defn field
  "Read a JSON field from a keyword- or string-keyed map. `names` may include
  camelCase and snake_case spellings used by A2A's JSON/protobuf projections."
  [m & names]
  (some (fn [n]
          (let [k (keyword n)]
            (cond
              (contains? m k) (get m k)
              (contains? m n) (get m n)
              :else nil)))
        names))

(defn- non-blank? [v]
  (and (string? v) (not (str/blank? v))))

(defn message-problems
  "Validate the bounded Message profile used by hosts accepting text tasks.
  The full A2A Part union remains representable, but this admission profile
  accepts only text so a URL, raw file or arbitrary data cannot silently become
  host input."
  [message]
  (let [parts (field message "parts")
        role (field message "role")
        message-id (field message "messageId" "message_id")]
    (cond-> []
      (not (map? message))
      (conj {:field :message :error :object-required})

      (not (non-blank? message-id))
      (conj {:field :messageId :error :required})

      (not= "ROLE_USER" role)
      (conj {:field :role :error :role-user-required})

      (not (and (sequential? parts) (seq parts)))
      (conj {:field :parts :error :non-empty-required})

      (and (sequential? parts)
           (some #(not (and (map? %) (non-blank? (field % "text")))) parts))
      (conj {:field :parts :error :text-only-profile}))))

(defn message-text
  "Return the admitted text joined with newlines, or a validation error."
  [message]
  (let [problems (message-problems message)]
    (if (seq problems)
      {:error :invalid-message :problems problems}
      {:text (str/join "\n"
                       (map #(field % "text")
                            (field message "parts")))
       :message-id (field message "messageId" "message_id")
       :context-id (field message "contextId" "context_id")
       :task-id (field message "taskId" "task_id")})))

(defn agent-card
  "Build a minimal A2A v1.0 Agent Card. Skills are already-public host
  projections; this function never derives them from a runtime tool registry."
  [{:keys [name description url version skills security-schemes security
           streaming? push-notifications?]}]
  (cond-> {:name name
           :description description
           :version version
           :supportedInterfaces [{:url url
                                  :protocolBinding "JSONRPC"
                                  :protocolVersion protocol-version}]
           :capabilities {:streaming (boolean streaming?)
                          :pushNotifications (boolean push-notifications?)}
           :defaultInputModes ["text/plain"]
           :defaultOutputModes ["text/plain"]
           :skills (vec skills)}
    (seq security-schemes) (assoc :securitySchemes security-schemes)
    (seq security) (assoc :security security)))

(defn agent-card-problems [card]
  (let [interfaces (field card "supportedInterfaces" "supported_interfaces")
        skills (field card "skills")]
    (cond-> []
      (not (non-blank? (field card "name")))
      (conj {:field :name :error :required})
      (not (non-blank? (field card "description")))
      (conj {:field :description :error :required})
      (not (non-blank? (field card "version")))
      (conj {:field :version :error :required})
      (not (and (sequential? interfaces) (seq interfaces)))
      (conj {:field :supportedInterfaces :error :non-empty-required})
      (and (sequential? interfaces)
           (some #(or (not (non-blank? (field % "url")))
                      (not= "JSONRPC" (field % "protocolBinding"
                                               "protocol_binding"))
                      (not= protocol-version
                            (field % "protocolVersion" "protocol_version")))
                 interfaces))
      (conj {:field :supportedInterfaces :error :unsupported-interface})
      (not (sequential? skills))
      (conj {:field :skills :error :array-required}))))

(defn- send-request
  [expected-method request]
  (let [method (field request "method")
        params (field request "params")
        message (field params "message")
        parsed (message-text message)
        problems (cond-> []
                   (not= json-rpc-version (field request "jsonrpc"))
                   (conj {:field :jsonrpc :error :version-required})
                   (nil? (field request "id"))
                   (conj {:field :id :error :required})
                   (not= expected-method method)
                   (conj {:field :method :error :unsupported-method})
                   (:error parsed)
                   (into (:problems parsed)))]
    (if (seq problems)
      {:error :invalid-request :problems problems}
      (merge {:request-id (field request "id")
              :tenant (field params "tenant")}
             parsed))))

(defn send-message-request
  "Validate a JSON-RPC A2A SendMessage call and return its admitted coordinates."
  [request]
  (send-request "SendMessage" request))

(defn send-streaming-message-request
  "Validate a JSON-RPC A2A SendStreamingMessage call for SSE delivery.
  Admission is identical to SendMessage: this profile remains text-only and
  a transport choice never widens host input or authority."
  [request]
  (send-request "SendStreamingMessage" request))

(defn get-task-request
  "Validate a JSON-RPC A2A GetTask call. Hosts still decide whether the
  authenticated caller owns the resulting task."
  [request]
  (let [params (field request "params")
        task-id (field params "id")
        problems (cond-> []
                   (not= json-rpc-version (field request "jsonrpc"))
                   (conj {:field :jsonrpc :error :version-required})
                   (nil? (field request "id"))
                   (conj {:field :id :error :required})
                   (not= "GetTask" (field request "method"))
                   (conj {:field :method :error :unsupported-method})
                   (not (non-blank? task-id))
                   (conj {:field :params/id :error :required}))]
    (if (seq problems)
      {:error :invalid-request :problems problems}
      {:request-id (field request "id") :task-id task-id})))

(defn task
  "Project a host task into the A2A Task shape."
  [{:keys [id context-id state timestamp text artifacts metadata]}]
  (cond-> {:id id
           :contextId context-id
           :status (cond-> {:state state}
                     timestamp (assoc :timestamp timestamp))}
    text (assoc-in [:status :message]
                   {:messageId (str id "-status")
                    :contextId context-id
                    :taskId id
                    :role "ROLE_AGENT"
                    :parts [{:text text :mediaType "text/plain"}]})
    (seq artifacts) (assoc :artifacts (vec artifacts))
    (seq metadata) (assoc :metadata metadata)))

(defn json-rpc-result [request-id result]
  {:jsonrpc json-rpc-version :id request-id :result result})

(defn json-rpc-error
  ([request-id code message]
   (json-rpc-error request-id code message nil))
  ([request-id code message data]
   {:jsonrpc json-rpc-version
    :id request-id
    :error (cond-> {:code code :message message}
             data (assoc :data data))}))

(defn terminal? [task]
  (contains? terminal-task-states (field (field task "status") "state")))
