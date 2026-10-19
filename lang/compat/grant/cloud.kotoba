(ns grant.cloud
  "The two remote authorities ADR-0041 names — kotobase for storage,
  murakumo for inference — expressed as decisions.

  Contract: `resources/aiueos/cloud_contract.edn`. Nothing here opens a socket.
  A plan is a request this policy would allow, and an admission is a verdict
  about a response a provider already received. Transport is provider
  mechanism, exactly as in `grant.ota`.

  ## The holes this namespace closes

  **A CID is a claim about bytes, and a response is bytes.** `plan-block-read`
  carries the digest the CID commits to, and `admit-block` refuses a response
  whose observed digest differs. A provider that cannot say what it hashed gets
  `:digest-missing` — silence is not a pass, because a check that could not run
  must not return the value of a check that ran and found nothing wrong.

  **kotobase's `PUT /ipfs/:cid` accepts raw CIDv1 only** (root ADR-2608148200).
  An object whose identity CID is dag-cbor is archived under the *raw* CID of
  the same bytes. Refusing that before the request is the difference between a
  decision and a collected 400.

  **The model alias is a redirect.** `murakumo-main` resolves through a mutable
  KV entry to an endpoint, so an allowlist that admitted `api.murakumo.cloud`
  would otherwise authorise whatever host that entry names next. `admit-model`
  re-checks the resolved endpoint against the same allowlist that admitted the
  resolver.

  **`alias-for` is not the model to request.** Sending the concrete id the alias
  currently points at freezes this machine at whatever was serving the moment it
  resolved — precisely what root ADR-2607173100 forbids. The request carries the
  alias; an override that equals `alias-for` is refused rather than honoured.

  ## What this does not do

  It does not put aiueos on the network. The bare-metal profile has no DNS, no
  TLS and no HTTP client (ADR-0041's gap ledger, steps 1–5); this is the
  decision layer those steps will be wired underneath, and it is checkable
  before any of them exist.

  It does not read a stream. A `text/event-stream` response is refused by name
  (`:response-streaming-unsupported`) rather than reported as the wrong
  document, because \"we do not do this yet\" and \"the server sent us
  something wrong\" are different sentences to the person reading the receipt."
  (:require [grant.anchors :as anchors]
            [grant.json :as json]
            [grant.net :as net]
            [grant.policy :as policy]
            [kotoba.lang.text :as str]))

(def default-config
  "Origins and the alias name. Overridable per policy map — a deployment that
  reaches a different kotobase or a different murakumo changes these, not the
  code. `:model-alias` is a name, never a model id."
  {:aiueos.cloud/storage-origin   "https://kotobase.net"
   :aiueos.cloud/inference-origin "https://api.murakumo.cloud"
   :aiueos.cloud/model-alias      "murakumo-main"})

(def deny-reasons
  "Reasons this namespace produces. `grant.net`'s own denial passes through
  `perform` unchanged."
  #{:cid-unparsable :cid-not-raw :cid-not-sha256
    :origin-not-allowed :insecure-transport :plan-not-allowed
    :response-not-ok :response-upstream-fault :digest-missing :digest-mismatch
    :payload-digest-mismatch
    :write-unauthorized :write-forbidden :write-digest-rejected
    :alias-unresolved :resolved-endpoint-not-allowed
    :model-is-alias-target :messages-missing
    :response-unmeasured :body-unparsable :completion-empty
    :response-shape-unknown :response-shape-mismatch
    :response-streaming-unsupported
    :no-trust-anchors :peer-unmeasured :peer-not-pinned
    :trust-anchors-unbound :anchor-binding-malformed
    :peer-host-unknown :host-not-pinned :peer-pinned-to-other-host
    :peer-pin-expired :peer-pin-window-unevaluated})

