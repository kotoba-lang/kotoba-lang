(ns mcp.model
  "MCP (Model Context Protocol) server manifest as EDN — a plain-data representation
  of an MCP server's tools, resources, and prompts, with a threading-friendly builder.
  No I/O, no third-party deps — portable .cljc (JVM, ClojureScript, SCI).

  A manifest is a map keyed by namespaced `:mcp/*` keys. Tools and resources are kept
  in name/uri-keyed maps for O(1) lookup; ordering is by name for deterministic output:

    {:mcp/server    {:mcp/name \"weather\" :mcp/version \"1.0.0\"}
     :mcp/tools     {\"get_forecast\" {:mcp/name \"get_forecast\"
                                      :mcp/description \"Get a weather forecast\"
                                      :mcp/input-schema {:type \"object\"
                                                         :properties {\"city\" {:type \"string\"}}
                                                         :required [\"city\"]}}}
     :mcp/resources {\"file:///readme\" {:mcp/uri \"file:///readme\"
                                         :mcp/name \"readme\"
                                         :mcp/mime \"text/markdown\"}}
     :mcp/prompts   {\"greet\" {:mcp/name \"greet\"
                               :mcp/arguments [{:mcp/name \"who\" :mcp/required true}]}}}")

;; --- builder (threadable) ---

(defn server
  "A fresh, empty MCP server manifest with the given server name and version."
  [name version]
  {:mcp/server    {:mcp/name name :mcp/version version}
   :mcp/tools     {}
   :mcp/resources {}
   :mcp/prompts   {}})

(defn add-tool
  "Add a tool to the manifest. opts: {:description :input-schema}.
  The :input-schema is JSON-Schema-as-EDN (keyword keys, e.g. {:type \"object\"
  :properties {\"city\" {:type \"string\"}} :required [\"city\"]})."
  ([manifest name] (add-tool manifest name nil))
  ([manifest name opts]
   (assoc-in manifest [:mcp/tools name]
             (cond-> {:mcp/name name}
               (:description opts)  (assoc :mcp/description (:description opts))
               (:input-schema opts) (assoc :mcp/input-schema (:input-schema opts))))))

(defn add-resource
  "Add a resource to the manifest. uri is the primary key. opts: {:name :mime}."
  ([manifest uri] (add-resource manifest uri nil))
  ([manifest uri opts]
   (assoc-in manifest [:mcp/resources uri]
             (cond-> {:mcp/uri uri}
               (:name opts) (assoc :mcp/name (:name opts))
               (:mime opts) (assoc :mcp/mime (:mime opts))))))

(defn add-prompt
  "Add a prompt to the manifest. opts: {:arguments} where :arguments is a vector of
  {:mcp/name \"...\" :mcp/required true|false} argument descriptors."
  ([manifest name] (add-prompt manifest name nil))
  ([manifest name opts]
   (assoc-in manifest [:mcp/prompts name]
             (cond-> {:mcp/name name}
               (:arguments opts) (assoc :mcp/arguments (:arguments opts))))))

;; --- queries ---

(defn tool     [manifest name] (get-in manifest [:mcp/tools name]))
(defn resource [manifest uri]  (get-in manifest [:mcp/resources uri]))
(defn prompt   [manifest name] (get-in manifest [:mcp/prompts name]))

(defn tools
  "All tool descriptors, sorted by name (deterministic)."
  [manifest]
  (sort-by :mcp/name (vals (:mcp/tools manifest))))

(defn resources
  "All resource descriptors, sorted by uri (deterministic)."
  [manifest]
  (sort-by :mcp/uri (vals (:mcp/resources manifest))))

(defn prompts
  "All prompt descriptors, sorted by name (deterministic)."
  [manifest]
  (sort-by :mcp/name (vals (:mcp/prompts manifest))))
