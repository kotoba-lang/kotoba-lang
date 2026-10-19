(ns wallet.chains
  "Chain support is DATA, not code (ADR-2608039900 decision 5, generalized to
  every family here). One registry entry per network; a small set of FAMILY
  drivers in wallet.chain consumes it:

    :evm   one secp256k1 key, one address, many chains — the MetaMask
           convention: every EVM chain derives at coin type 60', so
           :eth/:bnb/:polygon/... share an address and differ only in
           :chain-id (EIP-155 folds it into the signature, so a tx signed
           for one chain is invalid on every other).
    :utxo  Bitcoin-family. btc-crypto owns the MEASURED network constants
           (version bytes / bech32 HRPs verified against a real node);
           this table only names which btc-crypto network a chain key maps to.

  Entries with a :status are registered but NOT derivable here:

    :status :external  owned by another library (Cosmos family → saifu,
                       whose whole point is the kagi policy/ledger path).
    :status :planned   staged. `entry` REFUSES these instead of deriving a
                       plausible-looking address that nothing can verify —
                       a fabricated address for an unimplemented chain is
                       how funds get sent somewhere unspendable. Each entry
                       names the mechanism it is waiting on in :needs.

  Coin types are SLIP-44; chain ids are EIP-155. The CoinMarketCap-major
  networks are all REGISTERED so a caller asking for one gets either a real
  derivation or a measured refusal naming what is missing — never nil."
  )

(def chains
  {;; ── EVM family (implemented — data-driven over eth-crypto) ─────────────
   :eth       {:family :evm :symbol "ETH"  :name "Ethereum"        :coin-type 60 :chain-id 1}
   :bnb       {:family :evm :symbol "BNB"  :name "BNB Smart Chain" :coin-type 60 :chain-id 56}
   :polygon   {:family :evm :symbol "POL"  :name "Polygon PoS"     :coin-type 60 :chain-id 137}
   :avalanche {:family :evm :symbol "AVAX" :name "Avalanche C-Chain" :coin-type 60 :chain-id 43114}
   :arbitrum  {:family :evm :symbol "ETH"  :name "Arbitrum One"    :coin-type 60 :chain-id 42161}
   :optimism  {:family :evm :symbol "ETH"  :name "OP Mainnet"      :coin-type 60 :chain-id 10}
   :base      {:family :evm :symbol "ETH"  :name "Base"            :coin-type 60 :chain-id 8453}

   ;; ── UTXO family (implemented — data-driven over btc-crypto networks) ──
   :btc  {:family :utxo :symbol "BTC"  :name "Bitcoin"  :coin-type 0   :network :mainnet}
   :ltc  {:family :utxo :symbol "LTC"  :name "Litecoin" :coin-type 2   :network :litecoin}
   :doge {:family :utxo :symbol "DOGE" :name "Dogecoin" :coin-type 3   :network :dogecoin}
   ;; btc-crypto does not implement BCH's SIGHASH_FORKID digest — receive-only,
   ;; and wallet.chain refuses to sign rather than emitting an invalid spend.
   :bch  {:family :utxo :symbol "BCH"  :name "Bitcoin Cash" :coin-type 145 :network :bitcoin-cash
          :receive-only? true}

   ;; ── Cosmos family (external — kotoba-lang/saifu owns bech32 + SIGN_MODE_DIRECT) ──
   :atom {:family :cosmos :symbol "ATOM" :name "Cosmos Hub" :coin-type 118
          :status :external :owner "kotoba-lang/saifu"}

   ;; ── Staged (registered, refused, each naming its missing mechanism) ───
   :sol  {:family :ed25519  :symbol "SOL"  :name "Solana"     :coin-type 501 :status :planned
          :needs "SLIP-0010 ed25519 derivation (m/44'/501'/…') + base58 pubkey-as-address"}
   :xrp  {:family :xrp      :symbol "XRP"  :name "XRP Ledger" :coin-type 144 :status :planned
          :needs "ripple base58 alphabet + hash160 account id (secp256k1 key reusable)"}
   :trx  {:family :tron     :symbol "TRX"  :name "TRON"       :coin-type 195 :status :planned
          :needs "keccak address (as EVM) re-encoded base58check with 0x41 prefix"}
   :ton  {:family :ed25519  :symbol "TON"  :name "TON"        :coin-type 607 :status :planned
          :needs "ed25519 + TON wallet-contract address (workchain, stateInit hash)"}
   :sui  {:family :ed25519  :symbol "SUI"  :name "Sui"        :coin-type 784 :status :planned
          :needs "SLIP-0010 ed25519 + blake2b-256 address over flagged pubkey"}
   :apt  {:family :ed25519  :symbol "APT"  :name "Aptos"      :coin-type 637 :status :planned
          :needs "SLIP-0010 ed25519 + sha3-256 single-key auth-key address"}
   :near {:family :ed25519  :symbol "NEAR" :name "NEAR"       :coin-type 397 :status :planned
          :needs "ed25519, implicit account = hex(pubkey)"}
   :ada  {:family :cardano  :symbol "ADA"  :name "Cardano"    :coin-type 1815 :status :planned
          :needs "ed25519-bip32 (CIP-3) — NONSTANDARD derivation; use a reviewed impl, do not self-implement"}
   :dot  {:family :substrate :symbol "DOT" :name "Polkadot"   :coin-type 354 :status :planned
          :needs "sr25519 (schnorrkel) + SS58 — NONSTANDARD curve usage; use a reviewed impl, do not self-implement"}
   :xmr  {:family :monero   :symbol "XMR"  :name "Monero"     :coin-type 128 :status :planned
          :needs "CryptoNote dual-key (spend/view) + base58-monero — its own cryptosystem; use a reviewed impl"}})

(defn entry
  "Look up `chain`, refusing (with the reason pinned in ex-data :reason) a
  chain this library cannot derive for — an unknown one, a staged one, or one
  owned by another library — instead of returning nil and letting a caller
  derive on a silently-missing constant."
  [chain]
  (let [e (get chains chain)]
    (cond
      (nil? e)
      (throw (ex-info (str "wallet.chains: unknown chain " (pr-str chain))
                      {:reason :wallet.chains/unknown
                       :chain chain :known (sort (keys chains))}))

      (= :planned (:status e))
      (throw (ex-info (str "wallet.chains: " (pr-str chain) " is registered but STAGED — "
                           "refusing to derive an address nothing can verify. Needs: "
                           (:needs e))
                      {:reason :wallet.chains/staged :chain chain :needs (:needs e)}))

      (= :external (:status e))
      (throw (ex-info (str "wallet.chains: " (pr-str chain) " is owned by " (:owner e)
                           " — use that library (it carries the policy/custody model for its family)")
                      {:reason :wallet.chains/external :chain chain :owner (:owner e)}))

      :else e)))

(defn implemented
  "The chain keys `entry` will actually answer for, sorted."
  []
  (into [] (comp (filter (fn [[_ e]] (nil? (:status e)))) (map key))
        (sort-by key chains)))
