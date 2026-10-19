(ns kotoba.shell.input
  "Pure Hiccup attributes for binding a native text control to an explicit
  action. Hosts emit the current value as `value`; apps retain its meaning.")

(defn field
  ([id] (field id {}))
  ([id attrs]
   (when-not (and (string? id) (seq id))
     (throw (ex-info "input id must be a non-empty string" {:id id})))
   (assoc attrs :id id)))

(defn submit
  "Attributes for an action control that reads the current value of input-id."
  [action input-id]
  (when-not (and (string? action) (seq action))
    (throw (ex-info "action must be a non-empty string" {:action action})))
  (when-not (and (string? input-id) (seq input-id))
    (throw (ex-info "input id must be a non-empty string" {:input-id input-id})))
  {:data-action action :data-input-id input-id})
