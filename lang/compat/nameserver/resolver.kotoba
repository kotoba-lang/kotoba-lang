(ns nameserver.resolver
  "IResolver — the pluggable seam between a decoded query and an answer plan,
  the DNS analogue of godaddy-dns-clj's IDns / computer-use-clj's
  mock-computer injected-capability pattern. `zone-store-resolver` wraps
  nameserver.store for zones you host outright; `chain-resolver` composes
  resolvers in order (first non-:refused wins) — e.g. real hosted zones
  first, an alt-root custom-TLD resolver (nameserver.custom-tld) as
  fallback."
  (:require [nameserver.store :as store]))

(defprotocol IResolver
  (-resolve [this qname qtype qclass]
    "Answer a query for `qname`/`qtype`/`qclass` (all strings, e.g.
    \"www.example.com.\" / \"A\" / \"IN\"). Returns
    {:status :ok/:nxdomain/:nodata/:refused :aa? bool
     :answers […] :authority […] :additional […]} (record maps use
    zone.model's :zone/* shape with absolute owner names)."))

(defrecord ZoneStoreResolver [zones]
  IResolver
  (-resolve [_ qname qtype _qclass]
    (merge {:answers [] :authority [] :additional []}
           (store/lookup zones {:qname qname :qtype qtype}))))

(defn zone-store-resolver
  "An IResolver authoritative for `zones` ({origin-FQDN -> zone.model zone})."
  [zones]
  (->ZoneStoreResolver zones))

(defrecord ChainResolver [resolvers]
  IResolver
  (-resolve [_ qname qtype qclass]
    (loop [rs resolvers]
      (if (empty? rs)
        {:status :refused :aa? false :answers [] :authority [] :additional []}
        (let [result (-resolve (first rs) qname qtype qclass)]
          (if (= :refused (:status result))
            (recur (rest rs))
            result))))))

(defn chain-resolver
  "An IResolver that tries each of `resolvers` in order; the first whose
  result isn't :refused wins."
  [resolvers]
  (->ChainResolver resolvers))

(defn resolve-question
  "Resolve a wire-decoded question map ({:dns/qname :dns/qtype :dns/qclass})
  against `resolver`."
  [resolver {:dns/keys [qname qtype qclass]}]
  (-resolve resolver qname qtype qclass))
