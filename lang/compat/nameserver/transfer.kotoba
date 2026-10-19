(ns nameserver.transfer
  "Zone transfer: AXFR (RFC 5936) and IXFR (RFC 1995).

  A zone transfer is how a secondary nameserver gets a copy of the zone. It is
  the one DNS query whose answer is *the entire zone*, which makes it both the
  most useful query for an operator and the most dangerous one to leave open —
  an unrestricted AXFR hands an attacker every hostname you have, including the
  ones you assumed nobody would guess. So this namespace makes the access
  decision a required argument rather than an option with a permissive default:
  `axfr` takes an `allow?` predicate and refuses when it is absent.

  ## Shape of an AXFR response

  Not one message. RFC 5936 §2.2: the zone is sent as a sequence of messages,
  the **first record is the SOA** and the **last record is the same SOA**, and
  everything else is in between in any order. The closing SOA is what tells the
  secondary the transfer completed — a stream cut short is otherwise
  indistinguishable from a small zone, and a secondary that accepts it will
  serve a truncated zone as authoritative.

  ## IXFR is not a smaller AXFR

  RFC 1995: the client sends the serial it already has, and the server replies
  with **only the changes** since then — as alternating delete and add sections,
  each introduced by an SOA. If the server cannot produce that difference
  (it has no history back to that serial, or the client's serial is newer than
  its own), it must **fall back to a full AXFR**, which RFC 1995 §2 defines as a
  legitimate answer to an IXFR rather than an error. Returning an empty diff
  instead is the failure that leaves a secondary permanently stale: it asked for
  changes, was told there were none, and never learns otherwise.

  Serial arithmetic is **RFC 1982 sequence-space**, not integer comparison.
  Serials are 32-bit and wrap, so `1` is *newer* than `4294967295`. A zone that
  has been updated more than 2^31 times, or one whose serial was reset, breaks
  a naive `<` comparison — and the symptom is a secondary that silently stops
  updating."
  (:require [clojure.string :as str]))

;; ── RFC 1982 serial arithmetic ────────────────────────────────────────────

(def ^:const serial-space 4294967296)          ; 2^32
(def ^:const serial-half 2147483648)           ; 2^31

(defn serial-newer?
  "Is serial `a` newer than `b` in RFC 1982 sequence space?

  Defined as `0 < (a - b) mod 2^32 < 2^31`. Note both ends are exclusive: equal
  serials are not newer, and serials exactly 2^31 apart are *undefined* — RFC
  1982 §3.2 says the comparison has no answer there, so this returns false and
  the caller falls back to AXFR rather than guessing a direction."
  [a b]
  (let [d (mod (- a b) serial-space)]
    (and (pos? d) (< d serial-half))))

(defn serial-increment
  "Advance a serial, wrapping. Used when publishing a change: a secondary only
  re-fetches when the SOA serial moves, so a zone edit that forgets this is a
  change no secondary will ever see."
  [serial]
  (mod (inc serial) serial-space))

;; ── zone helpers ──────────────────────────────────────────────────────────

