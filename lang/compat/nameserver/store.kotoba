(ns nameserver.store
  "In-memory authoritative zone store: given `zones` ({origin-FQDN ->
  zone.model zone}) and a query, find the closest enclosing zone
  (longest-suffix match on :zone/origin) and answer straight from its
  :zone/records — no DNSSEC, no delegation-boundary referrals (zone transfer
  lives in `nameserver.transfer`, which reads a zone rather than this store)
  (if you host both a parent and a child zone, records simply live in
  whichever zone map has them; there is no separate NS-referral step). This
  is the single-master authoritative data plane; see README for how a real
  TLD (e.g. .com) gets NS-delegated here via godaddy-dns-clj."
  (:require [clojure.string :as str]
            [nameserver.names :as names]))

(defn- absolute-name
  "Zone-relative owner name (\"@\", \"www\", …) or an already-dotted FQDN -> the
  absolute name under `origin`."
  [origin owner]
  (cond
    (= "@" owner) origin
    (str/ends-with? owner ".") owner
    :else (str owner "." origin)))

(defn- records-at [zone origin qname]
  (filterv #(= qname (absolute-name origin (:zone/name %))) (:zone/records zone)))

(defn- closest-zone
  "The zone whose :zone/origin is the longest suffix-match of `qname`, or nil
  if no zone in `zones` is authoritative for it."
  [zones qname]
  (->> (vals zones)
       (filter #(names/subdomain-of? qname (:zone/origin %)))
       (sort-by (comp count names/split-labels :zone/origin))
       last))

(defn- soa-of [zone]
  (first (filter #(= "SOA" (:zone/type %)) (:zone/records zone))))

(defn- typed [records qtype]
  (filterv #(or (= "ANY" qtype) (= qtype (:zone/type %))) records))

(defn lookup
  "Answer `{:qname :qtype}` against `zones`. Returns one of:

    {:status :refused}                     — no hosted zone covers qname
    {:status :nxdomain :aa? true :authority [soa?]}  — zone found, name (and no
                                                        matching wildcard) exists
    {:status :nodata   :aa? true :authority [soa?]}  — name exists, not this qtype
    {:status :ok :aa? true :answers […]}             — direct, CNAME-chase, or
                                                        wildcard-synthesized match

  `qtype` \"ANY\" matches every type at the name."
  [zones {:keys [qname qtype]}]
  (let [qname (if (str/ends-with? qname ".") qname (str qname "."))]
    (if-let [zone (closest-zone zones qname)]
      (let [origin (:zone/origin zone)
            abs    (fn [r] (assoc r :zone/name (absolute-name origin (:zone/name r))))
            exact  (records-at zone origin qname)
            cname  (first (filter #(= "CNAME" (:zone/type %)) exact))
            direct (typed exact qtype)
            soa    (soa-of zone)]
        (cond
          (seq direct)
          {:status :ok :aa? true :answers (mapv abs direct)}

          (and cname (not= "CNAME" qtype))
          {:status :ok :aa? true :answers [(abs cname)]}

          (seq exact)
          {:status :nodata :aa? true :answers []
           :authority (cond-> [] soa (conj (abs soa)))}

          :else
          (let [wild (typed (records-at zone origin (names/wildcard-name qname)) qtype)]
            (if (seq wild)
              ;; wildcard answers echo the *queried* owner name (already absolute), not "*"
              {:status :ok :aa? true :answers (mapv #(assoc % :zone/name qname) wild)}
              {:status :nxdomain :aa? true :answers []
               :authority (cond-> [] soa (conj (abs soa)))}))))
      {:status :refused :aa? false :answers []})))
