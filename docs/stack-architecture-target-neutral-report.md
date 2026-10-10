# Target-neutral distributed stack design report

2026-10-10 · Status: accepted architecture direction; coordinated owner documentation integration. No runtime migration.

The [Japanese architecture direction](stack-architecture-target-neutral.ja.md)
and [EDN model](../lang/stack-architecture-target-neutral.edn) separate shared
semantic contracts from physical target ABIs, and compilation targets from
distributed consistency profiles. Current accepted owner contracts and the
dated manifest observation remain in [the composition spec](../lang/stack-architecture.edn).

The guide contains three diagrams: responsibility/composition, intended
contract dependencies, and distributed action/data flow. It covers language,
AMU, target backends, Kototama, grant/authority, hosted providers/AiueOS,
Codebase/CLI, Kotobase, protocol/network, inga, placement and proposed conductor
composition. It is a major-boundary design, not an inventory of every package.

Thirteen fetched owner main revisions are recorded in the EDN model. The abi
execution identity currently requires Component/WIT-related fields, so a
universal descriptor cannot be obtained by relabelling that schema. The
bounded EVM compiler target exists; its narrow admitted surface is preserved
in the guide. External settlement remains a separate adapter boundary.
Holochain/IPFS/Ethereum and Component Model primary documentation supports the
reference distinctions; the resulting Kotoba composition is the adopted direction.

Existing architecture, authority routing, refactor procedure and Japanese
presentation link to the adopted direction. Owner runtime implementations and wire schemas are unchanged. Language and
Kototama presentation sources/generated pages are refreshed; integration and
pin receipts are recorded separately below.

Verification:

- Sixteen EDN composition/observation documents parse. The intended contract graph has 22 edges,
  is acyclic and contains no listed forbidden direct edge.
- Twenty Mermaid diagrams parse, including owner guides and measured dependencies.
- The existing generated documentation check passes: five files, 49 entries.
- Forty-two relative Markdown links resolve; all fourteen owner diffs pass whitespace checks.
- The aggregate docs checker passes: 46 documents, five routes and 16 authorities.
  The SCI documentation check used the pinned cached text source with a temporary
  dependency-free nbb paths config; the committed dependency config was restored.
- Thirteen current owner manifests are parsed; 19 selected direct edges and alias
  extra/replace coordinates are recorded separately.
- Public presentation regeneration and locale checks pass: 20,743 assertions.
  Kototama browser presentation is regenerated; script behavior is unchanged.
- Root topology ADR passes the current owner Kotoba ADL decoder and value-type checks.
  An existing cached compatibility I/O assembly is loaded but not invoked by
  this pure decode check; this is document validation, not Q9 acceptance.
- Regenerated HTML keeps script blocks unchanged: 123 language pages and two
  Kototama browser pages checked.

No target artifacts, consensus network, effects, live service or physical
machine were qualified by these documentation checks. Formal schema migration,
consumer closure inventory and execution/refusal qualification
remain the next implementation work.
