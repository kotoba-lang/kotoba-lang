# 02 ソースと Kotoba form

[English](02-forms.md) | **日本語**

コンパイラは文字列を直接「理解」するのではなく、まず式の構造をデータにする。
Lisp の S 式はその構造を人も読みやすい形で表している。

## 動かす例

[01-arithmetic.kotoba](examples/01-arithmetic.kotoba) は本体を `(+ 40 2)` に変える。実行結果は `42`。
括弧の中で最初の要素が演算、残りがその引数である。この式は概念的には次の木になる。

```text
list
├─ symbol "+"
├─ integer 40
└─ integer 2
```

`+` という文字列と、加算という検査済み操作はまだ同じではない。
reader の後で、何を参照しているか、どんな型に使えるかを確かめる。

## form という語の三つの使われ方

**ソース form** は作者が書いた一つの式や宣言である。
**フロントエンドの Form 値**は、それを扱う構文木である。
**canonical なプログラム form** は、意味・表現の契約に従って解決・検査されたプログラムを表す。
同じ語が現れても、文字列・一時 AST・保存用の正規形のどれかを確認しよう。

Amu の `src/kotoba/form.kotoba` は `:form/r` レコードを持つ。
`tag`、文字列 `s`、整数 `n`、keyword `k`、子の `kids`、位置の `span`、生バイトの `data` で構成される。
子はソース順、map の子は key/value の交互の並びである。
`with-children` は子を取り替えても span やスカラー・浮動小数点の bit を保つ。
エラーを元の場所へ戻したり、変換で値を壊さないために必要な情報である。

一方、採用された native Seed core は `NODE` 等の表を一つの明示的なメモリ領域に持つ。
この教材で native pipeline を読むときは、その表を追う。
`kotoba.form` のレコードが現在の全 native 工程を通ると考えてはいけない。

## 表記と意味を分ける

canonical `.kotoba` と CLJK／EDN の reader sugar は、言語契約上、同じ core form に到達するべき表記である。
入力の sugar を受け取ることは、コンパイラ自身を JVM や nbb 上で動かすこととは別の話である。
具体的な表記の対応は [surface equivalence corpus](../../../lang/surface-equivalence-corpus.edn) で確認する。
その契約があることと、公開 Mac CLI が全表記を実装したことも区別する。

構文がデータだからといって、任意の式を host `eval` に渡す権限は得られない。
quote、desugar、typed eval はそれぞれ許される位置と入力が定まっている。

## Unix のパイプを Kotoba の関数呼び出しで表す

Kernighan と Pike は、入力から頻出単語を10件取り出す例に `tr | sort | uniq -c | sort -n | tail` を使っている。Kotoba で書いた [wordfreq.kotoba](examples/wordfreq.kotoba) では、単語の開始位置を関数間で渡す。 [The Unix Programming Environment 第4章](https://eecs.wsu.edu/~cs224/UPE.pdf#page=120)

```clojure
(defn- wordfreq [text :string limit :i64] :i64
  (let [lower (string-fold-case text)]
    (->> lower
         (word-starts)
         (top-n lower limit))))
```

`->>` は各段の結果を次の呼び出しの最後の引数に渡す。この例は `(top-n lower limit (word-starts lower))` に展開される。`top-n` は単語の開始位置を有限のベクターとして受け取り、出現回数を数え、上位の単語を `:io/write` で標準出力に書く。

言語リポジトリのルートから `/path/to/amu/bin/amu run docs/learn/amu/examples/wordfreq.kotoba` を実行する。macOS ARM64 のネイティブ実行では、出力の先頭が `3 to`、`3 be` になった。末尾の `0` は `main` の返り値。この例はソース内の短い ASCII 文字列を使い、大文字と小文字を同一視する。出現回数の多い順に出力し、同数なら初出順に並べる。単語ごとにベクターを走査して数えるため、短い入力向けの実装である。 [実行に使った Amu のリビジョン](https://github.com/kotoba-lang/amu/tree/87ec570aff47a0a21b4876fcda4fa9d4e3b934fb)

## 背景となる一次資料: S式

John McCarthy の [1960年の Lisp 論文](https://www-formal.stanford.edu/jmc/recursive.html)は、記号式と再帰的な計算を学ぶ一次資料である。上の式の木と対応付けて読むとよい。Kotoba の typed form、表現の契約、effect の admission は Kotoba 自身の仕様で定める。

## 読むソース

[Form の schema と constructor](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/src/kotoba/form.kotoba)、
[Seed のメモリ配置](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/MEMORY-MAP)、
[elaboration pipeline](../../../lang/elaboration-pipeline.edn)。

## 小課題と解説

`(+ 40 2)` と `(+  40\n 2)` はどこが同じで、どこが違うだろうか。
読んだ式の構造と意味は同じだが、元のバイト列と span は違う。
したがってソースのハッシュは変わる。一方、同じ契約で検査・正規化された定義の identity は、
この空白の違いでは変わらない設計になっている。第5・6章で理由を追う。

[前へ](01-first-program.ja.md) · [目次](README.ja.md) · [次へ 読み取りと名前解決](03-reader-and-names.ja.md)
