# Capability Value Semantics

Status: proposed profile extension
Date: 2026-07-01

Safe Kotoba already rejects ambient host authority and checks literal resource
ids against policy. Capability values are the profile-level contract for dynamic
resources: a program should pass an explicit scoped authority object, not a
plain string that later becomes authority.

## Principle

```text
resource string != capability
```

A resource id names a graph, model, egress origin, secret, or host surface. A
capability value authorizes a specific action on a constrained resource set.

## Core Capability Values

| Capability value | Actions | Resource constraint |
|---|---|---|
| `GraphReadCap` | graph read/query | graph CID or graph set |
| `GraphWriteCap` | graph assert/retract | graph CID or graph set |
| `InferCap` | model inference | model CID or model set |
| `EgressCap` | outbound request | origin/method/path constraints |
| `SecretReadCap` | secret unwrap/read | secret id, purpose, expiry |
| `ClockCap` | time access | monotonic/wall-clock mode |
| `RandomCap` | entropy access | deterministic/security entropy class |

The concrete ABI representation is implementation-owned, but the profile
requires these semantic fields:

- capability class;
- action set;
- resource constraint;
- issuer/delegation reference where applicable;
- expiry or epoch where applicable;
- policy/grant binding id;
- receipt id after use.

## Effect Consistency

If a function accepts a capability value, its effect row must include the effect
enabled by that capability. For example:

```clojure
(defn write-note
  {:effects #{:graph-write}}
  [^GraphWriteCap cap note]
  (graph-assert! cap note))
```

The profile rule is:

```text
capability parameter class => required effect
```

An implementation may infer this effect, but it must not silently treat the
capability as pure data when invoking host authority.

## Dynamic Resource Rule

Dynamic resource access should be expressed as capability passing:

```clojure
;; preferred
(defn writer [cap obj]
  (graph-assert! cap obj))

;; not authority by itself
(defn writer [graph-cid obj]
  (graph-assert-by-id! graph-cid obj))
```

The second form can remain as a compatibility surface, but it must be checked by
runtime policy and will often require broader grants. The first form lets
least-privilege policy generation avoid wildcard resource grants.

## Runtime Intersection

Using a capability value requires runtime intersection:

```text
effective capability =
  capability value
  intersect external delegation
  intersect local policy
  intersect component manifest
  intersect package lock
  intersect surface policy
  intersect runtime limits
```

The host call must fail closed if the intersection is empty.

## Receipt Requirement

Every host call using a capability value must emit or link to a receipt
containing:

- component id;
- package/component CID when available;
- capability class;
- concrete resource/action;
- delegation/grant id;
- policy id;
- result: grant, denial, or trap.

## Conformance Direction

The conformance suite should cover:

- a string resource id is not accepted where a capability value is required;
- a capability value carries the effect required by its class;
- a dynamic graph/model call can avoid wildcard policy when capability-valued;
- receipts include concrete capability information.

## Capability Value Contract

The machine-checkable form of this document is
`src/kotoba/lang/capability_values.cljc` (`kotoba.lang.capability-values`):
first-class `GraphReadCap` / `GraphWriteCap` / `InferCap` values
(`graph-read-cap`, `graph-write-cap`, `infer-cap`, `capability?`,
`validate-cap`), effect-row consistency (`effect-for-kind`,
`effects-consistent?`), host-call-time CACAO grant / local policy
intersection producing the concrete authorized capability or a fail-closed
denial (`intersect-grants`), and receipts embedding the concrete — never the
broader requested — capability (`receipt`, `validate-receipt`). Conformance
fixtures live under `lang/capability-conformance/` (positive/negative cases
listed in `lang/capability-conformance/manifest.edn`); they are exercised by
`test/kotoba/lang/capability_values_test.clj` and by the gate
`bb scripts/check-capability-values.bb`, which loads the same CLJC source.

