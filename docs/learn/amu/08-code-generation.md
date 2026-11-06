# 08 機械語と実行ファイル

検査済みの計算を CPU に実行させるには、値の位置・分岐先・呼出し規約を決め、実際の命令を並べる。
ここでは native AArch64 core の順序を読む。他の target の encoding と ABI はそれぞれ別である。

## loop と関数を動かす

[04-loop.kotoba](examples/04-loop.kotoba) は次を6回反復して `42` を返す。

```clojure
(loop [i 0 acc 0]
  (if (< i 6)
    (recur (+ i 1) (+ acc 7))
    acc))
```

`recur` は同じ束縛の次の値を同時に渡す。
lowering が一つずつ旧変数を上書きして、後の引数から新しい値が見えてしまうと意味が変わる。
例えば次の反復で `i` を使って累積する式へ変えると、この差を検査しやすい。

[05-functions.kotoba](examples/05-functions.kotoba) は `double` に `21` を渡す。
引数の型と戻り値を確認できるので、backend はその呼出しに必要な配置を決められる。

## 命令から実行ファイルまで

| 工程 | Amu native core の対応 | 決めること |
|---|---|---|
| lowering | `30-lower` | checked tree を SIR へ。local、label、call、fuel 等 |
| 命令の encoding | `40-a64enc` | AArch64 の opcode、レジスタ番号、即値の bit 配置 |
| コード生成と最適化 | `41-a64gen` | 命令選択、定数、値の置き場、呼出し・分岐等 |
| layout と fixup | `42-layout` | literal pool、分岐距離、未確定の参照、export table |
| output と packager | `50-out` と target の packager | コードから対象形式の artifact を構成 |

レジスタは CPU 内の小さい高速な値の置き場である。足りない値を stack に退避すると load/store が増える。
関数を呼ぶときには、どのレジスタに引数と結果を置き、何を保存するかという ABI を守る。
それは型が同じというだけでは決まらず、target と host の規約を要する。

fixup は、まだ場所が分からない参照を後で確定する仕組みである。
`42-layout` は literal pool を配置し、命令の opcode やレジスタの bit を保ちながら即値部分を埋める。
相対分岐が命令で表せる距離を超えた場合も扱わなければならない。

Mac CLI の `compile` は source を含む署名済み native executable を作る。
`run PROGRAM` は入力 bytes をそのまま任意実行するのではなく、埋め込まれた source を再コンパイルし、
全 image を照合してから private image を実行する。これは現在の bounded な Mac 入口の動作である。

## 読むソース

[40-a64enc](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/40-a64enc.kotoba)、
[41-a64gen](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/41-a64gen.kotoba)、
[42-layout](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/42-layout.kotoba)。
まず `gn-op-bin`、`gn-op-tail-call`、`ly-run` を探そう。
公開入口のパッケージングは [native-public-entry](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/native-public-entry.md) で確認する。

## 小課題と解説

`(+ 40 2)` が最終的に加算命令を一つも含まなくても正しいだろうか。
定数だけの式なら `42` を直接置ける。ただし値に加えて、その profile の fuel、trap、effect 等の観測も守る必要がある。
最適化を読むときは、ソースの命令の数と machine code の命令の数が一致するとは考えない。

[前へ](07-effects-and-authority.md) · [目次](README.md) · [次へ Kototama VM](09-kototama.md)
