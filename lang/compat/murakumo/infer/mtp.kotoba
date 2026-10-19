(ns murakumo.infer.mtp
  "Canary plan and evidence contract for same-artifact Ornith MTP qualification."
  (:require [kotodama.inference.edge :as inference-edge]))

(defn candidate-plan [options]
  (inference-edge/mtp-replica-plan options))

(defn end-to-end-tok-s
  "Generated tokens divided by the complete request wall time, including prefill."
  [{:keys [generated-tokens elapsed-ms]}]
  (if (and (pos-int? generated-tokens) (pos? (double (or elapsed-ms 0))))
    (/ (* 1000.0 generated-tokens) (double elapsed-ms))
    0.0))

(defn qualify
  "Qualify real MTP execution, exact token parity, determinism and E2E speed.

  `off`, `on`, and `on-repeat` are observations from the same binary, artifact,
  prompt, sampler and generated-token bound. Token IDs—not rendered text—are
  compared. `on` must also prove that llama.cpp drafted and accepted tokens."
  [{:keys [off on on-repeat]}]
  (let [off-tokens (vec (:token-ids off))
        on-tokens (vec (:token-ids on))
        repeat-tokens (vec (:token-ids on-repeat))
        parity? (= off-tokens on-tokens)
        deterministic? (= on-tokens repeat-tokens)
        off-tok-s (end-to-end-tok-s off)
        on-tok-s (end-to-end-tok-s on)
        speedup (if (pos? off-tok-s) (/ on-tok-s off-tok-s) 0.0)
        fully-offloaded? (true? (:fully-offloaded? on))
        mtp-observed? (and (pos-int? (:drafted-tokens on))
                           (pos-int? (:accepted-tokens on))
                           (<= (:accepted-tokens on) (:drafted-tokens on)))
        execution? (and fully-offloaded? mtp-observed? parity? deterministic?
                        (pos? on-tok-s))]
    {:execution-qualified? execution?
     :speed-qualified? (and execution? (> speedup 1.0))
     :token-parity? parity?
     :deterministic? deterministic?
     :fully-offloaded? fully-offloaded?
     :mtp-observed? mtp-observed?
     :off-end-to-end-tok-s off-tok-s
     :on-end-to-end-tok-s on-tok-s
     :speedup speedup}))
