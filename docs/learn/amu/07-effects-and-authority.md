# 07 Effects and authority

**English** | [日本語](07-effects-and-authority.ja.md)

Types describe values; effects describe what execution may do outside its pure computation. Amu's effect checks and the host's concrete authorization checks are both necessary.

## A refusal example

[negative-operation.kotoba](examples/negative-operation.kotoba) contains `(slurp "lesson.txt")`. The observed native CLI reports `E2101 unknown or unsupported form 'slurp'`. This is a compile-time refusal because the operation is outside this closed language, not an attempted file read rejected by the OS.

## A declaration is not permission

A checked definition that performs HTTP must include the operation in its effect row. Called functions' effects must propagate too; hiding an external operation in a helper cannot make it pure. Semantic effect names are separate from backend numeric wire IDs so an ABI renumbering need not change definition meaning.

Conceptually, invocation permission is the intersection of:

```text
Amu checked effects
∩ VM requested operations and resources
∩ capability-chain grants
∩ local policy permissions
∩ currently available runtime operations
```

Other checks include agreement between the definition world and runtime world. A missing condition denies access. Permission for one file must not become permission for its whole directory, and a previous invocation's grant must not silently authorize another.

The current Kototama admission core evaluates a closed Kotoba predicate. It does not execute user Datalog rules there. Biscuit is not the core grant representation either; an authentication-boundary adapter can transcode it into a capability chain where needed.

## Boundaries that support safety

The compiler checks types, effects, target support, and resource conditions. Grant and the local authorizer decide permission. Hosts and providers enforce concrete endpoints, paths, and quotas. Native memory protection and process isolation have their own responsibilities. A checked language does not eliminate host or provider bugs.

Browser apps must also obtain operation-specific user consent under their own contracts. A compiler's or bot's tool authority cannot manufacture consent for the user's service operation.

## Read the contracts and source

Read the [effect and capability contract](../../../lang/capability-semantics.edn), [elaboration pipeline](../../../lang/elaboration-pipeline.edn), and [Kototama admission](https://github.com/kotoba-lang/kototama/blob/b7bbcc44d08913fee69074ad70f92d341df2be92/src/kototama/admission.kotoba). The arguments and refusal ordering in `eligible?` and `admit` show which owner supplies each condition.

## Exercise and answer

Can an unchanged artifact and DefCID perform an operation after its grant expires? No. The content is unchanged, but the current execution conditions deny it. Immutable code can be cached; an old permission decision cannot simply be carried forward.

[Previous](06-definition-identity.md) · [Contents](README.md) · [Next: code generation](08-code-generation.md)
