;; murakumo.overlay.stream — deterministic stream/session framing.
;;
;; W6 product-shell + T6.4: window size + advance-seq + ack-accepted? + type
;; tokens via kotoba overlay_stream_core require the shipped `:overlay-stream`
;; KIR on **every** platform. Host pure mirrors are gone — cljs/nbb must
;; preload shipped KIR (resources/ via nbb cwd, register-kir!, or
;; set-resource-loader!) before requiring this ns
;; (ADR-260731-w6-t64-crypto-stream-mirror-delete).
;; stream-id hashing and frame maps stay host.

(ns murakumo.overlay.stream
  (:require [murakumo.identity :as identity]
            [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :overlay-stream)

(defn- o
  "Call a pure export. Requires the shipped oracle on every platform (T6.4)."
  [export args]
  (oracle/require-ready! oid)
  (oracle/call oid export args))

(defn- o-record
  "T5.2: structural host map → call-record (requires shipped oracle)."
  [export host-map field-specs]
  (oracle/require-ready! oid)
  (oracle/call-record oid export host-map field-specs))

;; ── residual type tokens + scalars ───────────────────────────────────

(def default-window-size
  "Stream receive window size. Kotoba SSoT (requires oracle)."
  (oracle/i64->host (o 'default-window-size [])))

(def initial-next-seq
  "Initial :next-seq for open-stream. Kotoba SSoT (requires oracle)."
  (oracle/i64->host (o 'initial-next-seq [])))

(def type-stream
  (o 'type-stream []))

(def type-frame
  (o 'type-frame []))

(def type-ack
  (o 'type-ack []))

(defn stream-id
  "Stable stream id for a logical service connection."
  [session service opened-at]
  (identity/sha256-hex
   (pr-str {:overlay (:overlay session)
            :node (:node session)
            :name (:name session)
            :principal (:principal session)
            :service service
            :opened-at opened-at})))

(defn open-stream
  ([session service] (open-stream session service 0))
  ([session service opened-at]
   {:type type-stream
    :id (stream-id session service opened-at)
    :overlay (:overlay session)
    :node (:node session)
    :name (:name session)
    :principal (:principal session)
    :service service
    :opened-at opened-at
    :next-seq initial-next-seq
    :window default-window-size
    :closed? false}))

(defn frame
  "Build one ordered stream frame."
  [stream payload]
  {:type type-frame
   :stream (:id stream)
   :overlay (:overlay stream)
   :node (:node stream)
   :name (:name stream)
   :service (:service stream)
   :seq (:next-seq stream)
   :payload payload})

(defn advance [stream]
  "T5.2: structural map → call-record for advance-seq."
  (update stream :next-seq
          (fn [s]
            (oracle/i64->host
             (o-record 'advance-seq
                       {:seq s}
                       [[:seq :i64]])))))

(defn frames
  "Turn payloads into ordered frames and the advanced stream state."
  [stream payloads]
  (reduce (fn [{:keys [stream frames]} payload]
            {:stream (advance stream)
             :frames (conj frames (frame stream payload))})
          {:stream stream :frames []}
          payloads))

(defn ack
  "T5.2: structural map → call-record for ack-accepted?."
  [frame accepted?]
  {:type type-ack
   :stream (:stream frame)
   :seq (:seq frame)
   :accepted? (oracle/bool->host
               (o-record 'ack-accepted?
                         {:accepted? accepted?}
                         [[:accepted? :bool]]))})

(defn close [stream reason]
  (assoc stream :closed? true :close-reason reason))
