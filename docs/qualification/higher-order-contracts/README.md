# Higher-order callable authority resync

The authority, its local byte copy and Sema's prepared copy match SHA256
2eb28eabdcf9648069a60976afc300219320cf10a42ee71f55e8df925e7e5212.
This records an accepted source contract with pending normal consumer
qualification, not a whole-component Q9 migration or native/Wasm qualification.
See ADR-higher-order-guest-callable-contracts and source-qualification.json.

Sema's fixed-count Node gate runs 40 tests / 219 assertions, including real
vendored byte/contract/source-bound checks. Old b379 vendor bytes fail nine
assertions in the same portable gate. Existing published definition-admission
cases independently run 2 tests / 4 assertions. The previous Sema JVM CI job
ran zero tests; only its actual Node gate is source coverage.

The authority's selected JVM compatibility diagnostic runs 31 tests / 233
assertions against exact workflow-pinned vendor blobs. Of eight registered
sibling paths, five exist and three are authoritatively missing at the verified
pinned commits. Missing copies are not comparisons. These are vendor fixtures,
not replacement sibling compiler components. The live local sibling diagnostic
preserves five failures naming the pre-existing Amu elaboration-pipeline drift;
no assertion or unrelated deferral was changed to hide that result.

Publish the authority first, then Sema's synchronized source and real Node gate,
then the normal Amu published pin/lock. Remove the Sema vendor deferral only when
its main byte copy matches this authority. Other consumers retain named debts.
