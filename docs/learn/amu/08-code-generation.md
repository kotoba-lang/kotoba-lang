# 08 Machine code and executables

**English** | [日本語](08-code-generation.ja.md)

Executing checked computation on a CPU requires choosing value locations, branch destinations, calling conventions, and instructions. This chapter follows the native AArch64 core; other targets have their own encodings and ABIs.

## Run a loop and a function

[04-loop.kotoba](examples/04-loop.kotoba) iterates six times and returns `42`:

```clojure
(loop [i 0 acc 0]
  (if (< i 6)
    (recur (+ i 1) (+ acc 7))
    acc))
```

`recur` supplies the next binding values simultaneously. Lowering must not overwrite one binding early and make its new value visible to a later argument. A modified accumulator expression that uses `i` helps expose this difference.

[05-functions.kotoba](examples/05-functions.kotoba) passes `21` to `double`. Its checked parameter and result types let the backend determine the required call layout.

## From instructions to an executable

| Stage | Native core component | Decisions |
|---|---|---|
| Lowering | `30-lower` | Checked tree to SIR, including locals, labels, calls, and fuel |
| Instruction encoding | `40-a64enc` | AArch64 opcode, register, and immediate bit fields |
| Code generation and optimization | `41-a64gen` | Instruction selection, constants, value locations, calls, and branches |
| Layout and fixups | `42-layout` | Literal pools, branch distances, unresolved references, exports |
| Output and packaging | `50-out` and target packager | Construct an artifact in the selected format |

Registers are small, fast CPU storage locations. Spilling values to the stack adds loads and stores. An ABI specifies where arguments and results go and which registers a call must preserve. Types alone do not determine that target- and host-specific convention.

A fixup completes a reference whose location was not yet known. `42-layout` places the literal pool and fills immediate fields while preserving opcode and register bits. It must also handle references outside an instruction's encodable range.

The Mac CLI's `compile` produces a signed native executable containing source. `run PROGRAM` does not execute arbitrary supplied bytes directly: it recompiles the embedded source, checks the entire image, then executes the regenerated private image. This describes the bounded current Mac entry.

## Read the source

Read [40-a64enc](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/40-a64enc.kotoba), [41-a64gen](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/41-a64gen.kotoba), and [42-layout](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/seed/42-layout.kotoba). Start with `gn-op-bin`, `gn-op-tail-call`, and `ly-run`. See [native-public-entry](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/native-public-entry.md) for public packaging behavior.

## Exercise and answer

Can `(+ 40 2)` compile correctly without any addition instruction? Yes: its result is already known, so code can place `42` directly. The transformation must also preserve the profile's fuel, trap, and effect observations. Source operation counts need not match machine-instruction counts.

[Previous](07-effects-and-authority.md) · [Contents](README.md) · [Next: the Kototama VM](09-kototama.md)
