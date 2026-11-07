# 11 ビルドの高速化と selfhost

[English](11-build-performance.md) | **日本語**

生成したプログラムの実行時間と、コンパイラが artifact を作る時間は別の性能である。
AI が生成・検査・修正を繰り返すと、後者の待ち時間も作業全体の費用になる。

## compiler 自身の仕事を減らす

Amu の native 作業では、大きい SIR の label を繰り返し探す走査、型推論の反復、
compiler を丸ごと再生成する mutation tests 等に費用がある。
例えば immutable な SIR の label index を一度作れば、同じ範囲の再走査を減らせる。
ただし SIR を変更したら古い index は使えない。配置を決める mutable な CODE/FIX の証明を、
単に件数が同じという理由で cache することもできない。

実装パラメータに明示型を付け、同じ型を何度も推論する仕事を避ける変更もある。
これは新しい型推論アルゴリズムや生成コードの高速化とは区別する。
source annotation が意味を変えていないことを、unannotated counterpart の whole-image bytes と動作で確認する。

これらの具体的な条件は [Amu の shared rules](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/AGENTS.md) の
Immutable SIR label proofs と Explicit selfhost parameter types にまとまっている。
並列 gate の longest-first scheduling や同時 build 数の制限は、compiler algorithm とは別の運用上の工夫である。

## DefCID による再利用が必要とするもの

定義の内容が同じなら、前に作った object を使いたくなる。
しかし build key は少なくとも定義または SCC の identity、compiler image、target/ABI、layout、
最適化設定、依存/interface closure、resource proof の契約を結び付けなければならない。

```text
DefCID/SCC + compiler + target/ABI + options + dependencies + proof contract
    → build key → 検証した relocatable object → 今回の配置で link
```

以前 link したアドレスは今回の配置で同じとは限らないので、symbolic relocation を残す。
cache の破損や key 不一致は再構築または拒否へ進める。
current admission、effect、grant、mutable な残り fuel は、immutable code と一緒に再利用しない。

native の identity／SCC／object-store prototype には、それぞれ bounded な検証がある。
完全な source→checked KIR producer、全 compiler の依存、永続 store、cold/warm mutation build、
end-to-end CI 時間は別の受入条件を持つ。
現時点の [incremental build の記録](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/native-incremental-build.md) を確認し、
「内容アドレスだから全ビルドが速い」と結論付けない。

## selfhost が確かめること

selfhost は、Amu が Amu のソースをコンパイルすることである。
一つの実行例が動くことより大きい closure を扱う必要があり、compiler・CLI・build・packaging の依存も含む。
現在の方針は canonical Kotoba と Amu-built native compiler でその closure を作り、
Node／JVM／C compiler や loader への fallback を置かないことである。

```text
起点 compiler → source を build → F1
F1            → 同じ source を build → F2
F2            → 同じ source を build → F3
```

F1/F2/F3 の一致は、記録した source と条件で生成が安定した強い観測になる。
それだけで compiler の正しさ、全言語機能、全 target、独立した初回 bootstrap の信頼を証明するわけではない。
元の semantic corpus、拒否例、実行・資源の観測も必要になる。
historical C-loader の固定点と現在の loaderless native の固定点も別の記録として読む。

## 小課題と解説

同じ DefCID、同じ CPU でも compiler image が更新された。古い object を cache hit として使えるだろうか。
この key 設計では使わない。compiler の生成規則や修正が変わった可能性があるためである。
cache hit は再利用条件の一致を意味し、今回の安全性の判定を済ませたことは意味しない。

[前へ](10-runtime-performance.ja.md) · [目次](README.ja.md) · [次へ 読解と実験](12-reading-and-experiments.ja.md)
