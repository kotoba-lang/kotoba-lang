(ns mcp.validate
  "Structural validation of an MCP manifest. Pure: returns a vector of problem maps
  `{:mcp/severity :error|:warn :mcp/code … :mcp/id … :mcp/msg …}` so a caller decides
  how to surface them. `valid?` is true iff there are no :error-level problems
  (warnings are advisory)."
  (:require [mcp.model :as m]))

(defn- problem [severity code id msg]
  {:mcp/severity severity :mcp/code code :mcp/id id :mcp/msg msg})

(defn- validate-schema
  "Check that a JSON-Schema-as-EDN input-schema is well-formed.
  Returns a seq of problem maps (may be empty)."
  [tool-name schema]
  (let [ps (transient [])]
    (when-not (get schema :type)
      (conj! ps (problem :error :schema/no-type tool-name
                         (str "tool " tool-name " input-schema has no :type"))))
    (when (= "object" (get schema :type))
      (let [props (get schema :properties)]
        (when-not (map? props)
          (conj! ps (problem :error :schema/bad-properties tool-name
                             (str "tool " tool-name " input-schema :properties is not a map"))))
        (when (map? props)
          (let [prop-keys (set (keys props))]
            (doseq [r (get schema :required [])]
              (when-not (contains? prop-keys r)
                (conj! ps (problem :error :schema/missing-required tool-name
                                   (str "tool " tool-name " :required names \"" r
                                        "\" which is not in :properties")))))))))
    (persistent! ps)))

(defn problems
  "Return a vector of structural problems with `manifest`."
  [manifest]
  (let [ps (transient [])]
    ;; tools: key/name agreement and schema validity
    (doseq [[k t] (:mcp/tools manifest)]
      (when (not= k (:mcp/name t))
        (conj! ps (problem :error :tool/name-mismatch k
                           (str "tool keyed " k " carries :mcp/name " (:mcp/name t)))))
      (when-let [schema (:mcp/input-schema t)]
        (doseq [p (validate-schema k schema)]
          (conj! ps p))))
    ;; resources: key/uri agreement and :mcp/uri present
    (doseq [[k r] (:mcp/resources manifest)]
      (when-not (:mcp/uri r)
        (conj! ps (problem :error :resource/no-uri k
                           (str "resource keyed " k " has no :mcp/uri"))))
      (when (and (:mcp/uri r) (not= k (:mcp/uri r)))
        (conj! ps (problem :error :resource/uri-mismatch k
                           (str "resource keyed " k " carries :mcp/uri " (:mcp/uri r))))))
    ;; prompts: key/name agreement
    (doseq [[k pr] (:mcp/prompts manifest)]
      (when (not= k (:mcp/name pr))
        (conj! ps (problem :error :prompt/name-mismatch k
                           (str "prompt keyed " k " carries :mcp/name " (:mcp/name pr))))))
    ;; warn if manifest has no tools, resources, or prompts
    (when (empty? (:mcp/tools manifest))
      (conj! ps (problem :warn :server/no-tools
                         (get-in manifest [:mcp/server :mcp/name])
                         "server manifest has no tools")))
    (persistent! ps)))

(defn errors [manifest] (filterv #(= :error (:mcp/severity %)) (problems manifest)))

(defn valid?
  "True iff `manifest` has no :error-level structural problems."
  [manifest]
  (empty? (errors manifest)))
