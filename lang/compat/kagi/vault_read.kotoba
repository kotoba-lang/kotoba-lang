(ns kagi.vault-read
  "vault を **読むだけ** のプロセス内 seam。app（cloud-itonami-app 等）が
  `bin/kagi` を叩かずに、governor を通したまま item を読むための入口。

  ## なぜ CLI ではなく ns なのか

  `kagi get` を subprocess で呼ぶと、平文が pipe とプロセステーブルを経由する。
  一度でも子プロセスの stdout に出た秘密は、その瞬間からログ・スクロールバック・
  クラッシュダンプの中にある。同一プロセス内で復号すれば、平文は呼び出し側が
  捨てるまでヒープにしか無い。

  ## この ns がやらないこと

  - **書かない。** `save-store!` に相当する経路がない。vault を読むだけの
    コードが vault を壊すことはできない。
  - **プロンプトしない。** unlock は OS keychain か `KAGI_MASTER` だけ。
    サーバプロセスに TTY は無く、`System/console` を読みに行けば HTTP
    リクエストがそこで固まる（CLI の passphrase fallback は対話用）。
  - **governor を迂回しない。** reveal は CLI の `kagi get` と同じ
    `kagi.operation` グラフを通る。ここで直接 DEK を開けば AccessGovernor の
    単一不変条件が意味を失う。

  ## 総当たり列挙について

  `items` は vault の item を列挙する。fleet の安全床が禁じている「総当たりで
  鍵を request する」のは *agent が自分のために* 無関係な credential を掘る
  行為で、**本人の app が本人に本人の item 一覧を見せること**とは別物
  （1Password のリスト画面がまさにそれ）。区別が保たれるのは列挙が復号を
  伴わないから: `items` はメタデータだけを返し、平文は `reveal` を明示的に
  呼んだ item にしか現れない。"
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [kagi.cacao :as cacao]
            [kagi.crypto :as crypto]
            [kagi.identity :as identity]
            [kagi.operation :as op]
            [kagi.persist :as persist]
            [kagi.store :as store]
            [kagi.unlock :as unlock]
            [kagitaba.field :as field]
            [langgraph.graph :as g])
  (:import [java.time Instant]
           [java.util UUID]))

(def ^:private aud "https://kotobase.net")

(defn vault-home
  "vault の置き場所（ADR-2607170500）。repo checkout の中には決して置かない。"
  []
  (or (not-empty (System/getenv "KAGI_HOME"))
      (str (System/getProperty "user.home") "/.kagi")))

(defn vault-present?
  "この機械に vault があるか。無いことは異常ではない（まだ `kagi init`
  していないだけ）ので、呼び出し側が例外ではなく状態として扱えるようにする。"
  []
  (.exists (java.io.File. (str (vault-home) "/vault.edn"))))

(defn- unlock-with-passphrase
  "master passphrase 経由。envelope は `:unlock/wraps` ではなく meta 直下の
  `{:salt :nonce :wrapped}`（`kagi init` が置く原初の wrap）。間違った
  passphrase は例外ではなく nil —— ここでの失敗は `:locked` であって異常ではない。"
  [p pass meta]
  (try
    (crypto/aead-open p
                      (crypto/argon2id p (.getBytes ^String pass "UTF-8") (:salt meta)
                                       {:m-kb 262144 :t 3 :p 4})
                      (:nonce meta) (:wrapped meta) (byte-array 0))
    (catch Exception _ nil)))

(defn- unlock-vmk
  "OS keychain → `KAGI_MASTER` の順。どちらも無ければ nil を返す
  （例外ではなく `:locked` として表現できるように）。プロンプトは出さない。"
  [p meta]
  (or (unlock/unlock-with-os-keychain p meta (not-empty (System/getenv "KAGI_UNLOCK_REF")))
      (when-let [pass (not-empty (System/getenv "KAGI_MASTER"))]
        (unlock-with-passphrase p pass meta))))

