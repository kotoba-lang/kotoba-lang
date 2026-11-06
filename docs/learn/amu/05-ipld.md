# 05 IPLD と内容アドレス

内容アドレスは「どこにあるか」ではなく「どの内容か」で値を指す方法である。
Kotoba は定義・値・状態・artifact の識別を明示し、保存や転送で内容を検証できるようにする。

## データから CID まで

[IPLD Data Model](https://ipld.io/docs/data-model/) は、整数・文字列・list・map・bytes・link 等でデータを表す。
link は別の内容を CID で指す。構造をバイトにする規約が codec、
[DAG-CBOR](https://ipld.io/specs/codecs/dag-cbor/spec/) はその一つである。

```text
論理的な値 → 契約に従う正規化 → DAG-CBOR bytes
          → SHA-256 digest → CIDv1（codec と hash の情報を含む）
```

これは導出の概念図である。実際には version、型タグ、整数・float の精度、link の encoding まで揃える。
EDN を `pr-str` したテキストに DAG-CBOR というラベルを付けても、DAG-CBOR の block にはならない。
Kotoba の keyword や set 等も、IPLD の基本 kind そのものではなく、言語の codec 規約で表す。

## 同じハッシュで全部を表さない

| 識別対象 | 内容 | 何が変わると変化するか |
|---|---|---|
| ソースの CID／digest | 作者が書いた正確なバイト列 | 空白・コメントも含めた bytes |
| Definition CID | 検査・正規化した定義と六つの sealed input | 型・effect・依存・契約・正規化した本体 |
| artifact の CID／digest | target 用に生成した bytes | compiler・target・ABI・最適化・配置等 |
| Value CID | codec 契約で正規化した値 | 値または encoding の契約 |
| Admission CID | 実行を許す条件を束縛した対象 | grant や実行条件。DefCID だけでは決まらない |

ここでソース一般の内容アドレスと、言語契約上の特定の source-tree identity の実装状況は別である。
単に sha256 を計算しただけの SIR やソースを Definition CID と呼んではいけない。

## node と block と実行メモリ

一つの IPLD block に複数の node を入れられる。式の各要素が一つの block、一つの通信になる必要はない。
小さい block は共有や部分取得に向き、大きい block はリンク・lookup の負担を減らす。
どちらが速いかはアクセスと更新のパターンで変わる。

IPLD は CPU の物理ポインタ配置を定めない。
値を検証して実行用の handle や連続 buffer にした後、要素を読むたびに CID を計算し直す必要はない。
CID は内容の識別、handle は一回の実行内の参照、capability は操作の権限という別の役割を持つ。
CID の一致から、取得可能性・秘密性・実行許可は導けない。

## 読む契約

[物理コストと IPLD の解説](../../lang/physical-model-and-ipld.ja.md)、
[value codec](../../../lang/value-codec.edn)、
[code identity](../../../lang/code-identity.edn)。
保存表現と実行用の配置の関係は [CID と handle の契約](../../adr/ADR-kotoba-cid-handle-cap-runtime.md) で追える。

## 小課題と解説

同じ定義を ARM64 と x86-64 にコンパイルした。DefCID が同じなら機械語も同じだろうか。
異なる命令セットなので同じ bytes は要求されない。
定義の identity は計算の内容を束縛し、artifact の identity はその実現方法を束縛する。
この分離が、意味を共有しつつ target を変えるために必要になる。

[前へ](04-checking-and-ir.md) · [目次](README.md) · [次へ 定義の識別](06-definition-identity.md)
