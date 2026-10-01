# CLI adapter guide (T9.1)

**Contract:** [`lang/cli.edn`](../../lang/cli.edn)  
**Matrix:** [`lang/cli-adapter-matrix.edn`](../../lang/cli-adapter-matrix.edn)

Host adapter target: the Kotoba implementation on the nbb launcher / amu native route (`:host-adapter-targets [:amu-nbb :amu-native]` in `lang/cli.edn`). No Rust adapter exists or is required; `kotoba refactor` delegates to `amu refactor` only.

```bash
# structural contract (13 commands)
bb scripts/check-cli-contract.bb lang/cli.edn
# adapter matrix vs contract
kbb -M:cli-adapter-matrix
```

## Public commands

| Id | Tier | Compiler CLI | Notes |
|---|---|---|---|
| run | M1 | partial | signed kexe run; pure source path gaps |
| compile | M1 | implemented | multi-target |
| check | **M2** | implemented | `--profile pure-product`, human/`--json` |
| db | M1 | — | contract-only |
| git | M1 | — | contract-only |
| rad | M1 | — | contract-only |
| deploy | M1 | — | contract-only |
| hinshitsu | M1 | — | contract-only |
| refactor | M1 | contract-only | validates argv, delegates to `amu refactor` (nbb/Kotoba only); `:refactor/engine-absent` when the engine is missing |

## Compiler extras (not in 8-id set)

| Command | Invoke |
|---|---|
| test | `kbb -M:run test file.kotoba` |
| fuel-estimate | `kbb -M:fuel-estimate file.kotoba` |
| sign / verify / receipt / … | `kbb -M:run <cmd>` |

## Closing M1→M2 for a command

1. Adapter implements contract options or documents intentional subset.  
2. Positive + negative fixtures.  
3. Matrix `:status :implemented` + evidence PR/ADR.  
4. Bump `:tier :m2` in `cli.edn` only when validated.
