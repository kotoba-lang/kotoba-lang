(ns tayori.docport
  "DocTarget port — where a document revision becomes a *real* PR. A
  reply-LLM/document-LLM proposal is data (a `:revision` record) until a human
  approves it; `publish!` is called exactly once, after that approval, by
  `tayori.operation`'s commit step. `tayori.docport.git` implements this
  against the GitHub API — the same 'server-side single-entry commit, no
  local shallow-merge fighting' shape this superproject itself uses to
  advance `manifest/west.yml` pins. `mock-doctarget` is the default — a
  deterministic in-memory target so the actor is runnable and testable with
  no network/creds.")

(defprotocol DocTarget
  (fetch-doc [dt document] "the document's current published content, or nil")
  (propose-revision! [dt document diff] "write `diff` to a branch as a commit — a PR candidate, not yet merged. Returns {:branch ...} to be recorded on the revision so `publish!` knows what to merge.")
  (publish! [dt document target revision] "merge/publish an already-proposed revision (the store's :revision record, including the :branch propose-revision! recorded) — the actuation"))

(defn mock-doctarget
  "A deterministic in-memory DocTarget: `published` is an atom of
  {document-id -> diff} so tests/sim can assert on what WOULD have been
  published, without any network call."
  ([] (mock-doctarget (atom {})))
  ([published]
   (reify DocTarget
     (fetch-doc [_ document] (get @published (:id document)))
     (propose-revision! [_ document diff]
       {:branch (str "tayori/" (:id document)) :diff diff})
     (publish! [_ document _target revision]
       (swap! published assoc (:id document) (:diff revision))
       revision))))
