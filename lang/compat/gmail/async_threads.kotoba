(ns gmail.async-threads
  "Async (Promise-returning), cljs/nbb-only, READ-ONLY thread reads:
  list-threads / get-thread -- the async twins of gmail.threads' own
  list/get. Deliberately NOT the write half of gmail.threads
  (modify/archive/trash/untrash/delete): this surface is inbound polling
  only. Query-building mirrors gmail.threads exactly; the difference is the
  return value is a js/Promise of the parsed body (via gmail.async-client)."
  (:require [gmail.async-client :as async-client]))

(defn list-threads
  "List threads, optionally filtered by `q` (Gmail search syntax), paginated
  via `max-results`/`page-token`. Returns a js/Promise of the parsed body
  (`{:threads [...] :resultSizeEstimate N :nextPageToken ...}`). Same query
  shape as gmail.threads/list-threads."
  ([] (list-threads {}))
  ([{:keys [q max-results page-token] :as http-opts}]
   (async-client/request! "/threads"
                          (assoc (dissoc http-opts :q :max-results :page-token)
                                 :query (cond-> {}
                                          q (assoc :q q)
                                          max-results (assoc :maxResults max-results)
                                          page-token (assoc :pageToken page-token))))))

(defn get-thread
  "Fetch one thread (and its messages) by id. Returns a js/Promise of the
  parsed body. Async twin of gmail.threads/get-thread."
  ([thread-id] (get-thread thread-id {}))
  ([thread-id http-opts]
   (async-client/request! (str "/threads/" thread-id) http-opts)))
