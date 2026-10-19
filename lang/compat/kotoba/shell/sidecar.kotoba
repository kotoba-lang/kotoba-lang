(ns kotoba.shell.sidecar
  "A companion process an app needs running for as long as it is open.

  Some apps are a window in front of a local server: `murakumo-studio` is a
  ClojureScript UI talking over HTTP to a JVM inference engine, and the window
  is useless without it. That app carried an 89-line Tauri crate whose entire
  job was to start that process and kill it when the window closed — a crate
  per app, in Rust, to express four decisions. Its own header said it: \"No
  business logic lives in Rust.\"

  Those four decisions live here instead, as data:

    where to run       :sidecar/dir, or a marker file to search upward for
    what to run        :sidecar/command, a vector of strings
    what may vary      :sidecar/env-overrides, host env var -> which key it sets
    when it stops      implicit: with the app

  This namespace is the DECISION half — it resolves a manifest into a runnable
  spec and refuses one it cannot. Starting, supervising and stopping the process
  belongs to the host, which already owns process mechanics for
  `native-host run`. Keeping the split means a new app that needs a companion
  process writes EDN, not a crate.

  Pure `.cljc`: no process, no filesystem. `resolve-dir` takes the directory
  listing it should decide over, so the same code decides identically on every
  runtime and a test needs no temp directories.")

(def schema "kotoba.shell.sidecar.v0")

(defn- non-blank-string? [s]
  (and (string? s) (not= "" (clojure.string/trim s))))

(defn- command-ok? [command]
  (and (vector? command)
       (seq command)
       (every? non-blank-string? command)))

(defn declared?
  "Does this manifest ask for a sidecar at all? Absence is legitimate — most
  apps are just a window."
  [manifest]
  (some? (:sidecar/command manifest)))

(defn problems
  "Every reason this manifest cannot produce a runnable sidecar, as a vector of
  strings. Empty means it can. Reporting all of them at once matters: a
  manifest is edited by hand, and fixing one error to be told about the next is
  the slowest possible way to learn the shape."
  [manifest]
  (cond-> []
    (not (command-ok? (:sidecar/command manifest)))
    (conj (str ":sidecar/command must be a non-empty vector of non-blank strings, got "
               (pr-str (:sidecar/command manifest))))

    (and (contains? manifest :sidecar/dir)
         (not (non-blank-string? (:sidecar/dir manifest))))
    (conj ":sidecar/dir, when given, must be a non-blank string")

    (and (contains? manifest :sidecar/find-root-marker)
         (not (non-blank-string? (:sidecar/find-root-marker manifest))))
    (conj ":sidecar/find-root-marker, when given, must be a non-blank string")

    (and (not (contains? manifest :sidecar/dir))
         (not (contains? manifest :sidecar/find-root-marker)))
    (conj (str "a sidecar needs somewhere to run: give :sidecar/dir, or "
               ":sidecar/find-root-marker to search upward for"))

    (and (contains? manifest :sidecar/env-overrides)
         (not (and (map? (:sidecar/env-overrides manifest))
                   (every? (fn [[k v]] (and (non-blank-string? k) (keyword? v)))
                           (:sidecar/env-overrides manifest)))))
    (conj ":sidecar/env-overrides must map an env var name to the manifest key it overrides")))

(defn resolve-dir
  "Which directory to run in.

  `start` is where the search begins and `ancestors` is that directory followed
  by each of its parents, outermost last — the caller supplies it because
  walking a filesystem is the host's job, not a decision. `contains-marker?` is
  asked of each in turn.

  Returns `{:dir d}` or `{:error msg}`. The Rust this replaces fell back to
  `$HOME/<app>` when the search failed, which turned a misconfigured checkout
  into a process started in the wrong place — a silent wrong answer where an
  error was available."
  [{:keys [start ancestors contains-marker?]} manifest]
  (if-let [d (:sidecar/dir manifest)]
    {:dir d}
    (let [marker (:sidecar/find-root-marker manifest)]
      (if-let [found (first (filter contains-marker? ancestors))]
        {:dir found}
        {:error (str "no ancestor of " (pr-str start) " contains " (pr-str marker)
                     " — cannot decide where to run " (pr-str (first (:sidecar/command manifest))))}))))

(defn apply-env-overrides
  "A manifest value the host environment is allowed to replace.

  Only keys the manifest itself lists in `:sidecar/env-overrides` can be
  overridden, so the set of things an env var can change is declared by the app
  rather than discovered by reading the launcher."
  [manifest env]
  (reduce (fn [m [var-name k]]
            (let [v (get env var-name)]
              (if (non-blank-string? v) (assoc m k v) m)))
          manifest
          (:sidecar/env-overrides manifest)))

(defn spec
  "manifest + host facts → `{:command [...] :dir d}` or `{:error msg}`.

  The whole decision, in one call, so a host has nothing left to decide."
  [{:keys [env] :as host} manifest]
  (let [m (apply-env-overrides manifest (or env {}))
        issues (problems m)]
    (if (seq issues)
      {:error (clojure.string/join "; " issues)}
      (let [{:keys [dir error]} (resolve-dir host m)]
        (if error
          {:error error}
          {:command (:sidecar/command m) :dir dir})))))
