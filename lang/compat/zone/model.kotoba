(ns zone.model
  "DNS zone-as-EDN: a plain-data representation of a DNS zone file (RFC 1035 subset),
  plus queries and a structural diff for sync.  No I/O, no third-party deps — portable
  .cljc (JVM, ClojureScript, SCI).

  A zone is a map of namespaced `:zone/*` keys:

    {:zone/origin  \"example.com.\"
     :zone/ttl     3600
     :zone/records [{:zone/name  \"@\"
                     :zone/ttl   3600
                     :zone/class \"IN\"
                     :zone/type  \"A\"
                     :zone/rdata {:zone/address \"93.184.216.34\"}}
                    ...]}

  rdata shapes per type:
    A / AAAA  {:zone/address <str>}
    CNAME / NS / PTR  {:zone/target <str>}
    MX        {:zone/pref <long> :zone/exchange <str>}
    TXT       {:zone/text <str>}
    SOA       {:zone/mname :zone/rname :zone/serial :zone/refresh
               :zone/retry :zone/expire :zone/minimum}
    SRV       {:zone/pri :zone/weight :zone/port :zone/target}
    CAA       {:zone/flags :zone/tag :zone/value}"
  (:require [clojure.set :as set]))

;; --- queries ---

(defn records
  "All records in the zone as a seq."
  [zone]
  (seq (:zone/records zone)))

(defn records-of-type
  "Records whose :zone/type equals `type-str` (e.g. \"A\", \"MX\")."
  [zone type-str]
  (filterv #(= type-str (:zone/type %)) (:zone/records zone)))

(defn by-name
  "Records whose :zone/name equals `name` (e.g. \"@\", \"www\")."
  [zone name]
  (filterv #(= name (:zone/name %)) (:zone/records zone)))

;; --- diff ---

(defn diff
  "Compare `old-zone` to `new-zone`.
  Returns {:zone/added [recs] :zone/removed [recs] :zone/changed [{:zone/from r :zone/to r}]}.

  Identity key is [name type rdata] (full equality).  A single-record [name type] group
  whose rdata changes is reported as :zone/changed rather than add+remove — this matches
  the godaddy-dns-clj API sync model."
  [old-zone new-zone]
  (let [full-key (fn [r] [(:zone/name r) (:zone/type r) (:zone/rdata r)])
        nt-key   (fn [r] [(:zone/name r) (:zone/type r)])
        old-recs (:zone/records old-zone)
        new-recs (:zone/records new-zone)
        old-fks  (into #{} (map full-key old-recs))
        new-fks  (into #{} (map full-key new-recs))
        old-map  (into {} (map (juxt full-key identity) old-recs))
        new-map  (into {} (map (juxt full-key identity) new-recs))
        old-nt   (group-by nt-key old-recs)
        new-nt   (group-by nt-key new-recs)
        ;; changed: [name type] present on both sides, exactly one record each, rdata differs
        changed  (into []
                       (for [[nt olds] old-nt
                             :when (contains? new-nt nt)
                             :let  [news (get new-nt nt)]
                             :when (= 1 (count olds) (count news))
                             :let  [o (first olds) n (first news)]
                             :when (not= (:zone/rdata o) (:zone/rdata n))]
                         {:zone/from o :zone/to n}))
        changed-old (into #{} (map (comp full-key :zone/from) changed))
        changed-new (into #{} (map (comp full-key :zone/to)   changed))
        pure-added   (set/difference new-fks old-fks)
        pure-removed (set/difference old-fks new-fks)]
    {:zone/added   (mapv #(get new-map %) (set/difference pure-added   changed-new))
     :zone/removed (mapv #(get old-map %) (set/difference pure-removed changed-old))
     :zone/changed changed}))
