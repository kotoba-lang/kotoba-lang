(ns kotoba.vm.evm.env
  "Environment / block information opcodes for the evm-storage+env slice
  (Paris fork).

  Account and block context ride an `:env` map attached to the machine:

    {:address      u256   executing account (ETH-address-shaped word)
     :caller       u256   message sender
     :callvalue    u256   wei attached to the message
     :origin       u256   tx origin (EOA)
     :gasprice     u256   tx gas price
     :balance      {addr-hex u256 ...}   mock ledger, address → wei
     :block        {:chainid     314 (Filecoin mainnet, FIP-0054 shape)
                    :coinbase    u256
                    :timestamp   u256
                    :number      u256
                    :difficulty  u256 (Paris: PREVRANDAO lives here)
                    :gaslimit    u256
                    :basefee     u256
                    :blockhash   {number-hex u256 ...}  mock recent-hash map}}

  All values are u256 words; the dispatcher pushes them directly. There
  is no real Filecoin or Ethereum state here — every map is caller-
  supplied, and the profile stays :partial."
  (:require [kotoba.vm.evm.u256 :as u256]))

(def filecoin-mainnet-chain-id 314)

(def default-env
  {:address (u256/from-long 0)
   :caller (u256/from-long 0)
   :callvalue (u256/from-long 0)
   :origin (u256/from-long 0)
   :gasprice (u256/from-long 0)
   :balance {}
   :block {:chainid (u256/from-long filecoin-mainnet-chain-id)
           :coinbase (u256/from-long 0)
           :timestamp (u256/from-long 0)
           :number (u256/from-long 0)
           :difficulty (u256/from-long 0)
           :gaslimit (u256/from-long 0)
           :basefee (u256/from-long 0)
           :blockhash {}}})

(defn balance-of
  "Mock ledger read: absent accounts hold 0 wei."
  [env addr]
  (let [v (get (:balance env) (u256/to-hex-string addr))]
    (or v (u256/from-long 0))))

(defn blockhash-of
  "Mock recent-block-hash read: absent numbers hash to 0."
  [env number]
  (let [v (get-in env [:block :blockhash (u256/to-hex-string number)])]
    (or v (u256/from-long 0))))
