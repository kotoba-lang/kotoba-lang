# Amu コンパイラを順番に学ぶ

Amu は、Kotoba のプログラムを検査し、選んだ実行先の artifact に変換するコンパイラとプロジェクトリンカである。
この教材では、`42` を返すプログラムを少しずつ拡張しながら、ソースを読むところから機械語を実行するところまでを追う。
その途中で Kotoba form、IPLD、Unison に通じる内容アドレスによる定義の識別、Kototama VM、高速化を結び付ける。

読者は関数・変数・条件分岐を知っていればよい。コンパイラ、Lisp、IPLD の予備知識は各章で補う。
章ごとに「動かす例」「内部の仕組み」「読むソース」「小課題と解説」を置いた。
Simple のような段階的な読み方で、実際の Amu を調べる。各章で別のコンパイラ実装を複製する方式ではない。

## 目次

| 章 | 学ぶこと | 手を動かす対象 |
|---|---|---|
| [01 最初の 42 と全体像](01-first-program.md) | Amu、Kotoba、Kototama、AiueOS の役割 | 整数を返す |
| [02 ソースと Kotoba form](02-forms.md) | S 式、構文木、canonical form、reader sugar | 足し算を木として読む |
| [03 読み取りと名前解決](03-reader-and-names.md) | lex、read、scope、名前を解決する理由 | let と未定義名 |
| [04 型検査と中間表現](04-checking-and-ir.md) | checked KIR、Seed tree、SIR の違い | 型エラーと条件分岐 |
| [05 IPLD と内容アドレス](05-ipld.md) | DAG-CBOR、CID、block、handle | ソースと定義を区別する |
| [06 Unison に通じる定義の識別](06-definition-identity.md) | α 正規化、依存 CID、再帰 SCC | 名前変更と依存更新を考える |
| [07 effects と権限](07-effects-and-authority.md) | 静的 effect、grant、呼出し時の admission | 未対応操作を拒否する |
| [08 機械語と実行ファイル](08-code-generation.md) | lowering、命令選択、レジスタ、fixup、ABI | loop と関数呼出し |
| [09 Kototama VM](09-kototama.md) | 遷移、IPLD state、admission、receipt、typed eval | 一回の実行を記述する |
| [10 生成コードの高速化](10-runtime-performance.md) | 構造の認識、定数、loop、SIMD、fuel と境界検査 | 最適化前後の意味を比べる |
| [11 ビルドの高速化と selfhost](11-build-performance.md) | compiler 内部の費用、DefCID cache、自己再ビルド | 再利用条件を設計する |
| [12 実装を読み検証する](12-reading-and-experiments.md) | ソース探索、負の対照、測定、Simple との比較 | 小さな研究課題を完了する |

## 最初に動かす

Apple Silicon Mac の native CLI を使う。Amu の checkout で、公開入口の
[使い方と範囲](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/native-public-entry.md)
を確認してから実行する。以下の `path/to` は実際のファイルの場所に置き換える。

```sh
/path/to/amu/bin/amu check /path/to/this/docs/learn/amu/examples/00-literal.kotoba
/path/to/amu/bin/amu run /path/to/this/docs/learn/amu/examples/00-literal.kotoba
```

順に `ok` と `42` が出る。Linux の入口・target・形式は Mac と同一ではないので、
このコマンドの実行結果を Linux や browser の資格に転用しない。
[全例の観測手順と結果](examples/README.md)も用意した。

## 学習で区別する四つの状態

**言語や VM の契約**は、実装が守るべき意味を定める。
**採用されたソース**は、特定のコンパイラの構成を示す。
**実験や候補**は、その契約を満たすか調べている途中の実装である。
**実行結果や測定**は、記録した artifact・入力・ホストに対する観測である。

例えば DefCID は言語の識別契約として存在するが、すべての native ビルドが定義単位の永続 cache を使う、という意味ではない。
また native の小例が動くことと、Kototama VM の状態・権限・receipt をすべて実装したことは別である。
各章はこの違いを、その機能を説明する箇所で示す。

## 参照の基準

本教材の確認日は 2026年11月6日。Amu の実装参照は
[`a4c16e7c`](https://github.com/kotoba-lang/amu/tree/a4c16e7c47005d2235dc48d3f69569773a1606e7)、
Kototama は [`b7bbcc44`](https://github.com/kotoba-lang/kototama/tree/b7bbcc44d08913fee69074ad70f92d341df2be92) に固定する。
言語の契約はこのリポジトリの相対リンクで辿れる。
コードの場所が後に移っても、固定した参照で同じ説明を読み直せる。
新しい版を使うときは、所有者の正本・manifest・実行証拠を確かめる。

[言語 Docs の入口](../../README.md)へ戻る。
