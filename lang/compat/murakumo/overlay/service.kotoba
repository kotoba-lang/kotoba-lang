;; murakumo.overlay.service — persistent local service proxy supervision.

(ns murakumo.overlay.service
  (:require [murakumo.overlay.forward :as forward]))

(defn service-spec [opts]
  {:type "murakumo.overlay.service"
   :name (or (:service opts) (:capability opts) "service")
   :listen (:listen opts)
   :mode (keyword (or (:mode opts) "bytes"))
   :restart (keyword (or (:restart opts) "always"))
   :max-restarts (Long/parseLong (str (or (:max-restarts opts) 3)))})

(defn start-once! [session spec]
  (case (:mode spec)
    :lines (forward/serve-once! session (:listen spec) forward/handle-client!)
    :bytes (forward/serve-once! session (:listen spec) forward/handle-client-bytes!)
    (forward/serve-once! session (:listen spec) forward/handle-client-bytes!)))

(defn serve!
  "Run a local proxy until stopped. A single listener stays up; per-client errors
   are contained by the forwarder loop."
  [session spec]
  (case (:mode spec)
    :lines (forward/serve! session (:listen spec) forward/handle-client!)
    :bytes (forward/serve! session (:listen spec) forward/handle-client-bytes!)
    (forward/serve! session (:listen spec) forward/handle-client-bytes!)))

(defn supervisor-plan [session spec]
  {:type "murakumo.overlay.service-supervisor"
   :ok? (boolean (:listen spec))
   :service (:name spec)
   :listen (:listen spec)
   :mode (:mode spec)
   :restart (:restart spec)
   :max-restarts (:max-restarts spec)
   :session (select-keys session [:overlay :node :name :principal :direct :relay])})
