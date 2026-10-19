(ns envelope.passkey
  "**passkey で開ける envelope 受信者** —— WebAuthn PRF から X25519 identity を
   取り出す（ADR-2608301039 D7 段3）。

   ## passkey は署名 credential であって、復号しない

   WebAuthn に decrypt 操作は無い。だから passkey を「読む鍵」にはできない。
   使えるのは **PRF 拡張**で、同じ (credential, salt) に対して**同じ 32 bytes**を
   返す。それが唯一この認証器から取り出せる秘密である。

   ## だから鍵は導出せず、wrap する

   PRF 出力から X25519 秘密鍵を直接導出する（決定的導出）と短く書けるが、
   **その identity は credential 1 個に永久に縛られる** —— passkey を失えば鍵も
   失われ、2 本目の passkey を足すことも、credential を回すこともできない。

   代わりに identity は**普通のランダムな X25519 鍵**とし、PRF 出力から作った
   KEK でそれを wrap して保管する。`kotoba-lang/webauthn` の `prf-envelope` が
   `:salt-ref` と `:wrapped-ref` を持つのはこの形を指している。同じ identity を
   複数の passkey がそれぞれ wrap でき、credential を回しても identity は動かない。

   ## この ns が持たないもの

   `navigator.credentials.get` は呼ばない。PRF 出力は host が渡す
   （`kotoba-lang/webauthn` の `derive-prf!` port が実装する境界）。ここに在るのは
   **バイト列から受信者を作る**部分だけで、ブラウザにも Worker にも JVM にも
   依存しない。"
  (:require [envelope.seal :as seal]
            [kotoba.signal.x25519 :as x25519]))

(def identity-aad
  "この wrap が何であるかを束縛する。別の目的で作った wrap を identity として
   持ち込めないようにするための固定文字列（AAD なので 1 byte 違えば開かない）。"
  "kotoba/envelope/v1 passkey-identity")

(defn generate-identity
  "新しい X25519 identity。`{:priv <bytes> :pub <bytes>}`。
   **これが object を開ける鍵**であり、passkey はその保管庫でしかない。"
  []
  (x25519/generate-keypair))

(defn seal-identity
  "identity の秘密鍵を PRF 出力で封じる。-> Promise<wrap map>。

   `salt` は PRF に渡した salt とは別物でよい（HKDF の salt）。nil でもよいが、
   同じ PRF 出力を別用途にも使うなら必ず分ける。"
  [{:keys [priv]} ^js prf-output & [^js salt]]
  (seal/wrap-under-key priv prf-output salt identity-aad))

(defn open-identity
  "封じた identity を PRF 出力で開ける。-> Promise<Uint8Array（秘密鍵）>。
   PRF 出力が違えば reject する（黙って別の鍵を返さない）。"
  [wrap ^js prf-output]
  (seal/unwrap-under-key wrap prf-output identity-aad))

(defn recipient
  "envelope の受信者エントリ入力。`seal-object` の recipients に渡す形。"
  [id {:keys [pub]}]
  {:id id :pub pub})

(defn reader
  "封緘した object を開く側の形（`kotoba.annex.sealed` の `:reader`）。"
  [id priv]
  {:id id :priv priv})
