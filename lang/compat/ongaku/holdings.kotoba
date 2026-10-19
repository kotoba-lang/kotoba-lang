(ns ongaku.holdings
  "ライセンス別に「その資産について licensee が**第三者へ渡せる**権利」を述べる表。

  `ongaku.policy` が答えるのは『自分がどう使ってよいか』で、こちらが答えるのは
  『**依頼主に何を渡せるか**』。この2つは別物で、**使用許諾を持っていることは
  それを他人に渡せることを意味しない**。カタログ資産を素材にした受注で
  `ongaku.rights` が `:not-held` を返すのは、多くの場合この違いによる。

  ## この表はコードが導いた事実ではない

  下の `license-holdings` は**人が維持する法的判断**であり、実装が
  ライセンス本文を解釈して導いたものではない。各エントリは根拠（`:basis`）と
  確認日（`:checked`）と出典（`:source-url`）を必ず持つ。

  **未知のライセンスは何も渡せない（`[]`）として扱う。** 判断が無いことを
  「制限が無い」と読み替えない —— 締める側に倒す。

  ## 表を足すとき

  1. ライセンス本文を実際に読み、日付を `:checked` に入れる。
  2. **再配布・サブライセンスが認められているか**を見る。使用許諾だけなら
     `:grantable []`。
  3. **独占を渡せるか**を見る。公衆ライセンス（CC 等）は誰にでも同じ権利が
     出ているので、独占は原理的に成立しない。
  4. 根拠を1〜2文で `:basis` に書く。書けないなら、まだ表に入れない。"
  (:require [ongaku.rights :as rights]))

(def license-holdings
  "`:license/id` → licensee が第三者へ渡せる権利。

  値の `:grantable` は `ongaku.rights/right` の引数 map の列。空なら何も渡せない。"
  {:license/dova-syndrome-license
   {:grantable []
    :exclusive-possible? false
    :checked "2026-06-29"
    :source-url "https://dova-s.jp/contents/license"
    :basis
    (str "自分の動画等での利用を認める使用許諾であって、原盤の再配布・"
         "サブライセンスは認められていない（raw-file redistribution / static "
         "exposure が禁止されている以上、依頼主へ素材として渡す経路が無い）。"
         "したがって第三者へ渡せる権利は無い。自分の render に混ぜて使うことは "
         "ongaku.policy の側で別途 gate される。")}

   :license/cc-by-4.0
   {:grantable [{:kind :sync :exclusive? false}]
    :exclusive-possible? false
    :checked "2026-06-29"
    :source-url "https://creativecommons.org/licenses/by/4.0/legalcode"
    :basis
    (str "CC BY 4.0 は worldwide / royalty-free / non-exclusive / irrevocable で"
         "複製・改変・再配布（商用含む）を認めるので、依頼主が使うこと自体は成立する"
         "（帰属表示が条件）。ただし §2(a)(5)(A) により下流の受領者は原ライセンサから"
         "**直接**ライセンスを受けるので、studio が独占を渡すことは原理的にできない"
         "——同じ権利が既に全世界に出ている。")}})

(defn holdings-for-license
  "`:license/id` に対する holdings エントリ。未知なら nil。"
  [license-id]
  (get license-holdings license-id))

(defn grantable-rights
  "カタログ資産について第三者へ渡せる権利の vector。

  **未知のライセンス・ライセンス未記載はどちらも `[]`**（何も渡せない）。
  これは fail-closed の既定であって「制限が無い」ではない。"
  [asset]
  (if-let [h (holdings-for-license (:license/id asset))]
    (mapv rights/right (:grantable h))
    []))

(defn attribution-required?
  "帰属表示が要るか。カタログの明示値を優先し、無ければライセンスから引く。"
  [asset]
  (if (contains? asset :license/attribution-required?)
    (boolean (:license/attribution-required? asset))
    (= :license/cc-by-4.0 (:license/id asset))))

(defn explain
  "なぜその資産についてそれしか渡せないのかを人が読める形で返す。

  `:not-held` で受注が落ちたときに、governor や台帳の担当者へ理由を出すため。"
  [asset]
  (let [lid (:license/id asset)
        h (holdings-for-license lid)]
    (cond
      (nil? lid)
      {:license/id nil
       :grantable []
       :reason "ライセンスが記載されていない資産なので、渡せる権利は無いものとして扱う"}

      (nil? h)
      {:license/id lid
       :grantable []
       :reason (str "未知のライセンス " lid " —— 判断が無いので何も渡せないものとして扱う。"
                    "ongaku.holdings/license-holdings に根拠つきで足すこと")}

      :else
      {:license/id lid
       :grantable (mapv rights/right (:grantable h))
       :exclusive-possible? (:exclusive-possible? h)
       :checked (:checked h)
       :source-url (:source-url h)
       :reason (:basis h)})))
