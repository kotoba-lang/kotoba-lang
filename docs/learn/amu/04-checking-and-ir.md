# 04 Type checking and intermediate representations

**English** | [日本語](04-checking-and-ir.ja.md)

Type checking establishes that values and operations fit together. An intermediate representation, or IR, expresses a program in a form suitable for checking, transforming, or generating code. A compiler can use several IRs.

## Run the example

[03-branch.kotoba](examples/03-branch.kotoba) adds a conditional:

```clojure
(let [x 40]
  (if (= x 40) (+ x 2) 0))
```

The comparison produces a bool, and both branches produce i64 values. The result is `42`. In contrast, `(+ 40 true)` in [negative-type.kotoba](examples/negative-type.kotoba) produced `E2104 type mismatch in '+'` in the observed CLI. It does not proceed by treating the bool's bits as an integer.

## KIR, the Seed tree, and SIR

| Representation | Main purpose |
|---|---|
| Typed KIR | Represents checked meaning and connects it to interface, effect, and definition-identity contracts |
| Seed checked tree and tables | Record native frontend resolution and checking information for lowering |
| SIR | Provides instructions for native code generation, including locals, temporaries, labels, calls, and fuel |
| Machine code | Contains actual target instructions and layout |

`seed/12-kirread` is a separate entry that reads KIR into a Seed tree. It is not the source reader `11-read`. Ordinary source compilation need not save a KIR file and read it back.

`30-lower` reads checked-tree resolution information and constructs SIR. This is explanatory pseudo-IR, not a real dump syntax or observed optimization result:

```text
t0 = CONST 40
t1 = CONST 2
t2 = ADD t0 t1
RET t2
```

Actual SIR has operations such as `OP-CONST`, `OP-BIN`, `OP-LGET`, `OP-LSET`, and `OP-BRZ`. An if needs labels and branches; a loop/recur needs iteration and binding updates. Keep the output of lowering separate from later backend transformations such as constant folding.

## Read the source

Read [21-check](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/21-check.kotoba), [30-lower](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/30-lower.kotoba), and the [SIR contract](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/SIR). [HEADS](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/HEADS) describes types and admitted operations.

## Exercise and answer

If one branch returns a bool, can the compiler execute first and select the result type afterward? No: this main promises an i64, so reachable branch results must satisfy that contract. Checking gives the backend information it can use without rediscovering types during each operation.

[Previous](03-reader-and-names.md) · [Contents](README.md) · [Next: IPLD](05-ipld.md)
