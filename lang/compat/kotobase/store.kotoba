(ns kotobase.store
  "IStore — the external-storage seam that lets murakumo / manimani (and any other
  com-junkawasaki app) run STANDALONE on a local backend or, when connected to the
  cloud, persist to **kotobase.net** (the kotoba PDS) — without the app code
  changing. Same injection pattern as the actors' Store (`MemStore ‖ DatomicStore`)
  and num-clj's IBackend.

  Two shapes of state cover both apps:
  - **docs** — a keyed, last-writer-wins document space (`put`/`get`/`list`):
    e.g. a fleet node's latest Heartbeat, a triage rule, a config fact.
  - **streams** — append-only event logs with a monotonic `:seq` cursor
    (`append`/`read`): e.g. manimani's Decision Ledger, murakumo's per-node event
    feed, the kotoba Datom log. `read` returns events whose `:seq` exceeds a cursor,
    in order — the Kafka-offset model, which is robust across devices and merges."
  )

(defprotocol IStore
  ;; document space (last-writer-wins) ---------------------------------------
  (-put  [s coll k v] "Idempotent put of value `v` at `[coll k]`; returns v.")
  (-get  [s coll k]   "Read `[coll k]` or nil.")
  (-list [s coll]     "Keys present in `coll` (vector).")
  ;; append-only streams (monotonic :seq cursor) -----------------------------
  (-append [s stream event] "Append `event` to `stream`; returns it stamped with :seq.")
  (-read   [s stream since] "Events in `stream` with :seq > `since` (0/nil = from start), :seq-ordered."))

(defprotocol ITransactionalStore
  "Optional strong extension to IStore. A snapshot is read at one tenant
  revision; transact atomically compares that revision and publishes every
  document mutation and stream append at one new revision."
  (-snapshot [s scope]
    "Read one revision. SCOPE is {:collections [...] :streams [...]} and the
    result is {:revision n :docs {...} :streams {...}}.")
  (-transact [s request]
    "Atomically apply {:tx-id :expected-revision :puts :deletes :appends}.
    Replaying a tx-id returns its original receipt; a stale expected revision
    throws RevisionConflict without applying any mutation."))

(defn store? [x] (satisfies? IStore x))
(defn transactional-store? [x] (satisfies? ITransactionalStore x))
