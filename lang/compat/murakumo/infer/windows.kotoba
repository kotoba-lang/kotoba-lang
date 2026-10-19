(ns murakumo.infer.windows
  "plan のレート上限 —— 5 時間 + 7 日のローリング窓（ADR-2608026600）。

  ── なぜ credits ではないのか ──

  subscription plan は **credits を配らない**。配るのは『アクセス + レート上限』で、
  窓が過ぎれば回復する。ChatGPT/Codex も Claude Code も同じ構造で、Codex は
  『API キー経由は両方の cap を通らず ChatGPT credits も消費しない』と明示している。

  この形にすると、ADR-2608026200 §5-3-2 が保留していた問題が 3 つ同時に消える:

  - **非繰越の実装** —— 窓のカウンタは残高ではないので、繰り越す対象が無い
  - **Delaware escheat**（stored value 5 年、住所不明なら設立州）—— 前払残高が
    無いので escheat の対象物が無い
  - **CARD Act の期限下限 / ASC 606 の breakage 認識不可** —— 未使用の窓は
    『使わなかった容量』であって前払金ではない

  ⚠ **この ns の消費イベントを `murakumo.infer.credits/balances` の fold に
  混ぜてはならない。** 混ぜた瞬間に 2 つのプールが 1 つの balance に同居し、
  『どの spend が窓を食い、どれが stored value を食ったか』という帰属問題が
  復活する —— それを消すのがこの設計の要点。`credits-admission/normalize-run`
  の event-keys にも足さない（allowance で踏んだ罠の裏返しで、**今度は
  足さないことが正しい**）。

  純関数のみ。時刻は呼び出し側が渡す（`now` はミリ秒 epoch）。")

(def window-specs
  "ローリング窓の定義。ADR-2608026600 §3。

   :short = 5 時間 —— 1 バーストで週を焼き切らせないための床。
            週次だけだと 1 人が 1 時間で 1 週間分を消し、共有容量を枯渇させられる
            （Claude Code の文書が短窓の理由としてこれを挙げている）。
   :week  = 7 日 —— 実際の予算。

   どちらも**ローリング**（月曜リセットではない）。最初の消費から数えて
   5 時間後 / 7 日後に、その分だけ回復する。"
  {:short {:ms (* 5 60 60 1000)      :label "5 時間"}
   :week  {:ms (* 7 24 60 60 1000)   :label "7 日"}})

(def short-window-share
  "5 時間窓 = 週次窓の何割か。

   ⚠ **根拠のある数字ではない**（ADR-2608026600 リスク 5）。『1 バーストで週を
   焼き切らせない』という要件を満たす最も単純な値として置いた —— 0.25 なら
   4 セッションで週を使い切れる。実利用のバースト分布を見て調整する前提で、
   **実測前にこの値を最適値として引用しない**。"
  0.25)

(defn plan-limits
  "plan SKU → {:short n :week n}。`:plan/units-per-week` から短窓を導出する。"
  [sku]
  (when-let [w (:plan/units-per-week sku)]
    {:week w
     :short (or (:plan/units-per-5h sku)
                (long (+ 0.5 (* w short-window-share))))}))

;; ---- 消費イベント -----------------------------------------------------------

(defn usage-event
  "1 ジョブの plan 消費イベント（純データ。呼び出し側が feed に append する）。

   `units` は掲示価格と同じ目盛り（`video/oss` なら 10 unit/秒）だが、
   **残高ではなくカウンタ**である —— 積み上がらない・譲渡できない・
   買い足せない・現金価値が無い。"
  [account units at {:keys [job modality model] :as _meta}]
  (when-not (pos? units)
    (throw (ex-info "plan usage must be positive" {:account account :units units})))
  {:plan.usage/account (name account)
   :plan.usage/units (double units)
   :plan.usage/at at
   :plan.usage/job job
   :plan.usage/modality modality
   :plan.usage/model model})

(defn usage-event? [e] (boolean (:plan.usage/account e)))

(defn- account-of [e]
  (or (:plan.usage/account e) (get e "plan.usage/account")))
(defn- units-of [e]
  (or (:plan.usage/units e) (get e "plan.usage/units") 0))
(defn- at-of [e]
  (or (:plan.usage/at e) (get e "plan.usage/at") 0))

(defn credits-feed
  "credits の fold に渡してよいイベントだけを残す（plan 消費を除去する）。

   **必要な理由**: `credits/balances-step` の `:else` 枝は未知のイベントを
   settle 済み run として扱うので、plan 消費イベントが誤って credits feed に
   混ざると、口座残高は動かないものの `head` / `:treasury` の phantom キーが
   生える（2026-08-03 の windows-test が実測で捕まえた）。

   `balances-step` 自体は触らない —— その docstring が『既定を変えると過去の
   run の意味を遡って書き換える』と警告しているとおり、append-only feed を
   replay する fold の分岐は歴史的契約である。**入口で分けるのが正しい。**"
  [feed]
  (into [] (remove usage-event?) feed))

(defn plan-feed
  "plan の窓集計に渡すイベントだけを残す（credits イベントを除去する）。"
  [feed]
  (into [] (filter usage-event?) feed))

;; ---- 窓の集計 ---------------------------------------------------------------

(defn consumed
  "`account` が `window`（:short / :week）の中で消費した unit 合計。

   ローリングなので『now から window-ms 以内』の消費だけを数える。
   window 外に落ちた分は自動的に回復する —— これが『繰り越す対象が無い』
   ということの実装上の意味。"
  [feed account window now]
  (let [span (get-in window-specs [window :ms])
        cutoff (- now span)
        a (name account)]
    (reduce (fn [acc e]
              (if (and (usage-event? e)
                       (= a (account-of e))
                       (> (at-of e) cutoff))
                (+ acc (double (units-of e)))
                acc))
            0.0 feed)))

(defn recovers-at
  "`window` の中で最も古い消費が窓から落ちる時刻（= その分が回復する時刻）。
   窓内に消費が無ければ nil。

   429 に必ず載せる —— ローリング窓は『いつ回復するか』が自明でないので、
   答えられないと苦情になる（ADR-2608026600 リスク 4）。"
  [feed account window now]
  (let [span (get-in window-specs [window :ms])
        cutoff (- now span)
        a (name account)
        ts (for [e feed
                 :when (and (usage-event? e) (= a (account-of e)) (> (at-of e) cutoff))]
             (at-of e))]
    (when (seq ts) (+ (apply min ts) span))))

(defn status
  "account の現在地。UI と 429 の両方がこれを読む。

   → {:window {:short {:limit n :used n :remaining n :recovers-at ms}
               :week  {...}}}

   **`:remaining` を『残高』として表示しない。** 表示は
   『今週あと N unit 使えます（M 時間後に回復）』であって残高ではない
   —— 残高のように見せた瞬間に stored value に化ける（ADR-2608026600 リスク 1）。"
  [feed account limits now]
  {:window
   (into {}
         (for [w [:short :week]
               :let [lim (get limits w)
                     used (consumed feed account w now)]]
           [w {:limit lim
               :used used
               :remaining (max 0.0 (- lim used))
               :recovers-at (recovers-at feed account w now)}]))})

;; ---- admission --------------------------------------------------------------

(defn admit
  "この job の `units` を今受け付けてよいか。

   → {:allow? true :status s}
   | {:allow? false :window :short|:week :retry-after-ms n :recovers-at ms :status s}

   **両方の窓を見る。** 短窓だけ見ると週を守れず、週だけ見ると 1 バーストで
   週を焼き切られる（§3 の二重窓の理由そのもの）。

   `job-cost` が価格未登録で例外を投げるのと同じく、**limits が無ければ例外**
   —— 上限を知らないまま通すのは『沈黙 = 無制限』であり、この repo が
   繰り返し潰してきた欠陥クラス。"
  [feed account units limits now]
  (when-not (and (:short limits) (:week limits))
    (throw (ex-info "plan has no rate limits — refusing to admit"
                    {:account (name account) :limits limits})))
  (let [st (status feed account limits now)
        ;; 短窓より大きい 1 ジョブは、短窓では **待っても永久に入らない**。
        ;; そこで 429 + retry-after を返すと、来ない回復を約束することになる。
        ;;
        ;; 実測（2026-08-03、この分岐が生まれた瞬間）: Plus は 1,140 unit/週 →
        ;; 短窓 285 に対し、Seedance 2.0 の 5 秒動画が 61×5 = 305 unit。
        ;; 短窓で弾くと **Plus 契約者は frontier 動画を 1 本も作れない**。
        ;; 窓を結線して初めて出た欠陥で、窓の算数も plan の数値もそれぞれは
        ;; 正しかった —— 分割不能なジョブが単位より大きい、という関係だけが
        ;; どちらからも見えていなかった。
        ;;
        ;; 短窓の役割は『週を 1 バーストで焼かせない』こと。分割不能な 1 本に
        ;; ついてはその役割が原理的に果たせないので、週窓に委ねる。通した後は
        ;; 短窓が超過状態になるので、**次のジョブは普通に止まる** —— バースト
        ;; 防止は 1 ジョブ分だけ緩む。
        indivisible? (> units (get-in st [:window :short :limit] 0))
        over (first (for [w (if indivisible? [:week] [:short :week])
                          :let [{:keys [remaining recovers-at]} (get-in st [:window w])]
                          :when (> units remaining)]
                      [w recovers-at]))]
    (if-not over
      {:allow? true :status st}
      (let [[w rec] over]
        {:allow? false
         :window w
         :recovers-at rec
         :retry-after-ms (when rec (max 0 (- rec now)))
         :status st}))))

(defn retry-after-seconds
  "429 の `retry-after` ヘッダ値（秒、切り上げ）。"
  [verdict]
  (when-let [ms (:retry-after-ms verdict)]
    (long (Math/ceil (/ (double ms) 1000.0)))))

(defn explain
  "429 の本文に入れる人間向けの説明。回復時刻を必ず含める。"
  [verdict]
  (if (:allow? verdict)
    "ok"
    (let [w (:window verdict)
          label (get-in window-specs [w :label])
          {:keys [limit used]} (get-in verdict [:status :window w])
          secs (retry-after-seconds verdict)]
      (str label "の上限 " (long limit) " unit に達しました（使用 "
           (long used) "）。"
           (if secs
             (str "約 " (long (Math/ceil (/ (double secs) 60.0))) " 分後に回復します。")
             "")
           " 上限を超えて使う場合は API credits をご利用ください（レート窓を通りません）。"))))
