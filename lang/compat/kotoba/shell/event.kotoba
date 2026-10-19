(ns kotoba.shell.event
  "Host -> app events, and the shape of an app's response to one.

  `kotoba.shell.input` already describes the app -> host half: an app labels a
  control with `:data-action` and, optionally, `:data-input-id`. This namespace
  is the missing return path -- what the host sends BACK, and what an app hands
  over when it has handled it.

  Why this did not exist: until now nothing consumed host events. The AppKit
  host prints them to stdout, `launcher.clj` never reads them, and
  `app run --execute` merely re-evaluates the app's zero-argument entry on a
  timer (KOTOBA_SHELL_ACTION_REFRESH_SECONDS) and re-commits the surface. The
  surface was a read-only projection; interaction was routed around the app
  entirely, by POSTing `data-endpoint`/`data-body` to a localhost service. An
  app that owns its own state needs `state + event -> next-state + effects`,
  which is what `step-result` fixes the shape of.

  Everything here is pure and allocation-only. Decoding takes an already
  parsed map (string keys, as JSON gives them) rather than a JSON string, so
  this namespace stays portable and free of a parser dependency -- the caller
  owns transport."
  (:require [clojure.string :as str]))

(def contract "kotoba.shell.app-event.v0")

(def ^:private input-kinds
  {"input/action" :action
   "input/action-cancelled" :action-cancelled
   "input/resize" :resize})

(def ^:private lifecycle-kinds
  {"lifecycle/launch" :launch
   "lifecycle/activate" :activate
   "lifecycle/terminate" :terminate
   "lifecycle/smoke-ready" :smoke-ready})

;; Emitted by the host but deliberately NOT an app event: `visual/captured`
;; reports a screenshot to the visual-test/kaizen harness. Routing it to an app
;; would let a test artifact drive application state, so `decode` rejects it
;; with a reason rather than silently ignoring it.
(def ^:private harness-kinds #{"visual/captured"})

(def kinds (into (sorted-set) (concat (vals input-kinds) (vals lifecycle-kinds))))

(defn- reject! [message data]
  (throw (ex-info message (assoc data :contract contract))))

(defn- non-blank [value]
  (when (and (string? value) (seq (str/trim value))) value))

;; Fields the decoder gives a name of its own. Anything else the host sends
;; (`path` on a file pick, and whatever a future host adds) survives in
;; :event/data rather than being dropped -- a host must be able to carry a
;; payload without this namespace being taught about it first.
(def ^:private reserved-fields #{"event" "action" "value" "width" "height"})

(defn decode
  "Canonicalize one host event map (string keys) into an app-facing event.

  Unknown `event` values are rejected rather than passed through: an app
  dispatching on :event/kind must not silently receive a kind it has no branch
  for."
  [raw]
  (when-not (map? raw)
    (reject! "host event must be a map" {:raw raw}))
  (let [name* (get raw "event")
        kind (or (get input-kinds name*) (get lifecycle-kinds name*))]
    (when (contains? harness-kinds name*)
      (reject! "harness event is not an app event" {:event name*}))
    (when-not kind
      (reject! "unknown host event" {:event name*}))
    (let [action (non-blank (get raw "action"))
          extra (into {} (remove (comp reserved-fields key)) raw)]
      (when (and (contains? #{:action :action-cancelled} kind) (nil? action))
        (reject! "action events require a non-blank action" {:event name*}))
      (cond-> {:event/kind kind}
        action (assoc :event/action action)
        ;; `value` is the current text of the control named by the action's
        ;; :data-input-id. Absent (not "") when the action has no bound input,
        ;; so an app can tell "no input" from "empty input".
        (contains? raw "value") (assoc :event/value (str (get raw "value")))
        (= :resize kind) (assoc :event/width (get raw "width")
                                :event/height (get raw "height"))
        (seq extra) (assoc :event/data extra)))))

(defn action
  "Construct an action event, as a host would send it."
  ([action-name] (action action-name nil))
  ([action-name value]
   (when-not (non-blank action-name)
     (reject! "action must be a non-blank string" {:action action-name}))
   (cond-> {:event/kind :action :event/action action-name}
     (some? value) (assoc :event/value (str value)))))

(defn action?
  "True for events an app should treat as an explicit user action. Cancelled
  actions and lifecycle notifications are deliberately excluded -- an app must
  opt into those rather than mistake a dismissed file picker for a submit."
  [event]
  (= :action (:event/kind event)))

(defn step-result
  "Validate what an app returns from one step.

  `:kotoba.app/surface-ops` is the same vector `app run` already commits, so an
  app that grows a step function keeps the renderer it had. `:app/effects` is
  data, never a thunk: the app names what should happen and the host decides
  whether it may, which is the whole reason effects are not ambient functions
  in this stack."
  [result]
  (when-not (map? result)
    (reject! "step result must be a map" {:result result}))
  (let [ops (:kotoba.app/surface-ops result)
        effects (get result :app/effects [])]
    (when-not (and (sequential? ops) (seq ops))
      (reject! "step result requires non-empty :kotoba.app/surface-ops"
               {:ops ops}))
    (when-not (every? sequential? ops)
      (reject! "every surface op must be sequential" {:ops ops}))
    (when-not (sequential? effects)
      (reject! ":app/effects must be sequential" {:effects effects}))
    (when-not (every? #(and (sequential? %) (keyword? (first %))) effects)
      (reject! "every effect must be a sequential keyed by a keyword"
               {:effects effects}))
    {:app/state (:app/state result)
     :kotoba.app/surface-ops (vec ops)
     :app/effects (vec effects)}))
