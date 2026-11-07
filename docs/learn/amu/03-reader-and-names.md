# 03 Reading and name resolution

**English** | [日本語](03-reader-and-names.ja.md)

Reading turns characters into structure. Name resolution identifies what references inside that structure point to. Separating them lets the compiler distinguish an unmatched parenthesis from an undefined variable.

## Run the example

The body of [02-bindings.kotoba](examples/02-bindings.kotoba) is:

```clojure
(let [x 40 delta 2]
  (+ x delta))
```

It returns `42`. `x` refers to the binding introduced by this let. It does not ask the host process to search for a variable with that spelling.

## Native core stages

`10-lex` separates tokens such as delimiters, names, and numbers. `11-read` builds a tree. `20-names` handles definitions and local bindings, connecting references to functions or local slots. `21-check` uses resolved information to check expressions. Read the call order in `90-drv` and the adopted module order in `seed/MANIFEST`.

```text
characters → tokens → read tree → resolved reference
"x"          symbol   ND-SYM      local slot or definition
```

A slot is an implementation index used to access a local value later. The backend need not repeatedly search source spellings. A slot belongs to this compilation; it is not a DefCID.

## Learn from a refusal

The `missing` reference in [negative-name.kotoba](examples/negative-name.kotoba) has no binding. The observed native CLI reports `E2102 unknown symbol 'missing'`. It refuses the input instead of loading something from the environment to fill the gap. The byte offset locates the problem in that source; changing its spelling can change the offset.

## Read the source

Read [10-lex](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/10-lex.kotoba), [11-read](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/11-read.kotoba), [20-names](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/20-names.kotoba), and [90-drv](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/90-drv.kotoba). Start with each file's input/output contract, then find calls to `lx-run`, `rd-run`, and `nm-run`.

## Exercise and answer

Rename the let binding `x` to `value` and update its use. The result stays the same. Forget to update the use and it becomes an undefined name. What matters is preserving which binding each reference denotes. Removing spellings while preserving that relationship leads to alpha normalization in chapter 6.

[Previous](02-forms.md) · [Contents](README.md) · [Next: checking and IR](04-checking-and-ir.md)
