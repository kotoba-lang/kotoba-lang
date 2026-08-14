# ADR — ValueCID, Handle, and Capability are three different identities

- **Status**: Accepted — reference ValueCID/Handle runtime, core Wasm
  transport, C-free normalized aiueos dispatch, bounded entry normalization,
  live syscall wiring, provider-generation grant policy, and sealed provider
  request queue and request-bound C-free CAS digest admission implemented;
  canonical value/descriptor binding and CPL3 semantic evidence are staged
- **Date**: 2026-08-12
- **Authority**: `lang/value-codec.edn`
- **Related**: `ADR-kotoba-canonical-value-codec.md`,
  `ADR-kotoba-content-addressed-codebase.md`,
  `ADR-safe-capability-language.md`
- **Implementation**:
  `kotoba-lang/io-ipld:src/kotoba/value/codec.cljc`,
  `kotoba-lang/codebase:src/kotoba/codebase/value_runtime.cljc`,
  `kotoba-lang/kotoba-lang:lang/value-runtime-native.edn`

## Decision

Kotoba has three identities with different lifetimes and security meanings:

```text
ValueCID = global immutable value identity
Handle   = bounded run-local execution location
Cap      = authority to perform an effect
```

They must never be substituted for one another.

```text
canonical kotoba.value.v1 bytes
              │ hash
              ▼
          ValueCID ─────── persistent verified CAS
              │ hydrate/intern once
              ▼
           Handle ──────── run-local table ─────── host object / memory

Capability ── authorizes an operation; it is not derived from either arrow
```

A ValueCID is therefore a logical address, never a physical address. Execution
does not put a CID in every stack slot and does not perform a hash-table lookup
for every projection. A host resolves or interns the CID once and guest code
carries a bounded machine-word handle. A moving collector or another process
may choose a different physical location and a different handle without moving
the ValueCID.

## Canonical ValueCID

`ValueCID(v)` is CIDv1 with codec `dag-cbor` and SHA-256 over the exact bytes
produced by `kotoba.value.v1`:

```text
ValueCID(v) = cidv1(dag-cbor, sha2-256(encode-value(v)))
```

No second value codec is introduced. Unsupported or authority-bearing host
objects continue to fail at the existing canonical codec. Equal maps and sets
have one ValueCID independent of insertion order. Byte arrays are copied
through encode/decode before retention; caller mutation cannot change the
value behind an already issued CID or handle.

## Runtime table contract

Each run owns a table bounded to at most 4,096 live handles by the reference
profile. Handle zero is invalid. Handles increase monotonically and are not
reused after release, preventing an ABA substitution where a stale word begins
to name unrelated content.

The table supports four semantic operations:

1. `intern(value) -> handle`: canonicalize, persist, and deduplicate by CID.
2. `hydrate(cid) -> handle`: verify the CAS block, validate it as
   `kotoba.value.v1`, then install it locally.
3. `resolve(handle) -> value`: reject forged/released handles and return a
   defensive decoded value.
4. `cid-of(handle) -> cid`: recover portable identity for persistence,
   transport, receipts, or cache keys.

The reference kernel lives in `kotoba-lang/codebase`, because it joins the
canonical value codec to a verified content store. `kotoba-kir` remains the
checked IR and reference evaluator; `amu`, `kotoba-wasm`, and `kotoba-native`
consume the runtime ABI instead of defining another ValueCID algorithm.

The shared synchronous host contract is `:kotoba.value-runtime/v1`. Its five
operations are `intern`, `hydrate`, `resolve`, `cid-of`, and `release` over
canonical bytes, CID text, and scalar handles. It rejects extra request fields,
unknown operations, non-byte value input, oversized input, malformed CID text,
and invalid handles. Typed core Wasm uses `externref` for canonical bytes/CID
text and `i64` for the Handle. Neither transport may change the identity
algorithm or derive authority from a CID or Handle. Component packaging
currently rejects the private core import namespace instead of emitting an
unbindable Component.

## Native ownership and the C-free boundary

Production native does **not** extend Amu's hosted `kexe_loader.c` context
table. That loader and its Windows sibling are compatibility, sanitizer, and
regression fixtures; they are excluded from production-native qualification by
root ADR-2608110400, Amu ADR-0240, kotoba-native ADR-0029, and aiueos ADR-0013.
Adding ValueCID codec, CAS access, or provider transport to those C files would
move semantic identity and authority into the wrong TCB.

The production dependency is one-way:

```text
kotoba.value.v1 / :kotoba.value-runtime/v1
  -> KIR + independent verifier + kotoba-native machine semantics
  -> Amu x86_64-aiueos-user-v1 artifact
  -> aiueos C-free CPL3 process
  -> typed capability syscall
  -> sealed ValueRuntime provider / verified CAS
```

