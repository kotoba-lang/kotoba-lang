# ADR — kotoba-lab: research notebook app and library

- **Status**: Proposed
- **Date**: 2026-07-01
- **Artifacts**: planned app `kotoba-lab`, planned libraries
  `kotoba-lang/{lab,table,viz,artifact,stats}`
- **Related**: `ADR-kotoba-lang-profile.md`,
  `ADR-safe-capability-language.md`,
  `ADR-kotoba-lang-foundational-stdlib.md`

## Context

Researchers need a Jupyter-like workflow: load data, run exploratory code in
small cells, inspect tables and plots, keep notes close to results, and export
the work as a reproducible paper appendix or report.

Kotoba should not clone Jupyter at the process boundary. Python notebooks assume
an ambient kernel with broad filesystem/network/package access. Kotoba's
strength is different: small `.kotoba` cells compiled to Wasm, explicit
capability policy, reproducible builds, content-addressed artifacts, and a
Datomic/KQE-style fact substrate. The notebook design should turn those
properties into the researcher UX rather than hiding them.

The target user is a researcher or analyst who cares about:

- exploratory iteration with fast feedback;
- reproducible cell outputs;
- durable provenance for data, code, parameters, plots, and claims;
- safe execution of AI-generated or collaborator-provided cells;
- publication-grade export without losing execution evidence.

## Decision

Design **kotoba-lab** as two layers:

1. **`kotoba-lang/lab` library**: a portable `.cljc` model and API for
   notebooks, cells, runs, artifacts, datasets, kernels, and provenance.
2. **`kotoba-lab` app**: a local-first browser/desktop workbench using the
   library, Kotoba Wasm execution, and Kotoba storage.

The core unit is not "a mutable notebook document"; it is a **research run
ledger**:

```edn
{:lab/notebook-id "did:kotoba:lab:..."
 :lab/title "solar panel degradation study"
 :lab/cells [{:cell/id "c-001"
              :cell/kind :kotoba
              :cell/source-cid "bafy..."
              :cell/policy-cid "bafy..."
              :cell/depends-on []}
             {:cell/id "c-002"
              :cell/kind :markdown
              :cell/source-cid "bafy..."
              :cell/depends-on ["c-001"]}]
 :lab/runs [{:run/id "r-20260701-001"
             :run/cell-id "c-001"
             :run/input-cids ["bafy-data"]
             :run/wasm-cid "bafy-wasm"
             :run/output-cids ["bafy-table" "bafy-plot"]
             :run/evidence-cid "bafy-evidence"
             :run/status :succeeded}]}
```

Each executable cell is compiled through `kotoba wasm safe-build` semantics.
The app never gives a cell ambient filesystem, network, model, graph, or clock
access. All effects are host-injected capabilities and recorded in the run
ledger.

## Product Shape

`kotoba-lab` opens directly into the workbench, not a landing page.

Primary panes:

- **Notebook outline**: sections, cells, run status, dependency status.
- **Editor**: `.kotoba`, `.cljc`, Markdown, SQL/KQE query, or parameter cell.
- **Result view**: rich tables, plots, JSON/EDN, images, logs, and errors.
- **Data/artifact shelf**: datasets, derived tables, model outputs, figures,
  external references, and CIDs.
- **Evidence panel**: source CID, policy, capabilities used, wasm CID, inputs,
  outputs, timing, host version, and replay status.

The default workflow:

1. Import a dataset or bind an existing Kotoba graph.
2. Create cells that transform, query, model, or visualize data.
3. Run one cell, a section, or the whole dependency graph.
4. Persist every output as an artifact with content address and metadata.
5. Export as HTML, PDF, paper appendix, or a replay bundle.

## Cell Types

| Kind | Purpose | Execution |
|---|---|---|
| `:markdown` | notes, equations, claims, citations | no execution |
| `:kotoba` | safe computation and graph updates | Kotoba -> Wasm |
| `:query` | KQE/Datomic/SPARQL-style reads | host query capability |
| `:table` | table transform/view specification | pure library path first |
| `:viz` | plot declaration | pure spec + host renderer |
| `:param` | named parameters for batch/replay | immutable per run |
| `:python-bridge` | optional compatibility bridge | out of core; capability-gated |

Python compatibility is intentionally a bridge, not the foundation. A
`:python-bridge` cell may exist later for migration, but it must run in a
separate sandbox and emit Kotoba artifacts. The core reproducibility contract is
Kotoba cell -> Wasm -> recorded artifacts.

## Library API

The library is pure data first. Host-specific work is behind protocols.

```clojure
(ns kotoba.lab)

(defn notebook [attrs] ...)
(defn add-cell [notebook cell] ...)
(defn update-cell [notebook cell-id f] ...)
(defn plan-run [notebook selected-cell-ids opts] ...)
(defn record-run [notebook run-result] ...)
(defn stale-cells [notebook artifact-index] ...)
```

```clojure
(defprotocol ICellCompiler
  (compile-cell [compiler cell policy]))

(defprotocol ICellRuntime
  (run-cell [runtime compiled-cell inputs capabilities]))

(defprotocol IArtifactStore
  (put-artifact [store artifact bytes metadata])
  (get-artifact [store cid])
  (artifact-metadata [store cid]))

(defprotocol IRenderer
  (render-result [renderer artifact opts]))
```

`kotoba-lang/lab` owns the portable model. Other planned libraries supply the
scientific surface:

