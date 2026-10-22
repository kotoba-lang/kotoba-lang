# Raw FS2 contract proposal

This refines the existing proposed `:fs/app-data :answer-kinds :bytes`
metadata. Status remains `:proposed`. No capability wire, effect, grant,
confinement rule, form ID or existing string/string contract changes.
Historical provider sources/measurements do not qualify the native proposal.

| Contract | Request | Result | Discriminator | Forms |
| --- | --- | --- | --- | --- |
| `bytes-v1` | `:bytes` | `:bytes` | 8 | write 1, append 2 |
| `string-bytes-v1` | `:string` | `:bytes` | 9 | read 0, range 4 |

Both ABI metadata words carry the same whole-contract discriminator. Kind 9
does not mean bytes/string. The compiler-port draft's numeric names are
proposal data until the owning canonical GMIR contract and full lowering
are admitted; GMIR main still stops at kind 7.

## Framing and boundaries

Both contracts use the existing wire 35 / provider 5 / handler 7. Requests
require FS2: five marker bytes 0,70,83,50,0, then one form byte, nonempty
UTF8 path, NUL and a tail. Read has no tail. Range uses offset:length text.
Write/append tail bytes are arbitrary, including NUL and malformed UTF8.
Only the path is text; raw payload does not pass through string decoding.

Every result begins with the five-byte acknowledgement, including an absent
or empty answer. Removing it yields raw bytes. Legacy token splitting or an
unacknowledged empty response is never a fallback for these contracts.
The existing grant/scope/no-follow/descriptor/resource refusals remain.

The native decision proposal limits kind 8 to write/append and kind 9 to
read/range before path access; unsupported kind/form combinations refuse.
All original sixteen kind-1 forms and all prior decision operations remain.
Exact updated Amu-produced C1 AND handler artifacts are required; an old
cache/seal or a C-loader/header-generator pin does not qualify this revision.

## Value bounds are not a file-size migration

Existing language bounds remain 65536 bytes for a bytes/string value. Framing
and the acknowledgement consume those bounds and real run budgets. No new
smaller limit is substituted for the compiler's existing 1 MiB source and
8 MiB artifact input domain.

The historical compiler IO component needs a complete chunked/streaming
representation and its caller/parser/hash/atomic-publication closure, or a
separately justified complete value-contract change. A single wire result
cannot be advertised as that full component. The same issue affects large
text values: decoding several chunks is not permission to concatenate an
out-of-domain string or silently shorten the source.

## Qualification still open

Complete canonical GMIR/signature/type/value lowering and both ISA encoding,
the Amu codec and full callers, native positive/refusal/authority/resource/
path/corpus evidence, exact artifacts/Definition CIDs, reviewed owning mains
and immutable locks, and actual whole self-rebuild remain required. Only
Amu selfhost / Kotoba-native gates and controller may qualify CDCI.

No compiler, generator, assembler, provider, artifact/native implementation
or tests were executed; no tests were added. This metadata does not activate
the profile, admit a kind or publish an artifact.
