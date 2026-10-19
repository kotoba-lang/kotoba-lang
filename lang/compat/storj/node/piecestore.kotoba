(ns storj.node.piecestore
  "Storing a piece, and giving it back.

  The node's actual job, and the only place it can be made to hold bytes
  nobody will pay for or hand out bytes nobody authorised. Everything here is
  written from that direction.

  ## One message type, four different messages

  `PieceUploadRequest` carries `limit`, `order`, `chunk` and `done`, and
  nothing in the encoding says which of the four a given message is. The first
  carries the limit, the middle ones carry orders and chunks, the last carries
  the hash. So the state machine is not decoration: **a node that reads a
  `limit` out of the fifth message it received is being told to forget the one
  it admitted**, and a node that accepts a `done` before any chunk has a piece
  of length zero signed as something else.

  ## What an upload is allowed to be

  - The **first** message must carry a limit, and that limit must admit
    (`storj.node.orders/admit`, action `:put`). Nothing else is read from it.
  - A **second** limit is refused rather than ignored. Ignoring it leaves two
    readings of the same stream disagreeing about what was authorised.
  - Chunks must be **contiguous from zero**. A gap is a piece with a hole in
    it that hashes to something the uplink never sent; an overlap is a rewrite
    of bytes already counted.
  - The running total must stay **within the order limit**. This is checked
    per chunk rather than at the end, because at the end the bytes are already
    in memory and the node has already done the work it will not be paid for.
  - `done` ends it. Its `piece_id` must be the one the limit named.

  ## What this does not do

  It does not write anything. `accept-chunk` returns the bytes it accepted and
  the caller decides where they go, because a node that streams to disk and a
  node that buffers are the same decision with different memory, and that is
  the host's to make.

  ## The uplink's signature on `done`

  `finish-upload` checks it when it is given a verifier, and reports
  `:hash-verified?` either way rather than leaving the caller to guess. The
  key is `uplink_public_key` in the limit — 32 raw ed25519 bytes, no DER
  wrapper, which is the one row in `host.verify`'s table that is not an SPKI.

  Two things about it are not obvious and are both enforced below:

  - **The signed bytes are a different message.** `signing.EncodePieceHash`
    builds a `PieceHashSigning`, which is a `PieceHash` with the signature
    dropped and the timestamp dropped *when it is zero*. That is the opposite
    of `OrderLimit`, where `nullable=false` means a zero timestamp is emitted
    — the two messages sit beside each other and disagree.
  - **Unknown fields are refused, not preserved.** `verifyUplinkPieceHash-
    Signature` rejects anything with unrecognized fields before it encodes.
    An order limit keeps them; a piece hash may not have them at all.

  When there is nothing to check with — no key in the limit, or no signature
  in the message — the upload still succeeds and reports `:hash-verified?
  false`. Those are real states an uplink can produce, and a node that
  rejected them would refuse pieces over a signature nobody claimed to have
  sent. What it must not do is claim the hash was checked.

  A missing *verifier* is not one of those states: `orders/admit` refuses a
  limit without one, so an upload with no verifier never reaches
  `finish-upload`. The guard for it exists because `verify-piece-hash` is
  also callable directly, and is unreachable through this path."
  (:require [proto.wire :as w]
            [storj.node.bytes :as b]
            [storj.node.orders :as orders]
            [storj.node.pb :as pb]
            [storj.node.protocols :as p]))

;; ── reading the parts of a message ──────────────────────────────────────────

(defn upload-parts
  "Which of the four things a `PieceUploadRequest` carries.

  All four may be absent and more than one may be present; the state machine
  decides whether that combination is allowed at this point in the stream."
  [msg]
  {:limit (pb/get-msg msg pb/piece-upload-request :limit)
   :order (pb/get-msg msg pb/piece-upload-request :order)
   :chunk (pb/get-msg msg pb/piece-upload-request :chunk)
   :done  (pb/get-msg msg pb/piece-upload-request :done)
   :hash-algorithm (pb/get-enum msg pb/piece-upload-request :hash-algorithm
                                pb/piece-hash-algorithm)})

(defn chunk-of
  "`{:offset n :data bytes}` from an upload chunk.

  A chunk with no offset field is offset zero — proto3 omits a zero, so the
  absent case and the zero case are the same message and must read the same."
  [chunk-msg]
  {:offset (or (pb/get-varint chunk-msg pb/upload-chunk :offset) 0)
   :data   (or (pb/get-bytes chunk-msg pb/upload-chunk :data) [])})

;; ── uploading ───────────────────────────────────────────────────────────────

