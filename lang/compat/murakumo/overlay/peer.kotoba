;; murakumo.overlay.peer — peer discovery and route selection state.
;;
;; W6 product-shell + T6.4: choose-via / health / via name tokens require the
;; shipped `:overlay-peer` KIR on **every** platform. Host pure mirrors are
;; gone — cljs/nbb must preload shipped KIR (resources/ via nbb cwd,
;; register-kir!, or set-resource-loader!) before requiring this ns
;; (ADR-260731-w6-t64-keyring-peer-mirror-delete).
;; Catalog/remember map folds stay host.

(ns murakumo.overlay.peer
  (:require [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :overlay-peer)

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

(def health-unknown
  "Peer health label: unknown. Kotoba SSoT (requires oracle)."
  (o 'health-unknown []))

(def health-seen
  "Peer health label: seen. Kotoba SSoT (requires oracle)."
  (o 'health-seen []))

(def health-down
  "Peer health label: down. Kotoba SSoT (requires oracle)."
  (o 'health-down []))

(def via-direct
  "Path via direct. Kotoba SSoT (requires oracle)."
  (o 'via-direct []))

(def via-relay
  "Path via relay. Kotoba SSoT (requires oracle)."
  (o 'via-relay []))

(defn- via-kw [s]
  (keyword s))

(defn peer-record [route]
  {:type "murakumo.overlay.peer"
   :overlay (:overlay route)
   :node (:node route)
   :name (:name route)
   :direct (vec (:direct route))
   :relay (:relay route)
   :seen-at 0
   :health (via-kw health-unknown)})

(defn catalog [routes]
  (into {}
        (map (fn [route] [(:node route) (peer-record route)]))
        routes))

(defn remember
  ([peers route] (remember peers route 0))
  ([peers route seen-at]
   (assoc peers (:node route)
          (assoc (peer-record route)
                 :seen-at seen-at
                 :health (via-kw health-seen)))))

(defn mark-health [peers node health]
  (assoc-in peers [node :health] health))

(defn by-name [peers name]
  (first (filter #(= name (:name %)) (vals peers))))

(defn candidate-paths [peer]
  (let [direct-kw (via-kw via-direct)
        relay-kw (via-kw via-relay)]
    (vec (concat (map #(assoc % :via direct-kw) (:direct peer))
                 (when-let [relay (:relay peer)]
                   [(assoc relay :via relay-kw)])))))

(defn choose-path
  "Prefer healthy direct paths, then relay fallback.
   Kotoba `choose-via` (Product Value ABI options)."
  [peer]
  (let [paths (candidate-paths peer)
        direct-kw (via-kw via-direct)
        relay-kw (via-kw via-relay)
        direct? (boolean (some #(= direct-kw (:via %)) paths))
        relay? (boolean (some #(= relay-kw (:via %)) paths))
        health (name (or (:health peer) (via-kw health-unknown)))
        via (o-record 'choose-via
                      {:x (oracle/record
                           [:record :peer/via
                            [[:direct [:option :string]]
                             [:health :string]
                             [:relay [:option :string]]]]
                           {:direct (when direct? via-direct)
                            :health health
                            :relay (when relay? via-relay)})}
                      [[:x :raw]])]
    (cond
      (= via via-direct) (first (filter #(= direct-kw (:via %)) paths))
      (= via via-relay) (first (filter #(= relay-kw (:via %)) paths))
      :else nil)))
