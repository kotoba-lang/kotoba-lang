(ns aiueos.watchdog
  "Deadline enforcement on a dedicated interruptible worker.

  Chicory's supported execution-limit mechanism observes thread interruption.
  A timed-out worker is interrupted, the executor is shut down, and the call
  does not return until termination is observed. Failure to terminate is a
  distinct fail-closed containment error."
  (:refer-clojure :exclude [run!])
  (:import [java.util.concurrent Callable ExecutionException Executors
            ThreadFactory TimeUnit TimeoutException]))

(def default-termination-grace-ms 1000)

(defn run!
  "Run zero-argument F with DEADLINE-MS. Returns {:status :completed :value v
  :elapsed-ms n}, or {:status :timed-out ...} only after worker termination.
  Re-throws F's exception. Throws :watchdog-containment-failed if interruption
  did not terminate the worker within the grace period."
  ([deadline-ms f]
   (run! deadline-ms default-termination-grace-ms f))
  ([deadline-ms termination-grace-ms f]
   (when-not (and (integer? deadline-ms) (pos? deadline-ms))
     (throw (ex-info "watchdog deadline must be a positive integer"
                     {:type :invalid-watchdog-deadline
                      :deadline-ms deadline-ms})))
   (when-not (and (integer? termination-grace-ms)
                  (pos? termination-grace-ms))
     (throw (ex-info "watchdog termination grace must be a positive integer"
                     {:type :invalid-watchdog-grace
                      :termination-grace-ms termination-grace-ms})))
   (let [worker (atom nil)
         factory (reify ThreadFactory
                   (newThread [_ runnable]
                     (doto (Thread. runnable "aiueos-wasm-watchdog")
                       (.setDaemon true)
                       (#(reset! worker %)))))
         executor (Executors/newSingleThreadExecutor factory)
         started (System/nanoTime)
         future (.submit executor ^Callable (fn [] (f)))]
     (try
       (let [value (.get future deadline-ms TimeUnit/MILLISECONDS)]
         {:status :completed
          :value value
          :elapsed-ms (quot (- (System/nanoTime) started) 1000000)})
       (catch TimeoutException _
         (.cancel future true)
         (.shutdownNow executor)
         (let [terminated? (.awaitTermination
                            executor termination-grace-ms
                            TimeUnit/MILLISECONDS)
               elapsed-ms (quot (- (System/nanoTime) started) 1000000)]
           (when-not terminated?
             (throw
              (ex-info "watchdog failed to terminate isolated worker"
                       {:type :watchdog-containment-failed
                        :deadline-ms deadline-ms
                        :termination-grace-ms termination-grace-ms
                        :worker-alive? (boolean (and @worker (.isAlive ^Thread @worker)))})))
           {:status :timed-out
            :deadline-ms deadline-ms
            :elapsed-ms elapsed-ms
            :terminated? true}))
       (catch ExecutionException error
         (throw (.getCause error)))
       (finally
         (.shutdownNow executor))))))
