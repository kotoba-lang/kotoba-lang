(ns kotoba.vm.syscall
  "Integer costs charged BEFORE the effect. Exhaustion is a value on the
  context, never a throw — the same discipline as inga.fuel (F2) and the
  opposite of the compiler Wasm fuel trap (`unreachable`).

  The schedule is a capability catalogue, not a Unix syscall table and
  not Filecoin gas. There is no FIL market, no base-fee, no refund of
  unspent remainder at the message boundary: a call pays its limit in
  the consensus layer (inga.state charges the op price before the seam
  runs). Inside the kernel, unused budget simply remains unused.")

(def costs
  {:ipld/open 10
   :ipld/read-base 4
   :ipld/write-base 4
   :ipld/link 20
   :ipld/stat 2
   :self/root 2
   :self/set-state 2
   :actor/call 100
   :guest/op 1
   :byte-unit 32})

(defn size-cost
  "Linear in bytes, integer division. A 0-byte write still pays the base."
  [base n]
  (+ (long base) (quot (long (or n 0)) (long (:byte-unit costs)))))

(defn read-cost [n] (size-cost (:ipld/read-base costs) n))
(defn write-cost [n] (size-cost (:ipld/write-base costs) n))
