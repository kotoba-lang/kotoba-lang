# Grade A malicious-source conformance corpus

`lang/malicious-source/manifest.edn` is the normative negative corpus for five
security classes:

- reader escape and unregistered tags;
- effect laundering;
- confused-deputy capability substitution;
- resource-limit escalation;
- parser exhaustion.

Every case has a stable reject code. Parser byte, nesting, and token limits are
computed before EDN allocation. Unknown attack classes fail closed. The corpus
is data-driven so compiler/runtime implementations can consume the same files
without copying the reference evaluator.

```sh
clojure -M:test -n kotoba.lang.malicious-source-test
```

L-07 remains `in-progress`: the production compiler, CLJS reader, and component
admission lanes must each run this manifest directly, and coverage-guided
mutation should expand it with minimized regressions from future findings.
