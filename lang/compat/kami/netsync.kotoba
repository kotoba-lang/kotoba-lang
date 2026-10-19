(ns kami.netsync
  "hiccup for replication — what crosses the wire, described as EDN. A schema names the
   synced components, their fields, authority, and interpolation. The wire payload is
   derived from the schema, not hand-coded; store the schema as datoms, fork it, retune
   bandwidth without touching transport code.

     {:components {:transform {:fields [:x :y :z] :authority :server :interp :lerp}
                   :health    {:fields [:hp]      :authority :server :interp :snap}}}

   Pure + cross-platform (.cljc): `snapshot` extracts the wire payload; `apply-snapshot`
   merges a received one; `interp` blends toward a target per each component's :interp.
   The same EDN drives any transport (WebRTC/WebSocket here-or-later, native sockets too).")

(def default-schema
  {:components {:transform {:fields [:x :y :z]  :authority :server :interp :lerp}
                :facing    {:fields [:rx :ry]   :authority :server :interp :lerp}
                :health    {:fields [:hp]       :authority :server :interp :snap}}})

(defn synced-fields
  "All fields the schema replicates, across components."
  [schema]
  (vec (mapcat (comp :fields val) (:components schema))))

(defn snapshot
  "The wire payload for an entity: only the schema's synced fields."
  [schema entity]
  (select-keys entity (synced-fields schema)))

(defn apply-snapshot
  "Merge a received snapshot into an entity (schema fields only)."
  [schema entity snap]
  (merge entity (select-keys snap (synced-fields schema))))

(defn interp
  "Blend an entity toward `target` by t∈[0,1] using each component's :interp — :lerp
   numerically tweens fields, anything else snaps. Pure."
  [schema entity target t]
  (reduce-kv
    (fn [e _ {:keys [fields interp]}]
      (reduce (fn [e f]
                (let [a (get e f) b (get target f)]
                  (cond
                    (not (contains? target f)) e
                    (and (= interp :lerp) (number? a) (number? b)) (assoc e f (+ a (* (- b a) t)))
                    :else (assoc e f b))))
              e fields))
    entity (:components schema)))
