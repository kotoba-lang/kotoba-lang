# Kotoba engineering maturity — XMILE calculation

This calculation uses the OASIS XMILE stock/flow engine maintained in
`kotoba-lang/org-oasis-open-xmile`. Run it with:

```sh
clojure -M:maturity
clojure -M:maturity --check
```

The evidence snapshot is
`docs/system-dynamics/kotoba-lang-maturity-evidence.edn`; the checked result is
`docs/system-dynamics/kotoba-lang-maturity-results.edn`.

## Result

The central engineering-readiness estimate is **0.8417**. Sensitivity to the
CI reliability adjustment time is **0.8106–0.8647**. The static repository
score alone is **0.9138**, but that number does not represent operational
history.

The dynamic part treats reliability as a stock. Each completed main-branch CI
run moves the relevant repository's reliability toward 1 for success or 0 for
failure:

```text
Reliability(t + 1) = Reliability(t)
                   + (CIOutcome(t) - Reliability(t)) / tau
```

The observed language sequence was 78 failures followed by 22 successes in the
latest 100 completed main-branch runs. The new release and IPFS adapter repos
each had one successful run. With prior reliability 0.5 and central `tau=10`,
the resulting reliability stocks are:

| Stock | Value |
|---|---:|
| language reliability | 0.9015 |
| release reliability | 0.5500 |
| IPFS adapter reliability | 0.5500 |
| geometric system reliability | 0.6485 |

The two new repositories are therefore the present evidence bottleneck: one
successful run raises confidence, but does not justify treating them as mature
at 1.0. Overall maturity is a weighted geometric aggregation of repository
maturity, system reliability, assertion evidence, and dependency-boundary
conformance. The geometric form prevents a strong static code score from
hiding a weak operational stock.

This is an engineering-readiness calculation, not a claim about user adoption,
economic sustainability, or production SLO attainment. Those require separate
observations before they can enter the model.
