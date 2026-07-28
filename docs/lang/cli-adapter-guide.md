# CLI adapter guide (T9.1)

**Contract:** [`lang/cli.edn`](../../lang/cli.edn)  
**Matrix:** [`lang/cli-adapter-matrix.edn`](../../lang/cli-adapter-matrix.edn)

```bash
# structural contract (8 commands)
bb scripts/check-cli-contract.bb lang/cli.edn
# adapter matrix vs contract
clojure -M:cli-adapter-matrix
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

## Compiler extras (not in 8-id set)

| Command | Invoke |
|---|---|
| test | `clojure -M:run test file.kotoba` |
| fuel-estimate | `clojure -M:fuel-estimate file.kotoba` |
| sign / verify / receipt / … | `clojure -M:run <cmd>` |

## Closing M1→M2 for a command

1. Adapter implements contract options or documents intentional subset.  
2. Positive + negative fixtures.  
3. Matrix `:status :implemented` + evidence PR/ADR.  
4. Bump `:tier :m2` in `cli.edn` only when validated.
