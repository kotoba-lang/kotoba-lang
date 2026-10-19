(ns kagi.import.onepassword
  "1Password `.1pux` export を kagi vault item として取り込む glue 層。

  kagitaba(crypto/store 非依存の純粋データモデル + 1PUX パーサ)が返す item を
  受け取り、その場で `pr-str`→bytes 化するだけ。復号・封緘は一切ここでは行わない
  ——`kagi.operation` の `:item/create` 副作用ノード(唯一の書込経路)に渡すのが
  この ns の仕事の全て。kagitaba item はこの ns の外(呼び出し元のローカル変数)
  にも、vault にも、平文のまま残らない。"
  (:require [clojure.string :as str]
            [kagitaba.import.onepux-file :as onepux-file]))

(defn slugify
  "compartment 名/item-id の既定導出。1Password vault 名 → kebab-case slug。"
  [s]
  (let [slug (-> (str/lower-case (or s ""))
                (str/replace #"[^a-z0-9]+" "-")
                (str/replace #"^-+|-+$" ""))]
    (if (str/blank? slug) "default" slug)))

(defn- unique-item-id [taken base]
  (if-not (contains? @taken base)
    (do (swap! taken conj base) base)
    (loop [n 2]
      (let [candidate (str base "-" n)]
        (if (contains? @taken candidate)
          (recur (inc n))
          (do (swap! taken conj candidate) candidate))))))

(defn plan
  "`.1pux` path を読み、副作用を一切起こさずに
  `{:warnings [...] :entries [{:item-id :compartment :category :title :plaintext}]}`
  を返す。呼び出し側(CLI)はこの entries を `:item/create` に 1:1 で渡すだけでよい。

  opts:
    :compartment-fn  vault 名(String) -> compartment 文字列。既定は `slugify`。
    :existing-ids    衝突を避けるための既存 item-id 集合(既定 #{})。
    :include-archived? true なら 1Password 側で archived の item も取り込む(既定 false)。"
  [path & [{:keys [compartment-fn existing-ids include-archived?]
            :or {compartment-fn slugify existing-ids #{} include-archived? false}}]]
  (let [{:keys [vaults warnings]} (onepux-file/load-1pux path)
        taken (atom (set existing-ids))
        entries (vec (for [{vault-name :name items :items} vaults
                            it items
                            :when (or include-archived? (= :active (:item/state it)))]
                       (let [base (let [t (slugify (:item/title it))]
                                    (if (str/blank? t) (or (:item/id it) "item") t))
                             item-id (unique-item-id taken base)]
                         {:item-id item-id
                          :compartment (compartment-fn vault-name)
                          :category (:item/category it)
                          :title (:item/title it)
                          :plaintext (.getBytes (pr-str (dissoc it :item/id)) "UTF-8")})))]
    {:warnings warnings :entries entries}))
