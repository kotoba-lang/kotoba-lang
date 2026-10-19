(ns kotoba.shohyo.jp
  "会社計算規則 — the 区分 a Japanese 貸借対照表 and 損益計算書 must have,
  and the 段階利益 ladder, read from the regulation rather than from custom.

  `kotoba.shohyo` classifies into the five account types and checks the
  accounting equation. That is jurisdiction-neutral and stops short of what a
  Japanese statement actually looks like: 流動 vs 固定, and the ladder from
  売上高 down to 税引前当期純利益.

  Those are not convention. 会社計算規則 (418M60000010013, revision
  418M60000010013_20250331_507M60000010014, retrieved 2026-08-18 from the
  e-Gov law API) states them, and every quote in `provisions` is verbatim.

  ## The rule a naive implementation gets wrong

  第八十九条第二項, and the identical second paragraph in 第九十条, 第九十一条
  and 第九十二条:

      前項の規定にかかわらず、売上総損益金額が零未満である場合には、
      零から売上総損益金額を減じて得た額を売上総損失金額として
      表示しなければならない。

  A negative 売上総損益金額 is NOT shown as a negative 売上総利益金額. It is
  shown as a **positive 売上総損失金額** — a different label and a sign flip.
  Printing -500 under 売上総利益 is the obvious implementation and it is not
  what the article says, so `stage-profits` returns a `:label` per rung, not
  only a number."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; 貸借対照表 — 第七十三条 / 第七十四条
;; ---------------------------------------------------------------------------

(def bs-sections
  "The sections of 資産の部 / 負債の部 / 純資産の部.

  第七十四条第二項 makes 固定資産 a section that CONTAINS three, so those
  three are `:parent`-linked rather than flattened into peers."
  {:current-assets      {:jp "流動資産"         :type :asset     :provision "第七十四条第一項"}
   :fixed-assets        {:jp "固定資産"         :type :asset     :provision "第七十四条第一項"}
   :tangible-fixed      {:jp "有形固定資産"     :type :asset     :parent :fixed-assets
                         :provision "第七十四条第二項"}
   :intangible-fixed    {:jp "無形固定資産"     :type :asset     :parent :fixed-assets
                         :provision "第七十四条第二項"}
   :investments-other   {:jp "投資その他の資産" :type :asset     :parent :fixed-assets
                         :provision "第七十四条第二項"}
   :deferred-assets     {:jp "繰延資産"         :type :asset     :provision "第七十四条第一項"}
   :current-liabilities {:jp "流動負債"         :type :liability :provision "第七十五条"}
   :fixed-liabilities   {:jp "固定負債"         :type :liability :provision "第七十五条"}
   :shareholders-equity {:jp "株主資本"         :type :equity    :provision "第七十六条"}})

;; ---------------------------------------------------------------------------
;; 損益計算書 — 第八十八条
;; ---------------------------------------------------------------------------

(def pl-sections
  "The seven 区分 of 第八十八条第一項, in the order the article lists them."
  {:net-sales             {:jp "売上高"               :type :revenue :order 1}
   :cost-of-sales         {:jp "売上原価"             :type :expense :order 2}
   :sga                   {:jp "販売費及び一般管理費" :type :expense :order 3}
   :non-operating-income  {:jp "営業外収益"           :type :revenue :order 4}
   :non-operating-expense {:jp "営業外費用"           :type :expense :order 5}
   :extraordinary-income  {:jp "特別利益"             :type :revenue :order 6}
   :extraordinary-loss    {:jp "特別損失"             :type :expense :order 7}})

(def provisions
  "The articles implemented here, quoted. The standing rule from
  `kotoba-lang/taxlaw` applies: a claim that is ENFORCED must be read."
  {:law/id "418M60000010013"
   :law/title "会社計算規則"
   :law/revision "418M60000010013_20250331_507M60000010014"
   :retrieved-at "2026-08-18"
   :articles
   [{:article "第七十三条" :on :bs-parts
     :quote "貸借対照表等は、次に掲げる部に区分して表示しなければならない。一 資産 二 負債 三 純資産"}
    {:article "第七十四条第一項" :on :asset-sections
     :quote "資産の部は、次に掲げる項目に区分しなければならない。…一 流動資産 二 固定資産 三 繰延資産"}
    {:article "第七十四条第二項" :on :fixed-asset-sections
     :quote "固定資産に係る項目は、次に掲げる項目に区分しなければならない。…一 有形固定資産 二 無形固定資産 三 投資その他の資産"}
    {:article "第八十八条第一項" :on :pl-sections
     :quote "損益計算書等は、次に掲げる項目に区分して表示しなければならない。…一 売上高 二 売上原価 三 販売費及び一般管理費 四 営業外収益 五 営業外費用 六 特別利益 七 特別損失"}
    {:article "第八十九条" :on :gross
     :quote "売上高から売上原価を減じて得た額（以下「売上総損益金額」という。）は、売上総利益金額として表示しなければならない。２ …零未満である場合には、零から売上総損益金額を減じて得た額を売上総損失金額として表示しなければならない。"}
    {:article "第九十条" :on :operating
     :quote "売上総損益金額から販売費及び一般管理費の合計額を減じて得た額（以下「営業損益金額」という。）は、営業利益金額として表示しなければならない。２ …零未満である場合には、…営業損失金額として表示しなければならない。"}
    {:article "第九十一条" :on :ordinary
     :quote "営業損益金額に営業外収益を加えて得た額から営業外費用を減じて得た額（以下「経常損益金額」という。）は、経常利益金額として表示しなければならない。２ …零未満である場合には、…経常損失金額として表示しなければならない。"}
    {:article "第九十二条" :on :pretax
     :quote "経常損益金額に特別利益を加えて得た額から特別損失を減じて得た額（以下「税引前当期純損益金額」という。）は、税引前当期純利益金額…として表示しなければならない。２ …零未満である場合には、…税引前当期純損失金額…として表示しなければならない。"}]})

(defn known-section? [s]
  (or (contains? bs-sections s) (contains? pl-sections s)))

(defn section-of [s] (or (bs-sections s) (pl-sections s)))

(defn section-problems
  "Sections the regulation does not name, and sections whose declared
  account type contradicts the article.

  The second is the one worth having: tagging 売上原価 as `:revenue` would
  put it on the wrong side of 第八十九条, and the gross figure would come out
  looking right and being wrong."
  [chart]
  (vec
   (mapcat
    (fn [[account {:keys [section type]}]]
      (cond
        (nil? section) []
        (not (known-section? section))
        [{:account account :problem :unknown-section :section section}]
        :else
        (let [declared (:type (section-of section))]
          (if (and type (not= type declared))
            [{:account account :problem :section-type-conflict :section section
              :detail (str (name section) " is " (name declared)
                           " under 会社計算規則, not " (name type))}]
            []))))
    chart)))

;; ---------------------------------------------------------------------------
;; 段階利益 — 第八十九条 to 第九十二条
;; ---------------------------------------------------------------------------

(defn- rung [amount profit-label loss-label article]
  ;; 第八十九条第二項 and its siblings: below zero it is not a negative
  ;; profit. It is a positive LOSS under a different name.
  (if (neg? amount)
    {:amount (- amount) :label loss-label :sign :loss :article article}
    {:amount amount :label profit-label :sign :profit :article article}))

(def ^:private ladder-sections
  [:net-sales :cost-of-sales :sga
   :non-operating-income :non-operating-expense
   :extraordinary-income :extraordinary-loss])

(defn stage-profits
  "The ladder of 第八十九条 → 第九十二条 from section totals.

  `section-totals` is `{section-keyword amount}` in natural positive
  magnitudes — 売上原価 is the cost, not a negative revenue.

  `:not-declared` when a section the ladder needs is absent, naming which.
  **A missing 売上原価 is not zero cost, it is an unstated one**, and
  treating it as zero would report a gross profit equal to sales."
  [section-totals]
  (let [missing (vec (remove #(contains? section-totals %) ladder-sections))]
    (if (seq missing)
      {:shohyo.jp/coverage :not-declared
       :shohyo.jp/missing-sections missing
       :shohyo.jp/why "a section the ladder needs was not declared; absent is not zero"}
      (let [g   (- (:net-sales section-totals) (:cost-of-sales section-totals))
            o   (- g (:sga section-totals))
            ord (- (+ o (:non-operating-income section-totals))
                   (:non-operating-expense section-totals))
            pre (- (+ ord (:extraordinary-income section-totals))
                   (:extraordinary-loss section-totals))]
        {:shohyo.jp/coverage :checked
         :shohyo.jp/gross     (rung g   "売上総利益金額"       "売上総損失金額"       "第八十九条")
         :shohyo.jp/operating (rung o   "営業利益金額"         "営業損失金額"         "第九十条")
         :shohyo.jp/ordinary  (rung ord "経常利益金額"         "経常損失金額"         "第九十一条")
         :shohyo.jp/pretax    (rung pre "税引前当期純利益金額" "税引前当期純損失金額" "第九十二条")
         ;; kanjō's canonical concepts, signed, so an internally-produced
         ;; ladder and an externally-read filing line up.
         :shohyo.jp/concepts {"gross-profit" g "operating-income" o
                              "ordinary-income" ord "pretax-income" pre}}))))
