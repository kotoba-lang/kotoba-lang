;; murakumo.tunnel — THE transport contract for every fleet operation.
;;
;; Portable (.cljc): the bb/JVM control plane (murakumo.ssh) and the nbb task
;; plane (murakumo.task.exec) must speak the SAME ssh dialect, or a defect fixed
;; in one keeps biting in the other. Three things live here:
;;
;;   1. connection options — non-interactive, fail-fast, host-key accept-new
;;   2. IN-BAND EXIT STATUS — see `wrap-cmd`; the one defect that silently
;;      corrupts every fleet decision if it is not fixed at this layer
;;   3. connection multiplexing — optional ControlMaster reuse, which removes
;;      the TCP+auth handshake from every command after the first
;;
;; W6 product-shell + T6.4: pure string/i64 helpers + ssh/scp bin/flag fragments
;; require the shipped `:tunnel` KIR on **every** platform. Host pure mirrors
;; are gone — cljs/nbb must preload shipped KIR (resources/ via nbb cwd,
;; register-kir!, or set-resource-loader!) before requiring this ns
;; (ADR-260731-w6-t64-tunnel-mirror-delete).
;; Host remains: line-split of parse-rc, argv vector assembly, SSH subprocess.

(ns murakumo.tunnel
  (:require [kotoba.lang.text :as str]
            [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :tunnel)

(defn- o
  "Call a pure export. Requires the shipped oracle on every platform (T6.4)."
  [export args]
  (oracle/require-ready! oid)
  (oracle/call oid export args))

(defn- o-record
  "T5.2: structural host map → call-record (requires shipped oracle)."
  [export host-map field-specs]
  (oracle/require-ready! oid)
  (oracle/call-record oid export host-map field-specs))

(def ^:private scp-schema
  [:record :tunnel/scp [[:host :string] [:dest :string]]])
(def ^:private forward-schema
  [:record :tunnel/forward
   [[:local-port :i64] [:remote-port :i64] [:host :string]]])
(def ^:private exit-schema
  [:record :tunnel/exit [[:has-rc :bool] [:rc :i64] [:ssh-exit :i64]]])

;; ── constants (oracle SSoT) ────────────────────────────────────────────

(def default-connect-timeout-s
  (oracle/i64->host (o 'default-connect-timeout-s [])))

(def default-control-persist-s
  (oracle/i64->host (o 'default-control-persist-s [])))

(def rc-marker
  (o 'rc-marker []))

(def ssh-bin
  "ssh binary name. Kotoba SSoT (requires oracle)."
  (o 'ssh-bin []))

(def scp-bin
  "scp binary name. Kotoba SSoT (requires oracle)."
  (o 'scp-bin []))

(def o-flag
  "ssh/scp -o flag. Kotoba SSoT (requires oracle)."
  (o 'o-flag []))

(def O-flag
  "ssh -O control flag. Kotoba SSoT (requires oracle)."
  (o 'O-flag []))

(def exit-ctl
  "ssh -O exit control verb. Kotoba SSoT (requires oracle)."
  (o 'exit-ctl []))

(def pgrep-bin
  (o 'pgrep-bin []))
(def pkill-bin
  (o 'pkill-bin []))
(def f-flag
  (o 'f-flag []))
(def fN-flag
  (o 'fN-flag []))
(def L-flag
  (o 'L-flag []))
(def null-redirect
  (o 'null-redirect []))
(def or-sep
  (o 'or-sep []))
(def settle-sleep
  (o 'settle-sleep []))
(def pkill-suffix
  (o 'pkill-suffix []))
(def localhost-colon
  (o 'localhost-colon []))
(def curl-prefix
  (o 'curl-prefix []))
(def curl-stderr-redirect
  (o 'curl-stderr-redirect []))

(defn- batch-mode-opt []
  (o 'batch-mode-opt []))

(defn- strict-host-key-opt []
  (o 'strict-host-key-opt []))

(defn- control-master-opt []
  (o 'control-master-opt []))

(defn- connect-timeout-opt [seconds]
  (o-record 'connect-timeout-opt {:seconds seconds} [[:seconds :i64]]))

(defn- control-path-opt [path]
  (o-record 'control-path-opt {:path path} [[:path :string]]))

(defn- control-persist-opt [seconds]
  (o-record 'control-persist-opt {:seconds seconds} [[:seconds :i64]]))

(def ssh-opts
  [o-flag (batch-mode-opt)
   o-flag (connect-timeout-opt default-connect-timeout-s)
   o-flag (strict-host-key-opt)])

(defn conn-opts
  "SSH -o flags for a connection. `:control-path` turns on multiplexing: the
   first connection to a host becomes the master, later ones reuse its socket.
   Opt fragments + o-flag via kotoba (required)."
  [{:keys [connect-timeout-s control-path control-persist-s]}]
  (vec (concat [o-flag (batch-mode-opt)
                o-flag (connect-timeout-opt
                        (or connect-timeout-s default-connect-timeout-s))
                o-flag (strict-host-key-opt)]
               (when control-path
                 [o-flag (control-master-opt)
                  o-flag (control-path-opt control-path)
                  o-flag (control-persist-opt
                          (or control-persist-s default-control-persist-s))]))))

;; --- in-band exit status ----------------------------------------------------

(defn wrap-cmd
  "Wrap a remote command so it reports its OWN exit status in-band.

   MEASURED 2026-07-25 (ADR-2607256000): Tailscale SSH on the macOS fleet nodes
   does NOT propagate the remote exit status — `ssh asher 'exit 7'` and
   `ssh asher false` both return 0, while the Linux node (gad) correctly returns
   7 and 1. Every fleet decision made on `(:exit (ssh/sh …))` — provision
   success, launchctl load, `rm -rf` in the model GC, engine probes — therefore
   reads as SUCCESS on 10 of the 11 reachable nodes, whatever actually happened.

   The command runs in a subshell (so a bare `exit N` inside it does not kill
   the reporting shell) and the true status is echoed as a sentinel line that
   `parse-rc` extracts and strips.
   Kotoba `wrap-cmd` (required)."
  [cmd]
  (o-record 'wrap-cmd {:cmd cmd} [[:cmd :string]]))

(defn parse-rc
  "Split captured stdout into [clean-stdout rc-or-nil]. rc is nil when the
   sentinel never arrived (connection failure, kill, non-shell remote), in which
   case the caller falls back to ssh's own exit code — which IS trustworthy for
   ssh-level failures (255) even where the remote status is not.
   Marker/digits classification via kotoba (required); line-split stays host."
  [out]
  (let [;; ⚠ The sentinel is only ITS OWN LINE when the remote command's stdout
        ;; ended with a newline. `curl` against a JSON API does not end with
        ;; one, so the sentinel glues onto the closing brace and the last line
        ;; reads `{...}__murakumo_rc=0` -- which `marker?` does not match.
        ;; Both halves of this function then fail at once, silently and in
        ;; opposite directions:
        ;;
        ;;   :out  keeps the sentinel, so every JSON parse downstream throws
        ;;         `trailing JSON input` (measured 2026-09-10: this is why
        ;;         `murakumo.infer.media/comfy` could not read /system_stats,
        ;;         and so why the ComfyUI gateway's /health returned an error)
        ;;   rc    is nil, so `pick-exit` falls back to ssh's own code -- the
        ;;         very code this function's docstring says is untrustworthy on
        ;;         this fleet's macOS nodes, which report 0 regardless
        ;;
        ;; So a command whose output lacks a trailing newline loses its exit
        ;; status AND corrupts its stdout, and reports success either way.
        ;; Put a glued sentinel back on its own line before splitting.
        raw (str out)
        i (str/index-of raw rc-marker)
        raw (if (and i (pos? i) (not= \newline (nth raw (dec i))))
              (str (subs raw 0 i) "\n" (subs raw i))
              raw)
        lines (str/split-lines raw)
        marker? (fn [l]
                  (let [t (str/trim (str l))]
                    (oracle/bool->host (o-record 'marker-prefix? {:t t} [[:t :string]]))))
        rc-line (last (filter marker? lines))
        digits (when rc-line
                 (let [t (str/trim (str rc-line))]
                   (o-record 'strip-marker-digits {:t t} [[:t :string]])))
        rc (when digits
             (let [v (oracle/i64->host (o-record 'parse-digits {:digits digits} [[:digits :string]]))]
               (when-not (neg? v) v)))]
    [(str/trim (str/join "\n" (remove marker? lines))) rc]))

;; --- argv shapes ------------------------------------------------------------

(defn ssh-argv
  "argv for running a remote shell command over SSH.

   The command is wrapped for in-band exit reporting unless `:wrap? false` is
   passed (use that only for argv that is not a remote shell command).
   Bin name via `ssh-bin`."
  ([host cmd] (ssh-argv host cmd nil))
  ([host cmd opts]
   (vec (concat [ssh-bin] (conn-opts opts)
                [host (if (false? (:wrap? opts)) cmd (wrap-cmd cmd))]))))

(defn scp-argv
  "argv for copying a local file to host:dest. scp runs no remote shell, so it
   is NOT wrapped — scp's own exit status is the client's, which this fleet does
   propagate correctly. Bin via `scp-bin`."
  ([host local dest] (scp-argv host local dest nil))
  ([host local dest opts]
   (vec (concat [scp-bin] (conn-opts opts)
                [local (o-record 'scp-dest
                                   {:s (oracle/record scp-schema
                                                      {:host host :dest dest})}
                                   [[:s :raw]])]))))

(defn close-master-argv
  "argv that shuts down a multiplexed connection's master socket. Run this when
   a batch finishes instead of leaving masters to expire on ControlPersist.
   Bin/flags via ssh-bin / o-flag / O-flag / exit-ctl."
  [host control-path]
  [ssh-bin o-flag (o-record 'close-master-control-opt {:control-path control-path} [[:control-path :string]])
   O-flag exit-ctl host])

(defn ensure-forward-command
  "Shell command that starts an SSH local forward only when an equivalent one is absent.
   Kotoba `ensure-forward-command` (required).
   T5.2: structural forward map → call-record."
  [local-port remote-port host]
  (o-record 'ensure-forward-command
            {:f (oracle/record forward-schema
                               {:local-port local-port
                                :remote-port remote-port
                                :host host})}
            [[:f :raw]]))

(defn replace-forward-command
  "Shell command that kills any forward on local-port, then starts a fresh one.
   Kotoba `replace-forward-command` (required).
   T5.2: structural forward map → call-record."
  [local-port remote-port host]
  (o-record 'replace-forward-command
            {:f (oracle/record forward-schema
                               {:local-port local-port
                                :remote-port remote-port
                                :host host})}
            [[:f :raw]]))

(defn remote-curl-command
  "Remote shell command for a bounded curl call from a node.
   Kotoba `remote-curl-command` (required)."
  [url]
  (o-record 'remote-curl-command {:url url} [[:url :string]]))

;; --- result shapes ----------------------------------------------------------

(defn- pick-exit
  "Prefer in-band rc when present. Kotoba `pick-exit` (required).
   Profile 5: has-rc is guest :bool presence.
   T5.2: structural exit map → call-record."
  [rc ssh-exit]
  (oracle/i64->host
   (o-record 'pick-exit
             {:e (oracle/record exit-schema
                                {:has-rc (some? rc)
                                 :rc (or rc 0)
                                 :ssh-exit (or ssh-exit 0)})}
             [[:e :raw]])))

(defn- trim-err
  "Trim stderr. Kotoba `trim-err` (required)."
  [err]
  (o-record 'trim-err {:err err} [[:err :string]]))

(defn sh-result
  "Normalise process output from an SSH command.

   `:exit` is the REMOTE command's status (from the in-band sentinel) whenever
   it is available, falling back to ssh's own code; `:ssh-exit` keeps what the
   ssh client reported, so a caller can still tell a transport failure from a
   remote non-zero exit. Exit pick + err trim via kotoba (required)."
  [{:keys [exit out err]}]
  (let [[clean rc] (parse-rc out)]
    {:exit (pick-exit rc exit)
     :ssh-exit exit
     :out clean
     :err (trim-err err)}))

(defn scp-result
  "Normalise process output from an SCP command.
   Err trim via kotoba (required)."
  [{:keys [exit err]}]
  {:exit exit
   :err (trim-err err)})
