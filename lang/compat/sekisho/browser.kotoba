(ns sekisho.browser
  "ブラウザ側の**操作** —— 鍵生成・保管・書き出し。Clerk の SDK にあたる層。

  ## なぜ純粋層だけでは足りなかったか

  `sekisho.account` / `sekisho.didkey` は判断だけを持つ設計にした。それ自体は
  正しいが、**それだけでは重複が消えなかった**（実測 2026-08-03）:

      cloud-murakumo   did 実装 4 → 3   localStorage 3 → 3

  定義は 1 箇所になったのに、**WebCrypto と localStorage を各サイトが書き続けた**
  ので、操作の重複がそのまま残った。壊れていたのもそこ —— local-murakumo が
  秘密鍵を捨て、base64 を切り詰め、失敗時にタイムスタンプを口座にしていたのは
  全部『操作』側の実装だった。

  判断を共有しても操作を共有しなければ、**同じ壊れ方を各サイトが独立に発明する**。

  ## fail closed

  Ed25519 が使えない環境では **識別子を発行せずに例外**。従来の生成器は
  `catch` で `Date.now()` を口座にしていた（ADR-2607320000 決定 2）——
  『測れない / 作れないものを作れたことにしない』。

  ## localStorage であることの限界を隠さない

  秘密鍵を localStorage に置くのは XSS に晒すということ。passkey PRF で包む方が
  安全だが、それは別の設計。**鍵を捨てるよりは桁違いにマシ**という判断で
  ここに置き、`recovery-state` が `:local-only` を独立した状態として返すことで
  『バックアップしていない』を UI が丸められないようにしている。"
  (:require [sekisho.account :as account]
            [sekisho.didkey :as didkey]))

(defn available?
  "この環境で口座を作れるか。"
  []
  (boolean (and (exists? js/window) js/window.crypto (.-subtle js/window.crypto))))

(defn load
  "保存済みの口座を読む → `{:did s :backup m}` または nil。

   **壊れた保存内容は nil にする。** 読めたことと使えることは別で、
   壊れた口座を『ある』と返すと、購入まで進んでから使えないことが分かる。"
  []
  (try
    (when-let [raw (.getItem js/localStorage account/storage-key)]
      ;; **wire 形から明示的に戻す。** `js->clj :keywordize-keys` に任せると
      ;; 非修飾キーのまま返り、`valid-backup?` が修飾キーを探して常に nil を
      ;; 返す（実測 2026-08-03: 本番で口座を作った直後にリロードすると
      ;; 『口座を作る』に戻った。鍵は在るのに読めていなかった）。
      (let [b (account/from-wire (js->clj (js/JSON.parse raw)))]
        (when (account/valid-backup? b)
          {:did (:sekisho/did b) :backup b})))
    (catch :default _ nil)))

(defn store!
  "口座を保存する。**保存できたかを返す** —— localStorage は quota や
   private mode で失敗しうるので、成功を仮定しない。"
  [backup]
  (try
    (.setItem js/localStorage account/storage-key
              (js/JSON.stringify (clj->js (account/->wire backup))))
    (some? (load))
    (catch :default _ false)))

(defn clear! []
  (try (.removeItem js/localStorage account/storage-key) true
       (catch :default _ false)))

(defn generate!
  "Ed25519 の鍵ペアを作り、口座として保存する。

   → Promise<`{:did s :backup m :stored? bool}`>、作れなければ **reject**。

   nil を返さず reject するのは、**呼び出し側が『作れなかった』を握り潰せない
   ようにする**ため。従来の生成器はまさに握り潰して、タイムスタンプの口座を
   作っていた。"
  []
  (if-not (available?)
    (js/Promise.reject
     (ex-info "この環境では鍵を作れません（WebCrypto が無い）。識別子は発行していません。"
              {:reason :no-webcrypto}))
    (-> (js/window.crypto.subtle.generateKey #js {:name "Ed25519"} true #js ["sign" "verify"])
        (.then (fn [^js kp]
                 (js/Promise.all
                  #js [(js/window.crypto.subtle.exportKey "raw" (.-publicKey kp))
                       (js/window.crypto.subtle.exportKey "jwk" (.-privateKey kp))])))
        (.then (fn [^js arr]
                 (let [pub (vec (js/Uint8Array. (aget arr 0)))
                       jwk (js->clj (aget arr 1))
                       backup (account/->backup pub jwk)]
                   (if-not backup
                     ;; 長さが違う鍵からは口座を作らない。31 byte でも
                     ;; base58 としては正しい文字列になり、`z6M` で始まる
                     ;; 『それらしい』識別子が出てしまう。
                     (throw (ex-info (str "鍵の長さが想定外でした（" (count pub)
                                          " byte）。識別子は発行していません。")
                                     {:reason :bad-key-length :length (count pub)}))
                     {:did (:sekisho/did backup)
                      :backup backup
                      :stored? (store! backup)}))))
        (.catch (fn [e]
                  (js/Promise.reject
                   (if (ex-data e)
                     e
                     (ex-info (str "この環境では Ed25519 が使えません（"
                                   (.-message e) "）。識別子は発行していません。")
                              {:reason :ed25519-unsupported}))))))))

(defn backup-url
  "バックアップを data: URL で返す（ダウンロードさせるため）。"
  [backup]
  (str "data:application/json;charset=utf-8,"
       (js/encodeURIComponent (js/JSON.stringify (clj->js (account/->wire backup)) nil 2))))

(defn recovery-state
  "いまの復旧可能性。`exported?` は呼び出し側しか知らない（ダウンロードしたか）。"
  ([] (recovery-state {}))
  ([{:keys [exported? bound-email?]}]
   (account/recovery-state {:stored? (some? (load))
                            :exported? exported?
                            :bound-email? bound-email?})))

(defn did
  "保存済みの did、または nil。**検証を通ったものだけ**を返す。"
  []
  (:did (load)))

(defn valid? [d] (didkey/valid? d))
