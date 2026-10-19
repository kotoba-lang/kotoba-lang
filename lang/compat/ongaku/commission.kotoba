(ns ongaku.commission
  "受注（commission）— 作曲家の商売の単位。

  `ongaku.compose` の単位が『カタログの1曲を選んで使わせる』であるのに対し、
  こちらの単位は『依頼を受けて作り、権利を定めて渡す』。受注1件は
  作品・納品物・譲渡する権利・対価を1つに束ね、成立するかどうかを
  `validate` が判定する。

  受注を受ける前に、その約束が
  (a) 持っていない権利を売っていないか（`ongaku.rights`）
  (b) 作れない物を約束していないか（`ongaku.work`）
  を構造的に確かめる。

  `validate` は問題の vector（または `nil`）を返すだけで、**その問題を
  hard reject にするか人間承認に回すかは決めない** —— それは職能側の
  governor の判断（`cloud-itonami-isco-2652` の `music-practice.governor`）。
  技芸は事実を返し、職能が処分を決める。"
  (:require [clojure.string :as str]
            [ongaku.rights :as rights]
            [ongaku.work :as work]))

(def statuses
  #{:draft :quoted :accepted :in-production :delivered :settled :cancelled})

(defn commission
  "受注を1件つくる。

  `:commission/grants` は依頼主へ譲渡する権利（`ongaku.rights/right`）、
  `:commission/deliverables` は納品する種別のベクタ
  （`ongaku.work/deliverable-kinds` のキー）。"
  [{:keys [id client brief work grants deliverables fee currency deadline status]}]
  {:commission/id id
   :commission/client client
   :commission/brief brief
   :commission/work work
   :commission/grants (vec grants)
   :commission/deliverables (vec deliverables)
   :commission/fee fee
   :commission/currency (or currency "JPY")
   :commission/deadline deadline
   :commission/status (or status :draft)})

(defn validate
  "受注が成立するかを検証する。

  `existing-grants` はこの作品について既に他所へ出してある譲渡
  （独占の二重譲渡を防ぐため）。問題が無ければ `nil`、あれば
  問題の vector を返す。"
  ([commission] (validate commission nil))
  ([commission existing-grants]
   (let [w (:commission/work commission)
         held (:work/held-rights w)
         problems
         (concat
          (when (str/blank? (str (:commission/id commission)))
            [{:problem/type :missing-id}])
          (when (str/blank? (str (:commission/client commission)))
            [{:problem/type :missing-client}])
          (when-not (contains? statuses (:commission/status commission))
            [{:problem/type :unknown-status
              :problem/value (:commission/status commission)}])
          (when (and (:commission/fee commission)
                     (neg? (:commission/fee commission)))
            [{:problem/type :negative-fee}])
          (work/validate-work w)
          (work/validate-deliverables w (:commission/deliverables commission))
          ;; 権利は1件ずつ検証し、同じ受注内の先行 grant も衝突判定に入れる
          (first
           (reduce
            (fn [[acc seen] g]
              [(concat acc (rights/validate-grant held g (concat existing-grants seen)))
               (conj seen g)])
            [nil []]
            (:commission/grants commission))))]
     (when (seq problems) (vec problems)))))

(defn acceptable?
  "この受注を受けてよいか。"
  ([commission] (acceptable? commission nil))
  ([commission existing-grants]
   (nil? (validate commission existing-grants))))