The aiueos kernel must independently validate the capability ID, handle
generation, owner domain, rights, provider descriptor, and every request/result
range. `hydrate` and persistence-capable `intern` may execute only through an
explicitly installed provider capability; the presence of a CID is never the
grant. Local `resolve`, `cid-of`, and `release` remain operations on the
process-owned bounded value table. Until that syscall/provider path and its
positive and fail-closed machine vectors exist, KIR value-runtime operations
remain rejected by native admission. A hosted C-loader success cannot satisfy
VR5.

The machine-readable native contract binds persistence to the existing object
capabilities instead of minting a new ambient runtime authority:

```text
intern  -> object/put-block  (wire 15)
hydrate -> object/get-stream (wire 14)
resolve / cid-of / release -> process-local Handle table
```

`kotoba-native` additionally refuses to package a manually sealed
`x86_64-aiueos-user-v1` artifact containing any ValueRuntime operation while
the provider is pending. This is a defense behind KIR admission: bypassing the
front gate still cannot silently fall back to the hosted C context.

## Stack, heap, and collection

Stack frames contain scalar values and handles. They do not contain physical
pointers to host-owned objects and do not need to contain full CIDs:

```text
frame { x = Handle(17), temporary = Handle(18) }
```

Runtime collection and CAS collection are deliberately separate:

| Plane | Root | Unit | Lifetime |
| --- | --- | --- | --- |
| runtime | stack/host invocation | handle/object | one run |
| persistent CAS | namespace, receipt, deployment and retained data roots | CID block | durable |

Releasing or closing a runtime table invalidates handles but never deletes CAS
blocks. Persistent deletion requires a separate reachability policy and must
not be triggered by local handle lifetime.

## Authority boundary

Possessing `ValueCID(x)` proves which bytes are requested. Possessing a Handle
proves only that the current table issued a local reference. Neither authorizes
I/O. Effects still require an explicitly admitted capability and runtime policy
intersection:

```text
read-object(GraphReadCap, ValueCID)
```

A capability must not be serialized by the general value codec merely because
it is represented by a host record. Capability/resource handles remain in
their affine authority tables; value handles remain freely shareable immutable
references. Mixing those tables would turn ordinary value copying into
authority duplication.

## Performance boundary

The semantic boundary computes and verifies CIDs. The hot execution boundary
uses handles. Implementations may cache decoded immutable objects and move them
under GC, but must preserve the table's handle validation and ValueCID mapping.
They must not expose host pointers to guest code.

The reference kernel currently decodes on `resolve` to make defensive byte
ownership undeniable. A backend may retain a proven-immutable decoded object,
but that optimization is conformant only if returned mutable byte storage
cannot mutate the interned value.

## aiueos arena increment

The first native stateful mechanism is
`aiueos-value-handle-arena`, compiled from Kotoba with no imports. One
process-owned RW/NX 4 KiB page contains a 64-byte versioned header and 63
physical slots. The physical live-set bound is deliberately smaller than the
4,096 logical-handle namespace: released slots may be recycled, while their
logical handle numbers never are. Every lookup compares the stored logical
handle, so reuse of storage cannot make a stale handle valid again.

The object acquires and releases its own u32 lock through
`kernel-compare-exchange-u32`. That intrinsic is independently admitted from
Sema through KIR and GMIR, allocated through MIR's five-register scalar action
contract, and emitted on x86 as a bounds-checked `LOCK CMPXCHG`. Invalid
base/length/index tuples trap before memory access. The arena stores only
opaque value/CID descriptor tokens; neither token is a pointer or capability.

This does not qualify the current C syscall dispatcher. Adding a call from
that dispatcher would reintroduce C ownership at exactly the boundary this ADR
excludes.

## aiueos normalized dispatcher increment

`aiueos-value-runtime-dispatch` is a closed six-module Kotoba project linking
the arena, SHA-256/digest verifier, provider transport, and typed dispatcher.
Its exact SysV export accepts the arena, a packed trusted profile, a fixed
96-byte normalized request, a bounded 4 KiB
capability table, and the presented capability handle. The current domain is
provided only by the trusted profile; the request cannot assert its caller.
Bytes 24..55 carry the request-originated expected SHA-256 digest; bytes
56..95 are canonical zero.

