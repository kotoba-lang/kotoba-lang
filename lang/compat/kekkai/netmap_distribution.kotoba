(ns kekkai.netmap-distribution
  "Cloud-provider-independent distribution of signed Kekkai netmaps.

  Roots are independent filesystem namespaces: local disks, mounted object
  stores, replicated volumes, or content-addressed gateways can all expose the
  same layout. No root is trusted; the pinned Ed25519 authority and CID are."
  (:require [kekkai.desired-state :as desired]))

(def kind :kekkai/netmap)

(defn seal
  [{:keys [netmap subject epoch previous-cid]} identity]
  (desired/seal {:kind kind
                 :subject subject
                 :epoch epoch
                 :previous-cid previous-cid
                 :payload netmap}
                identity))

(defn publish!
  [{:keys [roots min-copies netmap subject epoch previous-cid]} identity]
  (let [envelope (seal {:netmap netmap :subject subject :epoch epoch
                        :previous-cid previous-cid}
                       identity)]
    (desired/publish! roots (or min-copies (count roots)) envelope
                      (desired/authority-spki-b64 identity))))

(defn pull
  [{:keys [roots subject authority-spki-b64 min-epoch previous-cid]}]
  (desired/pull roots subject authority-spki-b64
                (cond-> {:kind kind}
                  min-epoch (assoc :min-epoch min-epoch)
                  previous-cid (assoc :previous-cid previous-cid))))