| Library | Responsibility |
|---|---|
| `kotoba-lang/table` | immutable column tables, schema, joins, group-by, CSV/Arrow-shaped metadata |
| `kotoba-lang/viz` | declarative plot specs, scales, facets, figure metadata |
| `kotoba-lang/artifact` | artifact manifests, CIDs, media types, provenance |
| `kotoba-lang/stats` | small deterministic stats functions and model summaries |
| `kotoba-lang/lab` | notebooks, cells, dependency graph, run ledger |

These libraries should follow the foundational stdlib rules: zero third-party
runtime deps, `.cljc`, host-injected I/O, capability-parameterized effects.

## Execution Model

Notebook execution is a DAG, not an ordered global kernel.

```text
source cell + policy + inputs
  -> compile/safe-build
  -> wasm cid
  -> capability-bound run
  -> artifacts
  -> datoms/provenance facts
  -> result renderers
```

Cell staleness is derived from content identity:

- source CID changed;
- policy CID changed;
- input artifact CID changed;
- compiler/runtime version changed;
- declared parameter value changed.

This avoids hidden mutable kernel state. A cell can still be interactive, but
the committed run is immutable and replayable.

## Data Model

Persist notebook state as Datoms plus artifact blocks.

Core entities:

- `:lab/notebook`
- `:lab/cell`
- `:lab/run`
- `:lab/artifact`
- `:lab/dataset`
- `:lab/claim`
- `:lab/citation`
- `:lab/export`

Important facts:

```edn
[:cell/id "c-001"]
[:cell/source-cid "bafy..."]
[:cell/policy-cid "bafy..."]
[:cell/depends-on "c-000"]
[:run/cell "c-001"]
[:run/wasm-cid "bafy..."]
[:run/input-cid "bafy..."]
[:run/output-cid "bafy..."]
[:run/capability-used :graph-read]
[:artifact/media-type "application/vnd.apache.arrow.file"]
[:artifact/derived-from "bafy-data"]
[:claim/supported-by-run "r-20260701-001"]
```

The app can render a notebook document from these facts, but the facts and
artifacts are the canonical storage.

## Capability Policy

Use deny-by-default policies per cell. Recommended capability classes:

| Capability | Example |
|---|---|
| `:graph-read` | query a named graph/dataset |
| `:graph-write` | write derived facts |
| `:artifact-read` | read a dataset/artifact CID |
| `:artifact-write` | write result artifacts |
| `:http-read` | fetch a declared URL/domain |
| `:llm-infer` | call a named model with budget |
| `:time-read` | read clock only when explicitly needed |
| `:random` | deterministic seed required for replay |

The UI should show the minimal inferred policy and make over-grants visible.
For normal research cells, the happy path is `safe-policy` -> user review ->
`safe-build` -> run.

## App Architecture

Recommended first implementation:

```text
kotoba-lab app
  browser UI (CLJS or web app)
  local lab host
    compiler adapter -> kotoba wasm safe-build
    runtime adapter  -> kotoba-runtime / wasm host
    artifact store   -> kotoba-store / OPFS / local fs
    fact store       -> kotoba-datomic / KQE
    renderer         -> table/viz/html export
```

Local-first is the default. Collaboration and remote execution are later
extensions via the same ledger and artifact model.

## CLI

Expose a CLI that mirrors the app:

```sh
kotoba lab new degradation-study
kotoba lab run notebook.kotoba-lab.edn
kotoba lab run notebook.kotoba-lab.edn --cell c-001
kotoba lab replay runs/r-20260701-001.edn
kotoba lab export notebook.kotoba-lab.edn --format html -o report.html
kotoba lab evidence notebook.kotoba-lab.edn --json
```

The app should call the same library/host APIs as the CLI, so replay and export
are not UI-only features.

## File Formats

Use EDN manifests plus content-addressed artifacts:

- `notebook.kotoba-lab.edn`: portable notebook manifest.
- `runs/*.edn`: run records and evidence summaries.
- `artifacts/`: optional local cache of CID-addressed blocks.
- `.kotoba` / `.cljc`: source cells when stored as files.

The manifest is an index, not the only source of truth. A synced workspace can
rebuild the manifest from Datoms and artifacts.

## Maturity Plan

- **M0**: this ADR; model agreed.
- **M1**: `kotoba-lang/lab` data contract and protocol definitions.
- **M2**: example notebook with Markdown, Kotoba cell, query cell, table
  artifact, and evidence output.
- **M3**: negative fixtures: denied filesystem/network/model access, stale cell
  detection, missing artifact, policy over-grant.
- **M4**: manifest-driven replay runner in CI.
- **M5**: `kotoba-lab` app consumes the library and runs a real local notebook.
- **M6**: compatibility/versioning policy for notebook manifests and artifact
  schemas.

## Non-goals

- Do not implement a general Python-compatible Jupyter kernel in core.
- Do not allow ambient package installation inside cells.
- Do not store canonical results only in browser local state.
- Do not make UI execution semantics differ from CLI replay semantics.
- Do not add new language safety gates; reuse the existing safe Kotoba policy
  and runtime boundaries.

## Open Questions

- Whether first app target should be pure browser/OPFS or local desktop with a
  companion host process.
- Whether `table` should standardize directly on Arrow-compatible binary
  artifacts or start with EDN/CSV plus schema metadata.
- How much plotting should be in pure `viz` data specs versus delegated to a
  host renderer.
- Whether collaborative notebooks use CRDT document state, Datom transactions,
  or both.
