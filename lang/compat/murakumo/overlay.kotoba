;; murakumo.overlay — CLI shell for the native overlay driver.

(ns murakumo.overlay
  (:require [kotoba.lang.edn :as edn]
            [kotoba.lang.text :as str]
            [murakumo.overlay.dial :as dial]
            [murakumo.overlay.driver :as driver]
            [murakumo.overlay.forward :as forward]
            [murakumo.overlay.relay :as relay]
            [murakumo.overlay.runtime :as runtime]
            [murakumo.overlay.service :as service]
            [murakumo.overlay.transport :as transport]))

(defn- read-edn-file [path]
  (edn/read-string (slurp path)))

(defn- adapters-lines []
  [(pr-str {:type "murakumo.overlay.adapters"
            :adapters (runtime/adapter-records)})])

(defn- transports-lines []
  [(pr-str {:type "murakumo.overlay.transports"
            :transports (transport/transport-records)})])

(defn- relay-session-result [args]
  (driver/relay-result (driver/parse-argv (into ["relay"] args))))

(defn- dial-session-result [args]
  (driver/dial-result (driver/parse-argv (into ["dial"] args))))

(defn- relay-check-lines [args]
  (let [result (relay-session-result args)]
    (if (:ok? result)
      [(pr-str (relay/check! (:session result)))]
      (driver/result-lines result))))

(defn- dial-check-lines [args]
  (let [opts (driver/parse-argv (into ["dial-check"] args))
        via (keyword (:via opts))
        frame (:frame opts)
        frames (when (:frames opts)
                 (->> (str/split (str (:frames opts)) #",")
                      (remove str/blank?)
                      vec))
        result (dial-session-result args)]
    (if (:ok? result)
      [(pr-str (dial/check! (:session result)
                            {:endpoint (when (= :relay via) :relay)
                             :frame frame
                             :frames frames}))]
      (driver/result-lines result))))

(defn- transport-probe-lines [args]
  (let [opts (driver/parse-argv (into ["transport-probe"] args))
        via (keyword (:via opts))
        result (dial-session-result args)]
    (if (:ok? result)
      [(pr-str (transport/direct-probe! (:session result)
                                        (if (= :relay via) :relay :direct)))]
      (driver/result-lines result))))

(defn- adapter-plan-lines [args]
  (let [opts (driver/parse-argv (into ["adapter-plan"] args))
        via (keyword (:via opts))
        action (keyword (or (:action opts) "check"))
        result (dial-session-result args)]
    (if (:ok? result)
      [(pr-str (transport/adapter-plan (:session result)
                                       (if (= :relay via) :relay :direct)
                                       action))]
      (driver/result-lines result))))

(defn- adapter-check-lines [args]
  (let [opts (driver/parse-argv (into ["adapter-check"] args))
        via (keyword (:via opts))
        result (dial-session-result args)]
    (if (:ok? result)
      [(pr-str (transport/adapter-check! (:session result)
                                         (if (= :relay via) :relay :direct)))]
      (driver/result-lines result))))

(defn- adapter-supervisor-lines [args]
  (let [opts (driver/parse-argv (into ["adapter-supervisor"] args))
        via (keyword (:via opts))
        action (keyword (or (:action opts) "serve"))
        restart (keyword (or (:restart opts) "always"))
        max-restarts (Long/parseLong (str (or (:max-restarts opts) 3)))
        result (dial-session-result args)]
    (if (:ok? result)
      [(pr-str (transport/adapter-supervisor-plan
                (:session result)
                (if (= :relay via) :relay :direct)
                {:action action
                 :restart restart
                 :max-restarts max-restarts}))]
      (driver/result-lines result))))

(defn- serve-relay! [args]
  (let [result (relay-session-result args)]
    (if (:ok? result)
      (relay/serve! (:session result))
      (do
        (doseq [line (driver/result-lines result)]
          (println line))
        (System/exit 2)))))

(defn- local-forward! [args once?]
  (let [opts (driver/parse-argv (into ["local-forward"] args))
        result (dial-session-result args)]
    (cond
      (not (:listen opts))
      (do
        (println "murakumo-overlay error: missing-options")
        (println "  missing: listen")
        (System/exit 2))

      (:ok? result)
      (let [report (if once?
                     (forward/serve-once! (:session result) (:listen opts))
                     (forward/serve! (:session result) (:listen opts)))]
        (when once?
          (println (pr-str report))))

      :else
      (do
        (doseq [line (driver/result-lines result)]
          (println line))
        (System/exit 2)))))

(defn- service-plan-lines [args]
  (let [opts (driver/parse-argv (into ["service-plan"] args))
        result (dial-session-result args)]
    (cond
      (not (:listen opts))
      ["murakumo-overlay error: missing-options"
       "  missing: listen"]

      (:ok? result)
      [(pr-str (service/supervisor-plan (:session result)
                                        (service/service-spec opts)))]

      :else
      (driver/result-lines result))))

(defn- service-proxy! [args once?]
  (let [opts (driver/parse-argv (into ["service-proxy"] args))
        result (dial-session-result args)]
    (cond
      (not (:listen opts))
      (do
        (println "murakumo-overlay error: missing-options")
        (println "  missing: listen")
        (System/exit 2))

      (:ok? result)
      (let [spec (service/service-spec opts)]
        (if once?
          (println (pr-str (service/start-once! (:session result) spec)))
          (service/serve! (:session result) spec)))

      :else
      (do
        (doseq [line (driver/result-lines result)]
          (println line))
        (System/exit 2)))))

(defn- local-forward-bytes! [args once?]
  (let [opts (driver/parse-argv (into ["local-forward-bytes"] args))
        result (dial-session-result args)]
    (cond
      (not (:listen opts))
      (do
        (println "murakumo-overlay error: missing-options")
        (println "  missing: listen")
        (System/exit 2))

      (:ok? result)
      (let [handler forward/handle-client-bytes!
            report (if once?
                     (forward/serve-once! (:session result) (:listen opts) handler)
                     (forward/serve! (:session result) (:listen opts) handler))]
        (when once?
          (println (pr-str report))))

      :else
      (do
        (doseq [line (driver/result-lines result)]
          (println line))
        (System/exit 2)))))

(defn -main [& args]
  (cond
    (= "serve-relay" (first args))
    (serve-relay! (rest args))

    (= "local-forward" (first args))
    (local-forward! (rest args) false)

    (= "local-forward-once" (first args))
    (local-forward! (rest args) true)

    (= "local-forward-bytes" (first args))
    (local-forward-bytes! (rest args) false)

    (= "local-forward-bytes-once" (first args))
    (local-forward-bytes! (rest args) true)

    (= "service-proxy" (first args))
    (service-proxy! (rest args) false)

    (= "service-proxy-once" (first args))
    (service-proxy! (rest args) true)

    :else
    (let [lines (case (first args)
                  "adapters" (adapters-lines)
                  "transports" (transports-lines)
                  "dial-check" (dial-check-lines (rest args))
                  "transport-probe" (transport-probe-lines (rest args))
                  "adapter-plan" (adapter-plan-lines (rest args))
                  "adapter-check" (adapter-check-lines (rest args))
                  "adapter-supervisor" (adapter-supervisor-lines (rest args))
                  "relay-check" (relay-check-lines (rest args))
                  "service-plan" (service-plan-lines (rest args))
                  (driver/command-lines args read-edn-file runtime/execute-step))
          ok? (not-any? #(re-find #"^murakumo-overlay error:" %) lines)]
      (doseq [line lines]
        (println line))
      (when-not ok?
        (System/exit 2)))))
