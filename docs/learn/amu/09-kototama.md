# 09 The Kototama VM

**English** | [日本語](09-kototama.ja.md)

Kototama is the Kotoba virtual-machine contract. It is not defined by a particular bytecode interpreter: it specifies execution conditions, world-state changes, outcomes, and execution records.

## Describe one execution

```text
program, message, state, authority, budget
    ↓ Kototama transition
outcome, state', events, receipt
```

A program alone does not define an execution. Inputs and state affect results; authority and budget can produce refusal or exhaustion. An engine implements this contract. Amu produces artifacts and a host executes them. A native-code implementation can therefore fulfill a VM role too.

## Four perspectives

**Computation** follows closed S-expression semantics. Names are resolved; host reflection and unrestricted eval are not introduced. Canonical program wire forms use tagged DAG-CBOR vectors, while execution can use target-specific typed calls and contiguous memory.

**State** is an IPLD world. A message accumulates changes in an overlay. Success commits a new root; revert, refusal, and resource exhaustion discard the relevant overlay. Nested-call snapshots and restoration are part of the contract. Rolling back state does not automatically undo effects already performed by an external service.

**Admission and authority** compare the conditions from chapter 7 using a closed predicate with explicit origins. Policy authoring and authentication adapters are separate from that invocation decision.

**Receipts** make an execution record a value. They bind the VM, implementation, profile, program/message CIDs, before/after state roots, outcome, fuel, events, and authority decision. A receipt CID identifies a record. Trust in its signer, consensus, and duplicate-execution prevention need separate mechanisms.

## Typed eval calls a checked definition

Typed eval does not submit source text to host eval. It resolves and verifies a Definition CID and its closed dependencies, then re-admits the interface and complete effect row under current conditions. The Definition CID identifies the computation, the Admission CID identifies execution conditions, and the Value CID identifies the result. Eval depth and fuel are bounded too.

The pure-result cache contract binds the Admission CID and input Value CIDs. It does not silently replace a fresh HTTP request or payment effect with a previous result.

## Separate the VM contract from implementation profiles

`core/v1`, `fvm-actor/v1`, `evm/v1`, and `fevm/v1` each need their own compatibility scope and evidence. An EVM-like architectural role does not establish Ethereum bytecode, state, or consensus compatibility. Emitting Wasm does not establish the full Kototama state-transition contract either.

Amu's `kototama.pure-i64-aot/v1` and `kototama.pure-i64-receipt/v2` are separately defined bounded profiles for closed integer computation with empty guest authority. The course's `main → 42` examples do not qualify IPLD worlds, every operation, or every target.

## Related execution models: Filecoin FVM and FEVM

The [Filecoin Virtual Machine overview](https://docs.filecoin.io/core-concepts/filecoin-virtual-machine) describes a Wasm-based execution environment for actors with IPLD data primitives. The [Filecoin EVM runtime documentation](https://docs.filecoin.io/core-concepts/filecoin-evm-runtime) describes FEVM as an EVM runtime layered on FVM. These sources help distinguish an execution platform from a compatibility runtime on that platform.

Kototama's [VM specification and profile definitions](https://github.com/kotoba-lang/kototama/blob/b7bbcc44d08913fee69074ad70f92d341df2be92/docs/kototama-virtual-machine.md) explicitly relate its architectural role to EVM/FVM. Its FVM-shaped profile covers actor messages, IPLD state, nested calls, gas and rollback. Read each profile's declared scope and evidence before making a compatibility claim. Filecoin consensus and storage proofs are outside Kototama's VM contract.

## Read the authorities

Read the [VM spec](https://github.com/kotoba-lang/kototama/blob/b7bbcc44d08913fee69074ad70f92d341df2be92/spec/kototama-vm-v1.edn), [VM explanation](https://github.com/kotoba-lang/kototama/blob/b7bbcc44d08913fee69074ad70f92d341df2be92/docs/kototama-virtual-machine.md), [typed eval contract](../../../lang/typed-eval.edn), and [native i64 receipt profile](https://github.com/kotoba-lang/amu/blob/a4c16e7c47005d2235dc48d3f69569773a1606e7/docs/kototama-i64-receipt.md). Older checkout descriptions centered on Datalog/Biscuit are not descriptions of the current admission core.

## Exercise and answer

Does having the same message and receipt CID establish that the message executed only once? No. Hashes identify content. Preventing duplicate execution requires an appropriate nonce, consumption journal, idempotency contract, or other mechanism.

[Previous](08-code-generation.md) · [Contents](README.md) · [Next: runtime performance](10-runtime-performance.md)
