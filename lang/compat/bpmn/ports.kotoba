(ns bpmn.ports
  "Host-injected ports for executing a BPMN model. bpmn-clj defines the protocols;
  the host supplies concrete implementations (call a service, evaluate a real
  expression language, prompt a user-task queue, …). The interpreter in
  `bpmn.execute` is pure orchestration over these — no I/O of its own.")

(defprotocol IActivity
  "Side of an activity/event node. `perform` receives the node map and the current
  process variables, and returns the (possibly updated) variables map."
  (perform [this node vars] "node → vars → vars'"))

(defprotocol ICondition
  "Evaluation of a sequence-flow `conditionExpression` against process variables."
  (truthy? [this condition vars] "condition-string → vars → boolean"))