Before persistence routing, Kotoba rederives the capability slot, generation,
type, active bit, rights, owner, and complete handle from the table entry.
`intern` requires write right 4 and enqueues compiler wire 15;
`hydrate` requires read right 1 and enqueues wire 14. Both return a bounded
queue ticket rather than treating the route number as a result. Local `resolve`,
`cid-of`, and `release` require no capability and enter the linked arena.
User phase 1 is always rejected; only the separate trusted provider completion
export may install a positive opaque value/CID descriptor. The linked object
has no imports and contains bounded atomic compare-exchange.

The dispatcher increment deliberately starts after request normalization and
ends at a provider queue ticket. The entry increment below closes the bounded
copy/zero semantics. The existing C dispatcher remains unchanged and cannot
qualify this path.

## aiueos entry normalization increment

`aiueos-value-runtime-entry` links the arena, digest verifier, transport, and
dispatcher as a closed seven-module Kotoba object. Its five-argument native ABI receives a trusted
entry profile, the current process's private 4 KiB page, a kernel-owned 96-byte
scratch request, the capability table, and the process arena. The profile fixes
the raw envelope length at 104 bytes and carries the trusted current domain and
an offset no greater than 3,992.

Kotoba bounds the 104-byte user subregion before reading it. Bytes 0..23 are
the operation header, bytes 24..55 are the expected SHA-256 digest, bytes
56..63 are decoded only as the separately presented capability handle, and raw
bytes 64..103 must be zero. The kernel scratch copies bytes 0..55 and zeros
bytes 56..95, so the capability word and untrusted padding can never enter the
canonical dispatcher request.
The linked exact export has no imports and includes bounded loads, stores,
subregion admission, capability checking, and the arena atomic operation.

The live `syscall` entry now supplies the trusted profile and kernel-owned
addresses directly. Provider routes 14/15 cross the sealed queue described
below; no ValueRuntime call or identity rule was added to `kernel/syscall.c`.

## C-free kernel image integration

`aiueos.native.value-runtime-kernel` links the boot kernel, arena, dispatcher,
and normalized entry into one closed Kotoba project. Its emitted ELF64 image
retains `aiueos_kernel_entry`, contains the ValueRuntime entry export and all
bounded/atomic operations, and has no imports or foreign-code receipt. This
establishes production ownership in the pure Kotoba kernel image rather than
the C-based QEMU kernel fixture.

The C fixture is not deleted yet: it still supplies existing scheduler,
capability lifecycle, storage, and QEMU regression evidence that the pure
kernel has not replaced. Its syscall-5 branch is not reused or counted as
ValueRuntime qualification. C files become removable only as those mechanisms
land in the pure image; deleting them earlier would remove live regression
coverage without creating a C-free replacement.

`aiueos-value-runtime-syscall-plan` is the decision core for the remaining
architectural shim. Before user memory or return state is trusted, it requires
syscall number 5, the scheduler-published domain, a canonical low-half pointer
whose complete 104-byte envelope remains within one page, and canonical
low-half RIP/RSP. It returns exactly the entry profile consumed by the bounded
normalizer. Eight positive/negative vectors and KIR execution agree with an
independent model. The remaining prerequisite is the live syscall entry/return
shim that consumes this decision before enabling `sysretq` in the pure image.

The pure x86 kernel image now owns its boot stack and privilege descriptors.
Its RW segment contains a closed 56-byte GDT, 104-byte TSS, and a page-aligned
64 KiB kernel stack. The entry shim switches stacks, executes `lgdt`, reloads
CS through `lretq` and DS/ES/SS/FS/GS explicitly, loads selector 0x28 with
`ltr`, and only then calls Kotoba `main`; TSS.RSP0 names the same stack top.
The local-first ELF/UEFI image passed the complete OVMF QEMU handoff with marker
`M` and debug-exit status 33.

Scheduler domain publication is now also owned by Kotoba. The kernel-only
`kernel-publish-current-domain` intrinsic stores a checked domain scalar at
private context offset `0x110`; source code receives neither the context
pointer nor a general unchecked store. `aiueos.value-runtime-domain` admits
only domains 1..32767, exports the exact native publication boundary, and is
linked into the same import-free image. This is authority identity, not a CID,
Handle, or physical address. The future SYSCALL shim reads the published scalar
to select domain-owned runtime state; it does not treat it as a pointer.

