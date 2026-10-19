(ns sekisho.didkey
  "did:key（Ed25519）の唯一の定義。**構造判定だけ。鍵は作らない。**

  ## なぜこの ns が要るのか

  did:key は murakumo の **口座そのもの** —— サインアップ画面もパスワードも無く、
  鍵がそのまま口座になる。ADR-2607320000 はそれを『弱点ではなくこの経済圏の最も
  良い部分』としつつ、**生成が壊れている以上どの扉を作っても意味がない**と書いた。

  実際に壊れていた（2026-08-03 実測、`local-murakumo/infer_view.cljc`）:

      did='did:key:z6Mk'+btoa(String.fromCharCode(...raw))
            .replace(/[^a-zA-Z0-9]/g,'').slice(0,32);
      catch(e){ did='did:key:z6Mk'+Math.abs(Date.now()).toString(36); }

  2 つとも did:key ではない。前者は **base64 から記号を落として 32 文字で切った
  もの**（base58btc でもなく multicodec 前置も無い）、後者は **タイムスタンプを
  口座にしている**。そして検証側は `starts-with \"did:key:\"` しか見ていなかった
  ので、**どちらも通っていた**。

  壊れ方の質が悪いのは、**見た目が did:key であること**。`z6Mk` で始まり長さも
  それらしいので、目視でもログでも気付けない。気付けるのは『その鍵で署名しよう
  とした時』——つまり顧客が credits を使おうとした瞬間に初めて壊れる。

  ## 定義を 1 箇所にする

  ADR-2607320000 決定 3:『valid did:key の定義を 1 つにする —— 構造判定
  （base58btc・multicodec prefix・長さ）を 1 箇所に置く』。生成側と検証側が
  別々の判定を持つと、**生成できるが検証を通らない**（またはその逆）が起きる。

  ## 鍵を作らないのは意図的

  Ed25519 の鍵生成は WebCrypto / libsodium の仕事で、判断を含まない機構
  （`saifu.address` が ripemd160 を実装しないのと同じ線引き）。この ns は
  **32 byte の公開鍵を受け取って文字列にする**だけなので、資金も鍵も無しに
  正しさを証明できる。"
  (:require [kotoba.lang.text :as str]))

(def base58btc-alphabet
  "Bitcoin 系 base58。0/O/I/l を除く —— 目視で取り違える文字を外してある。
   **base64 とは別物**（壊れた生成器はここを取り違えていた）。"
  "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

(def ^:private alpha-index
  (into {} (map-indexed (fn [i c] [c i]) base58btc-alphabet)))

(def ed25519-multicodec
  "multicodec `0xed01`（varint 表現）。did:key の Ed25519 公開鍵はこれを前置する。
   **前置を落とすと、別の鍵種別と区別が付かない文字列になる。**"
  [0xed 0x01])

(def ed25519-pubkey-length 32)

(def ^:private encoded-length
  (+ (count ed25519-multicodec) ed25519-pubkey-length))

(defn b58-encode
  "byte 列 → base58btc 文字列。

   BigInteger を使わず byte 単位の除算で書いてあるのは cljs のため ——
   JS の Number は 53bit しか持たず、34 byte を数値にすると壊れる。"
  [bytes]
  (let [bs (vec bytes)
        leading-zeros (count (take-while zero? bs))
        digits (loop [acc [] input bs]
                 (if (every? zero? input)
                   acc
                   (let [[q r] (reduce (fn [[out rem] b]
                                         (let [cur (+ (* rem 256) b)]
                                           [(conj out (quot cur 58)) (mod cur 58)]))
                                       [[] 0] input)]
                     (recur (conj acc r) q))))]
    (str (apply str (repeat leading-zeros (first base58btc-alphabet)))
         (apply str (map #(nth base58btc-alphabet %) (reverse digits))))))

(defn b58-decode
  "base58btc 文字列 → byte 列、または **nil**（不正文字を含む）。

   nil を空 vector にしない —— 『復号できなかった』と『空だった』を同じ値で
   表すと、壊れた識別子が空の識別子として通る。"
  [s]
  (when (and (string? s) (every? alpha-index s))
    (let [leading-ones (count (take-while #(= % (first base58btc-alphabet)) s))
          bytes (loop [acc [] input (mapv alpha-index s)]
                  (if (every? zero? input)
                    acc
                    (let [[q r] (reduce (fn [[out rem] d]
                                          (let [cur (+ (* rem 58) d)]
                                            [(conj out (quot cur 256)) (mod cur 256)]))
                                        [[] 0] input)]
                      (recur (conj acc r) q))))]
      (into (vec (repeat leading-ones 0)) (reverse bytes)))))

(defn from-public-key
  "Ed25519 公開鍵（32 byte）→ `did:key:z...`、または nil。

   **長さを検査する。** 31 byte でも base58 としては正しい文字列になり、
   `z6M` で始まる『それらしい』識別子が出てしまう —— 壊れた生成器が作って
   いたのは、まさにそういう文字列だった。"
  [pubkey]
  (let [b (vec pubkey)]
    (when (= ed25519-pubkey-length (count b))
      (str "did:key:z" (b58-encode (into (vec ed25519-multicodec) b))))))

(defn public-key-of
  "`did:key:z...` → 32 byte の公開鍵、または nil。

   nil になる条件を全部潰す: prefix 違い / `z` 以外の multibase / base58 として
   不正 / multicodec が Ed25519 でない / 長さが 34 byte でない。"
  [did]
  (when (and (string? did) (str/starts-with? did "did:key:z"))
    (let [body (subs did (count "did:key:z"))]
      (when-let [b (b58-decode body)]
        (when (and (= encoded-length (count b))
                   (= ed25519-multicodec (subvec b 0 2)))
          (subvec b 2))))))

(defn valid?
  "構造として正しい did:key（Ed25519）か。

   **これが唯一の定義。** 生成側も検証側もここを通す —— 別々の判定を持つと
   『生成できるが検証を通らない』が起きる（ADR-2607320000 決定 3）。"
  [did]
  (some? (public-key-of did)))

(defn diagnose
  "なぜ無効なのかを返す。UI がそのまま出せる粒度。

   `valid?` の真偽だけだと、顧客は**何を直せばいいのか分からない**。
   壊れた識別子を渡された時に『無効です』としか言えないと、顧客は
   自分の鍵が壊れていることに気付けず、もう一度同じものを貼る。"
  [did]
  (cond
    (not (string? did))                      :not-a-string
    (str/blank? did)                         :empty
    (not (str/starts-with? did "did:key:"))  :not-did-key
    (not (str/starts-with? did "did:key:z")) :not-base58btc-multibase
    (nil? (b58-decode (subs did (count "did:key:z")))) :invalid-base58
    (not= encoded-length (count (b58-decode (subs did (count "did:key:z")))))
    :wrong-length
    (not= ed25519-multicodec (subvec (b58-decode (subs did (count "did:key:z"))) 0 2))
    :not-ed25519
    :else nil))
