(ns sekisho.tenant
  "誰が誰か —— 組織 / 人 / いま動いている credential の 3 層を DID で表す。

  ## 既にある 2 層（ADR-2607252000、変更しない）

    accountDid  did:web:kotobase.net:tenant:<user>   人ひとりに 1 つ。鍵を持たず署名しない
    activeDid   did:key:z6Mk…                        いま動いている credential

  Clerk の User と Session に相当する分離は **既に設計・稼働している**。
  この ns が足すのは 3 層目だけ。

  ## 足りなかった層: 組織

  `did:web:kotobase.net:tenant:<user>` は **path segment が `tenant` なのに
  中身が user**。Clerk の Organizations に相当する層が無いので、
  『この人はどの組織の一員か』を DID で表せない。

  ## `tenant` を `user` に改名しない

  改名は正しく見えるが **やらない**。DID は発行済みの識別子で、
  改名は two-sided deploy を要し、**既に発行された accountDid を全部無効にする**。
  名前の正しさより識別子の安定を優先する —— 誤称は文書で説明できるが、
  壊れた識別子は説明できない。

  代わりに **新しい segment を足す**:

    orgDid      did:web:kotobase.net:org:<slug>      組織（テナント）

  ## 3 つを混ぜない理由

  それぞれ寿命と権限が違う:

    orgDid      長命。請求先・座席・ポリシーの所有者
    accountDid  人の一生に 1 つ。**鍵を持たない**ので漏れても署名できない
    activeDid   短命・複数可。実際に署名する鍵。失効も追加もできる

  1 つにまとめると『鍵を失った = 口座を失った』か『鍵を共有した = 人格を共有
  した』のどちらかになる。分けてあるのはそのため。"
  (:require [kotoba.lang.text :as str]))

(def authority
  "DID の権威。**増やさない。**

   ADR-2608039950: 第 2 authority を立てるのは、この設計で唯一取り返しの
   つかない選択。ブランド名は後から変えられるが、発行済み DID の namespace は
   two-sided deploy を要する。"
  "kotobase.net")

(def ^:private segments
  "種別 → DID の path segment。

   ⚠ `:account` が `tenant` なのは **歴史的経緯**（ADR-2607252000）。
   誤称だが発行済みなので変えない。"
  {:org "org" :account "tenant"})

(defn- slug? [s]
  (boolean (and (string? s) (re-matches #"[a-z0-9][a-z0-9_-]{0,62}" s))))

(defn ->did
  "種別 + slug → `did:web:<authority>:<segment>:<slug>`、不正なら nil。

   slug を検査するのは、**DID に任意の文字列を入れると別の DID に化ける**から。
   `:` を含む slug は path segment を増やし、`a:b` が `…:org:a:b` になって
   別の階層を指す。"
  [kind slug]
  (when-let [seg (segments kind)]
    (when (slug? slug)
      (str "did:web:" authority ":" seg ":" slug))))

(defn parse
  "`did:web:…` → `{:kind :org|:account :slug s}`、当てはまらなければ nil。

   authority が違えば nil —— **他の権威の DID を自分のものとして扱わない**。"
  [did]
  (when (string? did)
    (let [parts (str/split did #":")]
      (when (and (= 5 (count parts))
                 (= "did" (nth parts 0))
                 (= "web" (nth parts 1))
                 (= authority (nth parts 2)))
        (let [seg (nth parts 3)
              slug (nth parts 4)
              kind (some (fn [[k v]] (when (= v seg) k)) segments)]
          (when (and kind (slug? slug))
            {:kind kind :slug slug}))))))

(defn org-did [slug] (->did :org slug))
(defn account-did [slug] (->did :account slug))

(defn org? [did] (= :org (:kind (parse did))))
(defn account? [did] (= :account (:kind (parse did))))

;; ── 所属 ────────────────────────────────────────────────────────────────────

(def roles
  "組織内の役割。**厳しい順ではなく、能力の集合で表す** ——
   `:admin` が `:member` を含む、のような暗黙の階層を作ると、
   『どの role が何をできるか』が role の定義ではなく比較演算に散らばる。"
  #{:owner :admin :member :billing})

(defn membership
  "所属の 1 レコード（純データ）。

   `:since` を必須にするのは、**いつからの所属かが分からない所属は監査できない**
   から。座席課金も『いつから何席』が言えなければ計算できない。"
  [{:keys [org account role since]}]
  (when (and (org? org) (account? account) (roles role) since)
    {:sekisho/org org
     :sekisho/account account
     :sekisho/role role
     :sekisho/since since}))

(defn memberships-of
  "台帳から `account` の所属を引く（`org` 指定で 1 件に絞る）。

   台帳を引数で受け取り自分で持たない —— `saifu.policy` が累計消費を引数で
   取るのと同じ理由で、**2 プロセスが別々の写しを持つと権限が食い違う**。"
  ([ledger account] (filterv #(= account (:sekisho/account %)) ledger))
  ([ledger account org]
   (filterv #(and (= account (:sekisho/account %)) (= org (:sekisho/org %))) ledger)))

(defn role-in
  "`account` が `org` で持つ役割、または nil（非所属）。

   複数あれば nil ではなく **最初の 1 件**ではなく、**明示的に nil を返さない**
   —— 重複所属はデータの誤りなので、黙って 1 つ選ぶと誤りが隠れる。"
  [ledger account org]
  (let [ms (memberships-of ledger account org)]
    (case (count ms)
      0 nil
      1 (:sekisho/role (first ms))
      (throw (ex-info "同じ組織に複数の所属がある —— データの誤り"
                      {:account account :org org :count (count ms)})))))

(defn viewer
  "`authn` が返す viewer に組織を足した形。

   accountDid / activeDid は ADR-2607252000 の契約そのまま。**発明しない。**"
  [{:keys [account-did active-did handle org role]}]
  (cond-> {:sekisho/account account-did
           :sekisho/active active-did
           :sekisho/handle handle}
    org (assoc :sekisho/org org :sekisho/role role)))
