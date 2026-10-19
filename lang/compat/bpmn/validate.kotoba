(ns bpmn.validate
  "Structural validation of a BPMN-as-EDN model. Pure: returns a vector of problem
  maps `{:bpmn/severity :error|:warn :bpmn/code … :bpmn/id … :bpmn/msg …}` so a
  caller decides how to surface them. `valid?` is true iff there are no :error-level
  problems (warnings are advisory)."
  (:require [bpmn.model :as m]))

(defn- problem [severity code id msg]
  {:bpmn/severity severity :bpmn/code code :bpmn/id id :bpmn/msg msg})

(defn problems
  "Return a vector of structural problems with `model`."
  [model]
  (let [nodes  (:bpmn/nodes model)
        node-ids (set (keys nodes))
        ps (transient [])]
    ;; node key / id agreement and known type
    (doseq [[id n] nodes]
      (when (not= id (:bpmn/id n))
        (conj! ps (problem :error :node/id-mismatch id
                           (str "node keyed " id " carries :bpmn/id " (:bpmn/id n)))))
      (when-not (contains? m/node-types (:bpmn/type n))
        (conj! ps (problem :error :node/unknown-type id
                           (str "unknown node type " (:bpmn/type n))))))
    ;; flows must reference existing nodes
    (doseq [[id f] (:bpmn/flows model)]
      (when-not (contains? node-ids (:bpmn/source f))
        (conj! ps (problem :error :flow/dangling-source id
                           (str "sequenceFlow " id " sourceRef " (:bpmn/source f)
                                " is not a node"))))
      (when-not (contains? node-ids (:bpmn/target f))
        (conj! ps (problem :error :flow/dangling-target id
                           (str "sequenceFlow " id " targetRef " (:bpmn/target f)
                                " is not a node")))))
    ;; at least one start and one end event
    (when (empty? (m/start-events model))
      (conj! ps (problem :warn :process/no-start (:bpmn/id model)
                         "process has no start event")))
    (when (empty? (m/end-events model))
      (conj! ps (problem :warn :process/no-end (:bpmn/id model)
                         "process has no end event")))
    ;; connectivity of each node (start needs no incoming, end needs no outgoing)
    (doseq [n (m/nodes model)]
      (let [id (:bpmn/id n) t (:bpmn/type n)]
        (when (and (not= t :start-event) (empty? (m/incoming model id)))
          (conj! ps (problem :warn :node/no-incoming id
                             (str "node " id " has no incoming flow"))))
        (when (and (not= t :end-event) (empty? (m/outgoing model id)))
          (conj! ps (problem :warn :node/no-outgoing id
                             (str "node " id " has no outgoing flow"))))))
    ;; exclusive gateway with >1 outgoing should be decidable (conditions or default)
    (doseq [g (m/nodes-of-type model :exclusive-gateway)]
      (let [outs (m/outgoing model (:bpmn/id g))]
        (when (and (> (count outs) 1)
                   (not (:bpmn/default g))
                   (some #(nil? (:bpmn/condition %)) outs))
          (conj! ps (problem :warn :gateway/indeterminate (:bpmn/id g)
                             "exclusive gateway has an unconditioned branch and no default")))))
    ;; inclusive gateway with no default and every outgoing flow conditioned risks
    ;; taking zero flows at runtime if all conditions evaluate false
    (doseq [g (m/nodes-of-type model :inclusive-gateway)]
      (let [outs (m/outgoing model (:bpmn/id g))]
        (when (and (seq outs)
                   (not (:bpmn/default g))
                   (every? :bpmn/condition outs))
          (conj! ps (problem :warn :gateway/indeterminate (:bpmn/id g)
                             "inclusive gateway has no default and every outgoing flow is conditioned")))))
    (persistent! ps)))

(defn errors [model] (filterv #(= :error (:bpmn/severity %)) (problems model)))

(defn valid?
  "True iff `model` has no :error-level structural problems."
  [model]
  (empty? (errors model)))
