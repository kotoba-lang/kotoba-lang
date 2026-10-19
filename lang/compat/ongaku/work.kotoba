(ns ongaku.work
  "作品（work）— 何をどう作ったかと、そこから何が納品できるか。

  `:work/provenance` がこの namespace の中心概念。作曲の実体は 2 系統に
  分かれている:

  - `:authored`  — 人が `kami-ongaku-*` stack で書いたもの。DAW セッション
                   （`kami-ongaku-project`）・譜面（`kami-ongaku-notation`）・
                   MIDI（`kami-ongaku-sequencer`）が実在する。
  - `:generated` — `ai.gftd.ongakuka.compose` の生成パイプライン
                   （lyricist→composer→vocalist‖arranger→mixer→critic）が
                   出したもの。契約 model は `kotoba-lang/composer`。
                   出るのは master audio と stems であって、譜面や
                   DAW セッションは存在しない。
  - `:hybrid`    — 生成したものを人が DAW で手直ししたもの。

  この区別は美学の話ではなく**納品できる物が違う**という商売の話で、
  そこを検査するのがこの namespace の仕事。"
  (:require [clojure.string :as str]))

(def provenances
  {:authored  "人が kami-ongaku-* stack で書いた"
   :generated "ai.gftd.ongakuka.compose が生成した"
   :hybrid    "生成物を人が DAW で手直しした"
   ;; カタログから license を受けて持っているだけの第三者録音。手元に在るのは
   ;; ミックス済みの音声 1 本で、stem も譜面も DAW セッションも**この studio の
   ;; 手元には無い**（作った人のところには在るだろうが、それは別の話）。
   ;; 何を「渡せる」かは provenance ではなく `ongaku.holdings` が別に決める ——
   ;; provenance が答えるのは「どの成果物が実在するか」だけ。
   :licensed  "第三者の録音を license で使っている（手元にあるのは音声のみ）"})

(def deliverable-kinds
  "納品物の種別と、その正本を持つ craft library。"
  {:master-audio {:label "マスター音源" :craft nil}
   :stems        {:label "ステム" :craft "kotoba-lang/composer"}
   :midi         {:label "MIDI (SMF)" :craft "kotoba-lang/kami-ongaku-sequencer"}
   :score        {:label "譜面 (MusicXML)" :craft "kotoba-lang/kami-ongaku-notation"}
   :session      {:label "DAW セッション" :craft "kotoba-lang/kami-ongaku-project"}})

(def ^:private producible
  "provenance ごとに、実際に生成できる納品物。

  `:generated` に `:score` / `:midi` / `:session` が無いのは方針ではなく
  事実 — 生成パイプラインの出力は音声と stems だけで、譜面や MIDI は
  そもそも存在しない。`:licensed` が音声 1 本だけなのも同じく事実で、
  ライセンスを受けたのはミックス済みの録音であって素材ではない。"
  {:authored  #{:master-audio :stems :midi :score :session}
   :generated #{:master-audio :stems}
   :hybrid    #{:master-audio :stems :midi :score :session}
   :licensed  #{:master-audio}})

(defn producible-kinds
  "この provenance で実際に納品できる種別の集合。"
  [provenance]
  (get producible provenance #{}))

(defn work
  "作品を1件つくる。"
  [{:keys [id title provenance model-id disclosure held-rights]}]
  {:work/id id
   :work/title title
   :work/provenance provenance
   :work/model-id model-id
   :work/disclosure disclosure
   :work/held-rights (vec held-rights)})

(defn ai-involved?
  [work]
  (contains? #{:generated :hybrid} (:work/provenance work)))

(defn validate-work
  "作品レコードの検証。問題が無ければ `nil`、あれば問題の vector。

  AI が関与した作品は `:work/model-id` と `:work/disclosure` を必ず持つ
  ——どのモデルが作ったかを記録せずに納品することはできない
  （`ai.gftd.ongakuka.track` の `modelId` と同じ要求）。"
  [work]
  (let [problems
        (cond-> []
          (str/blank? (str (:work/id work)))
          (conj {:problem/type :missing-id})

          (not (contains? provenances (:work/provenance work)))
          (conj {:problem/type :unknown-provenance
                 :problem/value (:work/provenance work)})

          (and (ai-involved? work) (str/blank? (str (:work/model-id work))))
          (conj {:problem/type :missing-model-id
                 :problem/message "AI が関与した作品は model-id を記録しなければ納品できない"})

          (and (ai-involved? work) (str/blank? (str (:work/disclosure work))))
          (conj {:problem/type :missing-disclosure
                 :problem/message "AI が関与した作品は開示文を持たなければ納品できない"}))]
    (when (seq problems) problems)))

(defn validate-deliverables
  "作品の provenance で実際に納品できない種別が要求されていないか。

  純 AI 生成の作品に『譜面と MIDI つき』を約束することは、守れない
  約束なので構造的に拒否する。"
  [work requested-kinds]
  (let [ok (producible-kinds (:work/provenance work))
        impossible (remove ok requested-kinds)
        unknown (remove deliverable-kinds requested-kinds)
        problems
        (cond-> []
          (seq unknown)
          (conj {:problem/type :unknown-deliverable-kind
                 :problem/kinds (vec unknown)})

          (seq (remove (set unknown) impossible))
          (conj {:problem/type :not-producible
                 :problem/message
                 (str (name (:work/provenance work)) " の作品からは "
                      (str/join "," (map name (remove (set unknown) impossible)))
                      " を納品できない")
                 :problem/kinds (vec (remove (set unknown) impossible))}))]
    (when (seq problems) problems)))
