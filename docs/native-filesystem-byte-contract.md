# Raw FS2 contract proposal

This refines the existing proposed `:fs/app-data :answer-kinds :bytes`
metadata. Status remains `:proposed`. No capability wire, effect, grant,
confinement rule, existing form ID or string/string contract changes.
Proposed kind 11 adds FS2-only stage-member (16/Q), begin-output-set (17/R)
and discard-output-set (18/S) extensions;
the original sixteen forms remain. No hosted token or kind-1 route is added.
Historical provider sources/measurements do not qualify the native proposal.

| Contract | Request | Result | Discriminator | Forms |
| --- | --- | --- | --- | --- |
| `bytes-v1` | `:bytes` | `:bytes` | 8 | write 1, append 2 |
| `string-bytes-v1` | `:string` | `:bytes` | 9 | read 0, range 4 |
| `string-byte-chunks-v1` | `:string` | `[:list :bytes]` | 10 | read 0, range 4 |
| `byte-chunks-v1` | `[:list :bytes]` | `[:list :bytes]` | 11 | write 1, append 2, stage-member 16, begin 17, discard 18 |

Both ABI metadata words carry the same whole-contract discriminator. Kind 9
does not mean bytes/string. The compiler-port draft's numeric names are
proposal data until the owning canonical GMIR contract and full lowering
are admitted; GMIR main still stops at kind 7.

## Framing and boundaries

These contracts use the existing wire 35 / provider 5 / handler 7. Requests
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

Contract 11 validates the full outer list, all inner byte windows/words and
aggregate size before pool allocation. It concatenates one bounded raw
request span under real run budgets, then the original complete WRITE/APPEND
keeps one opened descriptor for the entire content. Only the path is UTF8
checked. No per-chunk reopen/truncation or aggregate value-size reduction is
introduced. Paired source now includes all seven IO names and ordered output
publication; caller/type adaptation and native terminal-trap cleanup remain
explicitly unqualified.

Stage-member tail is 1..6 octal mode digits, NUL, then arbitrary full payload.
After complete request validation and scope admission it exclusively creates
an empty no-follow 0600 file, applies permission bits with fchmod before any
data, writes all bytes, fsyncs and checks close through ONE descriptor. Thus
mode 0000/0400 does not require reopening a now inaccessible file. Success
is acknowledged payload "1" only after all steps; denied scope answers "0".
It is proposed only under kind 11; all other contracts reject form 16 before
path access. Existing WRITE/APPEND echo semantics are unchanged. Detailed
native stage-failure diagnostics and terminal cleanup remain unqualified.

Begin/discard have empty tails. Begin uses the original private-directory
creation policy with a fixed .kotoba-output- prefix; it returns an
acknowledged absolute staging path or an acknowledged empty absence/failure.
Discard matches the exact owned staging path and returns acknowledged "1"
only on complete removal, "0" on incomplete removal. Only kind 11 admits
these forms. No kind-1/legacy-token extension or new capability authority is
introduced.

Paired native source now retains parent/directory/current-member FDs,
journals successful mkdir/member-open before sealed outcome conversion,
and composes cleanup into normal/fatal/signal exits. The original fatal
diagnostic/exit is retained; incomplete cleanup is separately reported and
makes an otherwise successful exit nonzero. These are source facts only.
Name replacement races, mkdir/open journaling windows, signal interruption,
close failures and process death remain unqualified; an uncaptured created
directory can remain. This is not an all-crash removal guarantee.

Contract 10 returns the full response as ordered bounded byte chunks while
ONE read/range operation retains ONE opened descriptor. It does not reopen
the path per chunk, which could mix same-size files across a rename. The
aggregate uses actual run budgets; every element retains existing value
bounds. FS2 acknowledgement may span chunks and is still mandatory. Paired
Amu source now prepares a logical byte/text buffer and complete bounded input
unit for the original 1 MiB/8 MiB domain. Parent output source is now present;
caller/parser/hash integration and all native qualification remain open.

Complete canonical GMIR/signature/type/value lowering and both ISA encoding,
the Amu codec and full callers, native positive/refusal/authority/resource/
path/corpus evidence, exact artifacts/Definition CIDs, reviewed owning mains
and immutable locks, and actual whole self-rebuild remain required. Only
Amu selfhost / Kotoba-native gates and controller may qualify CDCI.

No compiler, generator, assembler, provider, artifact/native implementation
or tests were executed; no tests were added. This metadata does not activate
the profile, admit a kind or publish an artifact.
