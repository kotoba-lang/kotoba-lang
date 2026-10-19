(ns gmail.async-history
  "Async (Promise-returning), cljs/nbb-only READ of users.history.list --
  the async twin of gmail.history/list-history, mirroring its query shape
  (`:startHistoryId` + optional `:pageToken`) and its caveats, but returning
  a js/Promise of the parsed body. This is exactly the nbb-side use case that
  motivated the sync gmail.history in the first place: read-only inbound
  incremental-sync polling (kotoba-lang/tayori), the kind of loop that runs
  under nbb, not the JVM."
  (:require [gmail.async-client :as async-client]))

(defn list-history
  "List history records since `start-history-id`, paginated via
  `page-token`. Returns a js/Promise of the parsed body
  (`{:history [...] :historyId \"...\"}`).

  Carries over gmail.history/list-history's two caveats: (1) Gmail's
  `historyTypes` filter is a repeated query param this query-string builder
  can't express, so filter the returned `:history` records client-side; and
  (2) a 404 (surfaced here as a REJECTED Promise) means `start-history-id` is
  too old -- treat it as \"cursor expired, do a full resync\", not retry."
  ([start-history-id] (list-history start-history-id {}))
  ([start-history-id {:keys [page-token] :as http-opts}]
   (async-client/request! "/history"
                          (assoc (dissoc http-opts :page-token)
                                 :query (cond-> {:startHistoryId start-history-id}
                                          page-token (assoc :pageToken page-token))))))
