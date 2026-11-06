# 03 読み取りと名前解決

[English](03-reader-and-names.md) | **日本語**

読み取りは文字から構造を作る工程、名前解決は構造の中の参照先を確定する工程である。
この二つを分けると、「括弧が合わない」と「変数が存在しない」を別の場所で説明できる。

## 動かす例

[02-bindings.kotoba](examples/02-bindings.kotoba) の本体を読む。

```clojure
(let [x 40 delta 2]
  (+ x delta))
```

この例も `42` を返す。`x` は文字として何かを探すための命令ではなく、この let が導入した束縛を参照する。
同じ関数の外の名前や、host プロセスに存在する変数を勝手に探すことはない。

## native core の工程

`10-lex` は括弧・名前・数値などを token に分け、`11-read` が token から木を作る。
`20-names` は定義と局所束縛を扱い、参照を関数や local の slot に結び付ける。
`21-check` はその解決結果を使って式を検査する。
実際の処理の順序と呼出し元は `90-drv`、採用モジュールの順序は `seed/MANIFEST` で読む。

```text
文字列   → token の列 → read tree → 解決した参照
"x"        symbol      ND-SYM      local slot または definition
```

slot は、後で local の値を読むための実装上の番号である。
後段で毎回ソースの綴りを検索する必要がなくなる。
これは DefCID とは別の、一回のコンパイル内の表現である。

## 拒否から理解する

[negative-name.kotoba](examples/negative-name.kotoba) の `missing` は束縛されていない。
観測した native CLI は `E2102 unknown symbol 'missing'` を返した。
外部から何かをロードして補完するのではなく、この入力は検査を通らない。
byte offset はソースのどこで問題を見付けたかを示す。綴りを変えるとその位置も変わり得る。

## 読むソース

[10-lex](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/10-lex.kotoba)、
[11-read](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/11-read.kotoba)、
[20-names](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/20-names.kotoba)、
[90-drv](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/90-drv.kotoba)。
まず各ファイル冒頭の入力・出力契約を読み、次に `lx-run`、`rd-run`、`nm-run` の呼出しを探そう。

## 小課題と解説

let の束縛を `x` から `value` へ変え、使用箇所も変える。結果は変わらない。
使用箇所だけ変え忘れると未定義名になる。
重要なのは綴りの一致だけではなく、「どの束縛を参照するか」が一貫していることだ。
この関係を保持して綴りを取り除く操作が、第6章の α 正規化につながる。

[前へ](02-forms.ja.md) · [目次](README.ja.md) · [次へ 型検査と IR](04-checking-and-ir.ja.md)
