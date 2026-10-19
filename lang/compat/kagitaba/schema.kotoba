(ns kagitaba.schema
  "カテゴリ別のデフォルト field テンプレート(新規 item を作るときの雛形)。

  **注意**: 1Password はカテゴリごとの正式なフィールド定義を公開していない
  (1PUX ドキュメントは `categoryUuid` の例を 1 つ示すのみ)。ここに定義する
  テンプレートは 1Password アプリの実 UI から観測した **best-effort な代表値**で、
  import の正しさには影響しない(import は各 item が実際に持つ section/field を
  そのまま写すだけで、このテンプレートを一切参照しない)。用途は
  「新規 item を作るときの雛形」「未知フィールドが本当に未知かの目安」の 2 つに限る。"
  (:require [kagitaba.contract :as contract]))

(def templates
  "category key → デフォルト section/field 雛形(`kagitaba.item/item*` の
  :sections 引数にそのまま渡せる shape)。網羅ではなく代表カテゴリのみ。"
  {:login
   [{:title "Login" :fields [{:id "username" :title "username" :type :string}
                              {:id "password" :title "password" :type :concealed}
                              {:id "otp" :title "one-time password" :type :totp}]}]

   :password
   [{:title "Password" :fields [{:id "password" :title "password" :type :concealed}
                                 {:id "otp" :title "one-time password" :type :totp}]}]

   :secure-note
   []                                   ; 本文は :item/notes(notesPlain 相当)のみ

   :credit-card
   [{:title "Card" :fields [{:id "cardholder" :title "cardholder name" :type :string}
                             {:id "type" :title "type" :type :credit-card-type}
                             {:id "ccnum" :title "number" :type :credit-card-number}
                             {:id "cvv" :title "verification number" :type :concealed}
                             {:id "expiry" :title "expiry date" :type :month-year}
                             {:id "pin" :title "PIN" :type :concealed}]}]

   :identity
   [{:title "Identification"
     :fields [{:id "firstname" :title "first name" :type :string}
              {:id "lastname" :title "last name" :type :string}
              {:id "sex" :title "sex" :type :gender}
              {:id "birthdate" :title "birth date" :type :date}
              {:id "occupation" :title "occupation" :type :string}]}
    {:title "Address" :fields [{:id "address" :title "address" :type :address}]}]

   :server
   [{:title "Login" :fields [{:id "url" :title "URL" :type :string}
                              {:id "username" :title "username" :type :string}
                              {:id "password" :title "password" :type :concealed}]}]

   :database
   [{:title "Database" :fields [{:id "hostname" :title "server" :type :string}
                                 {:id "port" :title "port" :type :string}
                                 {:id "database" :title "database" :type :string}
                                 {:id "username" :title "username" :type :string}
                                 {:id "password" :title "password" :type :concealed}]}]

   :ssh-key
   [{:title "Key" :fields [{:id "private-key" :title "private key" :type :ssh-key}
                            {:id "public-key" :title "public key" :type :reference}
                            {:id "fingerprint" :title "fingerprint" :type :reference}]}]

   :api-credential
   [{:title "Credential" :fields [{:id "username" :title "username" :type :string}
                                   {:id "credential" :title "credential" :type :concealed}
                                   {:id "hostname" :title "hostname" :type :string}
                                   {:id "expires" :title "expires" :type :date}]}]

   :bank-account
   [{:title "Account" :fields [{:id "bank-name" :title "bank name" :type :string}
                                {:id "account-number" :title "account number" :type :concealed}
                                {:id "routing-number" :title "routing number" :type :string}
                                {:id "pin" :title "PIN" :type :concealed}]}]

   ;; 継続課金のあるサービスは Login + Contract の 2 section を 1 item に持つ。
   ;; ログインと契約を別 item に割ると片方だけ更新されて食い違う(kagitaba.contract)。
   :membership
   (into [{:title "Membership"
           :fields [{:id "org" :title "group" :type :string}
                    {:id "member-name" :title "member name" :type :string}
                    {:id "member-number" :title "member number" :type :string}]}
          {:title "Login"
           :fields [{:id "username" :title "username" :type :string}
                    {:id "password" :title "password" :type :concealed}
                    {:id "otp" :title "one-time password" :type :totp}]}]
         contract/template)})

(defn for-category [k] (get templates k []))
