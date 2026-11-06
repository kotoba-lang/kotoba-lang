# 12 実装を読み検証する

ここまでで、ソース・form・検査・IR・identity・artifact・実行という流れを一周した。
次は自分が知りたい振る舞いを一つ選び、入力から観測までを結ぶ。

## 実装を読む順序

最初に `seed/MANIFEST` と `FULL-MANIFEST` を見て、そのファイルが採用 closure に入るか確認する。
次に `90-drv` の入口から `lex → read → names → check → lower → gen → layout → out` を追う。
各モジュール冒頭の契約、成功・失敗の戻り方、対応する unit fixture の順で読むと、
大きい backend のすべてを一度に読まずに済む。

| 知りたいこと | 読む場所 | 観測する対象 |
|---|---|---|
| 名前の誤り | `20-names`、`21-check` | check の拒否と source position |
| loop の意味 | `30-lower` と `seed/SIR` | 並行な recur 更新、結果、fuel |
| 定数の生成 | `41-a64gen`、`40-a64enc` | artifact bytes と機械語、実行結果 |
| 分岐距離 | `42-layout` | fixup と範囲外の拒否 |
| 定義の同一性 | code-identity と standalone native producer | canonical payload bytes と DefCID |
| 実行許可 | Kototama `eligible?`／`admit` | 欠けた origin ごとの拒否 |

## Simple と対応させる

[Simple](https://github.com/SeaOfNodes/Simple) は独立した動く compiler を章ごとに育て、
Sea-of-Nodes の IR、SSA、型、最適化、backend を詳しく説明する。
Amu を読むときにも「整数→算術→local→if→loop→call→codegen」という順番を借りられる。
ただし Amu の native core が全体として Simple と同じ SoN 表現を使うという意味にはならない。

Amu には [Simple Chapter 9 を参照した rooted rewrite 診断](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/coscientist-rewrite-proof-20261006.md) がある。
逆向きの利用関係を持つ worklist で、ある node の変更をその利用者へ伝える考えを学べる。
この診断は完全な SoN／SSA 変換や製品 emitter の高速化を主張しない。

## 小さな実験を完成させる

1. 仮説を一文にする。例は「local の改名は結果を変えない」。
2. 入力と expected output を先に固定する。元の全体を残し、都合のよい部分だけに縮めない。
3. 間違った変更も作る。例は束縛だけを改名し、使用箇所を変え忘れた入力。
4. 同じ compiler で検査・実行し、exit、stdout、stderr と入力・artifact の digest を記録する。
5. 結果を仮説の範囲で述べる。DefCID を観測していないなら identity の実証とはしない。

速度を調べるときは正しさを確認した後、compiler と source、target と CPU、最適化設定、
runtime profile、入力、warm/cold、試行の順序、host の負荷、除外条件を固定する。
時間の集計だけでなく全採用・不採用サンプルを残す。
ノイズより小さい差や、命令が短くなっただけの結果を速度改善にしない。

## 最終課題と解説

`double` を使う例について、次の四つを説明してみよう。

- `double` の局所引数を改名しただけなら、結果と正規化した定義の関係はどうなるか。
- `double` の計算を変更すると、その依存を seal した `main` に何が起きるか。
- 同じ定義から別の target の machine code を作れるのはなぜか。
- 同じ machine code を cache から得ても、失効した grant で外部操作できないのはなぜか。

解説は順に、束縛関係を保つ α 正規化、依存 CID の伝播、意味と artifact の分離、
immutable code identity と現在の admission の分離である。
この四つを一つの例でつなげられれば、Amu の compiler と IPLD と Kototama を役割ごとに理解できている。

[前へ](11-build-performance.md) · [目次](README.md) · [例の観測結果](examples/README.md)
