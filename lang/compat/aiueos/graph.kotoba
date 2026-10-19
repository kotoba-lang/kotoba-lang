(ns aiueos.graph
  "The system graph and the derived capability graph, ported from the retired
  `aiueos/src/graph.rs` Rust module to CLJC per ADR-2607022200.

  A system graph is a vector of component manifests (pure EDN maps, each
  validated by `aiueos.contract/validate-manifest`). From it we derive a
  capability graph mapping each capability to the components exporting it,
  which `aiueos.policy` uses to resolve imports; and a topological boot order
  / dependency depth, which the (future) scheduler (ADR-0006) uses to order
  and parallelize component startup.

  File loading (`system.aiueos.edn` -> manifests) stays a host adapter
  concern; this namespace only reasons over already-loaded manifest data."
  (:require [clojure.set :as set]))

(defn- component-id [m]
  (:aiueos/component m))

(defn- device-key
  "A fully-specified `bus:vendor:device` triple, or nil if any part is
  missing (a partial binding is too ambiguous to claim exclusive ownership)."
  [m]
  (when-let [d (:aiueos/device m)]
    (let [{:keys [bus vendor device]} d]
      (when (and bus vendor device)
        [bus vendor device]))))

(defn check-unique-ids
  "Reject a system whose components don't have unique ids. Duplicates would
  silently collide: both would be credited as providers of the same exports,
  and the boot-order index would keep only one — a footgun worth a hard
  error. Returns nil on success, or an error map."
  [components]
  (loop [seen #{} [m & more] components]
    (if (nil? m)
      nil
      (let [id (component-id m)]
        (if (contains? seen id)
          {:aiueos/error :duplicate-component-id
           :aiueos/component id
           :aiueos/message (str "duplicate component id `" id "` in system")}
          (recur (conj seen id) more))))))

(defn check-unique-devices
  "Reject a system where two components bind the same physical device. A
  device (a fully-specified bus:vendor:device triple) can have exactly one
  driver. Returns nil on success, or an error map."
  [components]
  (loop [seen {} [m & more] components]
    (if (nil? m)
      nil
      (let [k (device-key m)]
        (if-not k
          (recur seen more)
          (if-let [prev (get seen k)]
            {:aiueos/error :duplicate-device-binding
             :aiueos/device k
             :aiueos/message (str "device " k " is bound by both `" prev
                                   "` and `" (component-id m) "`")}
            (recur (assoc seen k (component-id m)) more)))))))

(defn build
  "capability -> exporting component ids, in declaration order."
  [components]
  {:aiueos.graph/providers
   (reduce (fn [acc m]
             (reduce (fn [acc2 export]
                       (update acc2 export (fnil conj []) (component-id m)))
                     acc
                     (:aiueos/exports m)))
           {}
           components)})

(defn providers
  "Components exporting `cap` (empty vector if none)."
  [graph cap]
  (get-in graph [:aiueos.graph/providers cap] []))

(defn all-providers [graph]
  (:aiueos.graph/providers graph))

(defn- providers-of
  "For each component (by index), the indices of components in `components`
  that export something it imports (excluding self)."
  [components graph]
  (let [idx (into {} (map-indexed (fn [i m] [(component-id m) i])) components)]
    (mapv (fn [m]
            (into []
                  (comp (mapcat #(providers graph %))
                        (keep idx)
                        (remove #(= % (get idx (component-id m)))))
                  (:aiueos/imports m)))
          components)))

(defn boot-order
  "Topological launch order: a capability provider boots before any consumer
  that imports it. Returns `{:aiueos.graph/order [indices...]}` in boot
  order, or `{:aiueos.graph/cycle [component-ids...]}` for the components
  caught in a dependency cycle."
  [components]
  (let [graph (build components)
        n (count components)
        idx (into {} (map-indexed (fn [i m] [(component-id m) i])) components)
        edges (reduce (fn [acc m]
                        (let [ci (get idx (component-id m))]
                          (reduce (fn [acc2 imp]
                                    (reduce (fn [acc3 prov]
                                              (if-let [pi (get idx prov)]
                                                (if (= pi ci) acc3 (conj acc3 [pi ci]))
                                                acc3))
                                            acc2
                                            (providers graph imp)))
                                  acc
                                  (:aiueos/imports m))))
                      #{}
                      components)
        adj (reduce (fn [acc [p c]] (update acc p (fnil conj []) c)) (vec (repeat n [])) edges)
        indeg (reduce (fn [acc [_ c]] (update acc c inc)) (vec (repeat n 0)) edges)]
    (loop [ready (into (sorted-set) (filter #(zero? (nth indeg %))) (range n))
           indeg indeg
           order []]
      (if (empty? ready)
        (if (= (count order) n)
          {:aiueos.graph/order order}
          {:aiueos.graph/cycle (mapv (comp component-id (partial nth components))
                                      (remove (set order) (range n)))})
        (let [u (first ready)
              ready (disj ready u)
              [ready indeg]
              (reduce (fn [[ready indeg] v]
                        (let [indeg (update indeg v dec)]
                          [(if (zero? (nth indeg v)) (conj ready v) ready) indeg]))
                      [ready indeg]
                      (nth adj u))]
          (recur ready indeg (conj order u)))))))

(defn depths
  "Dependency depth of each component: 0 for a component nothing it imports
  is provided by another component (a source), else 1 + max(provider depth).
  Components at the same depth are in no provider->consumer relationship, so
  the scheduler may reorder them by priority without breaking dataflow
  (ADR-0006). On a cycle, falls back to index order. Returns a vector of
  depths parallel to `components`."
  [components]
  (let [n (count components)
        graph (build components)
        provs (providers-of components graph)
        order (let [r (boot-order components)]
                (or (:aiueos.graph/order r) (vec (range n))))]
    (reduce (fn [depth i]
              (let [d (if (empty? (nth provs i))
                        0
                        (inc (apply max (map depth (nth provs i)))))]
                (assoc depth i d)))
            (vec (repeat n 0))
            order)))

(defn priority-boot-order
  "Like `boot-order`, but within each dependency-depth level (components
  with no provider->consumer relationship among them -- `depths`'s own
  docstring: \"the scheduler may reorder them by priority without breaking
  dataflow\", ADR-0006), orders by PRIORITIES ascending (lower =
  more urgent) instead of the arbitrary within-depth order the Kahn's-
  algorithm traversal in `boot-order` happens to produce.

  PRIORITIES is a vector of ints parallel to COMPONENTS (e.g.
  `(mapv #(get-in % [:aiueos/schedule :aiueos.manifest/priority])
  normalized-manifests)`). This stays a valid topological order: sorting
  primarily by depth keeps every depth-group fully ordered before the
  next (providers strictly precede consumers, since a provider's depth is
  always < its consumer's), and priority only reorders WITHIN a
  depth-group, where `depths` already guarantees no dependency edges
  exist to violate.

  Returns the same shape as `boot-order` (`{:aiueos.graph/order [...]}` or
  `{:aiueos.graph/cycle [...]}`, unchanged on a cycle since there's no
  valid order to reorder)."
  [components priorities]
  (let [order-result (boot-order components)]
    (if-let [order (:aiueos.graph/order order-result)]
      (let [ds (depths components)]
        {:aiueos.graph/order (vec (sort-by (fn [i] [(nth ds i) (nth priorities i)]) order))})
      order-result)))
