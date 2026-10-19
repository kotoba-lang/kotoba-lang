(ns kagitaba.item
  "kagitaba の正準 item shape(鍵束の 1 エントリ)。1Password の
  overview/details/sections/fields 構造を、暗号にもストレージにも依存しない
  素の EDN として表す。

  この ns は **平文を扱ってよい唯一の層**であることに注意 — item はここでは
  ただの immutable map で、暗号化するかどうかは呼び出し側(kagi 等)の責務。
  kagitaba 自体は crypto/store への依存を一切持たない(zero-dep, .cljc 可搬)。"
  (:require [kagitaba.category :as category]
            [kagitaba.field :as field]))

(defn field*
  "1 field を正準形にする。:id が無ければ :title を id 代わりに使う。"
  [{:keys [id title type value]}]
  {:field/id (or id title)
   :field/title title
   :field/type (or type :unknown)
   :field/value value
   :field/classification (field/classification (or type :unknown))
   :field/sensitive? (field/sensitive? (or type :unknown))})

(defn section*
  [{:keys [id title fields]}]
  {:section/id id
   :section/title title
   :section/fields (mapv field* fields)})

(defn item*
  "正準 item を組み立てる。:category と :title 以外は省略可(import が
  1 フィールドずつ段階的に埋められるように、全て欠損に寛容)。"
  [{:keys [id category title tags favorite? url urls state username notes
           sections password-history]
    :or {tags [] sections [] password-history [] state :active favorite? false}}]
  {:item/id id
   :item/category category
   :item/title title
   :item/tags (vec tags)
   :item/favorite? (boolean favorite?)
   :item/url url
   :item/urls (vec urls)
   :item/state state
   :item/username username
   :item/notes notes
   :item/sections (mapv section* sections)
   :item/password-history (vec password-history)})

(def ^:private required-keys #{:item/category :item/title})

(defn valid?
  "最小限の構造検証(スキーマライブラリには依存しない)。:item/category と
  :item/title が非空であること、sections/fields が正準 shape であることだけ確認。"
  [item]
  (and (map? item)
       (every? #(some? (get item %)) required-keys)
       (keyword? (:item/category item))
       (vector? (:item/sections item))
       (every? (fn [s]
                 (and (vector? (:section/fields s))
                      (every? #(contains? field/value-types (:field/type %))
                              (:section/fields s))))
               (:item/sections item))))

(defn sensitive-fields
  "全 section を横断して、値型が機微な field だけを集める。"
  [item]
  (into [] (comp (mapcat :section/fields) (filter :field/sensitive?))
        (:item/sections item)))

(defn category-known?
  "categoryUuid 対応表(kagitaba.category)にある既知カテゴリかどうか。
  未知でも item 自体は有効(データを落とさないのが kagitaba の方針)。"
  [item]
  (category/known? (:item/category item)))
