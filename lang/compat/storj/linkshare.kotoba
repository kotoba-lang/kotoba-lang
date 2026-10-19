(ns storj.linkshare
  "Storj Linksharing URLs — public, unsigned, no credentials on the wire.

  Linksharing is the other half of Storj's edge: where Gateway-MT wants a signed
  S3 request, the linksharing service serves an object to anyone holding the
  URL. That makes it the right tool for public assets and the wrong tool for
  anything private, so the whole namespace is pure URL construction with one
  safety check.

  ## The access key here is not your S3 secret

  The `access-key` embedded in a linksharing URL is a *derived, restricted*
  access grant — read-only, scoped to a prefix, ideally expiring. It travels in
  the URL, which means it is as public as the URL is. Passing a root access
  grant would hand every reader full control of the project; `share-url` rejects
  the serialized-access-grant shape outright for exactly that reason.

  Create the restricted key with `uplink share --readonly --url --not-after …`
  (or the satellite UI) and pass the resulting access key here."
  (:require [clojure.string :as str]
            [sigv4.core :as v4]))

(def endpoints
  "Known linksharing hosts. `:global` is geo-routed to the nearest edge; the
  regional hosts pin to one."
  {:global "https://link.storjshare.io"
   :us1    "https://link.us1.storjshare.io"
   :eu1    "https://link.eu1.storjshare.io"
   :ap1    "https://link.ap1.storjshare.io"})

(def default-base (:global endpoints))

(defn- assert-not-access-grant!
  "A serialized Storj *access grant* is a long base58 blob that carries the
  project's encryption key. It is not a linksharing access key, and putting one
  in a URL publishes the project. Length is the reliable discriminator: access
  keys are short, grants are hundreds of characters."
  [access-key]
  (when (> (count (str access-key)) 128)
    (throw (ex-info (str "refusing to build a URL from what looks like a serialized "
                         "access grant — derive a restricted linksharing access key "
                         "first (uplink share --readonly --url)")
                    {:type ::access-grant-in-url})))
  (when (str/blank? (str access-key))
    (throw (ex-info "access-key is required" {:type ::access-grant-in-url}))))

(defn- build [kind {:keys [base access-key bucket key]}]
  (assert-not-access-grant! access-key)
  (let [base (str/replace (str (or base default-base)) #"/+$" "")
        segs (cond-> [kind (v4/uri-encode access-key) (v4/uri-encode bucket)]
               (not (str/blank? (str key)))
               (into (map v4/uri-encode (str/split (str key) #"/" -1))))]
    (str base "/" (str/join "/" segs))))

(defn share-url
  "Browser-facing URL: `<base>/s/<access-key>/<bucket>/<key>`. Serves Storj's
  HTML wrapper (preview, download button), so it is what you send to a person."
  [opts]
  (build "s" opts))

(defn raw-url
  "Direct-bytes URL: `<base>/raw/<access-key>/<bucket>/<key>`. No HTML wrapper,
  so it is what you put in an `<img src>`, a `<video>`, or a fetch — and what
  you hand to a machine."
  [opts]
  (build "raw" opts))
