(ns kotobase.protocols.router
  "Host-based dispatch for the kotobase protocol surfaces
  (ADR-2607171700; split into per-capability repos by ADR-2608051000).

    s3.<apex>       → the \"s3\" handler
    ipfs.<apex>     → the \"ipfs\" handler
    atproto.<apex>  → the \"atproto\" handler
    git.<apex>      → the \"git\" handler
    pinning.<apex>  → the \"pinning\" handler
    issues.<apex>   → the \"issues\" handler

  plus a single-origin fallback for deploys that own one hostname.

  ## Every surface is injected — core has no built-in table

  Upstream `kotobase-protocols` kept a `surfaces` def that `:require`d
  s3/ipfs/atproto/git/ipfs-pinning/issue directly and merged shell-injected
  surfaces *under* it. That table is exactly what prevented the surfaces from
  living in separate repositories: it made the router a compile-time consumer
  of all six.

  Here the table is empty and `ctx :surfaces` is the whole registry. The
  contract is otherwise **unchanged and deliberately so** — labels are
  strings, `:path-surfaces` is `{prefix label}`, injected mounts are checked
  before the built-in prefixes and are *not* stripped — so a deploy shell can
  move between the facade and this repo by changing a dependency, not a call.

  A shell that owns exactly one host does not need this namespace at all; it
  can call its one handler directly. This exists for combined deployments and
  for self-hosted mesh peers, where `:apex` is injectable so the same code
  serves any domain.

  A surface absent from `:surfaces` is not served — including its `/health`,
  so a shell never advertises readiness for a capability it does not carry."
  (:require [clojure.string :as str]
            [kotobase.protocols.http :as http]))

(def ^:private default-prefixes
  "Single-origin fallbacks, as [prefix label strip?].

  `strip?` drops the mount prefix before the handler sees it, which /s3,
  /git and /issues need because their wire formats are rooted at the mount
  point. /ipfs, /ipns, /xrpc and /pins carry protocol-inherent prefixes their
  handlers expect to receive intact."
  [["/ipfs/"   "ipfs"    false]
   ["/ipns/"   "ipfs"    false]
   ["/xrpc/"   "atproto" false]
   ["/pins"    "pinning" false]
   ["/s3/"     "s3"      true]
   ["/git/"    "git"     true]
   ["/issues/" "issues"  true]])

(defn surfaces-for
  "Every surface this request may reach. Unlike the facade's version there is
  nothing to merge with: the shell's `:surfaces` is the registry."
  [ctx]
  (or (:surfaces ctx) {}))

(defn surface-of
  "\"s3.kotobase.net\" + apex \"kotobase.net\" → \"s3\"; nil when host
  is not a single label in front of the apex."
  [host apex]
  (when (and host (str/ends-with? host (str "." apex)))
    (let [label (subs host 0 (- (count host) (inc (count apex))))]
      (when (and (seq label) (not (str/includes? label ".")))
        label))))

(defn- strip-prefix [req prefix]
  (assoc req :path (subs (:path req) (count prefix))))

(defn- mounted
  "A shell-injected single-origin mount, checked before the built-in
  prefixes so a shell can mount /sparql or /cypher on the one hostname it
  owns. The prefix is NOT stripped: those protocols specify their own
  absolute paths (SPARQL 1.1 Protocol, Neo4j's
  /db/data/transaction/commit) and stripping would break the handlers'
  own path checks."
  [ctx req]
  (let [all (surfaces-for ctx)
        path (or (:path req) "")]
    (some (fn [[prefix label]]
            (when (str/starts-with? path prefix)
              (when-let [h (get all label)] (h ctx req))))
          (:path-surfaces ctx))))

(defn handle
  "Route `req` to its protocol surface. ctx: {:store ... :now ...
  :apex \"kotobase.net\" :surfaces {label handler} :path-surfaces
  {prefix label}}."
  [{:keys [apex] :or {apex "kotobase.net"} :as ctx} req]
  (let [path (or (:path req) "")
        all (surfaces-for ctx)
        surface (surface-of (:host req) apex)]
    (if (and (= :get (:method req)) (= "/health" path) (contains? all surface))
      (http/response 200
                     {"content-type" "application/edn; charset=utf-8"
                      "cache-control" "no-store"}
                     (pr-str {:ok true :service (keyword (str "kotobase.protocols/" surface))
                              :surface (keyword surface) :apex apex}))
      (if-let [handler (get all surface)]
        (handler ctx req)
        (or (mounted ctx req)
            (if-let [[prefix label strip?]
                     (first (filter (fn [[p l _]]
                                      (and (str/starts-with? path p) (get all l)))
                                    default-prefixes))]
              ((get all label) ctx (if strip?
                                     (strip-prefix req (subs prefix 0 (dec (count prefix))))
                                     req))
              (http/not-found "no protocol surface for this host/path")))))))
