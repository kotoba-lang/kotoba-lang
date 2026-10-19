(ns mcp.json
  "MCP manifest ⇄ JSON-ready Clojure maps, with zero third-party deps.

  The host is responsible for JSON text parsing (e.g. clojure.data.json on JVM,
  js/JSON.parse in ClojureScript). This namespace only converts between:

    * *parsed-JSON maps* — Clojure maps/vectors with **string keys**, exactly as a
      JSON parser produces.
    * *EDN model* — the namespaced-keyword model in `mcp.model`.

  This mirrors the two-layer design of `bpmn.xml/from-elements`: the host's JSON
  parser produces the neutral form, and `from-data` / `to-data` handle the
  model conversion.

  JSON manifest shape (all string keys):

    {\"server\"    {\"name\" \"weather\" \"version\" \"1.0.0\"}
     \"tools\"     [{\"name\" \"get_forecast\"
                   \"description\" \"Get a weather forecast\"
                   \"inputSchema\" {\"type\" \"object\"
                                   \"properties\" {\"city\" {\"type\" \"string\"}}
                                   \"required\" [\"city\"]}}]
     \"resources\" [{\"uri\" \"file:///readme\" \"name\" \"readme\"
                   \"mimeType\" \"text/markdown\"}]
     \"prompts\"   [{\"name\" \"greet\"
                   \"arguments\" [{\"name\" \"who\" \"required\" true}]}]}")

;; --- internal schema conversion (JSON-Schema: string keys ↔ keyword keys) ---

(defn- keywordize-map
  "Shallow: convert string keys to keywords, leave values unchanged."
  [m]
  (into {} (map (fn [[k v]] [(if (string? k) (keyword k) k) v]) m)))

(defn- schema-from-data
  "JSON-Schema map (string keys) → JSON-Schema-as-EDN (keyword keys at each level)."
  [s]
  (when s
    (let [ks (keywordize-map s)]
      (cond-> ks
        (:properties ks)
        (update :properties
                (fn [props]
                  (into {} (map (fn [[k v]] [k (keywordize-map v)]) props))))))))

(defn- stringify-map
  "Shallow: convert keyword keys to their names (strings), leave values unchanged."
  [m]
  (into {} (map (fn [[k v]] [(if (keyword? k) (name k) k) v]) m)))

(defn- schema-to-data
  "JSON-Schema-as-EDN (keyword keys) → JSON-Schema map (string keys)."
  [s]
  (when s
    (let [ss (stringify-map s)]
      (cond-> ss
        (get ss "properties")
        (update "properties"
                (fn [props]
                  (into {} (map (fn [[k v]] [k (stringify-map v)]) props))))))))

;; --- tool / resource / prompt helpers ---

(defn- tool-from-data [t]
  (cond-> {:mcp/name (get t "name")}
    (get t "description") (assoc :mcp/description (get t "description"))
    (get t "inputSchema") (assoc :mcp/input-schema (schema-from-data (get t "inputSchema")))))

(defn- resource-from-data [r]
  (cond-> {:mcp/uri (get r "uri")}
    (get r "name")     (assoc :mcp/name (get r "name"))
    (get r "mimeType") (assoc :mcp/mime (get r "mimeType"))))

(defn- argument-from-data [a]
  (cond-> {:mcp/name (get a "name")}
    (contains? a "required") (assoc :mcp/required (boolean (get a "required")))))

(defn- prompt-from-data [pr]
  (cond-> {:mcp/name (get pr "name")}
    (get pr "arguments")
    (assoc :mcp/arguments (mapv argument-from-data (get pr "arguments")))))

(defn- tool-to-data [t]
  (cond-> {"name" (:mcp/name t)}
    (:mcp/description t)  (assoc "description" (:mcp/description t))
    (:mcp/input-schema t) (assoc "inputSchema" (schema-to-data (:mcp/input-schema t)))))

(defn- resource-to-data [r]
  (cond-> {"uri" (:mcp/uri r)}
    (:mcp/name r) (assoc "name" (:mcp/name r))
    (:mcp/mime r) (assoc "mimeType" (:mcp/mime r))))

(defn- argument-to-data [a]
  (cond-> {"name" (:mcp/name a)}
    (contains? a :mcp/required) (assoc "required" (boolean (:mcp/required a)))))

(defn- prompt-to-data [pr]
  (cond-> {"name" (:mcp/name pr)}
    (:mcp/arguments pr)
    (assoc "arguments" (mapv argument-to-data (:mcp/arguments pr)))))

;; --- public API ---

(defn from-data
  "Parsed-JSON manifest map (string keys) → EDN model.
  Accepts a top-level map with keys \"server\", \"tools\", \"resources\", \"prompts\";
  all are optional (missing collections default to empty)."
  [data]
  (let [srv        (get data "server" {})
        tools-v    (get data "tools"     [])
        res-v      (get data "resources" [])
        prompts-v  (get data "prompts"   [])]
    {:mcp/server    {:mcp/name    (get srv "name")
                     :mcp/version (get srv "version")}
     :mcp/tools     (into {} (map (fn [t] [(get t "name") (tool-from-data t)]) tools-v))
     :mcp/resources (into {} (map (fn [r] [(get r "uri")  (resource-from-data r)]) res-v))
     :mcp/prompts   (into {} (map (fn [p] [(get p "name") (prompt-from-data p)]) prompts-v))}))

(defn to-data
  "EDN model → JSON-ready manifest map (string keys).
  Tools, resources, and prompts are sorted (by name/uri) for deterministic output."
  [manifest]
  {"server"    {"name"    (get-in manifest [:mcp/server :mcp/name])
                "version" (get-in manifest [:mcp/server :mcp/version])}
   "tools"     (vec (map tool-to-data
                         (sort-by :mcp/name (vals (:mcp/tools manifest)))))
   "resources" (vec (map resource-to-data
                         (sort-by :mcp/uri (vals (:mcp/resources manifest)))))
   "prompts"   (vec (map prompt-to-data
                         (sort-by :mcp/name (vals (:mcp/prompts manifest)))))})
