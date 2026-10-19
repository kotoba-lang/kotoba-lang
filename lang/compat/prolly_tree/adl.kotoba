(ns prolly-tree.adl
  "IPLD Advanced Data Layout view of a Prolly Tree substrate.

  The substrate remains CID-addressed leaf/internal blocks. `ProllyMap`
  synthesizes the ordinary IPLD Map interface, so schema and selector code can
  consume it without knowing chunk boundaries or child layouts."
  (:require [ipld.data-model :as dm]
            [prolly-tree.core :as tree]))

(defn- string-segment! [segment]
  (when-not (string? segment)
    (throw (ex-info "prolly-tree ADL map keys must be strings"
                    {:type :ipld/invalid-path-segment :segment segment})))
  segment)

(defn- exact-entry [get-fn root-cid key]
  (some (fn [[entry-key _ :as entry]]
          (when (= entry-key key) entry))
        (tree/scan-prefix get-fn root-cid key)))

(defrecord ProllyMap [get-fn root-cid]
  dm/INode
  (-node-kind [_] :map)
  (-node-lookup [_ segment]
    (tree/lookup get-fn root-cid (string-segment! segment)))
  (-node-contains? [_ segment]
    (boolean (exact-entry get-fn root-cid (string-segment! segment))))
  (-node-entries [_]
    (let [entries (or (tree/scan-prefix get-fn root-cid "") [])]
      (doseq [[key value] entries]
        (string-segment! key)
        (dm/validate! value [key]))
      entries))
  (-node-length [this]
    (count (dm/entries this))))

(defn view
  "Create an IPLD Map view over `root-cid`. No blocks are read until the view
  is traversed. A nil root is the empty map."
  [get-fn root-cid]
  (->ProllyMap get-fn root-cid))

(defn substrate
  "Return the physical root link target represented by this ADL view."
  [prolly-map]
  {:root-cid (:root-cid prolly-map)})
