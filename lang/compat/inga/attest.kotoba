(ns inga.attest
  "Signatures on certificates — what makes a quorum a quorum rather than a
  list of names.

  `inga.consensus/qc` counts DISTINCT WITNESSES and its docstring says votes
  arrive 'already signature-verified by the caller'. That contract holds where
  a replica collects votes it received itself. It is not honoured anywhere in
  the sync path, and cannot be: a certificate inside a block from a stranger
  was never seen by this replica as votes at all.

  So until this namespace existed, `inga.sync` accepted any segment whose
  certificates NAMED quorum-many witnesses. A peer could list three witnesses
  who never voted and hand over a fabricated history that passed every check.
  The commit rule was sound and the thing it was checking was not.

  ## Aggregation, and what this is not

  This is aggregation by CONCATENATION: a certificate carries one signature
  per witness. Real aggregation — BLS, one signature regardless of quorum size
  — needs a pairing-friendly curve, and WebCrypto does not have one. Adding a
  curve implementation would put the most security-critical arithmetic in the
  system into hand-written code, in a project whose whole transport argument
  (ADR-2608021030) was that it must run wherever the platform already provides
  what it needs.

  The cost is stated rather than hidden: certificate size grows linearly with
  the validator set, so at 100 validators a certificate carries 100 signatures.
  That is a bandwidth problem at a scale this system is nowhere near, and it is
  a bandwidth problem rather than a correctness one. When it matters, the fix
  is a curve, not a shortcut.

  ## The payload is domain-separated

  A vote signature covers the chain id, the view, the height, the block hash
  and the witness. Each is load-bearing for the same reasons `torihiki.auth`
  gives: without the chain id a testnet signature authorises a mainnet vote;
  without the view a signature from one view certifies another; without the
  witness a signature can be replayed as somebody else's."
  (:require [kotoba.lang.text :as str]
            [inga.quorum :as q]))

