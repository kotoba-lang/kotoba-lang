# FS2 native filesystem protocol candidate

Status: proposed source contract, not an admitted/qualified provider or enabled
CDCI gate. Tracking: JUN-241 / JUN-201 / JUN-202, Kotoba Labs.

Existing :fs/browse wire 34 and :fs/app-data wire 35 remain distinct narrowed
authorities with string requests/results. Wire 36 remains :fs/browse-dir and
is not reassigned. FS2 adds unambiguous path/answer framing; it changes no
grant shape, C1 provider number, native handler id, syscall authority or limit.

## Byte grammar

The marker is the five bytes 0,70,83,50,0 (`NUL FS2 NUL`).

```
request  = marker + form-letter + path + NUL + tail
response = marker + operation-answer
```

Paths are nonempty valid UTF-8 native paths without NUL. TAB, LF, Unicode
and former `*_SEP` tokens are data, with no ASCII-only path restriction.
The first NUL after the letter uniquely bounds the path. Tail is the entire
remaining span. This is a text capability, not arbitrary raw-byte I/O.

| Letter | Existing app-data form | Tail |
| --- | --- | --- |
| A | 0 read | empty |
| B | 1 write | content |
| C | 2 append | content |
| D | 3 exists | empty |
| E | 4 range | decimal offset:length |
| F | 5 stat | empty |
| G | 6 mtime | empty |
| H | 7 chmod | octal mode |
| I | 8 mkdir | empty |
| J | 9 unlink | empty |
| K | 10 rmdir | empty |
| L | 11 rename | destination path |
| M | 12 fsync | empty |
| N | 13 make-temp-dir | prefix |
| O | 14 no-follow stat | empty |
| P | 15 owner | empty |

Each app-data unwrapped operation answer retains its existing meaning. The
owning sixteen-form implementation still validates every tail and bound.
Write/append text may contain NUL. Rename paths and temporary prefixes retain
their native path/prefix restrictions.

Browse requires A and an empty tail. Its operation answer is zero or more
`name NUL ASCII-0/1 NUL` records in stable unsigned-byte name order. The flag
retains DT_DIR=1, otherwise zero; it is not no-follow type proof. Dot/dot-dot
are omitted. Names are neither trimmed nor newline-split or normalized.

Every response, including absence and an empty listing, must carry the
marker. Canonical callers require acknowledgement and refuse an old provider's
empty answer. Invalid initial NUL/header/form/terminator/tail refuses before
filesystem dispatch. Version sniffing cannot silently fall back to a legacy
provider. Historical raw/token decoders remain temporary native migration
inventory until their callers are ported and the decoders are retired.

## Authority and acceptance

The full existing app-data confinement contract remains mandatory: narrowed
scope, no symlink traversal, proved descriptor ownership, and absence without
existence leakage outside scope. Framing does not relax or establish those
properties. Provider/authority/resource failures cannot become successful
empty responses. The native candidate now distinguishes C1 scope denial from
invalid state and applies owning decision op 48 before target filesystem
access. Both rename operands are checked before opening either parent.

An out-of-scope path answers `0` for boolean forms 3/7/8/9/10/11/12 and empty
text for data forms 0/1/2/4/5/6/13/14/15. Unknown forms and genuine request,
provider or resource failures remain terminal. Every FS2 absence answer still
carries the acknowledgement and uses real result budgets. This describes all
sixteen forms; it does not qualify actual confinement or no-leak behavior.
The complete path span, including a rename destination, rejects NUL before
normalization; ordinary valid path bytes and content tails are preserved.
The exact matching Amu-produced sealed handler artifact must include op 48;
an older artifact's unknown-op answer refuses rather than falling back.

Native also carries the admitted root length to the updated C1 op 8 so scope
`/` includes its descendants. The public decision arity stays unchanged;
exact matching C1 and handler artifacts are both required. Shared opens and
parent opens request BENEATH/NO_MAGICLINKS/NO_SYMLINKS, enforcing the declared
all-component no-symlink rule in the mechanism. Final-link metadata does not
traverse the link. Actual kernel/descriptor/race behavior is still unqualified.

The native path API performs lexical normalization before scope admission
and lookup. No-follow applies to that resulting path; raw POSIX traversal of
discarded components is not an added port requirement. Absent/false scope
uses a filesystem anchor at `/` while preserving inherited spawn cwd.
Direct children of `/` use the root parent; attempts to mutate the granted
root through its external parent answer negatively without opening it.
Exact matching handler source must also include these op 15/19 rules; public
decision arities/IDs, capability data and profile authority remain unchanged.

All frame/copy/answer bytes and result cells consume real checked budgets.
Actual source spans, path/root grants, complete C1/decision artifacts, native
syscalls and result UTF-8/handle checks remain required. Operations are not
transactional if a later failure occurs.

Source candidates: native #205 supplies the both-ISA mechanisms and Amu #1440
supplies the codec/capability boundary and all six fs-tree APIs. Required
follow-up includes full resolver/materializer/authoring callers, legacy
decoder retirement, raw-byte digest, runtime helpers/ABI, native admission,
actual filesystem/refusal/resource/race behavior, owning main/pin integration
and full Amu self-rebuild. No C-loader pin, CLJK sugar, KBB-first or hosted
oracle qualifies this protocol. No implementation/test/build/runtime or
service/production operation was run for this proposal.
