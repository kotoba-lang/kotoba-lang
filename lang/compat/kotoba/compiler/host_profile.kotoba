(ns kotoba.compiler.host-profile
  "Validated EDN host descriptors and generated Cloudflare workerd adapters.

  Applications receive a closed capability object, never the ambient Worker
  env. Generated JavaScript is an artifact; the EDN profile is authoritative."
  (:require [json.data-json :as json]
            [clojure.string :as str]
            [kotoba.artifact.core :as artifact]))

(def schema :kotoba.host-profile/v1)

(def ^:private top-level-keys
  #{:format :target :service :compatibility-date :application :routes :crons
    :bindings :vars :secrets :http :object-store})
(def ^:private application-keys #{:module :factory})
(def ^:private route-keys #{:pattern :custom-domain?})
(def ^:private binding-keys #{:binding :bucket-name})
(def ^:private http-keys
  #{:allowed-origins :allowed-methods :ingress-methods
    :max-request-bytes :max-response-bytes :deadline-ms})

(def ^:private default-ingress-methods
  "Inbound methods admitted when `:ingress-methods` is omitted. Egress
  `:allowed-methods` stays a separate closed set (ADR 0103)."
  ["DELETE" "GET" "HEAD" "POST" "PUT"])
(def ^:private object-store-keys #{:bindings :max-object-bytes})
(def ^:private object-binding-keys #{:key-prefixes})

(defn- reject! [message data]
  (throw (ex-info message (assoc data :phase :host-profile))))

(defn- exact-keys! [label value allowed]
  (when-not (map? value)
    (reject! (str label " must be a map") {:field label}))
  (when-let [unknown (seq (remove allowed (keys value)))]
    (reject! (str label " contains unknown fields")
             {:field label :unknown (set unknown)}))
  value)

(defn- bounded-string! [label value maximum pattern]
  (when-not (and (string? value)
                 (<= 1 (count value) maximum)
                 (re-matches pattern value))
    (reject! (str label " is invalid") {:field label}))
  value)

(defn- bounded-int! [label value minimum maximum]
  (when-not (and (integer? value) (<= minimum value maximum))
    (reject! (str label " is outside the admitted range")
             {:field label :minimum minimum :maximum maximum}))
  value)

(defn- module-path! [value]
  (bounded-string! :application/module value 256
                   #"(?:\./|(?:\.\./){1,8})[A-Za-z0-9_./-]+\.mjs"))

(defn- identifier! [value]
  (bounded-string! :application/factory value 96
                   #"[A-Za-z_$][A-Za-z0-9_$]*"))

(defn- service-name! [value]
  (bounded-string! :service value 63 #"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))

(defn- binding-name! [value]
  (bounded-string! :binding value 64 #"[A-Z][A-Z0-9_]*"))

(defn- object-name! [label value]
  (bounded-string! label value 128 #"[A-Za-z0-9][A-Za-z0-9._-]*"))

(defn- date! [value]
  (bounded-string! :compatibility-date value 10 #"\d{4}-\d{2}-\d{2}"))

(defn- origin! [value]
  (let [uri (try (java.net.URI. value) (catch Exception _ nil))]
    (when-not (and uri
                   (= "https" (.getScheme uri))
                   (.getHost uri)
                   (nil? (.getRawQuery uri))
                   (nil? (.getRawFragment uri))
                   (or (str/blank? (.getPath uri)) (= "/" (.getPath uri))))
      (reject! "HTTP origin must be an HTTPS origin without path/query/fragment"
               {:field :http/allowed-origins}))
    value))

(defn- prefix! [value]
  (bounded-string! :object-store/key-prefix value 256
                   #"[A-Za-z0-9][A-Za-z0-9._~!$&'()*+,;=:@/-]*"))

(defn- normalize-application [value]
  (exact-keys! :application value application-keys)
  (let [module (module-path! (:module value))
        factory (identifier! (:factory value))]
    (sorted-map :factory factory :module module)))

(defn- normalize-routes [values]
  (when-not (and (vector? values) (<= (count values) 16))
    (reject! "routes must be a bounded vector" {:field :routes :limit 16}))
  (mapv
   (fn [route]
     (exact-keys! :route route route-keys)
     (let [pattern (bounded-string! :route/pattern (:pattern route) 253
                                    #"[A-Za-z0-9.-]+")
           custom? (:custom-domain? route)]
       (when-not (boolean? custom?)
         (reject! "route custom-domain? must be boolean"
                  {:field :route/custom-domain?}))
       (sorted-map :custom-domain? custom? :pattern pattern)))
   values))

(defn- normalize-crons [values]
  (when-not (and (vector? values) (<= (count values) 16))
    (reject! "crons must be a bounded vector" {:field :crons :limit 16}))
  (mapv #(bounded-string! :cron % 96 #"[A-Za-z0-9*/,? -]+") values))

(defn- normalize-bindings [values]
  (when-not (and (vector? values) (<= (count values) 32))
    (reject! "bindings must be a bounded vector" {:field :bindings :limit 32}))
  (let [bindings
        (mapv
         (fn [binding]
           (exact-keys! :binding binding binding-keys)
           (sorted-map :binding (binding-name! (:binding binding))
                       :bucket-name
                       (object-name! :binding/bucket-name
                                     (:bucket-name binding))))
         values)
        names (map :binding bindings)]
    (when-not (= (count names) (count (set names)))
      (reject! "binding names must be unique" {:field :bindings}))
    bindings))

(defn- normalize-vars [values]
  (when-not (and (map? values) (<= (count values) 64))
    (reject! "vars must be a bounded map" {:field :vars :limit 64}))
  (into (sorted-map)
        (map (fn [[key value]]
               [(binding-name! (name key))
                (bounded-string! :vars/value value 4096 #"(?s).*")]))
        values))

(defn- normalize-secrets [values]
  (when-not (and (or (set? values)
                     (and (vector? values) (every? string? values)))
                 (<= (count values) 32))
    (reject! "secrets must be a bounded set" {:field :secrets :limit 32}))
  (vec (sort (map (comp binding-name! name) values))))

(defn- normalize-http-methods [field methods]
  (when-not (or (and (set? methods)
                     (seq methods)
                     (every? #{:get :head :post :put :delete} methods))
                (and (vector? methods)
                     (seq methods)
                     (every? #{"GET" "HEAD" "POST" "PUT" "DELETE"} methods)))
    (reject! "HTTP methods are invalid" {:field field}))
  (vec (sort (map #(if (keyword? %)
                     (str/upper-case (name %))
                     %)
                   methods))))

(defn- normalize-http [value]
  (exact-keys! :http value http-keys)
  (let [origins (:allowed-origins value)
        methods (:allowed-methods value)
        ingress (or (:ingress-methods value) default-ingress-methods)]
    (when-not (and (vector? origins) (<= 1 (count origins) 64))
      (reject! "HTTP origins must be a non-empty bounded vector"
               {:field :http/allowed-origins :limit 64}))
    (sorted-map
     :allowed-methods (normalize-http-methods :http/allowed-methods methods)
     :allowed-origins (mapv origin! origins)
     :deadline-ms (bounded-int! :http/deadline-ms (:deadline-ms value)
                                1 120000)
     :ingress-methods (normalize-http-methods :http/ingress-methods ingress)
     :max-request-bytes
     (bounded-int! :http/max-request-bytes (:max-request-bytes value)
                   1 16777216)
     :max-response-bytes
     (bounded-int! :http/max-response-bytes (:max-response-bytes value)
                   1 16777216))))

(defn- normalize-object-store [value declared-bindings]
  (exact-keys! :object-store value object-store-keys)
  (let [bindings (:bindings value)]
    (when-not (and (map? bindings) (<= 1 (count bindings) 32))
      (reject! "object-store bindings must be a non-empty bounded map"
               {:field :object-store/bindings :limit 32}))
    (sorted-map
     :bindings
     (into
      (sorted-map)
      (map
       (fn [[binding descriptor]]
         (let [binding (binding-name! (name binding))]
           (when-not (contains? declared-bindings binding)
             (reject! "object-store capability references an undeclared binding"
                      {:field :object-store/bindings :binding binding}))
           (exact-keys! :object-store/binding descriptor object-binding-keys)
           (let [prefixes (:key-prefixes descriptor)]
             (when-not (and (vector? prefixes) (<= 1 (count prefixes) 64))
               (reject! "object-store prefixes must be a non-empty bounded vector"
                        {:field :object-store/key-prefixes :limit 64}))
             [binding (sorted-map :key-prefixes (mapv prefix! prefixes))])))
       bindings))
     :max-object-bytes
     (bounded-int! :object-store/max-object-bytes (:max-object-bytes value)
                   1 16777216))))

(defn validate
  "Validate and canonicalize a host profile. Unknown fields fail closed."
  [profile]
  (exact-keys! :profile profile top-level-keys)
  (when-not (= schema (:format profile))
    (reject! "unsupported host profile format" {:field :format}))
  (when-not (= :cloudflare/workerd (:target profile))
    (reject! "unsupported host target" {:field :target :target (:target profile)}))
  (let [bindings (normalize-bindings (or (:bindings profile) []))
        binding-names (set (map :binding bindings))]
    (sorted-map
     :application (normalize-application (:application profile))
     :bindings bindings
     :compatibility-date (date! (:compatibility-date profile))
     :crons (normalize-crons (or (:crons profile) []))
     :format schema
     :http (normalize-http (:http profile))
     :object-store
     (normalize-object-store (:object-store profile) binding-names)
     :routes (normalize-routes (or (:routes profile) []))
     :secrets (normalize-secrets (or (:secrets profile) #{}))
     :service (service-name! (:service profile))
     :target :cloudflare/workerd
     :vars (normalize-vars (or (:vars profile) {})))))

(defn- json-text [value]
  ;; `json.data-json/write-str` takes ONE options map (see the note at the
  ;; :javascript/v1 branch in cli.clj). json.core never escapes `/`, so the
  ;; old `:escape-slash false` is a no-op here -- an empty options map keeps
  ;; the exact same output.
  (json/write-str value {}))

(defn- js-config [profile]
  (let [http (:http profile)
        object-store (:object-store profile)]
  {:http {:allowedMethods (:allowed-methods http)
          :allowedOrigins (:allowed-origins http)
          :deadlineMs (:deadline-ms http)
          :ingressMethods (:ingress-methods http)
          :maxRequestBytes (:max-request-bytes http)
          :maxResponseBytes (:max-response-bytes http)
          ;; Family-3 ingress path/header bounds (kit http-ingress-v1).
          :maxPathBytes 4096
          :maxHeaders 32}
   :objectStore
   {:bindings
    (into (sorted-map)
          (map (fn [[binding descriptor]]
                 [binding {:keyPrefixes (:key-prefixes descriptor)}]))
          (:bindings object-store))
    :maxObjectBytes (:max-object-bytes object-store)}
   :secrets (:secrets profile)
   :vars (:vars profile)}))

(defn emit-worker
  "Emit a closed workerd adapter for the validated profile."
  [profile]
  (let [profile (validate profile)
        module (get-in profile [:application :module])
        factory (get-in profile [:application :factory])
        config (json-text (js-config profile))]
    (str
     "import { " factory " } from " (pr-str module) ";\n"
     "const PROFILE = Object.freeze(" config ");\n"
     "const utf8 = new TextEncoder();\n"
     "function byteLength(value) { return typeof value === \"string\" ? utf8.encode(value).byteLength : value.byteLength; }\n"
     "function boundedStream(readable, limit, label) {\n"
     "  const reader = readable.getReader(); let readBytes = 0; let closed = false;\n"
     "  return Object.freeze({\n"
     "    async read(maxBytes) { if (closed) return Object.freeze({ bytes: new Uint8Array(), done: true }); if (!Number.isSafeInteger(maxBytes) || maxBytes < 1 || maxBytes > 65536) throw new Error(\"resource-limit:stream-read\"); const value = await reader.read(); if (value.done) { closed = true; return Object.freeze({ bytes: new Uint8Array(), done: true }); } const bytes = value.value instanceof Uint8Array ? value.value : new Uint8Array(value.value); readBytes += bytes.byteLength; if (readBytes > limit) { closed = true; await reader.cancel(); throw new Error(\"resource-limit:\" + label); } if (bytes.byteLength <= maxBytes) return Object.freeze({ bytes, done: false }); throw new Error(\"resource-limit:stream-chunk\"); },\n"
     "    async cancel() { if (!closed) { closed = true; await reader.cancel(); } }\n"
     "  });\n"
     "}\n"
     "function readyTask(resource) { let state = resource; return Object.freeze({ poll() { if (!state) return Object.freeze({ tag: \"cancelled\" }); const value = state; state = null; return Object.freeze({ tag: \"ready\", value }); }, cancel() { state = null; } }); }\n"
     "function objectBinding(env, name, key) {\n"
     "  const descriptor = PROFILE.objectStore.bindings[name];\n"
     "  if (!descriptor || !descriptor.keyPrefixes.some(prefix => key.startsWith(prefix))) throw new Error(\"capability-denied:object-store\");\n"
     "  const bucket = env[name]; if (!bucket) throw new Error(\"capability-unbound:object-store\"); return bucket;\n"
     "}\n"
     "function host(env) { return Object.freeze({\n"
     "  http: Object.freeze({ fetch: async input => {\n"
     "    const url = new URL(input.url); const method = String(input.method || \"GET\").toUpperCase();\n"
     "    if (!PROFILE.http.allowedOrigins.includes(url.origin) || !PROFILE.http.allowedMethods.includes(method)) throw new Error(\"capability-denied:http\");\n"
     "    const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), PROFILE.http.deadlineMs);\n"
     "    try { const response = await fetch(url, { method, headers: input.headers, body: input.body, redirect: \"manual\", signal: controller.signal });\n"
     "      if (response.status >= 300 && response.status < 400) throw new Error(\"capability-denied:redirect\");\n"
     "      const declared = Number(response.headers.get(\"content-length\")); if (Number.isFinite(declared) && declared > PROFILE.http.maxResponseBytes) throw new Error(\"resource-limit:http-bytes\");\n"
     "      const body = new Uint8Array(await response.arrayBuffer()); if (body.byteLength > PROFILE.http.maxResponseBytes) throw new Error(\"resource-limit:http-bytes\");\n"
     "      return Object.freeze({ status: response.status, body }); } finally { clearTimeout(timer); }\n"
     "  }, getStream: async input => {\n"
     "    const url = new URL(input.url); if (!PROFILE.http.allowedOrigins.includes(url.origin)) throw new Error(\"capability-denied:http\");\n"
     "    const response = await fetch(url, { method: \"GET\", headers: input.headers, redirect: \"manual\" }); if (response.status >= 300 && response.status < 400) throw new Error(\"capability-denied:redirect\");\n"
     "    const declared = Number(response.headers.get(\"content-length\")); if (Number.isFinite(declared) && declared > PROFILE.http.maxResponseBytes) throw new Error(\"resource-limit:http-bytes\"); if (!response.body) throw new Error(\"http-stream-unavailable\");\n"
     "    return Object.freeze({ status: response.status, task: readyTask(boundedStream(response.body, PROFILE.http.maxResponseBytes, \"http-bytes\")) });\n"
     "  } }),\n"
     "  objectStore: Object.freeze({\n"
     "    get: async (name, key) => { const object = await objectBinding(env, name, key).get(key); if (!object) return null; const bytes = new Uint8Array(await object.arrayBuffer()); if (bytes.byteLength > PROFILE.objectStore.maxObjectBytes) throw new Error(\"resource-limit:object-bytes\"); return Object.freeze({ bytes, etag: object.etag }); },\n"
     "    getStream: async (name, key) => { const object = await objectBinding(env, name, key).get(key); if (!object) return null; if (object.size > PROFILE.objectStore.maxObjectBytes) throw new Error(\"resource-limit:object-bytes\"); return Object.freeze({ etag: object.etag, task: readyTask(boundedStream(object.body, PROFILE.objectStore.maxObjectBytes, \"object-bytes\")) }); },\n"
     "    put: async (name, key, value) => { if (byteLength(value) > PROFILE.objectStore.maxObjectBytes) throw new Error(\"resource-limit:object-bytes\"); const result = await objectBinding(env, name, key).put(key, value); return Object.freeze({ etag: result.etag }); },\n"
     "    putBlock: async (name, digest, value) => { const key = \"blocks/sha256/\" + digest; if (!/^[0-9a-f]{64}$/.test(digest)) throw new Error(\"invalid-block-digest\"); const bytes = typeof value === \"string\" ? utf8.encode(value) : value; const actual = Array.from(new Uint8Array(await crypto.subtle.digest(\"SHA-256\", bytes)), b => b.toString(16).padStart(2, \"0\")).join(\"\"); if (actual !== digest) throw new Error(\"block-digest-mismatch\"); if (byteLength(bytes) > PROFILE.objectStore.maxObjectBytes) throw new Error(\"resource-limit:object-bytes\"); const result = await objectBinding(env, name, key).put(key, bytes, { onlyIf: { etagDoesNotMatch: \"*\" } }); return Object.freeze({ digest, stored: !!result }); },\n"
     "    putImmutable: async (name, key, value) => { if (byteLength(value) > PROFILE.objectStore.maxObjectBytes) throw new Error(\"resource-limit:object-bytes\"); const result = await objectBinding(env, name, key).put(key, value, { onlyIf: { etagDoesNotMatch: \"*\" } }); return result ? Object.freeze({ etag: result.etag }) : null; },\n"
     "    compareAndSet: async (name, key, expectedEtag, value) => { if (byteLength(value) > PROFILE.objectStore.maxObjectBytes) throw new Error(\"resource-limit:object-bytes\"); const onlyIf = expectedEtag ? { etagMatches: expectedEtag } : { etagDoesNotMatch: \"*\" }; const result = await objectBinding(env, name, key).put(key, value, { onlyIf }); return result ? Object.freeze({ won: true, etag: result.etag }) : Object.freeze({ won: false }); }\n"
     "  }),\n"
     "  config: Object.freeze({ get: name => Object.prototype.hasOwnProperty.call(PROFILE.vars, name) ? PROFILE.vars[name] : null }),\n"
     "  secret: Object.freeze({ get: name => PROFILE.secrets.includes(name) && typeof env[name] === \"string\" ? env[name] : null }),\n"
     "  clock: Object.freeze({ now: () => Date.now() })\n"
     "}); }\n"
     "const INSTANCES = new WeakMap();\n"
     "function application(env) { let value = INSTANCES.get(env); if (!value) { value = " factory "(host(env)); if (!value || (typeof value.fetch !== \"function\" && typeof value.handleIncoming !== \"function\")) throw new Error(\"invalid-kotoba-workerd-application\"); value = Object.freeze(value); INSTANCES.set(env, value); } return value; }\n"
     "async function readBoundedBody(request) {\n"
     "  const declared = Number(request.headers.get(\"content-length\"));\n"
     "  if (Number.isFinite(declared) && declared > PROFILE.http.maxRequestBytes) throw new Error(\"resource-limit:http-request-bytes\");\n"
     "  const bytes = new Uint8Array(await request.arrayBuffer());\n"
     "  if (bytes.byteLength > PROFILE.http.maxRequestBytes) throw new Error(\"resource-limit:http-request-bytes\");\n"
     "  return bytes;\n"
     "}\n"
     "function foldRequestHeaders(headers) {\n"
     "  const out = Object.create(null); let count = 0;\n"
     "  for (const [name, value] of headers) {\n"
     "    const key = String(name).toLowerCase();\n"
     "    if (Object.prototype.hasOwnProperty.call(out, key)) continue;\n"
     "    count += 1; if (count > PROFILE.http.maxHeaders) throw new Error(\"resource-limit:http-request-headers\");\n"
     "    out[key] = String(value);\n"
     "  }\n"
     "  return Object.freeze(out);\n"
     "}\n"
     "async function toIncoming(request) {\n"
     "  const method = String(request.method || \"GET\").toUpperCase();\n"
     "  if (!PROFILE.http.ingressMethods.includes(method)) throw new Error(\"capability-denied:http-ingress\");\n"
     "  const url = new URL(request.url);\n"
     "  const path = url.pathname + (url.search || \"\");\n"
     "  if (byteLength(path) < 1 || byteLength(path) > PROFILE.http.maxPathBytes) throw new Error(\"resource-limit:http-path\");\n"
     "  const bodyBytes = await readBoundedBody(request);\n"
     "  const body = new TextDecoder().decode(bodyBytes);\n"
     "  return Object.freeze({ method, path, headers: foldRequestHeaders(request.headers), body });\n"
     "}\n"
     "function fromReply(reply) {\n"
     "  if (!reply || typeof reply !== \"object\") throw new Error(\"invalid-http-ingress-reply\");\n"
     "  const status = Number(reply.status);\n"
     "  if (!Number.isSafeInteger(status) || status < 100 || status > 599) throw new Error(\"invalid-http-ingress-status\");\n"
     "  const body = reply.body == null ? \"\" : String(reply.body);\n"
     "  if (byteLength(body) > PROFILE.http.maxResponseBytes) throw new Error(\"resource-limit:http-bytes\");\n"
     "  const headers = new Headers();\n"
     "  if (reply.headers && typeof reply.headers === \"object\") {\n"
     "    let count = 0;\n"
     "    for (const [name, value] of Object.entries(reply.headers)) {\n"
     "      count += 1; if (count > PROFILE.http.maxHeaders) throw new Error(\"resource-limit:http-response-headers\");\n"
     "      headers.set(String(name), String(value));\n"
     "    }\n"
     "  }\n"
     "  return new Response(body, { status, headers });\n"
     "}\n"
     "export default Object.freeze({\n"
     "  async fetch(request, env, ctx) {\n"
     "    const app = application(env);\n"
     "    if (typeof app.handleIncoming === \"function\") {\n"
     "      const incoming = await toIncoming(request);\n"
     "      const reply = await app.handleIncoming(incoming, ctx);\n"
     "      return fromReply(reply);\n"
     "    }\n"
     "    if (typeof app.fetch !== \"function\") throw new Error(\"invalid-kotoba-workerd-application\");\n"
     "    return app.fetch(request, ctx);\n"
     "  },\n"
     "  scheduled(controller, env, ctx) { const app = application(env); if (typeof app.scheduled !== \"function\") throw new Error(\"scheduled-handler-unavailable\"); return app.scheduled(Object.freeze({ scheduledTime: controller.scheduledTime, cron: controller.cron }), ctx); },\n"
     "  queue(batch, env, ctx) { const app = application(env); if (typeof app.queue !== \"function\") throw new Error(\"queue-handler-unavailable\"); return app.queue(batch, ctx); },\n"
     "  tail(events, env, ctx) { const app = application(env); if (typeof app.tail !== \"function\") throw new Error(\"tail-handler-unavailable\"); return app.tail(events, ctx); }\n"
     "});\n")))

(defn wrangler-config [profile]
  (let [profile (validate profile)]
    (cond-> (array-map
             :name (:service profile)
             :compatibility_date (:compatibility-date profile)
             :main "./worker.mjs")
      (seq (:routes profile))
      (assoc :routes
             (mapv (fn [{:keys [pattern custom-domain?]}]
                     {:pattern pattern :custom_domain custom-domain?})
                   (:routes profile)))
      (seq (:crons profile)) (assoc :triggers {:crons (:crons profile)})
      (seq (:bindings profile))
      (assoc :r2_buckets
             (mapv (fn [{:keys [binding bucket-name]}]
                     {:binding binding :bucket_name bucket-name})
                   (:bindings profile)))
      (seq (:vars profile)) (assoc :vars (:vars profile)))))

(defn generate [profile]
  (let [profile (validate profile)
        worker (emit-worker profile)
        wrangler (wrangler-config profile)]
    {:profile profile
     :worker-source worker
     :wrangler wrangler
     :manifest
     {:format :kotoba.host-artifact/v1
      :profile-sha256 (artifact/sha256 profile)
      :worker-sha256 (artifact/sha256 worker)
      :wrangler-sha256 (artifact/sha256 wrangler)
      :target (:target profile)
      :service (:service profile)}}))
