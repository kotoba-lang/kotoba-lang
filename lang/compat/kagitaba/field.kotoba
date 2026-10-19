(ns kagitaba.field
  "1Password 互換の section field 型(item 内の個々の値の型システム)。

  1PUX の `details.sections[].fields[].value` は単一キーの tagged union
  (例: `{\"concealed\": \"...\"}` `{\"address\": {...}}`)として直列化される。
  ここではそのキー集合を正準 keyword として持つ(検証は複数 OSS 1PUX パーサ実装の
  デシリアライズ定義と突き合わせ済み)。")

(def value-types
  "サポートする field 値型の集合。`:unknown` は 1Password が将来追加する
  未知の型を黙って落とさないための catch-all。"
  #{:string :concealed :email :url :totp :phone :date :month-year :gender
    :menu :address :credit-card-type :credit-card-number :reference
    :file :ssh-key :sso-login :unknown})

(def sensitive-types
  "平文のままインデックス/graph に置いてはいけない値型。kagi 側はこの集合の
  field を含む item を必ず暗号化ペイロード(DEK 封緘 blob)側に置く
  ——:string/:menu 等の非機微フィールドと同じ経路には出さない。"
  #{:concealed :totp :credit-card-number :ssh-key})

(defn sensitive? [field-type] (contains? sensitive-types field-type))

(defn classification
  "Canonical information-flow label. Unknown future field types fail closed."
  [field-type]
  (cond
    (contains? sensitive-types field-type) :restricted
    (contains? value-types field-type) :internal
    :else :restricted))

(def login-field-designations
  "`details.loginFields[].designation` に現れる値(1Password の Login category
  専用フィールド、`sections` の外に別枠で置かれる)。"
  #{:username :password})

(defn value-type
  "field 値の tagged-union map(単一キー)から型 keyword を取り出す。
  複数キー/未知キーは :unknown にフォールバック(データは呼び出し側が
  raw のまま保持できるよう、この fn は分類のみ行い値は捨てない)。"
  [value-map]
  (if (and (map? value-map) (= 1 (count value-map)))
    (let [k (name (first (keys value-map)))]
      (or (get {"string" :string "concealed" :concealed "email" :email
                "url" :url "totp" :totp "phone" :phone "date" :date
                "monthYear" :month-year "gender" :gender "menu" :menu
                "address" :address "creditCardType" :credit-card-type
                "creditCardNumber" :credit-card-number "reference" :reference
                "file" :file "sshKey" :ssh-key "ssoLogin" :sso-login}
               k)
          :unknown))
    :unknown))
