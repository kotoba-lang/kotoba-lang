(ns filecoin.cloud.pdp
  "PDPVerifier — Proof of Data Possession, the contract a storage provider
  has to satisfy to keep being paid.

  The shape of the arrangement: a client creates a **data set**, adds
  **pieces** (named by PieceCID v2), and the provider must, each proving
  period, answer a challenge derived from on-chain randomness by producing
  Merkle inclusion proofs for randomly chosen leaves. Miss the window and the
  payment rail stops. Filecoin's older PoRep/PoSt proves a sealed sector;
  this proves an unsealed piece is still retrievable, which is what a *warm*
  storage service needs.

  Selectors are constants here, each beside the signature it came from. The
  only keccak in this workspace is JVM-only on purpose, so they cannot be
  computed on both runtimes — and every one is checked in the test against
  viem's `toFunctionSelector` applied to the deployed ABI's own signature.
  That check is the reason `addPieces` is right: the plausible spelling,
  `addPieces(uint256,(bytes)[],bytes)`, is one argument short of the real
  one and has a completely different selector.

  A piece argument is `(bytes)` — a **one-field tuple** wrapping the PieceCID
  bytes, not a bare `bytes`. The two encode differently and the wrong one is
  accepted by nothing."
  (:require [ethereum.abi :as abi]
            [filecoin.cloud.piece :as piece]))

;; ── selectors ────────────────────────────────────────────────────────────────
;; signature → selector. Checked against the deployed ABI in the test.

(def signatures
  {:create-data-set "createDataSet(address,bytes)"
   :add-pieces "addPieces(uint256,address,(bytes)[],bytes)"
   :schedule-piece-deletions "schedulePieceDeletions(uint256,uint256[],bytes)"
   :delete-data-set "deleteDataSet(uint256,bytes)"
   :claim-data-set-storage-provider "claimDataSetStorageProvider(uint256,bytes)"
   :propose-data-set-storage-provider "proposeDataSetStorageProvider(uint256,address)"
   :next-proving-period "nextProvingPeriod(uint256,uint256,bytes)"
   :prove-possession "provePossession(uint256,(bytes32,bytes32[])[])"
   :data-set-live "dataSetLive(uint256)"
   :piece-live "pieceLive(uint256,uint256)"
   :piece-challengable "pieceChallengable(uint256,uint256)"
   :get-piece-cid "getPieceCid(uint256,uint256)"
   :get-next-piece-id "getNextPieceId(uint256)"
   :get-next-data-set-id "getNextDataSetId()"
   :get-data-set-listener "getDataSetListener(uint256)"
   :get-data-set-storage-provider "getDataSetStorageProvider(uint256)"
   :get-data-set-leaf-count "getDataSetLeafCount(uint256)"
   :get-data-set-last-proven-epoch "getDataSetLastProvenEpoch(uint256)"
   :get-challenge-range "getChallengeRange(uint256)"
   :get-challenge-finality "getChallengeFinality()"
   :get-next-challenge-epoch "getNextChallengeEpoch(uint256)"
   :get-randomness "getRandomness(uint256)"
   :get-active-piece-count "getActivePieceCount(uint256)"
   :get-active-pieces "getActivePieces(uint256,uint256,uint256)"
   :get-scheduled-removals "getScheduledRemovals(uint256)"
   :find-piece-ids "findPieceIds(uint256,uint256[])"
   :calculate-proof-fee "calculateProofFee(uint256)"
   :calculate-proof-fee-for-size "calculateProofFeeForSize(uint256)"
   :fee-per-tib "feePerTiB()"
   :max-piece-size-log2 "MAX_PIECE_SIZE_LOG2()"
   :max-enqueued-removals "MAX_ENQUEUED_REMOVALS()"})

(def selectors
  {:create-data-set "0xbbae41cb"
   :add-pieces "0x9afd37f2"
   :schedule-piece-deletions "0x0c292024"
   :delete-data-set "0x7a1e2990"
   :claim-data-set-storage-provider "0xdf0f3248"
   :propose-data-set-storage-provider "0x43186080"
   :next-proving-period "0x45c0b92d"
   :prove-possession "0xf58f952b"
   :data-set-live "0xca759f27"
   :piece-live "0x1a271225"
   :piece-challengable "0xdc635266"
   :get-piece-cid "0x25bbbedf"
   :get-next-piece-id "0x1c5ae80f"
   :get-next-data-set-id "0x442cded3"
   :get-data-set-listener "0x2b3129bb"
   :get-data-set-storage-provider "0x21b7cd1c"
   :get-data-set-leaf-count "0xa531998c"
   :get-data-set-last-proven-epoch "0x04595c1a"
   :get-challenge-range "0x89208ba9"
   :get-challenge-finality "0xf83758fe"
   :get-next-challenge-epoch "0x6ba4608f"
   :get-randomness "0x453f4f62"
   :get-active-piece-count "0x5353bdfd"
   :get-active-pieces "0x39f51544"
   :get-scheduled-removals "0x6fa44692"
   :find-piece-ids "0x349c9179"
   :calculate-proof-fee "0x86981308"
   :calculate-proof-fee-for-size "0xe9a31a55"
   :fee-per-tib "0x22ef3f73"
   :max-piece-size-log2 "0xf8eb8276"
   :max-enqueued-removals "0x9f8cb3bd"})

(defn selector [k]
  (or (get selectors k)
      (throw (ex-info "pdp: unknown method" {:method k}))))

(defn call
  "Calldata for a PDPVerifier method, as `0x…`.

  The argument types come from the signature rather than from a second
  literal, so there is only one place to be wrong.

      (call :data-set-live [42])
      (call :add-pieces [set-id provider pieces extra-data])"
  [k values]
  (abi/encode-call-hex (selector k)
                       (abi/argument-types (get signatures k))
                       values))

;; ── pieces ───────────────────────────────────────────────────────────────────

(defn piece-argument
  "A PieceCID → the `(bytes)` tuple the contract takes.

  The wrapper is a struct with one field, not a naked `bytes`; and the bytes
  are the **binary CID**, not the `bafkzcib…` string. Passing the string
  encodes 63 ASCII characters, which is well-formed calldata for a piece
  that does not exist."
  [cid-or-piece]
  (let [c (if (map? cid-or-piece) (:cid cid-or-piece) cid-or-piece)]
    [(vec (piece/cid-bytes (piece/parse c)))]))

(defn add-pieces
  "The `addPieces` call for a set of PieceCIDs.

      (add-pieces 42 \"0x…provider\" [\"bafkzcib…\" \"bafkzcib…\"])"
  ([data-set-id provider cids] (add-pieces data-set-id provider cids []))
  ([data-set-id provider cids extra-data]
   (call :add-pieces [(str data-set-id) provider
                      (mapv piece-argument cids) extra-data])))

(defn create-data-set
  "`createDataSet(listener, extraData)`. The listener is the service contract
  that will be told about every change to the set — Warm Storage, in the
  arrangement this library is for. A data set with no listener is proven and
  paid for by nobody."
  ([listener] (create-data-set listener []))
  ([listener extra-data] (call :create-data-set [listener extra-data])))
