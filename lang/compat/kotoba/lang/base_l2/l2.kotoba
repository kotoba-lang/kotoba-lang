(ns kotoba.lang.base-l2.l2
  "CLJ port of `l2.ts` -- a Base L2 MST-root anchor client. Reads and
  writes the `EtzhayyimAnchor`-shaped contract's `anchor` / `rootCount` /
  `anchors` methods.

  JVM-only (.clj, not .cljc): this namespace signs raw Ethereum
  transactions with a caller-held private key (mirrors `l2.ts`'s
  `privateKeyToAccount` + `walletClient.writeContract`), which is a
  server-side-signing concern -- the production consumer this design
  serves is the `anchor-cron` K8s CronJob (etzhayyim/root
  `50-infra/anchor-cron/`, ADR-2605171800 Stage 5b), not a browser dapp.
  Holding a raw private key in an in-page JS/CLJS runtime would in fact
  run against this org's own no-server-key / no-custodial-key doctrine
  for user-facing flows (see `paymaster.clj` for the sibling module that
  *is* conceptually a plausible browser consumer, because it never
  touches a private key itself -- though it too stays JVM-only for now,
  transitively via `abi.clj`; see its own docstring). See the repo
  README's \"Clojure/CLJC port\" section for the full CLJS-scope writeup.

  Independent of the private-key-custody policy reasoning above, this
  namespace is ALSO directly JVM-only on pure dependency grounds: it
  requires `eth-crypto.core` (`sign-tx-legacy`/`hex->bytes`/
  `address-of-privkey`/`keccak256`/`utf8`) and `kotoba.lang.base-l2.abi`,
  both JVM-only (`eth-crypto` has zero CLJS portability despite its own
  `.cljc` extension -- see `abi.clj`'s docstring for the full finding).
  So even a hypothetical browser-safe redesign that never held a raw key
  would still need those two ported first.

  RPC transport: `kotoba.lang.base-l2.rpc` -- a pure JSON-RPC-over-HTTP
  core over an injected `kotoba.lang.base-l2.rpc/ITransport` (host
  supplies e.g. a `babashka.http-client`-backed adapter; this namespace
  and `rpc.cljc` carry zero HTTP-client dep themselves). `rpc.cljc` is
  itself portable `.cljc` (JVM + CLJS) except its blocking
  `wait-for-transaction-receipt`, which this namespace's `anchor-mst-root!`
  calls -- irrelevant to this namespace's own JVM-only status, since this
  namespace is already blocked by the reasons above regardless. `AnchorClient`
  carries the transport alongside `rpc-url`/`contract`/`private-key` --
  every call below reads `:transport` off `client` rather than taking it
  as a separate argument, since `client` is already the natural
  connection-bundle abstraction here (see `make-anchor-client`). The two
  module-level convenience functions (`submit-mst-root!` /
  `lookup-anchor-for-root`) don't carry a client, so they take
  `transport` as an explicit leading argument instead (mirroring
  `kotoba.lang.ipfs`'s `(pin-blob http api-url content)` convention).

  ABI encode/decode: `kotoba.lang.base-l2.abi` (hand-rolled, narrow).
  Crypto (Keccak-256, secp256k1 signing, RLP, EIP-155 legacy tx serialization):
  `eth-crypto.core` (`kotoba-lang/eth-crypto`, a sibling dependency-free
  Clojure library already verified against EIP-712/EIP-155 spec vectors
  -- reused here rather than hand-rolling elliptic-curve crypto a second
  time or pulling in BouncyCastle/web3j).

  Deliberate simplification vs. `l2.ts`: viem's `walletClient.writeContract`
  automatically negotiates EIP-1559 vs. legacy tx type and estimates fees.
  This port always builds an EIP-155 **legacy** transaction (universally
  accepted by Base L2 and any anvil/Hardhat/Geth dev chain, simpler to
  get byte-exact, and this SDK's own docstring already targets
  anvil-local dev chains by default) and resolves nonce/gasPrice/gas/
  chainId via RPC calls unless the caller supplies overrides. EIP-1559
  support can be added later (bump `eth-crypto`, add a `sign-tx-eip1559`
  there) if a deployment needs the fee-market gas savings."
  (:require [clojure.string :as str]
            [kotoba.lang.base-l2.rpc :as rpc]
            [kotoba.lang.base-l2.abi :as abi]
            [eth-crypto.core :as eth]))

;; ─── ANCHOR_ABI (documentation only -- calls below are hand-encoded) ──

(def anchor-abi
  "Documents the only 3 EtzhayyimAnchor methods this SDK touches (mirrors
  `l2.ts`'s exported `ANCHOR_ABI`). Not consumed by any encode/decode
  path here -- `encode-function-call`/`decode-function-result` are
  called directly with explicit signature strings + type lists."
  [{:type "function" :name "anchor"
    :inputs [{:name "rootHash" :type "bytes32"}
             {:name "ipfsCid" :type "bytes"}
             {:name "batchSize" :type "uint64"}]
    :outputs []
    :state-mutability "nonpayable"}
   {:type "function" :name "rootCount"
    :inputs []
    :outputs [{:name "" :type "uint256"}]
    :state-mutability "view"}
   {:type "function" :name "anchors"
    :inputs [{:name "" :type "bytes32"}]
    :outputs [{:name "rootHash" :type "bytes32"}
              {:name "ipfsCid" :type "bytes"}
              {:name "blockNumber" :type "uint256"}
              {:name "anchorer" :type "address"}
              {:name "batchSize" :type "uint64"}
              {:name "anchoredAt" :type "uint64"}]
    :state-mutability "view"}])

;; ─── AnchorClient ──────────────────────────────────────────────────────

(defrecord AnchorClient [transport rpc-url contract private-key address])

(defn make-anchor-client
  "Analog of `new AnchorClient(cfg)`. `cfg` is
  `{:transport an-ITransport :rpc-url \"http://…\" :contract \"0x…\"
  :private-key \"0x…\"}`. `:transport` must satisfy
  `kotoba.lang.base-l2.rpc/ITransport` (e.g. a `babashka.http-client`-backed
  adapter) -- required even for a read-only client, since every call
  (reads included) goes over RPC. `:private-key` may be omitted for a
  READ-ONLY client (`root-count` / `find-anchor-for-root` never need a
  key; calling `anchor-mst-root!` on a keyless client throws)."
  [{:keys [transport rpc-url contract private-key]}]
  (when-not transport
    (throw (ex-info "[kotoba.lang.base-l2.l2] make-anchor-client requires a :transport (satisfies kotoba.lang.base-l2.rpc/ITransport)"
                     {:rpc-url rpc-url :contract contract})))
  (let [pk-bytes (when private-key (eth/hex->bytes private-key))]
    (->AnchorClient transport rpc-url contract pk-bytes
                    (when pk-bytes (eth/address-of-privkey pk-bytes)))))

(defn- ipfs-cid->hex ^String [ipfs-cid]
  (if (string? ipfs-cid) ipfs-cid (str "0x" (eth/bytes->hex ipfs-cid))))

(defn- hex->big [^String h]
  (BigInteger. (if (str/starts-with? h "0x") (subs h 2) h) 16))

(defn anchor-mst-root!
  "Submit a batched MST root anchor (analog of
  `AnchorClient#anchorMstRoot`). `root-hash` is a `0x…` 32-byte hex
  string, `ipfs-cid` is a `0x…` hex string or byte array, `batch-size` is
  an integer. `opts` (optional) may override `:nonce` `:gas-price` `:gas`
  `:chain-id` `:interval-ms` `:timeout-ms` -- otherwise each is resolved
  via RPC (`eth_getTransactionCount` / `eth_gasPrice` / `eth_estimateGas`
  / `eth_chainId`). Returns `{:tx-hash \"0x…\" :block-number N}`; throws
  ex-info if the client has no private key, or if the mined receipt's
  status is not success (mirrors `l2.ts` throwing on `receipt.status !==
  \"success\"`)."
  ([client root-hash ipfs-cid batch-size] (anchor-mst-root! client root-hash ipfs-cid batch-size {}))
  ([client root-hash ipfs-cid batch-size opts]
   (let [{:keys [transport rpc-url contract private-key address]} client]
     (when-not private-key
       (throw (ex-info "[kotoba.lang.base-l2.l2] anchor-mst-root! requires a client with a :private-key"
                        {:contract contract})))
     (let [calldata (abi/encode-function-call "anchor(bytes32,bytes,uint64)"
                                               ["bytes32" "bytes" "uint64"]
                                               [root-hash (ipfs-cid->hex ipfs-cid) batch-size])
           nonce (or (:nonce opts) (rpc/eth-get-transaction-count transport rpc-url address))
           gas-price (or (:gas-price opts) (rpc/eth-gas-price transport rpc-url))
           chain-id (or (:chain-id opts) (rpc/eth-chain-id transport rpc-url))
           gas (or (:gas opts) (rpc/eth-estimate-gas transport rpc-url {:from address :to contract :data calldata}))
           raw-tx (eth/sign-tx-legacy {:nonce nonce :gas-price gas-price :gas gas
                                        :to contract :data calldata :chain-id chain-id}
                                       private-key)
           tx-hash (rpc/eth-send-raw-transaction transport rpc-url raw-tx)
           receipt (rpc/wait-for-transaction-receipt transport rpc-url tx-hash (select-keys opts [:interval-ms :timeout-ms]))]
       (when (not= "0x1" (:status receipt))
         (throw (ex-info (str "[kotoba.lang.base-l2.l2] anchor tx reverted: " tx-hash)
                          {:tx-hash tx-hash :receipt receipt})))
       {:tx-hash tx-hash :block-number (hex->big (:blockNumber receipt))}))))

(defn root-count
  "Read the global anchor count (analog of `AnchorClient#rootCount`).
  Returns a BigInteger."
  [client]
  (let [{:keys [transport rpc-url contract]} client
        calldata (abi/encode-function-call "rootCount()" [] [])
        result (rpc/eth-call transport rpc-url contract calldata)]
    (first (abi/decode-function-result ["uint256"] result))))

(defn find-anchor-for-root
  "Look up the anchor record for `root-hash` (a `0x…` bytes32 hex
  string), or nil if not anchored (analog of
  `AnchorClient#findAnchorForRoot`). Read-only -- `client` need not carry
  a private key."
  [client root-hash]
  (let [{:keys [transport rpc-url contract]} client
        calldata (abi/encode-function-call "anchors(bytes32)" ["bytes32"] [root-hash])
        result (rpc/eth-call transport rpc-url contract calldata)
        [_root-hash _ipfs-cid block-number anchorer batch-size anchored-at]
        (abi/decode-function-result ["bytes32" "bytes" "uint256" "address" "uint64" "uint64"] result)]
    (when-not (zero? block-number)
      {:tx-anchorer-address anchorer
       :block-number block-number
       :batch-size batch-size
       :anchored-at anchored-at})))