(defn begin-upload
  "Admit the first message of an upload.

  `opts` is what `storj.node.orders/admit` needs, plus this node's id. Returns
  a state, or `{:ok? false :reasons [...]}` — the same shape `admit` returns,
  because a refused upload and a refused order limit are the same event."
  [first-msg opts]
  (let [{:keys [limit chunk done hash-algorithm]} (upload-parts first-msg)]
    (cond
      (nil? limit)
      {:ok? false :reasons [{:reason :first-message-has-no-limit}]}

      done
      ;; a piece of length zero, signed as whatever the hash says
      {:ok? false :reasons [{:reason :done-before-any-data}]}

      :else
      (let [admitted (orders/admit limit (assoc opts :action :put))]
        (if-not (:ok? admitted)
          admitted
          (let [state {:ok? true
                       :limit limit
                       ;; carried from `opts` rather than asked for again at
                       ;; `finish-upload`: the verifier is already here, and a
                       ;; second parameter three calls later is one a caller
                       ;; can leave out and never notice
                       :verifier (:verifier opts)
                       :piece-id (:piece-id admitted)
                       :max-bytes (:limit admitted)
                       :hash-algorithm (or hash-algorithm :sha256)
                       :received 0
                       :chunks 0
                       :finished? false}]
            ;; the first message may carry data as well as the limit
            (if chunk
              (let [{:keys [offset data]} (chunk-of chunk)]
                (if (zero? offset)
                  (-> state (assoc :received (count data) :chunks 1)
                      (assoc :accepted (vec data)))
                  {:ok? false :reasons [{:reason :first-chunk-not-at-zero
                                         :offset offset}]}))
              state)))))))

(defn accept-chunk
  "Take one more message of an upload.

  Returns `{:ok? true :state s :accepted bytes}` or `{:ok? false :reasons
  [...]}`. `:accepted` is what the caller should store — this writes nothing."
  [state msg]
  (let [{:keys [limit chunk done]} (upload-parts msg)]
    (cond
      (:finished? state)
      {:ok? false :reasons [{:reason :message-after-done}]}

      limit
      ;; ignoring it would leave two readings of this stream disagreeing about
      ;; what was authorised
      {:ok? false :reasons [{:reason :second-limit-in-one-upload}]}

      done
      {:ok? false :reasons [{:reason :done-is-not-a-chunk}
                            {:note "call finish-upload"}]}

      (nil? chunk)
      ;; an order on its own is fine — the uplink is paying as it goes
      {:ok? true :state state :accepted []}

      :else
      (let [{:keys [offset data]} (chunk-of chunk)
            expected (:received state)
            total    (+ expected (count data))]
        (cond
          (not= offset expected)
          {:ok? false :reasons [{:reason :chunk-out-of-order
                                 :expected expected :offset offset
                                 :note (if (< offset expected)
                                         "an overlap rewrites bytes already counted"
                                         "a gap leaves a hole the uplink never sent")}]}

          (not (orders/within-limit? {:limit (:max-bytes state)} expected (count data)))
          {:ok? false :reasons [{:reason :over-the-order-limit
                                 :limit (:max-bytes state)
                                 :would-be total}]}

          :else
          {:ok? true
           :state (-> state (assoc :received total) (update :chunks inc))
           :accepted (vec data)})))))