(def unmeasurable-reasons
  "The refusals that mean **nobody has been shown to be wrong**: the request
  reached an authority that could not answer it right now.

  Named here rather than in the gate, because which refusals are retryable is
  a property of what the reason *means*, and the meaning is this namespace's.
  A caller mapping verdicts onto exit codes reads this set instead of keeping
  its own list, which would drift the day a reason is added.

  `:response-unmeasured` is no answer at all; `:response-upstream-fault` is an
  answer that says the peer is having a bad minute. Neither is evidence that a
  pin, a digest or an allowlist is wrong, and a gate that spends exit 1 on them
  teaches its operator to investigate the weather."
  #{:response-unmeasured :response-upstream-fault})

(defn upstream-fault-status?
  "Whether STATUS is the authority failing rather than refusing.

  5xx, all of it. Every host this plane reaches is Cloudflare-fronted, so the
  range covers both the ordinary 502/503/504 and Cloudflare's own 520–527
  origin errors, which say the edge could not reach the origin — which is the
  most literal possible form of \"could not answer\".

  **429 is deliberately not here.** It is retryable, and it is still a refusal:
  the authority is answering about *this caller's* behaviour, which is a fact
  about this machine and not about the weather. A gate that shrugged at rate
  limiting would hide a misconfigured loop behind the same exit code as a
  network outage."
  [status]
  (boolean (and (integer? status) (<= 500 status 599))))

(defn- cfg [policy k]
  (get policy k (get default-config k)))

(defn- deny [reason extra]
  (merge {:aiueos/decision :deny :aiueos.cloud/reason reason} extra))

(defn- allow [extra]
  (merge {:aiueos/decision :allow} extra))

(defn allowed?
  "Whether DECISION is an allow. Every plan and admission answers this."
  [decision]
  (= :allow (:aiueos/decision decision)))

(defn- status-refusal
  "The refusal for a STATUS that is not one this request accepts.

  One function, so a new request kind cannot classify 503 differently from the
  one next to it. A 5xx is `:response-upstream-fault` and everything else is
  `:response-not-ok`; both carry the status, because the number is what the
  operator will look at either way."
  [status extra]
  (deny (if (upstream-fault-status? status) :response-upstream-fault :response-not-ok)
        (assoc extra :aiueos.cloud/status status)))

;; ── CIDv1, narrowly ────────────────────────────────────────────────────────
;;
;; aiueos is dependency-minimal by invariant (README: deps.edn carries
;; `security` + Chicory only; enforcement layers import aiueos, never the
;; reverse). `kotoba-lang/io-multiformats` is the workspace authority for this
;; format and is where a general codec belongs; what is inlined here is the one
;; shape kotobase and the IPFS gateways emit — multibase base32-lower CIDv1 —
;; and it refuses everything else rather than guessing.

(def ^:private base32-lower "abcdefghijklmnopqrstuvwxyz234567")

(def ^:private base32-index
  (into {} (map-indexed (fn [i c] [(str c) i])) base32-lower))

(def ^:private hex-digits "0123456789abcdef")

(defn- base32-decode
  "RFC 4648 base32, lowercase, unpadded → vector of unsigned bytes, or nil.
  Leftover bits must be zero: a trailing partial group with data in it is a
  malformed encoding, not a truncated one to be tolerated."
  [s]
  (loop [i 0, acc 0, bits 0, out []]
    (if (= i (count s))
      (when (and (< bits 5) (zero? acc)) out)
      (if-let [v (get base32-index (subs s i (inc i)))]
        (let [acc (bit-or (bit-shift-left acc 5) v)
              bits (+ bits 5)]
          (if (>= bits 8)
            (let [rest-bits (- bits 8)]
              (recur (inc i)
                     (bit-and acc (dec (bit-shift-left 1 rest-bits)))
                     rest-bits
                     (conj out (bit-and (unsigned-bit-shift-right acc rest-bits) 0xff))))
            (recur (inc i) acc bits out)))
        nil))))

(defn- hex-of [bytes]
  (str/join (mapcat (fn [b]
                      [(nth hex-digits (unsigned-bit-shift-right b 4))
                       (nth hex-digits (bit-and b 0xf))])
                    bytes)))

(def ^:private codec-names
  "Only the single-byte codes. A codec whose varint continues into a second
  byte would shift every field after it, so `cid-info` refuses that string
  outright rather than reading the multihash out of the wrong offset."
  {0x55 :raw 0x70 :dag-pb 0x71 :dag-cbor})

(def ^:private sha2-256 0x12)

(defn cid-info
  "Decode CID. Returns `{:version 1 :codec <int> :codec-name <kw> :multihash
  <int> :digest-hex <string-or-nil>}`, or nil when the string is not a
  base32-lower CIDv1 at all.

  A non-sha2-256 multihash decodes to a nil `:digest-hex` rather than a nil
  result: the caller then denies with `:cid-not-sha256`, which says something
  different from `:cid-unparsable`."
  [cid]
  (let [s (str cid)]
    (when (and (> (count s) 1) (str/starts-with? s "b"))
      (when-let [bytes (base32-decode (subs s 1))]
        (when (and (>= (count bytes) 4)
                   (= 1 (nth bytes 0))
                   (< (nth bytes 1) 0x80))
          (let [codec (nth bytes 1)
                mh (nth bytes 2)
                mh-len (nth bytes 3)
                digest (subvec bytes 4)]
            {:version 1
             :codec codec
             :codec-name (get codec-names codec :unknown)
             :multihash mh
             :digest-hex (when (and (= mh sha2-256)
                                    (= mh-len 32)
                                    (= (count digest) 32))
                           (hex-of digest))}))))))

;; ── who is on the other end ────────────────────────────────────────────────
;;
;; A cloud-premised machine's whole authority story rests on reaching the right
;; host. Until now that rested on the platform's default trust store: several
;; hundred anchors chosen by whoever packaged the runtime, any one of which
;; could vouch for anything. That is a reasonable default for a browser, which
;; must reach hosts nobody enumerated in advance. It is the wrong default here,
;; because this machine talks to exactly two authorities and both are known
;; before it boots.

;; ── the pin set is host-bound, and a rotation is a window ──────────────────
;;
;; `:aiueos.cloud/trust-anchors` is a map from **host** to the pins this policy
;; accepts *from that host*:
;;
;;     {"kotobase.net"        {:pins #{"5060…"} :measured "2026-08-21"}
;;      "infer.murakumo.cloud" {:pins     #{"新…"}
;;                              :previous #{"旧…"}
;;                              :accept-previous-until-ms 1767225600000}}
;;
;; A bare collection under a host — `{"kotobase.net" #{"5060…"}}` — is the same
;; binding with no rotation in progress, and is normalised to the record form.
;; That is not a second shape in the sense that matters: the security property
;; is that the key is bound to a host, and both forms bind it.
;;
;; The rotation fields are `grant.anchors`' vocabulary, evaluated by
;; `grant.anchors/usable-anchors` — the same function the release-borne plane
;; uses. A rotation is therefore *the same act* in both places: ship the new
;; pin alongside the old, keep the old usable for a stated window, and let the
;; clock retire it. Nothing here re-implements the overlap rule, because two
;; implementations of "which keys work today" is how one of them ends up wrong.

(defn- hex-pins [pins]
  (set (map #(str/lower (str/trim (str %))) pins)))

(defn- normalize-binding [v]
  (if (map? v)
    {:pins (hex-pins (:pins v))
     :previous (hex-pins (:previous v))
     :accept-previous-until-ms (:accept-previous-until-ms v)}
    {:pins (hex-pins v) :previous #{}}))

(defn anchor-bindings
  "POLICY's declared anchors, normalised.

  Returns `{:shape :host-bound|:unbound|:none :by-host {host binding} :pins
  #{hex} :malformed [pin…]}`.

  `:none` is an operator who has not said. `:unbound` is the flat set ADR-0044
  shipped — a bare collection of pins with no host beside them — which is
  **accepted and marked**, never guessed at; see `admit-peer`. `:malformed`
  collects pins that are not 64 lowercase hex characters, using
  `grant.anchors/pin-well-formed?` so the two planes agree on what a pin is: a
  malformed pin can never match a measured key, so a policy carrying one is a
  policy that partly cannot work while looking entirely valid."
  [policy]
  (let [declared (:aiueos.cloud/trust-anchors policy)]
    (cond
      (empty? declared)
      {:shape :none :by-host {} :pins #{} :malformed []}

      (map? declared)
      (let [by-host (into {} (map (fn [[h v]] [(str/lower (str/trim (str h)))
                                               (normalize-binding v)]))
                          declared)
            all (into #{} (mapcat (fn [[_ b]] (into (:pins b) (:previous b)))) by-host)]
        {:shape :host-bound
         :by-host by-host
         :pins all
         :malformed (vec (sort (remove anchors/pin-well-formed? all)))})

      :else
      (let [pins (hex-pins declared)]
        {:shape :unbound
         :by-host {}
         :pins pins
         :malformed (vec (sort (remove anchors/pin-well-formed? pins)))}))))

(defn trust-anchors
  "Every pin this policy names, flattened, as a set.

  A **reporting** view — how many keys are written down, and are they all
  well-formed — and not a decision. `admit-peer` never asks this question,
  because the answer to \"is this key in the policy\" is the one that let host
  A's key work for host B."
  [policy]
  (:pins (anchor-bindings policy)))

(defn usable-pins
  "The pins POLICY accepts from HOST **right now**.

  During a rotation's overlap window that is the union of the host's current
  and previous pins; after it, the current pins alone. The union is computed by
  `grant.anchors/usable-anchors`, which is the release-borne plane's own rule.

  `:aiueos.cloud/now-ms` in policy is the clock. Without it a window cannot be
  evaluated, so the previous pins are **not** accepted — the direction that
  refuses rather than admits, which is the only safe way for a check that could
  not run to fall."
  [policy host]
  (let [{:keys [by-host]} (anchor-bindings policy)
        binding (get by-host (str/lower (str/trim (str host))))]
    (anchors/usable-anchors {:current-anchors (:pins binding)
                             :previous-anchors (:previous binding)
                             :accept-previous-until-ms (:accept-previous-until-ms binding)
                             :now-ms (:aiueos.cloud/now-ms policy)})))

(defn- hosts-pinning
  "Which **other** hosts POLICY pins SPKI to. Used only to explain a refusal.

  EXCEPT is the host being judged: its own retired key is a closed rotation
  window, not somebody else's key, and reporting it as the latter would send
  an operator hunting an attack that is really a schedule."
  [bindings spki except]
  (sort (keep (fn [[host b]]
                (when (and (not= host except)
                           (or (contains? (:pins b) spki) (contains? (:previous b) spki)))
                  host))
              (:by-host bindings))))

(defn- window-state
  "`:none`, `:open`, `:closed` or `:unevaluated` for a host binding's
  rotation window.

  `:unevaluated` covers both ways the question cannot be answered: no clock was
  supplied, and a `:previous` set with no deadline — a rotation with no end is
  not a rotation. Either way the previous pins are not honoured, and the
  refusal says which, because a window that could not be evaluated must not
  report the value of one that was evaluated and had closed."
  [binding now-ms]
  (let [until (:accept-previous-until-ms binding)]
    (cond
      (empty? (:previous binding)) :none
      (or (nil? until) (nil? now-ms)) :unevaluated
      (<= now-ms until) :open
      :else :closed)))

(defn anchors-declared?
  "Whether this policy names any peer key at all. Checked before the socket,
  so a machine with no anchors does not open a connection it could not have
  judged."
  [policy]
  (not= :none (:shape (anchor-bindings policy))))

(defn admit-peer
  "Judge the peer a connection reached. PEER is `{:spki-sha256 <hex> :host
  <name>}` — the SHA-256 of the leaf certificate's SubjectPublicKeyInfo as the
  provider measured it, and the host the provider was trying to reach.

  The pin is over the **key**, not the certificate, so an authority that
  renews its certificate with the same key keeps working and one that changes
  key does not — which is the event worth noticing.

  ## The host is half the question

  Until 2026-08-22 it asked only whether the key was in the set, so **any**
  pinned key was accepted from **any** allowed host: with three hosts in one
  policy, an attacker able to answer for one of them could answer for all
  three, and the mapping from key to host existed only in a comment. The host
  is now part of the verdict, and presenting host A's key for host B has its
  own refusal — `:peer-pinned-to-other-host` — because it is the one refusal
  here that cannot be a rotation.

  ## The flat set, deliberately

  A policy whose anchors are a bare collection is `:unbound`. It is accepted,
  and every verdict from it carries `:aiueos.cloud/anchor-binding :unbound`, so
  an unbound acceptance is visible on the line of every receipt rather than
  being the thing nobody can see. It is accepted because the release-borne
  anchor document (ADR-0045/0046) carries a device-wide pin list with no host
  field, and a device that boots from one has no other way to hold pins;
  refusing it would strand exactly the machines the anchors plane exists for.
  A deployment that can do better says `:aiueos.cloud/require-host-bound-anchors?
  true` and gets `:trust-anchors-unbound` if it ever regresses — which is what
  the shipped live policy does.

  ## Seven refusals, kept apart because seven people fix them differently

  - `:no-trust-anchors`      — nothing was declared. Not permission;
  - `:anchor-binding-malformed` — a declared pin cannot be a SHA-256, so part
    of this policy can never match anything;
  - `:trust-anchors-unbound` — a flat set where the deployment requires hosts;
  - `:peer-unmeasured`       — the provider did not report a key;
  - `:peer-host-unknown`     — the caller did not say which host it reached, so
    the host half of the question could not be asked at all;
  - `:host-not-pinned`       — this policy names no pins for that host;
  - `:peer-pin-expired`      — this host's **own** retired key, offered after
    the overlap window closed. A schedule, not an attack: either the authority
    has not finished rotating or the window was too short;
  - `:peer-pin-window-unevaluated` — the same key, with no clock to judge the
    window by. Refused, and named apart from `:peer-pin-expired` because
    \"the window closed\" and \"nobody could tell whether it had\" are different
    facts and only one of them is about the authority;
  - `:peer-pinned-to-other-host` — the key is pinned, for somebody else;
  - `:peer-not-pinned`       — a key nothing in this policy names.

  Only the last two are an attack shape. The rest are a machine that must not
  proceed as though it had checked."
  [policy peer]
  (let [bindings (anchor-bindings policy)
        measured (str/lower (str/trim (str (:spki-sha256 peer))))
        host (str/lower (str/trim (str (:host peer))))]
    (cond
      (= :none (:shape bindings))
      (deny :no-trust-anchors {})

      (seq (:malformed bindings))
      (deny :anchor-binding-malformed {:aiueos.cloud/malformed-pins (:malformed bindings)})

      (and (= :unbound (:shape bindings))
           (true? (:aiueos.cloud/require-host-bound-anchors? policy)))
      (deny :trust-anchors-unbound {})

      (str/blank? measured)
      (deny :peer-unmeasured {})

      (= :unbound (:shape bindings))
      (if (contains? (:pins bindings) measured)
        (allow {:aiueos.cloud/peer-spki measured
                :aiueos.cloud/anchor-binding :unbound})
        (deny :peer-not-pinned {:aiueos.cloud/observed-spki measured
                                :aiueos.cloud/anchor-binding :unbound}))

      (str/blank? host)
      (deny :peer-host-unknown {:aiueos.cloud/observed-spki measured})

      :else
      (let [binding (get (:by-host bindings) host)
            now-ms (:aiueos.cloud/now-ms policy)
            window (window-state binding now-ms)
            pins (usable-pins policy host)
            elsewhere (hosts-pinning bindings measured host)
            base {:aiueos.cloud/host host
                  :aiueos.cloud/observed-spki measured}]
        (cond
          (and (empty? pins) (empty? (:previous binding)))
          (deny :host-not-pinned base)

          (contains? pins measured)
          (allow {:aiueos.cloud/peer-spki measured
                  :aiueos.cloud/host host
                  :aiueos.cloud/anchor-binding :host
                  :aiueos.cloud/rotation-window window})

          (and (contains? (:previous binding) measured) (= :unevaluated window))
          (deny :peer-pin-window-unevaluated
                (assoc base :aiueos.cloud/accept-previous-until-ms
                       (:accept-previous-until-ms binding)))

          (contains? (:previous binding) measured)
          (deny :peer-pin-expired
                (assoc base :aiueos.cloud/accept-previous-until-ms
                       (:accept-previous-until-ms binding)
                       :aiueos.cloud/now-ms now-ms))

          (seq elsewhere)
          (deny :peer-pinned-to-other-host
                (assoc base :aiueos.cloud/pinned-for (vec elsewhere)))

          :else
          (deny :peer-not-pinned base))))))

;; ── storage: kotobase ──────────────────────────────────────────────────────

(defn- join-url [origin path]
  (str (if (str/ends-with? origin "/") (subs origin 0 (dec (count origin))) origin)
       path))

(defn- url-path
  "The path component of URL, or \"\" when it names only an origin. Written
  here rather than taken from a URL library because `grant.cloud` is `.cljc`
  and the two runtimes disagree about what a URL object is; the only question
  asked of it is whether an authority already said where to POST."
  [url]
  (let [after-scheme (str/replace-first (str url) #"^[a-zA-Z][a-zA-Z0-9+.-]*://" "")
        slash (str/index-of after-scheme "/")]
    (if slash (subs after-scheme slash) "")))

(defn- insecure-permitted?
  "Whether URL sits under an origin the operator explicitly marked as allowed
  to be plaintext. The escape hatch exists because a loopback test server has
  no certificate, and it is spelled out in policy so it is visible in the
  deployment rather than hidden in the code."
  [policy url]
  (boolean (some #(str/starts-with? url (str %))
                 (:aiueos.cloud/allow-insecure-origins policy))))

(defn- secure? [policy url]
  (or (str/starts-with? url "https://") (insecure-permitted? policy url)))

(defn- with-allowed-url
  "Gate URL through TLS and the same allowlist every provider shares, then hand
  it to BUILD. One place, so a new request kind cannot forget either check.

  `grant.policy/net-url-allowed?` matches on host: it strips the scheme and the
  port before comparing, so an entry admitting `kotobase.net` admits plaintext
  to `kotobase.net` just as readily. For a machine whose storage and inference
  authorities are both remote, plaintext is not a degraded mode, it is a
  different threat model — so the transport is checked here rather than left to
  whoever wrote the allowlist entry."
  [policy url build]
  (cond
    (not (secure? policy url)) (deny :insecure-transport {:aiueos.cloud/url url})
    (not (policy/net-url-allowed? policy url)) (deny :origin-not-allowed {:aiueos.cloud/url url})
    :else (allow (build url))))

(defn plan-block-read
  "Plan a block read of CID from the storage authority. Any codec may be read;
  the plan carries the digest the CID commits to so `admit-block` can check the
  bytes that come back."
  [policy cid]
  (if-let [info (cid-info cid)]
    (if-let [digest (:digest-hex info)]
      (with-allowed-url policy (join-url (cfg policy :aiueos.cloud/storage-origin)
                                         (str "/ipfs/" cid))
        (fn [url] {:aiueos.cloud/request {:method :get :url url}
                   :aiueos.cloud/cid (str cid)
                   :aiueos.cloud/codec (:codec-name info)
                   :aiueos.cloud/expect-digest digest}))
      (deny :cid-not-sha256 {:aiueos.cloud/cid (str cid)
                             :aiueos.cloud/multihash (:multihash info)}))
    (deny :cid-unparsable {:aiueos.cloud/cid (str cid)})))

(defn plan-block-write
  "Plan a block write of CID. `PUT /ipfs/:cid` takes raw CIDv1 only, so a
  dag-cbor identity CID is refused here rather than at the server: the Location
  of those bytes is the raw CID of the same bytes, and the caller has to say
  which one it means (root ADR-2608148200)."
  [policy cid]
  (if-let [info (cid-info cid)]
    (cond
      (not= :raw (:codec-name info))
      (deny :cid-not-raw {:aiueos.cloud/cid (str cid)
                          :aiueos.cloud/codec (:codec-name info)})

      (nil? (:digest-hex info))
      (deny :cid-not-sha256 {:aiueos.cloud/cid (str cid)
                             :aiueos.cloud/multihash (:multihash info)})

      :else
      (with-allowed-url policy (join-url (cfg policy :aiueos.cloud/storage-origin)
                                         (str "/ipfs/" cid))
        (fn [url] {:aiueos.cloud/request {:method :put :url url}
                   :aiueos.cloud/cid (str cid)
                   :aiueos.cloud/codec :raw
                   :aiueos.cloud/expect-digest (:digest-hex info)})))
    (deny :cid-unparsable {:aiueos.cloud/cid (str cid)})))

(defn admit-block
  "Judge a RESPONSE against the PLAN that asked for it. RESPONSE is
  `{:status <int> :digest-hex <string>}` — the provider hashed the bytes it
  received, because this namespace does not hash (`grant.signing/sha256-hex`
  is JVM-only, and a second canonicaliser would be its own hazard).

  A response with no `:digest-hex` is denied. The provider not having measured
  is a different fact from the bytes being right, and only one of them is a
  pass."
  [plan response]
  (cond
    (not (allowed? plan))
    (deny :plan-not-allowed {:aiueos.cloud/plan-reason (:aiueos.cloud/reason plan)})

    (not= 200 (:status response))
    (status-refusal (:status response) {:aiueos.cloud/cid (:aiueos.cloud/cid plan)})

    (str/blank? (str (:digest-hex response)))
    (deny :digest-missing {:aiueos.cloud/cid (:aiueos.cloud/cid plan)})

    (not= (:aiueos.cloud/expect-digest plan) (:digest-hex response))
    (deny :digest-mismatch {:aiueos.cloud/cid (:aiueos.cloud/cid plan)
                            :aiueos.cloud/expect-digest (:aiueos.cloud/expect-digest plan)
                            :aiueos.cloud/observed-digest (:digest-hex response)})

    :else
    (allow {:aiueos.cloud/cid (:aiueos.cloud/cid plan)
            :aiueos.cloud/digest (:digest-hex response)})))

(defn admit-write-payload
  "Judge the bytes a caller is about to write under PLAN's CID, given the
  digest the provider measured over them.

  A CID is a claim about bytes; `PUT /ipfs/:cid` is this machine making that
  claim to a store that will hold it. Checking it before the socket is not
  belt-and-braces — kotobase would reject the mismatch, but by then the wrong
  bytes have left the machine and the operator has a 400 to interpret instead
  of a refusal to read.

  A provider that did not measure gets `:digest-missing`, for the same reason
  `admit-block` gives it: no measurement is not the same fact as a good one."
  [plan payload-digest-hex]
  (let [measured (str payload-digest-hex)]
    (cond
      (not (allowed? plan))
      (deny :plan-not-allowed {:aiueos.cloud/plan-reason (:aiueos.cloud/reason plan)})

      (str/blank? measured)
      (deny :digest-missing {:aiueos.cloud/cid (:aiueos.cloud/cid plan)})

      (not= (:aiueos.cloud/expect-digest plan) measured)
      (deny :payload-digest-mismatch
            {:aiueos.cloud/cid (:aiueos.cloud/cid plan)
             :aiueos.cloud/expect-digest (:aiueos.cloud/expect-digest plan)
             :aiueos.cloud/observed-digest measured})

      :else
      (allow {:aiueos.cloud/cid (:aiueos.cloud/cid plan)
              :aiueos.cloud/digest measured}))))

(defn admit-write
  "Judge the RESPONSE to a block write. `{:status <int>}`.

  200, 201 and 204 are accepted: a content-addressed store has nothing to say
  about a block it already holds, so \"stored\" and \"already stored\" are the
  same outcome and stores spell it differently.

  ## Three refusals kept apart because three people fix them differently

  kotobase's block-write gate answers with statuses that mean unrelated things,
  and collapsing them into `:response-not-ok` would hand every one of them to
  the same puzzled operator:

  - **401** `:write-unauthorized` — no bearer token, or one the authority does
    not hold. Measured against the live `kotobase.net` on 2026-08-21, this is
    what an un-credentialed write gets;
  - **403** `:write-forbidden` — the authority has the feature switched off.
    Nothing the caller supplies will change it;
  - **422** `:write-digest-rejected` — the authority hashed the body and got a
    different digest from the CID. **This one should be unreachable**, because
    `admit-write-payload` refuses those bytes before the socket. Reaching it
    means this machine and the store disagree about what they hashed, which is
    worth a name of its own rather than a number in a field.

  All three are refusals by the authority, not faults: the request completed
  and was answered."
  [plan response]
  (let [status (:status response)
        extra {:aiueos.cloud/status status
               :aiueos.cloud/cid (:aiueos.cloud/cid plan)}]
    (cond
      (not (allowed? plan))
      (deny :plan-not-allowed {:aiueos.cloud/plan-reason (:aiueos.cloud/reason plan)})

      (nil? status) (deny :response-unmeasured {:aiueos.cloud/cid (:aiueos.cloud/cid plan)})
      (= 401 status) (deny :write-unauthorized extra)
      (= 403 status) (deny :write-forbidden extra)
      (= 422 status) (deny :write-digest-rejected extra)
      (not (contains? #{200 201 204} status)) (status-refusal status extra)

      :else
      (allow {:aiueos.cloud/cid (:aiueos.cloud/cid plan)
              :aiueos.cloud/status status}))))

;; ── inference: murakumo ────────────────────────────────────────────────────

(defn plan-model-resolve
  "Plan the alias lookup. The alias name is configuration; the model id it
  points at is never written down here."
  [policy]
  (let [alias (cfg policy :aiueos.cloud/model-alias)]
    (with-allowed-url policy (join-url (cfg policy :aiueos.cloud/inference-origin)
                                       (str "/infer/models/" alias))
      (fn [url] {:aiueos.cloud/request {:method :get :url url}
                 :aiueos.cloud/alias alias}))))

(defn admit-model
  "Judge the alias RESOLUTION — `{:endpoint <url> :alias-for <model-id>}`.

  The resolved endpoint is re-checked against the allowlist. The entry is
  mutable and lives outside this machine, so admitting the resolver is not
  admitting whatever the resolver names.

  ## When the resolution names no endpoint

  A resolution with no endpoint is `:alias-unresolved`, and that is the
  default. It is worth saying why the obvious convenience is not the default:
  the murakumo worker resolves the alias server-side, so a caller may POST to
  the inference origin with `model: \"murakumo-main\"` and never need an
  endpoint at all — which makes \"treat a missing endpoint as the configured
  origin\" a *correct* reading and a **silent** one. Silent is the problem. A
  machine that reaches an origin because a field was absent has been authorised
  by an omission.

  So the fallback exists and is off. `:aiueos.cloud/endpoint-from-origin?` in
  policy turns it on, and the admission records
  `:aiueos.cloud/endpoint-source` — `:resolved` or `:configured-origin` — so a
  receipt says which of the two happened rather than showing an endpoint and
  leaving the reader to assume the authority named it."
  [policy resolution]
  (let [alias (cfg policy :aiueos.cloud/model-alias)
        resolved (str (:endpoint resolution))
        from-origin? (true? (:aiueos.cloud/endpoint-from-origin? policy))
        source (cond (not (str/blank? resolved)) :resolved
                     from-origin? :configured-origin
                     :else :absent)
        endpoint (if (= :configured-origin source)
                   (str (cfg policy :aiueos.cloud/inference-origin))
                   resolved)]
    (cond
      (str/blank? endpoint)
      (deny :alias-unresolved {:aiueos.cloud/alias alias})

      (not (secure? policy endpoint))
      (deny :insecure-transport {:aiueos.cloud/alias alias
                                 :aiueos.cloud/url endpoint})

      (not (policy/net-url-allowed? policy endpoint))
      (deny :resolved-endpoint-not-allowed {:aiueos.cloud/alias alias
                                            :aiueos.cloud/url endpoint})

      :else
      (allow {:aiueos.cloud/endpoint-source source
              :aiueos.cloud/model {:alias alias
                                   :endpoint endpoint
                                   :endpoint-source source
                                   :alias-for (:alias-for resolution)}}))))

(defn admit-resolution
  "Judge the RESPONSE to `plan-model-resolve` and, if it is one, the resolution
  inside it. `{:status <int> :body <string>}`.

  The translation from the authority's JSON to the two fields `admit-model`
  reads happens **here**, narrowly and by name. Keywordising a remote
  authority's object keys would let it decide what keywords this machine holds;
  reading exactly `\"endpoint\"` and `\"alias-for\"` means a field this machine
  does not know about cannot become one it acts on."
  [policy plan response]
  (cond
    (not (allowed? plan))
    (deny :plan-not-allowed {:aiueos.cloud/plan-reason (:aiueos.cloud/reason plan)})

    (nil? (:status response))
    (deny :response-unmeasured {:aiueos.cloud/alias (:aiueos.cloud/alias plan)})

    (not= 200 (:status response))
    (status-refusal (:status response) {:aiueos.cloud/alias (:aiueos.cloud/alias plan)})

    :else
    (let [body (json/read-json (str (:body response)))]
      (if (json/failed? body)
        (deny :body-unparsable {:aiueos.cloud/alias (:aiueos.cloud/alias plan)
                                :grant.json/error (json/error-of body)})
        (admit-model policy {:endpoint (get body "endpoint")
                             :alias-for (get body "alias-for")})))))

(def response-shapes
  "Which answer shape an inference endpoint speaks, keyed by the path it is
  reached at.

  There are two, they are not compatible, and **both are live**. The alias
  `murakumo-main` resolves onto `infer.murakumo.cloud/v1/chat/completions`,
  which answers the OpenAI chat-completions shape; `api.murakumo.cloud/v1/messages`
  is the Anthropic-shaped surface in front of the same fleet. Measured
  2026-08-22, both.

  The alternative — one reader lenient enough to swallow either — is the thing
  this namespace exists to refuse. A parser that accepts both shapes cannot
  tell \"the model returned nothing\" from \"this is not the document I asked
  for\", because in both cases the key it wanted is absent. So the *plan*
  records which shape it expects, from the path it is about to request, and
  `admit-inference` reads exactly that one."
  {"/v1/messages" :messages-v1
   "/v1/chat/completions" :chat-completions-v1})

(def streaming-content-type
  "The media type a server-sent-event stream arrives as. Both inference
  surfaces emit it when `stream: true` is asked for; this plane never asks."
  "text/event-stream")

(defn streaming-response?
  "Whether RESPONSE is a server-sent-event stream rather than a document.

  The content type decides when the provider reported one. When it did not,
  the body's own framing does: an SSE payload begins with an `event:`, `data:`,
  `id:` or `retry:` field, and a JSON document cannot.

  Sniffing the body is a fallback and is written as one, because a server that
  streams without saying so is exactly the case where the header cannot be
  relied on — and getting this wrong in the lenient direction produces
  `:body-unparsable`, which reads as \"the authority is broken\" when the truth
  is \"this client does not implement the thing it was sent\"."
  [response]
  (let [ct (str/lower (str (:content-type response)))
        body (str/triml (str (:body response)))]
    (boolean (or (str/includes? ct streaming-content-type)
                 (re-find #"^(?:event|data|id|retry):" body)))))

(def response-shapes-by-name
  "The shapes `admit-inference` can read. A plan naming anything else is
  refused before the body is looked at: this machine cannot judge a document
  whose shape it was never told, and guessing is what `response-shapes` exists
  to stop."
  (set (vals response-shapes)))

(defn plan-inference
  "Plan a request against the admitted MODEL. OPTS carries `:messages` and may
  carry `:model-override`.

  ## The endpoint is a URL, not always an origin

  `/v1/messages` is appended only when the admitted endpoint is a bare origin.
  A resolution that already names a path — measured 2026-08-21, the live
  `murakumo-main` entry named `https://infer.murakumo.cloud/v1/chat/completions`
  — **is** the request URL, because appending to it produces an address the
  authority never named. The plan records which happened in
  `:aiueos.cloud/endpoint-carries-path?`; either way the URL goes through the
  same allowlist and transport check.

  The request's model is the alias. An override is honoured — an operator
  pinning a specific model is legitimate, and is the first step of the root
  resolution order — except for the one override that never is: the id the
  alias currently resolves to. Pinning to `alias-for` looks like precision and
  is a snapshot, and it stops this machine following the fleet."
  [policy model opts]
  (let [messages (:messages opts)
        override (:model-override opts)
        endpoint (str (:endpoint model))
        carries-path? (not (contains? #{"" "/"} (url-path endpoint)))
        url (if carries-path? endpoint (join-url endpoint "/v1/messages"))
        shape (or (:aiueos.cloud/response-shape policy)
                  (get response-shapes (url-path url) :unknown))]
    (cond
      (empty? messages)
      (deny :messages-missing {:aiueos.cloud/alias (:alias model)})

      (and (some? override) (= override (:alias-for model)))
      (deny :model-is-alias-target {:aiueos.cloud/alias (:alias model)
                                    :aiueos.cloud/model-override override})

      :else
      (with-allowed-url policy url
        (fn [url] {:aiueos.cloud/request
                   {:method :post
                    :url url
                    :body (cond-> {:model (or override (:alias model))
                                   :messages (vec messages)}
                            (:max-tokens opts) (assoc :max_tokens (:max-tokens opts)))}
                   :aiueos.cloud/alias (:alias model)
                   :aiueos.cloud/endpoint-carries-path? carries-path?
                   :aiueos.cloud/response-shape shape
                   :aiueos.cloud/pinned? (some? override)})))))

(defn- read-completion
  "Read BODY as SHAPE. Returns `{:present? :text :stop}`.

  `:present?` is about the *container*, not the text: whether the document has
  the array this shape puts completions in at all. That distinction is the
  whole reason the shape is declared rather than sniffed — an absent container
  means the authority sent a different kind of document, and an empty string
  inside a present container means the model said nothing. Those are different
  events with different fixes, and a lenient reader reports them identically.

  Reasoning fields are deliberately not read. A model that spent its whole
  budget thinking and emitted no answer produced no completion; counting the
  thinking would turn that into a pass. Measured 2026-08-22, that is exactly
  what `infer.murakumo.cloud` returns at `max_tokens: 8` — `content: \"\"`,
  a full `reasoning_content`, and `finish_reason: \"length\"`."
  [shape body]
  (if-not (map? body)
    {:present? false}
    (case shape
      :messages-v1
      (let [parts (get body "content")]
        {:present? (vector? parts)
         :text (when (vector? parts)
                 (str/join (keep #(when (map? %) (get % "text")) parts)))
         :stop (get body "stop_reason")})

      :chat-completions-v1
      (let [choices (get body "choices")]
        {:present? (vector? choices)
         :text (when (vector? choices)
                 (str/join (keep #(get-in % ["message" "content"]) choices)))
         :stop (get-in body ["choices" 0 "finish_reason"])})

      {:present? false})))

(defn admit-inference
  "Judge the RESPONSE to an inference request. `{:status <int> :body <string>}`.

  Three outcomes have to stay distinguishable, and a namespace that collapsed
  them would be the defect root ADR-2608136000 names:

  - **the provider could not measure** — no status at all, because the request
    faulted. `:response-unmeasured`. It is not a refusal; there was nothing
    to refuse;
  - **the model returned nothing** — a 200 whose body carries no completion
    text. `:completion-empty`;
  - **it worked** — an allow carrying the text, its length, and the stop
    reason.

  A non-200 the authority meant is `:response-not-ok` with the status, which is
  where an un-credentialed request to the gated surface lands:
  `POST https://api.murakumo.cloud/v1/messages` answered 401 on 2026-08-21. A
  **5xx is `:response-upstream-fault`** instead, and is in
  `unmeasurable-reasons`: a 503 from a Cloudflare edge says the peer is having
  a bad minute, and giving it the same weight as a digest mismatch teaches an
  operator to investigate the weather. A body that is not JSON is
  `:body-unparsable` with the reader's own reason, rather than an empty
  completion — \"could not read it\" and \"read it and it was empty\" are
  different facts.

  Two more refusals exist because the answer shape is **declared by the plan**
  rather than sniffed from the body (see `response-shapes`):

  - `:response-shape-unknown` — the plan targeted a path this namespace has no
    reader for. It is refused before the body is looked at, because a machine
    that cannot say what document it asked for cannot judge the one it got;
  - `:response-shape-mismatch` — the declared shape's container is absent. The
    authority sent a different kind of document, which is not the same event as
    the model returning nothing, and a lenient reader reports the two
    identically.

  And one because this plane does not read streams:
  `:response-streaming-unsupported`. An SSE response is not a malformed
  document and not an empty answer; it is a document neither reader
  understands, and saying so is the difference between a gap and a fault."
  [plan response]
  (cond
    (not (allowed? plan))
    (deny :plan-not-allowed {:aiueos.cloud/plan-reason (:aiueos.cloud/reason plan)})

    (nil? (:status response))
    (deny :response-unmeasured {:aiueos.cloud/alias (:aiueos.cloud/alias plan)})

    (not= 200 (:status response))
    (status-refusal (:status response) {:aiueos.cloud/alias (:aiueos.cloud/alias plan)})

    (not (contains? response-shapes-by-name (:aiueos.cloud/response-shape plan)))
    (deny :response-shape-unknown
          {:aiueos.cloud/alias (:aiueos.cloud/alias plan)
           :aiueos.cloud/response-shape (:aiueos.cloud/response-shape plan)})

    (streaming-response? response)
    (deny :response-streaming-unsupported
          {:aiueos.cloud/alias (:aiueos.cloud/alias plan)
           :aiueos.cloud/content-type (:content-type response)})

    :else
    (let [shape (:aiueos.cloud/response-shape plan)
          body (json/read-json (str (:body response)))]
      (if (json/failed? body)
        (deny :body-unparsable {:aiueos.cloud/alias (:aiueos.cloud/alias plan)
                                :grant.json/error (json/error-of body)})
        (let [{:keys [present? text stop]} (read-completion shape body)
              base {:aiueos.cloud/alias (:aiueos.cloud/alias plan)
                    :aiueos.cloud/response-shape shape}]
          (cond
            (not present?)
            (deny :response-shape-mismatch base)

            (str/blank? text)
            (deny :completion-empty (assoc base :aiueos.cloud/stop-reason stop))

            :else
            (allow (assoc base
                          :aiueos.cloud/completion text
                          :aiueos.cloud/completion-chars (count text)
                          :aiueos.cloud/stop-reason stop))))))))

;; ── liveness: is the authority answering at all ────────────────────────────
;;
;; This is the smallest useful question and it is easy to mistake for a bigger
;; one. Three states otherwise collapse into a single \"inference did not
;; happen\": the authority is unreachable, the authority answered but this
;; machine holds no credential, and the authority served a completion. A
;; liveness probe separates the first from the second and says nothing about
;; the third -- so `admit-liveness` returns `:aiueos.cloud/live? true` and no
;; completion, and no caller may read it as one.

(defn plan-liveness
  "Plan the inference authority's liveness endpoint. PATH defaults to
  `/ready`; it is configuration because it is the authority's route, not this
  machine's."
  ([policy] (plan-liveness policy (get policy :aiueos.cloud/liveness-path "/ready")))
  ([policy path]
   (with-allowed-url policy (join-url (cfg policy :aiueos.cloud/inference-origin) path)
     (fn [url] {:aiueos.cloud/request {:method :get :url url}
                :aiueos.cloud/liveness-path path}))))

(defn admit-liveness
  "Judge a liveness RESPONSE. A 200 and nothing else.

  **This is not an inference result.** It says the authority answered this
  machine on this path, which is worth knowing precisely because it is *less*
  than a completion."
  [plan response]
  (cond
    (not (allowed? plan))
    (deny :plan-not-allowed {:aiueos.cloud/plan-reason (:aiueos.cloud/reason plan)})

    (nil? (:status response))
    (deny :response-unmeasured {:aiueos.cloud/url (get-in plan [:aiueos.cloud/request :url])})

    (not= 200 (:status response))
    (status-refusal (:status response) {})

    :else
    (allow {:aiueos.cloud/live? true :aiueos.cloud/status 200})))

;; ── performing a plan ──────────────────────────────────────────────────────

(defn perform
  "Run an allowed PLAN's request through `grant.net/guarded-fetch`, so the URL
  is checked again at call time by the same function every other provider
  calls. A denied plan never reaches FETCH-FN, and neither does a plan whose
  URL stopped being allowed between planning and now."
  [policy plan fetch-fn]
  (if (allowed? plan)
    (net/guarded-fetch policy (get-in plan [:aiueos.cloud/request :url])
                       (fn [url] (fetch-fn (assoc (:aiueos.cloud/request plan) :url url))))
    {:ok? false
     :aiueos.cloud/denied (:aiueos.cloud/reason plan)}))
