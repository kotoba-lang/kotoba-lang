(ns kekkai.node.magicdns
  "MagicDNS equivalent: the netmap, served as DNS.

  `judah.kekkai.` resolves to judah's overlay IP for every process on this
  machine, with no `/etc/hosts` editing and no config to drift, because the
  answer is computed from the same netmap the data plane is enforcing. That is
  the whole feature — a name that cannot disagree with reachability, since both
  read one source.

  Implemented as an `nameserver.resolver/IResolver`
  ([`org-ietf-dns`](https://github.com/kotoba-lang/org-ietf-dns)), so it composes
  with that library's real RFC 1035 server and its `chain-resolver`: tailnet names
  here, everything else refused so the chain falls through to the host's normal
  resolver. Refusing (rather than answering NXDOMAIN) outside the tailnet suffix
  is deliberate — an over-eager overlay resolver that NXDOMAINs the public
  internet is a machine-wide outage.

  Answers are computed, never cached: a netmap swap changes resolution instantly.
  The TTL we hand out is short for the same reason.

  Records served:

  | query | answer |
  |---|---|
  | `<node>.<suffix>` A | the peer's overlay IPv4 |
  | `<node>.<suffix>` AAAA | the peer's overlay IPv6, if the netmap has one |
  | `<node>.<suffix>` TXT | node id + key fingerprint, for debugging who a name is |
  | `<n>.<n>.<n>.<n>.in-addr.arpa` PTR | the node name, for logs and `ssh -v` |
  | anything else under `<suffix>` | NXDOMAIN (authoritative: we own this suffix) |
  | anything outside `<suffix>` | `:refused` (let the chain continue) |"
  (:require [clojure.string :as str]
            [kekkai.node.netmap :as netmap]
            [nameserver.resolver :as resolver]))

(def ^:const default-ttl 60)

(defn- fqdn [s] (if (str/ends-with? s ".") s (str s ".")))

(defn- suffix-of [tailnet] (fqdn tailnet))

(defn- node-label
  "\"judah.kekkai.\" under suffix \"kekkai.\" -> \"judah\". Only a single label is
   accepted: `a.b.kekkai.` is not a node, and answering for it would make the
   overlay authoritative for names it does not own."
  [qname suffix]
  (when (str/ends-with? qname suffix)
    (let [prefix (subs qname 0 (- (count qname) (count suffix)))
          labels (remove str/blank? (str/split prefix #"\."))]
      (when (= 1 (count labels)) (str/lower-case (first labels))))))

(defn- ipv4->arpa [ip]
  (str (str/join "." (reverse (str/split ip #"\."))) ".in-addr.arpa."))

(defn- rr [qname type rdata ttl]
  {:zone/name qname :zone/ttl ttl :zone/class "IN" :zone/type type :zone/rdata rdata})

(defn- fingerprint
  "First 8 hex chars of the node key — enough to tell two nodes apart in a log
   without pasting a full public key into DNS."
  [k]
  (when k (subs (str k) 0 (min 8 (count (str k))))))

(defn- self-and-peers [nm]
  (cons (:netmap/self nm) (:netmap/peers nm)))

(defn- find-node [nm label]
  (first (filter #(= label (str/lower-case (str (or (:node/id %) (:id %)))))
                 (self-and-peers nm))))

(defn- find-by-ip [nm ip]
  (first (filter #(= ip (netmap/overlay-ip %)) (self-and-peers nm))))

(defn resolve-netmap
  "The pure resolution function — `netmap-fn` returns the current netmap, so a
   swap takes effect on the next query."
  [netmap-fn tailnet ttl qname qtype]
  (let [nm (netmap-fn)
        suffix (suffix-of tailnet)
        qname (str/lower-case (fqdn qname))
        nodata {:status :nodata :aa? true :answers [] :authority [] :additional []}
        nx {:status :nxdomain :aa? true :answers [] :authority [] :additional []}
        refused {:status :refused :aa? false :answers [] :authority [] :additional []}]
    (cond
      ;; reverse lookups for the overlay range
      (str/ends-with? qname ".in-addr.arpa.")
      (if-let [node (first (filter #(and (netmap/overlay-ip %)
                                         (= qname (ipv4->arpa (netmap/overlay-ip %))))
                                   (self-and-peers nm)))]
        (if (= "PTR" qtype)
          {:status :ok :aa? true :authority [] :additional []
           :answers [(rr qname "PTR" {:zone/target (str (or (:node/id node) (:id node))
                                                        "." suffix)} ttl)]}
          nodata)
        refused)   ; not our address space — do not claim in-addr.arpa at large

      (not (str/ends-with? qname suffix)) refused

      :else
      (if-let [label (node-label qname suffix)]
        (let [node (find-node nm label)]
          (if-not node
            nx
            (let [v4 (netmap/overlay-ip node)
                  v6 (:node/overlay-ip6 node)]
              (case qtype
                "A" (if v4
                      {:status :ok :aa? true :authority [] :additional []
                       :answers [(rr qname "A" {:zone/address v4} ttl)]}
                      nodata)
                "AAAA" (if v6
                         {:status :ok :aa? true :authority [] :additional []
                          :answers [(rr qname "AAAA" {:zone/address v6} ttl)]}
                         nodata)
                "TXT" {:status :ok :aa? true :authority [] :additional []
                       :answers [(rr qname "TXT"
                                     {:zone/text (str "node=" (or (:node/id node) (:id node))
                                                      " key=" (fingerprint (netmap/peer-key node))
                                                      " status=" (or (:node/status node) "self"))}
                                     ttl)]}
                ("ANY") {:status :ok :aa? true :authority [] :additional []
                         :answers (cond-> []
                                    v4 (conj (rr qname "A" {:zone/address v4} ttl))
                                    v6 (conj (rr qname "AAAA" {:zone/address v6} ttl)))}
                nodata))))
        ;; inside our suffix but not a single-label node name
        (if (= qname suffix) nodata nx)))))

(defrecord NetmapResolver [netmap-fn tailnet ttl]
  resolver/IResolver
  (-resolve [_ qname qtype _qclass]
    (resolve-netmap netmap-fn tailnet ttl qname qtype)))

(defn netmap-resolver
  "`netmap-fn` is a 0-arity function returning the current netmap (an atom deref
   in the agent), so DNS answers follow netmap swaps with no invalidation step."
  ([netmap-fn tailnet] (netmap-resolver netmap-fn tailnet default-ttl))
  ([netmap-fn tailnet ttl] (->NetmapResolver netmap-fn tailnet ttl)))

(defn search-domain
  "What a resolver config should append so `ssh judah` works, not just
   `ssh judah.kekkai`."
  [tailnet]
  (str/replace (suffix-of tailnet) #"\.$" ""))
