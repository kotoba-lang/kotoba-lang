(ns kototama.contract
  "Pure CLJC authority contract for the kototama host facade.

  This namespace defines data shapes and validation rules for actor host imports.
  Hosts may adapt these maps to Rust, JS, or JVM APIs, but the requested import
  surface is accepted only when the contract grants and runtime limits allow it."
  (:require [kotoba.lang.text :as str]))

(def actor-host-namespace "actor:host")
(def actor-host-version 0)

(def import-surface
  {:abi/namespace actor-host-namespace
   :abi/version actor-host-version
   :abi/imports
   [{:import/id :gen-keypair
     :import/name "gen-keypair"
     :import/category :identity
     :import/effects #{:crypto :secret}}
    {:import/id :sign
     :import/name "sign"
     :import/category :identity
     :import/effects #{:crypto :secret}}
    {:import/id :verify
     :import/name "verify"
     :import/category :identity
     :import/effects #{:crypto}}
    {:import/id :sha256-hex
     :import/name "sha256-hex"
     :import/category :content-addressing
     :import/effects #{:crypto}}
    {:import/id :http-post
     :import/name "http-post"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :log-read
     :import/name "log-read"
     :import/category :storage
     :import/effects #{:storage}}
    {:import/id :log-write
     :import/name "log-write"
     :import/category :storage
     :import/effects #{:storage :write}}
    {:import/id :clock-monotonic
     :import/name "clock-monotonic"
     :import/category :clock
     :import/effects #{:clock}}
    ;; Bounded OS CSPRNG fill (actor-host `random_bytes`; host-parity L5).
    ;; Default `:max-random-bytes` is 0 — fully denied until HostCaps raises
    ;; the quota (same pattern as :max-http-posts / :max-llm-infers).
    {:import/id :random-bytes
     :import/name "random-bytes"
     :import/category :randomness
     :import/effects #{:crypto :write}}
    ;; Decision-aware trusted-adapter sign (host-parity L5 / T8.4).
    ;; Key never enters the guest; default `:max-kagi-signs` 0 deny-by-default.
    ;; JVM links via tender inject (`:kagi-client` + `:kagi-decisions`);
    ;; browser intentional :no (native boundary); node :inject.
    {:import/id :kagi-sign
     :import/name "kagi-sign"
     :import/category :identity
     :import/effects #{:crypto}}
    {:import/id :llm-infer
     :import/name "llm-infer"
     :import/category :llm
     :import/effects #{:network}}
    ;; Second wave (com-junkawasaki/root ADR-2607230943): CBOR/JSON
    ;; wire-format encoding + GET-only HTTP fetch, for a future
    ;; news-collecting fleet actor (kawaraban/cloud-itonami) that builds
    ;; CACAO token bytes and XRPC request/response bodies from within a
    ;; `.kotoba` guest. `:http-fetch` reuses kotoba-core-contracts'
    ;; pre-existing "http/fetch" capability id 205 (`kotoba wasm emit`'s
    ;; own kernel-capability vocabulary already registers it with the
    ;; exact shape kototama needs) rather than a new one -- same "reuse,
    ;; don't duplicate" choice `:log-write`/`:clock-monotonic` above
    ;; already made.
    {:import/id :http-fetch
     :import/name "http-fetch"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :cbor-encode
     :import/name "cbor-encode"
     :import/category :codec
     :import/effects #{}}
    {:import/id :json-encode
     :import/name "json-encode"
     :import/category :codec
     :import/effects #{}}
    {:import/id :json-extract-field
     :import/name "json-extract-field"
     :import/category :codec
     :import/effects #{}}
    ;; Third wave (com-junkawasaki/root, this ADR -- Phase A's
    ;; ADR-2607231022 explicitly left this as a follow-up, closed here):
    ;; a SEPARATE import id from `:http-post` (a Wasm import's arity is
    ;; part of the compiled guest's own import section, so `:http-post`
    ;; itself cannot grow a headers parameter without breaking every
    ;; already-compiled guest that imports it), but the SAME `#{:network}`
    ;; effect and category -- this is the same operation (POST) with one
    ;; added guest-supplied input, not a new kind of effect.
    {:import/id :http-post-headers
     :import/name "http-post-headers"
     :import/category :network
     :import/effects #{:network}}
    ;; Transport / TLS inject path (kototama.transport-provider native-provider).
    ;; Not built into tender core — callers pass HostFunctions via
    ;; open-session :provider-host-functions (T8.4 host-parity L5 inject).
    {:import/id :transport-connect
     :import/name "transport-connect"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :tls-open
     :import/name "tls-open"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :tls-server-end-point
     :import/name "tls-server-end-point"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :transport-write
     :import/name "transport-write"
     :import/category :network
     :import/effects #{:network :write}}
    {:import/id :transport-read
     :import/name "transport-read"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :transport-close
     :import/name "transport-close"
     :import/category :network
     :import/effects #{:network}}
    ;; PostgreSQL cancel inject (transport-provider). Fail-closed without TLS session.
    {:import/id :pg-cancel-register
     :import/name "pg-cancel-register"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-cancel
     :import/name "pg-cancel"
     :import/category :network
     :import/effects #{:network}}
    ;; PostgreSQL opaque pool inject path (kototama.postgresql-pool-provider).
    ;; Not built into tender core — callers pass HostFunctions via
    ;; open-session :provider-host-functions (T8.4 host-parity L5 inject).
    ;; Production uses real pool-provider + SCRAM/query sessions; live runner
    ;; may inject fail-closed stubs to prove linkage without a live PG.
    {:import/id :pg-pool-open
     :import/name "pg-pool-open"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-pool-acquire
     :import/name "pg-pool-acquire"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-pool-query
     :import/name "pg-pool-query"
     :import/category :network
     :import/effects #{:network :write}}
    {:import/id :pg-pool-release
     :import/name "pg-pool-release"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-pool-stats
     :import/name "pg-pool-stats"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-pool-health
     :import/name "pg-pool-health"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-pool-drain
     :import/name "pg-pool-drain"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-pool-close
     :import/name "pg-pool-close"
     :import/category :network
     :import/effects #{:network}}
    ;; PostgreSQL wire component-link inject (T8.4 beyond pool/cancel).
    ;; Real wire is component-linked Wasm + SCRAM; live runner uses
    ;; postgresql-wire-provider fail-closed stubs.
    {:import/id :pg-open
     :import/name "pg-open"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-query
     :import/name "pg-query"
     :import/category :network
     :import/effects #{:network :write}}
    {:import/id :pg-simple-query
     :import/name "pg-simple-query"
     :import/category :network
     :import/effects #{:network :write}}
    {:import/id :pg-prepare
     :import/name "pg-prepare"
     :import/category :network
     :import/effects #{:network :write}}
    {:import/id :pg-session-reset
     :import/name "pg-session-reset"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-close-statement
     :import/name "pg-close-statement"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-query-state
     :import/name "pg-query-state"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-prepare-typed
     :import/name "pg-prepare-typed"
     :import/category :network
     :import/effects #{:network :write}}
    {:import/id :pg-execute-params2
     :import/name "pg-execute-params2"
     :import/category :network
     :import/effects #{:network :write}}
    {:import/id :pg-execute-params
     :import/name "pg-execute-params"
     :import/category :network
     :import/effects #{:network :write}}
    {:import/id :pg-bind-portal
     :import/name "pg-bind-portal"
     :import/category :network
     :import/effects #{:network :write}}
    {:import/id :pg-fetch-portal
     :import/name "pg-fetch-portal"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-close-portal
     :import/name "pg-close-portal"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-copy-out
     :import/name "pg-copy-out"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-copy-in
     :import/name "pg-copy-in"
     :import/category :network
     :import/effects #{:network :write}}
    {:import/id :pg-execute-batch
     :import/name "pg-execute-batch"
     :import/category :network
     :import/effects #{:network :write}}
    ;; Purpose-bound SCRAM crypto (guest never sees password). Required for
    ;; live SCRAM success path (pg_scram.kotoba); inject wire uses fail-closed.
    {:import/id :scram-sha256
     :import/name "scram-sha256"
     :import/category :secret
     :import/effects #{:secret}}
    ;; SCRAM wire inject (T8.4 remaining wire beyond portal/copy/batch).
    {:import/id :pg-open-scram
     :import/name "pg-open-scram"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-open-scram-random
     :import/name "pg-open-scram-random"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-open-scram-cancellable-random
     :import/name "pg-open-scram-cancellable-random"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-cancel-authority-use
     :import/name "pg-cancel-authority-use"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :pg-close-scram
     :import/name "pg-close-scram"
     :import/category :network
     :import/effects #{:network}}
    ;; Component-only linear resources. Core-Wasm tenders do not bind these
    ;; names; they are present in HostCaps so the Component adapter can carry
    ;; Aiueos decisions to individual WIT providers without an ambient escape.
    {:import/id :http-get-stream
     :import/name "http-get-stream"
     :import/category :network
     :import/effects #{:network}}
    {:import/id :object-get-stream
     :import/name "object-get-stream"
     :import/category :storage
     :import/effects #{:storage}}
    {:import/id :object-put-block
     :import/name "object-put-block"
     :import/category :storage
     :import/effects #{:storage :write}}
    {:import/id :object-compare-and-set-ref
     :import/name "object-compare-and-set-ref"
     :import/category :storage
     :import/effects #{:storage :write}}]})

(def import-by-id
  (into {} (map (juxt :import/id identity) (:abi/imports import-surface))))

(def import-id-by-name
  (into {} (map (juxt :import/name :import/id) (:abi/imports import-surface))))

(def RuntimeLimits
  {:model/name :kototama.contract/RuntimeLimits
   :model/keys {:max-imports :non-negative-int
                :max-http-posts :non-negative-int
                :max-http-fetches :non-negative-int
                :max-llm-infers :non-negative-int
                :max-kagi-signs :non-negative-int
                :max-log-read-bytes :non-negative-int
                :max-log-write-bytes :non-negative-int
                :max-random-bytes :non-negative-int
                :max-memory-pages :non-negative-int
                :max-transport-connections :non-negative-int
                :max-transport-connect-ms :non-negative-int
                :max-transport-read-ms :non-negative-int
                :max-transport-read-bytes :non-negative-int
                :max-transport-write-bytes :non-negative-int
                :max-pg-cancel-handles :non-negative-int
                :max-pg-cancel-requests :non-negative-int
                :max-scram-proofs :non-negative-int
                :scram-credential-allowlist :set-or-nil
                :transport-endpoint-allowlist :set-or-nil
                :transport-resolved-address-allowlist :set-or-nil
                :allowed-url-prefixes :vector-of-string-or-nil
                :allow-secret-imports? :boolean
                :allow-write-imports? :boolean
                :http-connect-timeout-ms :non-negative-int
                :http-request-timeout-ms :non-negative-int}})

(def default-runtime-limits
  {:max-imports (count (:abi/imports import-surface))
   :max-http-posts 0
   ;; Same "fully denied unless a caller's HostCaps explicitly raises the
   ;; limit AND grants the import" default as :max-http-posts -- GET is
   ;; still metered guest-triggered network egress, not a free action.
   :max-http-fetches 0
   ;; Same "fully denied unless a caller's HostCaps explicitly raises the
   ;; limit AND grants the import" default as :max-http-posts -- an LLM
   ;; call is metered network egress + real API spend, not a free action.
   :max-llm-infers 0
   ;; Finite kagi-sign invocation quota. Default 0 = deny-by-default until
   ;; HostCaps raises it (matches actor-host.js maxKagiSigns: 0).
   :max-kagi-signs 0
   :max-log-read-bytes 1048576
   :max-log-write-bytes 65536
   ;; CSPRNG fill quota (bytes). Default 0 = deny-by-default until raised
   ;; (matches actor-host.js maxRandomBytes: 0 and host-parity note).
   :max-random-bytes 0
   ;; Transport inject (transport-provider). Defaults deny-by-default:
   ;; connections/bytes 0; endpoint allowlist nil fails endpoint-allowed?
   ;; (requires set). Callers raise limits + pass provider-host-functions.
   :max-transport-connections 0
   :max-transport-connect-ms 5000
   :max-transport-read-ms 5000
   :max-transport-read-bytes 0
   :max-transport-write-bytes 0
   ;; PG cancel inject (transport-provider). Default 0 deny-by-default.
   :max-pg-cancel-handles 0
   :max-pg-cancel-requests 0
   ;; SCRAM crypto proofs. Default 0 deny-by-default until HostCaps raises.
   :max-scram-proofs 0
   :scram-credential-allowlist nil
   :transport-endpoint-allowlist nil
   :transport-resolved-address-allowlist nil
   ;; 16 Wasm pages (64 KiB/page) = 1 MiB -- a guest that legitimately
   ;; needs more grows this explicitly via HostCaps, same as every other
   ;; limit here; this is NOT an :abi/imports-gated effect (a guest's
   ;; linear memory ceiling applies regardless of which host imports it's
   ;; granted), so it is deliberately NOT checked by
   ;; validate-import-surface below -- a host adapter (kototama.tender,
   ;; the browser actor-host.js) reads it directly off HostCaps' :limits
   ;; when it instantiates.
   :max-memory-pages 16
   ;; nil = unrestricted (the default -- preserves prior behavior for every
   ;; existing caller that never set this). When non-empty, http-post is
   ;; allowed only to URLs starting with one of these prefixes. Like
   ;; :max-memory-pages above, this is deliberately NOT checked by
   ;; validate-import-surface (it doesn't gate whether the import is
   ;; granted, only which URLs a granted call may reach) -- host adapters
   ;; (kototama.tender, the browser actor-host.js) check it per-call via
   ;; url-allowed? below.
   :allowed-url-prefixes nil
   :allow-secret-imports? false
   :allow-write-imports? false
   ;; [com-junkawasaki/root, 2026-07-23] Both default to 5000 -- BYTE-
   ;; IDENTICAL to `kototama.tender`'s previous hardcoded `(Duration/
   ;; ofSeconds 5)` constants, so every existing caller that never sets
   ;; these behaves exactly as before. Added because a real go-live
   ;; deployment (kawaraban/cloud-itonami media actors posting to the
   ;; live pds.aozora.app) found the fixed 5s ceiling too aggressive for
   ;; that server's occasional latency (a real createRecord call that
   ;; DID eventually succeed, confirmed via a 30s raw HttpClient retry,
   ;; still timed out at kototama's own 5s default) -- rather than widen
   ;; the constant globally (risking every OTHER kototama consumer's
   ;; fast-fail DoS guarantee for a problem specific to one slow peer),
   ;; both are now caller-configurable per HostCaps, same opt-in pattern
   ;; :allowed-url-prefixes above already established.
   :http-connect-timeout-ms 5000
   :http-request-timeout-ms 5000})

(def HostCaps
  {:model/name :kototama.contract/HostCaps
   :model/keys {:abi/namespace :string
                :abi/version :non-negative-int
                :grants :set-of-import-id
                :limits (:model/name RuntimeLimits)}})

(def default-host-caps
  {:abi/namespace actor-host-namespace
   :abi/version actor-host-version
   :grants #{}
   :limits default-runtime-limits})

(defn import-id
  "Returns the canonical import id for a request item, or nil when unknown."
  [x]
  (cond
    (keyword? x) (when (contains? import-by-id x) x)
    (string? x) (get import-id-by-name x)
    (map? x) (or (import-id (:import/id x))
                 (import-id (:id x))
                 (import-id (:import/name x))
                 (import-id (:fn x))
                 (import-id (:name x)))
    :else nil))

(defn requested-import-ids
  "Normalizes a requested surface into canonical import ids.

  Accepts a vector of keywords/strings/maps, or an ABI-shaped map containing
  :abi/imports. Unknown entries are preserved as nil so callers can detect them
  by comparing with the original request."
  [surface]
  (let [imports (if (map? surface) (:abi/imports surface) surface)]
    (mapv import-id (or imports []))))

(defn runtime-limits
  "Builds RuntimeLimits data by overlaying m on default-runtime-limits."
  ([] default-runtime-limits)
  ([m] (merge default-runtime-limits (or m {}))))

(defn url-allowed?
  "true iff URL is permitted under LIMITS' :allowed-url-prefixes.

  Semantics (security kaizen 2026-07-17):
  - `nil` prefixes = unrestricted (legacy default; preserves callers that
    never set this key)
  - empty collection `#{}` / `[]` = deny all (fail closed when the caller
    explicitly opted into an allowlist but left it empty)
  - non-empty = URL must equal or start with one prefix

  Host adapters (kototama.tender's http-post-host-fn, the browser
  actor-host.js port) call this per http-post call, failing closed with
  the same in-band -1 convention a quota-exhausted call already uses --
  an unmatched URL is an ordinary, recoverable denial, not a structural
  grant violation."
  [limits url]
  (let [prefixes (:allowed-url-prefixes limits)]
    (cond
      (nil? prefixes) true
      (empty? prefixes) false
      :else (boolean (some #(and (string? %)
                                 (or (= % url) (str/starts-with? url %)))
                           prefixes)))))

(defn host-caps
  "Builds HostCaps data. :grants is normalized to canonical import ids."
  ([] default-host-caps)
  ([m]
   (let [limits (runtime-limits (:limits m))
         grants (set (keep import-id (:grants m)))]
     (merge default-host-caps
            (select-keys m [:abi/namespace :abi/version])
            {:grants grants
             :limits limits}))))

(defn- limit-errors [limits requested ids]
  (let [requested-count (count requested)
        ;; :http-post-headers shares :http-post's own :max-http-posts budget
        ;; (same operation, same network-egress quota -- see this ns'
        ;; :http-post-headers import entry comment above).
        http-posts (count (filter #{:http-post :http-post-headers} ids))
        http-fetches (count (filter #{:http-fetch} ids))
        llm-infers (count (filter #{:llm-infer} ids))
        kagi-signs (count (filter #{:kagi-sign} ids))
        secret-imports (filterv #(some (:import/effects (import-by-id %)) [:secret]) ids)
        write-imports (filterv #(some (:import/effects (import-by-id %)) [:write]) ids)]
    (cond-> []
      (> requested-count (:max-imports limits))
      (conj {:error :limit/max-imports
             :limit (:max-imports limits)
             :actual requested-count})

      (> http-posts (:max-http-posts limits))
      (conj {:error :limit/max-http-posts
             :limit (:max-http-posts limits)
             :actual http-posts})

      (> http-fetches (:max-http-fetches limits))
      (conj {:error :limit/max-http-fetches
             :limit (:max-http-fetches limits)
             :actual http-fetches})

      (> llm-infers (:max-llm-infers limits))
      (conj {:error :limit/max-llm-infers
             :limit (:max-llm-infers limits)
             :actual llm-infers})

      (> kagi-signs (:max-kagi-signs limits))
      (conj {:error :limit/max-kagi-signs
             :limit (:max-kagi-signs limits)
             :actual kagi-signs})

      (and (false? (:allow-secret-imports? limits))
           (seq secret-imports))
      (conj {:error :limit/secret-imports
             :imports secret-imports})

      (and (false? (:allow-write-imports? limits))
           (seq write-imports))
      (conj {:error :limit/write-imports
             :imports write-imports}))))

(defn- abi-errors [surface]
  (if (map? surface)
    (cond-> []
      (not= actor-host-namespace (:abi/namespace surface))
      (conj {:error :abi/namespace
             :expected actor-host-namespace
             :actual (:abi/namespace surface)})

      (not= actor-host-version (:abi/version surface))
      (conj {:error :abi/version
             :expected actor-host-version
             :actual (:abi/version surface)}))
    []))

(defn validate-import-surface
  "Validates requested imports against HostCaps grants and RuntimeLimits.

  Returns {:ok? true ...} or {:ok? false :errors [...] ...}. This is pure data
  so host adapters can report or encode failures without duplicating authority
  decisions."
  ([surface caps]
   (let [caps (host-caps caps)]
     (validate-import-surface surface (:grants caps) (:limits caps))))
  ([surface grants limits]
   (let [abi-errors (abi-errors surface)
         requested (if (map? surface) (:abi/imports surface) surface)
         requested (vec (or requested []))
         ids (requested-import-ids requested)
         indexed (map vector requested ids)
         unknown (vec (keep (fn [[request id]]
                              (when (nil? id) request))
                            indexed))
         known-ids (vec (keep identity ids))
         grants (set (keep import-id grants))
         missing (vec (remove grants known-ids))
         limits (runtime-limits limits)
         errors (cond-> abi-errors
                  (seq unknown)
                  (conj {:error :imports/unknown
                         :imports unknown})

                  (seq missing)
                  (conj {:error :grants/missing
                         :imports missing}))]
     (let [errors (into errors (limit-errors limits requested known-ids))]
       {:ok? (empty? errors)
        :requested known-ids
        :granted grants
        :limits limits
        :errors errors}))))
