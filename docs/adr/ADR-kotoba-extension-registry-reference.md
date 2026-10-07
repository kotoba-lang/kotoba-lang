# ADR: Reference Kotoba Registry for agent extensions

Status: accepted — 2026-10-07

## Decision

Use [`kotoba-lang/kotoba-registry`](https://github.com/kotoba-lang/kotoba-registry)
for published agent skills and MCP definitions. The
[generated catalog](https://raw.githubusercontent.com/kotoba-lang/kotoba-registry/main/index.json),
[agent entry point](https://raw.githubusercontent.com/kotoba-lang/kotoba-registry/main/llms.txt)
and [consumption contract](https://raw.githubusercontent.com/kotoba-lang/kotoba-registry/main/llms-full.txt)
are the common discovery references for Hermes, Claude and Codex.

README, the documentation map, agent instructions and site/assets/llms*.txt
link to that authority. The public site's LLM files are generated from these
source assets by the normal site build, never independently edited in dist.

Consumers read all entry files from one immutable registry commit and verify
the Hermes folder checksum before installation. Checksum verification establishes
byte consistency; the agent's own runtime authority determines permissions.

## Authority boundaries

The extension registry's SKILL.md and MCP manifest.json catalog is separate
from lang/package-registry.edn and the signed CID-pinned Kotoba package contract.
It does not claim Wasm execution, package admission or distributed availability.
The library discovery/publication contract remains in
[ADR-library-catalog-publication-boundary.md](ADR-library-catalog-publication-boundary.md).

For forest maintenance, `com-junkawasaki/west-manifest` owns the command,
installation and repository pins. The public registry publishes discoverable
definitions and their provenance. Existing clients need explicit configuration
to consume its catalog; adding a documentation link does not switch their
Discover implementation.
