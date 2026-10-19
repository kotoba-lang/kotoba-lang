(ns datom.source.adjacency
  "Brick 4: index-free adjacency.

  A covering datom index answers `[s p o]` in O(log n + result). It does not
  answer 'from these nodes, who is one hop away on p?' without re-seeking
  each start. Neo4j's advantage is that hop is pointer-chase, O(degree).
  ADR-2608011400 named this brick and left it unbuilt.

  This namespace materialises outgoing and incoming adjacency maps from
  quads. It is a **projection**: delete it and rebuild from the quads
  (the same delete-and-rebuild test as lake / view). It is also an
  `IPatternSource`, so it is interchangeable with `of-quads` / cursor /
  view on the conformance corpus.

  Temporary (backend, not language): maps/sets live in `.cljc`. The hop
  decision (union of neighbour sets) is a candidate for `kotoba/pure`
  once native collections qualify; until then this file is the oracle.
  Removing this comment after a `.kotoba` port without a parity test
  would be a mirror, which is forbidden."
  (:require [datom.source :as src]))

(defprotocol IAdjacency
  "One hop. `e` is an entity, `p` is a ground predicate.
  `-out` returns the objects, `-in` the subjects. Empty set, never nil."
  (-out [this e p])
  (-in [this e p]))

(defn out [g e p] (-out g e p))
(defn in [g e p] (-in g e p))

(defn- index-quads [quads]
  (reduce (fn [acc {:keys [s p o] :as q}]
            (-> acc
                (update-in [:out s p] (fnil conj #{}) o)
                (update-in [:in o p] (fnil conj #{}) s)
                (update-in [:by-p p] (fnil conj #{}) q)))
          {:out {} :in {} :by-p {}}
          quads))

(defrecord ^:no-doc Adjacency [out in by-p lookups]
  IAdjacency
  (-out [_ e p]
    (when lookups (swap! lookups update :out-lookups (fnil inc 0)))
    (get-in out [e p] #{}))
  (-in [_ e p]
    (when lookups (swap! lookups update :in-lookups (fnil inc 0)))
    (get-in in [e p] #{}))
  src/IPatternSource
  (-scan [_ [s p o]]
    (when lookups (swap! lookups update :scans (fnil inc 0)))
    (cond
      (and s p o) (if (contains? (get-in out [s p]) o)
                    #{{:s s :p p :o o}}
                    #{})
      (and s p) (into #{} (map (fn [o'] {:s s :p p :o o'}))
                      (get-in out [s p] #{}))
      (and p o) (into #{} (map (fn [s'] {:s s' :p p :o o}))
                      (get-in in [o p] #{}))
      (and s o) (into #{} (for [[p' os] (get out s {})
                                o' os
                                :when (= o' o)]
                            {:s s :p p' :o o}))
      s (into #{} (for [[p' os] (get out s {})
                        o' os]
                    {:s s :p p' :o o'}))
      o (into #{} (for [[p' ss] (get in o {})
                        s' ss]
                    {:s s' :p p' :o o}))
      p (get by-p p #{})
      :else (into #{} (mapcat identity) (vals by-p))))
  src/IRangeSource
  (-scan-range [this attr lo hi opts]
    (into #{} (filter #(src/in-range? (:o %) lo hi opts))
          (src/scan this [nil attr nil]))))

(defn of-quads
  "Build adjacency from `quads`. Pass a lookups atom to record hop cost
  as `{:out-lookups n :in-lookups n :scans n}` — wall-clock is not the
  comparison (see `datom.source/counting`)."
  ([quads] (of-quads quads nil))
  ([quads lookups]
   (let [{:keys [out in by-p]} (index-quads quads)]
     (->Adjacency out in by-p lookups))))

(defn hop
  "Nodes reachable in one step from `nodes` along `p`. Cost is the
  number of `-out` calls (= |nodes|), not |quads|."
  [g nodes p]
  (into #{} (mapcat #(out g % p)) nodes))

(defn hop-n
  "Apply `hop` `n` times. `n=0` is the start set."
  [g nodes p n]
  (nth (iterate #(hop g % p) (set nodes)) n))

(defn stats
  "The lookups atom, or zeros if the graph was built without one."
  [^Adjacency g]
  (or (some-> (:lookups g) deref)
      {:out-lookups 0 :in-lookups 0 :scans 0}))
