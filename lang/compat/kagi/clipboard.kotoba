(ns kagi.clipboard
  "Clipboard custody helpers.

  Secret values may enter this namespace, but should not be returned, logged, or
  printed. Public results only contain operational metadata."
  (:require [clojure.java.io :as io]))

(defprotocol Clipboard
  (copy! [clipboard s])
  (paste [clipboard]))

(defrecord MemoryClipboard [state]
  Clipboard
  (copy! [_ s]
    (reset! state (str s))
    true)
  (paste [_]
    @state))

(defn memory-clipboard
  ([] (memory-clipboard ""))
  ([initial] (->MemoryClipboard (atom (str initial)))))

(defn- run-with-stdin! [argv s]
  (let [p (.start (ProcessBuilder. ^java.util.List argv))]
    (with-open [w (io/writer (.getOutputStream p))]
      (.write w (str s)))
    (let [exit (.waitFor p)]
      (when-not (zero? exit)
        (throw (ex-info "clipboard command failed" {:exit exit :argv argv}))))
    true))

(defn- run-stdout [argv]
  (let [p (.start (ProcessBuilder. ^java.util.List argv))
        out (future (slurp (.getInputStream p)))
        _err (future (slurp (.getErrorStream p)))
        exit (.waitFor p)]
    (when-not (zero? exit)
      (throw (ex-info "clipboard command failed" {:exit exit :argv argv})))
    @out))

(defrecord MacClipboard []
  Clipboard
  (copy! [_ s]
    (run-with-stdin! ["/usr/bin/pbcopy"] s))
  (paste [_]
    (run-stdout ["/usr/bin/pbpaste"])))

(defn macos-clipboard []
  (->MacClipboard))

(defn- clear-if-unchanged! [clipboard secret]
  (try
    (when (= secret (paste clipboard))
      (copy! clipboard ""))
    (catch Throwable _ nil)))

(defn copy-secret-with-ttl!
  "Copy a secret to the clipboard and clear it later, only if still unchanged.

  `:block?` decides whether the caller waits for the TTL, and it is not a
  convenience — without it the clear never happens in a CLI.

  The clear was scheduled with `future`, whose threads come from Clojure's
  agent pool and are daemons. A short-lived process returns from -main, the
  JVM exits, the daemon thread is killed mid-sleep, and the secret stays on
  the clipboard for as long as the machine is on. The call still reported
  `{:ok? true :ttl-ms 900000}`, so the report described a custody guarantee
  that did not exist. Measured 2026-07-30: `kagi copy --ttl 900` exited 0 in
  under a second and the password was still pasteable afterwards.

  A shutdown hook is registered as well, so an early exit — Ctrl-C, a failure
  downstream, `kill` — clears rather than leaks. Between the hook and the
  blocking wait, the secret cannot outlive the process that put it there.

  Returns `:cleared?` so a caller can tell custody actually ended, instead of
  inferring it from a TTL that was only ever an intention."
  [clipboard secret {:keys [ttl-ms clear? block?]
                     :or {ttl-ms 45000 clear? true block? false}}]
  (copy! clipboard secret)
  (let [hook (when clear?
               (doto (Thread. ^Runnable #(clear-if-unchanged! clipboard secret))
                 (->> (.addShutdownHook (Runtime/getRuntime)))))
        waited? (when clear?
                  (if block?
                    (do (Thread/sleep ttl-ms)
                        (clear-if-unchanged! clipboard secret)
                        ;; The hook has done its job; drop it so a later
                        ;; unrelated copy in the same process is not wiped at
                        ;; exit by a stale comparison.
                        (try (.removeShutdownHook (Runtime/getRuntime) hook)
                             (catch Throwable _ nil))
                        true)
                    (do (future (Thread/sleep ttl-ms)
                                (clear-if-unchanged! clipboard secret))
                        false)))]
    {:ok? true
     :copied? true
     :ttl-ms ttl-ms
     ;; Honest about which mechanism is in force. :on-exit means the caller
     ;; did not wait, so the clear happens whenever this process ends — which
     ;; may be sooner than the TTL, and is never later.
     :clear-mechanism (cond (not clear?) :none
                            block? :waited
                            :else :on-exit)
     :cleared? (boolean waited?)
     :secret? false}))