(defn soa-record
  "The zone's SOA. Every transfer is framed by it, so its absence is a
  structural problem with the zone rather than a missing optional record."
  [zone]
  (first (filter #(= "SOA" (:zone/type %)) (:zone/records zone))))

(defn serial-of [zone]
  (get-in (soa-record zone) [:zone/rdata :zone/serial]))

;; ── AXFR ──────────────────────────────────────────────────────────────────

(defn axfr
  "Full zone transfer (RFC 5936).

  `allow?` is `(fn [{:keys [zone-origin peer]}] -> boolean)` and is **required**.
  There is no default, because every default is wrong: permissive leaks the
  zone, restrictive silently breaks replication an operator thought they had
  configured. Passing nil refuses.

  Returns `{:status :ok :records [...]}` — SOA first, SOA last — or
  `{:status :refused}`. Splitting the records across messages is the caller's
  job, since only it knows the transport's frame size."
  [zone {:keys [allow? peer]}]
  (let [soa (soa-record zone)]
    (cond
      (nil? soa)
      {:status :servfail :reason "zone has no SOA; a transfer cannot be framed"}

      (not (and allow? (allow? {:zone-origin (:zone/origin zone) :peer peer})))
      {:status :refused
       :reason "zone transfer not permitted for this peer"}

      :else
      (let [others (remove #(= "SOA" (:zone/type %)) (:zone/records zone))]
        {:status :ok
         :serial (serial-of zone)
         ;; The closing SOA is not decoration: it is the only end-of-transfer
         ;; marker, and a secondary that never sees it must discard everything
         ;; it received rather than serve a partial zone.
         :records (vec (concat [soa] others [soa]))}))))

;; ── IXFR ──────────────────────────────────────────────────────────────────

(defn diff
  "The record-level difference between two zone versions, as
  `{:deleted [...] :added [...]}`.

  Compares whole records — owner, type, class, TTL and rdata — because that is
  what the wire carries. A TTL-only change is a real change to a secondary and
  omitting it leaves the two servers answering with different TTLs for the same
  name, which is exactly the kind of divergence nobody notices until a cache
  behaves oddly."
  [old-zone new-zone]
  ;; DNS names are case-insensitive (RFC 4343), so the comparison key
  ;; lower-cases the owner. Without it a zone edit that only changed the case
  ;; of a name would show up as one delete and one add of the same record.
  (let [key-of (juxt #(some-> (:zone/name %) str/lower-case) :zone/type :zone/class
                     :zone/ttl :zone/rdata)
        old-set (into #{} (map key-of) (:zone/records old-zone))
        new-set (into #{} (map key-of) (:zone/records new-zone))]
    {:deleted (filterv #(not (contains? new-set (key-of %))) (:zone/records old-zone))
     :added   (filterv #(not (contains? old-set (key-of %))) (:zone/records new-zone))}))

(defn ixfr
  "Incremental transfer (RFC 1995).

  `history` maps a serial to the zone as it was at that serial. When the
  client's serial is not in it — the usual case for a server that keeps no
  history — this **falls back to a full AXFR**, which RFC 1995 §2 makes an
  explicit and legitimate response, rather than returning an empty diff that
  would leave the secondary permanently stale.

  The response shape (§4) is: current SOA, then for each step
  `[old-SOA, deleted records…, new-SOA, added records…]`, then the current SOA
  again. The alternating SOAs are how the client knows which side of the diff
  it is reading."
  [zone {:keys [client-serial history allow? peer]}]
  (let [current (serial-of zone)
        old-zone (get history client-serial)]
    (cond
      (not (and allow? (allow? {:zone-origin (:zone/origin zone) :peer peer})))
      {:status :refused :reason "zone transfer not permitted for this peer"}

      ;; Up to date: RFC 1995 §2 — reply with just the SOA and nothing else.
      (= client-serial current)
      {:status :ok :kind :up-to-date :serial current :records [(soa-record zone)]}

      ;; The client claims a serial newer than ours. Either it talked to a
      ;; different server or ours was rolled back; in both cases a diff would
      ;; be a fabrication, so send the whole zone and let the client replace.
      (serial-newer? client-serial current)
      (assoc (axfr zone {:allow? allow? :peer peer}) :kind :axfr-fallback
             :reason "client serial is newer than the server's")

      (nil? old-zone)
      (assoc (axfr zone {:allow? allow? :peer peer}) :kind :axfr-fallback
             :reason (str "no history for serial " client-serial))

      :else
      (let [{:keys [deleted added]} (diff old-zone zone)
            old-soa (soa-record old-zone)
            new-soa (soa-record zone)]
        {:status :ok
         :kind :incremental
         :serial current
         :records (vec (concat [new-soa]
                               [old-soa] (remove #(= "SOA" (:zone/type %)) deleted)
                               [new-soa] (remove #(= "SOA" (:zone/type %)) added)
                               [new-soa]))}))))

(defn transfer-qtype?
  [qtype]
  (contains? #{"AXFR" "IXFR"} qtype))

(defn split-messages
  "Chunk a transfer's records into messages of at most `max-records` each.

  RFC 5936 §2.2 permits many records per message and requires the first message
  to begin with the SOA and the last to end with it — which this preserves by
  construction, since it only cuts between records and never reorders.

  Zone transfers run over TCP (RFC 5936 §2.1: AXFR over UDP is not defined), so
  the constraint is message size rather than the 512-octet UDP ceiling; the
  caller picks a chunk size for its own buffers."
  [records max-records]
  (mapv vec (partition-all (max 1 max-records) records)))
