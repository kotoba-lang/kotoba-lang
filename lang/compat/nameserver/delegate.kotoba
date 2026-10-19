(ns nameserver.delegate
  "Pure data transform: compute the NS + glue-A record edit-set needed to
  delegate a *subdomain* zone to this nameserver, in godaddy-dns-clj's record
  shape ({:type :name :data :ttl}) — ready for `godaddydns.dns/-upsert-
  records!` (dry-run-first per that library's own ADR-0001). This is the last
  mile that makes hosting a real TLD's namespace concrete: get a subzone
  (e.g. `ns.example.com`) delegated via NS records at the registrar, point
  the glue A record(s) at this nameserver's public IP, and `nameserver.server`
  + your zone data becomes authoritative for that subzone from the
  internet's point of view.

  Scope: this covers *subdomain* delegation through the generic records API
  that godaddydns.dns/IDns exposes. Changing an entire registered domain's
  apex nameservers (the registrar account's own 'use these nameservers for
  the whole domain' setting) is a different registrar-account operation that
  godaddy-dns-clj's IDns does not implement — out of scope here too.")

(defn delegation-records
  "`domain` — the registrar-held apex, no trailing dot (e.g. \"example.com\").
  `subdomain` — the label being delegated (e.g. \"ns\" for `ns.example.com`).
  `ns-hosts` — ordered nameserver hostnames relative to `domain`
  (e.g. [\"ns1\" \"ns2\"] -> ns1.example.com / ns2.example.com).
  `glue` — {ns-host-label -> ip-address-string} for any of `ns-hosts` that
  itself lives under `domain` (glue is only needed when the nameserver's own
  name is inside the zone it's being delegated for).

  Returns {:ns-records […] :glue-records […]}, each record shaped
  {:type :name :data :ttl}."
  [{:keys [domain subdomain ns-hosts glue ttl] :or {ttl 3600 glue {}}}]
  {:ns-records (mapv (fn [host] {:type "NS" :name subdomain :data (str host "." domain ".") :ttl ttl})
                     ns-hosts)
   :glue-records (mapv (fn [[label ip]] {:type "A" :name label :data ip :ttl ttl}) glue)})