;; ─── Functional API (matches l2.ts's module-level convenience fns) ────

(defn submit-mst-root!
  "Convenience wrapper matching `l2.ts`'s module-level `anchorMstRoot(rpcUrl,
  contract, rootCid, signer, ipfsCidBytes?, batchSize?)`. If `root-cid`
  is already a `0x…` 32-byte hex digest it's used as-is for `root-hash`;
  otherwise `root-hash` = `keccak256(utf8 root-cid)`, matching `l2.ts`.
  `private-key` is a `0x…` 32-byte hex string.

  Doesn't carry a client (there's nothing to bundle it into), so unlike
  `anchor-mst-root!`/`root-count`/`find-anchor-for-root` this takes
  `transport` as an explicit leading argument (mirroring
  `kotoba.lang.ipfs`'s `(pin-blob http api-url content)` convention) --
  a required addition vs. the pre-retrofit signature, since there is no
  more embedded default HTTP client to fall back to."
  ([transport rpc-url contract root-cid private-key]
   (submit-mst-root! transport rpc-url contract root-cid private-key nil 1 {}))
  ([transport rpc-url contract root-cid private-key ipfs-cid-bytes]
   (submit-mst-root! transport rpc-url contract root-cid private-key ipfs-cid-bytes 1 {}))
  ([transport rpc-url contract root-cid private-key ipfs-cid-bytes batch-size]
   (submit-mst-root! transport rpc-url contract root-cid private-key ipfs-cid-bytes batch-size {}))
  ([transport rpc-url contract root-cid private-key ipfs-cid-bytes batch-size opts]
   (let [client (make-anchor-client {:transport transport :rpc-url rpc-url :contract contract :private-key private-key})
         root-hash (if (and (str/starts-with? root-cid "0x") (= 66 (count root-cid)))
                     root-cid
                     (str "0x" (eth/bytes->hex (eth/keccak256 (eth/utf8 root-cid)))))
         cid-bytes (or ipfs-cid-bytes (eth/utf8 root-cid))]
     (anchor-mst-root! client root-hash cid-bytes batch-size opts))))

(defn lookup-anchor-for-root
  "Convenience wrapper matching `l2.ts`'s module-level `findAnchorForRoot(rpcUrl,
  contract, rootHash)`. Read-only -- no private key needed (the TS
  version constructs an `AnchorClient` with an all-zero dummy key purely
  to reuse the class; this port's `find-anchor-for-root` never needed a
  key in the first place).

  Takes `transport` as an explicit leading argument, same reasoning as
  `submit-mst-root!` above."
  [transport rpc-url contract root-hash]
  (when-let [found (find-anchor-for-root (make-anchor-client {:transport transport :rpc-url rpc-url :contract contract}) root-hash)]
    {:block-number (:block-number found) :anchorer (:tx-anchorer-address found)}))
