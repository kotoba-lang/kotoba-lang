# 07 effects と権限

[English](07-effects-and-authority.md) | **日本語**

型は扱う値を説明し、effect は実行が外部に何を行い得るかを説明する。
Amu が effect を検査することと、host が具体的な操作を許すことは両方必要である。

## 拒否する例

[negative-operation.kotoba](examples/negative-operation.kotoba) は `(slurp "lesson.txt")` を書いてある。
観測した native CLI は `E2101 unknown or unsupported form 'slurp'` を返した。
これはファイルを読もうとして OS に拒否された結果ではない。
この入力に対応する操作がコンパイラの閉じた言語にないため、検査時に拒否された。

## 宣言は許可ではない

例えば HTTP 操作を持つ検査済み定義では、その operation が effect row に含まれる。
呼び出す関数の effect も伝播させる必要があり、外部操作を helper に隠して pure と呼ぶことはできない。
effect row の semantic な名前と、backend の数値 wire ID は区別する。ABI の番号変更だけで定義の意味を変えないためである。

実行時の判断は、概念的には次の積集合である。

```text
Amu が検査した effect
∩ VM が要求した operation と resource
∩ capability chain が与える範囲
∩ local policy が許す範囲
∩ runtime が現在提供できる範囲
```

さらに definition world と runtime world の一致などを調べる。
どれかが欠ければ許可にならない。ファイル一つへの許可がディレクトリ全体の許可になったり、
古い実行の grant が新しい実行へ持ち越されたりしてはいけない。

現行 Kototama の admission core は、この条件を閉じた Kotoba predicate として計算する。
Datalog のユーザ規則をそこで実行する方式ではない。Biscuit も core 内の grant 表現ではなく、
必要なら認証境界の adapter が capability chain へ変換する位置付けである。

## 安全性を支える境界

コンパイラは型・effect・対応 target・資源の条件を検査する。
grant と local authorizer は許可を決める。host と provider は具体的な endpoint、パス、quota を強制する。
native のメモリ保護やプロセス隔離も別の責任である。
コンパイラが安全な言語を受け付けるだけで、host/provider の不備までなくなるわけではない。

ブラウザアプリでは、operation ごとの利用者の承認もそのアプリの契約に従う。
コンパイラや Bot が持つ tool 権限から、利用者のサービス操作の承認は作れない。

## 読む契約とソース

[effect と capability の言語契約](../../../lang/capability-semantics.edn)、
[elaboration pipeline](../../../lang/elaboration-pipeline.edn)、
[Kototama admission](https://github.com/kotoba-lang/kototama/blob/b7bbcc44d08913fee69074ad70f92d341df2be92/src/kototama/admission.kotoba)。
`eligible?` と `admit` の引数、拒否理由の順序を読むと、どの所有者が何を供給するか分かる。

## 小課題と解説

同じ artifact と同じ DefCID でも、grant が失効した後にもう一度操作できるだろうか。
できない。内容は同じでも、現在の実行条件が許可しない。
このため immutable code の cache は使えても、過去の許可判断をそのまま再利用してはいけない。

[前へ](06-definition-identity.ja.md) · [目次](README.ja.md) · [次へ コード生成](08-code-generation.ja.md)