The x86 packager now emits that live boundary only when the sealed artifact
exports both `aiueos-value-runtime-syscall-plan` and
`aiueos-value-runtime-entry` at their exact five-word ABIs. Boot enables
IA32_EFER.SCE and installs STAR/LSTAR/FMASK. LSTAR first saves the user
RSP/RIP/RFLAGS/request pointer in image-private context, switches to TSS.RSP0,
and calls the compiled Kotoba planner with syscall number, published domain,
pointer, RIP, and RSP. A zero plan skips the entry. An admitted plan supplies
the image-owned 96-byte scratch region, 4 KiB capability table, and 4 KiB
Handle arena to the compiled normalized entry. Return whitelists user arithmetic
flags and IF, clears privileged RFLAGS state, restores the admitted RIP/RSP,
and executes `sysretq`. The two rel32 call targets are independently decoded
from the packaged ELF and compared with the compiler's exact export offsets.
The then-seven-module ELF/embedded-UEFI image also passed OVMF/QEMU after
installing those MSRs (`M`, debug-exit status 33). This proves the live LSTAR
configuration does not fault on the exercised x86-64 model; it is not counted
as a CPL3 syscall semantic vector because `main` exits before launching a user
request.

This completes machine wiring and the request-bound integrity gate. The image
currently contains one active runtime cell; multi-domain context-switch
rebinding, canonical value/descriptor binding, and positive/negative CPL3 QEMU
semantics remain VR5c evidence. Consequently the existing C regression kernel
is still not obsolete.

Capability population is now a C-free Kotoba mechanism rather than an
uninitialized reservation. `kernel-value-runtime-capability-table` derives the
fixed `context+0x1000` table address inside the compiler backend and accepts no
pointer operand. `aiueos.value-runtime-capability-table` issues and revokes
only slots 1..255, derives generations monotonically with wrap to 1, fixes the
type to ValueRuntime type 2, limits rights to 1..7 and owners to domain
1..32767, and returns the exact generation/type/rights handle. Slot zero is a
table-wide atomic lock shared by mutation and dispatcher admission. Issue
clears active first and publishes active last; revoke clears active first.
This prevents a concurrent reader from admitting a stale generation through a
torn reissue. Eight independent lifecycle/lock/invalid-rights model steps and
the exact native table-address/LOCK CMPXCHG encodings pass.

The raw mutator is not exported by the integrated kernel image. Its public
boundary is `aiueos.value-runtime-provider-policy`: only rights 1 (hydrate),
4 (intern), or their attenuation-preserving union 5 are grantable. A private
provider status at context offset `0x138` carries active plus a nonzero
generation. Every issued table record binds that generation; dispatcher
admission re-reads current status, so stop or restart invalidates all old
grants without a racy sweep. The independent policy model covers inactive
provider, unknown rights, and all three admitted grants.

This policy allocates authority metadata. Provider I/O crosses an image-private
512-byte bounded queue at context offset `0x400`: seven 64-byte slots carry
ticket, route, trusted domain, capability handle, and the request-originated
32-byte expected digest. Publication is under
an atomic queue lock with the state byte written last. The user syscall may
only submit. The trusted provider atomically claims a pending request through
`aiueos-value-runtime-provider-claim`, which returns packed ticket/route/domain
metadata and advances pending to claimed. Completion independently re-matches
ticket, route, and domain, hashes the returned 1..12,288-byte block in the
image-private 384-byte CAS scratch region at context offset `0x600`, compares
it with the retained digest, and only then installs into the image-private
arena at `0x2000`.
A mismatch leaves the claimed ticket live, and queue exhaustion fails closed.
Tickets never wrap or reuse; exhaustion at `0xffffffff` fails closed. The
independent transport model covers twelve lifecycle, digest, mismatch, contention,
invalid-input, and exhaustion cases. The closed kernel image now contains ten
Kotoba modules before CAS verification. Linking ValueRuntime-owned copies of
the already qualified bounded SHA-256 and fixed-work digest comparison with
`aiueos-value-runtime-cas-verify` produces a
thirteen-module image of 160,024 bytes with no imports or foreign code. The CAS
primitive accepts 1..12,288 bytes and compares the derived SHA-256 against the
32-byte digest from the fixed CIDv1(dag-cbor, sha2-256) envelope. Six
independent vectors cover the standard `abc` digest, mismatch, malformed digest
length, empty input, the exact 12 KiB bound, and oversize rejection.

The 104-byte syscall envelope now carries the immutable expected digest through
entry normalization and the sealed queue. Provider completion cannot install a
Handle until the returned block hashes to that digest. Thus transport integrity
is request-bound rather than provider-asserted.

The completion boundary still accepts an opaque descriptor from the trusted
provider after block verification. It does not yet parse the verified block as
canonical `kotoba.value.v1` bytes or prove that the descriptor denotes that
decoded value. That canonical value/descriptor binding, plus end-to-end CPL3
semantic vectors, remains VR5c.

## 2026-08-12 implementation closure

This session closes VR5b2b2e at the following exact boundary:

