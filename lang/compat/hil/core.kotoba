(ns hil.core
  "Portable, minimal Human-in-the-Loop approval boundary.

  Hosts implement IHumanApproval to present the request through a native alert,
  web dialog, chat card, or another interactive surface. The request contains
  only reviewable metadata; callers must never place credentials or raw page
  content in it."
  (:require [clojure.string :as str]))

(def decisions #{:approved :rejected :dismissed})

(def ^:private alert-limits
  {:id 80 :title 120 :summary 280 :action 120 :impact 180 :input-label 80})

(defprotocol IHumanApproval
  (-request-approval! [prompt request]
    "Present a minimal approval request and return :approved, :rejected, or :dismissed."))

(defn approval-request
  "Construct an approval request safe for an alert surface.

  Required keys: :id, :title, :summary, :action. Optional :impact explains the
  external effect. `:input-label` requests a short, non-secret value from the
  native dialog. No credential, token, MFA, or full page content belongs here."
  [{:keys [id title summary action impact input-label] :as request}]
  (when-not (every? #(and (string? %) (not (str/blank? %)))
                    [id title summary action])
    (throw (ex-info "HIL request needs non-blank id, title, summary, and action"
                    {:request (select-keys request [:id :title :action])})))
  (doseq [[key limit] alert-limits
          :let [value (get request key)]
          :when (and value (> (count value) limit))]
    (throw (ex-info "HIL request field exceeds alert limit"
                    {:field key :limit limit})))
  (when (and input-label
             (re-find #"(?i)(password|secret|token|api.?key|mfa|otp|code)" input-label))
    (throw (ex-info "HIL input must not request a secret" {:field :input-label})))
  (cond-> {:id id :title title :summary summary :action action}
    impact (assoc :impact impact)
    input-label (assoc :input-label input-label)))

(defn alert-text
  "The only text an alert host needs to display. Intentionally excludes IDs and
  arbitrary context, so alerts remain focused on the required decision."
  [{:keys [summary action impact]}]
  (str summary "\n\nAction: " action
       (when impact (str "\nImpact: " impact))))

(defn request-with-input!
  "Ask a human for a decision and optional short non-secret input.
  Returns `{:decision :approved|:rejected|:dismissed :input string?}`."
  [prompt request]
  (let [request (approval-request request)
        response (-request-approval! prompt request)
        {:keys [decision input]} (if (keyword? response)
                                   {:decision response}
                                   response)]
    (when-not (contains? decisions decision)
      (throw (ex-info "invalid HIL decision" {:decision decision :request-id (:id request)})))
    (cond-> {:decision decision}
      (and (= :approved decision) (string? input)) (assoc :input input))))

(defn request!
  "Ask a human for a decision and validate the host response."
  [prompt request]
  (:decision (request-with-input! prompt request)))

(defn mock-prompt
  "Deterministic prompt implementation for tests. Accepts a decision or fn."
  [decision]
  (reify IHumanApproval
    (-request-approval! [_ request]
      (if (fn? decision) (decision request) decision))))
