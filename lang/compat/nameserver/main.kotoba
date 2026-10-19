(ns nameserver.main
  "CLI entry point for running a real, standalone nameserver process:

    clojure -M -m nameserver.main [config.edn]

  (config path defaults to $NAMESERVER_CONFIG, then to \"config.edn\" in the
  cwd). Loads every *.zone file in :zones-dir into a zone-store-resolver,
  optionally chains a custom-tld alt-root resolver in front of it, boots
  nameserver.server, and blocks until the process is killed (SIGTERM/
  SIGINT run the JVM shutdown hook, which calls stop-server! so sockets
  close cleanly instead of leaking on `systemctl stop` / `docker stop`).

  Config shape (EDN):
    {:host \"0.0.0.0\"                     ; bind address
     :port 53                             ; UDP+TCP port (53 needs root/
                                           ; CAP_NET_BIND_SERVICE -- see README)
     :zones-dir \"/etc/nameserver/zones\"  ; directory of *.zone files (RFC 1035
                                           ; text); each file's own $ORIGIN is
                                           ; the zone key, filename is irrelevant
     :custom-tld {:suffixes [\"hogehoge.\"]         ; optional alt-root bridge
                  :gateway-host \"ipns.dweb.link\"}} ; optional"
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [zone.zone :as zone]
            [nameserver.resolver :as resolver]
            [nameserver.custom-tld :as ctld]
            [nameserver.server :as server]))

(defn- zone-files [dir]
  (->> (file-seq (io/file dir))
       (filter #(.isFile ^java.io.File %))
       (filter #(str/ends-with? (.getName ^java.io.File %) ".zone"))))

(defn- load-zones
  "Every *.zone file in `dir`, parsed and keyed by its own :zone/origin
  (not filename -- $ORIGIN in the file text is authoritative)."
  [dir]
  (let [files (zone-files dir)]
    (when (empty? files)
      (throw (ex-info (str "no *.zone files found in " dir) {:dir dir})))
    (into {} (for [f files
                   :let [z (zone/parse-str (slurp f))]]
               [(:zone/origin z) z]))))

(defn- build-resolver [{:keys [zones-dir custom-tld]}]
  (let [zones (load-zones zones-dir)
        zone-resolver (resolver/zone-store-resolver zones)]
    (println (str "loaded " (count zones) " zone(s): " (str/join ", " (keys zones))))
    (if custom-tld
      (resolver/chain-resolver
       [zone-resolver (ctld/custom-tld-resolver (set (:suffixes custom-tld)) (:gateway-host custom-tld))])
      zone-resolver)))

(defn -main [& args]
  (let [config-path (or (first args) (System/getenv "NAMESERVER_CONFIG") "config.edn")
        config (edn/read-string (slurp config-path))
        resolver (build-resolver config)
        srv (server/start-server! (assoc (select-keys config [:host :port]) :resolver resolver))]
    (println (str "org-ietf-dns listening on " (:host config "0.0.0.0")
                  ":" (:udp-port srv) " (udp) / :" (:tcp-port srv) " (tcp) -- Ctrl-C to stop"))
    (.addShutdownHook (Runtime/getRuntime)
                       (Thread. ^Runnable (fn [] (println "shutting down...") (server/stop-server! srv))))
    @(promise))) ; block forever; the shutdown hook does the real work on exit
