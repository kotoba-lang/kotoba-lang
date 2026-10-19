(ns connector.registry
  "Several connectors, seen as one surface.

  This is the piece the workspace did not have. Tools reached an agent by being
  written into it (`agent-control/available-tools` is a `cond->` over four
  hard-coded vectors), so adding an integration meant editing the application.
  A registry makes the set a value: build it from whichever connector
  repositories a deployment depends on, and the agent's tool list follows.

  Uniqueness is enforced rather than resolved. Two connectors offering a tool
  of the same name is not something to silently pick a winner for — the tool
  names are what a model sees, and a name that means different things in
  different deployments is worse than a load-time failure."
  (:require [clojure.string :as str]
            [connector.model :as m]
            [connector.provider :as p]))

(defn- problem [code id msg]
  {:connector/severity :error :connector/code code :connector/id id :connector/msg msg})

(defn problems
  "Conflicts across `providers`, as a vector. Empty is good."
  [providers]
  (let [ps (transient [])
        ids (map p/id providers)]
    (doseq [[id n] (frequencies ids) :when (> n 1)]
      (conj! ps (problem :registry/duplicate-connector id
                         (str "connector id " id " is registered " n " times"))))
    (let [owners (reduce (fn [acc pr]
                           (reduce (fn [a t] (update a t (fnil conj []) (p/id pr)))
                                   acc (p/tool-names pr)))
                         {} providers)]
      (doseq [[tool os] owners :when (> (count os) 1)]
        (conj! ps (problem :registry/duplicate-tool tool
                           (str "tool " tool " is offered by " (str/join ", " (sort os)))))))
    (persistent! ps)))

(defn registry
  "Build a registry from providers. Throws on conflicts."
  [providers]
  (let [providers (vec providers)
        ps (problems providers)]
    (when (seq ps)
      (throw (ex-info "connector registry has conflicts"
                      {:type :connector.registry/conflict :connector/problems ps})))
    {:connector.registry/providers providers
     :connector.registry/by-id (into {} (map (juxt p/id identity)) providers)
     :connector.registry/by-tool
     (into {} (for [pr providers t (p/tool-names pr)] [t pr]))}))

(def empty-registry (registry []))

(defn providers [reg] (:connector.registry/providers reg))
(defn provider [reg id] (get-in reg [:connector.registry/by-id id]))
(defn owner
  "The provider offering `tool-name`, or nil."
  [reg tool-name]
  (get-in reg [:connector.registry/by-tool tool-name]))

(defn descriptors
  "Every descriptor, sorted by id (deterministic)."
  [reg]
  (->> (providers reg) (map p/descriptor) (sort-by :connector/id) vec))

(defn tool-names [reg]
  (->> reg :connector.registry/by-tool keys sort vec))

(defn select
  "The registry narrowed to `ids`, and — when `enabled-tools` is given — to
  those tool names.

  This is how a host expresses what an operator turned on. Narrowing the
  registry rather than filtering at call time means `connector.consent` sees
  the same set the agent does, so the grant asked for and the tools offered
  cannot drift apart."
  ([reg ids] (select reg ids nil))
  ([reg ids enabled-tools]
   (let [wanted (set ids)
         ;; nil means 'no tool filter'; an EMPTY collection means 'no tools'.
         ;; Collapsing the two with `seq` made disabling everything enable
         ;; everything — the exact silent-widening this plane exists to stop.
         ;; Found by a host test asserting that an explicitly empty
         ;; configuration yields nothing.
         keep-tool? (if (nil? enabled-tools) (constantly true) (set enabled-tools))]
     (registry
      (for [pr (providers reg)
            :when (wanted (p/id pr))
            :let [d (p/descriptor pr)
                  kept (into {} (filter (fn [[k _]] (keep-tool? k)))
                             (:connector/tools d))]
            :when (seq kept)]
        (assoc pr :connector/descriptor (assoc d :connector/tools kept)))))))

(defn catalog
  "A plain-data listing for a connector directory: one row per connector, with
  the tool names and the scopes a full grant would need.

  `:connector/scopes` here is the everything-enabled figure and is the right
  thing to SHOW. It is the wrong thing to REQUEST — see `connector.consent`."
  [reg]
  (mapv (fn [d]
          (cond-> {:connector/id (:connector/id d)
                   :connector/name (:connector/name d)
                   :connector/auth-kind (get-in d [:connector/auth :connector.auth/kind])
                   :connector/tools (m/tool-names d)
                   :connector/scopes (m/scopes d)}
            (:connector/summary d) (assoc :connector/summary (:connector/summary d))
            (:connector/origin-domain d)
            (assoc :connector/origin-domain (:connector/origin-domain d))))
        (descriptors reg)))
