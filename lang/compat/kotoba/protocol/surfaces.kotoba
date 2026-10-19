(ns kotoba.protocol.surfaces
  "Project URL / DNS / HTTP / Git / pkg / RPC onto the 8 planes.

  Do not smash them into one Merkle DAG (ADR-2608145200). Each surface
  names the planes it occupies and whether it is identity."
  (:require [kotoba.protocol.layers :as layers]))

(def surfaces
  {:url {:planes #{:transport :naming}
         :identity? false
         :note "location + path. not a CID"}
   :dns {:planes #{:naming}
         :identity? false
         :note "mutable name. DNSLink is an alias toward IPNS"}
   :http {:planes #{:transport :session}
          :identity? false
          :note "bytes + request stream. gateway is a projection"}
   :git-blob {:planes #{:identity}
              :identity? true
              :address :output
              :link nil}
   :git-tree {:planes #{:content-protocol}
              :identity? true
              :address :output
              :link :merkle}
   :git-commit {:planes #{:identity :content-protocol}
                :identity? true
                :address :output
                :link :merkle}
   :git-ref {:planes #{:naming}
             :identity? false
             :link :action}
   :pkg-nar {:planes #{:identity}
             :identity? true
             :address :output}
   :pkg-drv {:planes #{:identity}
             :identity? true
             :address :input}
   :pkg-tag {:planes #{:naming}
             :identity? false}
   :rpc-call {:planes #{:identity :session :authorization}
              :identity? true
              :address :input}
   :rpc-result {:planes #{:identity}
                :identity? true
                :address :output}
   :ipni {:planes #{:discovery}
          :identity? false
          :note "CID → provider. does not rewrite the CID"}
   :unixfs-path {:planes #{}
                 :identity? false
                 :rejected true
                 :note "not used. leaf already has a CID"}})

(defn project
  "Surface keyword → plane occupancy | {:error :unknown-surface}."
  [surface]
  (or (surfaces surface)
      {:error :unknown-surface :value surface}))

(defn identity-surfaces
  "Surfaces whose public form is a content hash."
  []
  (into (sorted-set)
        (keep (fn [[k v]] (when (:identity? v) k)))
        surfaces))

(defn planes-of
  [surface]
  (:planes (project surface)))

(defn known-surface?
  [surface]
  (contains? surfaces surface))

(defn every-plane-declared?
  "Drift check: every plane named by a surface exists in `layers/planes`."
  []
  (let [declared (into #{} (map :plane) layers/planes)]
    (every? declared (mapcat :planes (vals surfaces)))))
