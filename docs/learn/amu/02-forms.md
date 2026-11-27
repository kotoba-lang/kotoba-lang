# 02 Source and Kotoba forms

**English** | [日本語](02-forms.ja.md)

A compiler first turns source text into structured data. Lisp's S-expressions make much of that structure readable to people too.

## Run the example

[01-arithmetic.kotoba](examples/01-arithmetic.kotoba) changes the body to `(+ 40 2)` and still returns `42`. The first list element names the operation; the remaining elements are its arguments. Conceptually, the tree is:

```text
list
├─ symbol "+"
├─ integer 40
└─ integer 2
```

The text `+` is not yet a checked addition operation. After reading, the compiler must resolve the reference and determine which types it accepts.

## Three uses of the word form

A **source form** is an expression or declaration written by an author. A **frontend Form value** is a syntax tree used to process it. A **canonical program form** represents a resolved, checked program under a defined semantic and representation contract. Identify which meaning is intended: text, temporary AST, or canonical stored representation.

Amu's `src/kotoba/form.kotoba` defines a `:form/r` record with a `tag`, string `s`, integer `n`, keyword `k`, child list `kids`, source `span`, and raw bytes `data`. Children preserve source order; map children alternate keys and values. `with-children` replaces children while preserving spans, scalar payloads, and floating-point bits. Those details support useful error locations and faithful transformations.

The adopted native Seed core instead uses tables such as `NODE` in an explicit memory area. Follow those tables when reading the native pipeline in this course. The `kotoba.form` record does not pass through every current native stage.

## Separate spelling from meaning

The language contract requires canonical `.kotoba` and its CLJK/EDN reader sugar to reach the same core forms. Accepting input sugar is separate from running the compiler itself on JVM or nbb. Consult the [surface equivalence corpus](../../../lang/surface-equivalence-corpus.edn) for concrete pairs. A contract also does not establish that the public Mac CLI implements every spelling.

Representing syntax as data grants no permission to call unrestricted host `eval`. Quote, desugaring, and typed eval each have their own permitted positions and inputs.

## From a Unix pipe to Kotoba function calls

Kernighan and Pike use `tr | sort | uniq -c | sort -n | tail` to find the ten most frequent words in an input. The Kotoba version in [wordfreq.kotoba](examples/wordfreq.kotoba) passes the positions of words between functions. See [The Unix Programming Environment, chapter 4](https://eecs.wsu.edu/~cs224/UPE.pdf#page=119).

```clojure
(defn- wordfreq [text :string limit :i64] :i64
  (let [lower (string-fold-case text)]
    (->> lower
         (word-starts)
         (top-n lower limit))))
```

`->>` puts each result in the last argument of the next call. Here the calls expand to `(top-n lower limit (word-starts lower))`. The input to `top-n` is a finite vector of word positions. It counts occurrences and prints the most common words to stdout through `:io/write`.

Run `/path/to/amu/bin/amu run docs/learn/amu/examples/wordfreq.kotoba` from the language repository. The native macOS ARM64 run begins with `3 to` and `3 be`. The final `0` is the value returned by `main`. This example uses a small ASCII string in the source, folds case, orders counts from highest to lowest, and breaks ties by the word's first appearance. Counting scans the vector for each distinct word, so the example is sized for short inputs. See the [Amu revision used for the run](https://github.com/kotoba-lang/amu/tree/87ec570aff47a0a21b4876fcda4fa9d4e3b934fb).

## Background reference: S-expressions

John McCarthy's [Recursive Functions of Symbolic Expressions and Their Computation by Machine, Part I](https://www-formal.stanford.edu/jmc/recursive.html), published in 1960, is a primary source for Lisp's symbolic expressions and recursive computation. Read it alongside the tree above. Kotoba's typed forms, representation contracts, and effect admission are defined by Kotoba's own specifications.

## Read the source

Read the [Form schema and constructors](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/src/kotoba/form.kotoba), [Seed memory layout](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/MEMORY-MAP), and [elaboration pipeline](../../../lang/elaboration-pipeline.edn).

## Exercise and answer

Compare `(+ 40 2)` with `(+  40\n 2)`, where `\n` denotes a newline in the source. The expression structure and meaning agree, but source bytes and spans differ. The source hash changes. The definition identity is designed to ignore that whitespace after checking and normalization under the same contract. Chapters 5 and 6 explain how.

[Previous](01-first-program.md) · [Contents](README.md) · [Next: reading and names](03-reader-and-names.md)
