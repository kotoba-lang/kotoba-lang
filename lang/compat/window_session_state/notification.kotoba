(ns window-session-state.notification
  "Notification queue — toast stack + consent-modal queue. Restored from
  the legacy kami-engine/kami-os `notification.rs` (deleted in
  kotoba-lang/kami-engine PR #82 'Remove Rust workspace from
  kami-engine') as part of the clj-wgsl migration (ADR-2607010930,
  com-junkawasaki/root).

  The original re-exported `kami-ui-gpu`'s generic `ToastStack` /
  `ToastLevel` and layered an OS-specific consent-modal FIFO queue on
  top. `ToastStack`'s actual GPU toast rendering/animation is out of
  scope (native substrate); here it is reimplemented as a plain,
  portable toast-list-with-expiry so `tick` remains a pure data
  transform. The consent-modal queue (human-in-the-loop approval,
  matching this project's actor `interrupt-before` / governor pattern)
  is ported in full.

  Zero-dep portable CLJC — pure data + pure functions, no IO/GPU.")

(def notification-levels #{:info :success :warning :error})

;; ── Toast stack (portable reimplementation of kami-ui-gpu's ToastStack) ──

(defn toast-stack [] {:toasts [] :next-id 1})

(defn push-toast
  "Push a toast; `duration-ms` is how long it remains before expiry."
  [stack title body level duration-ms]
  (let [id (:next-id stack)]
    (-> stack
        (update :toasts conj {:id id :title title :body body :level level
                               :duration-ms duration-ms :elapsed-ms 0})
        (update :next-id inc))))

(defn tick-toasts
  "Advance all toast timers by `dt-ms`, dropping any that have expired."
  [stack dt-ms]
  (update stack :toasts
          (fn [toasts]
            (->> toasts
                 (map #(update % :elapsed-ms + dt-ms))
                 (remove #(>= (:elapsed-ms %) (:duration-ms %)))
                 vec))))

;; ── Consent modal ────────────────────────────────

(defn consent-request
  [{:keys [agent-did agent-name action risk-tier estimated-cost context-json]}]
  {:id nil :agent-did agent-did :agent-name agent-name :action action
   :risk-tier risk-tier :estimated-cost estimated-cost :context-json context-json})

(def consent-responses #{:pending :approved :denied})

(defn notification-queue
  "Create an empty queue: no toasts, no pending consent requests."
  []
  {:toasts (toast-stack) :consent-queue [] :consent-responses [] :next-consent-id 1})

(defn push-toast!
  "Push a toast notification onto the queue's toast stack."
  [queue title body level duration-ms]
  (update queue :toasts push-toast title body level duration-ms))

(defn push-consent
  "Push a consent request, assigning it the next request ID. Returns
  `[queue' request-id]`."
  [queue request]
  (let [id (:next-consent-id queue)
        request (assoc request :id id)]
    [(-> queue
         (update :consent-queue conj request)
         (update :next-consent-id inc))
     id]))

(defn resolve-consent
  "Resolve a consent request: remove it from the pending queue and
  record the response."
  [queue request-id response]
  (-> queue
      (update :consent-queue (fn [q] (into [] (remove #(= (:id %) request-id)) q)))
      (update :consent-responses conj [request-id response])))

(defn active-consent
  "The current active consent modal (front of the FIFO queue)."
  [queue]
  (first (:consent-queue queue)))

(defn tick
  "Tick toast timers/expiry by `dt-ms`."
  [queue dt-ms]
  (update queue :toasts tick-toasts dt-ms))
