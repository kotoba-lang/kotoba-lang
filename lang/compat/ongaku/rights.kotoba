(ns ongaku.rights
  "原盤権・著作権・出版権の保有と譲渡のモデル、および譲渡の構造的拒否。

  この namespace が存在する理由は一つ: **持っていない権利は売れない**。
  既製カタログ資産（DOVA-SYNDROME / Incompetech 等）について studio が
  持つのは使用許諾だけで原盤権ではないから、その音源を素材にした納品で
  `:master` を exclusive 譲渡することは商売として成立しない。

  `ongaku.policy` との対。**policy は「使い方」を下流で gate し、rights は
  「渡し方」を上流で gate する。** どちらも catalog-agnostic /
  business-agnostic な純関数で、保有権利は呼び出し側が持ち込む
  （`ongaku.catalog` に catalog を持ち込むのと同じ形）。ADR-2607023000 の
  3 層でいう技芸層に属し、事業データはここに置かない。

  Portable `.cljc` — JVM / ClojureScript / nbb で同じに動く。日付は
  ISO-8601 の文字列として比較する（辞書順 = 時系列順）。"
  (:require [clojure.string :as str]))

;; --- 権利の種別 ------------------------------------------------------------

(def right-kinds
  "扱う権利の種別。値は日本の音楽実務の呼称に対応する。"
  {:master      "原盤権（録音物に対する権利）"
   :composition "著作権（作曲・作詞）"
   :publishing  "出版権"
   :sync        "同期使用権（映像作品への使用）"
   :mechanical  "録音権"})

(def ^:private worldwide :worldwide)
(def ^:private perpetual :perpetual)

;; --- 構築 ------------------------------------------------------------------

(defn right
  "権利を1件つくる。

  `:right/territory` は ISO 3166-1 alpha-2 の集合、または `:worldwide`。
  `:right/term` は `{:term/from \"YYYY-MM-DD\" :term/until \"YYYY-MM-DD\"}`
  または `:perpetual`。`:right/exclusive?` は既定 false（非独占）。"
  [{:keys [kind territory term exclusive?]}]
  (cond-> {:right/kind kind
           :right/territory (or territory worldwide)
           :right/term (or term perpetual)
           :right/exclusive? (boolean exclusive?)}))

;; --- 包含判定 --------------------------------------------------------------

(defn territory-covers?
  "held の地域が requested の地域を包含するか。"
  [held requested]
  (cond
    (= held worldwide) true
    (= requested worldwide) false
    :else (every? (set held) requested)))

(defn- term-end [term]
  (if (= term perpetual) nil (:term/until term)))

(defn- term-start [term]
  (if (= term perpetual) nil (:term/from term)))

(defn term-covers?
  "held の期間が requested の期間を包含するか。`:perpetual` は全期間。"
  [held requested]
  (cond
    (= held perpetual) true
    (= requested perpetual) false
    :else (let [hs (term-start held) he (term-end held)
                rs (term-start requested) re (term-end requested)]
            (and (<= (compare hs rs) 0)
                 (>= (compare he re) 0)))))

(defn- terms-overlap?
  [a b]
  (cond
    (or (= a perpetual) (= b perpetual)) true
    :else (and (neg? (compare (term-start a) (term-end b)))
               (neg? (compare (term-start b) (term-end a))))))

(defn- territories-overlap?
  [a b]
  (cond
    (or (= a worldwide) (= b worldwide)) true
    :else (boolean (seq (filter (set a) b)))))

(defn covers?
  "held が requested を完全に包含するか（種別・独占性・地域・期間すべて）。

  非独占しか持っていない権利を独占で譲渡することはできない。"
  [held requested]
  (and (= (:right/kind held) (:right/kind requested))
       (or (:right/exclusive? held) (not (:right/exclusive? requested)))
       (territory-covers? (:right/territory held) (:right/territory requested))
       (term-covers? (:right/term held) (:right/term requested))))

(defn conflicts?
  "既存の譲渡 `a` と新しい譲渡 `b` が両立しないか。

  同一種別で、どちらかが独占で、地域と期間が重なるなら衝突。"
  [a b]
  (and (= (:right/kind a) (:right/kind b))
       (or (:right/exclusive? a) (:right/exclusive? b))
       (territories-overlap? (:right/territory a) (:right/territory b))
       (terms-overlap? (:right/term a) (:right/term b))))

;; --- 検証 ------------------------------------------------------------------

(defn- describe [r]
  (str (name (:right/kind r))
       (if (:right/exclusive? r) "(独占)" "(非独占)")
       " " (if (= (:right/territory r) worldwide)
             "worldwide"
             (str/join "," (sort (:right/territory r))))))

(defn validate-grant
  "`held`（保有権利の集合）と `existing-grants`（既発の譲渡）のもとで
  `requested` を譲渡してよいかを検証する。

  問題が無ければ `nil`、あれば問題の vector を返す（`ongaku` の
  license gate と同じく、返り値が真なら呼び出し側は譲渡してはならない）。"
  ([held requested] (validate-grant held requested nil))
  ([held requested existing-grants]
   (let [problems
         (cond-> []
           (not (contains? right-kinds (:right/kind requested)))
           (conj {:problem/type :unknown-right-kind
                  :problem/kind (:right/kind requested)})

           (not-any? #(covers? % requested) held)
           (conj {:problem/type :not-held
                  :problem/message (str "保有していない権利は譲渡できない: "
                                        (describe requested))
                  :problem/requested requested})

           (some #(conflicts? % requested) existing-grants)
           (conj {:problem/type :exclusive-conflict
                  :problem/message (str "既存の独占譲渡と衝突する: "
                                        (describe requested))
                  :problem/conflicting
                  (vec (filter #(conflicts? % requested) existing-grants))}))]
     (when (seq problems) problems))))

(defn grantable?
  "`validate-grant` が問題を返さないか。"
  ([held requested] (grantable? held requested nil))
  ([held requested existing-grants]
   (nil? (validate-grant held requested existing-grants))))
