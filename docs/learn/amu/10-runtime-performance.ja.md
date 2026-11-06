# 10 生成コードの高速化

[English](10-runtime-performance.md) | **日本語**

実行を速くするには、同じ計算を少ない費用で行う。
Amu では値だけでなく fuel・trap・effect・arena の観測も保つ必要があり、
「チェックを消したら速かった」で最適化を採用することはできない。

## どこに費用があるか

式の再計算、関数呼出し、レジスタと stack の移動、分岐、buffer の読み書き、
型を実行時に解く処理、境界検査、fuel の更新が候補になる。
どれを減らせるかは、その位置で既に分かっている値・型・alias・effect によって違う。
第8章の定数 `40 + 2` は、計算前に結果が分かる最小例である。

## 実装を読むときの主な手法

| 工夫 | 費用を減らす仕組み | 守る条件 |
|---|---|---|
| 定数と値の性質を伝える | 同じ演算・分岐を繰り返さず、即値や適した命令を使う | i64 の wrap、型、例外・trap の意味 |
| 小さい関数の展開・tail call | call、frame、値の移動を減らす | 引数の評価順、live value、ABI、fuel |
| 構造を認識する native kernel | 認識した loop・計算を、適した命令列で実行する | 名前ではなく構造と guard で適用を決める |
| loop の展開・SIMD | 分岐負担を減らす、複数の要素を一命令で扱う | 全入力要素、端数、bounds、CPU 条件 |
| 定数から table を作る | 同じ決定木を何度も辿らず lookup する | 元の関数で完全に決まる値だけを使う |
| 検査の hoist・batch | 成立条件を先に確かめ、重複する判定を減らす | insufficient fuel や不正 index の観測も一致 |

`41-a64gen` には多数の構造認識と命令生成の経路がある。
一般的な最適化名を並べただけで全入力に適用されると考えず、入口の predicate と外れた場合の経路を読む。
特に過去の実験メモにある候補は、採用 source と同じとは限らない。

## 分岐を table に変える例

Amu の scalar threshold-tree 実験は、定数の閾値で分岐し、葉が整数定数で終わる純粋な木を認識した。
許された形全体を確かめた後、64項の table と端点処理へ変換する。
効果のある未知の関数や動的な葉には使わない。
これは入力に対応する答えを benchmark 名で返す仕組みではなく、元の関数に含まれる情報から構成する変換である。
当時の検証と測定の範囲は [実験記録](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/coscientist-scalar-tree-20261005.md) で読める。

## fuel と境界検査を消さずに速くする

loop の反復回数や index 範囲が分かれば、毎回の同じ検査をまとめられる場合がある。
ただし budget が途中で尽きたとき、元のどこで止まり、何を変更済みかまで一致する必要がある。
十分な fuel の fast path と、元の実行順序を保持する path を分ける設計にも、その証明が要る。
実際に [sufficient-fuel region 実験](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/coscientist-fuel-region-20261005.md) は、
正しさの比較を通しても性能条件を満たさず、全体採用を拒否した。検証成功と高速化成功は別の観測である。

fuel の意味も profile ごとに読む。通常 native route の設定と metered receipt profile の予算を同一視しない。
CPU 命令を減らしても、言語で定めた charge を勝手に減らせるとは限らない。

## 公開の速度値の読み方

2026年10月15日の Apple M4 の19本の translated Embench ports は、
一つの compiler snapshot、各 workload 一回の採用サンプルで、C に対する実行時間の幾何平均が約 `0.80×` だった。
その数には監査で区別した algorithm substitution と SHA 命令の差を含む。
released toolchain の一般的な速度や公式認定スコアではない。
[公開記事と監査](https://kotoba-lang.org/blog/embench-c-parity-20261015/)を比率と一緒に読む。

別の same-algorithm M104 baseline は約 `0.8213×` で、同じ測定とは扱わない。
これらの歴史的測定を現在の loaderless native compiler の新しい性能値に転用しない。
[compiler lineage](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/compiler-core.json) が両者の区別を持つ。

## 小課題と解説

ある最適化で出力が同じ、コードが10%短い、という二つの結果を得た。10%高速と言えるだろうか。
言えない。命令の依存、cache、branch、測定のばらつきが実行時間を変える。
値以外の意味を照合した上で、同じ workload と host と compiler 条件で時間を測る必要がある。

[前へ](09-kototama.ja.md) · [目次](README.ja.md) · [次へ ビルドの高速化](11-build-performance.ja.md)
