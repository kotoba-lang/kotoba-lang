# 09 Kototama VM

[English](09-kototama.md) | **日本語**

Kototama は Kotoba の virtual machine 契約である。
特定の bytecode interpreter の名前ではなく、プログラムをどの条件で実行し、
世界の状態をどう変え、どんな結果と記録を返すかを定める。

## 一回の実行を表す

```text
program, message, state, authority, budget
    ↓ Kototama の遷移
outcome, state', events, receipt
```

program だけで実行を定義しないところが重要である。
同じ定義でも入力や状態が異なれば結果は変わり、権限や予算が違えば拒否や資源切れになり得る。
runtime engine はこの契約を実装する。Amu は計算を artifact にし、host はそれを実行する。
native code を使う実装であっても、この意味で VM の役割を持てる。

## 四つの観点

**計算**は閉じた S 式の意味である。名前は解決され、host の reflection や unrestricted eval は持ち込まない。
canonical なプログラムの wire form は tagged DAG-CBOR vectors で表す。
一方、実行の内側は target の typed call や連続メモリを利用できる。

**状態**は IPLD の world として識別する。メッセージの実行中は overlay に変更を積む。
成功時に新しい root を commit し、revert・拒否・資源切れでは対応する overlay を破棄する。
入れ子の呼出しが失敗した場合の snapshot と復元も契約である。
外部サービスで起きた effect まで、state overlay の rollback で自動的に取り消せるわけではない。

**admission と authority**では、第7章の各出所を持つ条件を閉じた predicate で照合する。
policy の作成や認証 adapter と、その呼出し時判定は分離される。

**receipt**は実行の記録を値として扱う。VM と implementation と profile、program/message の CID、
前後の state root、outcome、fuel、events、authority decision 等を結び付ける。
receipt の CID は「どの記録か」を示す。署名者の信頼、合意、二重実行防止はそれぞれ別の仕組みである。

## typed eval は検査済み定義を呼ぶ

Kotoba の typed eval は、ソース文字列を host に評価させる機能ではない。
Definition CID と閉じた依存を解決・検証し、interface と完全な effect row と現在の条件で再 admission する。
`Definition CID` はどの計算か、`Admission CID` はどの実行条件か、`Value CID` はどの結果かを表す。
eval depth と fuel にも境界がある。

pure な結果の cache の契約は Admission CID と入力 Value CID 群を結び付ける。
HTTP や支払いの effect を過去の結果で黙って代用することは、その cache の意味ではない。

## VM 契約と実装 profile を区別する

`core/v1`、`fvm-actor/v1`、`evm/v1`、`fevm/v1` の profile は、それぞれ必要な互換性と証拠を持つ。
EVM に似た立ち位置という説明だけで Ethereum の bytecode・state・consensus 互換にはならない。
Wasm を出力できることも、Kototama のすべての状態遷移を実装した証拠にはならない。

Amu の `kototama.pure-i64-aot/v1` や `kototama.pure-i64-receipt/v2` は、
空の guest authority・閉じた整数計算という別途定めた bounded profile である。
この教材の `main → 42` の成功を、IPLD world・全 operation・全 target の conformance に広げない。

## 関連する実行モデル: Filecoin FVM と FEVM

[Filecoin Virtual Machine の概要](https://docs.filecoin.io/core-concepts/filecoin-virtual-machine)は、actor と IPLD のデータ操作を扱う Wasm ベースの実行環境を説明する。[Filecoin EVM runtime の資料](https://docs.filecoin.io/core-concepts/filecoin-evm-runtime)は、FVM の上で EVM を実行する runtime である FEVM を説明する。実行基盤と、その基盤上の互換 runtime を区別するための参照となる。

Kototama の [VM 仕様と profile の定義](https://github.com/kotoba-lang/kototama/blob/b7bbcc44d08913fee69074ad70f92d341df2be92/docs/kototama-virtual-machine.md)にも、EVM／FVM と対応する役割が明記されている。FVM 型の profile では actor のメッセージ、IPLD state、入れ子の呼出し、gas、rollback 等を扱う。互換性を述べる際は各 profile の範囲と検証結果を確認する。Filecoin の consensus や storage proof は Kototama の VM 契約の外にある。

## 読む正本

[VM spec](https://github.com/kotoba-lang/kototama/blob/b7bbcc44d08913fee69074ad70f92d341df2be92/spec/kototama-vm-v1.edn)、
[VM 解説](https://github.com/kotoba-lang/kototama/blob/b7bbcc44d08913fee69074ad70f92d341df2be92/docs/kototama-virtual-machine.md)、
[typed eval](../../../lang/typed-eval.edn)、
[native i64 receipt](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/kototama-i64-receipt.md)。
古い checkout の Datalog／Biscuit 中心の VM 説明は、現行 admission core の説明として使わない。

## 小課題と解説

同じ message と receipt の CID を持てば、その message が一度しか実行されていないと分かるだろうか。
分からない。hash は内容を識別する。再実行を防ぐには nonce、消費 journal、idempotency 等の適切な契約が必要である。

[前へ](08-code-generation.ja.md) · [目次](README.ja.md) · [次へ 実行の高速化](10-runtime-performance.ja.md)
