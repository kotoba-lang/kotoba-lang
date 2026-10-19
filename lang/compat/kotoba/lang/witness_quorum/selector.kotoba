(ns kotoba.lang.witness-quorum.selector
  "Deterministic Holochain-iso validator selection: given a record's CID and
  the Murakumo fleet's cell catalog, return the `quorum-size` cells that will
  attest to the record. Same CID -> same cells, every time.

  CLJ port of this repo's original @etzhayyim/witness-quorum TypeScript
  package (src/witness-selector.ts). JVM-only (plain .clj, not .cljc) --
  see the repo README for why this package skips CLJS entirely.

  Per kotoba-datomic SPEC S5 + ADR-2605231400."
  (:import [java.security MessageDigest]))

(defn fleet-cell
  "Build a fleet cell map from a node hostname/name + cell-id. The composite
  `:key` (`node::cell-id`) is the selection-universe identity -- a cell
  moving between nodes counts as a different witness slot (intentional: the
  node is part of the trust assertion).

  Optional `public-key` (raw 32-byte Ed25519 public key) is the cell's
  published key, used by `quorum/quorum-state` to verify each attestation's
  `:signature` before it counts toward quorum. A witness with no declared
  `:public-key` is a documented gap, not a silent one: `quorum-state` cannot
  verify what it has no key material for, so its attestations are accepted
  unverified (matching this library's current no-PKI-registry deployment
  reality) -- but any witness that DOES carry a public-key has its
  attestations' signatures checked and forged/invalid ones rejected."
  ([node cell-id] (fleet-cell node cell-id nil))
  ([node cell-id public-key]
   (cond-> {:node node :cell-id cell-id :key (str node "::" cell-id)}
     public-key (assoc :public-key public-key))))

(defn- sha256-hex ^String [^String s]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) digest))))

(defn quorum-group
  "Quorum group identifier for a record -- sha256(record-cid)[:16] hex.
  Matches com.etzhayyim.kotoba-datomic.attestation#quorumGroup. Enables O(1)
  quorum lookup by primary key once attestations land."
  [record-cid]
  (subs (sha256-hex record-cid) 0 16))

(defn select-witnesses
  "Select `quorum-size` (default 5) witnesses for a record from `fleet`.

  Algorithm:
    1. Sort the fleet by composite key (stable selection universe).
    2. Compute sha256(record-cid) as a 256-bit integer.
    3. Step through the sorted fleet starting at offset (hash mod fleet-len)
       and take the next `quorum-size` distinct cells (wrapping).

  Properties: deterministic, stable under fleet reordering (sorted first),
  unbiased (sha256 -> uniform offset), re-validatable from the record CID
  alone. Throws if the fleet has fewer cells than `quorum-size`."
  ([record-cid fleet] (select-witnesses record-cid fleet 5))
  ([record-cid fleet quorum-size]
   (let [n (count fleet)]
     (when (< n quorum-size)
       (throw (ex-info (str "witness-quorum: fleet has " n " cells, cannot select quorum of " quorum-size)
                        {:fleet-size n :quorum-size quorum-size})))
     (when (< quorum-size 1)
       (throw (ex-info (str "witness-quorum: quorum-size must be >=1, got " quorum-size)
                        {:quorum-size quorum-size})))
     (let [sorted (vec (sort-by :key fleet))
           digest-int (BigInteger. (sha256-hex record-cid) 16)
           offset (.intValue (.mod digest-int (BigInteger/valueOf n)))]
       (loop [i 0 selected [] seen #{}]
         (cond
           (>= (count selected) quorum-size) selected

           (>= i (* n 2))
           (throw (ex-info "witness-quorum: witness selection loop did not converge -- fleet may have duplicate keys"
                            {:fleet-size n}))

           :else
           (let [idx (mod (+ offset i) n)
                 cell (nth sorted idx)]
             (if (contains? seen (:key cell))
               (recur (inc i) selected seen)
               (recur (inc i) (conj selected cell) (conj seen (:key cell)))))))))))

(defn flatten-fleet
  "Flatten the fleet.toml shape (a seq of {:name/:hostname \"...\" :cells [...]})
  into a vector of fleet-cell maps. Caller is responsible for parsing the
  TOML; this is the shape transformer."
  [nodes]
  (vec (mapcat (fn [{:keys [name hostname cells]}]
                 (let [node-key (or hostname name)]
                   (when-not node-key
                     (throw (ex-info "witness-quorum: fleet node missing both 'name' and 'hostname'" {})))
                   (map #(fleet-cell node-key %) cells)))
               nodes)))
