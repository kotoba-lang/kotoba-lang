(ns kotoba.vm.evm.storage
  "Mock storage for the evm-storage+env slice: the KAMT/IPLD shape without
  the IPLD dependency.

  A store is a plain map keyed by 32-byte words rendered as canonical
  64-digit hex — the same key shape a KAMT-backed contract store would
  present through the FVM boundary. Reads of absent keys return the zero
  word (EVM semantics: every slot exists, implicitly zero). Writes of the
  zero word DELETE the key (SSTORE clearing keeps the store sparse, as a
  real hash-mapped store would).

  The store lives on the machine as :storage and is threaded immutably —
  a reverted frame would simply drop its copy; the calls slice wires the
  revert path."
  (:require [kotoba.vm.evm.u256 :as u256]))

(def zero-hex u256/zero-hex)

(def empty-store {})

(defn key-hex
  "u256 slot → canonical hex key."
  [slot] (u256/to-hex-string slot))

(defn sload
  "Slot → word. Absent keys read as zero."
  [store slot]
  (let [w (get store (key-hex slot))]
    (if (some? w)
      w
      (u256/from-long 0))))

(defn sstore
  "Store word at slot. Storing zero clears the entry (sparse store)."
  [store slot value]
  (let [k (key-hex slot)]
    (if (u256/eq value (u256/from-long 0))
      (dissoc store k)
      (assoc store k value))))

(defn storage-root-shape
  "Diagnostics: the store as sorted [key-hex, value-hex] pairs — the shape
  a KAMT would serialize leaf by leaf. NOT a real state root; no hashing
  is claimed."
  [store]
  (mapv (fn [k] [k (u256/to-hex-string (get store k))])
        (sort (keys store))))
