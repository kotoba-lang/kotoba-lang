(ns transit.core
  "Small CLJC plain-JSON wire layer for Kotoba app/resource envelopes.

  The public shape is deliberately plain, boring JSON (strings, numbers,
  booleans, nil, arrays, objects) so it can be handed to any host JSON
  encoder (Clojure, ClojureScript, babashka, browser fetch, curl) without a
  Kotoba-specific reader. Keywords and symbols are written as their bare
  `ns/name` (or `name`) string; sets and other seqs become JSON arrays.

  This is a lossy projection by design (ADR-kotoba-json-wire-protocol.md,
  superseding ADR-kotoba-transit-wire-protocol.md): the wire layer does not
  tag values to recover exact EDN type identity for a generic reader.
  Callers that must recover a keyword-typed field (e.g. a resource-kind
  discriminant) do so explicitly, because they already know the shape of
  the envelope they are reading."
  (:require [clojure.string :as str]))

(def media-type-json "application/json")
(def gzip-encoding "gzip")
(def default-gzip-threshold-bytes 1024)

(def office-family :kotoba.protocol/office)
(def office-envelope-version 1)
(def office-resource-kinds
  #{:slides/deck
    :sheets/workbook
    :docs/document
    :office/update
    :office/selection
    :office/presence})

(defn json-content-type? [content-type]
  (= media-type-json
     (some-> content-type
             (str/split #";")
             first
             str/trim
             str/lower-case)))

(defn accepts-json? [accept]
  (boolean
   (some #(= media-type-json
             (-> % (str/split #";") first str/trim str/lower-case))
         (str/split (or accept "") #","))))

(defn- write-key [k]
  (cond
    (keyword? k) (subs (str k) 1)
    (symbol? k) (str k)
    (string? k) k
    :else (str k)))

(defn write-json
  "Convert an EDN value into plain JSON-compatible data.

  Keywords and symbols become bare strings (their full `ns/name`, no tag
  prefix); sets and other sequences become JSON arrays; maps become JSON
  objects with string keys. Strings, numbers, booleans, and nil pass
  through unchanged."
  [x]
  (cond
    (nil? x) nil
    (string? x) x
    (keyword? x) (subs (str x) 1)
    (symbol? x) (str x)
    (set? x) (mapv write-json (sort-by pr-str x))
    (sequential? x) (mapv write-json x)
    (map? x) (into {} (map (fn [[k v]] [(write-key k) (write-json v)]) x))
    :else x))

(defn- envelope-key [k]
  (str/replace (write-key k) "-" "_"))

(defn- envelope-body [body]
  (into {}
        (map (fn [[k v]] [(envelope-key k) (write-json v)]))
        body))

(defn- with-gzip [envelope opts]
  (cond-> envelope
    (:gzip? opts) (assoc :content-encoding gzip-encoding)))

(defn datomic-envelope
  "Wrap a Datomic request body with the default Kotoba JSON headers.

  Pass `{:gzip? true}` when the caller will gzip-compress the JSON body
  (recommended above `default-gzip-threshold-bytes`); this only adds the
  `:content-encoding` header, the host performs the actual compression."
  ([body] (datomic-envelope body {}))
  ([body opts]
   (with-gzip
     {:content-type media-type-json
      :accept media-type-json
      :body (envelope-body body)}
     opts)))

(defn office-envelope
  "Wrap a Slides/Sheets/Docs payload with Kotoba's shared JSON wire envelope.

  `resource-kind` is an application resource such as :slides/deck,
  :sheets/workbook, or :docs/document. Hosts serialize `:body` as JSON
  bytes and use the returned media headers unchanged. Pass `{:gzip? true}`
  when the host will gzip-compress the body."
  ([resource-kind payload] (office-envelope resource-kind payload {}))
  ([resource-kind payload opts]
   (when-not (contains? office-resource-kinds resource-kind)
     (throw (ex-info "unknown Kotoba office resource kind"
                     {:kind resource-kind
                      :allowed office-resource-kinds})))
   (with-gzip
     {:content-type media-type-json
      :accept media-type-json
      :body (write-json
             (cond-> {:kotoba.protocol/family office-family
                      :kotoba.protocol/version office-envelope-version
                      :kotoba.resource/kind resource-kind
                      :kotoba.resource/payload payload}
               (:request-id opts) (assoc :kotoba.request/id (:request-id opts))
               (:cid opts) (assoc :kotoba.resource/cid (:cid opts))
               (:operation opts) (assoc :kotoba.operation/kind (:operation opts))))}
     opts)))

(defn read-office-envelope-body
  "Read the plain-JSON `:body` produced by office-envelope back into EDN.

  The office envelope shape is known ahead of time by both sides, so the
  handful of keyword-typed fields (family, resource kind, operation kind)
  are recovered explicitly here. `:kotoba.resource/payload` round-trips as
  plain JSON data (string keys, arrays) rather than the original EDN
  shape — callers that need EDN back on the payload convert it themselves,
  per ADR-kotoba-json-wire-protocol.md."
  [body]
  (let [family (some-> (get body "kotoba.protocol/family") keyword)
        operation (some-> (get body "kotoba.operation/kind") keyword)]
    (when (not= office-family family)
      (throw (ex-info "not a Kotoba office JSON envelope" {:family family})))
    (cond-> {:kotoba.protocol/family family
             :kotoba.protocol/version (get body "kotoba.protocol/version")
             :kotoba.resource/kind (some-> (get body "kotoba.resource/kind") keyword)
             :kotoba.resource/payload (get body "kotoba.resource/payload")}
      (get body "kotoba.request/id") (assoc :kotoba.request/id (get body "kotoba.request/id"))
      (get body "kotoba.resource/cid") (assoc :kotoba.resource/cid (get body "kotoba.resource/cid"))
      operation (assoc :kotoba.operation/kind operation))))
