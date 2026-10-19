(ns kagi.phase
  "段階導入ゲート。policy disposition に **caution を足すことしかできない**(緩めない)。"
  (:require [kagi.vault :as vault]))

(def phases
  {0 {:label "read-only"       :writes #{}                    :auto #{}}
   1 {:label "self-vault"      :writes vault/write-ops        :auto #{:item/create :item/update}}
   2 {:label "team-share"      :writes vault/write-ops        :auto #{:item/create :item/update :share/grant}}
   3 {:label "supervised-auto" :writes vault/write-ops        :auto vault/write-ops}})

(def default-phase 1)

(defn gate
  "phase 制約を当てて disposition を補正する。緩和は不可。"
  [phase {:keys [op]} disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold disposition)               {:disposition :hold :reason nil}
      (contains? vault/read-ops op)       {:disposition disposition :reason nil}
      (not (contains? writes op))         {:disposition :hold :reason :phase-disabled}
      (and (= :commit disposition)
           (not (contains? auto op)))     {:disposition :escalate :reason :phase-approval}
      :else                               {:disposition disposition :reason nil})))