```text
104-byte user envelope
  -> copy header + request-originated digest into 96-byte kernel request
  -> capability admission
  -> retain digest in one of seven 64-byte sealed queue slots
  -> trusted provider returns block + descriptor
  -> SHA-256(block) == retained digest
  -> Handle install
```

The linked artifact is a 160,024-byte, thirteen-module pure-Kotoba ELF64 image
with no imports and an empty foreign-code receipt. Independent checks passed at
each compiler/runtime boundary: Sema 9 tests/38 assertions, GMIR 13/87, KIR
135/581, kotoba-native 146/2,099, verifier 44/254, Amu provenance/native target
53/298, and the native contract 3/28. Runtime models passed 6 CAS vectors, 12
provider-transport vectors, 15 dispatcher vectors, 7 entry vectors, and 8
syscall-admission vectors. The final kernel-image verifier reconstructed the
thirteen-module image, checked both live syscall call targets, and reported no
imports or foreign code.

No C implementation participates in this ValueRuntime path. The existing C
kernel fixture is retained only for scheduler, capability lifecycle, storage,
and QEMU regression coverage that the pure image has not yet replaced; it is
not qualification evidence for VR5b2b2e and is not currently removable without
losing that coverage. The next implementation session starts at canonical
`kotoba.value.v1` validation and verified-block-to-descriptor binding, followed
by positive and negative CPL3 syscall vectors. It must not reopen digest
transport or move ValueCID, Handle, or capability authority into C.

## Delivery

| Stage | Deliverable | Status |
| --- | --- | --- |
| VR0 | This decision and machine-readable contract | implemented |
| VR1 | `value-cid` and CID verification in the canonical codec | implemented |
| VR2 | bounded reference `CID ↔ Handle` table over verified CAS | implemented |
| VR3 | forged-handle, mutation, deduplication, exhaustion, non-reuse and lifetime tests | implemented |
| VR4a | Backend-neutral synchronous value host ABI and fail-closed dispatcher | implemented |
| VR4b | KIR `value-call` contract and typed core Wasm imports for the value host ABI | implemented |
| VR5a | C-free aiueos ownership/authority contract and native packaging fail-closed gate | implemented |
| VR5b1 | C-free Kotoba Handle state-transition planner, exact native object export, and semantic vectors | implemented |
| VR5b2a | Kotoba atomic compare-exchange plus stateful bounded process-owned Handle arena object | implemented |
| VR5b2b1 | C-free normalized Kotoba dispatcher, capability admission, arena binding, and provider route receipts | implemented |
| VR5b2b2a | C-free bounded user-envelope copy/zero normalization and exact entry object | implemented |
| VR5b2b2a.1 | ValueRuntime graph linked into a closed, import-free pure Kotoba kernel ELF image | implemented |
| VR5b2b2b.1 | Pure-Kotoba syscall/domain/window/RIP/RSP admission planner | implemented |
| VR5b2b2b.2 | Image-owned GDT, TSS/RSP0, 64 KiB kernel stack, and OVMF QEMU boot | implemented |
| VR5b2b2b.3 | Checked scheduler-domain publication into private kernel context | implemented |
| VR5b2b2b.4 | Atomic Kotoba-owned ValueRuntime capability issue/revoke table | implemented |
| VR5b2b2b.5 | Provider-generation-bound, attenuated scheduler grant policy | implemented |
| VR5b2b2b | Live syscall machine wiring to the Kotoba planner and entry objects | implemented |
| VR5b2b2c | Sealed bounded provider queue and matched completion for routes 14/15 | implemented |
| VR5b2b2d | C-free bounded SHA-256 CAS digest verification primitive in the closed kernel image | implemented |
| VR5b2b2e | Request-originated digest carried through entry/queue and required by provider completion before Handle installation | implemented |
| VR5c | canonical value/descriptor binding and positive/negative CPL3 QEMU semantic vectors | pending |
| VR6 | independent runtime-GC and CAS-reachability qualification | pending |

The implemented claim is intentionally precise: Kotoba now has a working
host-side bridge from immutable ValueCID to a bounded local Handle. It does
not yet claim that every Wasm/native value automatically crosses that bridge,
nor general tracing GC, nor that every expression node is persisted.

## Rejected alternatives

- **CID as a CPU/RAM address** — rejected: resolution and large-key comparison
  would enter the hot path and GC could not freely move objects.
- **Hash every runtime temporary automatically** — rejected: semantic identity
  is useful at persistence, transport, memoization and receipt boundaries, not
  at every arithmetic step.
- **One handle table for values and capabilities** — rejected: immutable value
  aliasing is safe; authority duplication is not.
- **Delete CAS blocks when a handle dies** — rejected: another process,
  namespace, receipt, or deployment may still root the same CID.