(defn- unknown-fields
  "Field numbers in `msg` that `pb/piece-hash` does not name.

  A piece hash carrying one cannot be verified: `EncodePieceHash` would sign
  over bytes this build cannot reproduce, and Storj does not try — it refuses
  the message. So this is a list rather than a boolean, because a refusal that
  cannot say which field is a refusal nobody can act on."
  [msg]
  ;; a decoded message is already the vector of its fields — `w/fields` picks
  ;; one number out of it, which is the opposite of what this wants
  (->> msg
       (map :field-number)
       distinct
       (remove #(contains? pb/piece-hash %))
       sort))

(defn- verify-piece-hash
  "Check the uplink's signature over `done`.

      {:verified? bool}                      checked, or nothing to check with
      {:ok? false :reasons [...]}            checked and wrong

  Three ways to have nothing to check with, and they are not the same as a
  bad signature: no verifier configured, no key in the limit, no signature in
  the message. Each returns `:verified? false` and lets the upload stand —
  a node with no key material should not silently reject every piece — while
  a signature that is present and wrong is refused.

  The key is 32 raw ed25519 bytes out of `uplink_public_key`. A limit whose
  key is the wrong length is refused rather than passed to the verifier: an
  ed25519 verify with a short key is an error at a layer that reports it as
  `invalid signature`, which reads as the uplink's fault."
  [state done]
  (let [verifier (:verifier state)
        key      (some-> (:limit state)
                         (pb/get-bytes pb/order-limit :uplink-public-key))
        sig      (pb/get-bytes done pb/piece-hash :signature)]
    (cond
      (or (nil? verifier) (nil? key) (nil? sig)) {:verified? false}

      (not= 32 (count key))
      {:ok? false :reasons [{:reason :uplink-public-key-is-not-ed25519
                             :length (count key)}]}

      :else
      (let [signed (pb/encode-piece-hash-for-signing done)]
        (if (p/-verify verifier :ed25519 (vec key) signed (vec sig))
          {:verified? true}
          {:ok? false :reasons [{:reason :uplink-signature-invalid
                                 :piece-id (b/hex (:piece-id state))}]})))))

(defn finish-upload
  "Take the final message and say what was stored.

  Returns `{:ok? true :piece-id :size :hash :hash-verified? false ...}`.

  `:hash-verified? false` is not a placeholder to be read past. The uplink
  signs `done` with its piece key, and checking that signature needs the
  key out of the limit and the same `IVerifier` everything else uses. Saying
  so in the result is the difference between a node that has not checked and a
  node that appears to have."
  [state msg]
  (let [{:keys [done]} (upload-parts msg)]
    (cond
      (:finished? state)
      {:ok? false :reasons [{:reason :already-finished}]}

      (nil? done)
      {:ok? false :reasons [{:reason :no-done-in-final-message}]}

      :else
      (let [piece-id (pb/get-bytes done pb/piece-hash :piece-id)
            hash     (pb/get-bytes done pb/piece-hash :hash)
            declared (pb/get-varint done pb/piece-hash :piece-size)]
        (cond
          (and piece-id (not (b/equal? piece-id (:piece-id state))))
          {:ok? false :reasons [{:reason :done-names-another-piece
                                 :expected (b/hex (:piece-id state))
                                 :found (b/hex piece-id)}]}

          (and declared (not= declared (:received state)))
          {:ok? false :reasons [{:reason :declared-size-is-not-what-arrived
                                 :declared declared :received (:received state)}]}

          (seq (unknown-fields done))
          ;; `verifyUplinkPieceHashSignature` refuses these outright rather
          ;; than signing over them, which is the opposite of an order limit.
          ;; Checked before the signature: a field this build cannot read is
          ;; a field it cannot have signed the same bytes over.
          {:ok? false :reasons [{:reason :unknown-fields-in-piece-hash
                                 :fields (vec (unknown-fields done))}]}

          :else
          (let [checked (verify-piece-hash state done)]
            (if (false? (:ok? checked))
              {:ok? false :reasons (:reasons checked)}
              {:ok? true
               :piece-id (:piece-id state)
               :size (:received state)
               :chunks (:chunks state)
               :hash hash
               :hash-algorithm (:hash-algorithm state)
               :signature (pb/get-bytes done pb/piece-hash :signature)
               :hash-verified? (:verified? checked)
               :state (assoc state :finished? true)})))))))

;; ── downloading ─────────────────────────────────────────────────────────────

(defn begin-download
  "Admit the first message of a download.

  The requested range must lie inside what the limit authorises; a node that
  serves past it has done work it will not be paid for, and one that serves
  from a negative offset has been asked for bytes of something else."
  [first-msg opts]
  (let [limit (pb/get-msg first-msg pb/piece-download-request :limit)
        chunk (pb/get-msg first-msg pb/piece-download-request :chunk)]
    (cond
      (nil? limit)
      {:ok? false :reasons [{:reason :first-message-has-no-limit}]}

      (nil? chunk)
      {:ok? false :reasons [{:reason :no-range-requested}]}

      :else
      (let [admitted (orders/admit limit (assoc opts :action :get))]
        (if-not (:ok? admitted)
          admitted
          (let [offset (or (pb/get-varint chunk pb/download-chunk :offset) 0)
                size   (or (pb/get-varint chunk pb/download-chunk :chunk-size) 0)]
            (cond
              (neg? offset)
              {:ok? false :reasons [{:reason :negative-offset :offset offset}]}

              (not (pos? size))
              {:ok? false :reasons [{:reason :empty-range :size size}]}

              (not (orders/within-limit? {:limit (:limit admitted)} offset size))
              {:ok? false :reasons [{:reason :range-exceeds-the-order-limit
                                     :limit (:limit admitted)
                                     :offset offset :size size}]}

              :else
              {:ok? true
               :limit limit
               :piece-id (:piece-id admitted)
               :offset offset
               :size size
               :max-bytes (:limit admitted)
               :sent 0})))))))

(defn download-chunk-response
  "One `PieceDownloadResponse` carrying bytes back."
  [offset data]
  (w/encode [(w/message-field 1 [(w/varint-field 1 offset)
                                 (w/bytes-field 2 (vec data))])]))

(defn sending
  "Account for bytes about to go out, refusing to exceed what was authorised.

  Checked before the write rather than after: a node that notices afterwards
  has already sent them."
  [state n]
  (let [total (+ (:sent state) n)]
    (if (> total (:size state))
      {:ok? false :reasons [{:reason :more-than-was-requested
                             :requested (:size state) :would-be total}]}
      {:ok? true :state (assoc state :sent total)})))

(defn download-complete?
  [state]
  (>= (:sent state) (:size state)))
