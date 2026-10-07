# Close the published Sema grammar resync

Language authority PR719 and Sema PR100 now publish matching grammar bytes.
The existing test-consumer pin still read the older 9a32183e copy, whose source
bound was the reason for its hold. Advance it to published d34fb53f and remove
only the completed named Sema grammar deferral in the same change. Other vendor
deferrals retain their recorded blockers.

The selected JVM compatibility diagnostic passes 32 tests / 236 assertions in a
CI-shaped layout with verified pinned vendor-byte fixtures. Four of five grammar
copies are openable; missing files are not counted as comparisons. Five schema/
registration assertions retire with the removed deferral; no test is removed.
Replacing only the generated Sema fixture with the old pinned bytes yields six
assertion failures at the same test/assertion count. Restoration is hash-verified.

These fixtures are vendor blobs, not full sibling components. This is a byte/
consumer compatibility diagnostic, not whole-component Q9 migration, native
selfhost or JVM-free acceptance. Exact-head CI/main publication is pending.
System One made no model attempt.

Initial head 8d5bc43 failed actual CI because the workflow separately fetched and
detached the old sibling Sema revision. The desired-pin byte fixture therefore
did not describe that initial checkout. The follow-up synchronizes both workflow
references with the deps test pin; the six-failure stale-copy control describes
why the initial CI fails. Revised exact-head CI remains required before merge.

The synchronized c7ad38dc checkout ran actual CI: 518 tests / 4,649 assertions,
one failure and no errors. The remaining expectation was the historical empty
record refusal. Published Sema's options-map-argument floor explicitly supersedes
that refusal; Script PR112 now publishes matching JS codec support. Amu PR1251
qualifies the normal JVM-free JS routes with published pins (81 / 576 portable
assertions; offline Node 24.21.0 runs 1,000 fresh instances). That Amu consumer's
CI/main publication remains pending. Amu PR1250 is already main a0604488.

Reconcile the authority's historic blocker decision and manifest with the
published empty-record floor, retain the version-2 withdrawal history, add a
positive named constructor case, and keep two-nominal-result/reserved-name
refusals and all withdrawn exports. Frozen stdlib module bytes and export lists
are unchanged. This is consumer/contract compatibility work, not whole-component
Q9 migration. Revised exact-head full CI is still required before merge.
