# 04 型検査と中間表現

型検査は、プログラムが使う値と操作が噛み合うかを調べる。
中間表現 IR は、検査や変換やコード生成に適した形にしたプログラムである。
一つのコンパイラに複数の IR があってよい。

## 動かす例

[03-branch.kotoba](examples/03-branch.kotoba) は let に条件分岐を加える。

```clojure
(let [x 40]
  (if (= x 40) (+ x 2) 0))
```

比較の結果は bool、if の両分岐は i64 である。結果は `42`。
一方 [negative-type.kotoba](examples/negative-type.kotoba) の `(+ 40 true)` は、
観測した CLI で `E2104 type mismatch in '+'` として拒否された。
bool の bit を整数として扱って実行する経路には進まない。

## KIR と Seed tree と SIR

| 表現 | 主な役割 |
|---|---|
| typed KIR | 検査された意味を表し、interface・effect・定義 identity の契約に結び付ける |
| Seed の checked tree と表 | native frontend が名前・型などの検査結果を記録し、lowering に渡す |
| SIR | native core がコード生成へ渡す命令列。local、temporary、label、call、fuel 等を明示する |
| machine code | target の実際の命令と配置 |

`seed/12-kirread` は KIR 入力を Seed の tree へ読む別の入口であり、
通常のソースを読む `11-read` と同じ仕事ではない。
ソースの全経路が「KIR を一度ファイルに保存して読む」と考える必要はない。

`30-lower` は checked tree の解決情報を読み、SIR を作る。
次は加算の説明用の擬似 IR であり、実際の dump の文法や最適化結果ではない。

```text
t0 = CONST 40
t1 = CONST 2
t2 = ADD t0 t1
RET t2
```

実際の SIR は `OP-CONST`、`OP-BIN`、`OP-LGET`、`OP-LSET`、`OP-BRZ` 等を持つ。
`if` には label と分岐、`loop/recur` には反復と束縛の更新が要る。
SIR を作った段階と、backend で定数畳込み等を行った後を混同しないようにする。

## 読むソース

[21-check](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/21-check.kotoba)、
[30-lower](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/30-lower.kotoba)、
[SIR 契約](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/SIR)。
型の名前と許される操作は [HEADS](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/HEADS) にある。

## 小課題と解説

if の片側を bool に変えた場合、両方の分岐を実行してから型を選べるだろうか。
選べない。この main の戻り値は i64 であり、実行され得る分岐の型もそれに整合する必要がある。
検査の意味を理解すると、後段が型を毎回推測し直さずに命令を選べる理由も分かる。

[前へ](03-reader-and-names.md) · [目次](README.md) · [次へ IPLD](05-ipld.md)
