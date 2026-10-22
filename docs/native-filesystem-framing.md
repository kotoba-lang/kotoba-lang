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
empty responses. The native candidate's generic non-EXISTS path-failure trap
still needs reconciliation with the outside-scope absence rule.

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
