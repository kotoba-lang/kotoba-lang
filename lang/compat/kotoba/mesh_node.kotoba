(ns kotoba.mesh-node
  "ADR-2607082400 step 3 — the murakumo -> cljc-node HTTP contract.

  A reference (not fleet-deployed) mesh node: boots a plain JDK
  `com.sun.net.httpserver.HttpServer` (no new dependency -- this repo's
  existing modules already favour zero/minimal deps, e.g. `kotoba.wasm-exec`
  pulls in only Chicory) exposing the SAME url shape kotoba-server's own
  mesh routes use (`GET /health`, `POST /mesh/http/<route>`), so an operator
  or murakumo doesn't need to know which runtime answered a request.

  This is the strangler-fig target ADR-2607082400 designed: existing
  production mesh apps stay on Rust kotoba-server unchanged; a NEW capacity
  substrate for `.kotoba`-ported apps (see `mesh_drama_profile.kotoba`) can
  run here instead, with murakumo (or any operator) driving it over plain
  HTTP -- no gossipsub/libp2p needed, since murakumo already knows every
  node from its own `fleet.edn` (ADR-2607071900 already established
  murakumo as the fleet's de facto centralized placement authority).

  Deliberately NOT deployed to any fleet node by this commit -- it's the
  reference contract + a local, tested proof it works end-to-end, not a
  production rollout (that is its own, separately-scoped follow-up)."
  (:require [clojure.edn :as edn]
            [kotoba.runtime :as runtime]
            [kotoba.selfhost.contracts :as selfhost]
            [kotoba.wasm-exec :as wasm-exec])
  (:import [com.sun.net.httpserver HttpExchange HttpHandler HttpServer]
           [java.net InetSocketAddress]))

(defn compile-route
  "Compile SOURCE-PATH (a `.kotoba` file) once under POLICY-PATH's granted
  capabilities, returning `kotoba.runtime/wasm-binary`'s result map (with
  POLICY attached under :kotoba.mesh-node/policy for `dispatch` to enforce
  at run time). Throws if the static safe-subset/capability check
  (`kotoba.runtime/check`) or the binary emission didn't succeed -- a route
  this node can't actually serve safely is a startup-time configuration
  error, not a per-request one.

  `check` MUST run before `wasm-binary`: `wasm-binary`'s own `_policy`
  argument is not itself a guard (it never rejects an unsafe/ungranted
  program), so a caller that skipped `check` would get a compiled module
  with none of the denylist/capability-policy/affine-capability static
  checks applied to it."
  [source-path policy-path]
  (let [forms (runtime/read-file source-path :kotoba)
        policy (edn/read-string (slurp policy-path))
        checked (runtime/check (selfhost/load-seed "safe_analyzer_facts")
                               {:kotoba.source/path source-path} forms policy)]
    (when-not (:kotoba.runtime/ok? checked)
      (throw (ex-info "kotoba.mesh-node: safe-subset/capability check failed"
                      {:kotoba.mesh-node/source source-path
                       :kotoba.runtime/result checked})))
    (let [wasm (runtime/wasm-binary forms policy)]
      (when-not (:kotoba.wasm/ok? wasm)
        (throw (ex-info "kotoba.mesh-node: compile failed"
                        {:kotoba.mesh-node/source source-path
                         :kotoba.mesh-node/wasm wasm})))
      (assoc wasm :kotoba.mesh-node/policy policy))))

(defn dispatch
  "Run WASM's `main` through a FRESH Chicory Instance (its own kgraph STORE
  atom, seeded empty) and return whatever it wrote to its `kgraph_query`
  buffer, or nil if it never queried (an assert-only guest has nothing to
  answer with). No state survives across calls in this reference node --
  a real deployment would inject a shared/persistent store instead of a
  fresh atom per dispatch (follow-up, out of this ADR's scope).

  Both `instantiate` and `kgraph-host-functions` are called with WASM's
  :kotoba.mesh-node/policy (set by `compile-route`), which routes every
  kgraph-* host import through the same fail-closed, per-call capability
  guard (`kotoba.wasm-exec/guard-host-call`) the interpreter and `wasm run`
  CLI paths use -- the unguarded 1-arg forms exist only for callers that
  have already fully vetted their WASM bytes some other way, which this
  node has not (it compiles arbitrary operator-supplied `.kotoba` routes)."
  [wasm]
  (let [store (atom [])
        policy (:kotoba.mesh-node/policy wasm)
        instance (wasm-exec/instantiate (:kotoba.wasm/binary wasm)
                                        (wasm-exec/kgraph-host-functions store policy)
                                        policy)
        result (.apply (.export instance "main") (long-array 0))
        written (aget ^longs result 0)]
    (when (pos? written)
      (wasm-exec/read-memory-string instance (:kotoba.wasm/heap-base wasm) written))))

(defn- respond! [^HttpExchange exchange status ^String body]
  (let [bytes (.getBytes body "UTF-8")]
    (.sendResponseHeaders exchange status (long (count bytes)))
    (with-open [os (.getResponseBody exchange)]
      (.write os bytes))))

(defn handler
  "The contract itself:
     GET  /health              -> 200, node liveness + which runtime answers
     POST /mesh/http/<route>   -> dispatch the wasm ROUTE->WASM has bound to
                                  <route>; 404 if unbound, 204 if the guest
                                  ran but had nothing to answer with.
  ROUTE->WASM: route string -> `compile-route`'s result map (compiled ONCE
  at node startup; only the Chicory Instance is fresh per request, see
  `dispatch`)."
  [route->wasm]
  (reify HttpHandler
    (handle [_ exchange]
      (let [path (.getPath (.getRequestURI exchange))
            method (.getRequestMethod exchange)]
        (cond
          (and (= "GET" method) (= "/health" path))
          (respond! exchange 200 "{:status :ok :runtime :kotoba.wasm-exec}")

          (and (= "POST" method) (.startsWith path "/mesh/http/"))
          (let [route (subs path (count "/mesh/http/"))]
            (if-let [wasm (get route->wasm route)]
              (if-let [body (dispatch wasm)]
                (respond! exchange 200 body)
                (respond! exchange 204 ""))
              (respond! exchange 404 "{:error :route-not-bound}")))

          :else (respond! exchange 404 "{:error :not-found}"))))))

(defn start!
  "Boot an HttpServer on PORT dispatching ROUTE->WASM (see `handler`).
  Returns the HttpServer; caller shuts down with `(.stop server 0)`."
  [route->wasm port]
  (let [server (HttpServer/create (InetSocketAddress. (int port)) 0)]
    (.createContext server "/" (handler route->wasm))
    (.setExecutor server nil)
    (.start server)
    server))
