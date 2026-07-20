# ADR: WASI 0.3, WIT, and the Component Model are the Kotoba platform ABI

Status: accepted; staged implementation

## Context

Kotoba already defines capability-safe component source, but its portable ABI
was spread across private core-Wasm imports and older Component Model wording.
WASI 0.3.0 is stable and adds native async functions, futures, and streams to
the Component Model. WIT worlds provide the standard closed import/export
contract, while the Canonical ABI defines lifting and lowering to core Wasm.

## Decision

The normative Kotoba platform ABI is the WebAssembly Component Model and WIT,
with WASI 0.3.0 as the current system-interface baseline. The exact upstream
revisions and language rules are recorded in `lang/wasm-component-platform.edn`.

`.kotoba` remains capability-safe component source. Its `ns` declaration and
checked effects determine a per-component WIT world. No filesystem, network,
clock, random, environment, or process interface is inferred from a target
name. Imports must be explicitly declared and admitted by policy.

Synchronous Kotoba functions lower to WIT `func`. Async is an explicit effect:
only a declared async function may lower to `async func`, return or consume a
`future<T>`, or use a `stream<T>`. Streams require item, byte, and lifetime
budgets plus cancellation semantics. Async does not legalize threads, ambient
callbacks, mutable host objects, or unbounded queues.

WIT describes transport shape. Kotoba's string, collection, schema-depth,
canonical set/map, and resource limits remain language semantics enforced on
both sides of a provider boundary. General recursive value schemas are rejected
until WIT and the Kotoba validator can preserve their identity and bounds.

`kotoba-lang/compiler` owns deterministic WIT/world generation, core Wasm,
Canonical ABI adapters, and component encoding. `kototama` owns admission,
linking, provider composition, WASI 0.3 async polling/cancellation, and runtime
budgets. Neither layer may silently extend the language surface.

## Migration

Existing core-Wasm artifacts remain legacy inputs with explicit compatibility
profiles. New qualification claims require a compiler-produced component, an
exact WIT world, closed provider composition, and shared semantic vectors.

