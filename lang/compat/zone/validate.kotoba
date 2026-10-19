(ns zone.validate
  "Structural validation of a DNS zone-as-EDN model.  Pure: returns a vector of problem
  maps `{:zone/severity :error|:warn :zone/code .. :zone/id .. :zone/msg ..}` so a
  caller decides how to surface them.  `valid?` is true iff there are no :error-level
  problems (warnings are advisory)."
  (:require [zone.model :as m]
            [clojure.string :as str]))

(defn- problem [severity code id msg]
  {:zone/severity severity :zone/code code :zone/id id :zone/msg msg})

(defn- fqdn-no-dot?
  "True when `s` looks like a multi-label FQDN but is missing a trailing dot."
  [s]
  (and (string? s)
       (str/includes? s ".")
       (not (str/ends-with? s "."))))

(def ^:private soa-fields
  [:zone/mname :zone/rname :zone/serial
   :zone/refresh :zone/retry :zone/expire :zone/minimum])

(def ^:private srv-fields
  [:zone/pri :zone/weight :zone/port :zone/target])

(def ^:private mx-fields
  [:zone/pref :zone/exchange])

(defn problems
  "Return a vector of structural problems with `zone`."
  [zone]
  (let [records (:zone/records zone)
        origin  (:zone/origin zone)
        ps      (transient [])
        soas    (m/records-of-type zone "SOA")]

    ;; Exactly one SOA record is required
    (when (zero? (count soas))
      (conj! ps (problem :error :zone/no-soa origin
                         "zone has no SOA record")))
    (when (> (count soas) 1)
      (doseq [extra (rest soas)]
        (conj! ps (problem :error :zone/multiple-soa (:zone/name extra)
                           "zone has more than one SOA record"))))

    ;; SOA rdata completeness
    (doseq [r soas
            :when (some #(nil? (get-in r [:zone/rdata %])) soa-fields)]
      (conj! ps (problem :error :zone/soa-rdata-missing (:zone/name r)
                         "SOA record is missing one or more required rdata fields")))

    ;; CNAME exclusivity — a name with a CNAME must have no other records
    (let [by-name (group-by :zone/name records)]
      (doseq [[name recs] by-name
              :when (some #(= "CNAME" (:zone/type %)) recs)
              :when (> (count recs) 1)]
        (conj! ps (problem :error :zone/cname-conflict name
                           (str "name \"" name "\" has a CNAME and "
                                (dec (count recs)) " other record(s)")))))

    ;; MX rdata completeness
    (doseq [r (m/records-of-type zone "MX")
            :when (some #(nil? (get-in r [:zone/rdata %])) mx-fields)]
      (conj! ps (problem :error :zone/mx-rdata-missing (:zone/name r)
                         "MX record is missing pref or exchange")))

    ;; SRV rdata completeness
    (doseq [r (m/records-of-type zone "SRV")
            :when (some #(nil? (get-in r [:zone/rdata %])) srv-fields)]
      (conj! ps (problem :error :zone/srv-rdata-missing (:zone/name r)
                         "SRV record is missing one or more required rdata fields")))

    ;; Warn: FQDN-looking rdata value missing a trailing dot
    (let [dot-fields [:zone/target :zone/exchange :zone/mname :zone/rname]]
      (doseq [r records
              f dot-fields
              :let [v (get-in r [:zone/rdata f])]
              :when (fqdn-no-dot? v)]
        (conj! ps (problem :warn :zone/missing-trailing-dot (:zone/name r)
                           (str (name f) " value \"" v
                                "\" looks like an FQDN but lacks a trailing dot")))))

    (persistent! ps)))

(defn errors
  "Problems at :error severity."
  [zone]
  (filterv #(= :error (:zone/severity %)) (problems zone)))

(defn valid?
  "True iff `zone` has no :error-level structural problems."
  [zone]
  (empty? (errors zone)))
