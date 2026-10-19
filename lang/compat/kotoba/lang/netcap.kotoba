(ns kotoba.lang.netcap
  "Networking capability layer: tcp/udp/dns as wit capability tokens + INet
  host-injected driver. Same design as device — a component touches the network
  only if granted net:tcp/net:udp/net:dns. The driver (socket-level) is
  host-injected (WASM premise). Consumes wit + coll. No third-party deps; .cljc."
  (:require [kotoba.lang.wit :as w]
            [kotoba.lang.coll :as c])
  (:require [kotoba.netcap.net :as net-p]))

(def ^:private surface-effects
  {:tcp  #{:connect :read :write :close}
   :udp  #{:send :recv}
   :dns  #{:resolve}})

(defn- surface->cap [s] (str "net:" (name s)))

(defn surfaces []
  (for [[s effects] surface-effects]
    {:wit/capability (surface->cap s) :wit/effects effects}))

(defn surface-cap [s] (surface->cap s))

(def INet
  "The protocol itself lives in one repo of its own now. This name is that
  SAME protocol, not a second one: an implementation reified against either
  is accepted by both (ADR-2609091900)."
  net-p/Net)

(def close-net net-p/close-net)
(def connect net-p/connect)
(def read-net net-p/read-net)
(def recv-udp net-p/recv-udp)
(def resolve net-p/resolve)
(def send-udp net-p/send-udp)
(def write-net net-p/write-net)

(defn mock-net
  "An in-memory INet for tests."
  []
  (let [conns (atom {})]
    (reify INet
      (connect [_ host port]
        (let [handle (str host ":" port)
              buf (atom [])]
          (swap! conns assoc handle {:host host :port port :buf buf})
          handle))
      (read-net [_ handle]
        (when-let [conn (get @conns handle)]
          (let [b (:buf conn)]
            (when-let [v (first @b)]
              (swap! b rest)
              v))))
      (write-net [_ handle data]
        (when-let [conn (get @conns handle)]
          (swap! (:buf conn) conj data) true))
      (close-net [_ handle]
        (swap! conns dissoc handle) true)
      (send-udp [_ _host _port _data] true)
      (recv-udp [_ _handle _timeout] nil)
      (resolve [_ hostname]
        (if (= hostname "localhost") ["127.0.0.1"] ["0.0.0.0"])))))

(def denied ::denied)

(defn make-net-manager [policy drivers opts]
  {:policy policy :drivers drivers :opts opts})

(defn discover [mgr]
  (filter #(w/allows? (:policy mgr) (surface-cap %)) (keys surface-effects)))

(defn- gate [mgr surface]
  (when (w/allows? (:policy mgr) (surface-cap surface))
    (get-in mgr [:drivers surface])))

(defn call [mgr surface method args]
  (if-let [net (gate mgr surface)]
    (case [surface method]
      [:tcp :connect]  (connect net (:host args) (:port args))
      [:tcp :read]     (read-net net (:handle args))
      [:tcp :write]    (write-net net (:handle args) (:data args))
      [:tcp :close]    (close-net net (:handle args))
      [:udp :send]     (send-udp net (:host args) (:port args) (:data args))
      [:udp :recv]     (recv-udp net (:handle args) (or (:timeout args) 0))
      [:dns :resolve]  (resolve net (:hostname args))
      ::unknown-method)
    denied))
