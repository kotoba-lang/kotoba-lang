(ns kotoba.shell.client
  "Calling the in-app native provider bridge from a ClojureScript app.

  This is the half of the mobile story that has nothing to do with Xcode.
  `cloud-itonami-app` cannot ship to a phone because its interface is served
  by a JVM process on the same machine — there is no JVM on iOS or Android, so
  no amount of packaging work reaches a device. An app whose surface is a web
  bundle has no such problem: the bundle is what `:web/dist-dir` embeds, and
  from inside it these functions reach clipboard, app-data files, the keychain,
  HTTP and notifications through providers compiled into the app.

  Every call returns a JavaScript promise of a Clojure map. Namespaced audit
  keys survive the crossing (`:audit/event`, `:audit/matched-allow`), so a
  reply here has the same shape as the one `bin/kotoba-shell native-host
  provider --json` prints.

  Nothing here throws on a denied or failed provider call: the reply carries
  `:ok? false` and a reason. A caller has to handle \"the policy says no\"
  regardless, and an exception makes that path easy to forget.

  In a plain browser — `shadow-cljs watch`, a Pages preview — `available?` is
  false and every call resolves to `:ok? false` with
  `:error \"bridge-unavailable\"`, so one bundle can run in both places."
  (:require [clojure.string :as str]))

(defn- bridge []
  (some-> js/globalThis (aget "kotobaShell")))

(defn available?
  "Whether a native host is attached to this WebView."
  []
  (boolean (some-> (bridge) (aget "available"))))

(defn target
  "`:ios`, `:android`, `:macos`, or nil in a plain browser."
  []
  (when-let [value (some-> (bridge) (aget "target"))]
    (when-not (str/blank? value)
      (keyword value))))

(defn- ->clj [reply]
  (let [m (js->clj reply :keywordize-keys true)]
    ;; `ok` on the wire, `ok?` in Clojure: the rest of this repo's data uses
    ;; the question mark and a caller should not have to remember which side
    ;; of the bridge a map came from.
    (-> m
        (assoc :ok? (true? (:ok m)))
        (dissoc :ok))))

(defn invoke
  "Calls one provider command. Returns a promise of the reply map."
  ([command] (invoke command {}))
  ([command args]
   (if-let [b (bridge)]
     (.then (.invoke b (name command) (clj->js args)) ->clj)
     (js/Promise.resolve {:schema "kotoba.shell.bridge.v0"
                          :command (name command)
                          :ok? false
                          :error "bridge-unavailable"}))))

(defn ok?
  "Whether a reply succeeded. Convenience for threading."
  [reply]
  (true? (:ok? reply)))

(defn value
  "The provider's return value, or nil when the call did not succeed."
  [reply]
  (when (ok? reply) (:value reply)))

;; ---------------------------------------------------------------------------
;; The provider surface, named. Thin on purpose: these exist so a caller does
;; not have to spell command strings and argument keys correctly, not to add
;; behaviour on top of the bridge.

(defn clipboard-read [] (invoke "clipboard/read-text"))
(defn clipboard-write! [text] (invoke "clipboard/write-text" {:text text}))

(defn fs-read
  "Reads a path relative to the app's own data directory. Absolute paths and
  `..` are refused by the provider, not here."
  [path]
  (invoke "fs/read-text" {:path path}))

(defn fs-write! [path text] (invoke "fs/write-text" {:path path :text text}))
(defn fs-append! [path text] (invoke "fs/append-text" {:path path :text text}))

(defn keychain-read [account] (invoke "keychain/read-text" {:account account}))
(defn keychain-write! [account text] (invoke "keychain/write-text" {:account account :text text}))
(defn keychain-delete! [account] (invoke "keychain/delete" {:account account}))

(defn http-fetch
  "opts: :method (default GET), :headers, :body."
  [url opts]
  (invoke "http/fetch" (merge {:url url} opts)))

(defn notify!
  "Posts a local notification. Fails with an authorization reason rather than
  presenting a permission prompt mid-call — the host asks once at launch."
  [title body]
  (invoke "notify/show" {:title title :body body}))
