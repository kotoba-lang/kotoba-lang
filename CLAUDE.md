# Claude project rules

Follow `AGENTS.md`. In particular, Q9 migration is a complete-component move,
not decision-core extraction, and its build/acceptance path is JVM-free:
verified native `kotoba` plus Amu `--jvm-free`. Never use an installed JDK or
`clojure` fallback to make a migration pass; block the migration instead.
