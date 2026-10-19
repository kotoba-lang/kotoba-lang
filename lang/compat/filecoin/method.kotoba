(ns filecoin.method
  "Method numbers — FRC-0042, and the built-in actors' sequential ones.

  A Filecoin message carries a method **number**, not a name. There are two
  numbering schemes on the chain and they do not overlap:

  - **Sequential** (1, 2, 3 …) for the built-in actors' original methods.
    `Send` is 0 and means \"transfer value, invoke nothing\".
  - **FRC-0042**, which derives a number from a method *name* by hashing it,
    so independently written actors can agree on `Transfer` without
    coordinating a registry. Every FRC-0042 number is at least 2^24, which is
    what keeps it from colliding with a sequential one.

  The derivation is worth writing out because it has a retry in it:

      digest = BLAKE2b-512(\"1|\" ‖ name)
      take 4 bytes at a time, big-endian; return the first that is >= 2^24

  Values below 2^24 are rejected rather than used, and the loop moves on to
  the next four bytes. Skipping the retry gives the right answer for almost
  every name — the odds of needing it are about one in 256 — so it is exactly
  the kind of shortcut that passes a test and then produces one wrong number
  a year later.

  Note the domain separation tag is `\"1|\"`, and `|` is illegal in a method
  name so the concatenation is unambiguous.

  **This namespace is what connects calldata to a message.** An FEVM contract
  call is an ordinary Filecoin message whose method is `invoke-contract` and
  whose params are the ABI calldata; without the number there is nothing to
  put in the field."
  ;; `send` is the chain's name for method 0 and shadowing core's is worth it,
  ;; but only said out loud — an implicit replacement is how a later edit in
  ;; here calls what it thinks is `clojure.core/send`.
  (:refer-clojure :exclude [send])
  (:require [blake2.core :as blake2]))

(def ^:const send
  "Transfer value and invoke nothing. The only method number that is not
  derived from anything."
  0)

(def ^:const min-frc-0042
  "2^24. FRC-0042 numbers start here, which is what keeps them clear of the
  built-in actors' sequential numbering."
  16777216)

(def ^:const domain-separation-tag "1|")

(defn- be-u32 [bs i]
  (reduce (fn [acc k] (+ (* acc 256) (bit-and (int (nth bs (+ i k))) 0xff)))
          0 (range 4)))

(defn number
  "The FRC-0042 method number for a method name.

      (number \"InvokeEVM\")   ;; => 3844450837"
  [name]
  (let [digest (mapv #(bit-and (int %) 0xff)
                     (blake2/blake2b (blake2/utf8 (str domain-separation-tag name))
                                     {:digest-size 64}))]
    (loop [i 0]
      (if (> (+ i 4) (count digest))
        ;; the spec calls this a 2^-128 event; a throw is still better than a
        ;; number that is out of range
        (throw (ex-info "method: no FRC-0042 number could be derived" {:name name}))
        (let [n (be-u32 digest i)]
          (if (>= n min-frc-0042) n (recur (+ i 4))))))))

(def ^:const invoke-contract
  "`InvokeEVM` — how everything reaches an FEVM contract. The message's
  params are the ABI calldata as a CBOR byte string; see
  `filecoin.cloud.evm`."
  3844450837)

(def well-known
  "Named FRC-0042 numbers, each **computed** rather than transcribed, so the
  name and the number cannot drift apart. `invoke-contract` above is the same
  value written down, and the test asserts the two agree — a constant that is
  checked is worth more than one that is derived and never looked at."
  (into {} (map (juxt keyword number))
        ["InvokeEVM"
         "AddBalance" "WithdrawBalance" "PublishStorageDeals"
         "GetBalance" "GetDealDataCommitment" "GetDealClient"
         "GetDealProvider" "GetDealLabel" "GetDealTerm"
         "GetDealTotalPrice" "GetDealClientCollateral"
         "GetDealProviderCollateral" "GetDealVerified" "GetDealActivation"
         "GetDealSector" "SettleDealPayments" "SectorContentChanged"
         "Receive" "Transfer" "TransferFrom" "Mint" "Burn" "BurnFrom"
         "IncreaseAllowance" "DecreaseAllowance" "RevokeAllowance"
         "Allowance" "BalanceOf" "TotalSupply" "Granularity" "Name" "Symbol"
         "ChangeOwnerAddress" "ChangeWorkerAddress" "ChangePeerID"
         "ConfirmChangeWorkerAddress" "WithdrawBalanceExported"
         "CreateExternal" "AuthenticateMessage" "UniversalReceiverHook"]))

(defn named
  "The number for a well-known name, or nil. `number` derives any name; this
  only answers for the ones spelled out above, so a typo is nil rather than a
  plausible number for a method nothing implements."
  [n]
  (get well-known (keyword n)))
