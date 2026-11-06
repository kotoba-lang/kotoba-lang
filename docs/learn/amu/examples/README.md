# Amu 教材の実行例

6本の正常例は、整数・算術・let・if・loop・関数呼出しを順番に加える。
すべて `check` は `ok`、`run` は `42`、成功 exit code は `0` を期待する。
3本の負の対照は検査時の拒否を期待する。

| ファイル | 追加する内容 | 観測した結果 |
|---|---|---|
| [00-literal](00-literal.kotoba) | 引数なしの main と i64 | 42 |
| [01-arithmetic](01-arithmetic.kotoba) | `+` | 42 |
| [02-bindings](02-bindings.kotoba) | `let` | 42 |
| [03-branch](03-branch.kotoba) | 比較と `if` | 42 |
| [04-loop](04-loop.kotoba) | `loop/recur` | 42 |
| [05-functions](05-functions.kotoba) | 型付き helper の呼出し | 42 |
| [negative-name](negative-name.kotoba) | 未定義の変数 | E2102、exit 1 |
| [negative-operation](negative-operation.kotoba) | 未対応の `slurp` | E2101、exit 1 |
| [negative-type](negative-type.kotoba) | 整数の加算に bool | E2104、exit 1 |

## 自分で観測する

既存の Amu native Mac ARM64 CLI を指定し、未使用の出力ディレクトリを選ぶ。
observer はそこに入力をコピーする。元の例を書き換えず、既存の観測を上書きしない。
`/path/to` は実際の場所に置き換える。

```sh
python3 /path/to/amu/scripts/seed/native_build_queue.py -- \
  python3 docs/learn/amu/examples/observe.py \
  --amu /path/to/amu/bin/amu \
  --output /path/to/new-observation-directory
```

Python はファイルの staging、native process の起動と出力・hash の記録だけを行う。
Kotoba を解釈・変換したり machine code を生成したりしない。
検査とコンパイル・実行は指定した native Amu が行う。
Amu の共有 build queue は既存の同時実行数を尊重するために使う。

## 記録した実行

[2026年11月6日の生の観測](observations-macos-arm64-20261106.json)は macOS ARM64 の15件である。
6本の check/run と3本の拒否がすべて期待に一致した。
CLI の SHA-256 は `72e26e48e58d0cb9ce58f081b97877ffc132fdfff35ad371963d87aa97c0b310`。
指定した CLI の native compiler image と packager の digest、および source revision も記録している。
これらは [Amu の CLI descriptor](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/bootstrap/aarch64-macos/cli.json) に対応する。

この結果は教材の小例がその CLI で動いた観測であり、性能比較、全言語 conformance、
DefCID producer、IPLD state、Kototama VM 全体、Linux／browser の実行を検証したものではない。
本文の擬似 IR、identity と admission の模式図はこの observer の dump ではない。

[教材の目次](../README.md)