(def reasons
  ;; `:not-admitted` — a witness the certificate names is not in the validator
  ;; set (or no set was supplied). Distinct from `:bad-signature` on purpose:
  ;; the signature is typically PERFECTLY GOOD and that is the whole problem,
  ;; so an operator reading a log needs to see "a key nobody admitted signed
  ;; this", not "someone's crypto is broken".
  #{:unsigned :not-admitted :missing-signature :bad-signature :below-quorum})

(defn vote-payload
  "The canonical string a witness signs. Field-per-line with names, so two
  different votes cannot collide by juxtaposition."
  [chain-id view height block-hash witness]
  (str "engi/vote/v1\n"
       "chain=" chain-id "\n"
       "view=" view "\n"
       "height=" height "\n"
       "block=" block-hash "\n"
       "witness=" witness "\n"))

(defn sign-vote
  "Attach a signature to a vote. `sign-fn` receives the payload."
  [vote chain-id view sign-fn]
  (assoc vote :inga.vote/sig
         (sign-fn (vote-payload chain-id view
                                (:inga.vote/height vote)
                                (:inga.vote/block-hash vote)
                                (:inga.vote/witness vote)))))

(defn new-view-payload
  "The canonical string a witness signs when it abandons a view.

  Covers the high QC's identity, not just the view and the signer. A payload
  that named only the view would let an attacker take a genuine signed
  new-view and swap the certificate inside it — and the certificate is the
  load-bearing part of the message, since it is how the next leader learns
  what it must extend and what every replica will lock onto.

  A nil high QC is signed as such rather than omitted, so 'I have nothing' and
  'the field was stripped' are different strings."
  [chain-id view witness high-qc]
  (str "engi/new-view/v1\n"
       "chain=" chain-id "\n"
       "view=" view "\n"
       "witness=" witness "\n"
       "high-block=" (:inga.qc/block-hash high-qc "none") "\n"
       "high-height=" (:inga.qc/height high-qc -1) "\n"
       "high-view=" (:inga.qc/view high-qc -1) "\n"))

(defn certify
  "Attach the votes' signatures AND the view each was signed in, keyed by
  witness.

  Maps rather than vectors because a certificate's witnesses are a SET — a
  vector would impose an order that two replicas could disagree about, and
  ordering is exactly what this project has had to fix three times.

  The views are the part that was missing. `vote-payload` covers the view, so
  a vote signed in view 16 and a vote signed in view 51 have different
  payloads — and a certificate that remembers only one view can reconstruct
  only one of them. Every other signature then fails to verify, the
  certificate is refused `:below-quorum`, and a replica that needed the block
  is refused by the check that exists to let it in.

  It cannot be avoided by making the votes agree on a view: replicas time out
  independently, so votes for one block genuinely are cast in different
  views. The certificate has to remember which."
  [qc votes]
  (assoc qc
         :inga.qc/sigs
         (into {} (keep (fn [v]
                          (when-let [s (:inga.vote/sig v)]
                            [(:inga.vote/witness v) s]))
                        votes))
         :inga.qc/views
         ;; Only for votes that actually carry one. Recording a default of
         ;; zero would be asserting that the vote was signed in view zero,
         ;; which is a different claim from not knowing — and it made every
         ;; certificate built from view-less votes unverifiable, since the
         ;; fallback to the certificate's own view could no longer fire.
         (into {} (keep (fn [v]
                          (when (and (:inga.vote/sig v) (:inga.vote/view v))
                            [(:inga.vote/witness v) (:inga.vote/view v)]))
                        votes))))

(defn verify-certificate
  "nil when every named witness has a signature that verifies, and there are
  at least `quorum` of them. Otherwise a keyword from `reasons`.

  Requires a quorum of VERIFIED signatures rather than a quorum of names plus
  some signatures: counting names and checking signatures separately means a
  certificate naming five witnesses and signing for one passes both halves.

  `verify-fn` receives `[witness payload sig]`. Injected, as everywhere else —
  a browser that cannot re-verify a certificate is not a verifier.

  `admitted?` is `(fn [witness] -> boolean)`, REQUIRED. A set works directly.

  ## Why a certificate cannot be trusted to name its own witnesses

  `witnesses` comes out of the certificate. Before 2026-08-05 nothing
  compared it to the validator set, so a certificate that named an
  attacker's own keys, and carried good signatures from every one of them,
  satisfied every branch below: no missing signature, no bad signature, and
  `quorum` met. Minting keys is free. The certificate was self-certifying.

  `:not-admitted` is checked FIRST, before signatures and before quorum, so
  keys from outside the set cannot dilute or inflate either count. Same
  ordering rule as `inga.head/verify-cert` and for the same reason.

  A stake-weighted quorum predicate narrows this by accident — an unknown
  witness carries no bond — but head-count does not, and the defence must
  not depend on which quorum profile a deployment picked."
  [qc chain-id quorum verify-fn admitted?]
  (let [witnesses (:inga.qc/witnesses qc #{})
        sigs (:inga.qc/sigs qc)
        views (:inga.qc/views qc)
        ;; Each witness's own view, falling back to the certificate's for a
        ;; certificate built before views were recorded.
        view-of #(get views % (:inga.qc/view qc 0))]
    (cond
      (empty? sigs) :unsigned
      (not (ifn? admitted?)) :not-admitted
      (some (complement admitted?) witnesses) :not-admitted
      :else
      (let [verified (filter (fn [w]
                               (when-let [sig (get sigs w)]
                                 (verify-fn w
                                            (vote-payload chain-id
                                                          (view-of w)
                                                          (:inga.qc/height qc)
                                                          (:inga.qc/block-hash qc)
                                                          w)
                                            sig)))
                             (sort witnesses))]
        (cond
          (some #(nil? (get sigs %)) witnesses) :missing-signature
          (< (count verified) (count witnesses)) :bad-signature
          (not (q/met? quorum (set verified))) :below-quorum
          :else nil)))))

(defn pending-checks
  "Every `[witness payload sig]` a certificate needs verified.

  Exists because the platform's verifier is ASYNCHRONOUS and
  `verify-certificate` is not. WebCrypto returns a Promise; making the
  verification path async would push a transport concern into the consensus
  rules, which is the trade `torihiki-node` already refused when it verified
  transaction signatures before applying a block rather than making
  `apply-block` async.

  So the shape is: ask what needs checking, resolve it however the runtime
  resolves things, then hand back a lookup. The rules stay synchronous and the
  asynchrony stays at the edge where it came from."
  [qc chain-id]
  (let [sigs (:inga.qc/sigs qc)
        views (:inga.qc/views qc)]
    (vec (for [w (sort (:inga.qc/witnesses qc #{}))
               :let [sig (get sigs w)]
               :when sig]
           [w (vote-payload chain-id (get views w (:inga.qc/view qc 0))
                            (:inga.qc/height qc)
                            (:inga.qc/block-hash qc) w)
            sig]))))

(defn lookup-verifier
  "A `verify-fn` backed by an already-resolved map of
  `{[witness payload sig] true/false}`.

  Anything absent from the map verifies as FALSE, not as unknown. A verifier
  that treats 'I was not asked about this' as acceptance is the same defect as
  a codec that reads a broken certificate as the absence of one — it turns a
  gap in the caller's bookkeeping into an accepted signature."
  [resolved]
  (fn [w payload sig] (true? (get resolved [w payload sig]))))

(defn signed?
  "Does this certificate carry signatures at all? Lets a caller distinguish
  'not verified yet' from 'cannot be verified', which are different problems
  with different responses."
  [qc]
  (boolean (seq (:inga.qc/sigs qc))))

(defn signature-bytes
  "Roughly how much a certificate costs on the wire. Exposed because the
  linear growth is the stated cost of concatenation, and a cost nobody can
  measure is a cost nobody will notice until it hurts."
  [qc]
  (reduce + 0 (map (comp count str) (vals (:inga.qc/sigs qc)))))
