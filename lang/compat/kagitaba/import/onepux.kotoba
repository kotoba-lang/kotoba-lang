(ns kagitaba.import.onepux
  "1Password `.1pux` export(`export.data` の JSON body)を kagitaba item へ変換する
  **純粋関数のみ**の層。zip 展開や JSON 読み取りなどの IO は一切持たない
  (JVM 版は `kagitaba.import.onepux-file`、将来 CLJS 版もこの ns を再利用できる)。

  入力は `(json/read-str s :key-fn keyword)` で読んだ生 JSON — つまりキーは
  1Password の元の camelCase そのまま(`:categoryUuid` `:notesPlain` 等)を
  keyword 化したもの。1PUX の top-level shape:

    {:accounts [{:attrs {:name ..}
                 :vaults [{:attrs {:name ..}
                           :items [{:uuid :categoryUuid :state :favIndex
                                    :overview {:title :url :urls :tags}
                                    :details {:loginFields :notesPlain :password
                                              :sections :passwordHistory
                                              :documentAttributes}}]}]}]}

  方針(単一不変条件): **インポートは絶対にデータを黙って落とさない**。未知の
  category/field 型もフォールバック keyword で保持し、`item-warnings` が
  目視確認用の警告を返す(エラーにはしない)。"
  (:require [clojure.string :as str]
            [kagitaba.category :as category]
            [kagitaba.field :as field]
            [kagitaba.item :as item]))

;; ───────── section / field ─────────

(defn- raw-value
  "1PUX の tagged-union 値 map({\"concealed\": \"...\"} 等)から素の値を取り出す。
  単一キーでなければ map 全体をそのまま保持する(壊れたデータでも失わない)。"
  [value-map]
  (if (and (map? value-map) (= 1 (count value-map)))
    (first (vals value-map))
    value-map))

(defn- ->field [raw]
  (let [vtype (field/value-type (:value raw))]
    {:id (:id raw) :title (:title raw)
     :type vtype
     ;; known 型は tagged-union の中身だけを保持(型は :type に既に記録済み)。
     ;; :unknown は将来の 1Password 追加型かもしれないので raw map をそのまま残す
     ;; ——単一不変条件「import はデータを黙って落とさない」を型タグごと満たす。
     :value (if (= vtype :unknown) (:value raw) (raw-value (:value raw)))}))

(defn- ->section [raw]
  ;; 1PUX の section は表示名 `title` と、あれば安定 id の `name` を持つ。`name` を
  ;; 捨てると section は title でしか同定できなくなり、利用側が id で引く経路
  ;; （`kagitaba.contract`）から見えなくなる。無ければ nil のまま(title 照合に落ちる)。
  {:id (:name raw) :title (:title raw) :fields (mapv ->field (:fields raw []))})

;; ───────── Login category(loginFields は sections の外) ─────────

(defn- login-field->section-field [{:keys [name designation value]}]
  {:id (or (not-empty designation) name)
   :title name
   :type (if (= "password" designation) :concealed :string)
   :value value})

(defn- login-section
  "`details.loginFields` を 1 つの \"Login\" section に正準化する(username/password
  以外の任意フィールドが並ぶこともあるため、全件そのまま section field 化する)。"
  [login-fields]
  (when (seq login-fields)
    {:title "Login" :fields (mapv login-field->section-field login-fields)}))

(defn- username-of [login-fields]
  (:value (first (filter #(= "username" (:designation %)) login-fields))))

;; ───────── Password category(details.password は sections の外) ─────────

(defn- password-section [password]
  (when (some? password)
    {:title "Password" :fields [{:id "password" :title "password"
                                  :type :concealed :value password}]}))

;; ───────── Document category(details.documentAttributes) ─────────

(defn- document-section [{:keys [fileName documentId]}]
  (when (or fileName documentId)
    {:title "Document" :fields [{:id "file" :title (or fileName "file")
                                  :type :file
                                  :value {:file-name fileName :document-id documentId}}]}))

;; ───────── item ─────────

(defn ->item
  "1 件の生 1PUX item(JSON keyword 化済み)を正準 kagitaba item に変換する。"
  [raw]
  (let [{:keys [uuid categoryUuid state overview details favIndex]} raw
        {:keys [loginFields notesPlain password sections passwordHistory
                documentAttributes]} details
        synthesized (cond-> []
                      (seq loginFields) (conj (login-section loginFields))
                      (some? password) (conj (password-section password))
                      documentAttributes (conj (document-section documentAttributes)))]
    (item/item* {:id uuid
                 :category (category/uuid->key categoryUuid)
                 :title (:title overview)
                 :tags (:tags overview)
                 :favorite? (pos? (long (or favIndex 0)))
                 :url (:url overview)
                 :urls (mapv (fn [u] {:label (:label u) :url (:url u)}) (:urls overview))
                 :state (if (= "archived" state) :archived :active)
                 :username (username-of loginFields)
                 :notes notesPlain
                 :sections (into (filterv some? synthesized) (mapv ->section sections))
                 :password-history (mapv (fn [h] {:value (:value h) :time (:time h)})
                                          passwordHistory)})))

(defn item-warnings
  "確認用の警告(エラーではない)。未知カテゴリ、未知フィールド型、タイトル欠損等。"
  [item]
  (cond-> []
    (not (item/category-known? item))
    (conj (str "unknown category " (:item/category item) " for item " (:item/id item)))

    (str/blank? (:item/title item))
    (conj (str "missing title for item " (:item/id item)))

    :always
    (into (for [f (mapcat :section/fields (:item/sections item))
                :when (= :unknown (:field/type f))]
            (str "unknown field type for field " (pr-str (:field/id f))
                 " on item " (:item/id item))))))

(defn parse-export-data
  "top-level `export.data`(JSON keyword 化済み)を
  `{:vaults [{:account .. :name .. :items [kagitaba item ...]}] :warnings [...]}` に変換する。
  IO は一切行わない(呼び出し側が zip/JSON を読んで渡す)。"
  [export-data]
  (let [vaults (vec (for [account (:accounts export-data)
                           vault (:vaults account)]
                       {:account (get-in account [:attrs :name])
                        :name (get-in vault [:attrs :name])
                        :items (mapv ->item (:items vault))}))]
    {:vaults vaults
     :warnings (into [] (mapcat (fn [v] (mapcat item-warnings (:items v)))) vaults)}))
