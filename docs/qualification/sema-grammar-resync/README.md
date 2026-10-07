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
