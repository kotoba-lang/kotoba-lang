(ns kagitaba.category
  "1Password 互換の item category(鍵束の \"引き出し\" タクソノミー)。

  `categoryUuid` は 1Password の 1PUX/CLI/API 全体で使われる安定 ID。ここでの
  keyword(`:login` 等)が kagitaba/kagi 側の正準表現で、uuid は import/export の
  wire フォーマットとの往復にのみ使う。

  uuid 一覧は 1Password 公式ドキュメント(support.1password.com/1pux-format/)には
  `001`(Login)の例示しか無く全表は非公開のため、実 export ファイルと複数の
  独立 1PUX パーサ実装(例: OSS `import-1p-to-pass` の `src/category.rs`)を突き合わせて
  検証した値。101〜114 系(Bank Account 以降)は 1Password が後年追加したカテゴリ。")

(def categories
  "categoryUuid → {:key :label} の正準テーブル。"
  {"001" {:key :login :label "Login"}
   "002" {:key :credit-card :label "Credit Card"}
   "003" {:key :secure-note :label "Secure Note"}
   "004" {:key :identity :label "Identity"}
   "005" {:key :password :label "Password"}
   "006" {:key :document :label "Document"}
   "100" {:key :software-license :label "Software License"}
   "101" {:key :bank-account :label "Bank Account"}
   "102" {:key :database :label "Database"}
   "103" {:key :driver-license :label "Driver License"}
   "104" {:key :outdoor-license :label "Outdoor License"}
   "105" {:key :membership :label "Membership"}
   "106" {:key :passport :label "Passport"}
   "107" {:key :reward-program :label "Reward Program"}
   "108" {:key :social-security-number :label "Social Security Number"}
   "109" {:key :wireless-router :label "Wireless Router"}
   "110" {:key :server :label "Server"}
   "111" {:key :email-account :label "Email Account"}
   "112" {:key :api-credential :label "API Credential"}
   "113" {:key :medical-record :label "Medical Record"}
   "114" {:key :ssh-key :label "SSH Key"}})

(def by-key
  ":key → uuid の逆引き。"
  (into {} (map (fn [[uuid {:keys [key]}]] [key uuid])) categories))

(def known-keys (set (keys by-key)))

(defn uuid->key
  "categoryUuid → 正準 keyword。未知の uuid は `:category/unknown` の
  vector [:unknown uuid] ではなく、素直に `(keyword \"category\" uuid)` に
  フォールバックする — インポートは絶対にデータを黙って落とさない。"
  [uuid]
  (or (get-in categories [uuid :key])
      (keyword "category" (str uuid))))

(defn key->uuid
  "正準 keyword → categoryUuid。未知 key は nil(export 側で明示的に扱う)。"
  [k]
  (get by-key k))

(defn label [k]
  (get-in categories [(key->uuid k) :label] (name k)))

(defn known? [k] (contains? known-keys k))
