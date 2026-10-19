(ns bpmn.model
  "BPMN-as-EDN: a plain-data representation of a BPMN 2.0 process, plus a
  threading-friendly builder and the graph queries an interpreter or validator
  needs. No I/O, no third-party deps — portable .cljc (JVM, ClojureScript, SCI).

  A process is a map keyed by namespaced `:bpmn/*` keys. Nodes and flows are kept
  in id-keyed maps for O(1) lookup; element *order* is never relied on (graph
  topology comes from sequence-flow source/target, not document order):

    {:bpmn/id \"P1\" :bpmn/type :process :bpmn/name \"Order\" :bpmn/executable true
     :bpmn/nodes {\"S1\" {:bpmn/id \"S1\" :bpmn/type :start-event :bpmn/name \"…\"} …}
     :bpmn/flows {\"F1\" {:bpmn/id \"F1\" :bpmn/type :sequence-flow
                          :bpmn/source \"S1\" :bpmn/target \"T1\"
                          :bpmn/condition \"${approved}\"} …}}")

;; --- the BPMN element taxonomy we model (the practical executable core) ---

(def gateway-types
  #{:exclusive-gateway :parallel-gateway :inclusive-gateway
    :event-based-gateway :complex-gateway})

(def event-types
  #{:start-event :end-event :intermediate-catch-event
    :intermediate-throw-event :boundary-event})

(def activity-types
  #{:task :user-task :service-task :script-task :manual-task
    :business-rule-task :send-task :receive-task :sub-process :call-activity})

(def node-types (into #{} (concat gateway-types event-types activity-types)))

;; --- builder (threadable) ---

(defn process
  "A fresh, empty process. opts: {:name :executable} (:executable defaults true)."
  ([id] (process id nil))
  ([id opts]
   (cond-> {:bpmn/id id
            :bpmn/type :process
            :bpmn/executable (boolean (get opts :executable true))
            :bpmn/nodes {}
            :bpmn/flows {}}
     (:name opts) (assoc :bpmn/name (:name opts)))))

(defn- node-opts [opts]
  (cond-> {}
    (:name opts)    (assoc :bpmn/name (:name opts))
    (:default opts) (assoc :bpmn/default (:default opts))))

(defn add
  "Add a node of `type` (one of `node-types`) with `id`. opts: {:name :default}.
  `:default` (on a gateway) names the default outgoing sequence-flow id."
  ([model type id] (add model type id nil))
  ([model type id opts]
   (assoc-in model [:bpmn/nodes id]
             (merge {:bpmn/id id :bpmn/type type} (node-opts opts)))))

(defn add-flow
  "Add a sequence flow with an explicit id. opts: {:name :condition}."
  ([model id source target] (add-flow model id source target nil))
  ([model id source target opts]
   (assoc-in model [:bpmn/flows id]
             (cond-> {:bpmn/id id :bpmn/type :sequence-flow
                      :bpmn/source source :bpmn/target target}
               (:name opts)      (assoc :bpmn/name (:name opts))
               (:condition opts) (assoc :bpmn/condition (:condition opts))))))

(defn connect
  "Connect `source` → `target` with an auto-derived flow id (\"Flow_<src>_<tgt>\").
  opts: {:id :name :condition} (:id overrides the derived id)."
  ([model source target] (connect model source target nil))
  ([model source target opts]
   (add-flow model (or (:id opts) (str "Flow_" source "_" target))
             source target opts)))

;; --- queries ---

(defn node  [model id] (get-in model [:bpmn/nodes id]))
(defn flow  [model id] (get-in model [:bpmn/flows id]))
(defn nodes [model] (vals (:bpmn/nodes model)))
(defn flows [model] (vals (:bpmn/flows model)))

(defn nodes-of-type [model type] (filter #(= type (:bpmn/type %)) (nodes model)))
(defn start-events [model] (nodes-of-type model :start-event))
(defn end-events   [model] (nodes-of-type model :end-event))

(defn outgoing
  "Sequence flows leaving node `id`, ordered by flow id (deterministic)."
  [model id]
  (->> (flows model) (filter #(= id (:bpmn/source %))) (sort-by :bpmn/id)))

(defn incoming
  "Sequence flows entering node `id`, ordered by flow id."
  [model id]
  (->> (flows model) (filter #(= id (:bpmn/target %))) (sort-by :bpmn/id)))

(defn successors   [model id] (map :bpmn/target (outgoing model id)))
(defn predecessors [model id] (map :bpmn/source (incoming model id)))

(defn gateway?  [node] (contains? gateway-types  (:bpmn/type node)))
(defn activity? [node] (contains? activity-types (:bpmn/type node)))
(defn event?    [node] (contains? event-types    (:bpmn/type node)))
