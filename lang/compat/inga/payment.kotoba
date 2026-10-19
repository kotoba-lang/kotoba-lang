(ns inga.payment
  "Deterministic scalar decision core for reserving prepaid value.

  The store owns ordering and compare-and-set. This namespace owns only the
  decision that every contender must recompute against the head it actually
  read. `kotoba/payment.kotoba` is the independent reference implementation."
  (:require [clojure.spec.alpha :as s]))

(def ^:const max-safe-micros 9007199254740991)

(defn valid-micros?
  [n]
  (and (integer? n) (<= 0 n max-safe-micros)))

(defn reserve-verdict
  "Return the next consumed amount when one reservation fits.

  The subtraction form avoids overflow in runtimes whose integer addition is
  narrower than the payment domain. Invalid inputs fail closed."
  [{:keys [paid-micros consumed-micros price-micros]}]
  (cond
    (not (valid-micros? paid-micros))
    {:allow? false :reason :invalid-paid-micros}

    (not (valid-micros? consumed-micros))
    {:allow? false :reason :invalid-consumed-micros}

    (or (not (valid-micros? price-micros)) (zero? price-micros))
    {:allow? false :reason :invalid-price-micros}

    (> consumed-micros paid-micros)
    {:allow? false :reason :invalid-consumed-state}

    (> price-micros (- paid-micros consumed-micros))
    {:allow? false :reason :insufficient-balance
     :paid-micros paid-micros :consumed-micros consumed-micros}

    :else
    {:allow? true
     :reason :reserved
     :paid-micros paid-micros
     :previous-consumed-micros consumed-micros
     :consumed-micros (+ consumed-micros price-micros)
     :remaining-micros (- paid-micros consumed-micros price-micros)}))

(s/fdef reserve-verdict
  :args (s/cat :request map?)
  :ret map?)