(defn open
  "vault を開く。戻り値は `{:status :open …}` か
  `{:status :absent}` / `{:status :locked}`。

  `:locked` は「この機械では今 unlock できない」であって「vault が無い」でも
  「壊れている」でもない——app はその 3 つを別々に表示できなければならない
  （unlock されていないことを「契約 0 件」と描くのが最悪の嘘になる）。"
  ([] (open (vault-home)))
  ([home]
   (let [path (str home "/vault.edn")
         id-path (str home "/identity.edn")
         data (persist/load* path)]
     (if-not (and data (.exists (java.io.File. id-path)))
      ;; identity が無いのに identity を「作る」ことはしない。読むだけの経路が
      ;; 新しい did を生やすと、それは vault を開く鍵ではない別人になる
      ;; （ADR-2607170500 の消失モードそのもの）。
       {:status :absent :vault-home home}
       (let [p (crypto/jvm-provider)
             id (identity/load-or-create-identity!
                 id-path p
                 (when-let [ref (not-empty (System/getenv "KAGI_IDENTITY_REF"))]
                   {:secret-ref ref}))
             vmk (unlock-vmk p (:meta data))]
         (if-not vmk
           {:status :locked :vault-home home :did (:did id)}
           {:status :open
            :vault-home home
            :provider p
            :identity id
            :did (:did id)
            :vmk vmk
            :store (store/mem-store (dissoc data :meta))}))))))

(defn items
  "item のメタデータ一覧。**復号しない** —— id / compartment / category /
  version だけで、ここに平文は現れない。"
  [{:keys [store] :as session}]
  (when (= :open (:status session))
    (->> (vals (:items @(:a store)))
         (map #(select-keys % [:item/id :item/compartment :item/category
                               :item/version :item/created-at :item/updated-at]))
         (sort-by :item/id)
         vec)))

(defn- context [id vmk purpose]
  {:did (:did id) :role :owner :phase 3 :vmk vmk :purpose purpose
   :aud aud
   :cacao (cacao/mint id {:cap :cap/admin :scope (:graph id)}
                      {:aud aud :nonce (str (UUID/randomUUID))
                       :issued-at (str (Instant/now))
                       :expiry (str (.plusSeconds (Instant/now) 3600))})
   :register (identity/member-record id :owner)})

(defn reveal
  "1 item を governor 経由で復号して平文文字列を返す。拒否されたら nil。

  `purpose` は監査台帳に残る——なぜ開けたのかが記録されない開示は、
  後から誰も検証できない。"
  [{:keys [store provider identity vmk] :as session} item-id purpose]
  (when (and (= :open (:status session)) (store/item store item-id))
    (let [actor (op/build store {:crypto provider
                                 :signer (identity/sign-secret identity)})
          r (:state (g/run* actor
                            {:request {:op :item/reveal :item-id item-id}
                             :context (context identity vmk purpose)}
                            {:thread-id (str "reveal-" item-id "-" (UUID/randomUUID))}))]
      (when-let [pt (get-in r [:result :plaintext])]
        (String. ^bytes pt "UTF-8")))))

(defn strip-sensitive
  "kagitaba item から機微値型の field 値を落とす。field 自体は残す——
  「パスワードが設定されている」ことと「パスワードが無い」ことは別の事実で、
  field ごと消すと後者に見える。"
  [item]
  (update item :item/sections
          (fn [sections]
            (mapv (fn [s]
                    (update s :section/fields
                            (fn [fs]
                              (mapv (fn [f]
                                      (if (field/sensitive? (:field/type f))
                                        (assoc f :field/value nil :field/redacted? true)
                                        f))
                                    fs))))
                  sections))))

(defn- parse-item
  "reveal した平文を kagitaba item として読む。`kagi add` で入れた素の secret は
  item ではないので nil を返す（EDN として読めても map でなければ item ではない）。"
  [plaintext]
  (try
    (let [v (edn/read-string plaintext)]
      (when (and (map? v) (:item/category v)) v))
    (catch Exception _ nil)))

(defn kagitaba-items
  "vault の中の kagitaba item を、機微値を落とした形で返す。

  `pred` は復号 **前** のメタデータに対する述語（既定は category が
  `:membership` のもの）。これで「契約画面を開いたら vault 中の全 item を
  復号する」ことを避ける——見る必要のない item は開かない。"
  ([session purpose] (kagitaba-items session purpose
                                     #(= :membership (:item/category %))))
  ([session purpose pred]
   (when (= :open (:status session))
     (into []
           (keep (fn [meta]
                   (when-let [it (some-> (reveal session (:item/id meta) purpose)
                                         parse-item)]
                     (-> it
                         strip-sensitive
                         (assoc :item/id (:item/id meta)
                                :item/compartment (:item/compartment meta))))))
           (filter pred (items session))))))

(defn redact-home
  "表示用に vault path のホーム部分を伏せる。"
  [path]
  (let [home (System/getProperty "user.home")]
    (if (and home (str/starts-with? (str path) home))
      (str "~" (subs (str path) (count home)))
      (str path))))
