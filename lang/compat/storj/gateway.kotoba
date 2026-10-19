(ns storj.gateway
  "Storj Gateway-MT configuration — endpoints, defaults, and validation.

  Storj's S3-compatible edge (`gateway.storjshare.io`) speaks ordinary S3 with
  SigV4, so the wire format is `kotoba-lang/sigv4`'s problem and this namespace only
  owns what is *Storj-specific*: which hosts are legitimate gateways, what a
  Storj S3 credential looks like, and which defaults to fill in.

  Pure `.cljc`: validation returns/throws on data you hand it, nothing is read
  from the environment here. Hosts that want env-driven config build the config
  map themselves and pass it to `validate`."
  (:require [clojure.string :as str]))

(def endpoints
  "Known Storj Gateway-MT S3 endpoints.

  `:global` is the one to use: Storj routes it by BGP to the nearest instance,
  and their docs say they prefer to hand out the global URL. The regional hosts
  remain reachable and are kept here for pinning traffic to a specific edge."
  {:global "https://gateway.storjshare.io"
   :us1    "https://gateway.us1.storjshare.io"
   :eu1    "https://gateway.eu1.storjshare.io"
   :ap1    "https://gateway.ap1.storjshare.io"})

(def default-endpoint (:global endpoints))

(def default-region
  "SigV4 credential-scope region.

  Storj does not use this value for data placement — placement is a property of
  the access grant and the bucket's preferred region, not of the signature — and
  Storj's docs do not mandate a particular string. `us-east-1` is what S3 SDKs
  default to, so it is what interoperates. It only has to be *consistent*
  between your client config and the signature; override with `:region` if your
  tooling is configured otherwise."
  "us-east-1")

(def ^:private gateway-host-re
  ;; gateway.storjshare.io, or gateway.<region>.storjshare.io
  #"(?i)^gateway(?:\.[a-z0-9-]+)?\.storjshare\.io$")

(defn storj-gateway-host?
  "True when `host` is a Storj-operated Gateway-MT host. Used to keep credentials
  from being signed toward an arbitrary attacker-supplied endpoint; self-hosted
  Gateway-ST deployments are legitimate but must opt out via `:allow-any-host?`."
  [host]
  (boolean (re-find gateway-host-re (str host))))

;; ── validation ───────────────────────────────────────────────────────────────
;;
;; Config is a plain map:
;;   {:endpoint "https://gateway.storjshare.io"   ; optional, defaults
;;    :bucket   "my-bucket"
;;    :access-key "jw..."                          ; Storj S3 access key id
;;    :secret-key "..."                            ; Storj S3 secret key
;;    :region "us-east-1"                          ; optional, defaults
;;    :allow-any-host? false}                      ; optional, for Gateway-ST

(defn- fail [msg]
  (throw (ex-info msg {:type ::invalid-config})))

(defn- parse-origin
  "Split an origin-only URL into `{:scheme :host}`, rejecting anything carrying
  credentials, a path, a query, or a fragment — an endpoint string is a place to
  send signed requests, not a place to smuggle a URL."
  [raw]
  (let [s (str/replace (str raw) #"/+$" "")
        m (re-matches #"(?i)(https?)://([a-z0-9._-]+(?::\d+)?)" s)]
    (when-not m (fail (str "endpoint must be an origin-only http(s) URL without "
                           "credentials, path, query, or fragment: " raw)))
    {:scheme (str/lower-case (nth m 1))
     :host   (str/lower-case (nth m 2))
     :origin s}))

(defn- validate-bucket [v]
  (let [v (str v)]
    (when-not (re-matches #"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]" v)
      (fail (str "bucket must be a 3-63 character S3-compatible name of lowercase "
                 "letters, digits, dots, or hyphens: " v)))
    (when (str/includes? v "..")
      (fail "bucket must not contain adjacent dots"))
    (when (re-matches #"\d{1,3}(?:\.\d{1,3}){3}" v)
      (fail "bucket must not be formatted as an IPv4 address"))
    v))

(defn- validate-credentials [{:keys [access-key secret-key]}]
  (when (str/blank? (str access-key)) (fail "access-key is required"))
  (when (str/blank? (str secret-key)) (fail "secret-key is required"))
  ;; A credential that carries whitespace, a comma, or a slash cannot be placed
  ;; into the Authorization header's Credential=<id>/<scope> grammar without
  ;; changing its meaning — reject rather than emit an ambiguous header.
  (when (re-find #"[\x00-\x20\x7f,/\\]" (str access-key))
    (fail "access-key must be a single printable token without whitespace, commas, or slashes"))
  (when (re-find #"[\x00-\x1f\x7f]" (str secret-key))
    (fail "secret-key must be a single-line printable secret")))

(defn- validate-region [v]
  (when-not (re-matches #"[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])?" (str v))
    (fail (str "region must be 1-32 lowercase letters, digits, or hyphens: " v)))
  v)

(defn validate
  "Normalize and validate a gateway config, filling in `:endpoint` and `:region`
  defaults. → the normalized config with `:origin` and `:host` added.

  Throws `ex-info` with `{:type ::invalid-config}` on anything malformed. Call
  it once at construction time; the request builders assume validated input."
  [{:keys [endpoint bucket region allow-any-host?] :as config}]
  (let [{:keys [host origin scheme]} (parse-origin (or endpoint default-endpoint))
        region (validate-region (or region default-region))]
    (when-not (or allow-any-host? (storj-gateway-host? host))
      (fail (str "endpoint host is not a Storj gateway: " host
                 " (pass :allow-any-host? true for a self-hosted Gateway-ST)")))
    (when (and (= scheme "http") (not allow-any-host?))
      (fail "endpoint must be https for a hosted Storj gateway"))
    (validate-credentials config)
    (assoc config
           :endpoint origin
           :origin origin
           :host host
           :bucket (validate-bucket bucket)
           :region region)))

(defn configured?
  "True when `config` has every field needed to sign a request. Does not check
  their shape — use `validate` for that."
  [config]
  (every? #(not (str/blank? (str (get config %)))) [:bucket :access-key :secret-key]))
