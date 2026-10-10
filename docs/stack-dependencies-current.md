# Current selected owner dependencies

Measured 2026-10-10 from 17 immutable owner source revisions; each revision is recorded in the [observation spec](../lang/stack-dependency-observation.edn). Arrows mean consumer → dependency. All coordinates and alias extra/replace/override descriptors are recorded. This selected graph is not a transitive lock or runtime qualification.

Production dependencies (31 selected direct edges):

```mermaid
flowchart LR
  abi["abi"]
  aiueos["aiueos"]
  amu["amu"]
  authority["authority"]
  code_graph["code-graph"]
  grant["grant"]
  inga["inga"]
  kotoba["kotoba"]
  kotoba_component["kotoba-component"]
  kotoba_core_contracts["kotoba-core-contracts"]
  kotoba_lang["kotoba-lang"]
  kotoba_protocol["kotoba-protocol"]
  kotobase["kotobase"]
  kototama["kototama"]
  kototama_component["kototama-component"]
  murakumo["murakumo"]
  sahai["sahai"]
  abi --> kotoba_core_contracts
  aiueos --> grant
  amu --> abi
  amu --> kotoba_component
  amu --> kotoba_core_contracts
  code_graph --> kotoba_core_contracts
  code_graph --> abi
  code_graph --> kotobase
  grant --> abi
  grant --> kotoba_core_contracts
  grant --> authority
  kotoba --> kotoba_lang
  kotoba --> kototama
  kotoba --> amu
  kotoba --> kotoba_core_contracts
  kotoba_component --> abi
  kotoba_lang --> inga
  kotoba_lang --> grant
  kotoba_lang --> kotoba_core_contracts
  kotobase --> abi
  kotobase --> grant
  kototama --> abi
  kototama --> grant
  kototama --> kotoba_core_contracts
  kototama --> authority
  kototama_component --> abi
  kototama_component --> kototama
  murakumo --> abi
  murakumo --> grant
  murakumo --> aiueos
  sahai --> kototama
```

Build/test/integration aliases are a separate graph:

```mermaid
flowchart LR
  abi["abi"]
  aiueos["aiueos"]
  amu["amu"]
  authority["authority"]
  code_graph["code-graph"]
  grant["grant"]
  inga["inga"]
  kotoba["kotoba"]
  kotoba_component["kotoba-component"]
  kotoba_core_contracts["kotoba-core-contracts"]
  kotoba_lang["kotoba-lang"]
  kotoba_protocol["kotoba-protocol"]
  kotobase["kotobase"]
  kototama["kototama"]
  kototama_component["kototama-component"]
  murakumo["murakumo"]
  sahai["sahai"]
  aiueos -.->|"ci:extra"| amu
  aiueos -.->|"test:extra"| amu
  aiueos -.->|"test-fleet:extra"| amu
  aiueos -.->|"verify-admissions:extra"| amu
  inga -.->|"test:extra"| amu
  kotoba -.->|"dev:override"| kotoba_lang
  kotoba -.->|"dev:override"| amu
  kotoba -.->|"dev:override"| kotoba_core_contracts
  kotoba_lang -.->|"pure-product-examples:extra"| amu
  kotobase -.->|"integration:extra"| kotoba
  kototama -.->|"component-host:extra"| kototama_component
  kototama -.->|"postgresql-interop:extra"| kotoba
  kototama -.->|"postgresql-interop:extra"| amu
  kototama -.->|"postgresql-interop:extra"| kotoba_core_contracts
  kototama -.->|"postgresql-interop:extra"| kotoba_lang
  kototama -.->|"test:extra"| kotoba
  kototama -.->|"test:extra"| amu
  kototama -.->|"test:extra"| kotoba_core_contracts
  kototama -.->|"test:extra"| kotoba_lang
  kototama -.->|"test:override"| kotoba_lang
  kototama -.->|"test:override"| kotoba_core_contracts
  kototama_component -.->|"test:extra"| amu
  kototama_component -.->|"test:extra"| abi
  murakumo -.->|"test:override"| abi
```

Repo-level edges include multiple entrypoints. The neutral `kotoba.core.execution` namespace has no imports, while other core-contracts codec/crypto entrypoints have their own dependencies. `abi.component` imports that neutral namespace; the legacy facade imports both. The language's existing grant/osaho coupling remains visible. Alias cycles do not redefine the acyclic intended contract DAG. Compiler backends and IR libraries outside this selection remain in the complete coordinate records.

See [the implemented API split](stack-architecture-target-neutral-report.md) and [the intended contract architecture](stack-architecture-target-neutral.ja.md).
