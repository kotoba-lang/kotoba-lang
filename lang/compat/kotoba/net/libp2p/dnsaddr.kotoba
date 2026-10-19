(ns kotoba.net.libp2p.dnsaddr
  "`/dnsaddr/…` resolution.

  Every libp2p bootstrap list is written in `dnsaddr` form, so a dialer that
  cannot resolve it cannot reach the well-known entry points at all. The record
  is a TXT lookup on `_dnsaddr.<host>` whose values are `dnsaddr=<multiaddr>`,
  and one host answers with many addresses across several transports.

  Filtering by peer id is not optional. A `dnsaddr` entry may carry a `/p2p/…`
  component, and a host can legitimately answer for several peers; taking the
  first record because it parsed is how a dialer ends up authenticating a peer
  it did not mean to reach -- which would then verify correctly and be the
  wrong node."
  (:require [clojure.string :as str])
  (:import [javax.naming.directory InitialDirContext]
           [java.util Hashtable]))

(def prefix "/dnsaddr/")

(defn- txt-records
  "TXT values for a name, via the platform resolver.

  JNDI rather than a DNS library: this is mechanism, and the one thing that
  must not be reimplemented here is the resolver the host is configured with --
  a library with its own recursive resolver would ignore split-horizon DNS and
  local overrides that the rest of the machine obeys."
  [name]
  (let [env (doto (Hashtable.)
              (.put "java.naming.factory.initial" "com.sun.jndi.dns.DnsContextFactory"))
        ctx (InitialDirContext. env)
        attrs (.getAttributes ctx (str "dns:/" name) (into-array String ["TXT"]))]
    (when-let [attr (.get attrs "TXT")]
      (->> (range (.size attr))
           (map #(str (.get attr %)))
           ;; JNDI returns quoted strings, and long records arrive split.
           (map #(str/replace % "\"" ""))
           vec))))

(defn dnsaddr? [address] (str/starts-with? (str address) prefix))

(defn resolve-address
  "Resolve a `/dnsaddr/host[/p2p/id]` to the concrete multiaddrs it names.

  Returns them in the order the resolver gave, filtered to the peer id when the
  input named one. An empty result is an ordinary answer -- the host may be
  reachable only over transports this dialer cannot speak."
  [address]
  (when (dnsaddr? address)
    (let [components (str/split (subs (str address) (count prefix)) #"/")
          host (first components)
          wanted (second (drop-while #(not= "p2p" %) components))
          records (or (txt-records (str "_dnsaddr." host)) [])]
      (->> records
           (keep #(second (re-find #"^dnsaddr=(.+)$" (str/trim %))))
           (filter (fn [addr] (or (nil? wanted) (str/includes? addr wanted))))
           vec))))

(defn resolve-dialable
  "Resolve and keep only what a given transport can dial.

  `dialable?` is injected so this namespace does not decide what is dialable --
  that belongs to whichever transport is asking, and a hard-coded answer here
  would go stale the moment one is added."
  [address dialable?]
  (vec (filter dialable? (resolve-address address))))
