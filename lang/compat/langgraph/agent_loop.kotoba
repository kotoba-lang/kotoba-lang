(ns langgraph.agent-loop
  "Provider-neutral, pure agent turn loop.

  A turn asks the model for output, executes every correlated tool call, feeds
  the tool results into the next model turn, and stops when the model emits no
  tool calls. Hosts interpret returned effects and persist returned events."
  (:require [clojure.string :as str]))

(def terminal-phases #{:done :error :interrupted})
(def terminal-call-statuses #{:succeeded :failed :denied})

(defn terminal? [state] (contains? terminal-phases (:loop/phase state)))

(defn- event [state kind ts data]
  (merge {:event/kind kind :thread/id (:thread/id state)
          :turn/index (:turn/index state) :ts ts} data))

(defn start
  "Start one task. Hosts interpret the returned :model/respond effect."
  [{:keys [thread-id task-id max-turns max-tool-calls ts]
    :or {max-turns 32 max-tool-calls 128}}
   user-content]
  (let [state {:loop/version 1 :thread/id thread-id :task/id task-id
               :loop/phase :await-model :turn/index 0
               :budget/max-turns max-turns :budget/max-tool-calls max-tool-calls
               :budget/tool-calls 0 :response/id nil
               :items [{:item/type :user-message :content user-content}]
               :tool-calls {}}]
    {:state state
     :effects [{:effect :model/respond :thread-id thread-id :task-id task-id
                :turn-index 0 :items (:items state)}]
     :events [(event state :task/started ts {}) (event state :turn/started ts {})]}))

(defn- valid-call? [{:keys [call/id tool/name]}]
  (and (string? id) (not (str/blank? id))
       (or (keyword? name) (and (string? name) (not (str/blank? name))))))

(defn- call-needs-approval? [call]
  (not= :read-only (or (:tool/risk call) :unknown)))

(defn- prepare-call [call]
  (assoc call :call/status (if (call-needs-approval? call)
                             :awaiting-approval :running)))

(defn- all-calls-terminal? [state]
  (and (seq (:tool-calls state))
       (every? #(contains? terminal-call-statuses (:call/status %))
               (vals (:tool-calls state)))))

(defn- tool-result-items [state]
  (->> (:tool-calls state) vals (sort-by :call/order)
       (mapv (fn [{:keys [call/id call/status tool/output tool/error]}]
               {:item/type :tool-result :call-id id :status status
                :output output :error error}))))

(defn- continue-after-tools [state ts]
  (let [next-turn (inc (:turn/index state))
        items (into (:items state) (tool-result-items state))]
    (if (>= next-turn (:budget/max-turns state))
      (let [state' (assoc state :loop/phase :error :items items
                          :error/kind :budget/max-turns)]
        {:state state' :effects []
         :events [(event state' :task/failed ts {:reason :budget/max-turns})]})
      (let [state' (assoc state :loop/phase :await-model :turn/index next-turn
                          :items items :tool-calls {})]
        {:state state'
         :effects [{:effect :checkpoint/write :state state'}
                   {:effect :model/respond :thread-id (:thread/id state')
                    :task-id (:task/id state') :turn-index next-turn
                    :previous-response-id (:response/id state') :items items}]
         :events [(event state' :turn/started ts {})]}))))

(defn- reject [state ts reason]
  {:state state :effects []
   :events [(event state :event/rejected ts {:reason reason})]})

(defn step
  "Reduce a model, tool, approval, or interrupt event to state/effects/events.
  Tool results may arrive in any order; they are returned in call order."
  [state {:keys [event/type ts] :as input}]
  (cond
    (terminal? state) (reject state ts :terminal)

    (= type :model/completed)
    (if-not (= :await-model (:loop/phase state))
      (reject state ts :unexpected-model-result)
      (let [calls (vec (:tool-calls input)) ids (mapv :call/id calls)
            projected (+ (:budget/tool-calls state) (count calls))]
        (cond
          (not-every? valid-call? calls) (reject state ts :invalid-tool-call)
          (not= (count ids) (count (set ids))) (reject state ts :duplicate-tool-call-id)
          (> projected (:budget/max-tool-calls state))
          (let [state' (assoc state :loop/phase :error :error/kind :budget/max-tool-calls)]
            {:state state' :effects []
             :events [(event state' :task/failed ts {:reason :budget/max-tool-calls})]})

          (empty? calls)
          (let [state' (-> state
                           (assoc :loop/phase :done :response/id (:response-id input))
                           (update :items conj {:item/type :agent-message
                                                :content (:content input)}))]
            {:state state' :effects [{:effect :checkpoint/write :state state'}]
             :events [(event state' :turn/completed ts {:response-id (:response-id input)})
                      (event state' :task/completed ts {})]})

          :else
          (let [prepared (mapv #(assoc (prepare-call %2) :call/order %1)
                               (range) calls)
                state' (-> state
                           (assoc :loop/phase (if (some call-needs-approval? calls)
                                                :await-tools-and-approval :await-tools)
                                  :response/id (:response-id input)
                                  :tool-calls (into {} (map (juxt :call/id identity) prepared))
                                  :budget/tool-calls projected)
                           (update :items conj {:item/type :agent-message
                                                :content (:content input)
                                                :tool-calls calls}))]
            {:state state'
             :effects (vec (concat [{:effect :checkpoint/write :state state'}]
                                   (for [call prepared :when (= :running (:call/status call))]
                                     {:effect :tool/execute :call call})
                                   (for [call prepared :when (= :awaiting-approval (:call/status call))]
                                     {:effect :approval/request :call call})))
             :events (into [(event state' :turn/completed ts
                                        {:response-id (:response-id input)})]
                           (map #(event state' :tool/requested ts
                                        {:call-id (:call/id %) :tool (:tool/name %)}) prepared))}))))

    (= type :approval/resolved)
    (let [call-id (:call-id input) call (get-in state [:tool-calls call-id])]
      (if-not (= :awaiting-approval (:call/status call))
        (reject state ts :unexpected-approval)
        (let [approved? (true? (:approved? input))
              call' (assoc call :call/status (if approved? :running :denied)
                                :tool/error (when-not approved? "human approval denied"))
              state' (assoc-in state [:tool-calls call-id] call')]
          (if (all-calls-terminal? state')
            (continue-after-tools state' ts)
            {:state state'
             :effects (cond-> [{:effect :checkpoint/write :state state'}]
                        approved? (conj {:effect :tool/execute :call call'}))
             :events [(event state' :approval/resolved ts
                             {:call-id call-id :approved? approved?})]}))))

    (= type :tool/completed)
    (let [call-id (:call-id input) call (get-in state [:tool-calls call-id])]
      (if-not (= :running (:call/status call))
        (reject state ts :unexpected-tool-result)
        (let [ok? (true? (:ok? input))
              call' (assoc call :call/status (if ok? :succeeded :failed)
                                :tool/output (:output input) :tool/error (:error input))
              state' (assoc-in state [:tool-calls call-id] call')]
          (if (all-calls-terminal? state')
            (continue-after-tools state' ts)
            {:state state' :effects [{:effect :checkpoint/write :state state'}]
             :events [(event state' :tool/completed ts {:call-id call-id :ok? ok?})]}))))

    (= type :task/interrupted)
    (let [state' (assoc state :loop/phase :interrupted)]
      {:state state' :effects [{:effect :checkpoint/write :state state'}]
       :events [(event state' :task/interrupted ts {:reason (:reason input)})]})

    :else (reject state ts :unknown-event)))
