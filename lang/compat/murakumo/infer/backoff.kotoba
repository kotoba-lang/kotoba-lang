(ns murakumo.infer.backoff
  "How a node-resident poller waits after a failure.

  Measured 2026-09-02 (ADR-260902-fleet-outbound-hygiene): `infer-join`'s
  poll loop retried a failed fetch on its fixed 5 s cadence with no backoff.
  Once the node's ephemeral port range was full of TIME_WAIT sockets, every
  fetch failed instantly with EADDRNOTAVAIL, every retry opened another
  connection, and the range never drained — on benjamin for 60 days. The
  same exhaustion made the mesh watchdog's own probe fail, so it killed a
  healthy kotoba-server every two minutes on simeon.

  Two rules, both pure so the JVM suite executes them:
    1. a failure doubles the wait, bounded (`next-delay-ms`), with jitter so a
       fleet of workers does not re-synchronise on the gateway;
    2. a failure is CLASSIFIED (`classify`), because the right response to
       'my own ports are exhausted' is to wait a long time and say so — not to
       treat it as the gateway being down."
  (:require [kotoba.lang.text :as str]))

(def default-policy
  "`:base-ms` is the loop's normal cadence; `:max-ms` bounds the wait so a
   worker that has been failing for an hour still notices recovery within
   five minutes."
  {:base-ms 5000 :max-ms 300000 :factor 2})

(defn next-delay-ms
  "Delay before the next attempt after `failures` consecutive failures
   (0 = last attempt succeeded → base cadence). `jitter` ∈ [0,1) spreads the
   fleet; pass a fixed value in tests."
  ([failures] (next-delay-ms default-policy failures 0.0))
  ([{:keys [base-ms max-ms factor] :as _policy} failures jitter]
   (let [f (max 0 (long failures))
         raw (* base-ms (Math/pow factor f))
         bounded (min max-ms raw)
         spread (* bounded 0.25 (min 0.999 (max 0.0 jitter)))]
     (long (+ bounded spread)))))

(def local-exhaustion-codes
  "OS errors that mean THIS host cannot open a socket. Retrying faster makes
   them worse; the honest move is a long wait and a loud log line."
  #{"EADDRNOTAVAIL" "EMFILE" "ENFILE" "ENOBUFS"})

(def remote-unreachable-codes
  #{"ECONNREFUSED" "ECONNRESET" "ENOTFOUND" "EAI_AGAIN" "ETIMEDOUT"
    "UND_ERR_CONNECT_TIMEOUT" "UND_ERR_SOCKET" "EHOSTUNREACH" "ENETUNREACH"
    ;; Added 2026-09-10 from benjamin's log, where both were classified
    ;; `unknown`. Neither is a mystery: undici raises HEADERS_TIMEOUT when the
    ;; response headers do not arrive in time and INFO when the connection is
    ;; torn down under it. Both mean the far end did not answer, which is what
    ;; :remote-unreachable is for. `unknown` reached the same delay curve, so
    ;; this changes no timing -- it changes what the log says the node is
    ;; waiting on, and an operator reading `unknown` twice an hour cannot tell
    ;; a gateway outage from a node fault.
    "UND_ERR_HEADERS_TIMEOUT" "UND_ERR_BODY_TIMEOUT" "UND_ERR_INFO"})

(defn classify
  "`{:code :message}` (the host pulls `error.cause.code` / `error.message` off
   a fetch failure) → one of
     :local-exhaustion  this host is out of sockets/ports/fds
     :remote-unreachable the gateway/DNS/network refused or timed out
     :http              the request completed with a non-2xx we chose to throw
     :unknown"
  [{:keys [code message status]}]
  (let [c (some-> code str str/upper)]
    (cond
      (contains? local-exhaustion-codes c) :local-exhaustion
      (contains? remote-unreachable-codes c) :remote-unreachable
      (and (number? status) (>= status 400)) :http
      (and (string? message) (re-find #"(?i)assign requested address|too many open files" message))
      :local-exhaustion
      :else :unknown)))

(defn delay-for
  "The wait after a classified failure: local exhaustion jumps straight to
   the ceiling (nothing this loop does will free the ports faster), everything
   else backs off geometrically."
  ([kind failures] (delay-for default-policy kind failures 0.0))
  ([policy kind failures jitter]
   (if (= :local-exhaustion kind)
     (:max-ms policy)
     (next-delay-ms policy failures jitter))))
