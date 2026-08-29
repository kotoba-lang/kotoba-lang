# Public workload-domain comparison

This benchmark compares six representative runtime paths across six workload
domains: strings, collections, allocation, file I/O, concurrency, and a small
request-admission policy application kernel.

The benchmark has four non-negotiable rules:

- every measured sample starts a fresh process and must return the exact
  reference checksum;
- unsupported target contracts are `not-applicable` with a reason, never zero
  and never an implementation in a different language;
- results may be compared only inside one workload. They are not a universal
  language ranking because the targets, JIT/AOT modes, collectors, and host
  contracts differ.
- the process-cold lane and the larger amortized in-process lane stay separate.
  The latter divides a batch by its declared multiplier; it reduces startup's
  influence but is not described as a perfectly warmed steady-state value.

Kotoba's string, collection, and allocation cases compile sovereign `.kotoba`
sources with the public `kotoba compile` command. They execute the emitted Wasm
through the declared `kotoba:typed` ABI. Its real-application case evaluates a
fixed batch of request risk scores through an admission policy. The standalone
target deliberately has no ambient filesystem or thread access, so file I/O
and concurrency remain N/A until an admitted common capability-host contract
is benchmarked.

Run from the repository root with the named toolchains on `PATH`:

```sh
node scripts/benchmark-public-domains.mjs --runs 7 \
  --kotoba-cli /path/to/kotoba --kotoba-version kotoba-cli@COMMIT \
  --output bench/public-domain-comparison/latest.json
```

The JSON report records complete samples, workload contracts, tool versions,
artifact digests, machine load, and a qualification gate.
