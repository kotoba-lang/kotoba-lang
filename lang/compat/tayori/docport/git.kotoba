(ns tayori.docport.git
  "DocTarget backed by the GitHub API — literally a PR: `propose-revision!`
  branches off the default branch and commits the proposed `diff` to the
  document's `:path` (a real, reviewable git commit); `publish!` merges that
  branch (read off the store's :revision record, which `tayori.operation`
  merges the returned `:branch` back into after `propose-revision!`).
  Mirrors this superproject's own 'GitHub API single-entry commit' pattern for
  advancing `manifest/west.yml` pins (CLAUDE.md: west.yml 変更は GitHub API の
  サーバ側 single-entry commit を唯一の正経路にする) — no local shallow-clone
  merge to fight, no conflict markers.

  I/O injected (`:http-fn` `:json-write` `:json-read`
  `:creds {:token :owner :repo}`), same contract as `tayori.channel.email`.
  `:encode-fn`/`:decode-fn` (string <-> base64) are injected too, same
  reasoning as `tayori.channel.email`'s `:raw-fn` — the Contents API is
  base64-in/base64-out and this ns stays platform-encoding-free; a JVM host
  wires `java.util.Base64`, a cljs host `goog.crypt.base64`. Real binding is
  untested (needs a GitHub token with `contents`+`pull_requests` scope) — see
  `tayori.docport/mock-doctarget` for the runnable default."
  (:require [tayori.docport :as dp]))

(defn- gh [http-fn json-write json-read {:keys [token]} method path & [body]]
  (let [resp (http-fn (cond-> {:url (str "https://api.github.com" path)
                               :method method
                               :headers {"Authorization" (str "Bearer " token)
                                        "Accept" "application/vnd.github+json"}}
                        body (assoc :body (json-write body))))]
    (update resp :body #(when % (json-read %)))))

(defn- branch-name [document] (str "tayori/" (:id document)))

(defn- get-contents [http-fn json-write json-read creds owner repo path]
  (:body (gh http-fn json-write json-read creds :get
             (str "/repos/" owner "/" repo "/contents/" path))))

(defn git-doctarget
  [{:keys [http-fn json-write json-read creds encode-fn decode-fn default-branch]
    :or {default-branch "main"}}]
  (let [{:keys [owner repo]} creds]
    (reify dp/DocTarget
      (fetch-doc [_ document]
        (let [content (:content (get-contents http-fn json-write json-read creds owner repo (:path document)))]
          (cond
            (nil? content) nil
            decode-fn      (decode-fn content)
            :else          (throw (ex-info "git-doctarget: :decode-fn (base64 -> string) is required to fetch"
                                            {:document (:id document)})))))
      (propose-revision! [_ document diff]
        (when-not encode-fn
          (throw (ex-info "git-doctarget: :encode-fn (string -> base64) is required to propose"
                          {:document (:id document)})))
        (let [branch (branch-name document)
              base-sha (-> (gh http-fn json-write json-read creds :get
                              (str "/repos/" owner "/" repo "/git/ref/heads/" default-branch))
                           :body :object :sha)
              ;; existing file's blob sha — the Contents API 422s an update
              ;; PUT that omits it (only a brand-new path may omit :sha).
              file-sha (:sha (get-contents http-fn json-write json-read creds owner repo (:path document)))]
          (gh http-fn json-write json-read creds :post (str "/repos/" owner "/" repo "/git/refs")
              {:ref (str "refs/heads/" branch) :sha base-sha})
          (gh http-fn json-write json-read creds :put
              (str "/repos/" owner "/" repo "/contents/" (:path document))
              (cond-> {:message (str "tayori: propose revision for " (:path document))
                       :content (encode-fn diff) :branch branch}
                file-sha (assoc :sha file-sha)))
          {:branch branch}))
      (publish! [_ document _target revision]
        (gh http-fn json-write json-read creds :post (str "/repos/" owner "/" repo "/merges")
            {:base default-branch :head (:branch revision (branch-name document))
             :commit_message (str "tayori: publish " (:path document) " (human-approved)")})))))
